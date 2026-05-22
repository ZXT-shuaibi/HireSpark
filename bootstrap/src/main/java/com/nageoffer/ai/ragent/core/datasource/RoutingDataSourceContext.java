package com.nageoffer.ai.ragent.core.datasource;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class RoutingDataSourceContext {

    private static final ThreadLocal<Deque<String>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private RoutingDataSourceContext() {
    }

    public static void push(String dataSourceName) {
        String name = normalize(dataSourceName);
        if (name == null) {
            return;
        }
        CONTEXT.get().push(name);
    }

    public static Optional<String> peek() {
        Deque<String> stack = CONTEXT.get();
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    public static Optional<String> pop() {
        Deque<String> stack = CONTEXT.get();
        if (stack.isEmpty()) {
            clear();
            return Optional.empty();
        }
        String value = stack.pop();
        if (stack.isEmpty()) {
            clear();
        }
        return Optional.of(value);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private static String normalize(String dataSourceName) {
        if (StrUtil.isBlank(dataSourceName)) {
            return null;
        }
        return dataSourceName.trim();
    }
}
