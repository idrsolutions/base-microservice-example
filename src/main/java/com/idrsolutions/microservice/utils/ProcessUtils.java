package com.idrsolutions.microservice.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    static class Flag {

        boolean value;

        public Flag(final boolean v) {
            value = v;
        }

        public void setValue(final boolean v) {
            value = v;
        }

        public boolean getValue() {
            return value;
        }
    }

    public static ProcessUtils.Result runProcess(final String[] processCommand, final File workingDirectory, final String uuid, final String processIdentifier, final long timeoutDuration) {

        try {
            final Process process = createProcess(processCommand, workingDirectory);
            final InputStream stdout = process.getInputStream ();
            BufferedReader outRead = new BufferedReader (new InputStreamReader(stdout));
            final Flag run = new Flag(true);
            Thread t = new Thread(() -> {
                while (run.getValue()) {
                    try {
                        final String line = outRead.readLine();
                        if (line != null) {
                            LOG.log(Level.SEVERE, line);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            t.setDaemon(true);
            t.start();

            if (!process.waitFor(timeoutDuration, TimeUnit.MILLISECONDS)) {
                process.destroy();
                run.setValue(false);
                LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " timed out after " + timeoutDuration + "ms");
                return ProcessUtils.Result.TIMEOUT;
            }
            run.setValue(false);
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " threw an exception", e);
            return ProcessUtils.Result.ERROR;
        }
        return ProcessUtils.Result.SUCCESS;
    }

    private static Process createProcess(final String[] processCommand, final File workingDirectory) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(processCommand);
        pb.directory(workingDirectory);
        pb.redirectErrorStream(true);
        return pb.start();
    }
}
