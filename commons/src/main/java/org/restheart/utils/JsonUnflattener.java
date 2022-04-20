/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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

import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;

/**
 *
 * JsonUnflattener provides a static unflatten(String) method
 * to unflatten any flattened JSON string back to nested one.
 *
 * @author Wei-Ming Wu
 *
 */
public final class JsonUnflattener {
    /**
     * Returns a JSON string of nested objects by the given flattened JSON
     * string.
     *
     * @param json a flattened JSON string
     * @return a JSON string of nested objects
     */
    public static BsonValue unflatten(BsonValue json) {
        return new JsonUnflattener(json).unflatten();
    }

    private final BsonValue root;

    private static final Character separator = '.';
    private static final Character leftBracket = '[';
    private static final Character rightBracket = ']';

    /**
     * Creates a JSON unflattener.
     *
     * @param json
     */
    public JsonUnflattener(BsonValue json) {
        root = json;
    }

    private static final String objectComplexKey = Pattern.quote(leftBracket.toString()) + "\\s*\".+?\"\\s*" + Pattern.quote(rightBracket.toString());

    private static final Pattern keyPartPattern = Pattern.compile("^$|[^" + Pattern.quote(separator.toString()) + "]+");

    /**
     * Returns a JSON string of nested objects by the given flattened JSON
     * string.
     *
     * @return a JSON string of nested objects
     */
    public BsonValue unflatten() {
        if (root.isArray()) {
            return unflattenArray(root.asArray());
        } else if (root.isDocument()) {
            return unflatten(root.asDocument());
        } else {
            return root;
        }
    }

    private BsonDocument unflatten(BsonDocument flattened) {
        if (flattened.keySet().isEmpty()) {
            return new BsonDocument();
        }

        final BsonDocument unflattened = new BsonDocument();

        // add properties not using the dot notation
        flattened.keySet().stream()
            .filter(key -> keyPartPattern.matcher(key).matches())
            .forEach((key) -> unflattened.asDocument().put(key, flattened.get(key)));

        // add properties using the dot notation
        flattened.keySet().stream()
            .filter(key -> !keyPartPattern.matcher(key).matches())
            .forEach(key -> {
                BsonValue currentVal = unflattened;
                String objKey = null;
                Integer aryIdx = null;

                var matcher = keyPartPattern.matcher(key);
                while (matcher.find()) {
                    var keyPart = matcher.group();

                    var firstKey = matcher.start() == 0;

                    if (objKey != null ^ aryIdx != null) {
                        if (isJsonArray(firstKey, keyPart)) {
                            currentVal = findOrCreateJsonArray(currentVal, objKey, aryIdx);
                            objKey = null;
                            aryIdx = extractIndex(keyPart);
                        } else { // JSON object
                            if (flattened.get(key).isArray()) { // KEEP_ARRAYS mode
                                flattened.put(key, unflattenArray(flattened.get(key).asArray()));
                            }
                            currentVal = findOrCreateJsonObject(currentVal, objKey, aryIdx);
                            objKey = extractKey(keyPart);
                            aryIdx = null;
                        }
                    }

                    if (objKey == null && aryIdx == null) {
                        if (isJsonArray(firstKey, keyPart)) {
                            aryIdx = extractIndex(keyPart);
                            if (currentVal == null) {
                                currentVal = new BsonArray();
                            }
                        } else { // JSON object
                            objKey = extractKey(keyPart);
                            if (currentVal == null) {
                                currentVal = new BsonDocument();
                            }
                        }
                    }
                }

                setUnflattenedValue(flattened, key, currentVal, objKey, aryIdx);
            });

        return unflattened;
    }

    private BsonArray unflattenArray(BsonArray array) {
        var unflattenArray = new BsonArray();

        array.forEach((value) -> {
            if (value.isArray()) {
                unflattenArray.add(unflattenArray(value.asArray()));
            } else if (value.isDocument()) {
                unflattenArray.add(new JsonUnflattener(value.asDocument()).unflatten());
            } else {
                unflattenArray.add(value);
            }
        });

        return unflattenArray;
    }

    private String extractKey(String keyPart) {
        if (keyPart.matches(objectComplexKey)) {
            keyPart = keyPart.replaceAll("^" + Pattern.quote(leftBracket.toString()) + "\\s*\"", "");
            keyPart = keyPart.replaceAll("\"\\s*" + Pattern.quote(rightBracket.toString()) + "$", "");
        }
        return keyPart;
    }

    private Integer extractIndex(String keyPart) {
        return Integer.valueOf(keyPart);
    }

    private boolean isJsonArray(boolean firstPart, String keyPart) {
        return // the first part key is always the key of an object (0.a.b -> 0 is an object)
               !firstPart
               // if a number key, it must be an array index
               && keyPart.matches("\\d+");
    }

    private BsonValue findOrCreateJsonArray(BsonValue currentVal, String objKey, Integer aryIdx) {
        if (objKey != null) {
            var currentDocumentVal = currentVal.asDocument();

            if (!currentDocumentVal.containsKey(objKey)) {
                var ary = new BsonArray();
                currentDocumentVal.put(objKey, ary);

                return ary;
            } else {
                var ret = currentDocumentVal.get(objKey);

                if (!ret.isArray()) {
                    throw new IllegalArgumentException("a field key using the dot notation points to a non-array value");
                }

                return ret;
            }
        } else { // aryIdx != null
            var jsonAry = currentVal.asArray();

            if (jsonAry.size() <= aryIdx || jsonAry.get(aryIdx).equals(BsonNull.VALUE)) {
                var ary = new BsonArray();
                assureJsonArraySize(jsonAry, aryIdx);
                jsonAry.set(aryIdx, ary);

                return ary;
            }

            return jsonAry.get(aryIdx);
        }
    }

    private BsonValue findOrCreateJsonObject(BsonValue currentVal, String objKey, Integer aryIdx) {
        if (objKey != null) {
            var jsonObj = currentVal.asDocument();

            if (jsonObj.get(objKey) == null) {
                var obj = new BsonDocument();
                jsonObj.put(objKey, obj);

                return obj;
            }

            return jsonObj.get(objKey);
        } else { // aryIdx != null
            var jsonAry = currentVal.asArray();

            if (jsonAry.size() <= aryIdx || jsonAry.get(aryIdx).equals(BsonNull.VALUE)) {
                var obj = new BsonDocument();
                assureJsonArraySize(jsonAry, aryIdx);
                jsonAry.set(aryIdx, obj);

                return obj;
            }

            return jsonAry.get(aryIdx);
        }
    }

    private void setUnflattenedValue(BsonDocument flattened, String key, BsonValue currentVal, String objKey, Integer aryIdx) {
        var val = flattened.get(key);
        if (objKey != null) {
            if (val.isArray()) {
                var jsonArray = new BsonArray();
                val.asArray().forEach((arrayVal) ->  jsonArray.asArray().add(newJsonUnflattener(arrayVal).unflatten()));
                currentVal.asDocument().put(objKey, jsonArray);
            } else {
                currentVal.asDocument().put(objKey, val);
            }
        } else if (aryIdx != null) { // aryIdx != null
            assureJsonArraySize(currentVal.asArray(), aryIdx);
            currentVal.asArray().set(aryIdx, val);
        }
    }

    private JsonUnflattener newJsonUnflattener(BsonValue json) {
        var jf = new JsonUnflattener(json);

        return jf;
    }

    private void assureJsonArraySize(BsonArray jsonArray, Integer index) {
        while (index >= jsonArray.size()) {
            jsonArray.add(BsonNull.VALUE);
        }
    }

    @Override
    public int hashCode() {
        int result = 27;
        result = 31 * result + root.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JsonUnflattener)) {
            return false;
        }
        return root.equals(((JsonUnflattener) o).root);
    }

    @Override
    public String toString() {
        return "JsonUnflattener{root=" + root + "}";
    }
}
