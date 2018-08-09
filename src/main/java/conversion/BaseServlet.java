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
import java.net.URL;
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

    private final ExecutorService queue = Executors.newFixedThreadPool(5);

    /**
     * Sets the status of the response to the given error status. This method
     * should be called when setting error statuses on responses is called for.
     * No Checking is done to make sure the error status is correct or in the
     * right range.
     *
     * @param response
     * @param error
     * @param status
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
     * Get request to the servlet. Used here for the purpose of polling the
     * servlet for updates on progress. A UUID (unique user ID) must be provided
     * by the client. This UUID is received when beginning a conversion request.
     * <p>
     * If no UUID or an unknown UUID is provided then a 404 response is
     * generated. The response contains a json string defined by
     * {@link Individual#toJsonString()}.
     *
     * @param request
     * @param response
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
     * Responds with the communication methods that this server supports.
     *
     * @param request
     * @param response
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
     * A post request to the server. This method deals with the initial contact
     * with the client. It looks for a file within the http request itself, it
     * then falls back to looking for a file at a url that has passed in the
     * parameters.
     * <p>
     * If a file or url is not present a 400 error is returned. If the file
     * cannot be parsed from the http request then a 500 error is returned. If a
     * file cannot be downloaded from a passed url then the individual state is
     * set to "error" and the conversion does not happen.
     * <p>
     * If the file can be found and has been downloaded then an input dir and
     * and output dir for that file are created (overwritten if they already
     * exist).
     * <p>
     * The convert() method is then started in a separate thread.
     *
     * @param request
     * @param response
     * @see BaseServlet#convert(Individual, Map, String, String, String, String,
     * String, String)
     */
    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) {
        try {
            allowCrossOrigin(response);

            final String uuidStr = UUID.randomUUID().toString();
            final Individual individual = new Individual(uuidStr);
            final String contextUrl = getContextURL(request);

            imap.entrySet().removeIf(entry -> entry.getValue().timestamp < new Date().getTime() - 86400000); // 24 hours

            imap.put(uuidStr, individual);

            individual.isAlive = true;

            boolean isUrlDownload = false;

            // Attempt to get the filename from parameters (null if not in params).
            String fileName = request.getParameter("filename");
            Part filePart = null;

            // Fail silently now and check for null later as the file might be passed via a url.
            try {
                filePart = request.getPart("file");
            } catch (ServletException e) {
                LOG.fine("Request is not of type multipart/form-data (no file attached), checking if URL input provided.");
            }

            final String conversionUrl = request.getParameter("conversionUrl");

            // Prioritise file in request over file passed via url.
            if (filePart != null) {
                if (fileName == null) {
                    fileName = getFileNameFromRequestPart(filePart);
                }
            } else if (conversionUrl != null) {
                isUrlDownload = true;
                if (fileName == null) {
                    fileName = getFileNameFromUrl(conversionUrl);
                }
            } else {
                imap.remove(uuidStr);
                doError(response, "Missing file or URL", 400);
                return;
            }

            if (fileName == null) {
                imap.remove(uuidStr);
                doError(response, "Missing file", 500);
                return;
            }

            final Map<String, String[]> parameterMap = request.getParameterMap();
            // to avoid passing non-final variables into lambda.
            final String name = fileName;

            if (isUrlDownload) {
                // Download this in another thread.
                queue.submit(() -> {
                    try {
                        individual.state = "downloading";
                        final byte[] fileBytes = getFileFromUrl(conversionUrl, NUM_DOWNLOAD_RETRIES);
                        setupdAndConvertFile(true, fileBytes, individual, contextUrl, name, uuidStr, parameterMap);
                    } catch (IOException e) {
                        individual.state = "error";
                        LOG.warning("Error while getting or converting file from url");
                        LOG.warning(e.getMessage());
                    }
                });
            } else {
                byte[] fileBytes = null;
                try {
                    fileBytes = getFileFromRequestPart(filePart);
                } catch (IOException e) {
                    imap.remove(uuidStr);
                    doError(response, "Cannot get file data", 500);
                }
                // An IO error occured.
                // setupAndConvertFile() can throw an IOException too but catch it with above try/catch.
                if (fileBytes != null) {
                    setupdAndConvertFile(false, fileBytes, individual, contextUrl, name, uuidStr, parameterMap);
                }
            }

            response.setContentType("application/json");
            try (final PrintWriter out = response.getWriter()) {
                out.println("{" + "\"uuid\":\"" + uuidStr + "\"}");
            }

        } catch (final IOException e) {
            try {
                doError(response, "Cannot get file data", 500);
            } catch (IOException ee) {
                LOG.severe("Could not set error status to response. Logging stack trace.");
            }
            e.printStackTrace();
            LOG.severe(e.getMessage());
        }
    }

    /**
     * Sets up variables and converts file. Setups up output directories and
     * begins conversion. If isInThread is false then the conversion happens in
     * the thread that called this method.
     * <p>
     * Arguments do not include request or response objects as if this method is
     * run in a separate thread the garbage collector will not collect them.
     *
     * @param isInThread if this method has been run in a separate thread
     * @param fileBytes
     * @param individual
     * @param contextUrl
     * @param fileName
     * @param uuidStr
     * @param parameterMap
     * @throws IOException
     */
    private void setupdAndConvertFile(final boolean isInThread,
            final byte[] fileBytes,
            final Individual individual,
            final String contextUrl,
            String fileName,
            final String uuidStr,
            final Map<String, String[]> parameterMap)
            throws IOException {

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
        }

        final String name = fileName;

        Runnable conversion = () -> {
            try {
                convert(individual, parameterMap, name, inputDir.getAbsolutePath(),
                        outputDir.getAbsolutePath(), fileNameWithoutExt, ext,
                        contextUrl);
            } finally {
                individual.isAlive = false;
            }
        };

        // Don't start a new thread for conversion if we are already in a separate thread.
        if (isInThread) {
            conversion.run();
        } else {
            queue.submit(conversion);
        }
    }

    /**
     * This method converts a file and writes it to the output directory under
     * the Individuals UUID. It is called at the point when a valid user file is
     * stored in the input directory.
     * <p>
     * No Validation is done on the file when this method is called and it is up
     * to the implementing class to determine if the given file is actually a
     * pdf. Is it also up to the implementing class to generate preview/download
     * urls, zip the output, and set the individual state to processed.
     *
     * @param individual Internal representation of individual who made this
     * request.
     * @param parameterMap the map of parameters from the request.
     * @param fileName
     * @param inputDirectory
     * @param outputDirectory
     * @param fileNameWithoutExt
     * @param ext
     * @param contextURL The url up from the protocol up to the servlets url
     * pattern.
     */
    abstract void convert(final Individual individual, final Map<String, String[]> parameterMap, final String fileName,
            final String inputDirectory, final String outputDirectory,
            final String fileNameWithoutExt, final String ext, final String contextURL);

    /**
     * Gets array of file bytes from a url.
     *
     * @param strUrl
     * @return the bytes downloaded from the url, null if no bytes downloaded.
     * @throws IOException
     */
    private static byte[] getFileFromUrl(final String strUrl) throws IOException {

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
     * @param url
     * @param retries
     * @return bytes downloaded from the url, null on error.
     */
    private byte[] getFileFromUrl(final String url, int retries) throws IOException {
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
     * Extract the filename from the url.
     *
     * @param url
     * @return
     */
    private String getFileNameFromUrl(String url) {

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

    /**
     * Gets file bytes stored in the request part.
     *
     * @param filePart
     * @return
     * @throws IOException if the file cannot be read.
     */
    private static byte[] getFileFromRequestPart(Part filePart) throws IOException {
        final byte[] fileBytes = new byte[(int) filePart.getSize()];
        final InputStream fileContent = filePart.getInputStream();
        fileContent.read(fileBytes);
        fileContent.close();
        return fileBytes;
    }

    /**
     * Get the filename of the file contained in this request part.
     *
     * @param part
     * @return the file name or null if it does not exist.
     */
    private String getFileNameFromRequestPart(final Part part) {
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
     * @param request
     * @return protocol://servername/contextPath
     */
    protected static String getContextURL(final HttpServletRequest request) {
        final StringBuffer full = request.getRequestURL();
        return full.substring(0, full.length() - request.getServletPath().length());
    }

    /**
     * Get the conversion parameters. The parameter key value pairs are held in
     * a semi-colon separated list ";" with a colon separating the individual
     * key value pairs. E.g. "key1:val1;key2:val2;etc..."
     *
     * @param settings
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
     * @param dirPath
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
     * Update the progress of the conversion. This method is usually called
     * after a poll from a client to the servlet.
     *
     * @param individual
     */
    abstract void updateProgress(final Individual individual);

}
