package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.HostPaths
import com.nibras.terminal.ui.Ui
import java.io.File

/** Project screen: the phone is the device. Run, files, layout preview, new activity. */
class StudioActivity : Activity() {
    companion object {
        fun open(context: Context, env: String?, guestDir: String) = context.startActivity(
            Intent(context, StudioActivity::class.java).putExtra("env", env).putExtra("path", guestDir)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private lateinit var envId: String
    private lateinit var guest: String
    private lateinit var dir: File
    private lateinit var files: LinearLayout
    private var pkg = ""

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        envId = HostPaths.envId(this, intent.getStringExtra("env"))
        guest = intent.getStringExtra("path") ?: "/root"
        dir = HostPaths.toHost(this, envId, guest) ?: run { finish(); return }
        pkg = applicationId(dir)
        val root = Ui.screen(this, "▣ " + t("Studio", "Studiya", "Stüdyo", "Студия") + " · " + dir.name)
        root.addView(Ui.label(this, deviceLine(), 13f, Ui.INK), Ui.lp(this, 8))
        if (pkg.isNotEmpty()) root.addView(Ui.label(this, pkg, 12f), Ui.lp(this, 4))
        root.addView(Ui.row(this,
            Ui.button(this, "▶ " + t("Run", "Qur", "Çalıştır", "Запуск"), accent = true) { shell("nibras-apk run ${q(guest)}", "run") },
            Ui.button(this, t("Build", "Yığ", "Derle", "Сборка")) { shell("nibras-apk build ${q(guest)}", "build") },
            Ui.button(this, t("Test", "Test", "Test", "Тест")) { shell("nibras-apk test ${q(guest)}", "test") },
            Ui.button(this, "Lint") { shell("nibras-apk lint ${q(guest)}", "lint") },
        ), Ui.lp(this, 2))
        root.addView(Ui.row(this,
            Ui.button(this, "Logcat") { shell(if (pkg.isEmpty()) "nibras-apk logcat" else "nibras-apk logcat ${q(pkg)}", "logcat") },
            Ui.button(this, t("Layout", "Görünüş", "Yerleşim", "Макет")) { pickLayout() },
            Ui.button(this, t("Resources", "Resurs", "Kaynak", "Ресурсы")) { ResEditorActivity.open(this, envId, guest) },
            Ui.button(this, t("Problems", "Xətalar", "Hatalar", "Ошибки")) { shell("nibras-apk errors", "errors") },
        ), Ui.lp(this, 2))
        root.addView(Ui.row(this,
            Ui.button(this, "+ " + t("Activity", "Activity", "Activity", "Activity")) { askNew("activity") },
            Ui.button(this, "+ " + t("Class", "Sinif", "Sınıf", "Класс")) { askNew("class") },
            Ui.button(this, "+ " + t("Screen", "Ekran", "Ekran", "Экран")) { askScreen() },
            Ui.button(this, "+ " + t("Library", "Kitabxana", "Kütüphane", "Библиотека")) { askDep() },
        ), Ui.lp(this, 2))
        files = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(files) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        showFiles()
    }

    override fun onResume() {
        super.onResume()
        if (::files.isInitialized) showFiles()
    }

    private fun deviceLine(): String {
        val phone = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
        return t(
            "$phone  ·  API ${Build.VERSION.SDK_INT}  ·  this phone is the device",
            "$phone  ·  API ${Build.VERSION.SDK_INT}  ·  cihaz bu telefondur",
            "$phone  ·  API ${Build.VERSION.SDK_INT}  ·  cihaz bu telefon",
            "$phone  ·  API ${Build.VERSION.SDK_INT}  ·  устройство — этот телефон",
        )
    }

    private fun showFiles() {
        files.removeAllViews()
        val src = listOf(File(dir, "app/src/main"), File(dir, "src/main"), dir).firstOrNull { it.isDirectory } ?: dir
        val list = ArrayList<File>()
        walk(src, list)
        list.sortBy { it.absolutePath }
        if (list.isEmpty()) {
            files.addView(Ui.label(this, t("No source files yet.", "Mənbə faylı yoxdur.", "Kaynak dosya yok.", "Исходных файлов нет."), 15f))
            return
        }
        val base = src.absolutePath
        list.take(180).forEach { f ->
            val rel = f.absolutePath.removePrefix(base).removePrefix("/")
            val row = TextView(this).apply {
                text = rel
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setTextColor(if (rel.contains("/layout/") || rel.endsWith(".xml")) Ui.GOLD else Ui.INK)
                setPadding(Ui.dp(this@StudioActivity, 16), Ui.dp(this@StudioActivity, 8), Ui.dp(this@StudioActivity, 16), Ui.dp(this@StudioActivity, 8))
                setOnClickListener {
                    val g = HostPaths.toGuest(this@StudioActivity, envId, f)
                    EditorActivity.open(this@StudioActivity, envId, g)
                }
                setOnLongClickListener {
                    if (f.extension == "xml") { preview(f); true } else false
                }
            }
            files.addView(row)
        }
    }

    private fun walk(dir: File, out: MutableList<File>) {
        if (out.size > 400) return
        val kids = dir.listFiles() ?: return
        for (f in kids) {
            if (f.name == "build" || f.name == ".gradle" || f.name.startsWith(".")) continue
            if (f.isDirectory) walk(f, out)
            else if (f.extension in setOf("kt", "java", "xml", "gradle", "kts")) out.add(f)
        }
    }

    private fun pickLayout() {
        val res = listOf(File(dir, "app/src/main/res"), File(dir, "src/main/res"), File(dir, "res")).firstOrNull { it.isDirectory }
        val layouts = res?.listFiles()?.filter { it.isDirectory && it.name.startsWith("layout") }
            ?.flatMap { it.listFiles()?.toList().orEmpty() }?.filter { it.extension == "xml" }.orEmpty()
        if (layouts.isEmpty()) {
            Toast.makeText(this, t("No layout yet. + Screen", "Layout yoxdur. + Ekran", "Yerleşim yok. + Ekran", "Макета нет. + Экран"), Toast.LENGTH_LONG).show()
            return
        }
        val names = layouts.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle(t("Layout", "Görünüş", "Yerleşim", "Макет"))
            .setItems(names) { _, w -> preview(layouts[w]) }.show()
    }

    private fun preview(file: File) {
        val xml = runCatching { file.readText() }.getOrElse {
            Toast.makeText(this, it.message, Toast.LENGTH_LONG).show(); return
        }
        val view = runCatching { LayoutPreview.render(this, xml, file.parentFile) }.getOrElse {
            Toast.makeText(this, "XML: ${it.message}", Toast.LENGTH_LONG).show(); return
        }
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            addView(view)
        }
        AlertDialog.Builder(this).setTitle(file.name).setView(ScrollView(this).apply { addView(frame) })
            .setPositiveButton(android.R.string.ok, null).show()
            .window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun askNew(kind: String) {
        val field = Ui.field(this, if (kind == "class") "Helper" else "Details")
        AlertDialog.Builder(this).setTitle(if (kind == "class") t("New class", "Yeni sinif", "Yeni sınıf", "Новый класс") else t("New activity", "Yeni activity", "Yeni activity", "Новая activity"))
            .setView(pad(field)).setPositiveButton(android.R.string.ok) { _, _ ->
                val name = field.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                quiet("nibras-apk $kind ${q(name)} ${q(guest)}")
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun askScreen() {
        val kinds = arrayOf("button", "list", "form", "empty")
        AlertDialog.Builder(this).setTitle(t("Screen", "Ekran", "Ekran", "Экран")).setItems(kinds) { _, w ->
            val field = Ui.field(this, "home")
            AlertDialog.Builder(this).setTitle(kinds[w]).setView(pad(field)).setPositiveButton(android.R.string.ok) { _, _ ->
                val name = field.text.toString().trim().ifEmpty { "home" }
                quiet("nibras-apk screen ${kinds[w]} ${q(name)} ${q(guest)}")
            }.setNegativeButton(android.R.string.cancel, null).show()
        }.show()
    }

    private fun askDep() {
        val field = Ui.field(this, "androidx.recyclerview:recyclerview:1.4.0", mono = true)
        AlertDialog.Builder(this).setTitle(t("Library", "Kitabxana", "Kütüphane", "Библиотека"))
            .setView(pad(field)).setPositiveButton(android.R.string.ok) { _, _ ->
                val c = field.text.toString().trim()
                if (c.isEmpty()) return@setPositiveButton
                quiet("nibras-apk dep ${q(c)} ${q(guest)}")
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun pad(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(this@StudioActivity, 16), Ui.dp(this@StudioActivity, 8), Ui.dp(this@StudioActivity, 16), 0)
        addView(v)
    }

    private fun shell(line: String, title: String) = Launcher.run(this, envId, "cd ${q(guest)} && $line", title, keepShell = true)

    private fun quiet(line: String) {
        Toast.makeText(this, "…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = Headless.run(this, envId, "cd ${q(guest)} && $line", timeoutSec = 60)
            runOnUiThread {
                val msg = (r.stdout + r.stderr + (r.error ?: "")).trim().lineSequence().take(6).joinToString("\n")
                Toast.makeText(this, msg.ifBlank { if (r.exitCode == 0) "✓" else "exit ${r.exitCode}" }, Toast.LENGTH_LONG).show()
                showFiles()
            }
        }.start()
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun applicationId(root: File): String {
        val files = listOf("app/build.gradle", "app/build.gradle.kts", "build.gradle", "build.gradle.kts").map { File(root, it) }
        val text = files.firstOrNull { it.isFile }?.readText().orEmpty()
        return Regex("applicationId\\s*(?:=\\s*)?\\(?\\s*['\"]([^'\"]+)").find(text)?.groupValues?.get(1).orEmpty()
    }

    private fun t(en: String, az: String, tr: String, ru: String) = when (Lang.current(this)) {
        "az" -> az
        "tr" -> tr
        "ru" -> ru
        else -> en
    }
}
