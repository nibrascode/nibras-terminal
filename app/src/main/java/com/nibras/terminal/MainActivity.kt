package com.nibras.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val Navy = Color(0xFF07101C)
private val Ink = Color(0xFFF3ECDF)
private val Gold = Color(0xFFD6B36A)
private val Muted = Color(0xFF9A917F)
private val Line = Color(0xFF243044)
private val Ok = Color(0xFF8FBF8A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: TerminalViewModel = viewModel()
            val state by model.state.collectAsState()
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                model.onPermission(granted)
            }
            LaunchedEffect(state.permissionNonce) {
                state.permissionAsk?.let { ask.launch(it) }
            }
            NibrasApp(state, model)
        }
    }
}

@Composable
private fun NibrasApp(state: UiState, model: TerminalViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding(),
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            when (state.tab) {
                Tab.Home -> HomeTab(state) { model.select(Tab.Downloads) }
                Tab.Downloads -> DownloadsTab(state, model)
                Tab.Session -> SessionTab(state, model)
                Tab.Files -> FilesTab(state, model)
            }
        }
        state.notice?.let { notice ->
            Text(
                notice,
                color = Gold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B1524))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NavItem("Ev", state.tab == Tab.Home) { model.select(Tab.Home) }
            NavItem("Yüklə", state.tab == Tab.Downloads) { model.select(Tab.Downloads) }
            NavItem("Sessiya", state.tab == Tab.Session) { model.select(Tab.Session) }
            NavItem("Fayl", state.tab == Tab.Files) { model.select(Tab.Files) }
        }
    }
}

@Composable
private fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (active) Gold else Muted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HomeTab(state: UiState, onDownloads: () -> Unit) {
    val chosen = state.installed.size
    val bytes = state.installed.values.sumOf { it.bytes }
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Text("NIBRAS", color = Gold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        Text("TERMINAL", color = Ink, fontFamily = FontFamily.Serif, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("0.1  ·  arm64  ·  boş qabıq", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        Text(
            "Linux, Python, Node və şablonlar tətbiqin içində deyil. Hər biri GitHub-da ayrı dayanır. Nəyi seçsən, yalnız o yüklənir və SHA-256 ilə yoxlanır. Kiçik mühərrik tətbiqin içindədir, çünki telefon yüklənmiş əmri başqa qovluqdan işlətməyə icazə vermir.",
            color = Ink,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text("$chosen seçim  ·  ${formatBytes(bytes)}", color = Gold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDownloads) {
            Text("Yükləmələrə keç", color = Navy, modifier = Modifier.background(Gold, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun DownloadsTab(state: UiState, model: TerminalViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
        item {
            Text("Yükləmələr", color = Ink, fontFamily = FontFamily.Serif, fontSize = 28.sp)
            Text(
                "Heç biri öz-özünə gəlmir.",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp, top = 4.dp),
            )
        }
        items(state.components, key = { it.id }) { component ->
            val installed = state.installed[component.id]
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(component.name, color = Ink, fontSize = 16.sp)
                    Text(formatBytes(component.sizeBytes), color = Gold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text(component.summary, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    component.sha256.take(16) + "…  " + checksumLabel(component.checksumOrigin),
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.busyId == component.id) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = Gold,
                        trackColor = Line,
                    )
                }
                Row {
                    if (installed == null) {
                        TextButton(onClick = { model.download(component.id) }, enabled = state.busyId == null) {
                            Text("Yüklə", color = Gold)
                        }
                    } else {
                        Text("yoxlandı", color = Ok, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                        if (component.kind == "archive" && !installed.extracted) {
                            TextButton(onClick = { model.extract(component.id) }, enabled = state.busyId == null) {
                                Text("Aç", color = Ink)
                            }
                        }
                        if (component.kind == "file") {
                            TextButton(onClick = { model.openFile(component.id) }) {
                                Text("Bax", color = Ink)
                            }
                        }
                        TextButton(onClick = { model.remove(component.id) }, enabled = state.busyId == null) {
                            Text("Sil", color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTab(state: UiState, model: TerminalViewModel) {
    val env = state.installed.keys.filter { id ->
        id.startsWith("rootfs-") && state.installed[id]?.extracted == true
    }
    var line by remember { mutableStateOf("echo salam") }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text("Sessiya", color = Ink, fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (env.isEmpty()) "Linux mühiti olmadan da telefon əmrləri işləyir: nibras-battery-status, nibras-info, nibras-torch. Linux üçün arxivi Yükləmələrdən aç."
            else "Mühit: ${env.joinToString()}. Linux əmri prootda, telefon əmri isə birbaşa cihazda işləyir.",
            color = Ink,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("nibras-info", "nibras-battery-status", "uname -a").forEach { sample ->
                TextButton(onClick = { line = sample }, enabled = state.busyId == null) {
                    Text(sample, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
        OutlinedTextField(
            value = line,
            onValueChange = { line = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.busyId == null,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Line,
                cursorColor = Gold,
                disabledBorderColor = Line,
                disabledTextColor = Muted,
            ),
        )
        TextButton(
            onClick = { model.run(line) },
            enabled = state.busyId == null && line.isNotBlank() && (env.isNotEmpty() || line.trim().startsWith("nibras-") || line.trim().startsWith("termux-")),
        ) {
            Text(if (state.busyId == "session") "İşləyir…" else "İşlət", color = Gold)
        }
        if (state.sessionLog.isNotBlank()) {
            TextButton(onClick = { model.clearLog() }, enabled = state.busyId == null) {
                Text("Təmizlə", color = Muted)
            }
        }
        if (state.sessionLog.isNotBlank()) {
            Text(
                state.sessionLog,
                color = Ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun FilesTab(state: UiState, model: TerminalViewModel) {
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text("Fayllar", color = Ink, fontFamily = FontFamily.Serif, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        if (state.editorId != null) {
            Text(state.editorId, color = Gold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(state.editorText, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text("Mətn paketi yükləndikdən sonra burada açılır.", color = Muted, fontSize = 14.sp)
            state.installed.keys.forEach { id ->
                val component = state.components.firstOrNull { it.id == id } ?: return@forEach
                if (component.kind == "file") {
                    TextButton(onClick = { model.openFile(id) }) {
                        Text(component.name, color = Gold)
                    }
                }
            }
        }
    }
}

private fun checksumLabel(origin: String) = when (origin) {
    "official" -> "rəsmi checksum"
    "repo" -> "repo checksum"
    "vendor" -> "vendor checksum"
    else -> "güzgü checksum"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
