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
 */
public class Individual {

    public final String uuid;
    public boolean isAlive = true;
    final long timestamp;
    public String state;
    public String errorCode;
    public Object data;

    private final HashMap<String, String> customValues = new HashMap<>();

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
    
    void doError(int errorCode) {
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

        for (final Map.Entry<String, String> valuePair : customValues.entrySet()) {
            json.append(",\"").append(valuePair.getKey()).append("\":\"").append(valuePair.getValue()).append("\"");
        }

        json.append("}");

        return json.toString();
    }

    /**
     * Adds a key value pair to the individual to pass to the client the next
     * time the client polls the server.
     *
     * @param key the key to be passed to the client
     * @param value the value mapped to the key
     */
    public void setValue(final String key, final String value) {
        customValues.put(key, value);
    }
}
