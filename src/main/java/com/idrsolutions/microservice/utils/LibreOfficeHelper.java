package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.db.DBHandler;

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
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param uuid The uuid of the conversion on which to set the error if one occurs
     * @return true on success, false on failure
     * occurs
     */
    public static boolean convertToPDF(final String sofficePath, final File file, final String uuid) {
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final ProcessBuilder pb = new ProcessBuilder(sofficePath,
                "-env:UserInstallation=file://" + uniqueLOProfile,
                "--headless", "--convert-to", "pdf", file.getName());

        pb.directory(new File(file.getParent()));

        try {
            final Process process = pb.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroy();
                DBHandler.getInstance().setError(uuid, 1050, "Libreoffice timed out after 1 minute");
                return false;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Exception thrown when converting with LibreOffice", e); // soffice location may need to be added to the path
            DBHandler.getInstance().setError(uuid, 1070, "Internal error processing file");
            return false;
        } finally {
            FileHelper.deleteFolder(new File(uniqueLOProfile));
        }
        return true;
    }

}
