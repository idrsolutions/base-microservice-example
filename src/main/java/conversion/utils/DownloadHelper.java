/*
 * Copyright 2018 Patrick Sheppard.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package conversion.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Static collection of methods to help with downloading files from a url.
 */
public class DownloadHelper {
    
    /**
     * Gets array of file bytes from a url.
     *
     * @param strUrl the url to get the file from
     * @return the bytes downloaded from the url, null if no bytes downloaded.
     * @throws IOException when unable to fetch the file
     */
    public static byte[] getFileFromUrl(final String strUrl) throws IOException {

        final int bufferSize = 1024;

        final URL url = new URL(strUrl);
        final BufferedInputStream input = new BufferedInputStream(url.openStream());
        final ByteArrayOutputStream data = new ByteArrayOutputStream();

        final byte[] buffer = new byte[bufferSize];
        int count = 0;

        while ((count = input.read(buffer, 0, bufferSize)) != -1) {
            data.write(buffer, 0, count);
        }

        input.close();
        data.close();

        if (data.size() > 0) {
            return data.toByteArray();
        }

        throw new IOException();
    }

    
    /**
     * Gets array of bytes from url. If after n retries the bytes cannot be
     * retrieved the method returns null.
     *
     * @param url the url to get the file from
     * @param retries the number of retries to attempt before giving up
     * @return bytes downloaded from the url, null on error.
     * @throws IOException when unable to fetch the file
     */
    public static byte[] getFileFromUrl(final String url, int retries) throws IOException {
        while (retries > 0) {
            try {
                byte[] bytes = getFileFromUrl(url);

                if (bytes == null) {
                    throw new IOException();
                }

                return bytes;
            } catch (IOException e) {
                retries--;
            }
        }

        throw new IOException();
    }
    
    /**
     * Attempt to extract a filename from the url.
     *
     * @param url the url to extract the filename from
     * @return the filename if it is possible to extract, null otherwise
     */
    public static String getFileNameFromUrl(String url) {

        // Get rid of parameters.
        int index = url.indexOf("?");
        if (index > 0) {
            url = url.substring(0, index);
        }

        String name = null;

        index = url.lastIndexOf("/") + 1;
        if (index > 0 && index < url.length()) {
            name = url.substring(index, url.length());
        }

        if (name.length() == 0) {
            name = null;
        }

        return name;
    }
    
}
