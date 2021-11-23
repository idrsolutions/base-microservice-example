package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.BaseServlet;

import javax.servlet.ServletContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesHelper {

    public static void loadProperties(final ServletContext servletContext, final InputStream propertiesFile) {

        final Properties properties = readPropertiesFile(propertiesFile);

        //service.concurrentConversion
        loadConcurrentConversions(servletContext, properties);


        //service.inputLocation
        loadInputLocation(servletContext, properties);

        //service.outputLocation

    }

    private static Properties readPropertiesFile(final InputStream propertiesInput) {
        final Properties properties = new Properties();

        try {
            properties.load(propertiesInput);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

    private static void loadConcurrentConversions(final ServletContext servletContext, final Properties properties) {
        String concurrentConversions = properties.getProperty("service.concurrentConversion");
        final int concurrentConversionCount;
        if (concurrentConversions != null && !concurrentConversions.isEmpty() && concurrentConversions.matches("\\d+")) {
            concurrentConversionCount = Integer.parseInt(concurrentConversions);
        } else {
            concurrentConversionCount = Runtime.getRuntime().availableProcessors();
        }

        servletContext.setAttribute("service.concurrentConversion", concurrentConversionCount);
    }

    private static void loadInputLocation(final ServletContext servletContext, final Properties properties) {


        String propertiesInputLocation = properties.getProperty("service.inputLocation");
        final String inputLocation;
        if (propertiesInputLocation != null && !propertiesInputLocation.isEmpty()) {
            inputLocation = propertiesInputLocation;
        } else {
            String userDir = System.getProperty("user.home");
            if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
                userDir += System.getProperty("file.separator");
            }

            inputLocation = userDir + "/.idr/buildvu-microservice/input/";
        }

        servletContext.setAttribute("service.inputLocation", inputLocation);
    }

}
