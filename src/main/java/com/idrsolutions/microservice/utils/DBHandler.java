
package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.Individual;

import java.sql.*;
import java.util.Date;
import java.util.HashMap;

public class DBHandler {
    Connection connection;
    Statement statement;

    public DBHandler() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:database.db");
            statement = connection.createStatement();
            setupDatabase();
        } catch (SQLException | ClassNotFoundException err) {
            err.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Sets up the tables, clearing out any existing tables and recreating it
     * @throws SQLException An sql Exception
     */
    private void setupDatabase() throws SQLException {
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
        // Setup many to one relation with Cascade Delete to clear them out when the reference is deleted
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

    /**
     * Gets an individual from the database
     * @param id the UUID of the Individual
     * @return the individual whom the UUID Belongs to, or null if one doesn't exist
     * @throws SQLException an SQL Exception
     */
    public Individual getIndividual(String id) throws SQLException {
        ResultSet theIndividual = statement.executeQuery("SELECT * FROM conversions WHERE uuid=\"" + id + "\";");

        // Return null if the individual doesn't exist
        if (!theIndividual.next()) return null;

        // Get the hashmaps from the other tables
        ResultSet theSettings = statement.executeQuery("SELECT key, value FROM settings WHERE uuid=\"" + id + "\";");
        HashMap<String, String> settings = new HashMap<>();

        while (theSettings.next()) {
            settings.put(theSettings.getString("key"), theSettings.getString("value"));
        }
        theSettings.close();

        ResultSet theCustomValues = statement.executeQuery("SELECT key, value FROM customValues WHERE uuid=\"" + id + "\";");
        HashMap<String, String> customValues = new HashMap<>();

        while (theCustomValues.next()) {
            customValues.put(theCustomValues.getString("key"), theCustomValues.getString("value"));
        }

        theCustomValues.close();

        Individual individual = new Individual(theIndividual.getString("uuid"),
                theIndividual.getBoolean("isAlive"),
                theIndividual.getLong("theTime"),
                theIndividual.getString("state"),
                theIndividual.getString("errorCode"),
                theIndividual.getString("errorMessage"),
                settings,
                customValues
        );

        theIndividual.close();

        return individual;
    }

    /**
     * Executes the given SQL Update String
     * @see Statement#executeUpdate(String)
     * @param sql
     */
    public void executeUpdate(String sql) {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserts the given individual into the database
     * @param individual the individual to insert into the database
     */
    public void putIndividual(Individual individual) {
        try {
            statement.executeUpdate("INSERT INTO conversions (uuid, isAlive, theTime, state, errorCode, errorMessage) VALUES (\"" + individual.getUuid() + "\", \"" + individual.isAlive() + "\", \"" + individual.getTimestamp() + "\", \"" + individual.getState() + "\", \"" + individual.getErrorCode() + "\", \"" + individual.getErrorMessage() + "\")");
            String settings = Individual.getMassInsertString("settings", individual.getSettings());
            if (settings != null) {
                statement.executeUpdate(settings);
            }
            String customValues = Individual.getMassInsertString("customValues", individual.getCustomValues());
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
        try {
            statement.executeUpdate("DELETE FROM conversions WHERE theTime < " + (new Date().getTime() - TTL));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}