package com.example.policy.handler;

import com.example.policy.api.PolicyException;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;

final class HandlerSupport {
    private HandlerSupport() {
    }

    static <T> void uniqueIds(Collection<T> values, Function<T, String> idFn, String module) {
        var ids = new HashSet<String>();
        for (T item : values) {
            if (!ids.add(idFn.apply(item))) {
                throw PolicyException.badRequest(module + " 中存在重复规则 ID: " + idFn.apply(item));
            }
        }
    }
}
