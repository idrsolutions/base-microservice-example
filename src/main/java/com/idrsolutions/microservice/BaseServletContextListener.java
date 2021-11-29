package com.idrsolutions.microservice;

import com.idrsolutions.microservice.utils.PropertiesHelper;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

        final ServletContext servletContext = servletContextEvent.getServletContext();
        final File externalFile = new File(getConfigPath() + getConfigName());
        try {
            final InputStream propertiesFile;
            if (externalFile.exists()) {
                propertiesFile = new FileInputStream(externalFile.getAbsolutePath());
            } else {
                propertiesFile = servletContextEvent.getServletContext().getResourceAsStream("buildvu-microservice.properties");
            }

            PropertiesHelper.loadProperties(servletContext, propertiesFile);
            propertiesFile.close();

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "IOException thrown when reading properties file", e);
        }


        BaseServlet.setInputPath((String) servletContext.getAttribute("service.inputLocation"));

        final ExecutorService convertQueue = Executors.newFixedThreadPool((Integer) servletContext.getAttribute("service.concurrentConversion"));
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
}
