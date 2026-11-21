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
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
import com.nibras.terminal.core.Search
import com.nibras.terminal.core.Snippet
import com.nibras.terminal.core.Snippets
import com.nibras.terminal.core.Suggest
import com.nibras.terminal.core.VoiceCommands
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
    private var lastProjectCwd = ""
    private var background = Color.BLACK
    private lateinit var primary: TerminalView
    private var secondary: TerminalView? = null
    private lateinit var panes: LinearLayout
    private lateinit var suggestBar: LinearLayout
    private lateinit var suggestScroll: HorizontalScrollView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val suggestTask = Runnable { updateSuggestions() }
    private lateinit var monitorView: TextView
    private lateinit var lockOverlay: TextView
    private var stoppedAt = 0L
    private var unlocked = false
    private val monitorTask = object : Runnable {
        override fun run() { updateMonitor(); if (prefs.sp.getBoolean("monitor", false)) handler.postDelayed(this, 2000) }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private var startupKeyboardChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildLayout()
        firstRunLanguage(intent)
        pendingIntent = intent
        showPreviousCrash()
        startTerminalService()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.sp.getBoolean("askedNotifications", false)
        ) {
            prefs.sp.edit().putBoolean("askedNotifications", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_PERMISSION)
        }
    }

    /** Starts and binds the session service. A failure here must never close the app. */
    private fun startTerminalService() {
        val start = Intent(this, TerminalService::class.java)
        try {
            startForegroundService(start)
        } catch (t: Throwable) {
            NibrasApp.record(this, "startForegroundService", t)
            runCatching { startService(start) }
        }
        try {
            bindService(start, this, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            NibrasApp.record(this, "bindService", t)
            Toast.makeText(this, getString(R.string.boot_failed, t.message ?: t.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }

    /** If the last run crashed, show the report once so it can be copied or shared. */
    private fun showPreviousCrash() {
        val file = NibrasApp.crashFile(this)
        if (!file.isFile) return
        val text = runCatching { file.readText() }.getOrDefault("")
        file.delete()
        if (text.isBlank()) return
        val view = TextView(this).apply {
            this.text = text
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setView(android.widget.ScrollView(this).apply { addView(view) })
            .setPositiveButton(R.string.menu_copy_all) { _, _ -> copy(text) }
            .setNeutralButton(R.string.menu_share) { _, _ -> share(text) }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    // ---------------------------------------------------------------- layout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildLayout() {
        primary = newPane()
        terminal = primary
        panes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(primary, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        suggestBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        suggestScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(suggestBar)
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
            addView(panes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(suggestScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        monitorView = TextView(this).apply {
            setTextColor(0xFFD6B36A.toInt()); textSize = 10f; typeface = Typeface.MONOSPACE
            setBackgroundColor(0x99000000.toInt()); setPadding(dp(6), dp(2), dp(6), dp(2)); visibility = View.GONE
        }
        main.addView(monitorView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        lockOverlay = TextView(this).apply {
            text = "🔒\n\nNibras Terminal"; gravity = Gravity.CENTER; textSize = 22f; setTextColor(0xFFD6B36A.toInt())
            setBackgroundColor(Color.BLACK); visibility = View.GONE; isClickable = true; setOnClickListener { askUnlock() }
        }
        main.addView(lockOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = getString(R.string.app_name)
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                setTextColor(0xFFD6B36A.toInt())
                textSize = 20f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            })
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
        val tools1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools1.addView(button(getString(R.string.tool_hosts)) { drawer.closeDrawers(); startActivity(Intent(this, HostsActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        tools1.addView(button(getString(R.string.tool_files)) { drawer.closeDrawers(); FilesActivity.open(this, currentEnv(), null) }, LinearLayout.LayoutParams(lp))
        tools1.addView(button(getString(R.string.tool_snippets)) { drawer.closeDrawers(); snippets() }, LinearLayout.LayoutParams(lp))
        val tools2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools2.addView(button(getString(R.string.tool_search)) { drawer.closeDrawers(); searchScrollback() }, LinearLayout.LayoutParams(lp))
        tools2.addView(button(getString(R.string.tool_split)) { drawer.closeDrawers(); toggleSplit() }, LinearLayout.LayoutParams(lp))
        tools2.addView(button(getString(R.string.tool_desktop), long = { DesktopActivity.open(this, currentEnv()) }) {
            drawer.closeDrawers(); newSession(currentEnv(), Launcher.command(this, currentEnv(), "nibras-desktop start", keepShell = true), "desktop")
        }, LinearLayout.LayoutParams(lp))
        val tools3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools3.addView(button(getString(R.string.tool_store)) { drawer.closeDrawers(); startActivity(Intent(this, StoreActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        tools3.addView(button(getString(R.string.tool_float)) { drawer.closeDrawers(); FloatService.start(this) }, LinearLayout.LayoutParams(lp))
        tools3.addView(button("🎤 " + getString(R.string.tool_voice)) { drawer.closeDrawers(); voiceInput() }, LinearLayout.LayoutParams(lp))
        val tools4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools4.addView(button("🎬 " + getString(R.string.tools_media)) { drawer.closeDrawers(); startActivity(Intent(this, MediaActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        tools4.addView(button("🔌 USB") { drawer.closeDrawers(); Launcher.run(this, null, "nibras-usb -l; echo; nibras-usb serial", "USB", keepShell = true) }, LinearLayout.LayoutParams(lp))
        tools4.addView(button("📶 NFC") { drawer.closeDrawers(); Launcher.run(this, null, "nibras-nfc -r short", "NFC", keepShell = true) }, LinearLayout.LayoutParams(lp))
        panel.addView(tools1)
        panel.addView(tools2)
        panel.addView(tools3)
        panel.addView(tools4)
        val tools5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools5.addView(button("📜 " + getString(R.string.scripts_title)) { drawer.closeDrawers(); startActivity(Intent(this, ScriptsActivity::class.java)) }, LinearLayout.LayoutParams(lp))
        tools5.addView(button("🎓 " + getString(R.string.tool_learn)) { drawer.closeDrawers(); Launcher.run(this, null, "nibras-learn", "learn", keepShell = true) }, LinearLayout.LayoutParams(lp))
        tools5.addView(button("💻 " + getString(R.string.tool_remote)) { drawer.closeDrawers(); Launcher.run(this, null, "nibras-remote on", "remote", keepShell = true) }, LinearLayout.LayoutParams(lp))
        panel.addView(tools5)
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
            WhatsNew.maybe(this)
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
                WhatsNew.maybe(this)
                val svc = service ?: return@runOnUiThread
                if (!handleIntent(pendingIntent) && svc.sessions.isEmpty()) restoreOrNew(svc)
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
        // Open a tool screen (also used by the launch test): store, hosts, files, editor, split, search, snippets.
        intent.getStringExtra("open")?.let { what ->
            intent.removeExtra("open")
            when (what) {
                "store" -> startActivity(Intent(this, StoreActivity::class.java))
                "scripts" -> startActivity(Intent(this, ScriptsActivity::class.java))
                "hosts" -> startActivity(Intent(this, HostsActivity::class.java))
                "files" -> FilesActivity.open(this, currentEnv(), null)
                "editor" -> EditorActivity.open(this, Rootfs.ANDROID, java.io.File(filesDir, "snippets.txt").absolutePath)
                "split" -> toggleSplit()
                "media" -> startActivity(Intent(this, MediaActivity::class.java))
                "packages" -> startActivity(Intent(this, PackagesActivity::class.java))
                "clipboard" -> clipboardHistory()
                "monitor" -> { prefs.sp.edit().putBoolean("monitor", true).apply(); startMonitor() }
            }
        }
        val command = intent.getStringArrayExtra(EXTRA_COMMAND)
        if (command != null) {
            val line = if (command.size >= 3 && command[0] == "sh" && command[1] == "-c") command[2] else command.joinToString(" ")
            if (!AllowFile.automatic(this, line)) {
                Toast.makeText(this, "not allowed", Toast.LENGTH_LONG).show()
                intent.removeExtra(EXTRA_COMMAND)
                return false
            }
        }
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
        val other = listOfNotNull(primary, secondary).firstOrNull { it !== terminal && it.currentSession == session }
        if (other != null) { focusPane(other); return }
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
        val su = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su").firstOrNull { java.io.File(it).exists() }
        val labels = ids.map { if (it == Rootfs.ANDROID) getString(R.string.android_shell) else Rootfs.name(it) } +
            listOfNotNull(su?.let { getString(R.string.root_shell) })
        AlertDialog.Builder(this)
            .setTitle(R.string.new_session_in)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < ids.size) newSession(ids[which])
                else newSession(Rootfs.ANDROID, listOf("/system/bin/sh", "-c", "exec $su"), "root")
                drawer.closeDrawers()
            }
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
            getString(R.string.menu_suggestions) + if (prefs.sp.getBoolean("suggestions", true)) " ✓" else "",
            getString(R.string.menu_restore) + if (prefs.sp.getBoolean("restoreSessions", true)) " ✓" else "",
            getString(R.string.menu_external_apps) + if (Automation.allowed(this)) " ✓" else "",
            getString(R.string.menu_clipboard_history),
            getString(R.string.menu_monitor) + if (prefs.sp.getBoolean("monitor", false)) " ✓" else "",
            getString(R.string.menu_battery_saver) + if (prefs.sp.getBoolean("batterySaver", false)) " ✓" else "",
            getString(R.string.menu_app_lock) + if (prefs.sp.getBoolean("appLock", false)) " ✓" else "",
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
                12 -> { val on = !prefs.sp.getBoolean("suggestions", true); prefs.sp.edit().putBoolean("suggestions", on).apply(); if (!on) suggestScroll.visibility = View.GONE }
                13 -> prefs.sp.edit().putBoolean("restoreSessions", !prefs.sp.getBoolean("restoreSessions", true)).apply()
                14 -> if (Automation.allowed(this)) Automation.set(this, false) else AlertDialog.Builder(this)
                    .setMessage(R.string.external_apps_warning)
                    .setPositiveButton(R.string.ok) { _, _ -> Automation.set(this, true) }
                    .setNegativeButton(R.string.cancel, null).show()
                15 -> clipboardHistory()
                16 -> { prefs.sp.edit().putBoolean("monitor", !prefs.sp.getBoolean("monitor", false)).apply(); startMonitor() }
                17 -> toggleBatterySaver()
                18 -> if (prefs.sp.getBoolean("appLock", false)) prefs.sp.edit().putBoolean("appLock", false).apply()
                    else authenticate { prefs.sp.edit().putBoolean("appLock", true).apply(); Toast.makeText(this, R.string.app_lock_on, Toast.LENGTH_SHORT).show() }
            }
        }.show()
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("terminal", text))
        rememberClip(text)
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

    /** terminal-onclick-url-open: opens the URL under the finger. */
    private fun openUrlAt(e: MotionEvent): Boolean = runCatching {
        val emulator = terminal.mEmulator ?: return false
        val cr = terminal.getColumnAndRow(e, false)
        val line = emulator.screen.getSelectedText(0, cr[1], emulator.mColumns, cr[1])
        val m = Regex("""(https?|ftp|file)://[^\s'"<>()]+""").findAll(line).firstOrNull { cr[0] >= it.range.first && cr[0] <= it.range.last } ?: return false
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(m.value)))
        true
    }.getOrDefault(false)

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
        startMonitor()
        if (prefs.sp.getBoolean("appLock", false) && (!unlocked || System.currentTimeMillis() - stoppedAt > 30_000)) {
            unlocked = false; lockOverlay.visibility = View.VISIBLE; askUnlock()
        }
        if (prefs.cursorBlink) {
            terminal.setTerminalCursorBlinkerRate(com.nibras.terminal.ui.TermProps.blinkRate.coerceAtLeast(100))
            terminal.setTerminalCursorBlinkerState(true, true)
        }
        sessionAdapter.notifyDataSetChanged()
    }

    override fun onStop() {
        stoppedAt = System.currentTimeMillis()
        handler.removeCallbacks(monitorTask)
        terminal.setTerminalCursorBlinkerState(false, true)
        super.onStop()
    }

    override fun reloadSettings() {
        runOnUiThread { applySettings() }
    }

    override fun applyLanguage() { runOnUiThread { recreate() } }

    /** First launch: pick the language (device language preselected, English otherwise). */
    private fun firstRunLanguage(intent: Intent?) {
        if (Lang.chosen(this)) return
        intent?.getStringExtra("nibras.lang")?.let { code ->
            Lang.set(this, if (code in Lang.names) code else ""); if (code != "en" && code in Lang.names) recreate(); return
        }
        val codes = Lang.names.keys.toList()
        val dev = Lang.device(this)
        var pick = codes.indexOf(dev).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("🌐 Language · Dil · اللغة · Язык")
            .setSingleChoiceItems(codes.map { Lang.names[it] }.toTypedArray(), pick) { _, i -> pick = i }
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val code = codes[pick]
                Lang.set(this, if (code == dev) "" else code)
                if (code != dev) recreate()
            }
            .show()
    }

    private fun applySettings() {
        try {
            applySettingsUnsafe()
        } catch (t: Throwable) {
            NibrasApp.record(this, "applySettings", t)
        }
    }

    private fun applySettingsUnsafe() {
        val home = runCatching { Rootfs.homeOf(this) }.getOrNull()
        runCatching { com.nibras.terminal.ui.TermProps.load(this, home) }
        if (!startupKeyboardChecked) {
            startupKeyboardChecked = true
            if (com.nibras.terminal.ui.TermProps.hideKeyboardOnStart)
                window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        background = Themes.apply(prefs.theme, home)
        service?.sessions?.forEach { it.emulator?.mColors?.reset() }
        val wall = Themes.backgroundImage(home)?.let { f ->
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, o)
                var sample = 1; val max = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
                android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
        if (wall != null) {
            val img = android.graphics.drawable.BitmapDrawable(resources, wall).apply { gravity = android.view.Gravity.FILL }
            val shade = android.graphics.drawable.ColorDrawable((com.nibras.terminal.ui.TermProps.backgroundOpacity * 255 / 100 shl 24) or (background and 0xFFFFFF))
            content.background = android.graphics.drawable.LayerDrawable(arrayOf(img, shade))
        } else content.setBackgroundColor(background)
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
        val sessionNow = currentSession()
        val projectKeys = sessionNow?.let { s ->
            val env = service?.envOf?.get(s) ?: return@let null
            ProjectKeys.read(this, env, service?.cwdOf(s).orEmpty())
        }
        keys.setRows(projectKeys ?: com.nibras.terminal.ui.TermProps.extraKeys ?: Themes.extraKeys(home) ?: ExtraKeysView.DEFAULT)
        keys.visibility = if (prefs.extraKeys) View.VISIBLE else View.GONE
        // setTextSize must run before setTypeface: TerminalView has no renderer until a text size is set,
        // and setTypeface() dereferences it (this NPE closed 0.2.113 right after launch).
        fontSize = if (prefs.fontSize > 0) prefs.fontSize else if (fontSize > 0) fontSize else defaultFontSize()
        for (pane in listOfNotNull(primary, secondary)) {
            pane.setTextSize(fontSize)
            runCatching { pane.setTypeface(Themes.typeface(home)) }
            pane.keepScreenOn = prefs.keepScreenOn
            pane.setPadding(dp(com.nibras.terminal.ui.TermProps.marginH), dp(com.nibras.terminal.ui.TermProps.marginV), dp(com.nibras.terminal.ui.TermProps.marginH), dp(com.nibras.terminal.ui.TermProps.marginV))
            pane.invalidate()
        }
        applyCursor()
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
        listOfNotNull(primary, secondary).forEach { it.setTextSize(fontSize) }
    }

    // ---------------------------------------------------------------- extra keys

    private fun onExtraKey(key: String) {
        if (key.startsWith("MACRO:")) {
            var ctrl = false; var alt = false; var shift = false
            for (t in key.removePrefix("MACRO:").substringBeforeLast('|').split(' ').filter { it.isNotEmpty() }) {
                when (t.uppercase()) {
                    "CTRL" -> ctrl = true; "ALT" -> alt = true; "SHIFT" -> shift = true
                    else -> {
                        val code = keys.keyCode(t.uppercase())
                        if (code != null) {
                            var mod = 0
                            if (ctrl) mod = mod or KeyHandler.KEYMOD_CTRL; if (alt) mod = mod or KeyHandler.KEYMOD_ALT; if (shift) mod = mod or KeyHandler.KEYMOD_SHIFT
                            if (!terminal.handleKeyCode(code, mod)) terminal.onKeyDown(code, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, metaState(ctrl, alt, shift)))
                        } else t.codePoints().forEach { cp -> terminal.inputCodePoint(cp, ctrl, alt) }
                        ctrl = false; alt = false; shift = false
                    }
                }
            }
            return
        }
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

    // ---------------------------------------------------------------- panes, split, search, snippets, suggestions

    @SuppressLint("ClickableViewAccessibility")
    private fun newPane(): TerminalView = TerminalView(this, null).apply {
        setTerminalViewClient(viewClient)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = prefs.keepScreenOn
        setTextSize(if (fontSize > 0) fontSize else defaultFontSize())
        val self = this
        setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) focusPane(self); false }
    }

    private fun focusPane(pane: TerminalView) {
        if (terminal === pane) return
        terminal = pane
        listOfNotNull(primary, secondary).forEach { it.alpha = if (it === pane || secondary == null) 1f else 0.7f }
        pane.requestFocus()
        sessionAdapter.notifyDataSetChanged()
    }

    /** Two terminals side by side (landscape) or stacked (portrait); each pane shows its own session. */
    private fun toggleSplit() {
        val svc = service ?: return
        val second = secondary
        if (second != null) {
            panes.removeView(second)
            secondary = null
            terminal = primary
            primary.alpha = 1f
            primary.requestFocus()
            return
        }
        val pane = newPane()
        runCatching { pane.setTypeface(Themes.typeface(runCatching { Rootfs.homeOf(this) }.getOrNull())) }
        secondary = pane
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        panes.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val lp = if (landscape) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        primary.layoutParams = lp
        panes.addView(View(this).apply { setBackgroundColor(0xFFD6B36A.toInt()); tag = "divider" },
            if (landscape) LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        panes.addView(pane, LinearLayout.LayoutParams(lp))
        val other = svc.sessions.firstOrNull { it != primary.currentSession }
        focusPane(pane)
        if (other != null) showSession(other) else newSession(currentEnv())
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (secondary == null) return
        val landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        panes.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (i in 0 until panes.childCount) {
            val v = panes.getChildAt(i)
            v.layoutParams = if (v.tag == "divider") {
                if (landscape) LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            } else if (landscape) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
    }

    private fun currentEnv(): String? = currentSession()?.let { service?.envOf?.get(it) }

    /** Sessions that were open when Android killed the app come back in their last folder. */
    private fun restoreOrNew(svc: TerminalService) {
        val saved = if (prefs.sp.getBoolean("restoreSessions", true)) svc.savedSessions() else emptyList()
        if (saved.isEmpty()) { newSession(null); return }
        var last: TerminalSession? = null
        for (s in saved.take(8)) {
            val env = s.env.takeIf { it == Rootfs.ANDROID || Rootfs.isReady(this, it) } ?: continue
            last = runCatching { svc.createSession(env, null, s.name.ifBlank { null }, startDir = s.cwd.ifBlank { null }, key = s.key.ifBlank { null }) }
                .onFailure { NibrasApp.record(this, "restore", it) }.getOrNull() ?: last
        }
        if (last != null) {
            showSession(last)
            Toast.makeText(this, getString(R.string.sessions_restored, svc.sessions.size), Toast.LENGTH_SHORT).show()
        } else newSession(null)
    }

    /** All lines of the scrollback + screen, with their row numbers (negative = history). */
    private fun transcript(s: TerminalSession): List<Pair<Int, String>> {
        val emu = s.emulator ?: return emptyList()
        val screen = emu.screen
        val cols = emu.mColumns
        return (-screen.activeTranscriptRows until emu.mRows).map { row ->
            row to runCatching { screen.getSelectedText(0, row, cols, row).trimEnd() }.getOrDefault("")
        }
    }

    /** Search the scrollback; tap a result to jump there. */
    private fun searchScrollback() {
        val s = currentSession() ?: return
        val field = EditText(this).apply { hint = getString(R.string.search_hint); setSingleLine() }
        AlertDialog.Builder(this).setTitle(R.string.tool_search).setView(field)
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = field.text.toString()
                val all = transcript(s)
                val hits = Search.lines(all.map { it.second }, q)
                if (hits.isEmpty()) { Toast.makeText(this, R.string.search_none, Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val labels = hits.reversed().take(200).map { i ->
                    val ctx = listOfNotNull(all.getOrNull(i - 1)?.second?.takeIf { it.isNotBlank() }?.let { "  $it" }, "▸ " + all[i].second)
                    ctx.joinToString("\n").take(300)
                }
                val rows = hits.reversed().take(200).map { all[it].first }
                AlertDialog.Builder(this).setTitle(getString(R.string.search_results, hits.size))
                    .setItems(labels.toTypedArray()) { _, w -> jumpToRow(rows[w]) }
                    .setNegativeButton(R.string.cancel, null).show()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun jumpToRow(row: Int) {
        val emu = terminal.mEmulator ?: return
        val top = (row - emu.mRows / 2).coerceIn(-emu.screen.activeTranscriptRows, 0)
        terminal.topRow = top
        terminal.invalidate()
    }

    private fun snippetsFile() = java.io.File(filesDir, "snippets.txt")

    private fun loadSnippets(): List<Snippet> {
        val f = snippetsFile()
        if (!f.isFile) runCatching { f.writeText(Snippets.format(Snippets.defaults)) }
        return runCatching { Snippets.parse(f.readText()) }.getOrDefault(Snippets.defaults)
    }

    /** Saved commands (Termius / JuiceSSH snippets): tap runs, long list edit opens the editor. */
    private fun snippets() {
        val list = loadSnippets()
        val labels = list.map { (if (it.run) "▶ " else "⎘ ") + it.name } + listOf("+ " + getString(R.string.snippet_add_current), "✎ " + getString(R.string.snippet_edit))
        AlertDialog.Builder(this).setTitle(R.string.tool_snippets).setItems(labels.toTypedArray()) { _, w ->
            when {
                w < list.size -> {
                    val sn = list[w]
                    currentSession()?.write(sn.command.replace("\n", "\r") + if (sn.run) "\r" else "")
                }
                w == list.size -> addSnippetFromLine()
                else -> EditorActivity.open(this, Rootfs.ANDROID, snippetsFile().absolutePath)
            }
        }.show()
    }

    private fun addSnippetFromLine() {
        val typed = currentTyped().trim()
        val name = EditText(this).apply { hint = getString(R.string.host_name); setSingleLine() }
        val cmd = EditText(this).apply { setText(typed); typeface = Typeface.MONOSPACE }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0); addView(name); addView(cmd) }
        AlertDialog.Builder(this).setTitle(R.string.snippet_add_current).setView(form)
            .setPositiveButton(R.string.ok) { _, _ ->
                val c = cmd.text.toString(); if (c.isBlank()) return@setPositiveButton
                val all = loadSnippets() + Snippet(name.text.toString().ifBlank { c.take(24) }, c)
                runCatching { snippetsFile().writeText(Snippets.format(all)) }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun currentTyped(): String {
        val emu = terminal.mEmulator ?: return ""
        val row = emu.cursorRow
        val line = runCatching { emu.screen.getSelectedText(0, row, emu.mColumns, row) }.getOrDefault("")
        return Suggest.typed(line, emu.cursorCol)
    }

    private val pathCache = HashMap<String, Pair<Long, Set<String>>>()
    private val historyCache = HashMap<String, Pair<Long, List<String>>>()

    private fun commandsOn(env: String): Set<String> {
        val now = System.currentTimeMillis()
        pathCache[env]?.let { if (now - it.first < 60_000) return it.second }
        val dirs = if (env == Rootfs.ANDROID) listOf(java.io.File("/system/bin"))
        else listOf("usr/local/bin", "usr/bin", "bin", "usr/sbin", "sbin", "root/bin").map { java.io.File(Rootfs.dir(this, env), it) }
        val set = dirs.flatMap { d -> d.list()?.toList() ?: emptyList() }.filter { !it.startsWith("termux-") }.toSet()
        pathCache[env] = now to set
        return set
    }

    private fun historyOf(env: String): List<String> {
        val now = System.currentTimeMillis()
        historyCache[env]?.let { if (now - it.first < 5_000) return it.second }
        val home = Rootfs.homeOf(this, env)
        val lines = listOf(".ash_history", ".bash_history", ".zsh_history", ".local/share/fish/fish_history").flatMap { name ->
            java.io.File(home, name).takeIf { it.isFile && it.length() < 2_000_000 }?.readLines()?.takeLast(2000).orEmpty()
        }.map { it.substringAfter(';', it).removePrefix("- cmd: ").trim() }.filter { it.isNotEmpty() && it.length < 300 }
        historyCache[env] = now to lines
        return lines
    }

    /** Suggestion chips above the extra keys, from history, snippets and commands on PATH. */
    private fun updateSuggestions() {
        val s = terminal.currentSession
        val env = s?.let { service?.envOf?.get(it) }
        val emu = terminal.mEmulator
        if (s == null || env == null || emu == null || emu.isAlternateBufferActive || !prefs.sp.getBoolean("suggestions", true)) {
            suggestScroll.visibility = View.GONE; return
        }
        val typed = currentTyped()
        if (typed.isBlank() || typed.length > 200) { suggestScroll.visibility = View.GONE; return }
        val choices = runCatching { Suggest.rank(typed, historyOf(env), commandsOn(env), loadSnippets(), 8) }.getOrDefault(emptyList())
        if (choices.isEmpty()) { suggestScroll.visibility = View.GONE; return }
        suggestBar.removeAllViews()
        val light = com.termux.terminal.TerminalColors.getPerceivedBrightnessOfColor(background) > 130
        for (c in choices) {
            suggestBar.addView(TextView(this).apply {
                text = c
                typeface = Typeface.MONOSPACE
                textSize = 13f
                maxLines = 1
                setTextColor(if (light) Color.BLACK else 0xFFEDEDED.toInt())
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(if (light) 0x22000000 else 0x33FFFFFF) }
                setOnClickListener {
                    val (back, add) = Suggest.completion(currentTyped(), c)
                    val sess = terminal.currentSession ?: return@setOnClickListener
                    if (back > 0) sess.write("\u007f".repeat(back))
                    sess.write(add)
                    suggestScroll.visibility = View.GONE
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
        }
        suggestScroll.visibility = View.VISIBLE
        suggestScroll.scrollTo(0, 0)
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
            if (primary.currentSession == changedSession) primary.onScreenUpdated()
            secondary?.let { if (it.currentSession == changedSession) it.onScreenUpdated() }
            if (terminal.currentSession == changedSession && prefs.sp.getBoolean("suggestions", true)) {
                handler.removeCallbacks(suggestTask); handler.postDelayed(suggestTask, 250)
            }
            val cwd = service?.cwdOf(changedSession).orEmpty()
            if (cwd.isNotEmpty() && cwd != lastProjectCwd && terminal.currentSession == changedSession) {
                lastProjectCwd = cwd
                applySettings()
            }
        }
        override fun onTitleChanged(changedSession: TerminalSession) { sessionAdapter.notifyDataSetChanged() }
        override fun onSessionFinished(finishedSession: TerminalSession) { sessionAdapter.notifyDataSetChanged() }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) { if (text != null) copy(text) }
        override fun onPasteTextFromClipboard(session: TerminalSession?) { paste(session) }
        override fun onBell(session: TerminalSession) {
            when (com.nibras.terminal.ui.TermProps.bell) {
                "beep" -> runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120) }
                "ignore" -> Unit
                else -> if (prefs.bellVibrate || com.nibras.terminal.ui.TermProps.bell == "vibrate" && com.nibras.terminal.ui.TermProps.props.containsKey("bell-character"))
                    getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        override fun onColorsChanged(session: TerminalSession) {
            if (primary.currentSession == session) primary.invalidate()
            secondary?.let { if (it.currentSession == session) it.invalidate() }
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
            if (com.nibras.terminal.ui.TermProps.urlOnClick && e != null && emulator != null && openUrlAt(e)) return
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
            val P = com.nibras.terminal.ui.TermProps
            val ch = e.getUnicodeChar(0).toChar()
            if (P.shortcutKeys.isNotEmpty() && (e.isCtrlPressed || e.isAltPressed)) {
                when {
                    P.matches(P.shortcutKeys["create-session"], e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, ch) -> { newSession(null); return true }
                    P.matches(P.shortcutKeys["next-session"], e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, ch) -> { switchSession(true); return true }
                    P.matches(P.shortcutKeys["previous-session"], e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, ch) -> { switchSession(false); return true }
                    P.matches(P.shortcutKeys["rename-session"], e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, ch) -> { session?.let { renameSession(it) }; return true }
                }
            }
            if (e.isCtrlPressed && e.isAltPressed && !P.disableHwShortcuts) {
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
                terminal.setTerminalCursorBlinkerRate(com.nibras.terminal.ui.TermProps.blinkRate.coerceAtLeast(100))
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

    // ---------------------------------------------------------------- voice, clipboard, monitor, battery, lock

    private val REQ_VOICE = 41

    /** Speak a command (AZ/TR/EN/RU); the text is typed at the prompt for you to check, then Enter. */
    private fun voiceInput() {
        val tag = when (Lang.current(this)) { "az" -> "az-AZ"; "tr" -> "tr-TR"; "ru" -> "ru-RU"; else -> "en-US" }
        val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        runCatching { startActivityForResult(i, REQ_VOICE) }.onFailure { Toast.makeText(this, R.string.voice_missing, Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOCK && resultCode == RESULT_OK) { pendingUnlock?.invoke(); pendingUnlock = null }
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val said = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            val cmd = VoiceCommands.toCommand(said)
            currentSession()?.write(cmd)
        }
    }

    private fun clipFile() = java.io.File(filesDir, "clipboard.txt")

    private fun rememberClip(text: String) {
        if (text.isBlank() || text.length > 20_000) return
        runCatching {
            val old = if (clipFile().isFile) com.nibras.terminal.core.Records.decode(clipFile().readText()).mapNotNull { it["t"] } else emptyList()
            val list = (listOf(text) + old.filter { it != text }).take(50)
            clipFile().writeText(com.nibras.terminal.core.Records.encode(list.map { mapOf("t" to it) }))
        }
    }

    private fun clipboardHistory() {
        getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.let { rememberClip(it) }
        val list = runCatching { com.nibras.terminal.core.Records.decode(clipFile().readText()).mapNotNull { it["t"] } }.getOrDefault(emptyList())
        if (list.isEmpty()) { Toast.makeText(this, R.string.search_none, Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle(R.string.menu_clipboard_history)
            .setItems(list.map { it.replace('\n', '⏎').take(120) }.toTypedArray()) { _, w -> currentSession()?.write(list[w]) }
            .setNeutralButton(R.string.files_delete) { _, _ -> clipFile().delete() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun startMonitor() {
        handler.removeCallbacks(monitorTask)
        val on = prefs.sp.getBoolean("monitor", false)
        monitorView.visibility = if (on) View.VISIBLE else View.GONE
        if (on) handler.post(monitorTask)
    }

    private var lastCpu = 0L to 0L

    private fun updateMonitor() {
        val am = getSystemService(android.app.ActivityManager::class.java)
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val usedMb = (mi.totalMem - mi.availMem) / 1048576
        val bm = getSystemService(android.os.BatteryManager::class.java)
        val batt = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // CPU used by Nibras and everything it started (all sessions), from /proc/self/stat + children.
        val now = android.os.SystemClock.elapsedRealtime()
        val cpu = android.os.Process.getElapsedCpuTime() + childCpuMs()
        val (t0, c0) = lastCpu
        val pct = if (t0 > 0 && now > t0) ((cpu - c0) * 100 / ((now - t0) * Runtime.getRuntime().availableProcessors())).coerceIn(0, 100) else 0
        lastCpu = now to cpu
        val free = java.io.File(filesDir.absolutePath).usableSpace / 1073741824.0
        monitorView.text = "CPU %d%% · RAM %d/%d MB · 🔋%d%% · %.1f GB".format(pct, usedMb, mi.totalMem / 1048576, batt, free)
    }

    private fun childCpuMs(): Long = runCatching {
        val f = java.io.File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
        // fields after ')' : index 13,14 = cutime, cstime (clock ticks, 100 Hz on Android)
        (f[13].toLong() + f[14].toLong()) * 10
    }.getOrDefault(0L)

    /** Battery saver: no wake lock, no cursor blink, no suggestions or monitor. */
    private fun toggleBatterySaver() {
        val on = !prefs.sp.getBoolean("batterySaver", false)
        prefs.sp.edit().putBoolean("batterySaver", on).apply()
        if (on) {
            service?.setWakeLock(false)
            prefs.cursorBlink = false
            prefs.sp.edit().putBoolean("suggestions", false).putBoolean("monitor", false).apply()
            terminal.setTerminalCursorBlinkerState(false, true)
            suggestScroll.visibility = View.GONE
            startMonitor()
        } else {
            prefs.sp.edit().putBoolean("suggestions", true).apply()
        }
        Toast.makeText(this, if (on) R.string.battery_saver_on else R.string.battery_saver_off, Toast.LENGTH_SHORT).show()
    }

    private fun askUnlock() = authenticate {
        unlocked = true
        lockOverlay.visibility = View.GONE
    }

    /** Fingerprint / face / screen lock (BiometricPrompt on Android 9+, lock-screen check before). */
    private fun authenticate(onOk: () -> Unit) {
        val km = getSystemService(android.app.KeyguardManager::class.java)
        if (!km.isDeviceSecure) { Toast.makeText(this, R.string.app_lock_no_screen_lock, Toast.LENGTH_LONG).show(); onOk(); return }
        if (Build.VERSION.SDK_INT >= 28) {
            val b = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.app_lock_prompt))
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
            }
            b.build().authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
            })
        } else {
            @Suppress("DEPRECATION")
            val i = km.createConfirmDeviceCredentialIntent(getString(R.string.app_name), getString(R.string.app_lock_prompt))
            if (i == null) onOk() else { pendingUnlock = onOk; startActivityForResult(i, REQ_LOCK) }
        }
    }

    private var pendingUnlock: (() -> Unit)? = null
    private val REQ_LOCK = 42

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Suppress("unused")
    private fun unusedInputType() = InputType.TYPE_NULL
}
