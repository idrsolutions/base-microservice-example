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

import java.io.File;

public class LibreOfficeHelper {

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
     */
    public static boolean convertToPDF(final String sofficePath, final File file, final String uuid) {
        return convertDocToPDF(sofficePath, file, uuid, 60000) == ProcessUtils.Result.SUCCESS;
    }

    /**
     * Converts an office file to PDF using the specified LibreOffice executable.
     *
     * @param sofficePath The path to the soffice executable
     * @param file The office file to convert to PDF
     * @param uuid The uuid of the conversion on which to set the error if one occurs
     * @return Result enum value depending on the conversion result
     */
    public static ProcessUtils.Result convertDocToPDF(final String sofficePath, final File file, final String uuid, final long timeoutDuration) {
        final String uniqueLOProfile = TEMP_DIR.replace('\\', '/') + "LO-" + uuid;

        final String[] commandAndArgs = new String[] {sofficePath,
                "-env:UserInstallation=file:///" + uniqueLOProfile,
                "--headless", "--convert-to", "pdf", file.getName()};

        final ProcessUtils.Result result =  ProcessUtils.runProcess(commandAndArgs, file.getParentFile(), uuid, "LibreOfficeConversion", timeoutDuration);

        FileHelper.deleteFolder(new File(uniqueLOProfile));

        return result;
    }

}
