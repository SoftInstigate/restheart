/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
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

package org.restheart.graphql.predicates;

import org.apache.commons.jxpath.JXPathContext;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

public interface PredicateOverJxPathCtx extends Predicate {
    @Override
    default boolean resolve(HttpServerExchange exchage) {
        return resolve(ExchangeWithBsonValue.jxPathCtx(exchage));
    }

    boolean resolve(JXPathContext doc);
}
