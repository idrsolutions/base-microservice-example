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

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides static utility method(s) for zipping directories.
 */
public class ZipHelper {

    /**
     * Zip a folder and all its contents to the destination zip.
     * Includes a parent directory within the zip file with the same name as the source folder.
     *
     * @param srcFolder the source folder to zip
     * @param destZipFile the name of the zip file to zip to
     * @throws IOException if there is a problem writing or reading the file
     */
    public static void zipFolder(final String srcFolder, final String destZipFile) throws IOException {
        zipFolder(srcFolder, destZipFile, true);
    }

    /**
     * Zip a folder and all its contents to the destination zip.
     *
     * @param srcFolder the source folder to zip
     * @param destZipFile the name of the zip file to zip to
     * @param createParentDirectoryInZip whether to include a parent directory within the zip file with the same name as the source folder
     * @throws IOException if there is a problem writing or reading the file
     */
    public static void zipFolder(final String srcFolder, final String destZipFile, final boolean createParentDirectoryInZip) throws IOException {
        final File zipOut = new File(destZipFile);
        if (!zipOut.exists()) {
            zipOut.createNewFile();
        }

        try (
                final FileOutputStream fileWriter = new FileOutputStream(zipOut);
                final ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
            addFolderToZip(createParentDirectoryInZip ? new File(srcFolder).getName() + '/' : "", srcFolder, zip);
            zip.flush();
            fileWriter.flush();
        }
    }

    /**
     * Zip a folder and all its contents to the destination zip in memory.
     *
     * @param srcFolder the source folder to zip
     * @param createParentDirectoryInZip whether to include a parent directory within the zip file with the same name as the source folder
     * @return A bytearray containing the zipFile
     * @throws IOException if there is a problem writing or reading the file
     */
    public static byte[] zipFolderInMemory(final String srcFolder, final boolean createParentDirectoryInZip) throws IOException {
        try (ByteArrayOutputStream zipBAOS = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(zipBAOS)) {
            addFolderToZip(createParentDirectoryInZip ? new File(srcFolder).getName() + '/' : "", srcFolder, zip);
            zip.flush();
            return zipBAOS.toByteArray();
        }
    }

    /**
     * Zips the given file and writes it to the ZipOuputSream. If directory is
     * given then calls 
     * {@link ZipHelper#addFolderToZip(String, String, ZipOutputStream) }
     *
     * @param path the current location within the zip file (must include a trailing slash unless the value is empty)
     * @param srcFile the path of the file to zip file
     * @param zip the zip stream to write to
     * @throws IOException if there is a problem while reading the file
     */
    private static void addFileToZip(final String path, final String srcFile, final ZipOutputStream zip) throws IOException {

        final File file = new File(srcFile);
        if (file.isDirectory()) {
            addFolderToZip(path + file.getName() + '/', srcFile, zip);
        } else {
            try (final FileInputStream in = new FileInputStream(srcFile)) {
                final byte[] buf = new byte[1024];
                int len;
                zip.putNextEntry(new ZipEntry(path + file.getName()));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    /**
     * Zips the given folder (including all sub files and writes to ZipOutputStream).
     *
     * @param path the current location within the zip file (must include a trailing slash unless the value is empty)
     * @param srcFolder the path of the folder to add to the zip file
     * @param zip the zip stream to write to
     * @throws IOException if there is a problem while reading the file
     */
    private static void addFolderToZip(final String path, final String srcFolder, final ZipOutputStream zip) throws IOException {
        final File folder = new File(srcFolder);
        final String[] fileList = folder.list();
        if (fileList != null) {
            for (final String fileName : fileList) {
                addFileToZip(path, srcFolder + '/' + fileName, zip);
            }
        }
    }
}
