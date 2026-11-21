package com.nibras.terminal

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nibras.terminal.data.CatalogStore
import com.nibras.terminal.data.InstallStore
import com.nibras.terminal.data.PayloadClient
import com.nibras.terminal.data.extractArchive
import com.nibras.terminal.data.payloadFile
import com.nibras.terminal.data.seedSmallPayloads
import com.nibras.terminal.data.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MOTD = """| \ | (_) |__  _ __ __ _ ___ 
|  \| | | '_ \| '__/ _` / __|
| |\  | | |_) | | | (_| \__ \
|_| \_|_|_.__/|_|  \__,_|___/

Welcome to Nibras Terminal!

Community:  https://github.com/nibrascode/nibras-terminal

Setup Nibras:
- Help: help

Working with packages:
- Phone files: nibras-setup-storage
- Python: pip install <paket>
- Run: python3 fayl.py
- Search: pkg search <query>
- Install: pkg install <package>
- Preview: preview fayl.md
- Hosts: host add ad user@host
- Backup: nibras-backup
- Check: nibras-doctor

Report issues at https://github.com/nibrascode/nibras-terminal/issues"""

data class UiState(
    val components: List<Component> = emptyList(),
    val installed: Map<String, InstallRecord> = emptyMap(),
    val tab: Tab = Tab.Session,
    val busyId: String? = null,
    val progress: Float = 0f,
    val notice: String? = null,
    val editorId: String? = null,
    val editorText: String = "",
    val sessionLog: String = MOTD,
    val permissionAsk: String? = null,
    val permissionNonce: Int = 0,
    val pendingCommand: String? = null,
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val catalogStore = CatalogStore(app)
    private val installStore = InstallStore(app)
    private val client = PayloadClient()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val bundled = runCatching { catalogStore.bundled() }.getOrDefault(emptyList())
        val installed = runCatching { installStore.read() }.getOrDefault(emptyMap())
        _state.update { it.copy(components = bundled, installed = installed) }
        viewModelScope.launch {
            val seeded = withContext(Dispatchers.IO) {
                runCatching { seedSmallPayloads(getApplication(), bundled, installStore.read()) }.getOrDefault(installed)
            }
            installStore.write(seeded)
            val ready = seeded["rootfs-alpine"]?.extracted == true
            _state.update { it.copy(installed = seeded, notice = if (ready) "Alpine hazırdır" else null) }
            refresh()
        }
    }

    fun select(tab: Tab) {
        _state.update { it.copy(tab = tab, notice = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching { catalogStore.refresh(client.http) }
            }
            fresh.onSuccess { list ->
                _state.update { it.copy(components = list) }
            }
        }
    }

    fun download(id: String) {
        val component = _state.value.components.firstOrNull { it.id == id } ?: return
        if (_state.value.busyId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyId = id, progress = 0f, notice = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = payloadFile(getApplication(), component)
                    client.download(component.url, dest) { fraction ->
                        _state.update { state -> state.copy(progress = fraction) }
                    }
                    val sum = sha256(dest)
                    if (sum != component.sha256) {
                        dest.delete()
                        error("SHA-256 uyğun gəlmir. Fayl silindi.")
                    }
                    sum to dest.length()
                }
            }
            result.fold(
                onSuccess = { (sum, bytes) ->
                    val record = InstallRecord(
                        id = component.id,
                        filename = component.filename,
                        sha256 = sum,
                        bytes = bytes,
                        savedAt = System.currentTimeMillis(),
                        extracted = false,
                    )
                    val next = _state.value.installed + (component.id to record)
                    installStore.write(next)
                    _state.update {
                        it.copy(
                            installed = next,
                            busyId = null,
                            progress = 0f,
                            notice = "${component.name} yoxlandı və saxlandı.",
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busyId = null,
                            progress = 0f,
                            notice = error.message ?: "Yükləmə alınmadı",
                        )
                    }
                },
            )
        }
    }

    fun remove(id: String) {
        val component = _state.value.components.firstOrNull { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            payloadFile(getApplication(), component).delete()
            File(getApplication<Application>().filesDir, "unpacked/$id").deleteRecursively()
            val next = _state.value.installed - id
            installStore.write(next)
            _state.update { it.copy(installed = next, notice = "${component.name} silindi.") }
        }
    }

    fun extract(id: String) {
        val component = _state.value.components.firstOrNull { it.id == id } ?: return
        val record = _state.value.installed[id] ?: return
        if (component.kind != "archive") {
            _state.update { it.copy(notice = "Bu paket arxiv deyil.") }
            return
        }
        if (_state.value.busyId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyId = id, notice = null, progress = 0f) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val archive = payloadFile(getApplication(), component)
                    val sum = sha256(archive)
                    if (sum != record.sha256) error("Saxlanmış faylın checksum-u dəyişib")
                    val dest = File(getApplication<Application>().filesDir, "unpacked/$id")
                    extractArchive(archive, dest)
                }
            }
            result.fold(
                onSuccess = {
                    val next = _state.value.installed + (id to record.copy(extracted = true))
                    installStore.write(next)
                    _state.update {
                        it.copy(installed = next, busyId = null, notice = "${component.name} açıldı.")
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(busyId = null, notice = error.message ?: "Arxiv açılmadı")
                    }
                },
            )
        }
    }

    fun openFile(id: String) {
        val component = _state.value.components.firstOrNull { it.id == id } ?: return
        if (component.kind != "file") {
            _state.update { it.copy(notice = "Bu fayl mətn kimi açılmır.") }
            return
        }
        val file = payloadFile(getApplication(), component)
        if (!file.exists()) return
        _state.update {
            it.copy(tab = Tab.Files, editorId = id, editorText = file.readText())
        }
    }

    fun clearLog() {
        _state.update { it.copy(sessionLog = MOTD) }
    }

    fun onPermission(granted: Boolean) {
        val line = _state.value.pendingCommand
        _state.update { it.copy(permissionAsk = null, pendingCommand = null, notice = if (granted) null else "icazə verilmədi") }
        if (granted && !line.isNullOrBlank()) run(line)
    }

    fun run(command: String) {
        val line = command.trim()
        if (line.isEmpty() || _state.value.busyId != null) return
        val app = getApplication<Application>()
        val perm = PhoneBridge.permission(line)
        if (perm != null && ContextCompat.checkSelfPermission(app, perm) != PackageManager.PERMISSION_GRANTED) {
            _state.update {
                it.copy(permissionAsk = perm, pendingCommand = line, permissionNonce = it.permissionNonce + 1, notice = "icazə gözlənilir")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyId = "session", notice = null) }
            val text = withContext(Dispatchers.IO) {
                PhoneBridge.run(app, line) ?: runCatching {
                    if (rootfsDir() != null) execInProot(line) else execLocal(line)
                }.getOrElse { error -> error.message ?: "sessiya açılmadı" }
            }
            val previous = _state.value.sessionLog
            val joined = listOf(previous, "$ $line", text).filter { it.isNotBlank() }.joinToString("\n")
            val capped = if (joined.length > 8000) joined.takeLast(8000) else joined
            _state.update { it.copy(busyId = null, sessionLog = capped, tab = Tab.Session) }
        }
    }

    private var running: Process? = null

    fun stop() {
        running?.destroyForcibly()
        _state.update { it.copy(notice = "dayandırıldı") }
    }

    private fun rootfsDir(): File? {
        val app = getApplication<Application>()
        return listOf("rootfs-alpine", "rootfs-debian", "rootfs-ubuntu").firstNotNullOfOrNull { id ->
            val record = _state.value.installed[id]
            val dir = File(app.filesDir, "unpacked/$id")
            if (record?.extracted == true && dir.isDirectory) dir else null
        }
    }

    private fun execLocal(command: String): String {
        val home = File(getApplication<Application>().filesDir, "home").apply { mkdirs() }
        val cwd = File(home, ".cwd")
        val script = File(home, "cmd.sh")
        val start = cwd.takeIf { it.isFile }?.readText()?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifBlank { home.absolutePath }
        script.writeText(
            "cd ${posixQuote(start)} 2>/dev/null || cd ${posixQuote(home.absolutePath)}\n" +
                command + "\ncode=\$?\npwd > ${posixQuote(cwd.absolutePath)}\nexit \$code\n",
        )
        val process = ProcessBuilder("/system/bin/sh", script.absolutePath)
            .redirectErrorStream(true)
            .start()
        running = process
        return try {
            val output = process.inputStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            if (code == 0) output else listOf(output, "çıxdı $code").filter { it.isNotBlank() }.joinToString("\n")
        } catch (error: Exception) {
            error.message ?: "dayandırıldı"
        } finally {
            running = null
        }
    }

    private fun posixQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun execInProot(command: String): String {
        val app = getApplication<Application>()
        val proot = prootBinary(app)
        val root = rootfsDir() ?: error("Əvvəl mühit arxivini yüklə və aç")
        val home = File(root, "root").apply { mkdirs() }
        File(home, ".nibras_cmd.sh").writeText(sessionScript(command))
        val tmp = File(app.cacheDir, "proot-tmp").apply { mkdirs() }
        val storage = File(app.getExternalFilesDir(null), "storage")
        val args = mutableListOf(
            proot.absolutePath,
            "--link2symlink",
            "-0",
            "-r",
            root.absolutePath,
            "-b",
            "/dev/",
            "-b",
            "/proc/",
            "-b",
            "/sys/",
            "-w",
            "/root",
        )
        if (storage.isDirectory) {
            args += listOf("-b", "${storage.absolutePath}:/root/storage")
        }
        args += listOf("/bin/sh", "/root/.nibras_cmd.sh")
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .apply { environment()["PROOT_TMP_DIR"] = tmp.absolutePath }
            .start()
        running = process
        return try {
            val output = process.inputStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            if (code == 0) output.ifBlank { "çıxdı 0" } else listOf(output, "çıxdı $code").filter { it.isNotBlank() }.joinToString("\n")
        } catch (error: Exception) {
            error.message ?: "dayandırıldı"
        } finally {
            running = null
        }
    }

    private fun sessionScript(command: String) = """
        set +e
        if [ -f /root/.nibras_cwd ]; then cd "$(cat /root/.nibras_cwd)" || cd /root; else cd /root; fi
        if [ -f /root/.nibras_env ]; then . /root/.nibras_env; fi
        $command
        code=${'$'}?
        pwd > /root/.nibras_cwd 2>/dev/null
        export -p > /root/.nibras_env 2>/dev/null
        exit ${'$'}code
    """.trimIndent() + "\n"

    private fun prootBinary(app: Application): File {
        val bundled = File(app.applicationInfo.nativeLibraryDir, "libproot.so")
        if (bundled.exists()) return bundled
        val component = _state.value.components.firstOrNull { it.id == "tool-proot" }
            ?: error("PRoot siyahıda yoxdur")
        val downloaded = payloadFile(app, component)
        if (!downloaded.exists()) error("PRoot tapılmadı")
        downloaded.setExecutable(true, false)
        return downloaded
    }
}
