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

import java.sql.SQLException;
import java.util.Map;

public interface Database {

    /**
     * Initialises the conversion in the database
     * @param uuid The uuid of the conversion
     * @param callbackUrl
     * @param customData Custom data for the conversion
     * @param settings Settings for the conversion
     */
    void initializeConversion(final String uuid, String callbackUrl, final Map<String, String> customData, final Map<String, String> settings);

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    void cleanOldEntries(final long TTL);

    void setCustomValue(final String uuid, final String key, final String value);

    void setAlive(final String uuid, final boolean alive);

    void setState(final String uuid, final String state);

    void setError(final String uuid, final int errorCode, final String errorMessage);

    Map<String, String> getStatus(final String uuid) throws SQLException;

    String getCallbackUrl(final String uuid) throws SQLException;

    Map<String, String> getSettings(final String uuid) throws SQLException;

    Map<String, String> getCustomData(final String uuid) throws SQLException;

}
