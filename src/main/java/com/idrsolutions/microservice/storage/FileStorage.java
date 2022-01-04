package com.idrsolutions.microservice.storage;

import com.idrsolutions.microservice.BaseServlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A simple implementation of {@link IStorage} that just uses the local disk to store files
 */
public class FileStorage extends BaseStorage {
    /**
     * @inheritDoc
     */
    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        try {
            final File zipOut = new File(BaseServlet.getOutputPath() + uuid + "/" + fileName + ".zip");
            if (!zipOut.exists()) zipOut.createNewFile();

            try (final FileOutputStream fileWriter = new FileOutputStream(zipOut)) {
                fileWriter.write(fileToUpload);
            }
        } catch(IOException e){
            LOG.severe(e.getMessage());
        }
        return null;
    }
}
