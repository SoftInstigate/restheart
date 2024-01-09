/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
package org.restheart.mongodb.handlers.changestreams;

/**
 *
 * @author omartrasatti
 */
public class ChangeStreamNotification {

    private final SessionKey sessionKey;
    private final String notificationMessage;

    public ChangeStreamNotification(SessionKey sessionKey, String notificationMessage) {
        this.sessionKey = sessionKey;
        this.notificationMessage = notificationMessage;
    }

    public SessionKey getSessionKey() {
        return this.sessionKey;
    }

    public String getNotificationMessage() {
        return this.notificationMessage;
    }
}
