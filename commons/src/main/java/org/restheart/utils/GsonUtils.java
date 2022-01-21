package org.restheart.utils;

import java.util.Arrays;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;;

public class GsonUtils {
    /**
     * alias for ObjectBuilder.builder()
     *
     * @return the ObjectBuilder
     */
    public static ObjectBuilder object() {
        return ObjectBuilder.builder();
    }

    /**
     * alias for ObjectBuilder.builder()
     *
     * @return the ObjectBuilder
     */
    public static ObjectBuilder object(JsonObject obj) {
        return ObjectBuilder.builder(obj);
    }

    /**
     * alias for ArrayBuilder.builder()
     *
     * @return the ArrayBuilder
     */
    public static ArrayBuilder array() {
        return ArrayBuilder.builder();
    }

    /**
     * alias for ArrayBuilder.builder()
     *
     * @return the ArrayBuilder
     */
    public static ArrayBuilder array(JsonArray array) {
        return ArrayBuilder.builder(array);
    }

    /**
     * Builder to help creating JsonObject
     */
    public static class ObjectBuilder {
        private final JsonObject obj;

        public static ObjectBuilder builder() {
            return new ObjectBuilder();
        }

        public static ObjectBuilder builder(JsonObject obj) {
            return new ObjectBuilder(obj);
        }

        private ObjectBuilder() {
            this.obj = new JsonObject();
        }

        private ObjectBuilder(JsonObject obj) {
            this.obj = obj;
        }

        public ObjectBuilder put(String key, JsonElement value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.add(key, value);
            return this;
        }

        public ObjectBuilder putAll(JsonObject other) {
            Objects.nonNull(other);
            other.entrySet().stream().forEach(e -> obj.add(e.getKey(), e.getValue()));
            return this;
        }

        public ObjectBuilder put(String key, Integer value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        public ObjectBuilder put(String key, Number value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        public ObjectBuilder put(String key, boolean value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        public ObjectBuilder put(String key, Character value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        public ObjectBuilder put(String key, String value) {
            Objects.nonNull(key);
            Objects.nonNull(value);
            obj.addProperty(key, value);
            return this;
        }

        public ObjectBuilder putNull(String key) {
            obj.add(key, JsonNull.INSTANCE);
            return this;
        }

        public ObjectBuilder put(String key, ArrayBuilder builder) {
            Objects.nonNull(key);
            Objects.nonNull(builder);
            obj.add(key, builder.get());
            return this;
        }

        public JsonObject get() {
            return obj;
        }
    }

    /**
     * Builder to help creating JsonArray
     */
    public static class ArrayBuilder {
        private final JsonArray array;

        public static ArrayBuilder builder() {
            return new ArrayBuilder();
        }

        public static ArrayBuilder builder(JsonArray array) {
            return new ArrayBuilder(array);
        }

        private ArrayBuilder() {
            this.array = new JsonArray();
        }

        private ArrayBuilder(JsonArray array) {
            this.array = array;
        }

        public ArrayBuilder add(JsonElement... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(String... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Number... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Boolean... values) {
            Objects.nonNull(values);
            Arrays.stream(values).forEach(array::add);
            return this;
        }

        public ArrayBuilder addNull() {
            array.add(JsonNull.INSTANCE);
            return this;
        }

        public ArrayBuilder add(ObjectBuilder... builders) {
            Objects.nonNull(builders);
            Arrays.stream(builders).map(ObjectBuilder::get).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(ArrayBuilder... builders) {
            Objects.nonNull(builders);
            Arrays.stream(builders).map(ArrayBuilder::get).forEach(array::add);
            return this;
        }

        public JsonArray get() {
            return array;
        }
    }
}
