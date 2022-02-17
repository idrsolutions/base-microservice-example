package com.idrsolutions.microservice;

import com.idrsolutions.microservice.storage.Storage;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseServletContextListener implements ServletContextListener {

    protected static final String KEY_PROPERTIES = "properties";
    protected static final String KEY_PROPERTY_LIBRE_OFFICE = "libreOfficePath";
    private static final String KEY_PROPERTY_CONVERSION_COUNT = "conversionThreadCount";
    private static final String KEY_PROPERTY_DOWNLOAD_COUNT = "downloadThreadCount";
    private static final String KEY_PROPERTY_CALLBACK_COUNT = "callbackThreadCount";
    private static final String KEY_PROPERTY_INPUT_PATH = "inputPath";
    protected static final String KEY_PROPERTY_OUTPUT_PATH = "outputPath";

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
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        ((ExecutorService) servletContext.getAttribute("convertQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("downloadQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("callbackQueue")).shutdownNow();
    }

    protected void validateConfigFileValues(final Properties propertiesFile) {
        validateConversionThreadCount(propertiesFile);
        validateDownloadThreadCount(propertiesFile);
        validateCallbackThreadCount(propertiesFile);
        validateInputPath(propertiesFile);
        validateOutputPath(propertiesFile);
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
}
