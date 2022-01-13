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
    public static final Database INSTANCE;

    static {
        final DataSource dataSource = setupDatasource();
        INSTANCE = dataSource != null ? new ExternalDatabase(dataSource) : new MemoryDatabase();
    }

    private static DataSource setupDatasource() {
        try {
            // Attempt to grab from tomcat/jetty
            return (DataSource) new InitialContext().lookup("java:comp/env/jdbc/mydb");
        } catch (NamingException ignored) {}

        try {
            // Attempt to grab from Payara/Glassfish
            return (DataSource) new InitialContext().lookup("jdbc/mydb");
        } catch (NamingException ignored) {}

        // TODO: Point towards some instructions for setup
        LOG.warning("No Datasource setup, falling back to internal memory storage");
        return null;
    }

}
