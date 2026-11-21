package com.nibras.terminal.api

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** termux-am equivalent: builds the Intent in-app (fast, no app_process) for am start / broadcast / startservice. */
object AmCommand {
    fun run(context: Context, args: List<String>): String {
        val cmd = args.firstOrNull() ?: return usage()
        val i = Intent()
        var k = 1
        fun next(): String = args.getOrNull(k++) ?: throw IllegalArgumentException("missing value after ${args[k - 2]}")
        while (k < args.size) {
            when (val a = args[k++]) {
                "-a" -> i.action = next()
                "-d" -> i.data = Uri.parse(next())
                "-t" -> i.type = next()
                "-c" -> i.addCategory(next())
                "-n" -> i.component = ComponentName.unflattenFromString(next()) ?: throw IllegalArgumentException("bad component")
                "-p" -> i.setPackage(next())
                "-f" -> i.flags = i.flags or next().let { if (it.startsWith("0x")) it.drop(2).toLong(16).toInt() else it.toInt() }
                "-e", "--es" -> { val key = next(); i.putExtra(key, next()) }
                "--esn" -> i.putExtra(next(), null as String?)
                "--ei" -> { val key = next(); i.putExtra(key, next().toInt()) }
                "--el" -> { val key = next(); i.putExtra(key, next().toLong()) }
                "--ef" -> { val key = next(); i.putExtra(key, next().toFloat()) }
                "--ed" -> { val key = next(); i.putExtra(key, next().toDouble()) }
                "--ez" -> { val key = next(); i.putExtra(key, next().lowercase().let { it == "true" || it == "1" || it == "t" }) }
                "--eu" -> { val key = next(); i.putExtra(key, Uri.parse(next())) }
                "--esa" -> { val key = next(); i.putExtra(key, next().split(Regex("(?<!\\\\),")).map { it.replace("\\,", ",") }.toTypedArray()) }
                "--eia" -> { val key = next(); i.putExtra(key, next().split(',').map { it.trim().toInt() }.toIntArray()) }
                "--ela" -> { val key = next(); i.putExtra(key, next().split(',').map { it.trim().toLong() }.toLongArray()) }
                "--grant-read-uri-permission" -> i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                "--grant-write-uri-permission" -> i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                "--activity-clear-task" -> i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                "--activity-clear-top" -> i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                "--activity-single-top" -> i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                "--activity-no-history" -> i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                "--activity-multiple-task" -> i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                "--activity-new-task" -> i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                "--include-stopped-packages" -> i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                "--receiver-foreground" -> i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                "-W", "-S", "-R", "--user", "-P", "--start-profiler", "--sampling", "--streaming", "--attach-agent" -> if (a in setOf("--user", "-R", "-P", "--start-profiler", "--sampling", "--attach-agent")) k++
                else -> if (!a.startsWith("-") && i.data == null && i.action == null) { // "am start <URI>"
                    val parsed = runCatching { Intent.parseUri(a, Intent.URI_INTENT_SCHEME) }.getOrNull()
                    if (parsed != null && a.startsWith("intent:")) { i.action = parsed.action; i.data = parsed.data; parsed.component?.let { i.component = it }; parsed.extras?.let { i.putExtras(it) } }
                    else i.data = Uri.parse(a)
                } else throw IllegalArgumentException("unsupported option: $a")
            }
        }
        if (i.action == null && i.component == null && i.data != null) i.action = Intent.ACTION_VIEW
        val main = Handler(Looper.getMainLooper())
        var error: Throwable? = null
        val gate = java.util.concurrent.CountDownLatch(1)
        main.post {
            try {
                when (cmd) {
                    "start", "start-activity" -> context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "broadcast" -> context.sendBroadcast(i)
                    "startservice", "start-service" -> context.startService(i)
                    "start-foreground-service" -> context.startForegroundService(i)
                    else -> throw IllegalArgumentException(usage())
                }
            } catch (t: Throwable) { error = t } finally { gate.countDown() }
        }
        gate.await(10, java.util.concurrent.TimeUnit.SECONDS)
        error?.let { throw it }
        return when (cmd) {
            "broadcast" -> "Broadcasting: $i\nBroadcast completed"
            "startservice", "start-service", "start-foreground-service" -> "Starting service: $i"
            else -> "Starting: $i"
        }
    }

    private fun usage() = "usage: nibras-am start|broadcast|startservice [-a ACTION] [-d URI] [-t TYPE] [-c CATEGORY] [-n PKG/.CLS] [-p PKG] [-f FLAGS] [--es K V] [--ei K N] [--ez K B] [--el K N] [--ef K F] [--eu K URI] [--esa K a,b] [--eia K 1,2]"
}
