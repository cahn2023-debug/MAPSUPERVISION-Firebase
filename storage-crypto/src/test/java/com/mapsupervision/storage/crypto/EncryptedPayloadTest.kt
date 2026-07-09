package com.mapsupervision.storage.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedPayloadTest {
    @Test
    fun payload_preserves_iv_and_ciphertext() {
        val payload = EncryptedPayload(
            iv = byteArrayOf(1, 2, 3),
            payload = byteArrayOf(4, 5, 6)
        )

        assertArrayEquals(byteArrayOf(1, 2, 3), payload.iv)
        assertArrayEquals(byteArrayOf(4, 5, 6), payload.payload)
    }

    @Test
    fun diagnostics_report_constructor_values() {
        val diagnostics = KeyDiagnostics(
            isHardwareBacked = true,
            isStrongBoxBacked = false
        )

        assertEquals(true, diagnostics.isHardwareBacked)
        assertEquals(false, diagnostics.isStrongBoxBacked)
    }
}
