package com.nibras.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nibras.terminal.data.CatalogStore
import com.nibras.terminal.data.InstallStore
import com.nibras.terminal.data.PayloadClient
import com.nibras.terminal.data.extractArchive
import com.nibras.terminal.data.payloadFile
import com.nibras.terminal.data.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val components: List<Component> = emptyList(),
    val installed: Map<String, InstallRecord> = emptyMap(),
    val tab: Tab = Tab.Home,
    val busyId: String? = null,
    val progress: Float = 0f,
    val notice: String? = null,
    val editorId: String? = null,
    val editorText: String = "",
    val sessionLog: String = "",
)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val catalogStore = CatalogStore(app)
    private val installStore = InstallStore(app)
    private val client = PayloadClient()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val bundled = runCatching { catalogStore.bundled() }.getOrDefault(emptyList())
        _state.update { it.copy(components = bundled, installed = installStore.read()) }
        refresh()
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

    fun run(command: String) {
        val line = command.trim()
        if (line.isEmpty() || _state.value.busyId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyId = "session", notice = null) }
            val text = withContext(Dispatchers.IO) {
                runCatching { execInProot(line) }.getOrElse { error -> error.message ?: "sessiya açılmadı" }
            }
            _state.update { it.copy(busyId = null, sessionLog = text, tab = Tab.Session) }
        }
    }

    private fun execInProot(command: String): String {
        val app = getApplication<Application>()
        val proot = prootBinary(app)
        val root = listOf("rootfs-alpine", "rootfs-debian", "rootfs-ubuntu").firstNotNullOfOrNull { id ->
            val record = _state.value.installed[id]
            val dir = File(app.filesDir, "unpacked/$id")
            if (record?.extracted == true && dir.isDirectory) dir else null
        } ?: error("Əvvəl mühit arxivini yüklə və aç")
        val tmp = File(app.cacheDir, "proot-tmp").apply { mkdirs() }
        val process = ProcessBuilder(
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
            "/bin/sh",
            "-c",
            command,
        )
            .redirectErrorStream(true)
            .apply { environment()["PROOT_TMP_DIR"] = tmp.absolutePath }
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val code = process.waitFor()
        if (code == 0) return output.ifBlank { "çıxdı 0" }
        return listOf(output, "çıxdı $code").filter { it.isNotBlank() }.joinToString("\n")
    }

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
