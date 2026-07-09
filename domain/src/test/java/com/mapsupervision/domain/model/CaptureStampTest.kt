package com.mapsupervision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CaptureStampTest {

    @Test
    fun `resolvedLocationText prefers address then coordinates`() {
        val withAddress = CaptureStamp(
            timestampMs = 0L,
            latitude = 10.2,
            longitude = 106.3,
            address = "Ward 1, District 3",
            note = "",
            bearingDeg = 0f
        )
        val withCoordinates = withAddress.copy(address = "")
        val withoutLocation = withCoordinates.copy(latitude = null, longitude = null)

        assertEquals("Ward 1, District 3", withAddress.resolvedLocationText(missingLocationText = "Missing"))
        assertEquals("10.20000, 106.30000", withCoordinates.resolvedLocationText(Locale.US, "Missing"))
        assertEquals("Missing", withoutLocation.resolvedLocationText(Locale.US, "Missing"))
        assertEquals("10.2000, 106.3000", withCoordinates.coordinateText(Locale.US))
        assertNull(withoutLocation.coordinateText(Locale.US))
    }
}
