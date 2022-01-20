package com.idrsolutions.microservice;

import com.amazonaws.regions.Regions;
import com.oracle.bmc.Region;

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

        validateRemoteStorage(propertiesFile);
    }

    private void validateRemoteStorage(Properties propertiesFile) {
        final String storageProvider = propertiesFile.getProperty("storageprovider");
        if (storageProvider == null) {
            return;
        }

        String error = "";
        switch (storageProvider) {
            case "":
                break;

            case "AWS":
                error += validateAWS(propertiesFile);
                break;

            case "DigitalOcean":
                error += validateDigitalOcean(propertiesFile);
                break;

            case "Azure":
                error += validateAzure(propertiesFile);
                break;

            case "GCP":
                error += validateGCP(propertiesFile);
                break;

            case "Oracle":
                error += validateOracle(propertiesFile);
                break;

            default:
                error += "Unknown storage option, available options are:\n" +
                        "AWS\n" +
                        "DigitalOcean\n" +
                        "Azure\n" +
                        "GCP\n" +
                        "Oracle";
        }

        if (!error.isEmpty()) {
            throw new IllegalStateException("Errors found in remote storage config:\n" + error);
        }
    }

    private String validateAWS(Properties propertiesFile) {
        String error = "";

        // storageprovider.aws.region
        String region = propertiesFile.getProperty("storageprovider.aws.region");
        if (region == null || region.isEmpty()) {
            error += "You must set storageprovider.aws.region to the name of an aws region, Eg eu-west-1\n";
        } else {
            try {
                Regions.fromName(region);
            } catch (IllegalArgumentException e) {
                error += "storageprovider.aws.region has been set to an unknown region, please check you have entered the region correctly\n";
            }
        }

        // storageprovider.aws.accesskey
        String accessKey = propertiesFile.getProperty("storageprovider.aws.accesskey");
        if (accessKey == null || accessKey.isEmpty()) {
            error += "storageprovider.aws.accesskey must have a value\n";
        }

        // storageprovider.aws.secretkey
        String secretKey = propertiesFile.getProperty("storageprovider.aws.secretkey");
        if (accessKey == null || accessKey.isEmpty()) {
            error += "storageprovider.aws.secretkey must have a value\n";
        }

        // storageprovider.aws.bucketname
        String bucketName = propertiesFile.getProperty("storageprovider.aws.bucketname");
        if (accessKey == null || accessKey.isEmpty()) {
            error += "storageprovider.aws.bucketname must have a value\n";
        }

        return error;
    }

    private String validateDigitalOcean(Properties propertiesFile) {
        String error = "";

        // storageprovider.do.region
        String region = propertiesFile.getProperty("storageprovider.do.region");
        if (region == null || region.isEmpty()) {
            error += "storageprovider.do.region must have a value\n";
        }

        // storageprovider.do.accesskey
        String accesskey = propertiesFile.getProperty("storageprovider.do.accesskey");
        if (accesskey == null || accesskey.isEmpty()) {
            error += "storageprovider.do.accesskey must have a value\n";
        }

        // storageprovider.do.secretkey
        String secretkey = propertiesFile.getProperty("storageprovider.do.secretkey");
        if (secretkey == null || secretkey.isEmpty()) {
            error += "storageprovider.do.secretkey must have a value\n";
        }

        // storageprovider.do.bucketname
        String bucketname = propertiesFile.getProperty("storageprovider.do.bucketname");
        if (bucketname == null || bucketname.isEmpty()) {
            error += "storageprovider.do.bucketname must have a value\n";
        }

        return error;
    }

    private String validateAzure(Properties propertiesFile) {
        String error = "";

        // storageprovider.azure.accountname
        String accountname = propertiesFile.getProperty("storageprovider.azure.accountname");
        if (accountname == null || accountname.isEmpty()) {
            error += "storageprovider.azure.accountname must have a value\n";
        }

        // storageprovider.azure.accountkey
        String accountkey = propertiesFile.getProperty("storageprovider.azure.accountkey");
        if (accountkey == null || accountkey.isEmpty()) {
            error += "storageprovider.azure.accountkey must have a value\n";
        }

        // storageprovider.azure.containername
        String containername = propertiesFile.getProperty("storageprovider.azure.containername");
        if (containername == null || containername.isEmpty()) {
            error += "storageprovider.azure.containername must have a value\n";
        }

        return error;
    }

    private String validateGCP(Properties propertiesFile) {
        String error = "";

        // storageprovider.gcp.credentialspath
        String credentialspath = propertiesFile.getProperty("storageprovider.gcp.credentialspath");
        if (credentialspath == null || credentialspath.isEmpty()) {
            error += "storageprovider.gcp.credentialspath must have a value\n";
        } else {
            File credentialsFile = new File(credentialspath);
            if (!credentialsFile.exists() || !credentialsFile.isFile() || !credentialsFile.canRead()) {
                error += "storageprovider.gcp.credentialspath must point to a valid credentials file that can be accessed";
            }
        }

        // storageprovider.gcp.projectid
        String projectid = propertiesFile.getProperty("storageprovider.gcp.projectid");
        if (projectid == null || projectid.isEmpty()) {
            error += "storageprovider.gcp.projectid must have a value\n";
        }

        // storageprovider.gcp.bucketname
        String bucketname = propertiesFile.getProperty("storageprovider.gcp.bucketname");
        if (bucketname == null || bucketname.isEmpty()) {
            error += "storageprovider.gcp.bucketname must have a value\n";
        }

        return error;
    }



    private String validateOracle(Properties propertiesFile) {
        String error = "";

        // storageprovider.oracle.ociconfigfilepath
        String ociconfigfilepath = propertiesFile.getProperty("storageprovider.oracle.ociconfigfilepath");
        if (ociconfigfilepath == null || ociconfigfilepath.isEmpty()) {
            error += "storageprovider.oracle.ociconfigfilepath must have a value\n";
        } else {
            File configFile = new File(ociconfigfilepath);
            if (!configFile.exists() || !configFile.isFile() || !configFile.canRead()) {
                error += "storageprovider.oracle.ociconfigfilepath must point to a valid config file that can be accessed";
            }
        }

        // storageprovider.oracle.region
        String region = propertiesFile.getProperty("storageprovider.oracle.region");
        if (region == null || region.isEmpty()) {
            error += "storageprovider.oracle.region must have a value\n";
        } else {
            try {
                Region.fromRegionId(region);
            } catch (IllegalArgumentException e) {
                error += "storageprovider.oracle.region has been set to an unknown region, please check you have entered the region correctly\n";
            }
        }

        // storageprovider.oracle.namespace
        String namespace = propertiesFile.getProperty("storageprovider.oracle.namespace");
        if (namespace == null || namespace.isEmpty()) {
            error += "storageprovider.oracle.namespace must have a value\n";
        }

        // storageprovider.oracle.bucketname
        String bucketname = propertiesFile.getProperty("storageprovider.oracle.bucketname");
        if (bucketname == null || bucketname.isEmpty()) {
            error += "storageprovider.oracle.bucketname must have a value\n";
        }

        return error;
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
