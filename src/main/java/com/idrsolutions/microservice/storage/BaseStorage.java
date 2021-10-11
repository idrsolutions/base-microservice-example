package com.idrsolutions.microservice.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class BaseStorage implements IStorage {
    @Override
    public String put(File fileToUpload, String fileName, String uuid) {
        try {
            return put(Files.readAllBytes(fileToUpload.toPath()), fileName, uuid);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
