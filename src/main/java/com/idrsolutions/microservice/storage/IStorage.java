package com.idrsolutions.microservice.storage;

import java.io.File;

public interface IStorage {
    String put(byte[] fileToUpload, String fileName, String uuid);
    String put(File fileToUpload, String fileName, String uuid);
}
