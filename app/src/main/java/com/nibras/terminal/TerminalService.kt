package com.nibras.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.nibras.terminal.api.ApiBridge
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Rootfs
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

/**
 * Keeps terminal sessions alive in the background (foreground service + notification),
 * owns the wake lock and the phone API bridge. Like Termux's TermuxService.
 */
class TerminalService : Service(), ApiBridge.Host {
    companion object {
        const val ACTION_STOP = "com.nibras.terminal.STOP"
        const val ACTION_WAKE = "com.nibras.terminal.WAKE"
        const val ACTION_RUN = "com.nibras.terminal.RUN"
        const val ACTION_BOOT = "com.nibras.terminal.BOOT"
        const val EXTRA_ENV = "env"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_BACKGROUND = "background"
        const val EXTRA_TITLE = "title"
        private const val CHANNEL = "sessions"
        private const val NOTE_ID = 1337
        const val TRANSCRIPT_ROWS = 5000
    }

    /** Implemented by MainActivity while it is bound. */
    interface Ui {
        val sessionClient: TerminalSessionClient
        fun onSessionsChanged()
        fun reloadSettings()
        fun requestPermission(permission: String)
        fun requestStorage()
        fun finishUi()
        fun showSession(session: TerminalSession)
    }

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    private val binder = LocalBinder()
    val sessions = mutableListOf<TerminalSession>()
    val envOf = HashMap<TerminalSession, String>()
    private val background = HashSet<TerminalSession>()
    var ui: Ui? = null
        set(value) {
            field = value
            sessions.forEach { it.updateTerminalSessionClient(client) }
        }
    lateinit var api: ApiBridge
        private set
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    val wakeLocked: Boolean get() = wakeLock?.isHeld == true

    /** Forwards to the activity when attached; keeps background sessions running otherwise. */
    val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) { ui?.sessionClient?.onTextChanged(changedSession) }
        override fun onTitleChanged(changedSession: TerminalSession) { ui?.sessionClient?.onTitleChanged(changedSession) }
        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (finishedSession in background) {
                removeSession(finishedSession)
            } else {
                ui?.sessionClient?.onSessionFinished(finishedSession)
            }
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) { ui?.sessionClient?.onCopyTextToClipboard(session, text) }
        override fun onPasteTextFromClipboard(session: TerminalSession?) { ui?.sessionClient?.onPasteTextFromClipboard(session) }
        override fun onBell(session: TerminalSession) { ui?.sessionClient?.onBell(session) }
        override fun onColorsChanged(session: TerminalSession) { ui?.sessionClient?.onColorsChanged(session) }
        override fun onTerminalCursorStateChange(state: Boolean) { ui?.sessionClient?.onTerminalCursorStateChange(state) }
        override fun getTerminalCursorStyle(): Int? = ui?.sessionClient?.terminalCursorStyle
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        startForegroundCompat()
        api = ApiBridge(applicationContext, this)
        api.start()
    }

    private fun startForegroundCompat() {
        val note = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTE_ID, note)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                sessions.toList().forEach { it.finishIfRunning() }
                sessions.clear()
                envOf.clear()
                setWakeLock(false)
                ui?.finishUi()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKE -> setWakeLock(!wakeLocked)
            ACTION_RUN -> {
                val command = intent.getStringArrayExtra(EXTRA_COMMAND)?.toList()
                val bg = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
                createSession(intent.getStringExtra(EXTRA_ENV), command, intent.getStringExtra(EXTRA_TITLE), bg)
            }
            ACTION_BOOT -> runBootScripts()
        }
        return START_NOT_STICKY
    }

    fun createSession(env: String?, command: List<String>? = null, title: String? = null, inBackground: Boolean = false): TerminalSession {
        val id = Rootfs.pick(this, env)
        if (id != Rootfs.ANDROID) Rootfs.refreshSessionFiles(this, id, api.port, api.token)
        val launch = Rootfs.launch(this, id, command)
        val session = TerminalSession(launch.exe, launch.cwd, launch.args, launch.env, TRANSCRIPT_ROWS, client)
        session.mSessionName = title ?: Rootfs.name(id)
        sessions += session
        envOf[session] = id
        if (inBackground) {
            background += session
            session.initializeEmulator(80, 24) // starts the process without a view
        }
        updateNotification()
        ui?.onSessionsChanged()
        return session
    }

    fun removeSession(session: TerminalSession) {
        session.finishIfRunning()
        sessions.remove(session)
        envOf.remove(session)
        background.remove(session)
        updateNotification()
        ui?.onSessionsChanged()
        if (sessions.isEmpty() && ui == null) {
            setWakeLock(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Termux:Boot: executables in ~/.nibras/boot (or ~/.termux/boot) run once per boot. */
    private fun runBootScripts() {
        val home = Rootfs.homeOf(this)
        val scripts = listOf(File(home, ".nibras/boot"), File(home, ".termux/boot"))
            .flatMap { it.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .sortedBy { it.name }
        if (scripts.isEmpty()) {
            if (sessions.isEmpty()) stopSelf()
            return
        }
        setWakeLock(true)
        for (f in scripts) {
            val guest = "/root/" + f.relativeTo(home).path
            createSession(null, listOf(guest), "boot: ${f.name}", inBackground = true)
        }
    }

    override fun setWakeLock(on: Boolean): Boolean {
        if (on && !wakeLocked) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nibras:terminal").apply { acquire() }
            @Suppress("DEPRECATION")
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nibras:terminal")?.apply { acquire() }
        } else if (!on) {
            wakeLock?.takeIf { it.isHeld }?.release()
            wifiLock?.takeIf { it.isHeld }?.release()
            wakeLock = null
            wifiLock = null
        }
        updateNotification()
        return wakeLocked
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val exit = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP), flags)
        val wake = PendingIntent.getService(this, 2, Intent(this, TerminalService::class.java).setAction(ACTION_WAKE), flags)
        var text = getString(R.string.notif_text, sessions.size)
        if (wakeLocked) text += " · " + getString(R.string.notif_wake_on)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_exit), exit).build())
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(if (wakeLocked) R.string.notif_release else R.string.notif_acquire),
                    wake,
                ).build(),
            )
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTE_ID, buildNotification())
    }

    override fun onDestroy() {
        sessions.toList().forEach { it.finishIfRunning() }
        sessions.clear()
        setWakeLock(false)
        api.stop()
        super.onDestroy()
    }

    // ApiBridge.Host
    override fun reloadSettings() { ui?.reloadSettings() }
    override fun newSession(env: String?, command: List<String>?) {
        val s = createSession(env, command)
        ui?.showSession(s)
    }
    override fun requestPermission(permission: String) { ui?.requestPermission(permission) }
    override fun requestStorage() { ui?.requestStorage() }
}
