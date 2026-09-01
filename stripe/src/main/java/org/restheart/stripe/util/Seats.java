/*-
 * ========================LICENSE_START=================================
 * restheart-stripe
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.stripe.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.restheart.plugins.stripe.PlanConfig;
import org.restheart.plugins.stripe.SubscriptionState;

/**
 * Combines a plan's seat configuration with the current subscription state to answer
 * "how many seats does this entity have". Not part of the SPI: it needs both
 * {@link PlanConfig} (module configuration) and {@link SubscriptionState} (persisted
 * state), so it lives here rather than on either data type.
 */
public final class Seats {

    private Seats() {
    }

    /**
     * @param plan  the entity's current plan configuration, or {@code null} if unknown
     * @param state the entity's subscription state
     * @return the seat limit: the plan's {@code max} for {@code capped}, the purchased
     *         {@link SubscriptionState#seats()} for {@code per-seat}, or {@code null} for
     *         {@code unlimited} / an unconfigured plan
     */
    public static Integer limit(PlanConfig plan, SubscriptionState state) {
        if (plan == null || plan.seats() == null) {
            return null;
        }
        return switch (plan.seats().mode()) {
            case UNLIMITED -> null;
            case CAPPED -> plan.seats().max();
            case PER_SEAT -> state.seats();
        };
    }

    /**
     * @param limit         the seat limit, or {@code null} for unlimited
     * @param licensedCount the number of currently licensed members
     * @return {@code limit - licensedCount}, or {@code null} if {@code limit} is {@code null}
     */
    public static Integer available(Integer limit, int licensedCount) {
        return limit == null ? null : Math.max(0, limit - licensedCount);
    }

    /**
     * Days elapsed since the entity crossed into the over-limit state — a fact, not a
     * policy. The module does not decide what should happen at any particular value; a
     * deployment composes it with the {@code gte}/{@code lte} ACL predicates to build
     * whatever enforcement (or none) it wants, e.g.
     * {@code gte(@subscription.seats.over_limit_days, 5)}.
     *
     * @param state the entity's subscription state
     * @return whole days elapsed since {@link SubscriptionState#overLimitSince()}, or
     *         {@code null} if the entity is not over limit
     */
    public static Integer overLimitDays(SubscriptionState state) {
        if (state.overLimitSince() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(state.overLimitSince(), Instant.now());
    }
}
