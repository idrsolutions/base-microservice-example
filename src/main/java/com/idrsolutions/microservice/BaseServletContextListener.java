package com.idrsolutions.microservice;

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

    private static final Logger LOG = Logger.getLogger(BaseServletContextListener.class.getName());

    public abstract String getConfigPath();

    public abstract String getConfigName();

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {

        final Properties propertiesFile = new Properties();
        final ServletContext servletContext = servletContextEvent.getServletContext();
        final File externalFile = new File(getConfigPath() + getConfigName());

        try(InputStream intPropertiesFile = BaseServletContextListener.class.getResourceAsStream("/" + getConfigName())) {
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

        final ExecutorService convertQueue = Executors.newFixedThreadPool(Integer.parseInt(propertiesFile.getProperty("conversionThreadCount")));
        final ExecutorService downloadQueue = Executors.newFixedThreadPool(5);
        final ScheduledExecutorService callbackQueue = Executors.newScheduledThreadPool(5);

        servletContext.setAttribute("convertQueue", convertQueue);
        servletContext.setAttribute("downloadQueue", downloadQueue);
        servletContext.setAttribute("callbackQueue", callbackQueue);

        BaseServlet.setInputPath(propertiesFile.getProperty("inputPath"));
        BaseServlet.setOutputPath(propertiesFile.getProperty("outputPath"));
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
        final String conversonThreads = properties.getProperty("conversionThreadCount");
        if (conversonThreads == null || conversonThreads.isEmpty()) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty("conversionThreadCount", "" + availableProcessors);
            LOG.log(Level.INFO, "Properties value for \"conversionThreadCount\" has not been set. Using a value of " + Runtime.getRuntime().availableProcessors() + " based on available processors.");
        } else if (!conversonThreads.matches("\\d+") || Integer.parseInt(conversonThreads) == 0) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty("conversionThreadCount", "" + availableProcessors);
            LOG.log(Level.WARNING, "Properties value for \"conversionThreadCount\" was set to \"" + conversonThreads + "\" but should be a positive integer. Using a value of " + availableProcessors + " based on available processors.");
        }
    }

    private static void validateDownloadThreadCount(final Properties properties) {
        final String downloadThreads = properties.getProperty("downloadThreadCount");
        if (downloadThreads == null || downloadThreads.isEmpty() || !downloadThreads.matches("\\d+") || Integer.parseInt(downloadThreads) == 0) {
            properties.setProperty("downloadThreadCount", "" + downloadThreads);
            LOG.log(Level.WARNING, "Properties value for \"downloadThreadCount\" was set to \"" + downloadThreads + "\" but should be a positive integer. Using a value of 5.");
        }
    }

    private static void validateCallbackThreadCount(final Properties properties) {
        final String callbackThreads = properties.getProperty("callbackThreadCount");
        if (callbackThreads == null || callbackThreads.isEmpty() || !callbackThreads.matches("\\d+") || Integer.parseInt(callbackThreads) == 0) {
            properties.setProperty("callbackThreadCount", "5");
            LOG.log(Level.WARNING, "Properties value for \"callbackThreadCount\" was set to \"" + callbackThreads + "\" but should be a positive integer. Using a value of 5.");
        }
    }

    private static void validateInputPath(final Properties properties) {
        final String inputPath = properties.getProperty("inputPath");
        if (inputPath == null || inputPath.isEmpty()) {
            String inputDir = System.getProperty("user.home");
            if (!inputDir.endsWith("/") && !inputDir.endsWith("\\")) {
                inputDir += System.getProperty("file.separator");
            }
            inputDir += "/.idr/buildvu-microservice/input/";
            properties.setProperty("inputPath", inputDir);
            LOG.log(Level.WARNING, "Properties value for \"inputPath\" was not set. Using a value of \"" + inputDir + "\"");
        } else if (inputPath.startsWith("~")) {
            properties.setProperty("inputPath", System.getProperty("user.home") + inputPath.substring(1));
        }
    }

    private static void validateOutputPath(final Properties properties) {
        final String outputPath = properties.getProperty("outputPath");
        if (outputPath == null || outputPath.isEmpty()) {
            String outputDir = System.getProperty("user.home");
            if (!outputDir.endsWith("/") && !outputDir.endsWith("\\")) {
                outputDir += System.getProperty("file.separator");
            }
            outputDir += "/.idr/buildvu-microservice/input/";
            properties.setProperty("outputPath", outputDir);
            LOG.log(Level.WARNING, "Properties value for \"outputPath\" was not set. Using a value of \"" + outputDir + "\"");
        } else if (outputPath.startsWith("~")) {
            properties.setProperty("outputPath", System.getProperty("user.home") + outputPath.substring(1));
        }
    }


}
