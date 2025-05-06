package org.restheart.security;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.restheart.exchange.Request;

/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

 /**
  * Allows to modify the predicate of a BaseAclPermission
  */
public class BaseAclPermissionTransformer {
    Predicate<BaseAclPermission> resolve;
    BiPredicate<BaseAclPermission, Request<?>> additionalPredicate;

    /**
     *
     * @param resolve a predicate that returs true on permissions that should be transformed
     * @param additionalPredicate the predicate to AND compose with the BaseAclPermission's predicate
     */
    public BaseAclPermissionTransformer(Predicate<BaseAclPermission> resolve, BiPredicate<BaseAclPermission, Request<?>> additionalPredicate) {
        Objects.nonNull(resolve);
        Objects.nonNull(additionalPredicate);

        this.resolve = resolve;
        this.additionalPredicate = additionalPredicate;
    }

    public void transform(BaseAclPermission permission) {
        if (resolve.test(permission)) {
            permission.setPredicate(permission.gePredicate().and(top(additionalPredicate, permission)));
        }
    }

    /**
     *  allows to combine predicate with bipredicate
     * @param bp
     * @param permission
     * @return
     */
    private static Predicate<Request<?>> top(BiPredicate<BaseAclPermission, Request<?>> bp, BaseAclPermission permission) {
        return r -> bp.test(permission, r);
    }
}
