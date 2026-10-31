/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import org.graalvm.polyglot.Value;
import org.restheart.emails.EmailSender;

/**
 * The {@code mail} capability of the SDK, over the {@code emails} provider, available when
 * email is configured.
 *
 * <p>{@code mail.send({ to, name?, subject, html })} sends on the calling thread and returns
 * when the SMTP transaction is over; {@code mail.sendAsync(...)} hands it to the shared virtual
 * threads executor and returns at once, which is what a plugin on the request path usually
 * wants.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhMail {
    private final EmailSender sender;

    /**
     * @param sender the configured sender
     */
    public RhMail(EmailSender sender) {
        this.sender = sender;
    }

    /**
     * @param message {@code { to, name?, subject, html }}
     */
    public void send(Value message) {
        var m = Message.of(message);
        sender.sendEmail(m.to, m.name, m.subject, m.html);
    }

    /**
     * @param message {@code { to, name?, subject, html }}
     */
    public void sendAsync(Value message) {
        var m = Message.of(message);
        sender.sendEmailAsync(m.to, m.name, m.subject, m.html);
    }

    private record Message(String to, String name, String subject, String html) {
        static Message of(Value v) {
            if (v == null || !v.hasMembers()) {
                throw new IllegalArgumentException("mail.send() needs { to, subject, html }");
            }

            var to = GuestValues.string(v.getMember("to"), "to");
            var subject = GuestValues.string(v.getMember("subject"), "subject");
            var html = GuestValues.string(v.getMember("html"), "html");
            var name = v.hasMember("name") ? GuestValues.string(v.getMember("name"), "name") : null;

            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("mail.send() needs a recipient (to)");
            }
            if (subject == null) {
                throw new IllegalArgumentException("mail.send() needs a subject");
            }
            if (html == null) {
                throw new IllegalArgumentException("mail.send() needs an html body");
            }

            return new Message(to, name, subject, html);
        }
    }
}
