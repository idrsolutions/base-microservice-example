/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2022 IDRsolutions
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
package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.db.DBHandler;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LibreOfficeHelper {

    private static final Logger LOG = Logger.getLogger(LibreOfficeHelper.class.getName());
    private static final String TEMP_DIR;

    static {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (!tempDir.endsWith("/") && !tempDir.endsWith("\\")) {
            tempDir += System.getProperty("file.separator");
        }
        TEMP_DIR = tempDir;
    }

    /**
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param uuid The uuid of the conversion on which to set the error if one occurs
     * @return true on success, false on failure
     * occurs
     */
    public static boolean convertToPDF(final String sofficePath, final File file, final String uuid) {
        return convertToPDF(sofficePath, file, uuid, 60000);
    }

    /**
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param uuid The uuid of the conversion on which to set the error if one occurs
     * @param timeoutDuration The timeout duration for libreoffice conversions in milliseconds
     * @return true on success, false on failure
     * occurs
     */
    public static boolean convertToPDF(final String sofficePath, final File file, final String uuid, final long timeoutDuration) {
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder(sofficePath,
                "-env:UserInstallation=file://" + uniqueLOProfile,
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(timeoutDuration, TimeUnit.MILLISECONDS)) {
                process.destroy();
                DBHandler.getInstance().setError(uuid, 1050, "Libreoffice timed out after 1 minute");
                return false;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Exception thrown when converting with LibreOffice", e); // soffice location may need to be added to the path
            DBHandler.getInstance().setError(uuid, 1070, "Internal error processing file");
            return false;
        } finally {
            FileHelper.deleteFolder(new File(uniqueLOProfile));
        }
        return true;
    }

}
