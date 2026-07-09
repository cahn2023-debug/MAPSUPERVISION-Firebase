package com.mapsupervision.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityWorkspaceShellTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )

    @Test
    fun launch_with_runtime_permissions_reaches_workspace_shell() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Bản đồ")).check(matches(isDisplayed()))
            onView(withText("Tiến độ")).check(matches(isDisplayed()))
            onView(withText("Nhập liệu")).check(matches(isDisplayed()))
        }
    }
}
