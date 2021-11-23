package com.idrsolutions.microservice;

import com.idrsolutions.microservice.utils.PropertiesHelper;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class BaseServletContextListener implements ServletContextListener {

    InputStream propertiesFile;

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {

        final ServletContext servletContext = servletContextEvent.getServletContext();

        PropertiesHelper.loadProperties(servletContext, propertiesFile);

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
