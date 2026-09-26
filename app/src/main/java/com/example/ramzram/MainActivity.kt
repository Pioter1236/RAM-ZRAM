package com.example.ramzram

import android.app.Activity
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {
    companion object { const val REQ_OVERLAY = 100 }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 60, 60, 60) }
        val start = Button(this).apply { text = "START nakladki" }
        val stop = Button(this).apply { text = "STOP nakladki" }
        layout.addView(start); layout.addView(stop)
        setContentView(layout)
        start.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), REQ_OVERLAY)
            } else startOverlay()
        }
        stop.setOnClickListener { stopService(Intent(this, OverlayService::class.java)) }
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == REQ_OVERLAY && Settings.canDrawOverlays(this)) startOverlay()
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}