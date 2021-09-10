
package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.BaseServlet;
import com.idrsolutions.microservice.Individual;

import java.sql.*;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

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

    public void shutdown() {
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupDatabase() throws SQLException {
        // Clear Tables
        statement.executeUpdate("DROP TABLE IF EXISTS settings");
        statement.executeUpdate("DROP TABLE IF EXISTS conversions");

        // Create Tables
        statement.executeUpdate("CREATE TABLE conversions (" +
                                        "uuid VARCHAR(36), " +
                                        "isAlive BOOLEAN, " +
                                        "theTime UNSIGNED BIGINT(255), " +
                                        "state VARCHAR(10), " +
                                        "errorCode VARCHAR(5), " +
                                        "errorMessage VARCHAR(255), " +
                                        "previewURL TEXT," +
                                        "downloadURL TEXT," +
                                        "PRIMARY KEY (uuid)" +
                                ")");
        statement.executeUpdate("CREATE TABLE settings (" +
                                        "uuid VARCHAR(36), " +
                                        "key VARCHAR(70), " +
                                        "value VARCHAR(255), " +
                                        "PRIMARY KEY (uuid, key), " +
                                        "FOREIGN KEY (uuid) REFERENCES conversions(uuid) ON DELETE CASCADE" +
                                ")");

    }

    public Individual getIndividual(String id) throws SQLException {
        ResultSet theIndividual = statement.executeQuery("SELECT * FROM conversions WHERE uuid=\"" + id + "\";");
        Individual individual = new Individual(theIndividual.getString("uuid"),
                theIndividual.getBoolean("isAlive"),
                theIndividual.getLong("theTime"),
                theIndividual.getString("state"),
                theIndividual.getString("errorCode"),
                theIndividual.getString("errorMessage")
        );

        ResultSet theSettings = statement.executeQuery("SELECT key, value FROM settings WHERE uuid=\"" + id + "\";");
        HashMap<String, String> settings = new HashMap<>();

        while (theSettings.next()) {
            settings.put(theSettings.getString("key"), theSettings.getString("value"));
        }

        individual.setSettings(settings);

        return individual;
    }

    public void executeUpdate(String sql) {
        try {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void putIndividual(Individual individual) {
        try {
            statement.executeUpdate("INSERT INTO conversions (uuid, isAlive, theTime, state, errorCode, errorMessage) VALUES (" + individual.getUuid() + ", " + individual.isAlive() + ", " + individual.getTimestamp() + ", " + individual.getState() + ", " + individual.getErrorCode() + ", " + individual.getErrorMessage() + ", " + ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void cleanOldEntries() {
        try {
            statement.executeUpdate("DELETE FROM conversions WHERE theTime < " + (new Date().getTime() - BaseServlet.individualTTL));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
