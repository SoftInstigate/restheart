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

import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;

/**
 * Utility class for unflattening BSON documents that have been flattened using dot notation.
 * This class converts flattened BSON structures (where nested properties are represented
 * with dot-separated keys) back to their original nested form.
 *
 * <p>For example, a flattened document like:</p>
 * <pre>{@code
 * {
 *   "user.name": "John",
 *   "user.address.street": "123 Main St",
 *   "user.hobbies.0": "reading",
 *   "user.hobbies.1": "coding"
 * }
 * }</pre>
 *
 * <p>Would be unflattened to:</p>
 * <pre>{@code
 * {
 *   "user": {
 *     "name": "John",
 *     "address": {
 *       "street": "123 Main St"
 *     },
 *     "hobbies": ["reading", "coding"]
 *   }
 * }
 * }</pre>
 *
 * <p>The class supports both documents and arrays, and can handle complex nested structures
 * including arrays within objects and objects within arrays.</p>
 *
 * @author Wei-Ming Wu
 */
public final class JsonUnflattener {
    /**
     * Unflattens a BSON value by converting dot-notation keys back to nested structures.
     * This static method provides a convenient way to unflatten BSON documents or arrays
     * without creating an instance of JsonUnflattener.
     *
     * @param json the flattened BSON value (document or array) to unflatten
     * @return the unflattened BSON value with nested structures restored
     */
    public static BsonValue unflatten(BsonValue json) {
        return new JsonUnflattener(json).unflatten();
    }

    /** The root BSON value to be unflattened. */
    private final BsonValue root;

    /** The separator character used in dot notation (period). */
    private static final Character separator = '.';
    
    /** The left bracket character used for array index notation. */
    private static final Character leftBracket = '[';
    
    /** The right bracket character used for array index notation. */
    private static final Character rightBracket = ']';

    /**
     * Creates a new JsonUnflattener for the specified BSON value.
     * The provided BSON value should contain flattened properties using dot notation
     * that will be converted back to nested structures.
     *
     * @param json the flattened BSON value to be processed
     */
    public JsonUnflattener(BsonValue json) {
        root = json;
    }

    /** Pattern for matching complex object keys with bracket notation. */
    private static final String objectComplexKey = Pattern.quote(leftBracket.toString()) + "\\s*\".+?\"\\s*" + Pattern.quote(rightBracket.toString());

    /** Pattern for matching individual key parts separated by dots. */
    private static final Pattern keyPartPattern = Pattern.compile("^$|[^" + Pattern.quote(separator.toString()) + "]+");

    /**
     * Unflattens the BSON value by converting dot-notation keys back to nested structures.
     * This method processes the root BSON value and recursively converts any flattened
     * properties back to their nested form.
     *
     * @return the unflattened BSON value with restored nested structure
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

    /**
     * Unflattens a BSON document by processing dot-notation keys.
     * This method separates keys that use dot notation from those that don't,
     * and processes them accordingly to rebuild the nested structure.
     *
     * @param flattened the flattened BSON document to process
     * @return the unflattened BSON document with nested structure
     */
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

    /**
     * Recursively unflattens a BSON array by processing each element.
     * Array elements that are documents or arrays themselves are recursively
     * unflattened, while primitive values are left unchanged.
     *
     * @param array the BSON array to unflatten
     * @return the unflattened BSON array
     */
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

    /**
     * Extracts the key name from a key part, handling complex bracket notation.
     * If the key part uses bracket notation for complex keys, the brackets and
     * quotes are removed to get the actual key name.
     *
     * @param keyPart the key part to extract the key from
     * @return the extracted key name
     */
    private String extractKey(String keyPart) {
        if (keyPart.matches(objectComplexKey)) {
            keyPart = keyPart.replaceAll("^" + Pattern.quote(leftBracket.toString()) + "\\s*\"", "");
            keyPart = keyPart.replaceAll("\"\\s*" + Pattern.quote(rightBracket.toString()) + "$", "");
        }
        return keyPart;
    }

    /**
     * Extracts an array index from a key part.
     * The key part should be a numeric string representing the array index.
     *
     * @param keyPart the key part containing the numeric index
     * @return the extracted array index as an Integer
     */
    private Integer extractIndex(String keyPart) {
        return Integer.valueOf(keyPart);
    }

    /**
     * Determines if a key part represents an array index.
     * A key part is considered an array index if it's not the first part
     * of the key path and consists only of digits.
     *
     * @param firstPart true if this is the first part of the key path
     * @param keyPart the key part to check
     * @return true if the key part represents an array index, false otherwise
     */
    private boolean isJsonArray(boolean firstPart, String keyPart) {
        return // the first part key is always the key of an object (0.a.b -> 0 is an object)
               !firstPart
               // if a number key, it must be an array index
               && keyPart.matches("\\d+");
    }

    /**
     * Finds an existing JSON array or creates a new one at the specified location.
     * This method handles both object properties and array elements, creating
     * the necessary structure if it doesn't exist.
     *
     * @param currentVal the current BSON value context
     * @param objKey the object key (if accessing via object property)
     * @param aryIdx the array index (if accessing via array index)
     * @return the found or created BSON array
     */
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

    /**
     * Finds an existing JSON object or creates a new one at the specified location.
     * This method handles both object properties and array elements, creating
     * the necessary structure if it doesn't exist.
     *
     * @param currentVal the current BSON value context
     * @param objKey the object key (if accessing via object property)
     * @param aryIdx the array index (if accessing via array index)
     * @return the found or created BSON object
     */
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

    /**
     * Sets the final unflattened value at the appropriate location in the structure.
     * This method handles the placement of values in both objects and arrays,
     * and recursively processes array values if needed.
     *
     * @param flattened the original flattened document
     * @param key the flattened key being processed
     * @param currentVal the current location in the unflattened structure
     * @param objKey the object key (if setting an object property)
     * @param aryIdx the array index (if setting an array element)
     */
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

    /**
     * Creates a new JsonUnflattener instance for recursive processing.
     * This method is used when processing nested structures that themselves
     * need to be unflattened.
     *
     * @param json the BSON value to create a new unflattener for
     * @return a new JsonUnflattener instance
     */
    private JsonUnflattener newJsonUnflattener(BsonValue json) {
        var jf = new JsonUnflattener(json);

        return jf;
    }

    /**
     * Ensures that a JSON array has sufficient size to accommodate the specified index.
     * If the array is smaller than the required index, it is padded with null values
     * until it reaches the necessary size.
     *
     * @param jsonArray the BSON array to resize if necessary
     * @param index the minimum index that should be accessible in the array
     */
    private void assureJsonArraySize(BsonArray jsonArray, Integer index) {
        while (index >= jsonArray.size()) {
            jsonArray.add(BsonNull.VALUE);
        }
    }

    /**
     * Returns the hash code for this JsonUnflattener.
     * The hash code is based on the root BSON value.
     *
     * @return the hash code value for this object
     */
    @Override
    public int hashCode() {
        int result = 27;
        result = 31 * result + root.hashCode();
        return result;
    }

    /**
     * Indicates whether some other object is "equal to" this JsonUnflattener.
     * Two JsonUnflattener instances are considered equal if they have the same root BSON value.
     *
     * @param o the reference object with which to compare
     * @return true if this object is the same as the obj argument; false otherwise
     */
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

    /**
     * Returns a string representation of this JsonUnflattener.
     * The string includes the root BSON value being processed.
     *
     * @return a string representation of the object
     */
    @Override
    public String toString() {
        return "JsonUnflattener{root=" + root + "}";
    }
}
