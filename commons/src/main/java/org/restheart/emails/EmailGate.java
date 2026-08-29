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
package org.restheart.emails;

import org.restheart.exchange.Request;

/**
 * Asked before an email is sent, and told after it was not.
 *
 * <p>RESTHeart itself sends whatever it is asked to. A deployment that sends on behalf of many
 * tenants through one mail server has a reason to say no — one tenant sending too much spends the
 * sending reputation every other tenant is relying on — and that reason belongs to the deployment,
 * not here.
 *
 * <p>Register an implementation as {@code @RegisterPlugin(name = "email-gate")} and
 * {@link SmtpEmailSender} will consult it. With none registered nothing is refused, which is what
 * a single-tenant install wants.
 *
 * <p>Implementations must be cheap and must not throw: this sits in front of every email the
 * server sends, and a gate that fails should not take the mail down with it.
 *
 * @since 9.8.0
 */
public interface EmailGate {

    /**
     * Whether this email may be sent, and why not when it may not.
     *
     * @param allowed  whether to send
     * @param reason   why not, in a sentence somebody can act on — logged, and shown to whoever
     *                 is in a position to do something about it. Never null when refused: a
     *                 refusal without a reason leaves the operator guessing, and the person it
     *                 affects with nothing at all.
     */
    record Decision(boolean allowed, String reason) {
        private static final Decision ALLOWED = new Decision(true, null);

        public static Decision allow() {
            return ALLOWED;
        }

        public static Decision refuse(String reason) {
            return new Decision(false, reason);
        }
    }

    /**
     * Asked once per email, immediately before sending.
     *
     * <p>Counting belongs here rather than in the caller: a gate that both decides and counts
     * cannot disagree with itself about what was sent.
     *
     * @param request     the request this email arose from, or {@code null} when it arose from
     *                    none — a scheduled job, a webhook delivery. A gate that identifies the
     *                    tenant from the request has to decide what to do with null; refusing
     *                    everything anonymous is rarely what anyone wants.
     * @param to          the recipient. One address: a message to several is several calls, and
     *                    each one costs the same reputation.
     * @param ownSender   whether the tenant's own SMTP settings are being used rather than the
     *                    deployment's shared ones. A tenant spending their own reputation on their
     *                    own contract is usually not who a cap is protecting anyone from.
     */
    Decision check(Request<?> request, String to, boolean ownSender);
}
