package com.wittycat.knowledgebase.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 具名线程工厂：线程名带业务前缀与自增序号，便于 jstack 排查时定位线程归属
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final boolean daemon;

    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, false);
    }

    public NamedThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix + "-";
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable task) {
        return new Thread(null, task, namePrefix + nextId.getAndIncrement(), 0, daemon);
    }
}
