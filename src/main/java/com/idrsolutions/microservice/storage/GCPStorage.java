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
    final Storage storage;

    final String bucketName = "";
    final String projectID = "";

    /**
     * Finds the credentials file using the Environment Variable: GOOGLE_APPLICATION_CREDENTIALS
     */
    public GCPStorage() {
        storage = StorageOptions.newBuilder().setProjectId(projectID).build().getService();
    }

    /**
     * Initialises with a user provided credentials file
     * @param credentialsPath The path to a file containing the credentials
     * @throws IOException if it cannot find or access the credentialsPath
     */
    public GCPStorage(String credentialsPath) throws IOException {
        GoogleCredentials credentials;

        try (FileInputStream fileStream = new FileInputStream(credentialsPath)) {
            credentials = GoogleCredentials.fromStream(fileStream).createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        }

        // Will fetch from the "GOOGLE_APPLICATION_CREDENTIALS" environment variable
        storage = StorageOptions.newBuilder().setProjectId(projectID).setCredentials(credentials).build().getService();
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
