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
package com.idrsolutions.microservice;

import com.idrsolutions.microservice.db.DBHandler;
import com.idrsolutions.microservice.utils.DownloadHelper;
import com.idrsolutions.microservice.utils.FileHelper;
import com.idrsolutions.microservice.utils.HttpHelper;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;
import javax.naming.SizeLimitExceededException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An extendable base for conversion microservices. Provides general
 * functionality for polling, file upload/download, initial creation of files
 * and UUID's.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    protected static final String USER_HOME;

    static {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        USER_HOME = userDir;
    }

    private static String INPUTPATH = USER_HOME + ".idr/input/";
    private static String OUTPUTPATH = USER_HOME + ".idr/output/";

    private static long individualTTL = 86400000L; // 24 hours

    private static final int NUM_DOWNLOAD_RETRIES = 2;


    /**
     * Get the location where input files is stored
     * @return inputPath the path where input files is stored
     */
    public static String getInputPath() {
        return INPUTPATH;
    }

    /**
     * Get the location where the converter output is stored
     *
     * @return outputPath the path where output files is stored
     */
    public static String getOutputPath() {
        return OUTPUTPATH;
    }

    /**
     * Get the time to live of individuals on the server (The duration that the 
     * information of an individual is kept on the server)
     *
     * @return individualTTL the time to live of an individual
     */
    public static long getIndividualTTL() {
        return individualTTL;
    }

    /**
     * Set the location where input files is stored
     *
     * @param inputPath the path where input files is stored
     */
    public static void setInputPath(final String inputPath) {
        INPUTPATH = inputPath;
    }

    /**
     * Set the location where the converter output is stored
     *
     * @param outputPath the path where output files is stored
     */
    public static void setOutputPath(final String outputPath) {
        OUTPUTPATH = outputPath;
    }

    /**
     * Set the time to live of individuals on the server (The duration that the 
     * information of an individual is kept on the server)
     *
     * @param ttlDuration the time to live of an individual
     */
    public static void setIndividualTTL(final long ttlDuration) {
        individualTTL = ttlDuration;
    }

    /**
     * Set an HTTP error code and message to the given response.
     *
     * @param request the HttpServletRequest object to reply to
     * @param response the HttpServletResponse object on which to send the
     * response
     * @param error the error message to pass in the body of the client
     * @param status the HTTP status to set the response to response.
     */
    protected static void doError(final HttpServletRequest request, final HttpServletResponse response, final String error, final int status) {
        response.setStatus(status);
        sendResponse(request, response, Json.createObjectBuilder().add("error", error).build().toString());
    }

    /**
     * Send a JSON response
     *
     * @param request the HttpServletRequest object to reply to
     * @param response the HttpServletResponse object on which to send the
     * response
     * @param content the JSON response to send
     */
    private static void sendResponse(final HttpServletRequest request, final HttpServletResponse response, final String content) {
        allowCrossOrigin(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (final PrintWriter out = response.getWriter()) {
            out.println(content);
        } catch (final IOException e) {
            LOG.log(Level.SEVERE, "IOException thrown when sending json response", e);
        }
    }

    /**
     * Get request to the servlet. See API docs in respective end servlets for
     * more information.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        final String uuidStr = request.getParameter("uuid");
        if (uuidStr == null) {
            doError(request, response, "No uuid provided", 404);
            return;
        }

        final Map<String, String> status;
        try {
            status = DBHandler.INSTANCE.getStatus(uuidStr);
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Database error", e);
            doError(request, response, "Database failure", 500);
            return;
        }

        if (status == null) {
            doError(request, response, "Unknown uuid: " + uuidStr, 404);
            return;
        }

        final JsonObjectBuilder json = Json.createObjectBuilder();
        status.forEach(json::add);

        sendResponse(request, response, json.build().toString());
    }

    /**
     * Writes to response object with the communication methods that this server
     * supports.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     * @see BaseServlet#allowCrossOrigin(HttpServletRequest, HttpServletResponse)
     */
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        allowCrossOrigin(request, response);
    }

    /**
     * Allow cross origin requests according to the CORS standard.
     * This method will not override any existing headers that are already set in the response.
     *
     * @param request the request from the client
     * @param response the response object to the request from the client
     */
    private static void allowCrossOrigin(final HttpServletRequest request, final HttpServletResponse response) {
        final String credentials = response.getHeader("Access-Control-Allow-Credentials");
        if (credentials == null) {
            response.addHeader("Access-Control-Allow-Credentials", "true");
        }
        final String origin = response.getHeader("Access-Control-Allow-Origin");
        if (origin == null) {
            String requestOrigin = request.getHeader("origin");
            if (requestOrigin == null) {
                requestOrigin = "*";
            }
            response.addHeader("Access-Control-Allow-Origin", requestOrigin);
        }
        final String methods = response.getHeader("Access-Control-Allow-Methods");
        if (methods == null) {
            response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS, DELETE");
        }
        final String headers = response.getHeader("Access-Control-Allow-Headers");
        if (headers == null) {
            response.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Access-Control-Allow-Origin, authorization");
        }
    }

    /**
     * A post request to the server.
     *
     * @param request the request from the client
     * @param response the response to send once this method exits
     * @see BaseServlet#convert(String, Map, File, File, String)
     */
    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
        DBHandler.INSTANCE.cleanOldEntries(individualTTL);

        final String inputType = request.getParameter("input");
        if (inputType == null) {
            doError(request, response, "Missing input type", 400);
            return;
        }

        final String uuid = UUID.randomUUID().toString();

        if (!validateRequest(request, response, uuid)) {
            return;
        }

        final Map<String, String[]> parameterMap = new HashMap<>(request.getParameterMap());
        final Map<String, String> customData = (Map<String, String>) request.getAttribute("com.idrsolutions.microservice.customData");
        final Map<String, String> settings = (Map<String, String>) request.getAttribute("com.idrsolutions.microservice.settings");

        switch (inputType) {
            case "upload":
                if (!handleFileFromRequest(uuid, request, response, parameterMap, customData, settings)) {
                    return;
                }
                break;

            case "download":
                if (!handleFileFromUrl(uuid, request, response, parameterMap, customData, settings)) {
                    return;
                }
                break;

            default:
                doError(request, response, "Unrecognised input type", 400);
                return;
        }

        sendResponse(request, response, Json.createObjectBuilder().add("uuid", uuid).build().toString());
    }

    /**
     * Sanitize the file name by removing all non filepath friendly characters.
     *
     * Allow only characters valid across all (most) platforms.
     * Note that this does not cover all reserved filenames on Windows (E.g. CON, COM1, LTP1, etc), therefore it
     * remains possible for a user to pass a file that cannot be stored if the server is running on Windows.
     *
     * The space character is also currently replaced with an underscore because file names that consist only of
     * spaces and file paths that end with spaces are not allowed on Windows.
     *
     * More info: https://stackoverflow.com/a/31976060
     *
     * @param fileName the filename to sanitize
     * @return the sanitized filename
     */
    private static String sanitizeFileName(final String fileName) {
        return fileName.replaceAll("[\\\\/:\"*?<>| \\p{Cc}]", "_");
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
            FileHelper.deleteFolder(outputDir);
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
     * @param uuid the uuid associated with this conversion
     * @param request the request for this conversion
     * @param response the response object for the request
     * @param params the parameter map from the request
     * @return true on success, false on failure
     */
    private boolean handleFileFromRequest(final String uuid, final HttpServletRequest request,
                                          final HttpServletResponse response, final Map<String, String[]> params,
                                          final Map<String, String> customData, final Map<String, String> settings) {
        final Part filePart;
        try {
            filePart = request.getPart("file");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "IOException when getting the file part", e);
            doError(request, response, "Error handling file", 500);
            return false;
        } catch (ServletException e) {
            doError(request, response, "Missing file", 400);
            return false;
        }

        if (filePart == null) {
            doError(request, response, "Missing file", 400);
            return false;
        }

        final long fileSizeLimit = getFileSizeLimit(request);
        if (fileSizeLimit > 0 && filePart.getSize() > fileSizeLimit) {
            doError(request, response, "File size limit exceeded", 400);
            return false;
        }

        final String originalFileName = getFileName(filePart);
        if (originalFileName == null) {
            doError(request, response, "Missing file name", 400);
            return false;
        }

        if (originalFileName.indexOf('.') == -1) {
            doError(request, response, "File has no extension", 400);
            return false;
        }

        final File inputFile;
        try {
            final InputStream fileContent = filePart.getInputStream();
            final byte[] fileBytes = new byte[(int) filePart.getSize()];
            fileContent.read(fileBytes);
            fileContent.close();
            inputFile = outputFile(originalFileName, uuid, fileBytes);
        } catch (final IOException e) {
            LOG.log(Level.SEVERE, "IOException when reading an uploaded file", e);
            doError(request, response, "Internal error", 500); // Failed to save file to disk
            return false;
        }

        final File outputDir = createOutputDirectory(uuid);

        final String[] rawParam = params.get("callbackUrl");
        final String callbackUrl = (rawParam != null && rawParam.length > 0) ? rawParam[0] : "";

        DBHandler.INSTANCE.initializeConversion(uuid, callbackUrl, customData, settings);

        addToQueue(uuid, params, inputFile, outputDir, getContextURL(request));

        return true;
    }

    /**
     * Handle and convert a file located at a given url.
     * <p>
     * This method does not block when attempting to download the file from the
     * url.
     *
     * @param uuid the uuid associated with this conversion
     * @param request the request for this conversion
     * @param response the response object for the request
     * @param params the parameter map from the request
     * @return true on initial success (url has been provided)
     */
    private boolean handleFileFromUrl(final String uuid, final HttpServletRequest request,
                                      final HttpServletResponse response, final Map<String, String[]> params,
                                      final Map<String, String> customData, final Map<String, String> settings) {

        String url = request.getParameter("url");
        if (url == null || url.isEmpty()) {
            doError(request, response, "No url given", 400);
            return false;
        }

        if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://")) {
            doError(request, response, "Unsupported protocol", 400);
            return false;
        }

        // This does not need to be asynchronous
        String filename = DownloadHelper.getFileNameFromUrl(url);
        // In case a filename cannot be parsed from the url.
        if (filename == null) {
            filename = "document.pdf";
        }

        if (filename.indexOf('.') == -1) {
            doError(request, response, "File has no extension", 400);
            return false;
        }

        final long fileSizeLimit = getFileSizeLimit(request);
        if (fileSizeLimit > 0) {
            long fileSize;
            try {
                fileSize = DownloadHelper.getFileSizeFromUrl(url);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "IOException when finding the FileSize of a remote file", e);
                doError(request, response, "Internal error", 500);
                return false;
            }

            if (fileSize > fileSizeLimit) {
                doError(request, response, "File size limit exceeded", 400);
                return false;
            }
        }

        // To allow use in lambda function.
        final String finalFilename = filename;
        final String contextUrl = getContextURL(request);

        final ExecutorService downloadQueue = (ExecutorService) getServletContext().getAttribute("downloadQueue");

        final String[] rawParam = params.get("callbackUrl");
        final String callbackUrl = (rawParam != null && rawParam.length > 0) ? rawParam[0] : "";

        DBHandler.INSTANCE.initializeConversion(uuid, callbackUrl, customData, settings);

        downloadQueue.submit(() -> {
            File inputFile = null;
            try {
                final byte[] fileBytes = DownloadHelper.getFileFromUrl(url, NUM_DOWNLOAD_RETRIES, fileSizeLimit);
                inputFile = outputFile(finalFilename, uuid, fileBytes);
            } catch (IOException e) {
                DBHandler.INSTANCE.setError(uuid, 1200, "Could not get file from URL");
            } catch (SizeLimitExceededException e) {
                DBHandler.INSTANCE.setError(uuid, 1210, "File exceeds file size limit");
            }

            final File outputDir = createOutputDirectory(uuid);
            addToQueue(uuid, params, inputFile, outputDir, contextUrl);
        });

        return true;
    }

    /**
     * Add a conversion task to the thread queue.
     *
     * @param uuid the uuid of this conversion
     * @param params the parameter map from the request
     * @param inputFile the input file to convert
     * @param outputDir the output directory to convert to
     * @param contextUrl the context url of the servlet
     */
    private void addToQueue(final String uuid, final Map<String, String[]> params, final File inputFile,
                            final File outputDir, final String contextUrl) {

        final ExecutorService convertQueue = (ExecutorService) getServletContext().getAttribute("convertQueue");

        convertQueue.submit(() -> {
            try {
                convert(uuid, params, inputFile, outputDir, contextUrl);
            } finally {
                handleCallback(uuid);
                DBHandler.INSTANCE.setAlive(uuid, false);
            }
        });
    }

    /**
     * Validate the request to ensure suitable for the microservice conversion,
     * failure will lead to the request stopping before starting the conversion.
     *
     * It is recommended to call doError and set the individual conversionParams
     * from inside implementations of validateRequest.
     *
     * @param request the request for this conversion
     * @param response the response object for the request
     * @param uuid the uuid of this conversion
     * @return true if the request is valid, false if not
     */
    protected abstract boolean validateRequest(final HttpServletRequest request, final HttpServletResponse response,
                                               final String uuid);

    /**
     * This method converts a file and writes it to the output directory under
     * the Individual's UUID.
     *
     * @param uuid the uuid of the conversion
     * @param params the map of parameters from the request
     * @param inputFile the File to convert
     * @param outputDir the directory the converted file should be written to
     * @param contextUrl The url from the protocol up to the servlet url
     * pattern.
     */
    protected abstract void convert(String uuid, Map<String, String[]> params,
                                    File inputFile, File outputDir, String contextUrl);

    /**
     * Write the given file bytes to the output directory under filename.
     *
     * @param filename the filename to output to
     * @param uuid the uuid of the conversion request
     * @param fileBytes the bytes to be written.
     * @return the created file
     * @throws IOException on file not being writable
     */
    private File outputFile(String filename, final String uuid, byte[] fileBytes) throws IOException {
        final File inputDir = createInputDirectory(uuid);
        final File inputFile = new File(inputDir, sanitizeFileName(filename));

        try(FileOutputStream output = new FileOutputStream(inputFile)) {
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
    private static String getFileName(final Part part) {
        // Note that the rules for values allowed inside the content-disposition is fuzzy because the rules in the HTML
        // spec do not match those in RFCs, so browsers may do something different to other HTTP clients.
        //
        // Certain characters may be percent-encoded 0x0A (LF), 0x0D (CR), 0x22 ("). However it is not possible to
        // differentiate them from occurrences of %0A, %0D & %22 because % itself does not get percent-encoded, so a
        // filename of %22" appears as filename="%22%22".
        // See https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#multipart-form-data
        //
        // The rules for the encoding of HTTP headers is not the same as the rules for the encoding of the
        // content-disposition header's filename value in multipart/form-data requests. Most sources say that header
        // values may only contain ISO-8859-1, but this does not apply to the filename value in this case.
        //
        // The following wording from RFC 2183 is obsolete and should be ignored:
        // "Current [RFC 2045] grammar restricts parameter values (and hence Content-Disposition filenames) to
        // US-ASCII." - https://datatracker.ietf.org/doc/html/rfc2183#section-2.3
        //
        // multipart/form-data requests send their payload (which includes the content-disposition header) in the body
        // of the POST request. It is not a typical HTTP header.
        //
        // RFC 5987 and RFC 6266 provides a method to specify the charset of header values (using filename*="value"),
        // however this is explicitly disallowed by RFC 7578.
        // "NOTE: The encoding method described in [RFC5987], which would add a "filename*" parameter to the
        // Content-Disposition header field, MUST NOT be used." - https://datatracker.ietf.org/doc/html/rfc7578#section-4.2
        //
        // Note also that RFC 6266 applies to response headers only - not multipart/form-data headers in POST requests.
        //
        // "Some commonly deployed systems use multipart/form-data with file names directly encoded including octets
        // outside the US-ASCII range. The encoding used for the file names is typically UTF-8, although HTML forms will
        // use the charset associated with the form." - https://datatracker.ietf.org/doc/html/rfc7578#section-4.2
        //
        // Thus we should treat the value of the filename as UTF-8. Whilst researching, I observed that it is typical to
        // pass the filename separately to the content-disposition header (e.g. as JSON) in order to store the value correctly.

        final String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition.isEmpty()) {
            return null;
        }

        int startIndex = contentDisposition.indexOf("filename=");
        if (startIndex == -1) {
            return null;
        }
        startIndex += 9; // 9 = length of "filename="

        int index = startIndex;
        boolean isQuoted = false;
        boolean isEscaped = false;
        while (index < contentDisposition.length()) {
            char ch = contentDisposition.charAt(index);
            if (ch == ';') {
                if (!isQuoted) {
                    break;
                }
            } else if (!isEscaped && ch == '"') {
                isQuoted = !isQuoted;
            }
            isEscaped = !isEscaped && ch == '\\';
            index++;
        }

        if (contentDisposition.charAt(startIndex) == '"' && contentDisposition.charAt(index - 1) == '"') {
            startIndex++;
            index--;
        }

        return new String(contentDisposition.substring(startIndex, index).getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Checks if the callbackUrl parameter was included in the request, if so it
     * will queue the callback into the callbackQueue.
     *
     * @param uuid the uuid of the conversion to send to the callback URL
     */
    private void handleCallback(final String uuid) {
        final String callbackUrl;
        try {
            callbackUrl = DBHandler.INSTANCE.getCallbackUrl(uuid);

            if (!callbackUrl.equals("")) {
                final Map<String, String> status;
                status = DBHandler.INSTANCE.getStatus(uuid);

                if (status == null) {
                    LOG.log(Level.SEVERE, "Callback failed. UUID was not in database.");
                    return;
                }

                final JsonObjectBuilder json = Json.createObjectBuilder();
                status.forEach(json::add);

                final ScheduledExecutorService callbackQueue = (ScheduledExecutorService) getServletContext().getAttribute("callbackQueue");
                callbackQueue.submit(() -> HttpHelper.sendCallback(callbackUrl, json.build().toString(), callbackQueue, 1));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error while handling callback", e);
        }
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
     * Try to get the fileSizeLimit attribute as a long from the
     * HttpServeletRequest
     *
     * @param request the request from the client
     * @return the value of fileSizeLimit or -1 if the attribute is not set
     */
    private static long getFileSizeLimit(final HttpServletRequest request) {
        final Object rawSizeLimit = request.getAttribute("com.idrsolutions.microservice.fileSizeLimit");
        if (rawSizeLimit != null && rawSizeLimit instanceof Long) {
            return (long) rawSizeLimit;
        } else {
            return -1L;
        }
    }

    /**
     * Get the conversion parameters from a JSON string.
     *
     * JSON Array values are ignored.
     * Embedded objects have all k/v extracted (but the key for the object is lost).
     *
     * @param settings a JSON string of settings
     * @return a Map made from the JSON k/v
     * @throws JsonParsingException on issue with JSON parsing
     */
    protected static Map<String, String> parseSettings(final String settings) throws JsonParsingException {
        final Map<String, String> out = new HashMap<>();

        if (settings == null || settings.isEmpty()) {
            return out;
        }

        try(JsonParser jp = Json.createParser(new StringReader(settings))) {
            String currentKey = null;
            byte arrayDepth = 0;
            while (jp.hasNext()) {
                JsonParser.Event e = jp.next();
                switch (e) {
                    case START_ARRAY:
                        arrayDepth++;
                        break;
                    case END_ARRAY:
                        arrayDepth--;
                        break;
                    case KEY_NAME:
                        currentKey = jp.getString();
                        break;
                    case VALUE_STRING:
                        if (currentKey != null && arrayDepth < 1) {
                            out.put(currentKey, jp.getString());
                        }
                        break;
                    case VALUE_NUMBER:
                        if (currentKey != null && arrayDepth < 1) {
                            out.put(currentKey, String.valueOf(jp.getInt()));
                        }
                        break;
                    case VALUE_TRUE:
                        if (currentKey != null && arrayDepth < 1) {
                            out.put(currentKey, "true");
                        }
                        break;
                    case VALUE_FALSE:
                        if (currentKey != null && arrayDepth < 1) {
                            out.put(currentKey, "false");
                        }
                        break;
                }
            }
        }
        return out;
    }
}
