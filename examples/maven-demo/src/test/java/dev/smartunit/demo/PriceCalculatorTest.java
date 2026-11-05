// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("smartunit")
class PriceCalculatorTest {
    @Test
    void calculatesSubtotal() {
        assertEquals(60, new PriceCalculator().subtotal(20, 3));
    }
}
