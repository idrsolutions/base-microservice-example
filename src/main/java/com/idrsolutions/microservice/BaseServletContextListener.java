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
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        ((ExecutorService) servletContext.getAttribute("convertQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("downloadQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("callbackQueue")).shutdownNow();
    }


    protected void validateConfigFileValues(final Properties propertiesFile) {
        //service.concurrentConversion
        validateConcurrentConversions(propertiesFile);
    }

    private static void validateConcurrentConversions(final Properties properties) {
        final String concurrentConversions = properties.getProperty("conversionThreadCount");
        if (concurrentConversions == null || concurrentConversions.isEmpty()) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty("conversionThreadCount", "" + availableProcessors);
            LOG.log(Level.INFO, "Properties value for \"conversionThreadCount\" has not been set. Using a value of " + Runtime.getRuntime().availableProcessors() + " based on available processors.");
        } else {
            if (!concurrentConversions.matches("\\d+") || Integer.parseInt(concurrentConversions) == 0) {
                final int availableProcessors = Runtime.getRuntime().availableProcessors();
                properties.setProperty("conversionThreadCount", "" + availableProcessors);
                LOG.log(Level.WARNING, "Properties value for \"conversionThreadCount\" was set to \"" + concurrentConversions + "\" but should be a positive integer. Using a value of " + availableProcessors + " based on available processors.");
            }
        }
    }


}
