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
package conversion;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a file conversion request to the server. Allows storage of UUID's
 * for identification of clients which are requesting file conversions.
 * <p>
 * Information stores includes: UUID, isAlive flag, a timestamp, and the curent
 * state of the conversion ("queued", "processing", "done", ...). It can also
 * hold custom values to be passed to the user.
 */
class Individual {

    public final String uuid;
    public boolean isAlive = false;
    public String outputDir = null;
    final long timestamp;
    public String state;
    public String errorCode;

    private final HashMap<String, String> customValues = new HashMap<>();

    /**
     * Create individual with a specific UUID.
     *
     * @param uuid
     */
    Individual(final String uuid) {
        this.uuid = uuid;
        timestamp = new Date().getTime();
        state = "queued";
    }

    /**
     * Get a JSON string representing the current state of this individual. JSON
     * string holds the current state of the conversion, the error code (if it
     * exists), as well as any custom key value pairs set though 
     * {@link Individual#setValue(String, String) }
     *
     * @return a JSON string representing this individuals state
     */
    String toJsonString() {
        final StringBuilder json = new StringBuilder();
        json.append("{\"state\":\"").append(state).append("\"")
                .append(errorCode != null ? ",\"errorCode\":" + errorCode : "");

        for (final Map.Entry<String, String> valuePair : customValues.entrySet()) {
            json.append(",\"").append(valuePair.getKey()).append("\":\"").append(valuePair.getValue()).append("\"");
        }

        json.append("}");

        return json.toString();
    }

    /**
     * Adds a key value pair to he individual to pass to the client the next
     * time the client polls the server.
     *
     * @param key
     * @param value
     */
    public void setValue(final String key, final String value) {
        customValues.put(key, value);
    }
}
