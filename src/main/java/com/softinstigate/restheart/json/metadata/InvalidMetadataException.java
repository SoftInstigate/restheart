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
package com.softinstigate.restheart.json.metadata;

/**
 *
 * @author uji
 */
public class InvalidMetadataException extends Exception
{
    public InvalidMetadataException()
    {
        super();
    }
    
    public InvalidMetadataException(String message)
    {
        super(message);
    }
    
    public InvalidMetadataException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
