/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
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
