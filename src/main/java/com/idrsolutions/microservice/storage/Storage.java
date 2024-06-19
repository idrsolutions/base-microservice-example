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
import java.io.InputStream;

public interface Storage {
    /**
     * Places the given data as a file inside the storage
     * @param fileToUpload The file in raw bytes to upload
     * @param fileName The name of the file being uploaded
     * @param uuid The UUID of the file being uploaded
     * @return A URI to access the uploaded file, or null if the upload was unsuccessful
     */
    String put(final byte[] fileToUpload, final String fileName, final String uuid);

    /**
     * Places the given file inside the storage
     * @param fileToUpload The file to upload
     * @param fileName The name of the file being uploaded
     * @param uuid The UUID of the file being uploaded
     * @return A URI to access the uploaded file, or null if the upload was unsuccessful
     */
    String put(final File fileToUpload, final String fileName, final String uuid);

    String put(final InputStream fileToUpload, long fileSize, final String fileName, final String uuid);
}
