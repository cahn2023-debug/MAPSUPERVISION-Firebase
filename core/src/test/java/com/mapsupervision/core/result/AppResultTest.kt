package com.mapsupervision.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppResultTest {
    @Test
    fun success_wraps_value() {
        val result = AppResult.Success("ok")

        assertEquals("ok", result.data)
    }

    @Test
    fun error_wraps_throwable() {
        val throwable = IllegalStateException("boom")
        val result = AppResult.Error(throwable)

        assertSame(throwable, result.throwable)
    }
}
