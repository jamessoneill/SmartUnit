// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.demo;

public final class PriceCalculator {
    public int subtotal(int unitPrice, int quantity) {
        if (unitPrice < 0 || quantity < 0) throw new IllegalArgumentException("negative inputs");
        return unitPrice * quantity;
    }
}
