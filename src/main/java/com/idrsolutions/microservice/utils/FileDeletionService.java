package com.idrsolutions.microservice.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A class that will remove files after they exceed the individual's time to live (TTL).
 * An example use would be to remove the input/output files for conversions once they exceed the TTL.
 * When first run, it will immediately scan the directories and remove files that exceed the individual's TTL.
 *
 * If a new directory is used and requires a FileDeletionService, it is suggested to create a new instance.
 */
public class FileDeletionService {
    private static final Logger LOG = Logger.getLogger(FileDeletionService.class.getName());

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    /**
     * Create an instance of the FileDeletionService
     *
     * @param dirs the root directories that will be scanned for files that should be deleted
     * @param fileLifeSpan the life span in milliseconds of the files before they should be deleted
     * @param frequency the frequency in minutes that the service will run
     */
    public FileDeletionService(final String[] dirs, final long fileLifeSpan, final long frequency) {
        setUpService(dirs, fileLifeSpan, frequency);
    }

    /**
     * Use a ScheduledExecutorService to set up a service with the process of deleting any files that exceed the fileLifeSpan
     *
     * This method does not remove any previously set scheduled tasks from the ExecutorService
     *
     * @param dirs the root directories that will be scanned for files that should be deleted
     * @param fileLifeSpan the life span in milliseconds of the files before they should be deleted
     * @param frequency the frequency in minutes that the service will run
     */
    private void setUpService(final String[] dirs, final long fileLifeSpan, final long frequency) {
        final Runnable deleteFiles = () -> {
            if (dirs != null) {
                final long currentTime = new Date().getTime();

                final long timeToDelete = currentTime - fileLifeSpan;
                for (final String dir : dirs) {
                    try {
                        final File fileDir = new File(dir);
                        final File[] files = fileDir.listFiles();

                        if (fileDir.exists() && files != null) {
                            Arrays.stream(files)
                                    .filter(file -> file.lastModified() < timeToDelete)
                                    .forEach(FileDeletionService::deleteFile);
                        }
                    } catch (final Throwable e) {
                        final String message = String.format("Exception thrown whilst FileDeletionService was scanning (%s)", dir);
                        LOG.log(Level.WARNING, message, e);
                    }
                }
            }
        };
        scheduledExecutorService.scheduleAtFixedRate(deleteFiles, 0, frequency, TimeUnit.MINUTES);
    }

    /**
     * Static method to delete the provided file
     *
     * If the file is a directory, it will delete all contained files/folders
     *
     * @param file the file to be deleted
     */
    private static void deleteFile(final File file) {
        try (Stream<Path> filePath = Files.walk(file.toPath())) {
            filePath.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (final IOException e) {
                            final String message = String.format("Error when trying to delete file (%s)", path);
                            LOG.log(Level.WARNING, message, e);
                        }
                    });
        } catch (final Throwable e) {
            final String message = String.format("Exception thrown when trying to delete file (%s)", file.getAbsolutePath());
            LOG.log(Level.WARNING, message, e);
        }
    }

    /**
     * Starts a shutdown where previously submitted tasks are executed, but no new tasks are accepted.
     * Invocation has no additional effect if already shut down.
     */
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the processing of waiting tasks,
     * and returns previously queued tasks.
     *
     * @return a list of the tasks that were awaiting execution
     */
    public List<Runnable> shutdownNow() {
        return scheduledExecutorService.shutdownNow();
    }

}
