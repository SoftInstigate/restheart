/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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
package org.restheart.test.plugins.services;

import static org.restheart.utils.GsonUtils.object;

import java.util.function.BiConsumer;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;

/**
 * functional style, test service
 */
@RegisterPlugin(name="test",description = "foo")
public class FunctionalService implements JsonService {
    @Override
    public BiConsumer<JsonRequest, JsonResponse> handle() {
        return (req, res) -> {
            (switch(req.getMethod()) {
                case GET -> body().andThen(ok());
                case OPTIONS -> handleOptions();
                default -> error();
            }).accept(req, res);
        };
    }

    private BiConsumer<JsonRequest, JsonResponse> body() {
        return (req, resp) -> resp.setContent(object().put("msg", "hello world"));
    }

    private BiConsumer<JsonRequest, JsonResponse> ok() {
        return (req, resp) -> resp.setStatusCode(201);
    }

    private BiConsumer<JsonRequest, JsonResponse> error() {
        return (req, resp) -> resp.setStatusCode(500);
    }
}
