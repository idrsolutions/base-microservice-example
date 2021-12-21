package com.idrsolutions.microservice.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link IStorage} that uses GCP Cloud Storage to store files
 */
public class GCPStorage extends BaseStorage {
    private final Storage storage;

    private final String projectID;
    private final String bucketName;

    /**
     * Finds the credentials file using the Environment Variable: GOOGLE_APPLICATION_CREDENTIALS
     * @param projectID The project ID Containing the bucket
     * @param bucketName The name of the bucket that the converted files should be uploaded to
     */
    public GCPStorage(String projectID, String bucketName) {
        storage = StorageOptions.newBuilder().setProjectId(projectID).build().getService();
        this.projectID = projectID;
        this.bucketName = bucketName;
    }

    /**
     * Initialises with a user provided credentials file
     * @param credentialsPath The path to a file containing the credentials
     * @param projectID The project ID Containing the bucket
     * @param bucketName The name of the bucket that the converted files should be uploaded to
     * @throws IOException if it cannot find or access the credentialsPath
     */
    public GCPStorage(String credentialsPath, String projectID, String bucketName) throws IOException {
        GoogleCredentials credentials;

        try (FileInputStream fileStream = new FileInputStream(credentialsPath)) {
            credentials = GoogleCredentials.fromStream(fileStream).createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        }

        // Will fetch from the "GOOGLE_APPLICATION_CREDENTIALS" environment variable
        storage = StorageOptions.newBuilder().setProjectId(projectID).setCredentials(credentials).build().getService();
        this.projectID = projectID;
        this.bucketName = bucketName;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        Blob blob = storage.create(BlobInfo.newBuilder(bucketName, uuid + "/" + fileName).build(), fileToUpload, Storage.BlobTargetOption.detectContentType());
        return blob.signUrl(30, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature()).toString();
    }
}
