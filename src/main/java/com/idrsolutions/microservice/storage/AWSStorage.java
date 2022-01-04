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

    protected final String bucketName;
    protected final String basePath;

    protected AWSStorage(final AmazonS3 s3Client, final String bucketName, final String basePath) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    /**
     * Uses Environment variables: AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
     * @param region The AWS Region
     * @param bucketName The name of the bucket in AWS that the converted files should be uploaded to
     * @param basePath The path inside the bucket that the converted files should end up in
     */
    public AWSStorage(final Regions region, final String bucketName, final String basePath) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build();
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    /**
     * Allows passing any form of AWS authentication
     * @param region The AWS Region
     * @param credentialsProvider The user credentials for AWS
     * @param bucketName The name of the bucket in AWS that the converted files should be uploaded to
     * @param basePath The path inside the bucket that the converted files should end up in
     */
    public AWSStorage(final Regions region, final AWSCredentialsProvider credentialsProvider, final String bucketName, final String basePath) {
        s3Client = AmazonS3ClientBuilder.standard().withRegion(region).withCredentials(credentialsProvider).build();
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(final byte[] fileToUpload, final String fileName, final String uuid) {
        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)){
            final ObjectMetadata metadata = new ObjectMetadata();
            // Assume zip file
            metadata.setContentType("application/zip");
            s3Client.putObject(bucketName, basePath + uuid + "/" + fileName, fileStream, metadata);

            long expTimeMillis = Instant.now().toEpochMilli();
            expTimeMillis += 1000 * 60 * 30;    // 30 Minutes
            final Date expiration = new Date(expTimeMillis);

            return s3Client.generatePresignedUrl(bucketName, basePath + uuid + "/" + fileName, expiration).toString();
        } catch (IOException e) {
            LOG.severe(e.getMessage());
        }
        return null;
    }
}
