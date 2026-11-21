package com.nibras.terminal

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.nibras.terminal.core.DeviceAbi
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.ExtraKeysView
import com.nibras.terminal.ui.Themes
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.util.regex.Pattern
import kotlin.concurrent.thread

/**
 * The whole app is a full-screen terminal, like Termux. Sessions, environments and
 * settings live in a left drawer (swipe from the left edge, or the ☰ extra key).
 */
class MainActivity : Activity(), ServiceConnection, TerminalService.Ui {
    companion object {
        const val EXTRA_ENV = "env"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BACKGROUND = "background"
        const val ACTION_NEW_SESSION = "com.nibras.terminal.NEW_SESSION"
        private const val REQ_PERMISSION = 7
    }

    private lateinit var prefs: Prefs
    private lateinit var drawer: DrawerLayout
    private lateinit var terminal: TerminalView
    private lateinit var keys: ExtraKeysView
    private lateinit var content: LinearLayout
    private lateinit var bootOverlay: TextView
    private lateinit var input: EditText
    private lateinit var sessionList: ListView
    private lateinit var drawerPanel: LinearLayout
    private var service: TerminalService? = null
    private var fontSize = 0
    private var volumeDown = false
    private var volumeUp = false
    private var pendingIntent: Intent? = null
    private var background = Color.BLACK

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildLayout()
        pendingIntent = intent
        val start = Intent(this, TerminalService::class.java)
        startForegroundService(start)
        bindService(start, this, Context.BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.sp.getBoolean("askedNotifications", false)
        ) {
            prefs.sp.edit().putBoolean("askedNotifications", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_PERMISSION)
        }
    }

    // ---------------------------------------------------------------- layout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildLayout() {
        terminal = TerminalView(this, null).apply {
            setTerminalViewClient(viewClient)
            isFocusable = true
            isFocusableInTouchMode = true
            keepScreenOn = prefs.keepScreenOn
        }
        keys = ExtraKeysView(this, ::onExtraKey) { }
        input = EditText(this).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "↵"
            textSize = 14f
            isSingleLine = true
            setOnEditorActionListener { v, _, _ ->
                val text = v.text.toString()
                currentSession()?.write(text + "\r")
                v.text = null
                true
            }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(terminal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Swipe left/right on the extra keys toggles a normal text field (IME suggestions, voice typing).
        val swipe = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (kotlin.math.abs(vx) > kotlin.math.abs(vy) * 2) { toggleInputField(); return true }
                return false
            }
        })
        keys.setOnTouchListener { _, e -> swipe.onTouchEvent(e); false }

        bootOverlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xEE000000.toInt())
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp(24), dp(24), dp(24), dp(24))
            visibility = View.GONE
        }
        val main = FrameLayout(this).apply {
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(bootOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        drawerPanel = buildDrawer()
        drawer = DrawerLayout(this).apply {
            addView(main, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(drawerPanel, DrawerLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
            setScrimColor(0x99000000.toInt())
        }
        setContentView(drawer)
        ViewCompat.setOnApplyWindowInsetsListener(drawer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val full = prefs.fullscreen
            main.setPadding(bars.left, if (full) 0 else bars.top, bars.right, maxOf(if (full) 0 else bars.bottom, ime.bottom))
            drawerPanel.setPadding(dp(12), bars.top + dp(12), dp(12), bars.bottom + dp(12))
            insets
        }
        applySettings()
    }

    private fun buildDrawer(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101418.toInt())
            isClickable = true
        }
        panel.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(0xFFD6B36A.toInt())
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "${versionName()} · ${DeviceAbi.label}"
            setTextColor(Color.GRAY)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })
        panel.addView(TextView(this).apply {
            text = getString(R.string.drawer_sessions)
            setTextColor(Color.LTGRAY)
            textSize = 13f
        })
        sessionList = ListView(this).apply {
            adapter = sessionAdapter
            divider = null
            setOnItemClickListener { _, _, pos, _ ->
                service?.sessions?.getOrNull(pos)?.let { showSession(it) }
                drawer.closeDrawers()
            }
            setOnItemLongClickListener { _, _, pos, _ ->
                service?.sessions?.getOrNull(pos)?.let { sessionMenu(it) }
                true
            }
        }
        panel.addView(sessionList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        fun button(label: String, long: (() -> Unit)? = null, click: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1E252D.toInt())
            setOnClickListener { click() }
            if (long != null) setOnLongClickListener { long(); true }
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        row1.addView(button("+ " + getString(R.string.new_session), long = { chooseEnvironment() }) {
            newSession(null); drawer.closeDrawers()
        }, lp)
        row1.addView(button(getString(R.string.keyboard)) { toggleKeyboard(); drawer.closeDrawers() }, LinearLayout.LayoutParams(lp))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button(getString(R.string.packages)) { startActivity(Intent(this, PackagesActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        row2.addView(button(getString(R.string.settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(button(getString(R.string.more)) { drawer.closeDrawers(); moreMenu() }, LinearLayout.LayoutParams(lp))
        row3.addView(button(getString(R.string.exit_app)) {
            startService(Intent(this, TerminalService::class.java).setAction(TerminalService.ACTION_STOP))
        }, LinearLayout.LayoutParams(lp))
        panel.addView(row1)
        panel.addView(row2)
        panel.addView(row3)
        return panel
    }

    private val sessionAdapter = object : BaseAdapter() {
        override fun getCount() = service?.sessions?.size ?: 0
        override fun getItem(position: Int) = service?.sessions?.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                setPadding(dp(8), dp(10), dp(8), dp(10))
                textSize = 15f
                typeface = Typeface.MONOSPACE
            }
            val s = getItem(position)
            val current = s != null && s == terminal.currentSession
            val name = s?.mSessionName ?: "?"
            val title = s?.title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            val env = s?.let { service?.envOf?.get(it) }?.let { if (it == Rootfs.ANDROID) "android" else Rootfs.name(it).lowercase() } ?: ""
            view.text = "[${position + 1}] $name$title" + (if (s?.isRunning == false) "  ✕" else "") + "\n     $env"
            view.setTextColor(if (current) 0xFFD6B36A.toInt() else Color.WHITE)
            view.setBackgroundColor(if (current) 0x22FFFFFF else Color.TRANSPARENT)
            return view
        }
    }

    // ---------------------------------------------------------------- service

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val svc = (binder as TerminalService.LocalBinder).service
        service = svc
        svc.ui = this
        if (svc.sessions.isEmpty()) {
            bootstrapThenStart()
        } else {
            showSession(svc.sessions.last())
            handleIntent(pendingIntent)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    private fun bootstrapThenStart() {
        bootOverlay.visibility = View.VISIBLE
        bootOverlay.text = getString(R.string.boot_extracting, "Alpine ${DeviceAbi.label}")
        thread(name = "bootstrap") {
            var problem: String? = null
            try {
                if (Rootfs.prootAvailable(this)) {
                    Rootfs.ensureBundledAlpine(this) { msg -> runOnUiThread { bootOverlay.text = msg } }
                    // Update Nibras scripts inside every environment after an app update.
                    val code = versionCode()
                    if (prefs.sp.getLong("preparedFor", -1) != code) {
                        Rootfs.ready(this).forEach { Rootfs.prepare(this, it.id) }
                        prefs.sp.edit().putLong("preparedFor", code).apply()
                    }
                } else {
                    problem = "PRoot (${DeviceAbi.current}) missing"
                }
            } catch (t: Throwable) {
                problem = t.message ?: t.javaClass.simpleName
            }
            runOnUiThread {
                bootOverlay.visibility = View.GONE
                if (problem != null) Toast.makeText(this, getString(R.string.boot_failed, problem), Toast.LENGTH_LONG).show()
                val svc = service ?: return@runOnUiThread
                if (!handleIntent(pendingIntent) && svc.sessions.isEmpty()) newSession(null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (service != null && bootOverlay.visibility != View.VISIBLE) handleIntent(intent) else pendingIntent = intent
    }

    /** Widget / shortcut / share: start a session for the given command. Returns true if one was started. */
    private fun handleIntent(intent: Intent?): Boolean {
        pendingIntent = null
        intent ?: return false
        val command = intent.getStringArrayExtra(EXTRA_COMMAND)
        if (command != null && intent.getBooleanExtra(EXTRA_BACKGROUND, false)) {
            service?.createSession(intent.getStringExtra(EXTRA_ENV), command.toList(), intent.getStringExtra(EXTRA_TITLE), inBackground = true)
            intent.removeExtra(EXTRA_COMMAND)
            return false
        }
        if (command != null) {
            newSession(intent.getStringExtra(EXTRA_ENV), command.toList(), intent.getStringExtra(EXTRA_TITLE))
            intent.removeExtra(EXTRA_COMMAND)
            return true
        }
        if (intent.action == ACTION_NEW_SESSION) {
            intent.action = null
            newSession(intent.getStringExtra(EXTRA_ENV))
            return true
        }
        return false
    }

    fun newSession(env: String?, command: List<String>? = null, title: String? = null) {
        val svc = service ?: return
        if (env != null && env != Rootfs.ANDROID && !Rootfs.isReady(this, env)) {
            Toast.makeText(this, getString(R.string.env_not_installed, Rootfs.name(env)), Toast.LENGTH_LONG).show()
        }
        showSession(svc.createSession(env, command, title))
    }

    override fun showSession(session: TerminalSession) {
        terminal.attachSession(session)
        applyCursor()
        terminal.onScreenUpdated()
        terminal.requestFocus()
        sessionAdapter.notifyDataSetChanged()
    }

    private fun currentSession(): TerminalSession? = terminal.currentSession

    override fun onSessionsChanged() {
        runOnUiThread { sessionAdapter.notifyDataSetChanged() }
    }

    override fun finishUi() {
        runOnUiThread { finishAndRemoveTask() }
    }

    private fun chooseEnvironment() {
        val ids = Rootfs.ready(this).map { it.id } + Rootfs.ANDROID
        val labels = ids.map { if (it == Rootfs.ANDROID) getString(R.string.android_shell) else Rootfs.name(it) }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_session_in)
            .setItems(labels.toTypedArray()) { _, which -> newSession(ids[which]); drawer.closeDrawers() }
            .show()
    }

    private fun sessionMenu(session: TerminalSession) {
        val items = arrayOf(getString(R.string.session_rename), getString(R.string.session_kill))
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> renameSession(session)
                1 -> closeSession(session)
            }
        }.show()
    }

    private fun renameSession(session: TerminalSession) {
        val field = EditText(this).apply { setText(session.mSessionName); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle(R.string.session_rename)
            .setView(field)
            .setPositiveButton(R.string.ok) { _, _ ->
                session.mSessionName = field.text.toString().ifBlank { session.mSessionName }
                sessionAdapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun closeSession(session: TerminalSession) {
        val svc = service ?: return
        val index = svc.sessions.indexOf(session)
        svc.removeSession(session)
        if (svc.sessions.isEmpty()) {
            finishAndRemoveTask()
            startService(Intent(this, TerminalService::class.java).setAction(TerminalService.ACTION_STOP))
            return
        }
        if (terminal.currentSession == session || terminal.currentSession == null) {
            showSession(svc.sessions[(index - 1).coerceIn(0, svc.sessions.lastIndex)])
        }
    }

    private fun switchSession(forward: Boolean) {
        val list = service?.sessions ?: return
        if (list.isEmpty()) return
        val i = list.indexOf(currentSession())
        val next = if (forward) (i + 1) % list.size else (i - 1 + list.size) % list.size
        showSession(list[next])
    }

    // ---------------------------------------------------------------- menus

    private fun moreMenu() {
        val s = currentSession() ?: return
        val items = arrayOf(
            getString(R.string.menu_copy_all), getString(R.string.menu_paste), getString(R.string.menu_select_url),
            getString(R.string.menu_share), getString(R.string.menu_reset), getString(R.string.menu_kill),
            getString(R.string.menu_keep_screen) + if (prefs.keepScreenOn) " ✓" else "",
            getString(R.string.menu_wakelock) + if (service?.wakeLocked == true) " ✓" else "",
            getString(R.string.menu_toggle_keys) + if (prefs.extraKeys) " ✓" else "",
            getString(R.string.menu_font_bigger), getString(R.string.menu_font_smaller), getString(R.string.menu_help),
        )
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> s.emulator?.screen?.transcriptTextWithoutJoinedLines?.let { copy(it) }
                1 -> paste(s)
                2 -> selectUrl(s)
                3 -> s.emulator?.screen?.transcriptTextWithoutJoinedLines?.let { share(it) }
                4 -> { s.reset(); terminal.onScreenUpdated() }
                5 -> android.os.Process.sendSignal(s.pid, 9)
                6 -> { prefs.keepScreenOn = !prefs.keepScreenOn; terminal.keepScreenOn = prefs.keepScreenOn }
                7 -> { val on = service?.setWakeLock(service?.wakeLocked != true) ?: false
                    Toast.makeText(this, if (on) R.string.wakelock_on else R.string.wakelock_off, Toast.LENGTH_SHORT).show() }
                8 -> { prefs.extraKeys = !prefs.extraKeys; applySettings() }
                9 -> changeFontSize(true)
                10 -> changeFontSize(false)
                11 -> s.write("nibras-help\r")
            }
        }.show()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("terminal", text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun paste(session: TerminalSession?) {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        session?.emulator?.paste(text)
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.takeLast(100_000))
        startActivity(Intent.createChooser(send, getString(R.string.menu_share)))
    }

    private fun selectUrl(session: TerminalSession) {
        val text = session.emulator?.screen?.transcriptTextWithFullLinesJoined ?: return
        val m = Pattern.compile("(https?|ftp|file)://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+").matcher(text)
        val urls = LinkedHashSet<String>()
        while (m.find()) urls += m.group()
        if (urls.isEmpty()) { Toast.makeText(this, R.string.no_urls, Toast.LENGTH_SHORT).show(); return }
        val list = urls.toList().reversed()
        AlertDialog.Builder(this).setTitle(R.string.menu_select_url).setItems(list.toTypedArray()) { _, which ->
            val url = list[which]
            AlertDialog.Builder(this).setMessage(url)
                .setPositiveButton("Open") { _, _ -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                .setNeutralButton(R.string.menu_copy_all) { _, _ -> copy(url) }
                .show()
        }.show()
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        terminal.requestFocus()
        @Suppress("DEPRECATION")
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    private fun showKeyboard() {
        terminal.requestFocus()
        getSystemService(InputMethodManager::class.java).showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleInputField() {
        if (input.visibility == View.VISIBLE) {
            input.visibility = View.GONE
            terminal.requestFocus()
        } else {
            input.visibility = View.VISIBLE
            input.requestFocus()
            getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // ---------------------------------------------------------------- settings / style

    override fun onResume() {
        super.onResume()
        applySettings()
        if (prefs.cursorBlink) {
            terminal.setTerminalCursorBlinkerRate(500)
            terminal.setTerminalCursorBlinkerState(true, true)
        }
        sessionAdapter.notifyDataSetChanged()
    }

    override fun onStop() {
        terminal.setTerminalCursorBlinkerState(false, true)
        super.onStop()
    }

    override fun reloadSettings() {
        runOnUiThread { applySettings() }
    }

    private fun applySettings() {
        val home = runCatching { Rootfs.homeOf(this) }.getOrNull()
        background = Themes.apply(prefs.theme, home)
        service?.sessions?.forEach { it.emulator?.mColors?.reset() }
        content.setBackgroundColor(background)
        drawer.setBackgroundColor(background)
        window.statusBarColor = background
        window.navigationBarColor = background
        val light = com.termux.terminal.TerminalColors.getPerceivedBrightnessOfColor(background) > 130
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
            if (prefs.fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        keys.keyColor = if (light) Color.BLACK else Color.WHITE
        keys.setRows(Themes.extraKeys(home) ?: ExtraKeysView.DEFAULT.let { rows ->
            listOf(rows[0], rows[1])
        })
        keys.visibility = if (prefs.extraKeys) View.VISIBLE else View.GONE
        terminal.setTypeface(Themes.typeface(home))
        fontSize = if (prefs.fontSize > 0) prefs.fontSize else if (fontSize > 0) fontSize else defaultFontSize()
        terminal.setTextSize(fontSize)
        terminal.keepScreenOn = prefs.keepScreenOn
        applyCursor()
        terminal.invalidate()
        drawer.requestApplyInsets()
    }

    private fun applyCursor() {
        terminal.mEmulator?.setCursorStyle()
    }

    private fun defaultFontSize(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics).toInt().coerceIn(12, 48)

    private fun changeFontSize(bigger: Boolean) {
        val min = dp(6).coerceAtLeast(8)
        val max = dp(40)
        fontSize = (fontSize + if (bigger) 2 else -2).coerceIn(min, max)
        prefs.fontSize = fontSize
        terminal.setTextSize(fontSize)
    }

    // ---------------------------------------------------------------- extra keys

    private fun onExtraKey(key: String) {
        when (key) {
            "KBD" -> { toggleKeyboard(); return }
            "DRAWER" -> { drawer.openDrawer(Gravity.START); return }
            "PASTE" -> { paste(currentSession()); return }
        }
        val code = keys.keyCode(key)
        val ctrl = keys.readCtrl()
        val alt = keys.readAlt()
        val shift = keys.readShift()
        if (code != null) {
            var mod = 0
            if (ctrl) mod = mod or KeyHandler.KEYMOD_CTRL
            if (alt) mod = mod or KeyHandler.KEYMOD_ALT
            if (shift) mod = mod or KeyHandler.KEYMOD_SHIFT
            if (!terminal.handleKeyCode(code, mod)) {
                terminal.onKeyDown(code, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, metaState(ctrl, alt, shift)))
            }
        } else {
            key.codePoints().forEach { cp -> terminal.inputCodePoint(cp, ctrl, alt) }
        }
    }

    private fun metaState(ctrl: Boolean, alt: Boolean, shift: Boolean): Int {
        var m = 0
        if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return m
    }

    // ---------------------------------------------------------------- permissions

    override fun requestPermission(permission: String) {
        runOnUiThread { requestPermissions(arrayOf(permission), REQ_PERMISSION) }
    }

    override fun requestStorage() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= 30) {
                val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                runCatching { startActivity(app) }.onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_PERMISSION)
            }
        }
    }

    // ---------------------------------------------------------------- back / lifecycle

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawer.isDrawerOpen(Gravity.START) -> drawer.closeDrawers()
            input.visibility == View.VISIBLE -> toggleInputField()
            else -> moveTaskToBack(true) // sessions keep running, like Termux
        }
    }

    override fun onDestroy() {
        service?.ui = null
        runCatching { unbindService(this) }
        super.onDestroy()
    }

    private fun versionName() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
    private fun versionCode() = runCatching { packageManager.getPackageInfo(packageName, 0).longVersionCode }.getOrDefault(0L)

    // ---------------------------------------------------------------- session client

    override val sessionClient: TerminalSessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            if (terminal.currentSession == changedSession) terminal.onScreenUpdated()
        }
        override fun onTitleChanged(changedSession: TerminalSession) { sessionAdapter.notifyDataSetChanged() }
        override fun onSessionFinished(finishedSession: TerminalSession) { sessionAdapter.notifyDataSetChanged() }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) { if (text != null) copy(text) }
        override fun onPasteTextFromClipboard(session: TerminalSession?) { paste(session) }
        override fun onBell(session: TerminalSession) {
            if (prefs.bellVibrate) getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        override fun onColorsChanged(session: TerminalSession) {
            if (terminal.currentSession == session) terminal.invalidate()
        }
        override fun onTerminalCursorStateChange(state: Boolean) {
            if (state && prefs.cursorBlink) terminal.setTerminalCursorBlinkerState(true, false)
        }
        override fun getTerminalCursorStyle(): Int = prefs.cursorStyle
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    // ---------------------------------------------------------------- view client

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            if (scale < 0.9f || scale > 1.1f) {
                changeFontSize(scale > 1f)
                return 1.0f
            }
            return scale
        }

        override fun onSingleTapUp(e: MotionEvent?) {
            val emulator = terminal.mEmulator
            if (emulator != null && emulator.isMouseTrackingActive) return
            showKeyboard()
        }

        override fun shouldBackButtonBeMappedToEscape() = prefs.backIsEscape
        override fun shouldEnforceCharBasedInput() = false
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {
            drawer.setDrawerLockMode(if (copyMode) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)
        }

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
            if (session != null && !session.isRunning && keyCode == KeyEvent.KEYCODE_ENTER) {
                closeSession(session)
                return true
            }
            if (prefs.volumeKeys) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { volumeDown = true; return true }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { volumeUp = true; return true }
            }
            if (e.isCtrlPressed && e.isAltPressed) {
                val uc = e.getUnicodeChar(0)
                when {
                    keyCode == KeyEvent.KEYCODE_C -> newSession(null)
                    keyCode == KeyEvent.KEYCODE_N || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> switchSession(true)
                    keyCode == KeyEvent.KEYCODE_P || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> switchSession(false)
                    keyCode == KeyEvent.KEYCODE_V -> paste(session)
                    keyCode == KeyEvent.KEYCODE_K -> toggleKeyboard()
                    keyCode == KeyEvent.KEYCODE_M -> moreMenu()
                    keyCode == KeyEvent.KEYCODE_R -> session?.let { renameSession(it) }
                    keyCode == KeyEvent.KEYCODE_D -> drawer.openDrawer(Gravity.START)
                    uc == '+'.code || keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_PLUS -> changeFontSize(true)
                    uc == '-'.code || keyCode == KeyEvent.KEYCODE_MINUS -> changeFontSize(false)
                    keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
                        service?.sessions?.getOrNull(keyCode - KeyEvent.KEYCODE_1)?.let { showSession(it) }
                    else -> return false
                }
                return true
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volumeDown) { volumeDown = false; return true }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && volumeUp) { volumeUp = false; return true }
            return false
        }

        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = keys.readCtrl() || volumeDown
        override fun readAltKey() = keys.readAlt()
        override fun readShiftKey() = keys.readShift()
        override fun readFnKey() = keys.readFn()

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            if (!volumeUp || session == null) return false
            // Termux volume-up shortcuts
            val c = Character.toLowerCase(codePoint.toChar())
            val code = when (c) {
                'w' -> KeyEvent.KEYCODE_DPAD_UP
                'a' -> KeyEvent.KEYCODE_DPAD_LEFT
                's' -> KeyEvent.KEYCODE_DPAD_DOWN
                'd' -> KeyEvent.KEYCODE_DPAD_RIGHT
                'p' -> KeyEvent.KEYCODE_PAGE_UP
                'n' -> KeyEvent.KEYCODE_PAGE_DOWN
                't' -> KeyEvent.KEYCODE_TAB
                'i' -> KeyEvent.KEYCODE_INSERT
                'h' -> -2
                'u' -> -3
                'l' -> -4
                'e' -> KeyEvent.KEYCODE_ESCAPE
                'q', 'k' -> -1
                'v' -> -5
                'b', 'f', 'x' -> -6
                '.' -> -7
                in '1'..'9' -> KeyEvent.KEYCODE_F1 + (c - '1')
                '0' -> KeyEvent.KEYCODE_F10
                else -> return false
            }
            when (code) {
                -1 -> runOnUiThread { prefs.extraKeys = !prefs.extraKeys; applySettings() }
                -2 -> session.write("~")
                -3 -> session.write("_")
                -4 -> session.write("|")
                -5 -> runOnUiThread { paste(session) }
                -6 -> session.write("\u001b$c")
                -7 -> session.write("\u001c")
                else -> terminal.handleKeyCode(code, 0)
            }
            return true
        }

        override fun onEmulatorSet() {
            applyCursor()
            if (prefs.cursorBlink) {
                terminal.setTerminalCursorBlinkerRate(500)
                terminal.setTerminalCursorBlinkerState(true, true)
            }
        }

        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Suppress("unused")
    private fun unusedInputType() = InputType.TYPE_NULL
}
