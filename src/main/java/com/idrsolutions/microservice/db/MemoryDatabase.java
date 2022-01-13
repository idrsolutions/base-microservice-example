/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2022 IDRsolutions
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

import java.util.Date;
import java.util.HashMap;
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
     *
     * @param uuid       The uuid of the conversion
     * @param customData Custom data for the conversion
     * @param settings   Settings for the conversion
     */
    @Override
    public void initUuid(final String uuid, final Map<String, String> customData, final Map<String, String> settings) {
        final Individual individual = new Individual(uuid);
        individual.setCustomData(customData);
        individual.setSettings(settings);

        imap.put(individual.getUuid(), individual);
    }

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     *
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    @Override
    public void cleanOldEntries(long TTL) {
        imap.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < new Date().getTime() - TTL);
    }

    @Override
    public void setCustomValue(String uuid, String key, String value) {
        imap.get(uuid).getCustomValues().put(key, value);
    }

    @Override
    public void setAlive(String uuid, boolean alive) {
        imap.get(uuid).setAlive(alive);
    }

    @Override
    public void setState(String uuid, String state) {
        imap.get(uuid).setState(state);
    }

    @Override
    public void setSettings(String uuid, Map<String, String> settings) {
        imap.get(uuid).setSettings(settings);
    }

    @Override
    public void setCustomValues(String uuid, Map<String, String> customValues) {
        imap.get(uuid).setCustomValues(customValues);
    }

    @Override
    public void setCustomData(String uuid, Map<String, String> customData) {
        imap.get(uuid).setCustomData(customData);
    }

    @Override
    public void setError(String uuid, int errorCode, String errorMessage) {
        final Individual individual = imap.get(uuid);
        individual.state = "error";
        individual.errorCode = String.valueOf(errorCode);
        individual.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    @Override
    public Map<String, String> getState(final String uuid) {
        final Individual individual = imap.get(uuid);

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
    public Map<String, String> getSettings(String uuid) {
        final Individual individual = imap.get(uuid);

        if (individual == null) {
            return null;
        }

        return individual.getSettings();
    }

    @Override
    public Map<String, String> getCustomData(String uuid) {
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
    static class Individual {
        private final String uuid;
        private boolean isAlive = true;
        private final long timestamp;
        private String state;
        private String errorCode;
        private String errorMessage;

        private Map<String, String> settings;
        private Map<String, String> customValues = new ConcurrentHashMap<>();
        private Map<String, String> customData;

        /**
         * Create individual with a specific UUID.
         *
         * @param uuid the uuid to identify this individual
         */
        Individual(final String uuid) {
            this.uuid = uuid;
            timestamp = new Date().getTime();
            state = "queued";
        }

        /**
         * Create an individual, setting all of it's fields
         * This is intended for use by the database, as this method does not create an entry inside the database
         *
         * @param uuid         the uuid to identify this individual
         * @param isAlive      the alive state of the individual
         * @param timestamp    the creation timestamp of the individual
         * @param state        the state of the individual
         * @param errorCode    the error code of the Individual
         * @param errorMessage the error message of the Individual
         * @param settings     the conversion settings
         * @param customValues the custom values
         */
        public Individual(String uuid, boolean isAlive, long timestamp, String state, String errorCode, String errorMessage, HashMap<String, String> settings, Map<String, String> customValues) {
            this.uuid = uuid;
            this.isAlive = isAlive;
            this.timestamp = timestamp;
            this.state = state;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.settings = settings;
            this.customValues = customValues;
        }


        public Map<String, String> getCustomValues() {
            return customValues;
        }

        /**
         * Store a HashMap containing custom values
         *
         * @param customValues The custom values
         */
        private synchronized void setCustomValues(Map<String, String> customValues) {
            this.customValues = customValues;
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
        private void setAlive(boolean alive) {
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
        private void setState(String state) {
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
         * Store the settings used for the conversion
         *
         * @param settings the settings to store
         */
        private void setSettings(final Map<String, String> settings) {
            this.settings = settings;
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
         * Store custom data in the Individual
         *
         * @param customData the custom data to store
         */
        public void setCustomData(Map<String, String> customData) {
            this.customData = customData;
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
