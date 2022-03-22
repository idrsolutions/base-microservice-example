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

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.logging.Logger;

public abstract class DBHandler {
    private static final Logger LOG = Logger.getLogger(DBHandler.class.getName());
    private static Database INSTANCE = null;

    private static String databaseJNDIName;

    public static void initialise() {
        final DataSource dataSource = setupDatasource();
        INSTANCE = dataSource != null ? new ExternalDatabase(dataSource) : new MemoryDatabase();
    }

    public static Database getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Attempted to access instance before it has been initialised");
        }
        return INSTANCE;
    }

    private static DataSource setupDatasource() {
        if (databaseJNDIName != null && !databaseJNDIName.isEmpty()) {
            try {
                // Attempt to grab from tomcat
                return (DataSource) new InitialContext().lookup("java:comp/env/" + databaseJNDIName);
            } catch (NamingException ignored) {}

            try {
                // Attempt to grab from Payara/Glassfish/jetty
                return (DataSource) new InitialContext().lookup(databaseJNDIName);
            } catch (NamingException ignored) {}

            LOG.warning(String.format("Failed to find Datasource with JNDI %s, falling back to internal memory storage", databaseJNDIName));
        } else {
            LOG.info("No Datasource specified, falling back to internal memory storage");
        }

        return null;
    }

    public static void setDatabaseJNDIName(String databaseJNDIName) {
        DBHandler.databaseJNDIName = databaseJNDIName;
    }
}
