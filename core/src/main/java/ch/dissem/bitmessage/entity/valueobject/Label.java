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

package ch.dissem.bitmessage.entity.valueobject;

import java.io.Serializable;
import java.util.Objects;

public class Label implements Serializable {
    private static final long serialVersionUID = 831782893630994914L;

    private Object id;
    private String label;
    private Type type;
    private int color;

    public Label(String label, Type type, int color) {
        this.label = label;
        this.type = type;
        this.color = color;
    }

    /**
     * @return RGBA representation for the color.
     */
    public int getColor() {
        return color;
    }

    /**
     * @param color RGBA representation for the color.
     */
    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return label;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Label label1 = (Label) o;
        return Objects.equals(label, label1.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

    public enum Type {
        INBOX,
        BROADCAST,
        DRAFT,
        OUTBOX,
        SENT,
        UNREAD,
        TRASH
    }
}
