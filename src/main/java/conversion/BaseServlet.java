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

public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    private static final String INPUTPATH = "../docroot/input/";
    private static final String OUTPUTPATH = "../docroot/output/";

    private static final int NUM_DOWNLOAD_RETRIES = 2;

    private final ConcurrentHashMap<String, Individual> imap = new ConcurrentHashMap<>();

    private final ExecutorService convertQueue = Executors.newFixedThreadPool(5);
    private final ExecutorService downloadQueue = Executors.newFixedThreadPool(5);

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

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        allowCrossOrigin(response);
    }

    private void allowCrossOrigin(final HttpServletResponse response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST, OPTIONS, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Access-Control-Allow-Origin");
    }

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

    private static String sanitizeFileName(final String fileName) {
        final int extPos = fileName.lastIndexOf('.');
        // Limit filenames to chars allowed in unencoded URLs and Windows filenames for now
        final String fileNameWithoutExt = fileName.substring(0, extPos).replaceAll("[^$\\-_.+!'(),a-zA-Z0-9]", "_");
        final String ext = fileName.substring(extPos + 1);

        return fileNameWithoutExt + '.' + ext;
    }

    private static File createInputDirectory(final String uuid) {
        final String userInputDirPath = INPUTPATH + uuid;
        final File inputDir = new File(userInputDirPath);
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }
        return inputDir;
    }

    private static File createOutputDirectory(final String uuid) {
        final String userOutputDirPath = OUTPUTPATH + uuid;
        final File outputDir = new File(userOutputDirPath);
        if (outputDir.exists()) {
            deleteFolder(outputDir);
        }
        outputDir.mkdirs();
        return outputDir;
    }

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

    abstract void convert(Individual individual, Map<String, String[]> params,
            File inputFile, File outputDir, String contextUrl);

    private File outputFile(String filename, Individual individual, byte[] fileBytes) throws IOException {
        final File inputDir = createInputDirectory(individual.uuid);
        final File inputFile = new File(inputDir, sanitizeFileName(filename));

        try (final FileOutputStream output = new FileOutputStream(inputFile)) {
            output.write(fileBytes);
            output.flush();
        }

        return inputFile;
    }

    private String getFileName(final Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
    }

    protected static String getContextURL(final HttpServletRequest request) {
        final StringBuffer full = request.getRequestURL();
        return full.substring(0, full.length() - request.getServletPath().length());
    }

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
