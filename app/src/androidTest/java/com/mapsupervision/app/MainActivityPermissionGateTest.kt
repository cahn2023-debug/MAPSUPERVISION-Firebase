package com.mapsupervision.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityPermissionGateTest {

    @Test
    fun launch_shows_permission_gate_when_permissions_are_missing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Cấp quyền truy cập")).check(matches(isDisplayed()))
            onView(withText("Cấp quyền và tiếp tục")).check(matches(isDisplayed()))
        }
    }
}
