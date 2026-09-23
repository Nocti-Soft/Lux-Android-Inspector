package com.noctisoft.layoutmeasurement.sample

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Real modal windows, deliberately with no inspector-specific attachment code. */
class BottomSheetShowcaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bottom_sheet_showcase)
        val content = findViewById<View>(R.id.showcase_content)
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        findViewById<View>(R.id.show_xml_bottom_sheet).setOnClickListener {
            if (supportFragmentManager.findFragmentByTag("xml_showcase_sheet") == null) {
                XmlShowcaseSheet().show(supportFragmentManager, "xml_showcase_sheet")
            }
        }
        findViewById<ComposeView>(R.id.compose_sheet_launcher).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MaterialTheme { ComposeSheetShowcase() } }
        }
    }
}
