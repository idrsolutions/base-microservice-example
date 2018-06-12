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

public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    private static final String INPUTPATH = "../docroot/input/";
    private static final String OUTPUTPATH = "../docroot/output/";

    private final ConcurrentHashMap<String, Individual> imap = new ConcurrentHashMap<>();

    private final ExecutorService queue = Executors.newFixedThreadPool(5);

    private static void doError(final HttpServletResponse response, final String error, final int status) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        try (final PrintWriter out = response.getWriter()) {
            out.println("{\"error\":\"" + error + "\"}");
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

        updateProgress(individual);

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
            final String fileNameWithoutExt = fileName.substring(0, extPos).replaceAll("[^a-zA-Z0-9]", "_");
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
                    convert(individual, parameterMap, name, inputDir.getAbsolutePath(), outputDir.getAbsolutePath(), fileNameWithoutExt, ext);
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

    abstract void convert(final Individual individual, final Map<String, String[]> parameterMap, final String fileName,
                          final String inputDirectory, final String outputDirectory,
                          final String fileNameWithoutExt, final String ext);

    private String getFileName(final Part part) {
        for (String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return content.substring(
                        content.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return null;
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

    abstract void updateProgress(final Individual individual);

}
