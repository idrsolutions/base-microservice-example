package com.idrsolutions.microservice.utils;

import java.io.File;
import java.io.IOException;
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

    public static ProcessUtils.Result runProcess(final String processCommand, final File workingDirectory, final String uuid, final String processIdentifier) {

        try {
            final Process process = createProcess(processCommand, workingDirectory);
            final int result = process.waitFor();
            if (result != 0) {
                LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " returned with a result of " + result);
                return Result.ERROR;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " threw an exception", e);
            return ProcessUtils.Result.ERROR;
        }
        return ProcessUtils.Result.SUCCESS;
    }


    public static ProcessUtils.Result runProcess(final String processCommand, final File workingDirectory, final String uuid, final String processIdentifier, final long timeoutDuration) {

        try {
            final Process process = createProcess(processCommand, workingDirectory);
            if (!process.waitFor(timeoutDuration, TimeUnit.MILLISECONDS)) {
                process.destroy();
                LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " timed out after " + timeoutDuration + "ms");
                return ProcessUtils.Result.TIMEOUT;
            }
        } catch (final IOException | InterruptedException e) {
            LOG.log(Level.SEVERE, "Process " + processIdentifier + " for " + uuid + " threw an exception", e);
            return ProcessUtils.Result.ERROR;
        }
        return ProcessUtils.Result.SUCCESS;
    }

    private static Process createProcess(final String processCommand, final File workingDirectory) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(processCommand);
        pb.directory(workingDirectory);
        return pb.start();
    }
}
