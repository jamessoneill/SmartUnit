// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.util.Map;
import java.util.TreeMap;

/** Data from a previously SUCCESSFUL test execution only. */
public record CacheEntry(String testId, String testClassHash,
                         String scopeHash, Map<String, String> dependencies) {
    public CacheEntry {
        dependencies = Map.copyOf(new TreeMap<>(dependencies));
    }
}
