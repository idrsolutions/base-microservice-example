/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2021 IDRsolutions
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides static utility method(s) for zipping directories.
 */
public class ZipHelper {

    /**
     * Zip a folder and all its contents to the destination zip.
     *
     * @param srcFolder the source folder to zip
     * @param destZipFile the name of the zip file to zip to
     * @throws IOException if there is a problem writing or reading the file
     */
    public static void zipFolder(final String srcFolder, final String destZipFile) throws IOException {
        final File zipOut = new File(destZipFile);
        if (!zipOut.exists()) {
            zipOut.createNewFile();
        }

        try (
                final FileOutputStream fileWriter = new FileOutputStream(zipOut);
                final ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
            addFolderToZip("", srcFolder, zip);
            zip.flush();
            fileWriter.flush();
        }
    }

    /**
     * Zips the given file and writes it to the ZipOuputSream. If directory is
     * given then calls 
     * {@link ZipHelper#addFolderToZip(String, String, ZipOutputStream) }
     *
     * @param path the path to the file to zip
     * @param srcFile the name of the file to zip
     * @param zip the zip stream to write to
     * @throws IOException if there is a problem while reading the file
     */
    private static void addFileToZip(final String path, final String srcFile, final ZipOutputStream zip) throws IOException {

        final File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            try (final FileInputStream in = new FileInputStream(srcFile)) {
                final byte[] buf = new byte[1024];
                int len;
                zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    /**
     * Zips the given folder (including all sub files and writes to
     * ZipOutputStream.
     *
     * @param path The path to the folder to recursively zip
     * @param srcFolder the name of the folder to zip
     * @param zip the zip stream to write to
     * @throws IOException if there is a problem while reading the file
     */
    private static void addFolderToZip(final String path, final String srcFolder, final ZipOutputStream zip) throws IOException {
        final File folder = new File(srcFolder);
        final String[] fileList = folder.list();
        if (fileList != null) {
            for (final String fileName : fileList) {
                if (path.equals("")) {
                    addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
                } else {
                    addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
                }
            }
        }
    }
}
