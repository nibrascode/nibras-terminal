package com.nibras.terminal

import android.content.Context
import android.content.Intent
import com.nibras.terminal.core.Rootfs

/** Opens the terminal with a command line in a new session (used by hosts, editor, files, snippets). */
object Launcher {
    fun command(context: Context, env: String?, line: String, keepShell: Boolean): List<String> {
        val id = Rootfs.pick(context, env)
        return if (id == Rootfs.ANDROID) {
            listOf("/system/bin/sh", "-c", if (keepShell) "$line; exec /system/bin/sh -i" else line)
        } else {
            listOf("/bin/sh", "-c", if (keepShell) "$line; printf '\\n'; exec /bin/sh -l" else line)
        }
    }

    fun run(context: Context, env: String?, line: String, title: String, keepShell: Boolean = false) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_COMMAND, command(context, env, line, keepShell).toTypedArray())
                .putExtra(MainActivity.EXTRA_ENV, env)
                .putExtra(MainActivity.EXTRA_TITLE, title),
        )
    }
}
