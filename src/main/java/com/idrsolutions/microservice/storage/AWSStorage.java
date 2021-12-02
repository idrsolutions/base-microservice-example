package com.idrsolutions.microservice.storage;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;

/**
 * An implementation of {@link IStorage} that uses AWS S3 to store files
 */
public class AWSStorage extends BaseStorage {
    final AmazonS3 s3Client;

    String bucketName = "idr-pdf-dest";
    String basePath = "";

    protected AWSStorage(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Uses Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
     * @param region The AWS Region
     */
    public AWSStorage(Regions region) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build();
    }

    /**
     * Allows passing any form of AWS authentication
     * @param region The AWS Region
     * @param credentialsProvider The user credentials for AWS
     */
    public AWSStorage(Regions region, AWSCredentialsProvider credentialsProvider) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).withCredentials(credentialsProvider).build();
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)){
            ObjectMetadata metadata = new ObjectMetadata();
            // Assume zip file
            metadata.setContentType("application/zip");
            s3Client.putObject(bucketName, basePath + uuid + "/" + fileName, fileStream, metadata);

            long expTimeMillis = Instant.now().toEpochMilli();
            expTimeMillis += 1000 * 60 * 30;    // 30 Minutes
            Date expiration = new Date(expTimeMillis);

            return s3Client.generatePresignedUrl(bucketName, basePath + uuid + "/" + fileName, expiration).toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
