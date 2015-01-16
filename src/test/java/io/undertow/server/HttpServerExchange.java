/*
 * RESTHeart - the data REST API server
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
package io.undertow.server;

import io.undertow.util.AbstractAttachable;
import io.undertow.util.HttpString;

/**
 * A mock for io.undertow.server.HttpServerExchange
 * The original class is final so it can't be mocked directly.
 * Then use Mockito:
 * <code>HttpServerExchange ex = mock(HttpServerExchange.class);</code>
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public abstract class HttpServerExchange extends AbstractAttachable {

    public HttpServerExchange() {
    }
    
    public abstract String getRequestPath();
    
    public abstract HttpString getRequestMethod();

}
