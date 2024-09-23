package org.restheart.security.mechanisms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JwtAuthenticationMechanismTest {
    @Test
    void testValidateKeyComplexity() {
        assertFalse(JwtAuthenticationMechanism.validateKeyComplexity("simplepassword123"));
        assertTrue(JwtAuthenticationMechanism.validateKeyComplexity("C0mpl3x@JWT!Key$With@UpperAndLowercase"));
    }
}
