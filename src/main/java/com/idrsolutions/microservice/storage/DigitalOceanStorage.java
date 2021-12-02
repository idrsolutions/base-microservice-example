package com.idrsolutions.microservice.storage;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

/**
 * An implementation of {@link IStorage} that uses DigitalOcean Spaces to store files
 * This reuses {@link AWSStorage} by chaning the endpoint as the API is compatible
 */
public class DigitalOceanStorage extends AWSStorage {
    /**
     * Uses Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
     * @param region The DigitalOcean Region
     */
    DigitalOceanStorage(String region) {
        super(AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).build());
    }

    /**
     * Allows passing any form of AWS authentication
     * @param region The DigitalOcean Region
     * @param credentialsProvider The user credentials for DigitalOcean
     */
    public DigitalOceanStorage(String region, AWSCredentialsProvider credentialsProvider) {
        super(AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).withCredentials(credentialsProvider).build());
    }
}
