package com.idrsolutions.microservice;

import com.idrsolutions.microservice.utils.DBHandler;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@WebListener
public class BaseServletContextListener implements ServletContextListener {

    private static final int PCOUNT = Runtime.getRuntime().availableProcessors();

    private final ExecutorService convertQueue = Executors.newFixedThreadPool(PCOUNT);
    private final ExecutorService downloadQueue = Executors.newFixedThreadPool(5);
    private final ScheduledExecutorService callbackQueue = Executors.newScheduledThreadPool(5);

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        servletContext.setAttribute("convertQueue", convertQueue);
        servletContext.setAttribute("downloadQueue", downloadQueue);
        servletContext.setAttribute("callbackQueue", callbackQueue);

        // Force the DBHandler to start
        @SuppressWarnings("unused")
        DBHandler instance = DBHandler.INSTANCE;
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        final ServletContext servletContext = servletContextEvent.getServletContext();

        ((ExecutorService) servletContext.getAttribute("convertQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("downloadQueue")).shutdownNow();
        ((ExecutorService) servletContext.getAttribute("callbackQueue")).shutdownNow();
    }
}
