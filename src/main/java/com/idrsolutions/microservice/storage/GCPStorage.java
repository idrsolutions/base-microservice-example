package com.idrsolutions.microservice.storage;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;

import java.io.*;
import java.nio.file.Files;

public class GCPStorage implements IStorage {
    Storage storage;

    String bucketName = "idr-gcp-storage";
    String projectID = "base-microservice-test";

    public GCPStorage() throws IOException {
        storage = StorageOptions.newBuilder()
                                        .setProjectId(projectID)
                                        .setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream("C:/Users/work/Downloads/base-microservice-test-853ae6798ab9.json")))
                                        .build()
                                        .getService();
    }

    @Override
    public String put(File fileToUpload, String fileName, String uuid) {
        Bucket bucket = storage.get(bucketName, Storage.BucketGetOption.fields((Storage.BucketField.values())));

        try {
            byte[] file = Files.readAllBytes(fileToUpload.toPath());
            Blob blob = bucket.create(uuid + "/" + fileName, file);

            WriteChannel writer = storage.writer();

            writer.write()
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
