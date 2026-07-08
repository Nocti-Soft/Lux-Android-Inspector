package dev.pinij.inspector.sample

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
        findViewById<Button>(R.id.open_compose).setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
        findViewById<Button>(R.id.open_mixed).setOnClickListener {
            startActivity(Intent(this, MixedActivity::class.java))
        }
    }
}
