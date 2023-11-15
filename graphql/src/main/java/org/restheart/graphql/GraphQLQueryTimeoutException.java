/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package org.restheart.graphql;

import graphql.execution.AbortExecutionException;

/**
 *
 * @author uji
 */
public class GraphQLQueryTimeoutException extends AbortExecutionException {
    public GraphQLQueryTimeoutException(String message) {
        super(message);
    }
}
