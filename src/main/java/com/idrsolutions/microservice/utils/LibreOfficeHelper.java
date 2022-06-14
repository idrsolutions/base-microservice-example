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

    public enum Result {
        SUCCESS(0),
        TIMEOUT(1050),
        ERROR(1070);

        final int code;

        Result(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

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
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder(sofficePath,
                "-env:UserInstallation=file:///" + uniqueLOProfile + '/',
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
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

    /**
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param uuid The uuid of the conversion on which to set the error if one occurs
     * @return 0 if successful, 1050 if libreoffice timed out, or 1070 if libreoffice has an internal error
     * occurs
     */
    public static Result convertDocToPDF(final String sofficePath, final File file, final String uuid) {
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder(sofficePath,
                "-env:UserInstallation=file:///" + uniqueLOProfile,
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroy();
                final long fileSize = file.length();
                final String fileSizeString;
                if (fileSize < 1_000) {
                    fileSizeString = fileSize + " bytes";
                } else {
                    if (fileSize < 1_000_000) {
                        fileSizeString = String.format("%.2f KB", (fileSize / 1_000f));
                    } else {
                        fileSizeString = String.format("%.2f MB", (fileSize / 1_000_000f));
                    }
                }
                LOG.log(Level.SEVERE, "LibreOffice timed out on " + uuid + " with filesize: " + fileSizeString); // soffice location may need to be added to the path
                return Result.TIMEOUT;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Exception thrown when converting with LibreOffice", e); // soffice location may need to be added to the path
            return Result.ERROR;
        } finally {
            FileHelper.deleteFolder(new File(uniqueLOProfile));
        }
        return Result.SUCCESS;
    }
}
