/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2023 IDRsolutions
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
package com.idrsolutions.microservice.db;

import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class MemoryDatabase implements Database {

    private final Map<String, Individual> imap;

    MemoryDatabase() {
        imap = new ConcurrentHashMap<>();
    }

    /**
     * Initialises the conversion in the database
     *  @param uuid       The uuid of the conversion
     * @param callbackUrl
     * @param customData Custom data for the conversion
     * @param settings   Settings for the conversion
     */
    @Override
    public void initializeConversion(final String uuid, String callbackUrl, final Map<String, String> customData,
                                     final Map<String, String> settings) {
        imap.put(uuid, new Individual(uuid, callbackUrl, customData, settings));
    }

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     *
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    @Override
    public void cleanOldEntries(final long TTL) {
        imap.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < new Date().getTime() - TTL);
    }

    @Override
    public void setCustomValue(final String uuid, final String key, final String value) {
        imap.get(uuid).getCustomValues().put(key, value);
    }

    @Override
    public void setAlive(final String uuid, final boolean alive) {
        imap.get(uuid).setAlive(alive);
    }

    @Override
    public void setState(final String uuid, final String state) {
        imap.get(uuid).setState(state);
    }

    @Override
    public void setError(final String uuid, final int errorCode, final String errorMessage) {
        final Individual individual = imap.get(uuid);
        individual.state = "error";
        individual.errorCode = String.valueOf(errorCode);
        individual.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    @Override
    public Map<String, String> getStatus(final String uuid) {
        final Individual individual = imap.get(uuid);

        if (individual == null) {
            return null;
        }

        final Map<String, String> state = new LinkedHashMap<>();
        state.put("state", individual.state);

        final String errorCode = individual.errorCode;
        if (errorCode != null) {
            state.put("errorCode", errorCode);
            state.put("error", individual.errorMessage);
        }

        state.putAll(individual.customValues);

        return state;
    }

    @Override
    public String getCallbackUrl(final String uuid) {
        final Individual individual = imap.get(uuid);

        if (individual == null) {
            return null;
        }

        return individual.getCallbackUrl();
    }

    @Override
    public Map<String, String> getSettings(final String uuid) {
        final Individual individual = imap.get(uuid);

        if (individual == null) {
            return null;
        }

        return individual.getSettings();
    }

    @Override
    public Map<String, String> getCustomData(final String uuid) {
        final Individual individual = imap.get(uuid);

        if (individual == null) {
            return null;
        }

        return individual.getCustomData();
    }

    /**
     * Represents a file conversion request to the server. Allows storage of UUID's
     * for identification of clients which are requesting file conversions.
     */
    private static final class Individual {
        private final String uuid;
        private String callbackUrl;
        private boolean isAlive = true;
        private final long timestamp;
        private String state;
        private String errorCode;
        private String errorMessage;

        private final Map<String, String> settings;
        private final Map<String, String> customValues = new ConcurrentHashMap<>();
        private final Map<String, String> customData;

        /**
         * Create individual with a specific UUID.
         *
         * @param uuid the uuid to identify this individual
         */
        Individual(final String uuid, String callbackUrl, final Map<String, String> customData,
                   final Map<String, String> settings) {
            this.uuid = uuid;
            this.callbackUrl = callbackUrl;
            timestamp = new Date().getTime();
            state = "queued";

            this.customData = customData;
            this.settings = settings;
        }


        public Map<String, String> getCustomValues() {
            return customValues;
        }

        /**
         * Adds a key value pair to the individual to pass to the client in GET
         * requests and callbacks.
         *
         * @param key   the key to be passed to the client
         * @param value the value mapped to the key
         */
        private synchronized void setValue(final String key, final String value) {
            customValues.put(key, value);
        }

        /**
         * Returns the unique identifier for this Individual
         *
         * @return the unique identifier for this Individual
         */
        private String getUuid() {
            return uuid;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public void setCallbackUrl(final String callbackUrl) {
            this.callbackUrl = callbackUrl;
        }

        /**
         * Returns true if this conversion is queued or in progress, and false if the
         * conversion has completed or thrown an error.
         *
         * @return if the conversion is alive
         */
        private boolean isAlive() {
            return isAlive;
        }

        /**
         * Set the alive state of the Individual. True if the conversion is queued or
         * in progress, and false if the conversion has completed or thrown an error.
         *
         * @param alive the alive state of the Individual
         */
        private void setAlive(final boolean alive) {
            isAlive = alive;
        }

        /**
         * Get the state of the Individual. This could be queued, processing,
         * processed, or something else.
         *
         * @return the state of the Individual
         */
        public String getState() {
            return state;
        }

        /**
         * Sets the state of the Individual. This could be queued, processing,
         * processed, or something else.
         *
         * @param state the state of the Individual
         */
        private void setState(final String state) {
            this.state = state;
        }

        /**
         * Gets the error code of the Individual (0 if not set). This is used
         * when an error has occurred during processing. The error code should
         * specify what went wrong.
         *
         * @return the error code of the Individual (0 if not set)
         */
        private String getErrorCode() {
            return errorCode;
        }

        /**
         * Gets the error code of the Individual (empty string if not set). This is used
         * when an error has occurred during processing. The error message should
         * specify what went wrong.
         *
         * @return the error message of the Individual (empty string if not set)
         */
        private String getErrorMessage() {
            return errorMessage;
        }

        /**
         * Get the settings used for the conversion (null if not set)
         *
         * @return the conversion settings
         */
        private Map<String, String> getSettings() {
            return settings;
        }

        /**
         * Get custom data stored in the Individual (null if not set)
         *
         * @return the custom data
         */
        public Map<String, String> getCustomData() {
            return customData;
        }

        /**
         * Gets the timestamp for when the Individual was created
         *
         * @return the timestamp for when the Individual was created
         */
        public long getTimestamp() {
            return timestamp;
        }
    }
}
