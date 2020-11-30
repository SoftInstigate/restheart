/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.exchange;

import io.undertow.connector.PooledByteBuffer;
import java.io.IOException;

/**
 *
 * A buffered exchage stores content in a PooledByteBuffer
 *
 * This makes possibile using it in proxied requests.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public interface BufferedExchange<T> {
    /**
     * reads data from the buffer converting it to T
     * 
     * @return
     * @throws IOException 
     */
    public abstract T readContent() throws IOException;

    /**
     * writes data the buffer from T 
     * 
     * @param content
     * @throws IOException 
     */
    public abstract void writeContent(T content) throws IOException;

    public PooledByteBuffer[] getBuffer();
    
    public void setBuffer(PooledByteBuffer[] raw);

    public boolean isContentAvailable();
}
