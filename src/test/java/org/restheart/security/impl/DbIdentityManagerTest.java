/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.security.impl;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.handlers.files.GetFileHandlerTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DbIdentityManagerTest {
    private static final Logger LOG
            = LoggerFactory.getLogger(GetFileHandlerTest.class);

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public DbIdentityManagerTest() {
    }

    @Test
    public void testHashedPassword() {
        final String plain = "secret"; // $2a$10$vDQSOahBQgjl6fL8Si9KPOAgb9OYhDRHnPKEg35E0.FrHevA2zcjq 

        String hashed = BCrypt.hashpw(plain, BCrypt.gensalt());

        Assert.assertTrue("check plain pwd",
                DbIdentityManager.checkPassword(
                        false,
                        plain.toCharArray(),
                        plain.toCharArray()));
        
        Assert.assertFalse("check plain pwd",
                DbIdentityManager.checkPassword(
                        false,
                        "wrong".toCharArray(),
                        plain.toCharArray()));

        Assert.assertTrue("check hashed pwd",
                DbIdentityManager.checkPassword(
                        true,
                        plain.toCharArray(),
                        hashed.toCharArray()));
        
        Assert.assertFalse("check hashed pwd",
                DbIdentityManager.checkPassword(
                        true,
                        "wrong".toCharArray(),
                        hashed.toCharArray()));
        
        Assert.assertFalse("check hashed pwd",
                DbIdentityManager.checkPassword(
                        true,
                        plain.toCharArray(),
                        "wrong".toCharArray()));
    }
}
