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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static ch.dissem.bitmessage.utils.Strings.hex;

/**
 * Helper class that does Flyway migration, provides JDBC connections and some helper methods.
 */
abstract class JdbcHelper {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcHelper.class);

    protected final JdbcConfig config;

    protected JdbcHelper(JdbcConfig config) {
        this.config = config;
    }

    public static StringBuilder join(long... objects) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(objects[i]);
        }
        return streamList;
    }

    public static StringBuilder join(byte[]... objects) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(hex(objects[i]));
        }
        return streamList;
    }

    public static StringBuilder join(ObjectType... types) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append(types[i].getNumber());
        }
        return streamList;
    }

    public static StringBuilder join(Enum... types) {
        StringBuilder streamList = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) streamList.append(", ");
            streamList.append('\'').append(types[i].name()).append('\'');
        }
        return streamList;
    }

    protected void writeBlob(PreparedStatement ps, int parameterIndex, Streamable data) throws SQLException, IOException {
        if (data != null) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            data.write(os);
            byte[] bytes = os.toByteArray();
            ps.setBinaryStream(parameterIndex, new ByteArrayInputStream(bytes), bytes.length);
        } else {
            ps.setBlob(parameterIndex, (Blob) null);
        }
    }
}
