/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
