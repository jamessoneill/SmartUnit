// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests without @Tag("smartunit") always execute normally. */
class OrdinaryTest {
    @Test void notOptedIn() {
        assertEquals(4, 2 + 2);
    }
}
