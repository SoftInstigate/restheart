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
     * @param state     the entity's subscription state
     * @param graceDays the effective over-limit grace period in days, or {@code null} if it
     *                  never expires
     * @return when the over-limit grace period expires, or {@code null} if the entity is not
     *         over limit, or the grace period never expires
     */
    public static Instant graceExpiresAt(SubscriptionState state, Integer graceDays) {
        if (state.overLimitSince() == null || graceDays == null) {
            return null;
        }
        return state.overLimitSince().plus(graceDays, ChronoUnit.DAYS);
    }

    /**
     * @param state     the entity's subscription state
     * @param graceDays the effective over-limit grace period in days
     * @return {@code true} if the entity is over limit and its grace period has expired —
     *         every user of the entity is blocked ({@code @subscription.licensed} is
     *         {@code false} for all of them), regardless of their individual licence
     */
    public static boolean isBlocked(SubscriptionState state, Integer graceDays) {
        var expiresAt = graceExpiresAt(state, graceDays);
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
