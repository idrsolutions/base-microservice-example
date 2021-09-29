
package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.Individual;

import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DBHandler {
    Connection connection;

    public DBHandler() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:database.db");
            setupDatabase();
        } catch (SQLException | ClassNotFoundException err) {
            err.printStackTrace();
        }
    }

    /**
     * Sets up the tables, clearing out any existing tables and recreating it
     * @throws SQLException An sql Exception
     */
    private void setupDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Clear Tables
            statement.executeUpdate("DROP TABLE IF EXISTS settings");
            statement.executeUpdate("DROP TABLE IF EXISTS customValues");
            statement.executeUpdate("DROP TABLE IF EXISTS conversions");

            // Create Tables
            statement.executeUpdate("CREATE TABLE conversions (" +
                    "uuid VARCHAR(36), " +
                    "isAlive BOOLEAN, " +
                    "theTime UNSIGNED BIGINT(255), " +
                    "state VARCHAR(10), " +
                    "errorCode VARCHAR(5), " +
                    "errorMessage VARCHAR(255), " +
                    "PRIMARY KEY (uuid)" +
                    ")");
            // Setup many-to-one relation with Cascade Delete to clear them out when the reference is deleted
            statement.executeUpdate("CREATE TABLE settings (" +
                    "uuid VARCHAR(36), " +
                    "key VARCHAR(70), " +
                    "value VARCHAR(255), " +
                    "PRIMARY KEY (uuid, key), " +
                    "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE" +
                    ")");
            statement.executeUpdate("CREATE TABLE customValues (" +
                    "uuid VARCHAR(36), " +
                    "key VARCHAR(70), " +
                    "value TEXT, " +
                    "PRIMARY KEY (uuid, key), " +
                    "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE" +
                    ")");
        }
    }

    /**
     * Gets an individual from the database
     * @param id the UUID of the Individual
     * @return the individual whom the UUID Belongs to, or null if one doesn't exist
     * @throws SQLException an SQL Exception
     */
    public Individual getIndividual(String id) throws SQLException {
        try (PreparedStatement individualStatement = connection.prepareStatement("SELECT * FROM conversions WHERE uuid = ?;");
             PreparedStatement settingsStatement = connection.prepareStatement("SELECT key, value FROM settings WHERE uuid = ?;");
             PreparedStatement customValuesStatement = connection.prepareStatement("SELECT key, value FROM customValues WHERE uuid = ?;")) {
            individualStatement.setString(1, id);
            ResultSet individualResultSet = individualStatement.executeQuery();

            if (!individualResultSet.next()) {
                return null;
            }

            // Get the hashmaps from the other tables
            settingsStatement.setString(1, id);
            ResultSet settingsResultSet = settingsStatement.executeQuery();
            HashMap<String, String> settings = new HashMap<>();

            while (settingsResultSet.next()) {
                settings.put(settingsResultSet.getString("key"), settingsResultSet.getString("value"));
            }

            customValuesStatement.setString(1, id);
            ResultSet customValuesResultSet = customValuesStatement.executeQuery();
            HashMap<String, String> customValues = new HashMap<>();

            while (customValuesResultSet.next()) {
                customValues.put(customValuesResultSet.getString("key"), customValuesResultSet.getString("value"));
            }

            return new Individual(individualResultSet.getString("uuid"),
                    individualResultSet.getBoolean("isAlive"),
                    individualResultSet.getLong("theTime"),
                    individualResultSet.getString("state"),
                    individualResultSet.getString("errorCode"),
                    individualResultSet.getString("errorMessage"),
                    settings,
                    customValues
            );
        }
    }



    /**
     * Inserts the given individual into the database
     * @param individual the individual to insert into the database
     */
    public void putIndividual(Individual individual) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO conversions (uuid, isAlive, theTime, state, errorCode, errorMessage) VALUES (\"" + individual.getUuid() + "\", \"" + individual.isAlive() + "\", \"" + individual.getTimestamp() + "\", \"" + individual.getState() + "\", \"" + individual.getErrorCode() + "\", \"" + individual.getErrorMessage() + "\")");
            String settings = individual.getMassInsertString("settings", individual.getSettings());
            if (settings != null) {
                statement.executeUpdate(settings);
            }
            String customValues = individual.getMassInsertString("customValues", individual.getCustomValues());
            if (customValues != null) {
                statement.executeUpdate(customValues);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    public void cleanOldEntries(long TTL) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM conversions WHERE theTime < " + (new Date().getTime() - TTL));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setIndividualCustomValue(String uuid, String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO customValues VALUES (?, ?, ?)")) {
            statement.setString(1, uuid);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualAlive(String uuid, boolean alive) {
        try (PreparedStatement statement = connection.prepareStatement("Update conversions SET isAlive = ? WHERE uuid = ?")) {
            statement.setBoolean(1, alive);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualState(String uuid, String state) {
        try (PreparedStatement statement = connection.prepareStatement("Update conversions SET state = ? WHERE uuid = ?")) {
            statement.setString(1, state);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (SQLException err) {
            err.printStackTrace();
        }
    }

    private void setIndividualMap(String uuid, String table, Map<String, String> map) {
        try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM ? WHERE uuid = ?");
             PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO ? VALUES (?, ?, ?)")) {
            deleteStatement.setString(1, table);
            deleteStatement.setString(2, uuid);
            deleteStatement.executeUpdate();

            insertStatement.setString(1, table);
            insertStatement.setString(2, uuid);
            for (String key : map.keySet()) {
                insertStatement.setString(3, key);
                insertStatement.setString(4, map.get(key));
                insertStatement.executeUpdate();
            }
        } catch (SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualSettings(String uuid, Map<String, String> settings) {
        setIndividualMap(uuid, "settings", settings);
    }

    public void setIndividualCustomValues(String uuid, Map<String, String> customValues) {
        setIndividualMap(uuid, "customValues", customValues);
    }

    public void doInvidivualError(String uuid, String state, int errorCode, String errorMessage) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE conversions SET state = ?, errorCode = ?, errorMessage = ? WHERE UUID = ?")) {
            statement.setString(1, state);
            statement.setInt(2, errorCode);
            statement.setString(3, errorMessage);
            statement.setString(4, uuid);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
}
