package com.kimsooin77.sync.sync;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PerformanceSqlInspector implements StatementInspector {

    private static final ConcurrentLinkedQueue<String> STATEMENTS = new ConcurrentLinkedQueue<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    static void clear() {
        STATEMENTS.clear();
    }

    static List<String> snapshot() {
        return List.copyOf(STATEMENTS);
    }
}
