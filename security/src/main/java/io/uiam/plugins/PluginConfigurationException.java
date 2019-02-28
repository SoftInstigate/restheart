/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uiam.plugins;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginConfigurationException extends Exception {
    private static final long serialVersionUID = -107523180700714817L;

    public PluginConfigurationException() {
        super();
    }

    public PluginConfigurationException(String message) {
        super(message);
    }

    public PluginConfigurationException(String message, Throwable t) {
        super(message, t);
    }
}
