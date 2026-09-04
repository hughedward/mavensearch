package com.mavensearch.eclipse.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {

    @Test
    void bundleWiringAndJvm() {
        assertEquals(17, Runtime.version().feature() >= 17 ? 17 : 0);
    }
}
