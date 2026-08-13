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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.stripe;

/**
 * Outcome of {@link SubscriptionOwnerProvider#grantLicense}. A plain {@code boolean} would
 * conflate three cases an HTTP endpoint must answer differently: no seat available is a
 * {@code 409}, an unknown member is a {@code 404}, and a member already licensed is a
 * successful no-op — none of which is "false" in the same sense.
 */
public enum LicenseGrantResult {
    /** The licence was granted. */
    GRANTED,
    /** The member already held a licence — idempotent, not an error. */
    ALREADY_LICENSED,
    /** No member with that id exists on this entity. */
    MEMBER_NOT_FOUND,
    /** The member exists and is unlicensed, but the seat limit is already reached. */
    NO_SEAT_AVAILABLE
}
