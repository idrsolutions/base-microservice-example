package com.idrsolutions.microservice.storage;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;

import java.io.*;
import java.nio.file.Files;
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
