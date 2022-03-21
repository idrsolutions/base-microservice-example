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

import javax.sql.DataSource;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ExternalDatabase implements Database {
    private static final Logger LOG = Logger.getLogger(ExternalDatabase.class.getName());

    private final DataSource dataSource;

    ExternalDatabase(final DataSource dataSource) {
        this.dataSource = dataSource;

        try {
            setupDatabase();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialise database", e);
        }
    }

    /**
     * Sets up the tables, clearing out any existing tables and recreating it
     *
     * @throws SQLException An sql Exception
     */
    private void setupDatabase() throws SQLException {

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // Create Tables
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS conversions (" +
                    "uuid VARCHAR(36), " +
                    "callbackUrl TEXT, " +
                    "isAlive BOOLEAN, " +
                    "theTime BIGINT(20) UNSIGNED, " +
                    "state VARCHAR(10), " +
                    "errorCode VARCHAR(5), " +
                    "errorMessage VARCHAR(255), " +
                    "PRIMARY KEY (uuid)" +
                    ")");
            // Setup many-to-one relations with Cascade Delete to clear them out when the reference is deleted
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS settings (" +
                    "uuid VARCHAR(36), " +
                    "mapKey VARCHAR(70), " +
                    "value VARCHAR(255), " +
                    "PRIMARY KEY (uuid, mapKey), " +
                    "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE ON UPDATE CASCADE" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS customValues (" +
                    "uuid VARCHAR(36), " +
                    "mapKey VARCHAR(70), " +
                    "value TEXT, " +
                    "PRIMARY KEY (uuid, mapKey), " +
                    "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE ON UPDATE CASCADE" +
                    ")");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS customData (" +
                    "uuid VARCHAR(36), " +
                    "mapKey VARCHAR(70), " +
                    "value TEXT, " +
                    "PRIMARY KEY (uuid, mapKey), " +
                    "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE ON UPDATE CASCADE" +
                    ")");
        }
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement individualStatement = connection.prepareStatement("INSERT INTO conversions (uuid, callbackUrl, isAlive, theTime, state, errorCode, errorMessage) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            individualStatement.setString(1, uuid);
            individualStatement.setString(2, callbackUrl);
            individualStatement.setBoolean(3, true);
            individualStatement.setLong(4, new Date().getTime());
            individualStatement.setString(5, "queued");
            individualStatement.setString(6, null);
            individualStatement.setString(7, null);

            individualStatement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error Inserting individual into the database", e);
        }

        // As these create their own connection, we should execute this outside of the above try so the connection object can be released and reused
        setSettings(uuid, settings);
        setCustomData(uuid, customData);
    }

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     *
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    @Override
    public void cleanOldEntries(final long TTL) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM conversions WHERE theTime < ?")) {
            statement.setFloat(1, new Date().getTime() - TTL);
            statement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error Cleaning old entries in the database", e);
        }
    }

    @Override
    public void setCustomValue(final String uuid, final String key, final String value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("REPLACE INTO customValues VALUES (?, ?, ?) ")) {
            statement.setString(1, uuid);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error Inserting individual's custom value into the database", e);
        }
    }

    @Override
    public void setAlive(final String uuid, final boolean alive) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("Update conversions SET isAlive = ? WHERE uuid = ?")) {
            statement.setBoolean(1, alive);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error setting individual alive", e);
        }
    }

    @Override
    public void setState(final String uuid, final String state) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("Update conversions SET state = ? WHERE uuid = ?")) {
            statement.setString(1, state);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error setting individual state", e);
        }
    }

    private void setIndividualMap(final String uuid, final String table, final Map<String, String> map) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?");
             PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO " + table + " VALUES (?, ?, ?)")) {
            deleteStatement.setString(1, uuid);
            deleteStatement.executeUpdate();

            if (map != null && !map.isEmpty()) {
                insertStatement.setString(1, uuid);
                for (String key : map.keySet()) {
                    insertStatement.setString(2, key);
                    insertStatement.setString(3, map.get(key));
                    insertStatement.executeUpdate();
                }
            }
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error setting individual " + table, e);
        }
    }

    private void setSettings(final String uuid, final Map<String, String> settings) {
        setIndividualMap(uuid, "settings", settings);
    }

    private void setCustomValues(final String uuid, final Map<String, String> customValues) {
        setIndividualMap(uuid, "customValues", customValues);
    }

    private void setCustomData(final String uuid, final Map<String, String> customData) {
        setIndividualMap(uuid, "customData", customData);
    }

    @Override
    public void setError(final String uuid, final int errorCode, final String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE conversions SET state = ?, errorCode = ?, errorMessage = ? WHERE UUID = ?")) {
            statement.setString(1, "error");
            statement.setInt(2, errorCode);
            statement.setString(3, errorMessage == null ? "" : errorMessage);
            statement.setString(4, uuid);

            statement.executeUpdate();
        } catch (final SQLException e) {
            LOG.log(Level.SEVERE, "Error setting individual error", e);
        }
    }

    @Override
    public Map<String, String> getStatus(final String uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement individualStatement = connection.prepareStatement("SELECT * FROM conversions WHERE uuid = ?;");
             PreparedStatement customValuesStatement = connection.prepareStatement("SELECT key, value FROM customValues WHERE uuid = ?;")) {
            individualStatement.setString(1, uuid);

            final ResultSet individualResultSet = individualStatement.executeQuery();

            if (!individualResultSet.next()) {
                return null;
            }

            customValuesStatement.setString(1, uuid);
            final ResultSet customValuesResultSet = customValuesStatement.executeQuery();

            final Map<String, String> state = new LinkedHashMap<>();
            state.put("state", individualResultSet.getString("state"));

            final String errorCode = individualResultSet.getString("errorCode");
            if (errorCode != null) {
                state.put("errorCode", errorCode);
                state.put("error", individualResultSet.getString("errorMessage"));
            }

            while (customValuesResultSet.next()) {
                state.put(customValuesResultSet.getString("key"), customValuesResultSet.getString("value"));
            }

            return state;
        }
    }

    @Override
    public String getCallbackUrl(final String uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
            PreparedStatement callbackStatement = connection.prepareStatement("SELECT callbackUrl FROM conversions WHERE uuid = ?;")) {
            callbackStatement.setString(1, uuid);
            ResultSet callbackResultSet = callbackStatement.executeQuery();

            if (!callbackResultSet.next()) {
                return null;
            }

            return callbackResultSet.getString("callbackUrl");
        }
    }

    @Override
    public Map<String, String> getSettings(final String uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
            PreparedStatement settingsStatement = connection.prepareStatement("SELECT key, value FROM settings WHERE uuid = ?;")) {

            // Get the hashmaps from the other tables
            settingsStatement.setString(1, uuid);

            final ResultSet settingsResultSet = settingsStatement.executeQuery();
            final HashMap<String, String> settings = new HashMap<>();

            while (settingsResultSet.next()) {
                settings.put(settingsResultSet.getString("key"), settingsResultSet.getString("value"));
            }

            return settings;
        }
    }

    @Override
    public Map<String, String> getCustomData(final String uuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
            PreparedStatement customDataStatement = connection.prepareStatement("SELECT key, value FROM customData WHERE uuid = ?;")) {

            // Get the hashmaps from the other tables
            customDataStatement.setString(1, uuid);

            final ResultSet customDataResultSet = customDataStatement.executeQuery();
            final HashMap<String, String> customData = new HashMap<>();

            while (customDataResultSet.next()) {
                customData.put(customDataResultSet.getString("key"), customDataResultSet.getString("value"));
            }

            return customData;
        }
    }
}
