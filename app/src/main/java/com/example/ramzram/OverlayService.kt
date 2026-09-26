package com.example.ramzram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.io.File

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var root: LinearLayout
    private lateinit var ramTv: TextView
    private lateinit var swpTv: TextView
    private lateinit var opLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var dp = 1f
    private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0
    private var rDownX = 0f; private var rDownY = 0f; private var rW = 0; private var rH = 0

    private val tick = object : Runnable {
        override fun run() { updateTexts(); handler.postDelayed(this, 1000) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        dp = resources.displayMetrics.density
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildView()
        wm.addView(root, params)
        handler.post(tick)
    }

    private fun buildView() {
        val prefs = getSharedPreferences("overlay", MODE_PRIVATE)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 0); y = prefs.getInt("y", 200)
            alpha = prefs.getInt("alpha", 80) / 100f
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(Color.argb(210, 0, 0, 0))
        }
        ramTv = TextView(this).apply { setTextColor(Color.WHITE) }
        swpTv = TextView(this).apply { setTextColor(Color.WHITE) }
        opLabel = TextView(this).apply { setTextColor(Color.WHITE); text = "Alfa: ${prefs.getInt("alpha", 80)}%" }
        val seek = SeekBar(this).apply { max = 100; progress = prefs.getInt("alpha", 80) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                params.alpha = p / 100f
                wm.updateViewLayout(root, params)
                opLabel.text = "Alfa: $p%"
                prefs.edit().putInt("alpha", p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val resize = TextView(this).apply { text = "⤢ rozmiar"; setTextColor(Color.CYAN) }
        resize.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { rDownX = e.rawX; rDownY = e.rawY; rW = root.width; rH = root.height; true }
                MotionEvent.ACTION_MOVE -> {
                    params.width = (rW + (e.rawX - rDownX)).toInt().coerceAtLeast((150 * dp).toInt())
                    params.height = (rH + (e.rawY - rDownY)).toInt().coerceAtLeast((90 * dp).toInt())
                    wm.updateViewLayout(root, params); true
                }
                else -> false
            }
        }
        val close = Button(this).apply { text = "X — wylacz" }
        close.setOnClickListener { stopSelf() }

        root.addView(ramTv); root.addView(swpTv)
        root.addView(opLabel); root.addView(seek)
        root.addView(resize); root.addView(close)

        // przesuwanie palcem za tlo panelu
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(root, params); true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("x", params.x).putInt("y", params.y).apply(); true
                }
                else -> false
            }
        }
    }

    private fun updateTexts() {
        val m = HashMap<String, Long>()
        runCatching {
            File("/proc/meminfo").readLines().forEach { l ->
                val p = l.split(":")
                if (p.size == 2) m[p[0].trim()] = p[1].trim().removeSuffix("kB").trim().toLongOrNull() ?: 0L
            }
        }
        val mt = m["MemTotal"] ?: 0; val ma = m["MemAvailable"] ?: 0
        val st = m["SwapTotal"] ?: 0; val sf = m["SwapFree"] ?: 0
        val mu = mt - ma; val su = st - sf
        ramTv.text = String.format("RAM  %.1f / %.1f GB  (%d%%)", mu / 1048576.0, mt / 1048576.0, if (mt > 0) mu * 100 / mt else 0)
        swpTv.text = String.format("ZRAM %.1f / %.1f GB  (%d%%)", su / 1048576.0, st / 1048576.0, if (st > 0) su * 100 / st else 0)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("ov", "Nakladka", NotificationManager.IMPORTANCE_LOW))
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "ov")
        else Notification.Builder(this))
            .setContentTitle("MemOverlay aktywna")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { wm.removeView(root) }
        super.onDestroy()
    }
}