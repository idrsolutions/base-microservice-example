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
package conversion;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.*;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * An extendable base for conversion microservices. Provides general
 * functionality for polling, file upload/download, initial creation of files
 * and UUID's.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    private static final String INPUTPATH = "../docroot/input/";
    private static final String OUTPUTPATH = "../docroot/output/";

    private final ConcurrentHashMap<String, Individual> imap = new ConcurrentHashMap<>();

    private final ExecutorService queue = Executors.newFixedThreadPool(5);

    /**
     * Set an HTTP error code and message to the given response.
     *
     * @param response The response to send to the client
     * @param error the error message to pass in the body of the client
     * @param status the HTTP status to set the response to
     * @throws IOException if the error message cannot be written to the
     * response.
     */
    private static void doError(final HttpServletResponse response, final String error, final int status) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        try (final PrintWriter out = response.getWriter()) {
            out.println("{\"error\":\"" + error + "\"}");
        }
    }

    /**
     * Get request to the servlet. See API docs in respective end servlets for
     * more information.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     * @throws IOException if the error message cannot be written to the
     * response.
     * @see Individual#toJsonString()
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        allowCrossOrigin(response);
        final String uuidStr = request.getParameter("uuid");
        if (uuidStr == null) {
            doError(response, "No uuid provided", 404);
            return;
        }

        final Individual individual = imap.get(uuidStr);
        if (individual == null) {
            doError(response, "Unknown uuid: " + uuidStr, 404);
            return;
        }

        updateProgress(individual);

        response.setContentType("application/json");
        try (final PrintWriter out = response.getWriter()) {
            out.println(individual.toJsonString());
        }
    }

    /**
     * Writes to response object with the communication methods that this server
     * supports.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     * @see BaseServlet#allowCrossOrigin(HttpServletResponse)
     */
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        allowCrossOrigin(response);
    }

    /**
     * Allow cross origin requests according to the CORS standard.
     *
     * @param response
     */
    private void allowCrossOrigin(final HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Access-Control-Allow-Origin");
    }

    /**
     * A post request to the server.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     * @see BaseServlet#convert(Individual, Map, String, String, String, String,
     * String, String)
     */
    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {

        try {
            allowCrossOrigin(response);

            final String uuidStr = UUID.randomUUID().toString();
            final Individual individual = new Individual(uuidStr);

            imap.entrySet().removeIf(entry -> entry.getValue().timestamp < new Date().getTime() - 86400000); // 24 hours

            imap.put(uuidStr, individual);

            individual.isAlive = true;

            final Part filePart = request.getPart("file");
            if (filePart == null) {
                imap.remove(uuidStr);
                doError(response, "Missing file", 400);
                return;
            }

            final byte[] fileBytes = new byte[(int) filePart.getSize()];
            final InputStream fileContent = filePart.getInputStream();
            fileContent.read(fileBytes);
            fileContent.close();

            String fileName = getFileName(filePart);
            if (fileName == null) {
                imap.remove(uuidStr);
                doError(response, "Missing file name", 500); // Would this ever occur?
                return;
            }
            final int extPos = fileName.lastIndexOf('.');
            // Limit filenames to chars allowed in unencoded URLs and Windows filenames for now
            final String fileNameWithoutExt = fileName.substring(0, extPos).replaceAll("[^$\\-_.+!'(),a-zA-Z0-9]", "_");
            final String ext = fileName.substring(extPos + 1);

            fileName = fileNameWithoutExt + '.' + ext;

            final String userInputDirPath = INPUTPATH + uuidStr;
            final File inputDir = new File(userInputDirPath);
            if (!inputDir.exists()) {
                inputDir.mkdirs();
            }

            //Creates the output dir based on session ID
            final String userOutputDirPath = OUTPUTPATH + uuidStr;
            final File outputDir = new File(userOutputDirPath);
            if (outputDir.exists()) {
                deleteFolder(outputDir);
            }
            outputDir.mkdirs();

            final File inputFile = new File(userInputDirPath + "/" + fileName);

            try (final FileOutputStream output = new FileOutputStream(inputFile)) {
                output.write(fileBytes);
                output.flush();
            } catch (final IOException e) {
                e.printStackTrace();
                LOG.severe(e.getMessage());
                imap.remove(uuidStr);
                doError(response, "Internal error", 500); // Failed to save file to disk
                return;
            }

            final Map<String, String[]> parameterMap = request.getParameterMap();
            final String name = fileName;

            queue.submit(() -> {
                try {
                    convert(individual, parameterMap, name, inputDir.getAbsolutePath(),
                            outputDir.getAbsolutePath(), fileNameWithoutExt, ext,
                            getContextURL(request));
                } finally {
                    individual.isAlive = false;
                }
            });

            response.setContentType("application/json");
            try (final PrintWriter out = response.getWriter()) {
                out.println("{" + "\"uuid\":\"" + uuidStr + "\"}");
            }

        } catch (final ServletException | IOException e) {
            e.printStackTrace();
            LOG.severe(e.getMessage());
        }
    }

    /**
     * This method converts a file and writes it to the output directory under
     * the Individuals UUID.
     *
     * @param individual Internal representation of individual who made this
     * request
     * @param parameterMap the map of parameters from the request
     * @param fileName the name of the file on disk
     * @param inputDirectory the directory of the uploaded file
     * @param outputDirectory the directory the converted file should be written
     * to
     * @param fileNameWithoutExt the filename without its extension
     * @param ext the extension of the file name
     * @param contextURL The url from the protocol up to the servlet url
     * pattern.
     */
    abstract void convert(final Individual individual, final Map<String, String[]> parameterMap, final String fileName,
            final String inputDirectory, final String outputDirectory,
            final String fileNameWithoutExt, final String ext, final String contextURL);

    /**
     * Get the filename of the file contained in this request part.
     *
     * @param part the file part from the HTTP request
     * @return the file name or null if it does not exist
     */
    private String getFileName(final Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    /**
     * Gets the full URL before the part containing the path(s) specified in
     * urlPatterns of the servlet.
     *
     * @param request the request from the client
     * @return protocol://servername/contextPath
     */
    protected static String getContextURL(final HttpServletRequest request) {
        final StringBuffer full = request.getRequestURL();
        return full.substring(0, full.length() - request.getServletPath().length());
    }

    /**
     * Get the conversion parameters.
     *
     * @param settings a list of k/v pairs in the form:
     * "key1:val1;key2:val2;etc..."
     * @return a String array in the form [key1, val1, key2, val2, etc...]
     */
    protected static String[] getConversionParams(final String settings) {
        if (settings == null) {
            return null;
        }
        final String[] splits = settings.split(";");
        final String[] result = new String[splits.length * 2];
        int p = 0;
        for (final String set : splits) {
            final String[] ss = set.split(":");
            result[p++] = ss[0];
            result[p++] = ss[1];
        }
        return result;
    }

    /**
     * Delete a folder and all of its contents.
     *
     * @param dirPath the path to the folder to delete
     */
    private static void deleteFolder(final File dirPath) {
        final File[] files = dirPath.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                }
                file.delete();
            }
        }
    }

    /**
     * Update the progress of the conversion.
     *
     * @param individual the individual object for this client
     */
    abstract void updateProgress(final Individual individual);

}
