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
package com.idrsolutions.microservice.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static collection of methods to help with sending http requests.
 */
public class HttpHelper {

    private final static Logger LOG = Logger.getLogger(HttpHelper.class.getName());
    public static int MaxRetries = 3;

    /**
     * Tries to send json data to the callbackUrl provided by the user when the
     * file is submitted.
     *
     * @param callbackUrl The URL which will receive the json data
     * @param jsonData The data to be sent to the callbackUrl
     * @return HTTP response code
     * @throws MalformedURLException if URL provided is malformed
     * @throws IOException if there is an issue sending the json data
     */
    public static int contactCallback(final String callbackUrl, final String jsonData) throws MalformedURLException, IOException {
        final URL callback = new URL(callbackUrl);

        final HttpURLConnection connection = (HttpURLConnection) callback.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("charset", "utf-8");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        final byte[] outputBytes = jsonData.getBytes("UTF-8");
        connection.setFixedLengthStreamingMode(outputBytes.length);

        try (OutputStream os = connection.getOutputStream(); OutputStreamWriter wr = new OutputStreamWriter(os)) {
            wr.write(jsonData);
        }

        return connection.getResponseCode();
    }

    /**
     * This method handles the process of sending the callback data and handles
     * any failed attempts. It will retry once every 10 seconds until it reaches
     * the MaxRetries, this value is set to 3 by default.
     *
     * @param callbackUrl The URL which will receive the json data
     * @param jsonData The data to be sent to the callbackUrl
     * @param ses The executor to add in delays between url calls
     * @param currentRetries The current count of retries
     */
    public static void sendCallback(final String callbackUrl, final String jsonData, final ScheduledExecutorService ses, final int currentRetries) {
        try {
            int resCode = contactCallback(callbackUrl, jsonData);

            if (resCode != HttpURLConnection.HTTP_OK) {
                LOG.log(Level.WARNING, "Callback URL ''{0}'' returned http code: {1} on attempt no.{2}", new Object[]{callbackUrl, Integer.toString(resCode), currentRetries});

                if (currentRetries < MaxRetries) {
                    ses.schedule(() -> sendCallback(callbackUrl, jsonData, ses, currentRetries + 1), 10, TimeUnit.SECONDS);
                }
            }
        } catch (IOException e) {
            LOG.severe(e.getMessage());
        }
    }
}
