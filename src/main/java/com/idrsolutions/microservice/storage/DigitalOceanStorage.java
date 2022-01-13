package com.idrsolutions.microservice.storage;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.util.Properties;

/**
 * An implementation of {@link IStorage} that uses DigitalOcean Spaces to store files
 * This reuses {@link AWSStorage} by chaining the endpoint as the API is compatible
 */
public class DigitalOceanStorage extends AWSStorage {
    /**
     * Uses Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
     * @param region The DigitalOcean Region
     * @param bucketName The name of the bucket in AWS that the converted files should be uploaded to
     * @param basePath The path inside the bucket that the converted files should end up in
     */
    DigitalOceanStorage(final String region, final String bucketName, final String basePath) {
        super(AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).build(), bucketName, basePath);
    }

    /**
     * Allows passing any form of AWS authentication
     * @param region The DigitalOcean Region
     * @param credentialsProvider The user credentials for DigitalOcean
     * @param bucketName The name of the bucket in AWS that the converted files should be uploaded to
     * @param basePath The path inside the bucket that the converted files should end up in
     */
    public DigitalOceanStorage(final String region, final AWSCredentialsProvider credentialsProvider, final String bucketName, final String basePath) {
        super(AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).withCredentials(credentialsProvider).build(), bucketName, basePath);
    }

    public DigitalOceanStorage(Properties properties) {
        // storageprovider.do.region
        // storageprovider.do.accesskey
        // storageprovider.do.secretkey
        // storageprovider.do.bucketname
        // storageprovider.do.bucketname

        this(properties.getProperty("storageprovider.do.region"),
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(properties.getProperty("storageprovider.do.accesskey"),
                        properties.getProperty("storageprovider.do.secretkey"))),
                properties.getProperty("storageprovider.do.bucketname"),
                properties.getProperty("storageprovider.do.basepath"));
    }
}
