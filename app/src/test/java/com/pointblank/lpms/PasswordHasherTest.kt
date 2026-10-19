package com.pointblank.lpms

import com.pointblank.lpms.data.security.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test fun correctPasswordVerifies() {
        val stored = PasswordHasher.hash("password123")
        assertTrue(PasswordHasher.verify("password123", stored))
    }

    @Test fun wrongPasswordFails() {
        val stored = PasswordHasher.hash("password123")
        assertFalse(PasswordHasher.verify("password124", stored))
    }

    @Test fun hashIsSaltedAndNotPlaintext() {
        val a = PasswordHasher.hash("password123")
        val b = PasswordHasher.hash("password123")
        assertNotEquals(a, b)
        assertFalse(a.contains("password123"))
    }

    @Test fun malformedStoredValueFailsSafely() {
        assertFalse(PasswordHasher.verify("password123", "garbage"))
    }
}
