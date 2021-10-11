package com.idrsolutions.microservice.storage;

import java.io.File;

public interface IStorage {
    void put(File fileToUpload, String fileName, String UUID);
}
