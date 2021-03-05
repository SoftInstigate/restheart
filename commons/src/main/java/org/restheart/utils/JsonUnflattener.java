/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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

import java.util.regex.Matcher;
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

    private Character separator = '.';
    private Character leftBracket = '[';
    private Character rightBracket = ']';

    /**
     * Creates a JSON unflattener.
     *
     * @param json
     */
    public JsonUnflattener(BsonValue json) {
        root = json;
    }

    private String objectComplexKey() {
        return Pattern.quote(leftBracket.toString()) + "\\s*\".+?\"\\s*"
                + Pattern.quote(rightBracket.toString());
    }

    private Pattern keyPartPattern() {
        return Pattern.compile("^$|[^" + Pattern.quote(separator.toString()) + "]+");
    }

    /**
     * A fluent setter to setup the separator within a key in the flattened
     * JSON. The default separator is a dot(.).
     *
     * @param separator any character
     * @return this {@link JsonUnflattener}
     */
    public JsonUnflattener withSeparator(char separator) {
        this.separator = separator;
        return this;
    }

    /**
     * A fluent setter to setup the left and right brackets within a key in the
     * flattened JSON. The default left and right brackets are left square
     * bracket([) and right square bracket(]).
     *
     * @param leftBracket any character
     * @param rightBracket any character
     * @return this {@link JsonUnflattener}
     */
    public JsonUnflattener withLeftAndRightBrackets(char leftBracket,
            char rightBracket) {
        this.leftBracket = leftBracket;
        this.rightBracket = rightBracket;
        return this;
    }

    /**
     * Returns a JSON string of nested objects by the given flattened JSON
     * string.
     *
     * @return a JSON string of nested objects
     */
    public BsonValue unflatten() {
        if (root.isArray()) {
            return unflattenArray(root.asArray());
        }

        if (!root.isDocument()) {
            return root;
        }

        BsonDocument flattened = root.asDocument();
        BsonValue unflattened = flattened.keySet().isEmpty() ? new BsonDocument() : null;

        for (String key : flattened.keySet()) {
            BsonValue currentVal = unflattened;
            String objKey = null;
            Integer aryIdx = null;

            Matcher matcher = keyPartPattern().matcher(key);
            while (matcher.find()) {
                String keyPart = matcher.group();

                if (objKey != null ^ aryIdx != null) {
                    if (isJsonArray(keyPart)) {
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
                    if (isJsonArray(keyPart)) {
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

                if (unflattened == null) {
                    unflattened = currentVal;
                }
            }

            setUnflattenedValue(flattened, key, currentVal, objKey, aryIdx);
        }

        return unflattened;
    }

    private BsonArray unflattenArray(BsonArray array) {
        BsonArray unflattenArray = new BsonArray();

        array.forEach((value) -> {
            if (value.isArray()) {
                unflattenArray.add(unflattenArray(value.asArray()));
            } else if (value.isDocument()) {
                unflattenArray.add(new JsonUnflattener(value.asDocument())
                        .withSeparator(separator).unflatten());
            } else {
                unflattenArray.add(value);
            }
        });

        return unflattenArray;
    }

    private String extractKey(String keyPart) {
        if (keyPart.matches(objectComplexKey())) {
            keyPart = keyPart.replaceAll(
                    "^" + Pattern.quote(leftBracket.toString()) + "\\s*\"", "");
            keyPart = keyPart.replaceAll(
                    "\"\\s*" + Pattern.quote(rightBracket.toString()) + "$", "");
        }
        return keyPart;
    }

    private Integer extractIndex(String keyPart) {
        return Integer.valueOf(keyPart);

    }

    private boolean isJsonArray(String keyPart) {
        return keyPart.matches("\\d+");
    }

    private BsonValue findOrCreateJsonArray(BsonValue currentVal, String objKey,
            Integer aryIdx) {
        if (objKey != null) {
            BsonDocument jsonObj = currentVal.asDocument();

            if (jsonObj.get(objKey) == null) {
                BsonValue ary = new BsonArray();
                jsonObj.put(objKey, ary);

                return ary;
            }

            return jsonObj.get(objKey);
        } else { // aryIdx != null
            BsonArray jsonAry = currentVal.asArray();

            if (jsonAry.size() <= aryIdx || jsonAry.get(aryIdx).equals(BsonNull.VALUE)) {
                BsonValue ary = new BsonArray();
                assureJsonArraySize(jsonAry, aryIdx);
                jsonAry.set(aryIdx, ary);

                return ary;
            }

            return jsonAry.get(aryIdx);
        }
    }

    private BsonValue findOrCreateJsonObject(BsonValue currentVal, String objKey,
            Integer aryIdx) {
        if (objKey != null) {
            BsonDocument jsonObj = currentVal.asDocument();

            if (jsonObj.get(objKey) == null) {
                BsonValue obj = new BsonDocument();
                jsonObj.put(objKey, obj);

                return obj;
            }

            return jsonObj.get(objKey);
        } else { // aryIdx != null
            BsonArray jsonAry = currentVal.asArray();

            if (jsonAry.size() <= aryIdx || jsonAry.get(aryIdx).equals(BsonNull.VALUE)) {
                BsonValue obj = new BsonDocument();
                assureJsonArraySize(jsonAry, aryIdx);
                jsonAry.set(aryIdx, obj);

                return obj;
            }

            return jsonAry.get(aryIdx);
        }
    }

    private void setUnflattenedValue(BsonDocument flattened, String key,
            BsonValue currentVal, String objKey, Integer aryIdx) {
        BsonValue val = flattened.get(key);
        if (objKey != null) {
            if (val.isArray()) {
                BsonValue jsonArray = new BsonArray();
                val.asArray().forEach((arrayVal) -> {
                    jsonArray.asArray().add(newJsonUnflattener(
                            arrayVal).unflatten());
                });
                currentVal.asDocument().put(objKey, jsonArray);
            } else {
                currentVal.asDocument().put(objKey, val);
            }
        } else { // aryIdx != null
            assureJsonArraySize(currentVal.asArray(), aryIdx);
            currentVal.asArray().set(aryIdx, val);
        }
    }

    private JsonUnflattener newJsonUnflattener(BsonValue json) {
        JsonUnflattener jf = new JsonUnflattener(json);

        if (leftBracket != null && rightBracket != null) {
            jf.withLeftAndRightBrackets(leftBracket, rightBracket);
        }
        if (separator != null) {
            jf.withSeparator(separator);
        }
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
