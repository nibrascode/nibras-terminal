package com.nibras.terminal.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nibras.terminal.core.GuestPaths
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.data.resolveInside
import java.io.File

/** Small view helpers so every Nibras screen shares one dark look (gold accent). */
object Ui {
    const val BG = 0xFF0B0F14.toInt()
    const val PANEL = 0xFF151B22.toInt()
    const val GOLD = 0xFFD6B36A.toInt()
    const val INK = 0xFFEDEDED.toInt()
    const val MUTED = 0xFF9AA0A6.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun screen(a: Activity, title: String): LinearLayout {
        WindowCompat.setDecorFitsSystemWindows(a.window, false)
        a.window.statusBarColor = BG; a.window.navigationBarColor = BG
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        root.addView(TextView(a).apply {
            text = title; setTextColor(GOLD); textSize = 20f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 8))
        })
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        a.setContentView(root)
        return root
    }

    fun button(c: Context, label: String, accent: Boolean = false, click: () -> Unit) = Button(c).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(if (accent) Color.BLACK else INK)
        background = GradientDrawable().apply { cornerRadius = dp(c, 8).toFloat(); setColor(if (accent) GOLD else PANEL) }
        setOnClickListener { click() }
    }

    fun field(c: Context, hint: String, value: String = "", mono: Boolean = false) = EditText(c).apply {
        this.hint = hint; setText(value); setTextColor(INK); setHintTextColor(MUTED); textSize = 15f
        if (mono) typeface = Typeface.MONOSPACE
    }

    fun label(c: Context, text: String, size: Float = 13f, color: Int = MUTED) = TextView(c).apply {
        this.text = text; setTextColor(color); textSize = size
    }

    fun row(c: Context, vararg views: View) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3)) }) }
    }

    fun lp(c: Context, margin: Int = 6) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { setMargins(dp(c, margin * 2), dp(c, margin / 2), dp(c, margin * 2), dp(c, margin / 2)) }
}

/** Maps a guest path (as seen inside an environment) to the real file, staying inside the rootfs. */
object HostPaths {
    fun envId(context: Context, envName: String?): String =
        Rootfs.all.firstOrNull { it.name.equals(envName, ignoreCase = true) || it.id == envName }?.id
            ?: if (envName.equals("android", true)) Rootfs.ANDROID else Rootfs.pick(context, null)

    fun toHost(context: Context, envId: String, guest: String): File? {
        if (envId == Rootfs.ANDROID) return File(guest)
        val path = GuestPaths.normalize(guest)
        if (path == "/sdcard" || path.startsWith("/sdcard/") || path.startsWith("/storage/")) {
            val ext = Environment.getExternalStorageDirectory()
            return if (path.startsWith("/storage/")) File(path) else File(ext, path.removePrefix("/sdcard"))
        }
        val root = Rootfs.dir(context, envId).toPath().toAbsolutePath().normalize()
        return runCatching { resolveInside(root, path, followLast = true).toFile() }.getOrNull()
    }

    fun toGuest(context: Context, envId: String, host: File): String {
        if (envId == Rootfs.ANDROID) return host.absolutePath
        val root = Rootfs.dir(context, envId).absolutePath
        val ext = Environment.getExternalStorageDirectory().absolutePath
        val p = host.absolutePath
        return when {
            p.startsWith(root) -> p.removePrefix(root).ifEmpty { "/" }
            p.startsWith(ext) -> "/sdcard" + p.removePrefix(ext)
            else -> p
        }
    }
}
