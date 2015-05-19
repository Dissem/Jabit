/*
 * Copyright 2015 Christian Basler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.repository;

import ch.dissem.bitmessage.entity.Streamable;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Helper class that does Flyway migration, provides JDBC connections and some helper methods.
 */
abstract class JdbcHelper {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcHelper.class);

    private static final String DB_URL = "jdbc:h2:~/jabit";
    private static final String DB_USER = "sa";
    private static final String DB_PWD = null;


    static {
        Flyway flyway = new Flyway();
        flyway.setDataSource(DB_URL, DB_USER, DB_PWD);
        flyway.migrate();
    }

    protected void writeBlob(PreparedStatement ps, int parameterIndex, Streamable data) throws SQLException, IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        data.write(os);
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        ps.setBlob(parameterIndex, is);
    }

    protected Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PWD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
