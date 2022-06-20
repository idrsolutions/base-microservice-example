package com.idrsolutions.microservice.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * An abstract implementation of Storage that pre-implements the File version of {@link #put(File, String, String)} to load the file into memory and put it through {@link #put(byte[], String, String)}
 */
public abstract class BaseStorage implements Storage {
    protected static final Logger LOG = Logger.getLogger(BaseStorage.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public String put(final File fileToUpload, final String fileName, final String uuid) {
        try (FileInputStream fileStream = new FileInputStream(fileToUpload)) {
            return put(fileStream, fileToUpload.length(), fileName, uuid);
        } catch (IOException e) {
            LOG.severe(e.getMessage());
            return null;
        }
    }
}
