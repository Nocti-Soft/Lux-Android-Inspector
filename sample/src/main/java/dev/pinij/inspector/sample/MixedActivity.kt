package dev.pinij.inspector.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class MixedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mixed)
        findViewById<ComposeView>(R.id.compose_island).setContent {
            Row {
                Text("island A", Modifier.testTag("island_a").size(120.dp, 40.dp))
                Spacer(Modifier.width(20.dp))
                Text("island B", Modifier.testTag("island_b").size(120.dp, 40.dp))
            }
        }
    }
}
