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

package ch.dissem.bitmessage.utils;

/**
 * Some property that has a name, a value and/or other properties. This can be used for any purpose, but is for now
 * used to contain different status information. It is by default displayed in some JSON inspired human readable
 * notation, but you might only want to rely on the 'human readable' part.
 * <p>
 * If you need a real JSON representation, please add a method <code>toJson()</code>.
 * </p>
 */
public class Property {
    private String name;
    private Object value;
    private Property[] properties;

    public Property(String name, Object value, Property... properties) {
        this.name = name;
        this.value = value;
        this.properties = properties;
    }

    @Override
    public String toString() {
        return toString("");
    }

    private String toString(String indentation) {
        StringBuilder result = new StringBuilder();
        result.append(indentation).append(name).append(": ");
        if (value != null || properties.length == 0) {
            result.append(value);
        }
        if (properties.length > 0) {
            result.append("{\n");
            for (Property property : properties) {
                result.append(property.toString(indentation + "  ")).append('\n');
            }
            result.append(indentation).append("}");
        }
        return result.toString();
    }
}
