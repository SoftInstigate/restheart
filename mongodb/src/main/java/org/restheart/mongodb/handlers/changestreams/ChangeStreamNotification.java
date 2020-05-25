/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
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
