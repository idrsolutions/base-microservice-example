package com.idrsolutions.microservice.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * An abstract implementation of IStorage that pre-implements the File version of {@link #put(File, String, String)} to load the file into memory and put it through {@link #put(byte[], String, String)}
 */
public abstract class BaseStorage implements IStorage {
    protected static final Logger LOG = Logger.getLogger(BaseStorage.class.getName());

    /**
     * @inheritDoc
     */
    @Override
    public String put(final File fileToUpload, final String fileName, final String uuid) {
        try {
            return put(Files.readAllBytes(fileToUpload.toPath()), fileName, uuid);
        } catch (IOException e) {
            LOG.severe(e.getMessage());
            return null;
        }
    }
}
