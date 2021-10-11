package com.idrsolutions.microservice.storage;

import com.idrsolutions.microservice.BaseServlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileStorage extends BaseStorage {
    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        try {
            final File zipOut = new File(BaseServlet.getOutputPath() + uuid + "/" + fileName + ".zip");
            if (!zipOut.exists()) {
                zipOut.createNewFile();
            }

            try (final FileOutputStream fileWriter = new FileOutputStream(zipOut)) {
                fileWriter.write(fileToUpload);
            }
        } catch(IOException e){
            e.printStackTrace();
        }
        return null;
    }
}
