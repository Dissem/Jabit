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
import ch.dissem.bitmessage.entity.payload.ObjectType;
import ch.dissem.bitmessage.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

import static ch.dissem.bitmessage.utils.Strings.hex;

/**
 * Helper class that does Flyway migration, provides JDBC connections and some helper methods.
 */
public abstract class JdbcHelper {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcHelper.class);

    protected final JdbcConfig config;

    protected JdbcHelper(JdbcConfig config) {
        this.config = config;
    }

    public static void writeBlob(PreparedStatement ps, int parameterIndex, Streamable data) throws SQLException, IOException {
        if (data == null) {
            ps.setBytes(parameterIndex, null);
        } else {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            data.write(os);
            ps.setBytes(parameterIndex, os.toByteArray());
        }
    }
}
