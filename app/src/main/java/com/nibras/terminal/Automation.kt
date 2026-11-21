package com.nibras.terminal

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.PersistableBundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.CheckBox
import android.widget.Button
import android.widget.TextView
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Rootfs
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs a command in an environment without a terminal and captures its output (for automation). */
/** External apps (Tasker, intents) may run commands only after the user switches this on in the menu. */
object Automation {
    private const val KEY = "allowExternalApps"
    fun allowed(c: Context) = c.getSharedPreferences("nibras", Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun set(c: Context, on: Boolean) = c.getSharedPreferences("nibras", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
}

object Headless {
    data class Result(val exitCode: Int, val stdout: String, val stderr: String, val error: String? = null)

    fun run(context: Context, env: String?, command: String, workdir: String? = null, stdin: String? = null, timeoutSec: Long = 600): Result {
        return try {
            val id = Rootfs.pick(context, env)
            val extra = if (workdir.isNullOrBlank()) emptyList() else listOf("NIBRAS_START_DIR=$workdir")
            val launch = Rootfs.launch(context, id, listOf("/bin/sh", "-c", command).let { if (id == Rootfs.ANDROID) listOf(command) else it }, extra)
            val argv = launch.args.toMutableList()
            argv[0] = launch.exe
            if (id == Rootfs.ANDROID) { argv.clear(); argv += listOf("/system/bin/sh", "-c", command) }
            val pb = ProcessBuilder(argv).directory(File(launch.cwd))
            pb.environment().clear()
            launch.env.forEach { kv -> val i = kv.indexOf('='); if (i > 0) pb.environment()[kv.substring(0, i)] = kv.substring(i + 1) }
            val p = pb.start()
            thread { runCatching { p.outputStream.use { o -> if (stdin != null) o.write(stdin.toByteArray()) } } }
            var out = ""; var err = ""
            val t1 = thread { out = p.inputStream.bufferedReader().readText() }
            val t2 = thread { err = p.errorStream.bufferedReader().readText() }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); return Result(-1, out, err, "timeout") }
            t1.join(2000); t2.join(2000)
            Result(p.exitValue(), out.takeLast(100_000), err.takeLast(100_000))
        } catch (t: Throwable) {
            Result(-1, "", "", t.message ?: t.javaClass.simpleName)
        }
    }
}

/**
 * Automation entry point for Tasker, MacroDroid, Automate and other apps (Termux RUN_COMMAND style),
 * protected by the com.nibras.terminal.permission.RUN_COMMAND permission the calling app must hold.
 *
 * Extras: command (String, shell line), env (alpine/debian/ubuntu/android), workdir, stdin,
 * background (Boolean, default true), title, pending_intent (PendingIntent receiving
 * stdout / stderr / exit_code / error extras).
 */
class RunCommandService : Service() {
    companion object {
        const val ACTION = "com.nibras.terminal.RUN_COMMAND"
        fun envId(name: String?): String? = when (name?.lowercase()) {
            null, "" -> null
            "android" -> Rootfs.ANDROID
            else -> Rootfs.all.firstOrNull { it.name.equals(name, true) || it.id == name }?.id
        }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra("command")
        if (intent == null || command.isNullOrBlank() || !Automation.allowed(this)) { stopSelf(startId); return START_NOT_STICKY }
        val env = envId(intent.getStringExtra("env"))
        val background = intent.getBooleanExtra("background", true)
        if (!background) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_COMMAND, Launcher.command(this, env, command, keepShell = false).toTypedArray())
                .putExtra(MainActivity.EXTRA_ENV, env)
                .putExtra(MainActivity.EXTRA_TITLE, intent.getStringExtra("title") ?: "automation"))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        val reply: PendingIntent? = intent.getParcelableExtra("pending_intent")
        thread(name = "nibras-run") {
            val r = Headless.run(this, env, command, intent.getStringExtra("workdir"), intent.getStringExtra("stdin"))
            reply?.let {
                val data = Intent().putExtra("stdout", r.stdout).putExtra("stderr", r.stderr)
                    .putExtra("exit_code", r.exitCode).putExtra("error", r.error)
                runCatching { it.send(this, 0, data) }
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}

/** Tasker / Locale plug-in: edit screen. */
class TaskerEditActivity : Activity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = intent.getBundleExtra("com.twofortyfouram.locale.intent.extra.BUNDLE")
        val pad = (16 * resources.displayMetrics.density).toInt()
        val command = EditText(this).apply { hint = getString(R.string.auto_command); setText(old?.getString("command")) }
        val env = EditText(this).apply { hint = "alpine / debian / ubuntu / android"; setText(old?.getString("env")) }
        val wait = CheckBox(this).apply { text = getString(R.string.auto_wait); isChecked = old?.getBoolean("wait", true) ?: true }
        val save = Button(this).apply { text = getString(R.string.ok) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad)
            addView(TextView(this@TaskerEditActivity).apply { text = getString(R.string.auto_title); textSize = 18f })
            addView(command); addView(env); addView(wait); addView(save)
        })
        save.setOnClickListener {
            val b = Bundle().apply {
                putString("command", command.text.toString()); putString("env", env.text.toString()); putBoolean("wait", wait.isChecked)
            }
            val blurb = command.text.toString().take(60)
            setResult(RESULT_OK, Intent().putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", b)
                .putExtra("com.twofortyfouram.locale.intent.extra.BLURB", blurb)
                .putExtra("net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", "command")
                .putExtra("net.dinglisch.android.tasker.RELEVANT_VARIABLES", arrayOf("%stdout\nOutput", "%stderr\nErrors", "%exit_code\nExit code")))
            finish()
        }
    }
}

/** Tasker / Locale plug-in: fire. Results come back to Tasker as %stdout, %stderr, %exit_code. */
class TaskerFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Automation.allowed(context)) return
        val b = intent.getBundleExtra("com.twofortyfouram.locale.intent.extra.BUNDLE") ?: return
        val command = b.getString("command").orEmpty()
        if (command.isBlank()) return
        val env = RunCommandService.envId(b.getString("env"))
        val pending = goAsync()
        thread(name = "nibras-tasker") {
            val r = Headless.run(context, env, command, timeoutSec = 25)
            if (isOrderedBroadcast) {
                val vars = Bundle().apply { putString("%stdout", r.stdout); putString("%stderr", r.stderr); putString("%exit_code", r.exitCode.toString()) }
                pending.setResultCode(if (r.exitCode == 0) 16 /* Tasker: SUCCESS */ else 17 /* FAILED */)
                pending.setResultExtras(Bundle().apply { putBundle("net.dinglisch.android.tasker.extras.VARIABLES", vars) })
            }
            pending.finish()
        }
    }
}

/** nibras-job-scheduler: runs a script periodically / when charging / on Wi-Fi, even if the app is closed. */
class ScriptJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val command = params.extras.getString("command") ?: return false
        val env = params.extras.getString("env")
        thread(name = "nibras-job") {
            val r = Headless.run(this, env, command, timeoutSec = 540)
            runCatching {
                File(filesDir, "jobs").mkdirs()
                File(filesDir, "jobs/${params.jobId}.log").writeText("exit=${r.exitCode}\n${r.stdout}\n${r.stderr}\n${r.error ?: ""}")
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        /** args: "--script <cmd> [--period-ms N] [--network any|unmetered] [--charging] [--id N]" | "--cancel N" | "--list" */
        fun handle(context: Context, args: List<String>, env: String?): String {
            val js = context.getSystemService(JobScheduler::class.java)
            fun opt(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
            if ("--list" in args || args.isEmpty()) {
                return js.allPendingJobs.filter { it.service.className == ScriptJobService::class.java.name }
                    .joinToString("\n") { "${it.id}: ${it.extras.getString("command")} (every ${it.intervalMillis / 60000} min)" }.ifBlank { "-" }
            }
            opt("--cancel")?.toIntOrNull()?.let { js.cancel(it); return "cancelled $it" }
            if ("--cancel-all" in args) {
                js.allPendingJobs.filter { it.service.className == ScriptJobService::class.java.name }.forEach { js.cancel(it.id) }
                return "cancelled all"
            }
            val script = opt("--script") ?: return "usage: nibras-job-scheduler --script <command> [--period-ms 900000] [--network any|unmetered] [--charging] [--id N] | --list | --cancel N"
            val id = opt("--id")?.toIntOrNull() ?: (1000 + (script.hashCode() and 0xffff))
            val period = (opt("--period-ms")?.toLongOrNull() ?: 15 * 60_000L).coerceAtLeast(15 * 60_000L)
            val extras = PersistableBundle().apply { putString("command", script); putString("env", env) }
            val b = JobInfo.Builder(id, ComponentName(context, ScriptJobService::class.java))
                .setExtras(extras).setPeriodic(period).setPersisted(true)
            when (opt("--network")) {
                "any" -> b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                "unmetered" -> b.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
            }
            if ("--charging" in args) b.setRequiresCharging(true)
            val ok = js.schedule(b.build()) == JobScheduler.RESULT_SUCCESS
            return if (ok) "scheduled job $id every ${period / 60000} min (log: files/jobs/$id.log)" else "failed"
        }
    }
}
