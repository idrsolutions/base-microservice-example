
package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.Individual;

import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DBHandler {
    public static final DBHandler INSTANCE = new DBHandler();

    Connection connection;

    private DBHandler() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:database.db");
            setupDatabase();
        } catch (final SQLException | ClassNotFoundException err) {
            err.printStackTrace();
        }
    }

    /**
     * Sets up the tables, clearing out any existing tables and recreating it
     * @throws SQLException An sql Exception
     */
    private void setupDatabase() throws SQLException {
        try (final Statement statement = connection.createStatement()) {
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
        try (final PreparedStatement individualStatement = connection.prepareStatement("SELECT * FROM conversions WHERE uuid = ?;");
             final PreparedStatement settingsStatement = connection.prepareStatement("SELECT key, value FROM settings WHERE uuid = ?;");
             final PreparedStatement customValuesStatement = connection.prepareStatement("SELECT key, value FROM customValues WHERE uuid = ?;")) {
            individualStatement.setString(1, id);

            final ResultSet individualResultSet = individualStatement.executeQuery();

            if (!individualResultSet.next()) {
                return null;
            }

            // Get the hashmaps from the other tables
            settingsStatement.setString(1, id);

            final ResultSet settingsResultSet = settingsStatement.executeQuery();
            HashMap<String, String> settings = new HashMap<>();

            while (settingsResultSet.next()) {
                settings.put(settingsResultSet.getString("key"), settingsResultSet.getString("value"));
            }

            customValuesStatement.setString(1, id);

            final ResultSet customValuesResultSet = customValuesStatement.executeQuery();
            Map<String, String> customValues = new ConcurrentHashMap<>();

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
        try (final PreparedStatement individualStatement = connection.prepareStatement("INSERT INTO conversions (uuid, isAlive, theTime, state, errorCode, errorMessage) VALUES (?, ?, ?, ?, ?, ?)")) {
            individualStatement.setString(1, individual.getUuid());
            individualStatement.setBoolean(2, individual.isAlive());
            individualStatement.setLong(3, individual.getTimestamp());
            individualStatement.setString(4, individual.getState());
            individualStatement.setString(5, individual.getErrorCode());
            individualStatement.setString(6, individual.getErrorMessage());

            setIndividualSettings(individual.getUuid(), individual.getSettings());
            setIndividualCustomValues(individual.getUuid(), individual.getCustomValues());
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes all individuals in the database who are older than the passed Time to Live
     * @param TTL the maximum amount of time an individual is allowed to remain in the database
     */
    public void cleanOldEntries(long TTL) {
        try (final PreparedStatement statement = connection.prepareStatement("DELETE FROM conversions WHERE theTime < ?")) {
            statement.setFloat(1, new Date().getTime() - TTL);
            statement.executeUpdate();
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public void setIndividualCustomValue(String uuid, String key, String value) {
        try (final PreparedStatement statement = connection.prepareStatement("INSERT INTO customValues VALUES (?, ?, ?)")) {
            statement.setString(1, uuid);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
        } catch (final SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualAlive(String uuid, boolean alive) {
        try (final PreparedStatement statement = connection.prepareStatement("Update conversions SET isAlive = ? WHERE uuid = ?")) {
            statement.setBoolean(1, alive);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (final SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualState(String uuid, String state) {
        try (final PreparedStatement statement = connection.prepareStatement("Update conversions SET state = ? WHERE uuid = ?")) {
            statement.setString(1, state);
            statement.setString(2, uuid);
            statement.executeUpdate();
        } catch (final SQLException err) {
            err.printStackTrace();
        }
    }

    public void setIndividualMap(String uuid, String table, Map<String, String> map) {
        try (final PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM ? WHERE uuid = ?");
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
        } catch (final SQLException err) {
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
        try (final PreparedStatement statement = connection.prepareStatement("UPDATE conversions SET state = ?, errorCode = ?, errorMessage = ? WHERE UUID = ?")) {
            statement.setString(1, state);
            statement.setInt(2, errorCode);
            statement.setString(3, errorMessage);
            statement.setString(4, uuid);
        } catch (final SQLException throwables) {
            throwables.printStackTrace();
        }
    }
}
