/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * =========================LICENSE_END==================================
 */
package org.restheart.security.analysis;

import org.restheart.security.MongoPermissions;

/**
 * The part of a permission that is not in its predicate.
 *
 * <p>A permission's {@code mongo} block carries switches that the MongoDB plugins turn into
 * <em>additional</em> predicates at load time, through {@code BaseAclPermissionTransformer}. They
 * are Java, so there is no text to read, but their conditions are fixed and known — which makes
 * them exactly decidable here, unlike an authorizer written by somebody else.
 *
 * <p>They matter because they refuse what the predicate allows: with
 * {@code "allowManagementRequests": false} — the default — a permission whose predicate matches
 * {@code DELETE /coll} still does not permit dropping it. A catalog that looked only at the
 * predicate would announce an action nobody can perform.
 *
 * <table>
 * <caption>The three switches</caption>
 * <tr><th>Switch</th><th>Refuses</th></tr>
 * <tr><td>{@code allowManagementRequests}</td>
 *     <td>the data management API: databases and collections other than reading them or posting a
 *     document, indexes, file buckets, the schema store, and every {@code _meta}</td></tr>
 * <tr><td>{@code allowBulkPatch}, {@code allowBulkDelete}</td>
 *     <td>a {@code PATCH} or {@code DELETE} over many documents at once</td></tr>
 * <tr><td>{@code allowWriteMode}</td>
 *     <td>a write carrying {@code ?wm=}</td></tr>
 * </table>
 */
public final class MongoGates {

    private MongoGates() {
    }

    /**
     * What the action a listing is describing asks of MongoDB — enough to know which switches
     * apply, and no more.
     *
     * @param management whether it is a data management request: anything on a database or a
     *                   collection that is not a read or a document creation, an index, a file
     *                   bucket's own resource, the schema store, or a {@code _meta}
     * @param bulkPatch  whether it patches many documents at once
     * @param bulkDelete whether it deletes many documents at once
     */
    public record Action(boolean management, boolean bulkPatch, boolean bulkDelete) {

        /** An ordinary read or single-document write, which no switch refuses. */
        public static final Action ORDINARY = new Action(false, false, false);

        /** A data management request: the switch that gates it is off unless declared. */
        public static Action managementRequest() {
            return new Action(true, false, false);
        }
    }

    /**
     * Whether {@code permission} permits {@code action} once its {@code mongo} switches are taken
     * into account.
     *
     * <p>Always {@link Truth#TRUE} or {@link Truth#FALSE}: these are properties of the permission
     * and of the action, both in hand. {@code allowWriteMode} is not among them because it refuses
     * only a call that carries {@code ?wm=}, and a call without it is always available — so it can
     * never make a resource disappear.
     */
    public static Truth allows(MongoPermissions permissions, Action action) {
        if (permissions == null) {
            return Truth.TRUE;
        }

        if (action.management() && !permissions.isAllowManagementRequests()) {
            return Truth.FALSE;
        }

        if (action.bulkPatch() && !permissions.isAllowBulkPatch()) {
            return Truth.FALSE;
        }

        if (action.bulkDelete() && !permissions.isAllowBulkDelete()) {
            return Truth.FALSE;
        }

        return Truth.TRUE;
    }
}
