/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bson.BsonArray;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

public class PipelineSummarizerTest {

    @Test
    public void emptyArray_emptyString() {
        assertEquals("", PipelineSummarizer.summarize(new BsonArray()));
    }

    @Test
    public void notAnArray_emptyString() {
        assertEquals("", PipelineSummarizer.summarize(new BsonInt32(1)));
    }

    @Test
    public void nullInput_emptyString() {
        assertEquals("", PipelineSummarizer.summarize(null));
    }

    @Test
    public void singleStage_operatorName() {
        var stages = BsonArray.parse("[{\"$match\": {\"status\": \"active\"}}]");
        assertEquals("$match", PipelineSummarizer.summarize(stages));
    }

    @Test
    public void multipleStages_joinedInOrder() {
        var stages = BsonArray.parse("[{\"$match\": {}}, {\"$group\": {}}, {\"$sort\": {}}]");
        assertEquals("$match → $group → $sort", PipelineSummarizer.summarize(stages));
    }

    @Test
    public void multiKeyStage_keysJoinedWithPlus() {
        var stages = BsonArray.parse("[{\"$match\": {}, \"$comment\": \"x\"}]");
        assertEquals("$match+$comment", PipelineSummarizer.summarize(stages));
    }

    @Test
    public void emptyStageDocument_questionMark() {
        var stages = BsonArray.parse("[{}]");
        assertEquals("?", PipelineSummarizer.summarize(stages));
    }
}
