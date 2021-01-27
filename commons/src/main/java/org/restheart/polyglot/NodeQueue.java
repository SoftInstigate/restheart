/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2021 SoftInstigate
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
package org.restheart.polyglot;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

public class NodeQueue {
    // a concurrent queue shared with Node
    private final Queue<Object> queue;
    private boolean runningOnNode = false;

    private NodeQueue() {
      this.queue = new LinkedBlockingDeque<>();
    }

    private static NodeQueue instance;

    public static NodeQueue instance() {
        if (instance == null) {
            instance = new NodeQueue();
        }

        return instance;
    }

    public Queue<Object> queue() {
        return queue;
    }

    public boolean isRunningOnNode() {
        return runningOnNode;
    }

    public void setAsRunningOnNode() {
        this.runningOnNode = true;
    }
  }
