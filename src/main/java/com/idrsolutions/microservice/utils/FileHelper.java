package com.idrsolutions.microservice.utils;

import java.io.File;

public class FileHelper {

    /**
     * Delete a folder and all of its contents.
     *
     * @param dirPath the path to the folder to delete
     */
    public static void deleteFolder(final File dirPath) {
        final File[] files = dirPath.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                }
                file.delete();
            }
        }
        dirPath.delete();
    }

}
