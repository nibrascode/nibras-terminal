package com.nibras.terminal

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nibras.terminal.core.DeviceAbi
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.data.CatalogStore
import com.nibras.terminal.data.PayloadClient
import com.nibras.terminal.data.deleteTree
import com.nibras.terminal.data.extractArchive
import com.nibras.terminal.data.payloadFile
import com.nibras.terminal.data.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Bg = Color(0xFF0B0F14)
private val Ink = Color(0xFFEDEDED)
private val Gold = Color(0xFFD6B36A)
private val Muted = Color(0xFF9AA0A6)
private val Ok = Color(0xFF8FBF8A)

data class PkgState(
    val components: List<Component> = emptyList(),
    val ready: Set<String> = emptySet(),
    val busy: String? = null,
    val progress: Float = 0f,
    val notice: String? = null,
    val defaultEnv: String = "rootfs-alpine",
)

/** Downloads, verifies (SHA-256) and installs the large payloads for this device's ABI. */
class PackagesViewModel(app: Application) : AndroidViewModel(app) {
    private val catalog = CatalogStore(app)
    private val client = PayloadClient()
    val state = MutableStateFlow(PkgState())

    init {
        val bundled = runCatching { catalog.bundled() }.getOrDefault(emptyList())
        publish(bundled)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { catalog.refresh(client.http) } }.onSuccess { publish(it) }
        }
    }

    private fun publish(all: List<Component>) {
        val app = getApplication<Application>()
        state.update {
            it.copy(
                components = all.filter { c -> c.group == "environment" || c.group == "runtime" },
                ready = readyIds(app),
                defaultEnv = Prefs(app).defaultEnv,
            )
        }
    }

    private fun readyIds(app: Context): Set<String> =
        Rootfs.ready(app).map { it.id }.toSet() +
            listOf("runtime-python" to "bin/python3", "runtime-node" to "bin/node")
                .filter { (id, marker) -> Rootfs.runtimeHome(app, id, marker) != null }.map { it.first }

    fun install(component: Component) {
        if (state.value.busy != null) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            state.update { it.copy(busy = component.id, progress = 0f, notice = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = payloadFile(app, component)
                    val have = dest.isFile && dest.length() == component.sizeBytes && sha256(dest) == component.sha256
                    if (!have) {
                        client.download(component.url, dest) { f -> state.update { s -> s.copy(progress = f) } }
                        if (sha256(dest) != component.sha256) {
                            dest.delete()
                            error("SHA-256")
                        }
                    }
                    state.update { it.copy(progress = -1f) }
                    if (component.group == "environment") {
                        Rootfs.installArchive(app, component.id, dest, component) { }
                    } else {
                        extractArchive(dest, File(app.filesDir, "unpacked/${component.id}"))
                    }
                    dest.delete() // keep storage small; the archive can be downloaded again
                }
            }
            state.update {
                it.copy(
                    busy = null,
                    progress = 0f,
                    ready = readyIds(app),
                    notice = result.fold(
                        { app.getString(R.string.pkg_done, component.name) },
                        { e -> app.getString(R.string.pkg_failed, e.message ?: e.javaClass.simpleName) },
                    ),
                )
            }
        }
    }

    fun remove(component: Component) {
        val app = getApplication<Application>()
        if (component.id == "rootfs-alpine") return // the bundled environment stays
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                payloadFile(app, component).delete()
                deleteTree(File(app.filesDir, "unpacked/${component.id}"))
            }
            if (Prefs(app).defaultEnv == component.id) Prefs(app).defaultEnv = "rootfs-alpine"
            publish(state.value.components)
        }
    }

    fun makeDefault(id: String) {
        Prefs(getApplication()).defaultEnv = id
        state.update { it.copy(defaultEnv = id) }
    }
}

class PackagesActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val model: PackagesViewModel = viewModel()
            val state by model.state.collectAsState()
            PackagesScreen(state, model) { id ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_NEW_SESSION)
                        .putExtra(MainActivity.EXTRA_ENV, id)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
            }
        }
    }
}

@Composable
private fun PackagesScreen(state: PkgState, model: PackagesViewModel, open: (String) -> Unit) {
    val abi = DeviceAbi.current
    val ids = state.components.map { it.id }.distinct()
    LazyColumn(Modifier.fillMaxSize().background(Bg).safeDrawingPadding().padding(horizontal = 16.dp)) {
        item {
            Text(stringResource(R.string.pkg_title), color = Ink, fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.pkg_subtitle, DeviceAbi.label), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
            state.notice?.let { Text(it, color = Gold, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
        }
        for (group in listOf("environment", "runtime")) {
            item {
                Text(
                    stringResource(if (group == "environment") R.string.pkg_group_env else R.string.pkg_group_runtime),
                    color = Gold, fontSize = 16.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                )
                if (group == "runtime") Text(stringResource(R.string.pkg_runtime_hint), color = Muted, fontSize = 12.sp)
            }
            val groupIds = ids.filter { id -> state.components.first { it.id == id }.group == group }
            items(groupIds, key = { it }) { id ->
                val variant = state.components.firstOrNull { it.id == id && DeviceAbi.matches(it.abi) }
                val any = state.components.first { it.id == id }
                Item(id, variant, any, abi, state, model, open)
            }
        }
    }
}

@Composable
private fun Item(id: String, c: Component?, any: Component, abi: String, state: PkgState, model: PackagesViewModel, open: (String) -> Unit) {
    val ready = id in state.ready
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text((c ?: any).name + "  " + (c?.version ?: ""), color = Ink, fontSize = 16.sp)
            Text(
                when {
                    c == null -> ""
                    id == "rootfs-alpine" -> stringResource(R.string.pkg_bundled)
                    else -> formatBytes(c.sizeBytes)
                },
                color = Gold, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            )
        }
        if (c == null) {
            Text(stringResource(R.string.pkg_not_for_abi, abi), color = Muted, fontSize = 13.sp)
            return@Column
        }
        Text(c.summary, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(c.sha256.take(16) + "…  " + c.license, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        if (state.busy == id) {
            if (state.progress >= 0f) {
                LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Gold)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Gold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!ready) {
                TextButton(onClick = { model.install(c) }, enabled = state.busy == null) {
                    Text(if (state.busy == id) stringResource(R.string.pkg_working) else stringResource(R.string.pkg_install), color = Gold)
                }
            } else {
                Text(stringResource(R.string.pkg_installed), color = Ok, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                if (c.group == "environment") {
                    TextButton(onClick = { open(id) }) { Text(stringResource(R.string.pkg_open), color = Ink) }
                    if (state.defaultEnv == id) {
                        Text("★ " + stringResource(R.string.pkg_default), color = Gold, fontSize = 12.sp)
                    } else {
                        TextButton(onClick = { model.makeDefault(id) }) { Text(stringResource(R.string.pkg_set_default), color = Ink) }
                    }
                }
                if (id != "rootfs-alpine") {
                    TextButton(onClick = { model.remove(c) }, enabled = state.busy == null) { Text(stringResource(R.string.pkg_remove), color = Muted) }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    return String.format("%.1f MB", kb / 1024.0)
}
