/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.idrsolutions.microservice;

import com.idrsolutions.microservice.db.DBHandler;
import com.idrsolutions.microservice.storage.Storage;
import com.idrsolutions.microservice.utils.FileDeletionService;
import com.idrsolutions.microservice.utils.ProgressTracker;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseServletContextListener implements ServletContextListener {

    public static final String KEY_PROPERTIES = "properties";
    public static final String KEY_PROPERTY_LIBRE_OFFICE = "libreOfficePath";
    public static final String KEY_PROPERTY_LIBRE_OFFICE_TIMEOUT = "libreOfficeTimeout";
    public static final String KEY_PROPERTY_CONVERSION_COUNT = "conversionThreadCount";
    public static final String KEY_PROPERTY_DOWNLOAD_COUNT = "downloadThreadCount";
    public static final String KEY_PROPERTY_CALLBACK_COUNT = "callbackThreadCount";
    public static final String KEY_PROPERTY_INPUT_PATH = "inputPath";
    public static final String KEY_PROPERTY_OUTPUT_PATH = "outputPath";
    public static final String KEY_PROPERTY_INDIVIDUAL_TTL = "individualTTL";
    public static final String KEY_PROPERTY_FILE_DELETION_SERVICE = "fileDeletionService";
    public static final String KEY_PROPERTY_FILE_DELETION_SERVICE_FREQUENCY = "fileDeletionService.frequency";
    public static final String KEY_PROPERTY_MAX_CONVERSION_DURATION = "maxConversionDuration";
    public static final String KEY_PROPERTY_REMOTE_TRACKING_REGISTRY = "remoteTrackingRegistry";
    public static final String KEY_PROPERTY_REMOTE_TRACKING_PORT = "remoteTracker.port";
    public static final String KEY_PROPERTY_CONVERSION_MEMORY = "conversionMemoryLimit";

    private static final String KEY_PROPERTY_DATABASE_JNDI_NAME = "databaseJNDIName";

    private static final Logger LOG = Logger.getLogger(BaseServletContextListener.class.getName());

    public abstract String getConfigPath();

    public abstract String getConfigName();

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        final Properties propertiesFile = new Properties();
        final ServletContext servletContext = servletContextEvent.getServletContext();
        final File externalFile = new File(getConfigPath() + getConfigName());

        try (InputStream intPropertiesFile = BaseServletContextListener.class.getResourceAsStream("/" + getConfigName())) {
            propertiesFile.load(intPropertiesFile);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "IOException thrown when reading default properties file", e);
        }

        if (externalFile.exists()) {
            try (InputStream extPropertiesFile = new FileInputStream(externalFile.getAbsolutePath())) {
                propertiesFile.load(extPropertiesFile);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "IOException thrown when reading external properties file", e);
            }
        }

        validateConfigFileValues(propertiesFile);
        final String storageProvider = propertiesFile.getProperty("storageprovider");

        if (storageProvider != null) {
            try {
                final Class<?> cls = this.getClass().getClassLoader().loadClass(storageProvider);
                if (!Storage.class.isAssignableFrom(cls)) throw new ClassCastException();

                servletContext.setAttribute("storage", cls.getConstructor(Properties.class).newInstance(propertiesFile));
            } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                throw new IllegalStateException(e.getCause());
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Failed to find a valid constructor, the IStorage implementation needs to have a constructor that takes a properties object", e);
            } catch (ClassCastException e) {
                throw new IllegalStateException("The storage provider class must be an implementation of com.idrsolutions.microservice.storage.IStorage", e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to find a class with that name", e);
            }
        }

        servletContext.setAttribute(KEY_PROPERTIES, propertiesFile);

        final ExecutorService convertQueue = Executors.newFixedThreadPool(Integer.parseInt(propertiesFile.getProperty(KEY_PROPERTY_CONVERSION_COUNT)));
        final ExecutorService downloadQueue = Executors.newFixedThreadPool(Integer.parseInt(propertiesFile.getProperty(KEY_PROPERTY_DOWNLOAD_COUNT)));
        final ScheduledExecutorService callbackQueue = Executors.newScheduledThreadPool(Integer.parseInt(propertiesFile.getProperty(KEY_PROPERTY_CALLBACK_COUNT)));

        servletContext.setAttribute("convertQueue", convertQueue);
        servletContext.setAttribute("downloadQueue", downloadQueue);
        servletContext.setAttribute("callbackQueue", callbackQueue);

        BaseServlet.setInputPath(propertiesFile.getProperty(KEY_PROPERTY_INPUT_PATH));
        BaseServlet.setOutputPath(propertiesFile.getProperty(KEY_PROPERTY_OUTPUT_PATH));
        BaseServlet.setIndividualTTL(Long.parseLong(propertiesFile.getProperty(KEY_PROPERTY_INDIVIDUAL_TTL)));

        DBHandler.setDatabaseJNDIName(propertiesFile.getProperty(KEY_PROPERTY_DATABASE_JNDI_NAME));
        DBHandler.initialise();

        if (Boolean.parseBoolean(propertiesFile.getProperty(KEY_PROPERTY_FILE_DELETION_SERVICE))) {
            servletContext.setAttribute(KEY_PROPERTY_FILE_DELETION_SERVICE, new FileDeletionService(
                    new String[]{
                            propertiesFile.getProperty(KEY_PROPERTY_INPUT_PATH), propertiesFile.getProperty(KEY_PROPERTY_OUTPUT_PATH)
                    },
                    Long.parseLong(propertiesFile.getProperty(KEY_PROPERTY_INDIVIDUAL_TTL)),
                    Long.parseLong(propertiesFile.getProperty(KEY_PROPERTY_FILE_DELETION_SERVICE_FREQUENCY))
            ));
        }

        final String remoteTrackingPort = propertiesFile.getProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT);
        try {
            LOG.log(Level.INFO, "Creating RMI registry on port " + remoteTrackingPort);
            final Registry registry = LocateRegistry.createRegistry(Integer.parseInt(remoteTrackingPort));
            propertiesFile.put(KEY_PROPERTY_REMOTE_TRACKING_REGISTRY, registry);
            registry.bind("com.idrsolutions.remoteTracker.stub", new ProgressTracker(Integer.parseInt(remoteTrackingPort)));
        } catch (final RemoteException | AlreadyBoundException e) {
            LOG.log(Level.SEVERE, "Unable to create Registry to allow conversion tracking.", e);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        ((ExecutorService) servletContext.getAttribute("convertQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("downloadQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("callbackQueue")).shutdownNow();
        ((FileDeletionService) servletContext.getAttribute(KEY_PROPERTY_FILE_DELETION_SERVICE)).shutdownNow();

        try {
            if (!((ExecutorService) servletContext.getAttribute("convertQueue")).awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.log(Level.SEVERE, "convertQueue did not terminate within timeout");
            }
        } catch (final InterruptedException e) {
            LOG.log(Level.SEVERE, "convertQueue shutdown timed out", e);
        }
        try {
            if (!((ExecutorService) servletContext.getAttribute("downloadQueue")).awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.log(Level.SEVERE, "downloadQueue did not terminate within timeout");
            }
        } catch (final InterruptedException e) {
            LOG.log(Level.SEVERE, "downloadQueue shutdown timed out", e);
        }
        try {
            if (!((ExecutorService) servletContext.getAttribute("callbackQueue")).awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.log(Level.SEVERE, "callbackQueue did not terminate within timeout");
            }
        } catch (final InterruptedException e) {
            LOG.log(Level.SEVERE, "callbackQueue shutdown timed out", e);
        }
        try {
            if (!((FileDeletionService) servletContext.getAttribute(KEY_PROPERTY_FILE_DELETION_SERVICE)).awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.log(Level.SEVERE, "FileDeletionService did not terminate within timeout");
            }
        } catch (final InterruptedException e) {
            LOG.log(Level.SEVERE, "FileDeletionService shutdown timed out", e);
        }

        LOG.log(Level.INFO, "Shutting down RMI registry");
        final Properties propertiesFile = (Properties) servletContextEvent.getServletContext().getAttribute(KEY_PROPERTIES);
        final Registry registry = ((Registry) propertiesFile.get(KEY_PROPERTY_REMOTE_TRACKING_REGISTRY));
        if (registry != null) {
            try {
                final Remote stub = registry.lookup("com.idrsolutions.remoteTracker.stub");
                registry.unbind("com.idrsolutions.remoteTracker.stub");
                UnicastRemoteObject.unexportObject(stub, true);
            } catch (final RemoteException | NotBoundException e) {
                LOG.log(Level.SEVERE, "Unable to unbind/unexport RemoteTracker stub", e);
            }
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (final RemoteException e) {
                LOG.log(Level.SEVERE, "Unable to stop Registry for conversion tracking.", e);
            }
        }
    }

    protected void validateConfigFileValues(final Properties propertiesFile) {
        validateConversionThreadCount(propertiesFile);
        validateDownloadThreadCount(propertiesFile);
        validateCallbackThreadCount(propertiesFile);
        validateInputPath(propertiesFile);
        validateOutputPath(propertiesFile);
        validateIndividualTTL(propertiesFile);
        validateFileDeletionService(propertiesFile);
        validateFileDeletionServiceFrequency(propertiesFile);
        validateMaxConversionDuration(propertiesFile);
        validateConversionMemoryLimit(propertiesFile);
        validateRemoteTrackerPort(propertiesFile);
    }

    private static void validateConversionThreadCount(final Properties properties) {
        final String conversonThreads = properties.getProperty(KEY_PROPERTY_CONVERSION_COUNT);
        if (conversonThreads == null || conversonThreads.isEmpty()) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty(KEY_PROPERTY_CONVERSION_COUNT, "" + availableProcessors);
            final String message = String.format("Properties value for \"conversionThreadCount\" has not been set. Using a value of \"%d\" based on available processors.", availableProcessors);
            LOG.log(Level.INFO, message);
        } else if (!conversonThreads.matches("\\d+") || Integer.parseInt(conversonThreads) == 0) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty(KEY_PROPERTY_CONVERSION_COUNT, "" + availableProcessors);
            final String message = String.format("Properties value for \"conversionThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of \"%d\" based on available processors.", conversonThreads, availableProcessors);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateDownloadThreadCount(final Properties properties) {
        final String downloadThreads = properties.getProperty(KEY_PROPERTY_DOWNLOAD_COUNT);
        if (downloadThreads == null || downloadThreads.isEmpty() || !downloadThreads.matches("\\d+") || Integer.parseInt(downloadThreads) == 0) {
            properties.setProperty(KEY_PROPERTY_DOWNLOAD_COUNT, "5");
            final String message = String.format("Properties value for \"downloadThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of 5.", downloadThreads);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateCallbackThreadCount(final Properties properties) {
        final String callbackThreads = properties.getProperty(KEY_PROPERTY_CALLBACK_COUNT);
        if (callbackThreads == null || callbackThreads.isEmpty() || !callbackThreads.matches("\\d+") || Integer.parseInt(callbackThreads) == 0) {
            properties.setProperty(KEY_PROPERTY_CALLBACK_COUNT, "5");
            final String message = String.format("Properties value for \"callbackThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of 5.", callbackThreads);
            LOG.log(Level.WARNING, message);
        }
    }

    private void validateInputPath(final Properties properties) {
        final String inputPath = properties.getProperty(KEY_PROPERTY_INPUT_PATH);
        if (inputPath == null || inputPath.isEmpty()) {
            final String inputDir = getConfigPath() + "input";
            properties.setProperty(KEY_PROPERTY_INPUT_PATH, inputDir);
            final String message = String.format("Properties value for \"inputPath\" was not set. Using a value of \"%s\"", inputDir);
            LOG.log(Level.WARNING, message);
        } else if (inputPath.startsWith("~")) {
            properties.setProperty(KEY_PROPERTY_INPUT_PATH, System.getProperty("user.home") + inputPath.substring(1));
        }
    }

    private void validateOutputPath(final Properties properties) {
        final String outputPath = properties.getProperty(KEY_PROPERTY_OUTPUT_PATH);
        if (outputPath == null || outputPath.isEmpty()) {
            final String outputDir = getConfigPath() + "output";
            properties.setProperty(KEY_PROPERTY_OUTPUT_PATH, outputDir);
            final String message = String.format("Properties value for \"outputPath\" was not set. Using a value of \"%s\"", outputDir);
            LOG.log(Level.WARNING, message);
        } else if (outputPath.startsWith("~")) {
            properties.setProperty(KEY_PROPERTY_OUTPUT_PATH, System.getProperty("user.home") + outputPath.substring(1));
        }
    }

    private void validateIndividualTTL(final Properties properties) {
        final String rawIndividualTTL = properties.getProperty(KEY_PROPERTY_INDIVIDUAL_TTL);
        if (rawIndividualTTL == null || rawIndividualTTL.isEmpty() || !rawIndividualTTL.matches("\\d+")) {
            final String defaultTTL = Long.toString(BaseServlet.getIndividualTTL());
            properties.setProperty(KEY_PROPERTY_INDIVIDUAL_TTL, defaultTTL);
            final String message = String.format("Properties value for \"individualTTL\" was set to \"%s\" but should" +
                    " be a positive long. Using a value of %s.", rawIndividualTTL, defaultTTL);
            LOG.log(Level.WARNING, message);
        }
    }

    private void validateFileDeletionService(final Properties properties) {
        final String fileDeletionService = properties.getProperty(KEY_PROPERTY_FILE_DELETION_SERVICE);
        if (fileDeletionService == null || fileDeletionService.isEmpty() || !Boolean.parseBoolean(fileDeletionService)) {
            properties.setProperty(KEY_PROPERTY_FILE_DELETION_SERVICE, "false");
            if (!"false".equalsIgnoreCase(fileDeletionService)) {
                final String message = String.format("Properties value for \"fileDeletionService\" was set to \"%s\" " +
                        "but should be a boolean. Using a value of false.", fileDeletionService);
                LOG.log(Level.WARNING, message);
            }
        }
    }

    private void validateFileDeletionServiceFrequency(final Properties properties) {
        final String fdsFrequency = properties.getProperty(KEY_PROPERTY_FILE_DELETION_SERVICE_FREQUENCY);
        if (fdsFrequency == null || fdsFrequency.isEmpty() || "0".equals(fdsFrequency) || !fdsFrequency.matches("\\d+")) {
            properties.setProperty(KEY_PROPERTY_FILE_DELETION_SERVICE_FREQUENCY, "5");
            final String message = String.format("Properties value for \"fileDeletionService.frequency\" was set to " +
                    "\"%s\" but should be a positive long. Using a value of 5 Minutes.", fdsFrequency);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateMaxConversionDuration(final Properties properties) {
        final String maxDuration = properties.getProperty(KEY_PROPERTY_MAX_CONVERSION_DURATION);
        if (maxDuration == null || maxDuration.isEmpty() || "0".equals(maxDuration)
                || (!maxDuration.matches("\\d+") && !"Infinity".equals(maxDuration))) {
            properties.setProperty(KEY_PROPERTY_MAX_CONVERSION_DURATION, String.valueOf(Long.MAX_VALUE));
            final String message = String.format("Properties value for \"maxConversionDuration\" was set to " +
                    "\"%s\" but should be a positive long or Infinity. Using a value of Infinity.", maxDuration);
            LOG.log(Level.WARNING, message);
        } else if ("Infinity".equals(maxDuration)) {
            properties.setProperty(KEY_PROPERTY_MAX_CONVERSION_DURATION, String.valueOf(Long.MAX_VALUE));
            final String message = String.format("Interpreting \"Infinity\" as %d", Long.MAX_VALUE);
            LOG.log(Level.INFO, message);
        }
    }

    private static void validateConversionMemoryLimit(final Properties properties) {
        final String maxMemory = properties.getProperty(KEY_PROPERTY_CONVERSION_MEMORY);
        if (maxMemory == null || maxMemory.isEmpty()) {
            properties.setProperty(KEY_PROPERTY_CONVERSION_MEMORY, "-1");
            LOG.log(Level.WARNING, "Properties value for \"conversionMemoryLimit\" was not set so memory consumption for conversion will be unlimited.");
        } else if ("0".equals(maxMemory) || !maxMemory.matches("\\d+")){
            properties.setProperty(KEY_PROPERTY_CONVERSION_MEMORY, "-1");
            final String message = String.format("Properties value for \"conversionMemoryLimit\" was set to " +
                    "\"%s\" but should be a positive integer. Using a value of Infinity.", maxMemory);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateRemoteTrackerPort(final Properties properties) {
        final String remoteTrackingPort = properties.getProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT);
        if (remoteTrackingPort == null || remoteTrackingPort.isEmpty() || !remoteTrackingPort.matches("\\d+")) {
            properties.setProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT, "1099");
            LOG.log(Level.WARNING, "Properties value for \"remoteTracker.port\" was not set. Using a value of \"1099\"");
        }
    }
}
