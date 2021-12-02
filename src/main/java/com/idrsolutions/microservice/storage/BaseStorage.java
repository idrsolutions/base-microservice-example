package com.idrsolutions.microservice.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * An abstract implementation of IStorage that pre-implements the File version of {@link #put(File, String, String)} to load the file into memory and put it through {@link #put(byte[], String, String)}
 */
public abstract class BaseStorage implements IStorage {
    /**
     * @inheritDoc
     */
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
