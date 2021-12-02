package com.idrsolutions.microservice.storage;

import java.io.File;

public interface IStorage {
    /**
     * Places the given data as a file inside the storage
     * @param fileToUpload The file in raw bytes to upload
     * @param fileName The name of the file being uploaded
     * @param uuid The UUID of the file being uploaded
     * @return A URI to access the uploaded file, or null if the upload was unsuccessful
     */
    String put(byte[] fileToUpload, String fileName, String uuid);

    /**
     * Places the given file inside the storage
     * @param fileToUpload The file to upload
     * @param fileName The name of the file being uploaded
     * @param uuid The UUID of the file being uploaded
     * @return A URI to access the uploaded file, or null if the upload was unsuccessful
     */
    String put(File fileToUpload, String fileName, String uuid);
}
