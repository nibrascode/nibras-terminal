package com.nibras.terminal

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.Themes
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/** Floating terminal window over other apps (Termux:Float). Drag the title bar, ⤡ resizes, ✕ closes. */
class FloatService : Service(), ServiceConnection {
    companion object {
        fun start(context: Context) {
            if (Channel.store) {
                Toast.makeText(context, Channel.unavailable("Floating window").removePrefix("ERR\n"), Toast.LENGTH_LONG).show()
                return
            }
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, R.string.float_permission, Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            context.startService(Intent(context, FloatService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var terminal: TerminalView
    private var service: TerminalService? = null
    private var focused = false

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) {
            try { show() } catch (t: Throwable) { NibrasApp.record(this, "float", t); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        wm = getSystemService(WindowManager::class.java)
        val prefs = Prefs(this)
        params = WindowManager.LayoutParams(
            dp(320), dp(240), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(24); y = dp(120) }
        terminal = TerminalView(this, null).apply {
            setTerminalViewClient(client)
            setTextSize(dp(10).coerceAtLeast(18))
            runCatching { setTypeface(Themes.typeface(runCatching { Rootfs.homeOf(this@FloatService) }.getOrNull())) }
            isFocusable = true; isFocusableInTouchMode = true
        }
        val bg = runCatching { Themes.apply(prefs.theme, null) }.getOrDefault(Color.BLACK)
        terminal.setBackgroundColor(bg)
        fun icon(t: String, click: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = 16f; setPadding(dp(10), dp(4), dp(10), dp(4)); setOnClickListener { click() }
        }
        val title = TextView(this).apply {
            text = "Nibras"; setTextColor(0xFFD6B36A.toInt()); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val resize = icon("⤡") {}
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xEE1E252D.toInt())
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(icon("+") { service?.let { s -> attach(s.createSession(null)) } })
            addView(icon("⇄") { nextSession() })
            addView(icon("⤢") {
                startActivity(Intent(this@FloatService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); stopSelf()
            })
            addView(resize)
            addView(icon("✕") { stopSelf() })
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            addView(bar)
            addView(terminal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Drag by the title.
        var sx = 0f; var sy = 0f; var px = 0; var py = 0
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = params.x; py = params.y }
                MotionEvent.ACTION_MOVE -> { params.x = px + (e.rawX - sx).toInt(); params.y = py + (e.rawY - sy).toInt(); wm.updateViewLayout(view, params) }
            }
            true
        }
        var pw = 0; var ph = 0
        resize.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; pw = params.width; ph = params.height }
                MotionEvent.ACTION_MOVE -> {
                    params.width = (pw + (e.rawX - sx).toInt()).coerceAtLeast(dp(180))
                    params.height = (ph + (e.rawY - sy).toInt()).coerceAtLeast(dp(120))
                    wm.updateViewLayout(view, params)
                }
            }
            true
        }
        view.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) setFocus(false); false }
        terminal.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) setFocus(true); false }
        wm.addView(view, params)
        root = view
        bindService(Intent(this, TerminalService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun setFocus(on: Boolean) {
        if (focused == on) return
        focused = on
        params.flags = if (on) WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        root?.let { wm.updateViewLayout(it, params) }
        if (on) {
            terminal.requestFocus()
            terminal.postDelayed({ getSystemService(InputMethodManager::class.java).showSoftInput(terminal, 0) }, 100)
        }
    }

    private fun attach(session: TerminalSession) {
        terminal.attachSession(session)
        terminal.onScreenUpdated()
    }

    private fun nextSession() {
        val list = service?.sessions ?: return
        if (list.isEmpty()) return
        val i = list.indexOf(terminal.currentSession)
        attach(list[(i + 1) % list.size])
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val svc = (binder as TerminalService.LocalBinder).service
        service = svc
        svc.floatListener = { s -> if (terminal.currentSession == s) terminal.post { terminal.onScreenUpdated() } }
        attach(svc.sessions.lastOrNull() ?: svc.createSession(null))
    }

    override fun onServiceDisconnected(name: ComponentName?) { service = null }

    override fun onDestroy() {
        service?.floatListener = null
        runCatching { unbindService(this) }
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private val client = object : TerminalViewClient {
        override fun onScale(scale: Float) = 1f
        override fun onSingleTapUp(e: MotionEvent?) { setFocus(true) }
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = false
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) = Unit
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_BACK) { setFocus(false); return true }
            return false
        }
        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = false
        override fun readAltKey() = false
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?) = false
        override fun onEmulatorSet() = Unit
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }
}
