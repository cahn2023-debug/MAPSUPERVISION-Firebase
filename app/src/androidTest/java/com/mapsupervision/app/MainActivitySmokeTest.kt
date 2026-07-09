package com.mapsupervision.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapsupervision.app.widget.WidgetEntryPoint
import dagger.hilt.EntryPoints
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun mainActivity_launches_without_crashing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull(it)
        }
    }

    @Test
    fun widget_entry_point_resolves_core_repositories() {
        val app = ApplicationProvider.getApplicationContext<MapSupervisionApplication>()
        val entryPoint = EntryPoints.get(app, WidgetEntryPoint::class.java)

        assertNotNull(entryPoint.dailyLogRepository())
        assertNotNull(entryPoint.activeProjectRepository())
    }
}
