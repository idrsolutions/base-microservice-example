package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.Individual;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LibreOfficeHelper {

    private static final Logger LOG = Logger.getLogger(LibreOfficeHelper.class.getName());
    private static final String TEMP_DIR;

    static {
        String tempDir = System.getProperty("java.io.tmpdir");
        if (!tempDir.endsWith("/") && !tempDir.endsWith("\\")) {
            tempDir += System.getProperty("file.separator");
        }
        TEMP_DIR = tempDir;
    }

    /**
     * Converts an office file to PDF using LibreOffice.
     *
     * @param file The office file to convert to PDF
     * @param individual The Individual on which to set the error if one occurs
     * @return true on success, false on failure
     * occurs
     */
    public static boolean convertToPDF(final File file, final Individual individual) {
        return convertToPDF("soffice", file, individual);
    }

    /**
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param individual The Individual on which to set the error if one occurs
     * @return true on success, false on failure
     * occurs
     */
    public static boolean convertToPDF(final String sofficePath, final File file, final Individual individual) {
        final String uuid = individual.getUuid();
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder(sofficePath,
                "-env:UserInstallation=file://" + uniqueLOProfile,
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroy();
                individual.doError(1050, "Libreoffice timed out after 1 minute");
                return false;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Exception thrown when converting with LibreOffice", e); // soffice location may need to be added to the path
            individual.doError(1070, "Internal error processing file");
            return false;
        } finally {
            FileHelper.deleteFolder(new File(uniqueLOProfile));
        }
        return true;
    }

}
