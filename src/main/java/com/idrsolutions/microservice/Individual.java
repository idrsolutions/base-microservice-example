/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2021 IDRsolutions
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
package com.idrsolutions.microservice;

import java.util.Date;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Represents a file conversion request to the server. Allows storage of UUID's
 * for identification of clients which are requesting file conversions.
 */
public class Individual {

    private final String uuid;
    private boolean isAlive = true;
    private final long timestamp;
    private String state;
    private String errorCode;
    private String errorMessage;
    private Object customData;

    private Map<String, String> settings;
    private JsonObjectBuilder customValues = Json.createObjectBuilder();

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
     * Change the state to error and set error code. This is used when an error 
     * has occurred during processing. The error code should specify what went
     * wrong.
     *
     * @param errorCode the error code of the Individual
     * @param errorMessage the error message of the Individual
     */
    public void doError(final int errorCode, final String errorMessage) {
        this.state = "error";
        this.errorCode = String.valueOf(errorCode);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /**
     * Get a JSON string representing the current state of this individual.
     *
     * @return a JSON string representing this individuals state
     */
    String toJsonString() {
        final JsonObjectBuilder json = Json.createObjectBuilder()
                .add("state", state);
        if (errorCode != null) {
            json.add("errorCode", errorCode);
            json.add("error", errorMessage);
        }

        getCustomValues().forEach((s, jsonValue) -> json.add(s, jsonValue));

        return json.build().toString();
    }

    /**
     * Get the immutable JsonObject from customValues, while ensuring customValues
     * keeps mutability
     *
     * @return an immutable JsonObject of customValues
     */
    private JsonObject getCustomValues() {
        final JsonObject jsonObj = customValues.build();
        customValues = Json.createObjectBuilder();
        jsonObj.forEach((s, jsonValue) -> customValues.add(s, jsonValue));

        return jsonObj;
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final String value) {
        customValues.add(key, value);
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final boolean value) {
        customValues.add(key, value);
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final int value) {
        customValues.add(key, value);
    }

    /**
     * Returns the unique identifier for this Individual
     *
     * @return the unique identifier for this Individual
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Returns true if this conversion is queued or in progress, and false if the
     * conversion has completed or thrown an error.
     *
     * @return if the conversion is alive
     */
    public boolean isAlive() {
        return isAlive;
    }

    /**
     * Set the alive state of the Individual. True if the conversion is queued or
     * in progress, and false if the conversion has completed or thrown an error.
     *
     * @param alive the alive state of the Individual
     */
    public void setAlive(boolean alive) {
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
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Gets the error code of the Individual (0 if not set). This is used
     * when an error has occurred during processing. The error code should
     * specify what went wrong.
     *
     * @return the error code of the Individual (0 if not set)
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the error code of the Individual (empty string if not set). This is used
     * when an error has occurred during processing. The error message should
     * specify what went wrong.
     *
     * @return the error message of the Individual (empty string if not set)
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the settings used for the conversion (null if not set)
     *
     * @return the conversion settings
     */
    public Map<String, String> getSettings() {
        return settings;
    }

    /**
     * Store the settings used for the conversion
     *
     * @param settings the settings to store
     */
    public void setSettings(final Map<String, String> settings) {
        this.settings = settings;
    }

    /**
     * Get custom data stored in the Individual (null if not set)
     *
     * @return the custom data
     */
    public Object getCustomData() {
        return customData;
    }

    /**
     * Store custom data in the Individual
     *
     * @param customData the custom data to store
     */
    public void setCustomData(Object customData) {
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
