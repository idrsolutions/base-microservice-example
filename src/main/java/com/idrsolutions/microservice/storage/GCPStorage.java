package com.idrsolutions.microservice.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GCPStorage extends BaseStorage {
    Storage storage;

    String bucketName = "";
    String projectID = "";

    public GCPStorage() throws IOException {
        // Will fetch from the "GOOGLE_APPLICATION_CREDENTIALS" environment variable
        storage = StorageOptions.newBuilder().setProjectId(projectID).build().getService();
    }

    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        Blob blob = storage.create(BlobInfo.newBuilder(bucketName, uuid + "/" + fileName).build(), fileToUpload, Storage.BlobTargetOption.detectContentType());
        return blob.signUrl(30, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature()).toString();
    }
}
