package com.noctisoft.layoutmeasurement.sample

import android.app.Application
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = Application::class)
class SampleEdgeToEdgeTest {
    @Test
    fun mainActivityLaysOutBehindSystemBars() = assertLaysOutBehindSystemBars(MainActivity::class.java)

    @Test
    fun composeActivityLaysOutBehindSystemBars() = assertLaysOutBehindSystemBars(ComposeActivity::class.java)

    @Test
    fun mixedActivityLaysOutBehindSystemBars() = assertLaysOutBehindSystemBars(MixedActivity::class.java)

    private fun assertLaysOutBehindSystemBars(activityClass: Class<out ComponentActivity>) {
        val activity = Robolectric.buildActivity(activityClass).setup().get()

        assertEquals(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION,
            activity.window.decorView.systemUiVisibility and
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION),
        )
    }
}
