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

import conversion.utils.DownloadHelper;
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

    private static final int NUM_DOWNLOAD_RETRIES = 2;

    private final ConcurrentHashMap<String, Individual> imap = new ConcurrentHashMap<>();

    private final ExecutorService convertQueue = Executors.newFixedThreadPool(5);
    private final ExecutorService downloadQueue = Executors.newFixedThreadPool(5);

    /**
     * Set an HTTP error code and message to the given response.
     *
     * @param response The response to send to the client
     * @param error the error message to pass in the body of the client
     * @param status the HTTP status to set the response to
     * @throws IOException if the error message cannot be written to the
     * response.
     */
    private static void doError(final HttpServletResponse response, final String error, final int status) {
        response.setContentType("application/json");
        response.setStatus(status);
        try (final PrintWriter out = response.getWriter()) {
            out.println("{\"error\":\"" + error + "\"}");
        } catch (final IOException e) {
            e.printStackTrace();
            LOG.severe(e.getMessage());
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
     * @param response the response object to the request from the client
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
     * @see BaseServlet#convert(Individual, Map, File, File, String)
     */
    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {

        allowCrossOrigin(response);

        final String uuidStr = UUID.randomUUID().toString();
        final Individual individual = new Individual(uuidStr);

        imap.entrySet().removeIf(entry -> entry.getValue().timestamp < new Date().getTime() - 86400000); // 24 hours

        final String inputType = request.getParameter("input");
        if (inputType == null) {
            doError(response, "Missing input type", 400);
            return;
        } else {

            switch (inputType) {
                case "upload":
                    if (!handleFileFromRequest(individual, request, response)) {
                        return;
                    }
                    break;

                case "download":
                    if (!handleFileFromUrl(individual, request, response)) {
                        return;
                    }
                    break;

                default:
                    doError(response, "Unrecognised input type", 400);
                    return;
            }
        }

        imap.put(uuidStr, individual);

        response.setContentType("application/json");
        try (final PrintWriter out = response.getWriter()) {
            out.println("{" + "\"uuid\":\"" + uuidStr + "\"}");
        } catch (final IOException e) {
            e.printStackTrace();
            LOG.severe(e.getMessage());
        }
    }

    /**
     * Sanitize the file name by removing all none url/filepath friendly
     * characters.
     *
     * @param fileName the filename to sanitize
     * @return the sanitized filename
     */
    private static String sanitizeFileName(final String fileName) {
        final int extPos = fileName.lastIndexOf('.');
        // Limit filenames to chars allowed in unencoded URLs and Windows filenames for now
        final String fileNameWithoutExt = fileName.substring(0, extPos).replaceAll("[^$\\-_.+!'(),a-zA-Z0-9]", "_");
        final String ext = fileName.substring(extPos + 1);

        return fileNameWithoutExt + '.' + ext;
    }

    /**
     * Create the input directory for the clients file.
     *
     * @param uuid the uuid to use to create the directory
     * @return the input directory
     */
    private static File createInputDirectory(final String uuid) {
        final String userInputDirPath = INPUTPATH + uuid;
        final File inputDir = new File(userInputDirPath);
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }
        return inputDir;
    }

    /**
     * Create the output directory to store the converted pdf at.
     *
     * @param uuid the uuid to use to create the output directory
     * @return the output directory
     */
    private static File createOutputDirectory(final String uuid) {
        final String userOutputDirPath = OUTPUTPATH + uuid;
        final File outputDir = new File(userOutputDirPath);
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdirs();
        return outputDir;
    }

    /**
     * Handle and convert file uploaded in the request.
     * <p>
     * This method blocks until the file is initially processed and exists when
     * the conversion begins.
     *
     * @param individual the individual associated with this conversion
     * @param request the request for this conversion
     * @param response the response object for the request
     * @return true on success, false on failure
     */
    private boolean handleFileFromRequest(final Individual individual, final HttpServletRequest request, final HttpServletResponse response) {
        final Part filePart;
        try {
            filePart = request.getPart("file");
        } catch (IOException e) {
            doError(response, "Error handling file", 500);
            return false;
        } catch (ServletException e) {
            doError(response, "Missing file", 400);
            return false;
        }

        if (filePart == null) {
            doError(response, "Missing file", 400);
            return false;
        }

        final String originalFileName = getFileName(filePart);
        if (originalFileName == null) {
            doError(response, "Missing file name", 500); // Would this ever occur?
            return false;
        }

        final File inputFile;
        try {
            final InputStream fileContent = filePart.getInputStream();
            final byte[] fileBytes = new byte[(int) filePart.getSize()];
            fileContent.read(fileBytes);
            fileContent.close();
            inputFile = outputFile(originalFileName, individual, fileBytes);
        } catch (final IOException e) {
            e.printStackTrace();
            LOG.severe(e.getMessage());
            doError(response, "Internal error", 500); // Failed to save file to disk
            return false;
        }

        final File outputDir = createOutputDirectory(individual.uuid);

        addToQueue(individual, request.getParameterMap(), inputFile, outputDir, getContextURL(request));

        return true;
    }

    /**
     * Handle and convert a file located at a given url.
     * <p>
     * This method does not block when attempting to download the file from the 
     * url.
     *
     * @param individual the individual associated with this conversion
     * @param request the request for this conversion
     * @param response the response object for the request
     * @return true on initial success (url has been provided)
     */
    private boolean handleFileFromUrl(final Individual individual, final HttpServletRequest request, final HttpServletResponse response) {

        String url = request.getParameter("url");
        if (url == null) {
            doError(response, "No url given", 400);
            return false;
        }
        // This does not need to be asynchronous
        String filename = DownloadHelper.getFileNameFromUrl(url);
        // In case a filename cannot be parsed from the url.
        if (filename == null) {
            filename = "document.pdf";
        }

        // To allow use in lambda function.
        final String finalFilename = filename;
        final String contextUrl = getContextURL(request);
        final Map<String, String[]> parameterMap = request.getParameterMap();

        downloadQueue.submit(() -> {
            File inputFile;
            try {
                byte[] fileBytes = DownloadHelper.getFileFromUrl(url, NUM_DOWNLOAD_RETRIES);
                inputFile = outputFile(finalFilename, individual, fileBytes);
            } catch (IOException e) {
                individual.doError(1200);
                return;
            }

            final File outputDir = createOutputDirectory(individual.uuid);
            addToQueue(individual, parameterMap, inputFile, outputDir, contextUrl);
        });

        return true;
    }

    /**
     * Add a conversion task to the thread queue.
     *
     * @param individual the individual belonging to this conversion
     * @param params the parameter map from the request
     * @param inputFile the input file to convert
     * @param outputDir the output directory to convert to
     * @param contextUrl the context url of the servlet
     */
    private void addToQueue(final Individual individual, final Map<String, String[]> params, final File inputFile,
            final File outputDir, final String contextUrl) {
        convertQueue.submit(() -> {
            try {
                convert(individual, params, inputFile, outputDir, contextUrl);
            } finally {
                individual.isAlive = false;
            }
        });
    }

    /**
     * This method converts a file and writes it to the output directory under
     * the Individual's UUID.
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
    abstract void convert(Individual individual, Map<String, String[]> params,
            File inputFile, File outputDir, String contextUrl);

    /**
     * Write the given file bytes to the output directory under filename.
     *
     * @param filename the filename to output to
     * @param individual the individual that began the conversion request
     * @param fileBytes the bytes to be written.
     * @return the created file
     * @throws IOException on file not being writable
     */
    private File outputFile(String filename, Individual individual, byte[] fileBytes) throws IOException {
        final File inputDir = createInputDirectory(individual.uuid);
        final File inputFile = new File(inputDir, sanitizeFileName(filename));

        try (final FileOutputStream output = new FileOutputStream(inputFile)) {
            output.write(fileBytes);
            output.flush();
        }

        return inputFile;
    }

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
}
