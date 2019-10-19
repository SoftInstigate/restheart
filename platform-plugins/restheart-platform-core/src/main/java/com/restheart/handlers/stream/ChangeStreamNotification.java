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
package com.restheart.handlers.stream;

/**
 *
 * @author omartrasatti
 */
public class ChangeStreamNotification {
    
    private final String streamKey;
    private final String notificationMessage;
    
    public ChangeStreamNotification(String streamKey, String notificationMessage) {
        this.streamKey = streamKey;
        this.notificationMessage = notificationMessage;
    }
    
    public String getStreamKey() {
        return this.streamKey;
    }
    
    public String getNotificationMessage() {
        return this.notificationMessage;
    }
    
}
