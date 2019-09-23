/*
 * Base Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/base-microservice-example
 *
 * Copyright 2018 IDRsolutions
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
package com.idrsolutions.conversion;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a file com.idrsolutions.conversion request to the server. Allows storage of UUID's
 * for identification of clients which are requesting file conversions.
 */
public class Individual {

    private final String uuid;
    private boolean isAlive = true;
    private final long timestamp;
    private String state;
    private String errorCode;
    private Object customData;

    private final HashMap<String, JsonValue> customValues = new HashMap<>();

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
     */
    public void doError(final int errorCode) {
        this.state = "error";
        this.errorCode = String.valueOf(errorCode);
    }

    /**
     * Get a JSON string representing the current state of this individual.
     *
     * @return a JSON string representing this individuals state
     */
    String toJsonString() {
        final StringBuilder json = new StringBuilder();
        json.append("{\"state\":\"").append(state).append("\"")
                .append(errorCode != null ? ",\"errorCode\":" + errorCode : "");

        for (final Map.Entry<String, JsonValue> valuePair : customValues.entrySet()) {
            json.append(",\"").append(valuePair.getKey()).append("\":").append(valuePair.getValue().toString());
        }

        json.append("}");

        return json.toString();
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final String value) {
        customValues.put(key, JsonValue.of(value));
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final boolean value) {
        customValues.put(key, JsonValue.of(value));
    }

    /**
     * Adds a key value pair to the individual to pass to the client in GET
     * requests and callbacks.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final int value) {
        customValues.put(key, JsonValue.of(value));
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
     * Returns true if this com.idrsolutions.conversion is queued or in progress, and false if the
     * com.idrsolutions.conversion has completed or thrown an error.
     *
     * @return if the com.idrsolutions.conversion is alive
     */
    public boolean isAlive() {
        return isAlive;
    }

    /**
     * Set the alive state of the Individual. True if the com.idrsolutions.conversion is queued or
     * in progress, and false if the com.idrsolutions.conversion has completed or thrown an error.
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

    private static abstract class JsonValue {
        private JsonValue() {}

        abstract public String toString();

        private static JsonValue of(final String value) {
            return new JsonValue() {
                @Override
                public String toString() {
                    return '"' + value + '"';
                }
            };
        }

        private static JsonValue of(final int value) {
            return new JsonValue() {
                @Override
                public String toString() {
                    return String.valueOf(value);
                }
            };
        }

        private static JsonValue of(final boolean value) {
            return new JsonValue() {
                @Override
                public String toString() {
                    return String.valueOf(value);
                }
            };
        }
    }
}
