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
package org.restheart.ai.util;

import java.util.Map;
import java.util.Optional;

import org.restheart.plugins.PluginsRegistry;

/**
 * Resolves a named {@code Provider<T>}'s supplied instance from the {@link PluginsRegistry},
 * caching the result per name — the pattern {@code autoEmbeddingInterceptor},
 * {@code rerankingInterceptor}, {@code vectorizeOperator}, and
 * {@code documentChunkingInterceptor} all need for their own
 * {@code override-ai-embedding-provider} / {@code override-ai-rerank-provider}
 * multi-tenant selection.
 *
 * <p>Resolved lazily (call this from request-handling code, not {@code @OnInit}) so
 * callers don't depend on plugin initialization order relative to the configured
 * provider — by the time requests are handled, every plugin's {@code @OnInit} has
 * already run. Cached per provider name (the caller owns and passes in its own cache;
 * this class is stateless) because the effective provider name can differ per request.
 *
 * <p>Returns {@link Optional#empty()} — never throws, never logs — for "not found",
 * "not enabled", or "wrong type"; callers decide what that means for them (a silent
 * skip, a warning, or a thrown exception all differ by call site).
 */
public final class PluginModelResolver {
    private PluginModelResolver() {
    }

    public static <T> Optional<T> resolve(PluginsRegistry registry, Map<String, T> cache, String providerName, Class<T> type) {
        var cached = cache.get(providerName);
        if (cached != null) {
            return Optional.of(cached);
        }

        var providerRecord = registry.getProviders().stream()
            .filter(p -> providerName.equals(p.getName()))
            .findFirst()
            .orElse(null);

        if (providerRecord == null || !providerRecord.isEnabled()) {
            return Optional.empty();
        }

        var provided = providerRecord.getInstance().get(null);
        if (!type.isInstance(provided)) {
            return Optional.empty();
        }

        var model = type.cast(provided);
        cache.put(providerName, model);
        return Optional.of(model);
    }
}
