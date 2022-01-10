package com.idrsolutions.microservice;

import com.idrsolutions.microservice.utils.FileDeletionService;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseServletContextListener implements ServletContextListener {

    private static final String CONVERSION_COUNT_PROPERTY_NAME = "conversionThreadCount";
    private static final String DOWNLOAD_COUNT_PROPERTY_NAME = "downloadThreadCount";
    private static final String CALLBACK_COUNT_PROPERTY_NAME = "callbackThreadCount";
    private static final String INPUT_PROPERTY_NAME = "inputPath";
    private static final String OUTPUT_PROPERTY_NAME = "outputPath";
    private static final String INDIVIDUAL_TTL_PROPERTY_NAME = "individualTTL";
    private static final String FILE_DELETION_SERVICE_PROPERTY_NAME = "fileDeletionService";
    private static final String FILE_DELETION_SERVICE_FREQUENCY_PROPERTY_NAME = "fileDeletionService.frequency";

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

        servletContext.setAttribute("properties", propertiesFile);

        final ExecutorService convertQueue = Executors.newFixedThreadPool(Integer.parseInt(propertiesFile.getProperty(CONVERSION_COUNT_PROPERTY_NAME)));
        final ExecutorService downloadQueue = Executors.newFixedThreadPool(Integer.parseInt(propertiesFile.getProperty(DOWNLOAD_COUNT_PROPERTY_NAME)));
        final ScheduledExecutorService callbackQueue = Executors.newScheduledThreadPool(Integer.parseInt(propertiesFile.getProperty(CALLBACK_COUNT_PROPERTY_NAME)));

        servletContext.setAttribute("convertQueue", convertQueue);
        servletContext.setAttribute("downloadQueue", downloadQueue);
        servletContext.setAttribute("callbackQueue", callbackQueue);

        BaseServlet.setInputPath(propertiesFile.getProperty(INPUT_PROPERTY_NAME));
        BaseServlet.setOutputPath(propertiesFile.getProperty(OUTPUT_PROPERTY_NAME));
        BaseServlet.setIndividualTTL(Long.parseLong(propertiesFile.getProperty(INDIVIDUAL_TTL_PROPERTY_NAME)));

        if (Boolean.parseBoolean(propertiesFile.getProperty(FILE_DELETION_SERVICE_PROPERTY_NAME))) {
            servletContext.setAttribute(FILE_DELETION_SERVICE_PROPERTY_NAME, new FileDeletionService(
                    new String[]{
                            propertiesFile.getProperty(INPUT_PROPERTY_NAME), propertiesFile.getProperty(OUTPUT_PROPERTY_NAME)
                    },
                    Long.parseLong(propertiesFile.getProperty(INDIVIDUAL_TTL_PROPERTY_NAME)),
                    Long.parseLong(propertiesFile.getProperty(FILE_DELETION_SERVICE_FREQUENCY_PROPERTY_NAME))
            ));
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        ((ExecutorService) servletContext.getAttribute("convertQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("downloadQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("callbackQueue")).shutdownNow();

        ((FileDeletionService) servletContext.getAttribute(FILE_DELETION_SERVICE_PROPERTY_NAME)).shutdownNow();
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
    }

    private static void validateConversionThreadCount(final Properties properties) {
        final String conversonThreads = properties.getProperty(CONVERSION_COUNT_PROPERTY_NAME);
        if (conversonThreads == null || conversonThreads.isEmpty()) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty(CONVERSION_COUNT_PROPERTY_NAME, "" + availableProcessors);
            final String message = String.format("Properties value for \"conversionThreadCount\" has not been set. Using a value of \"%d\" based on available processors.", availableProcessors);
            LOG.log(Level.INFO, message);
        } else if (!conversonThreads.matches("\\d+") || Integer.parseInt(conversonThreads) == 0) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty(CONVERSION_COUNT_PROPERTY_NAME, "" + availableProcessors);
            final String message = String.format("Properties value for \"conversionThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of \"%d\" based on available processors.", conversonThreads, availableProcessors);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateDownloadThreadCount(final Properties properties) {
        final String downloadThreads = properties.getProperty(DOWNLOAD_COUNT_PROPERTY_NAME);
        if (downloadThreads == null || downloadThreads.isEmpty() || !downloadThreads.matches("\\d+") || Integer.parseInt(downloadThreads) == 0) {
            properties.setProperty(DOWNLOAD_COUNT_PROPERTY_NAME, "5");
            final String message = String.format("Properties value for \"downloadThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of 5.", downloadThreads);
            LOG.log(Level.WARNING, message);
        }
    }

    private static void validateCallbackThreadCount(final Properties properties) {
        final String callbackThreads = properties.getProperty(CALLBACK_COUNT_PROPERTY_NAME);
        if (callbackThreads == null || callbackThreads.isEmpty() || !callbackThreads.matches("\\d+") || Integer.parseInt(callbackThreads) == 0) {
            properties.setProperty(CALLBACK_COUNT_PROPERTY_NAME, "5");
            final String message = String.format("Properties value for \"callbackThreadCount\" was set to \"%s\" but should be a positive integer. Using a value of 5.", callbackThreads);
            LOG.log(Level.WARNING, message);
        }
    }

    private void validateInputPath(final Properties properties) {
        final String inputPath = properties.getProperty(INPUT_PROPERTY_NAME);
        if (inputPath == null || inputPath.isEmpty()) {
            final String inputDir = getConfigPath() + "input";
            properties.setProperty(INPUT_PROPERTY_NAME, inputDir);
            final String message = String.format("Properties value for \"inputPath\" was not set. Using a value of \"%s\"", inputDir);
            LOG.log(Level.WARNING, message);
        } else if (inputPath.startsWith("~")) {
            properties.setProperty(INPUT_PROPERTY_NAME, System.getProperty("user.home") + inputPath.substring(1));
        }
    }

    private void validateOutputPath(final Properties properties) {
        final String outputPath = properties.getProperty(OUTPUT_PROPERTY_NAME);
        if (outputPath == null || outputPath.isEmpty()) {
            final String outputDir = getConfigPath() + "output";
            properties.setProperty(OUTPUT_PROPERTY_NAME, outputDir);
            final String message = String.format("Properties value for \"outputPath\" was not set. Using a value of \"%s\"", outputDir);
            LOG.log(Level.WARNING, message);
        } else if (outputPath.startsWith("~")) {
            properties.setProperty(OUTPUT_PROPERTY_NAME, System.getProperty("user.home") + outputPath.substring(1));
        }
    }

    private void validateIndividualTTL(final Properties properties) {
        final String rawIndividualTTL = properties.getProperty(INDIVIDUAL_TTL_PROPERTY_NAME);
        if (rawIndividualTTL == null || rawIndividualTTL.isEmpty() || !rawIndividualTTL.matches("\\d+")) {
            final String defaultTTL = Long.toString(BaseServlet.getIndividualTTL());
            properties.setProperty(INDIVIDUAL_TTL_PROPERTY_NAME, defaultTTL);
            final String message = String.format("Properties value for \"individualTTL\" was set to \"%s\" but should" +
                    " be a positive long. Using a value of %s.", rawIndividualTTL, defaultTTL);
            LOG.log(Level.WARNING, message);
        }
    }

    private void validateFileDeletionService(final Properties properties) {
        final String fileDeletionService = properties.getProperty(FILE_DELETION_SERVICE_PROPERTY_NAME);
        if (fileDeletionService == null || fileDeletionService.isEmpty() || !Boolean.parseBoolean(fileDeletionService)) {
            properties.setProperty(FILE_DELETION_SERVICE_PROPERTY_NAME, "false");
            if (!"false".equalsIgnoreCase(fileDeletionService)) {
                final String message = String.format("Properties value for \"fileDeletionService\" was set to \"%s\" " +
                        "but should be a boolean. Using a value of false.", fileDeletionService);
                LOG.log(Level.WARNING, message);
            }
        }
    }

    private void validateFileDeletionServiceFrequency(final Properties properties) {
        final String fdsFrequency = properties.getProperty(FILE_DELETION_SERVICE_FREQUENCY_PROPERTY_NAME);
        if (fdsFrequency == null || fdsFrequency.isEmpty() || "0".equals(fdsFrequency) || !fdsFrequency.matches("\\d+")) {
            properties.setProperty(FILE_DELETION_SERVICE_FREQUENCY_PROPERTY_NAME, "5");
            final String message = String.format("Properties value for \"fileDeletionService.frequency\" was set to " +
                    "\"%s\" but should be a positive long. Using a value of 5 Minutes.", fdsFrequency);
            LOG.log(Level.WARNING, message);
        }
    }
}
