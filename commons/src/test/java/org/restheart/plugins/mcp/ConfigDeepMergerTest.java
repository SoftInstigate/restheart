/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2024 - 2026 SoftInstigate
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
package org.restheart.plugins.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class ConfigDeepMergerTest {

    @Test
    public void bothNull_returnsEmptyMap() {
        var merged = ConfigDeepMerger.merge(null, null);
        assertTrue(merged.isEmpty());
    }

    @Test
    public void onlyBase_returnsBaseUnchanged() {
        var base = Map.<String, Object>of("description", "from code");
        var merged = ConfigDeepMerger.merge(base, null);
        assertEquals("from code", merged.get("description"));
    }

    @Test
    public void onlyOverlay_returnsOverlay() {
        var overlay = Map.<String, Object>of("description", "from operator");
        var merged = ConfigDeepMerger.merge(null, overlay);
        assertEquals("from operator", merged.get("description"));
    }

    @Test
    public void scalarConflict_operatorWins() {
        var base = Map.<String, Object>of("description", "default");
        var overlay = Map.<String, Object>of("description", "override");
        var merged = ConfigDeepMerger.merge(base, overlay);
        assertEquals("override", merged.get("description"));
    }

    @Test
    public void nestedObject_mergesRecursively_operatorWinsOnConflict() {
        var base = Map.<String, Object>of("auth", Map.of("scope", "read", "issuer", "restheart"));
        var overlay = Map.<String, Object>of("auth", Map.of("scope", "read-write"));
        var merged = ConfigDeepMerger.merge(base, overlay);

        @SuppressWarnings("unchecked")
        var auth = (Map<String, Object>) merged.get("auth");
        assertEquals("read-write", auth.get("scope"));
        assertEquals("restheart", auth.get("issuer"));
    }

    @Test
    public void examples_areConcatenated_defaultsFirst() {
        var base = Map.<String, Object>of("examples", List.of(Map.of("description", "default example")));
        var overlay = Map.<String, Object>of("examples", List.of(Map.of("description", "operator example")));
        var merged = ConfigDeepMerger.merge(base, overlay);

        @SuppressWarnings("unchecked")
        var examples = (List<Map<String, Object>>) merged.get("examples");
        assertEquals(2, examples.size());
        assertEquals("default example", examples.get(0).get("description"));
        assertEquals("operator example", examples.get(1).get("description"));
    }

    @Test
    public void actions_operatorReplacesDefaultEntirely_notMergedPerKey() {
        var base = Map.<String, Object>of("actions", Map.of(
                "query", Map.of("method", "GET"),
                "create", Map.of("method", "POST")));
        var overlay = Map.<String, Object>of("actions", Map.of(
                "query", Map.of("method", "GET", "description", "operator description")));
        var merged = ConfigDeepMerger.merge(base, overlay);

        @SuppressWarnings("unchecked")
        var actions = (Map<String, Object>) merged.get("actions");
        // "create" from the default is gone: the operator's actions map replaced it wholesale
        assertEquals(1, actions.size());
        assertTrue(actions.containsKey("query"));
    }

    @Test
    public void keyOnlyInBase_isPreserved() {
        var base = Map.<String, Object>of("description", "default", "kind", "service");
        var overlay = Map.<String, Object>of("description", "override");
        var merged = ConfigDeepMerger.merge(base, overlay);
        assertEquals("service", merged.get("kind"));
    }

    @Test
    public void doesNotMutateInputs() {
        var base = new java.util.HashMap<String, Object>(Map.of("description", "default"));
        var overlay = new java.util.HashMap<String, Object>(Map.of("description", "override"));
        ConfigDeepMerger.merge(base, overlay);
        assertEquals("default", base.get("description"));
        assertEquals("override", overlay.get("description"));
    }

    @Test
    public void emptyOverlay_leavesBaseUntouched() {
        var base = Map.<String, Object>of("description", "default");
        var merged = ConfigDeepMerger.merge(base, Map.of());
        assertEquals("default", merged.get("description"));
        assertNull(ConfigDeepMerger.merge(null, Map.of()).get("description"));
    }
}
