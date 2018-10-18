/*
 * uIAM - the IAM for microservices
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
package io.uiam.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ExecutorServiceSingleton {

    /**
     *
     * @return
     */
    public static ExecutorServiceSingleton getInstance() {
        return ExecutorServiceSingletonHolder.INSTANCE;
    }

    private final ExecutorService executorService;

    private ExecutorServiceSingleton() {
        this.executorService = Executors.newFixedThreadPool(100);
    }

    /**
     * @return the executorService
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    private static class ExecutorServiceSingletonHolder {

        private static final ExecutorServiceSingleton INSTANCE = new ExecutorServiceSingleton();

        private ExecutorServiceSingletonHolder() {
        }
    }
}
