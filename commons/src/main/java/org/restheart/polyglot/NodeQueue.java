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