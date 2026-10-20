package xyz.felixb.congatrol

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import xyz.felixb.congatrol.di.requestDemoRobot
import xyz.felixb.congatrol.widget.RobotWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before setContent, so the invented robot is in place before any screen asks for one.
        if (savedInstanceState == null && isDebuggable() && demoRequested()) requestDemoRobot()
        setContent {
            // UIAutomator matches by resource-id, and Compose emits none of its own. Setting this
            // once at the root turns every `Modifier.testTag(...)` below into one.
            App(modifier = Modifier.semantics { testTagsAsResourceId = true })
        }
    }

    override fun onStop() {
        super.onStop()
        // The robot may have been started, named or moved to another address in here.
        RobotWidget.refresh(this)
    }

    /** `demo` as a launch extra: `adb shell am start ... --ez demo true`. */
    private fun demoRequested(): Boolean {
        val extras = intent?.extras ?: return false
        return extras.getBoolean("demo", false) || extras.getString("demo") == "true"
    }

    private fun isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
