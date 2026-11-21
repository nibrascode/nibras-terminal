package com.nibras.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.Ui

/**
 * Nibras Store: install packs by category. Nothing here is bundled in the APK; each pack is
 * downloaded into the Linux environment by "install <pack>" when the user taps it.
 */
class StoreActivity : Activity() {
    data class Pack(val id: String, val icon: String, val cat: Int, val en: String, val az: String, val tr: String, val ru: String, val usage: String)

    companion object {
        val CATS = listOf(
            listOf("Media", "Media", "Medya", "Медиа"),
            listOf("Documents", "Sənədlər", "Belgeler", "Документы"),
            listOf("Internet & sharing", "İnternet və paylaşım", "İnternet ve paylaşım", "Интернет"),
            listOf("Programming", "Proqramlaşdırma", "Programlama", "Программирование"),
            listOf("System", "Sistem", "Sistem", "Система"),
            listOf("Learning & fun", "Öyrənmə və əyləncə", "Öğrenme ve eğlence", "Обучение и игры"),
        )
        val PACKS = listOf(
            Pack("video", "🎬", 0, "Merge, cut, compress, convert videos", "Videoları birləşdir, kəs, sıx, çevir", "Videoları birleştir, kes, sıkıştır", "Склейка, обрезка, сжатие видео", "video merge a.mp4 b.mp4"),
            Pack("audio", "🎵", 0, "Convert, cut, normalize, record audio", "Səsi çevir, kəs, yaz", "Sesi dönüştür, kes, kaydet", "Аудио: конвертация, запись", "audio convert in.wav out.mp3"),
            Pack("image", "🖼", 0, "Resize, convert, compress images", "Şəkilləri kiçilt, çevir, sıx", "Resimleri boyutlandır, dönüştür", "Изменение размера изображений", "image resize photo.jpg 1080"),
            Pack("download", "⬇", 0, "Download videos, music and files (yt-dlp, aria2)", "Video, musiqi, fayl yüklə (yt-dlp, aria2)", "Video, müzik, dosya indir", "Загрузка видео, музыки, файлов", "download <url>"),
            Pack("record", "⏺", 0, "Record terminal sessions", "Terminal sessiyasını yaz", "Terminal oturumunu kaydet", "Запись сессий", "record"),
            Pack("pdf", "📄", 1, "Merge, split, compress, lock PDFs", "PDF birləşdir, böl, sıx, kilidlə", "PDF birleştir, böl, sıkıştır", "PDF: склеить, разделить, сжать", "pdf merge a.pdf b.pdf"),
            Pack("ocr", "🔤", 1, "Text from photos and scans", "Şəkildən mətn oxu", "Resimden metin", "Текст с фото", "ocr photo.jpg"),
            Pack("office", "📝", 1, "Markdown → DOCX / PDF / HTML (pandoc)", "Markdown → DOCX / PDF / HTML", "Markdown → DOCX / PDF / HTML", "Markdown → DOCX / PDF", "office notes.md notes.docx"),
            Pack("notes", "🗒", 1, "Markdown notes in the terminal", "Terminalda qeydlər", "Terminalde notlar", "Заметки", "notes new"),
            Pack("vault", "🔐", 1, "Encrypted vault for private files", "Şəxsi fayllar üçün şifrəli seyf", "Şifreli kasa", "Зашифрованное хранилище", "vault lock folder"),
            Pack("web", "🌐", 2, "Instant web server", "Ani veb server", "Anında web sunucu", "Веб-сервер", "web ."),
            Pack("share", "📡", 2, "Share folders: WebDAV, FTP, DLNA + QR", "Qovluq paylaş: WebDAV, FTP, DLNA + QR", "Klasör paylaş: WebDAV, FTP", "Общий доступ к папкам", "share webdav /sdcard"),
            Pack("webterm", "💻", 2, "Use this terminal from a PC browser", "Bu terminalı kompüter brauzerində aç", "Bu terminali PC tarayıcısında aç", "Терминал в браузере ПК", "webterm"),
            Pack("net", "📶", 2, "Network scan, speed test, DNS, ports", "Şəbəkə skanı, sürət testi, DNS", "Ağ tarama, hız testi", "Сеть: скан, скорость", "net speed"),
            Pack("backup", "💾", 2, "Backups to phone or cloud (rclone)", "Telefona və ya buluda ehtiyat", "Telefona veya buluta yedek", "Резервные копии", "backup home"),
            Pack("git", "🌿", 3, "Git with easy commands", "Asan Git", "Kolay Git", "Простой Git", "nibras git get <url>"),
            Pack("python", "🐍", 3, "Python 3, pip, venv", "Python 3, pip, venv", "Python 3, pip, venv", "Python 3, pip, venv", "py"),
            Pack("node", "🟩", 3, "Node.js and npm", "Node.js və npm", "Node.js ve npm", "Node.js и npm", "node"),
            Pack("dev", "🛠", 3, "C/C++, Go, Rust, make, cmake", "C/C++, Go, Rust, make, cmake", "C/C++, Go, Rust", "C/C++, Go, Rust", "gcc hello.c"),
            Pack("apk", "📦", 3, "Build, decompile and sign APKs", "APK yığ, aç, imzala", "APK derle, aç, imzala", "Сборка и подпись APK", "nibras apk new MyApp"),
            Pack("llm", "🤖", 3, "Offline AI chat (llama.cpp)", "Oflayn süni intellekt", "Çevrimdışı yapay zeka", "Офлайн ИИ", "llm"),
            Pack("desktop", "🖥", 4, "Linux desktop (XFCE) inside the app", "Tətbiqin içində Linux masaüstü", "Uygulama içinde Linux masaüstü", "Рабочий стол Linux", "nibras desktop"),
            Pack("monitor", "📊", 4, "System monitor (htop / btop)", "Sistem monitoru", "Sistem monitörü", "Системный монитор", "monitor"),
            Pack("security", "🛡", 4, "Security tools for your own systems", "Öz sistemlərin üçün təhlükəsizlik alətləri", "Kendi sistemlerin için güvenlik araçları", "Инструменты безопасности", "nmap -F 192.168.1.1"),
            Pack("learn", "🎓", 5, "Linux lessons and quizzes", "Linux dərsləri və testlər", "Linux dersleri ve testler", "Уроки Linux и тесты", "learn"),
            Pack("games", "🎮", 5, "Classic terminal games", "Klassik terminal oyunları", "Klasik terminal oyunları", "Терминальные игры", "games"),
            Pack("quran", "📖", 5, "Quran and hadith (AZ/TR/EN/RU)", "Quran və hədis", "Kuran ve hadis", "Коран и хадисы", "quran 1:1"),
            Pack("prayer", "🕌", 5, "Prayer times and reminders", "Namaz vaxtları və xatırlatmalar", "Namaz vakitleri", "Время намаза", "prayer city Baku"),
        )
    }

    private val lang by lazy { Lang.current(this) }
    private fun pick(en: String, az: String, tr: String, ru: String) = when (lang) { "az" -> az; "tr" -> tr; "ru" -> ru; else -> en }
    private var cat = -1
    private val rows = mutableListOf<Any>()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.screen(this, getString(R.string.tool_store))
        root.addView(Ui.label(this, getString(R.string.store_hint)).apply { setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 6)) })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        val all = listOf(pick("All", "Hamısı", "Tümü", "Все")) + CATS.map { pick(it[0], it[1], it[2], it[3]) }
        all.forEachIndexed { i, name ->
            chips.addView(Ui.button(this, name, accent = i == 0) { cat = i - 1; refresh(); for (k in 0 until chips.childCount) (chips.getChildAt(k) as TextView).alpha = if (k == i) 1f else 0.6f },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(Ui.dp(this@StoreActivity, 4), 0, Ui.dp(this@StoreActivity, 4), 0) })
        }
        root.addView(scroll, Ui.lp(this, 2))
        val list = ListView(this).apply {
            adapter = this@StoreActivity.adapter; divider = null
            setOnItemClickListener { _, _, pos, _ -> (rows.getOrNull(pos) as? Pack)?.let { open(it) } }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun refresh() {
        rows.clear()
        CATS.indices.filter { cat < 0 || it == cat }.forEach { c ->
            rows.add(c)
            rows.addAll(PACKS.filter { it.cat == c })
        }
        adapter.notifyDataSetChanged()
    }

    private fun open(p: Pack) {
        AlertDialog.Builder(this).setTitle("${p.icon} ${p.id}")
            .setMessage(pick(p.en, p.az, p.tr, p.ru) + "\n\n$ install ${p.id}\n$ ${p.usage}")
            .setPositiveButton(R.string.store_install) { _, _ -> Launcher.run(this, null, "nibras-kit install ${p.id}", "install ${p.id}", keepShell = true); finish() }
            .setNeutralButton(R.string.store_try) { _, _ -> Launcher.run(this, null, "nibras-kit menu >/dev/null 2>&1; printf '%s\\n' '${p.usage}'", p.id, keepShell = true); finish() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int) = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(p: Int) = if (rows[p] is Pack) 0 else 1
        override fun isEnabled(p: Int) = rows[p] is Pack
        override fun getView(p: Int, convertView: View?, parent: ViewGroup?): View {
            val v = (convertView as? TextView) ?: TextView(this@StoreActivity)
            when (val r = rows[p]) {
                is Pack -> v.apply {
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 8)); textSize = 15f; setTextColor(Ui.INK)
                    typeface = Typeface.DEFAULT
                    text = "${r.icon}  ${r.id}\n      ${pick(r.en, r.az, r.tr, r.ru)}"
                }
                is Int -> v.apply {
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 2)); textSize = 13f; setTextColor(Ui.GOLD)
                    typeface = Typeface.DEFAULT_BOLD
                    text = CATS[r].let { pick(it[0], it[1], it[2], it[3]) }.uppercase()
                }
            }
            return v
        }
    }
}
