/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2018 IDRsolutions
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
package conversion.utils;

import conversion.Individual;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Static collection of methods to help with sending http requests.
 */
public class HttpHelper {
    
    private static Logger LOG = Logger.getLogger(HttpHelper.class.getName());
    
    /**
     * Tries to send individual json data to the callbackUrl provided by the 
     * user when the file is submitted.
     *
     * @param callbackUrl
     * @param individual
     * @return HTTP response code
     * @throws MalformedURLException
     * @throws IOException
     */
    public static int contactCallback(String callbackUrl, Individual individual) throws MalformedURLException, IOException {
        final URL callback = new URL(callbackUrl);
        
        final HttpURLConnection connection = (HttpURLConnection) callback.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("charset", "utf-8");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        final String outputString = individual.toJsonString();

        final byte[] outputBytes = outputString.getBytes("UTF-8");
        connection.setFixedLengthStreamingMode(outputBytes.length);

        try (OutputStream os = connection.getOutputStream(); OutputStreamWriter wr = new OutputStreamWriter(os)) {
            wr.write(outputString);
            wr.flush();
            wr.close();
            os.close();
        }

        return connection.getResponseCode();
    }
    
    /**
     * This method handles the process of sending the callback data and handles
     * any failed attempts.
     * 
     * @param callbackUrl The URL which will receive the json data
     * @param individual The data to be sent to the callbackUrl
     */
    public static void sendCallback(String callbackUrl, Individual individual) {
        try {
            int resCode = contactCallback(callbackUrl, individual);

            if (resCode != HttpURLConnection.HTTP_OK) {
                LOG.log(Level.WARNING, "Callback URL ''{0}'' returned http code: {1} on attempt no.1", new Object[]{callbackUrl, Integer.toString(resCode)});

                resCode = contactCallback(callbackUrl, individual);

                if (resCode != HttpURLConnection.HTTP_OK) {
                    LOG.log(Level.WARNING, "Callback URL ''{0}'' returned http code: {1} on attempt no.2", new Object[]{callbackUrl, Integer.toString(resCode)});
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            LOG.severe(e.getMessage());
        }
    }
}
