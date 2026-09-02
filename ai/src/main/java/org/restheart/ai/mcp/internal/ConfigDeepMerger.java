/*-
 * ========================LICENSE_START=================================
 * restheart-ai
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
package org.restheart.ai.mcp.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-merges a plugin's code-baked {@code defaultMcpConfig()} with the operator's
 * {@code mcp-config} from YAML, per the rule settled for the MCP framework
 * (restheart#615): scalars and nested objects merge recursively with the operator
 * winning on conflict, with two named exceptions —
 * <ul>
 *   <li>{@code examples}: the operator's list is <b>concatenated</b> after the
 *       default's (curated examples add up rather than replace each other);</li>
 *   <li>{@code actions}: the operator's map, if present, <b>replaces</b> the
 *       default's entirely (the operator wants full control over the action
 *       surface — including any nested {@code params} — not a per-key patch).</li>
 * </ul>
 */
public final class ConfigDeepMerger {

    private ConfigDeepMerger() {
    }

    /**
     * @param base    the code-baked defaults (may be {@code null})
     * @param overlay the operator-supplied overrides (may be {@code null})
     * @return a new map; neither argument is mutated
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overlay) {
        var merged = new LinkedHashMap<String, Object>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overlay == null) {
            return merged;
        }

        for (var entry : overlay.entrySet()) {
            var key = entry.getKey();
            var overlayValue = entry.getValue();
            var baseValue = merged.get(key);

            if ("examples".equals(key) && baseValue instanceof List<?> baseList && overlayValue instanceof List<?> overlayList) {
                var concatenated = new ArrayList<Object>(baseList);
                concatenated.addAll(overlayList);
                merged.put(key, concatenated);
            } else if (baseValue instanceof Map<?, ?> baseMap && overlayValue instanceof Map<?, ?> overlayMap && !"actions".equals(key)) {
                merged.put(key, merge((Map<String, Object>) baseMap, (Map<String, Object>) overlayMap));
            } else {
                // "actions" (present in overlay) and every other conflicting or
                // overlay-only key: the operator's value wins outright.
                merged.put(key, overlayValue);
            }
        }

        return merged;
    }
}
