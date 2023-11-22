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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessUtils {


    public enum Result {
        SUCCESS(0),
        TIMEOUT(1050),
        ERROR(1070);

        final int code;

        Result(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static final Logger LOG = Logger.getLogger(ProcessUtils.class.getName());

    public static ProcessUtils.Result runProcess(final String[] processCommand, final File workingDirectory, final String uuid, final String processIdentifier, final long timeoutDuration) {

        final Process process;
        try {
            process = createProcess(processCommand, workingDirectory);
        } catch (final IOException e) {
            LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " threw an exception", e);
            return ProcessUtils.Result.ERROR;
        }

        setupStandardOutputLogger(process);

        try {
            if (!process.waitFor(timeoutDuration, TimeUnit.MILLISECONDS)) {
                process.destroy();
                LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " timed out after " + timeoutDuration + "ms");
                return ProcessUtils.Result.TIMEOUT;
            }
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            LOG.log(Level.INFO, "Terminated child process " + processIdentifier + ' ' + uuid + " for shutdown.");
            return Result.ERROR;
        }

        final int v = process.exitValue();
        if (v != 0) {
            LOG.log(Level.SEVERE, "Exit Value " + v);
            return Result.ERROR;
        }

        return Result.SUCCESS;
    }

    private static void setupStandardOutputLogger(final Process process) {
        final BufferedReader outRead = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final Thread stdOutReaderThread = new Thread(() -> {
            try {
                String line;
                // readLine() returns null when the process terminates
                while ((line = outRead.readLine()) != null) {
                    if (!line.startsWith("NOTE: Picked up JDK_JAVA_OPTIONS:")) {
                        LOG.log(Level.SEVERE, line);
                    }
                }
            } catch (final IOException e) {
                LOG.log(Level.SEVERE, "Standard Output Reader threw an exception", e);
            }
        });
        stdOutReaderThread.setDaemon(true);
        stdOutReaderThread.start();
    }

    private static Process createProcess(final String[] processCommand, final File workingDirectory) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(processCommand);
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);
        return pb.start();
    }
}
