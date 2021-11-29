package com.idrsolutions.microservice.storage;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class DigitalOceanStorage extends AWSStorage {

    /**
     * Uses Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
     * @param region The AWS Region
     */
    DigitalOceanStorage(String region) {
        this.s3Client = AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).build();
    }

    public DigitalOceanStorage(String region, AWSCredentialsProvider credentialsProvider) {
        this.s3Client = AmazonS3ClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("https://" + region + ".digitaloceanspaces.com", region)).withCredentials(credentialsProvider).build();
    }
}
