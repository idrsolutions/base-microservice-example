package com.idrsolutions.microservice.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link IStorage} that uses GCP Cloud Storage to store files
 */
public class GCPStorage extends BaseStorage {
    private final Storage storage;

    private final String projectID;
    private final String bucketName;
    private final String basePath;

    /**
     * Finds the credentials file using the Environment Variable: GOOGLE_APPLICATION_CREDENTIALS
     * @param projectID The project ID Containing the bucket
     * @param bucketName The name of the bucket that the converted files should be uploaded to
     */
    public GCPStorage(final String projectID, final String bucketName, String basePath) {
        // Will fetch from the "GOOGLE_APPLICATION_CREDENTIALS" environment variable
        storage = StorageOptions.newBuilder().setProjectId(projectID).build().getService();
        this.projectID = projectID;
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    /**
     * Initialises with a user provided credentials file
     * @param credentialsPath The path to a file containing the credentials
     * @param projectID The project ID Containing the bucket
     * @param bucketName The name of the bucket that the converted files should be uploaded to
     * @throws IOException if it cannot find or access the credentialsPath
     */
    public GCPStorage(final String credentialsPath, final String projectID, final String bucketName, String basePath) throws IOException {
        GoogleCredentials credentials;

        try (FileInputStream fileStream = new FileInputStream(credentialsPath)) {
            credentials = GoogleCredentials.fromStream(fileStream).createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        }

        storage = StorageOptions.newBuilder().setProjectId(projectID).setCredentials(credentials).build().getService();
        this.projectID = projectID;
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    public GCPStorage(Properties properties) throws IOException {
        // storageprovider.gcp.credentialspath
        // storageprovider.gcp.projectid
        // storageprovider.gcp.bucketname
        this(properties.getProperty("storageprovider.gcp.credentialspath"),
                properties.getProperty("storageprovider.gcp.projectid"),
                properties.getProperty("storageprovider.gcp.bucketname"),
                properties.getProperty("storageprovider.gcp.basepath"));
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(final byte[] fileToUpload, final String fileName, final String uuid) {
        final Blob blob = storage.create(BlobInfo.newBuilder(bucketName, basePath + "/" + uuid + "/" + fileName).build(), fileToUpload, Storage.BlobTargetOption.detectContentType());
        return blob.signUrl(30, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature()).toString();
    }

    @Override
    public String put(InputStream fileToUpload, long fileSize, String fileName, String uuid) {
        try {
            final Blob blob = storage.createFrom(BlobInfo.newBuilder(bucketName, basePath + "/" + uuid + "/" + fileName).build(), fileToUpload, Storage.BlobWriteOption.detectContentType());
            return blob.signUrl(30, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature()).toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
