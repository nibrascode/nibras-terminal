package com.nibras.terminal

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records any uncaught crash to files/crash.txt so the next launch can show and share it. */
class NibrasApp : Application() {
    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "crash.txt")

        fun record(context: android.content.Context, where: String, t: Throwable) {
            runCatching {
                val sw = StringWriter()
                t.printStackTrace(PrintWriter(sw))
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                val head = "Nibras Terminal $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL} · ${Build.SUPPORTED_ABIS.joinToString()}\n" +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()) + " · $where\n\n"
                crashFile(context).writeText(head + sw.toString().take(20_000))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(this, "thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
