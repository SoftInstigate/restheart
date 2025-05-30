/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.utils;

import java.util.Arrays;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;;

/**
 * Utility class providing builder patterns and helper methods for creating Gson JsonObjects and JsonArrays.
 * This class offers a fluent API for constructing JSON structures programmatically with type safety
 * and null checking.
 *
 * <p>The class provides two main builder patterns:</p>
 * <ul>
 * <li>{@link ObjectBuilder} for creating JsonObject instances</li>
 * <li>{@link ArrayBuilder} for creating JsonArray instances</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GsonUtils {
    /**
     * Creates a new ObjectBuilder for building JsonObject instances.
     * This is an alias for ObjectBuilder.builder().
     *
     * @return a new ObjectBuilder instance
     */
    public static ObjectBuilder object() {
        return ObjectBuilder.builder();
    }

    /**
     * Creates a new ObjectBuilder initialized with an existing JsonObject.
     * This is an alias for ObjectBuilder.builder(JsonObject).
     *
     * @param obj the JsonObject to initialize the builder with
     * @return a new ObjectBuilder instance initialized with the provided object
     */
    public static ObjectBuilder object(JsonObject obj) {
        return ObjectBuilder.builder(obj);
    }

    /**
     * Creates a new ArrayBuilder for building JsonArray instances.
     * This is an alias for ArrayBuilder.builder().
     *
     * @return a new ArrayBuilder instance
     */
    public static ArrayBuilder array() {
        return ArrayBuilder.builder();
    }

    /**
     * Creates a new ArrayBuilder initialized with an existing JsonArray.
     * This is an alias for ArrayBuilder.builder(JsonArray).
     *
     * @param array the JsonArray to initialize the builder with
     * @return a new ArrayBuilder instance initialized with the provided array
     */
    public static ArrayBuilder array(JsonArray array) {
        return ArrayBuilder.builder(array);
    }

    /**
     * Builder class for creating JsonObject instances using a fluent API.
     * This builder provides type-safe methods for adding various types of values
     * to a JsonObject with automatic null checking.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * JsonObject obj = GsonUtils.object()
     *     .put("name", "John")
     *     .put("age", 30)
     *     .put("active", true)
     *     .get();
     * }</pre>
     */
    public static class ObjectBuilder {
        /** The JsonObject being built. */
        private final JsonObject obj;

        /**
         * Creates a new ObjectBuilder with an empty JsonObject.
         *
         * @return a new ObjectBuilder instance
         */
        public static ObjectBuilder builder() {
            return new ObjectBuilder();
        }

        /**
         * Creates a new ObjectBuilder initialized with the provided JsonObject.
         *
         * @param obj the JsonObject to initialize the builder with
         * @return a new ObjectBuilder instance
         */
        public static ObjectBuilder builder(JsonObject obj) {
            return new ObjectBuilder(obj);
        }

        /**
         * Private constructor for creating an ObjectBuilder with an empty JsonObject.
         */
        private ObjectBuilder() {
            this.obj = new JsonObject();
        }

        /**
         * Private constructor for creating an ObjectBuilder with the provided JsonObject.
         *
         * @param obj the JsonObject to use for this builder
         */
        private ObjectBuilder(JsonObject obj) {
            this.obj = obj;
        }

        /**
         * Adds a JsonElement to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the JsonElement value (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, JsonElement value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.add(key, value);
            return this;
        }

        /**
         * Adds all properties from another JsonObject to this object.
         *
         * @param other the JsonObject to copy properties from (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder putAll(JsonObject other) {
            Objects.nonNull(other);
            other.entrySet().stream().forEach(e -> obj.add(e.getKey(), e.getValue()));
            return this;
        }

        /**
         * Adds an Integer value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the Integer value (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, Integer value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        /**
         * Adds a Number value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the Number value (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, Number value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        /**
         * Adds a boolean value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the boolean value
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, boolean value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        /**
         * Adds a Character value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the Character value (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, Character value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        /**
         * Adds a String value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param value the String value (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, String value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        /**
         * Adds a null value to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder putNull(String key) {
            obj.add(key, JsonNull.INSTANCE);
            return this;
        }

        /**
         * Adds a JsonArray built by an ArrayBuilder to the object with the specified key.
         *
         * @param key the property key (must not be null)
         * @param builder the ArrayBuilder containing the array to add (must not be null)
         * @return this ObjectBuilder for method chaining
         */
        public ObjectBuilder put(String key, ArrayBuilder builder) {
            Objects.nonNull(key);
            Objects.nonNull(builder);
            obj.add(key, builder.get());
            return this;
        }

        /**
         * Returns the built JsonObject.
         *
         * @return the JsonObject that was built
         */
        public JsonObject get() {
            return obj;
        }
    }

    /**
     * Builder class for creating JsonArray instances using a fluent API.
     * This builder provides type-safe methods for adding various types of values
     * to a JsonArray with automatic null checking.
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * JsonArray array = GsonUtils.array()
     *     .add("item1", "item2")
     *     .add(1, 2, 3)
     *     .add(true, false)
     *     .get();
     * }</pre>
     */
    public static class ArrayBuilder {
        /** The JsonArray being built. */
        private final JsonArray array;

        /**
         * Creates a new ArrayBuilder with an empty JsonArray.
         *
         * @return a new ArrayBuilder instance
         */
        public static ArrayBuilder builder() {
            return new ArrayBuilder();
        }

        /**
         * Creates a new ArrayBuilder initialized with the provided JsonArray.
         *
         * @param array the JsonArray to initialize the builder with
         * @return a new ArrayBuilder instance
         */
        public static ArrayBuilder builder(JsonArray array) {
            return new ArrayBuilder(array);
        }

        /**
         * Private constructor for creating an ArrayBuilder with an empty JsonArray.
         */
        private ArrayBuilder() {
            this.array = new JsonArray();
        }

        /**
         * Private constructor for creating an ArrayBuilder with the provided JsonArray.
         *
         * @param array the JsonArray to use for this builder
         */
        private ArrayBuilder(JsonArray array) {
            this.array = array;
        }

        /**
         * Adds one or more JsonElement values to the array.
         *
         * @param values the JsonElement values to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(JsonElement... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        /**
         * Adds one or more String values to the array.
         *
         * @param values the String values to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(String... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        /**
         * Adds one or more Number values to the array.
         *
         * @param values the Number values to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(Number... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        /**
         * Adds one or more Boolean values to the array.
         *
         * @param values the Boolean values to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(Boolean... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        /**
         * Adds a null value to the array.
         *
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder addNull() {
            array.add(JsonNull.INSTANCE);
            return this;
        }

        /**
         * Adds JsonObjects built by one or more ObjectBuilders to the array.
         *
         * @param builders the ObjectBuilders containing objects to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(ObjectBuilder... builders) {
            Objects.nonNull(builders);
            Arrays.stream(builders).map(ObjectBuilder::get).forEach(array::add);
            return this;
        }

        /**
         * Adds JsonArrays built by one or more ArrayBuilders to the array.
         *
         * @param builders the ArrayBuilders containing arrays to add (must not be null)
         * @return this ArrayBuilder for method chaining
         */
        public ArrayBuilder add(ArrayBuilder... builders) {
            Objects.nonNull(builders);
            Arrays.stream(builders).map(ArrayBuilder::get).forEach(array::add);
            return this;
        }

        /**
         * Returns the built JsonArray.
         *
         * @return the JsonArray that was built
         */
        public JsonArray get() {
            return array;
        }
    }
}
