package com.nibras.terminal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibras.terminal.core.DeviceAbi
import com.nibras.terminal.core.Lang
import com.nibras.terminal.core.Prefs
import com.nibras.terminal.core.Rootfs
import com.nibras.terminal.ui.Themes

private val Bg = Color(0xFF0B0F14)
private val Ink = Color(0xFFEDEDED)
private val Gold = Color(0xFFD6B36A)
private val Muted = Color(0xFF9AA0A6)

class SettingsActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen(tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++ // refresh permission states
    }

    @SuppressLint("BatteryLife")
    @Composable
    private fun SettingsScreen(@Suppress("UNUSED_PARAMETER") tick: Int) {
        val prefs = remember { Prefs(this) }
        var refresh by remember { mutableIntStateOf(0) }
        fun bump() { refresh++ }
        Column(
            Modifier.fillMaxSize().background(Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            refresh.let { }
            Text(stringResource(R.string.settings_title), color = Ink, fontSize = 24.sp)
            Spacer(Modifier.padding(6.dp))

            Section(stringResource(R.string.set_language))
            val langs = listOf("" to stringResource(R.string.lang_system), "az" to "Azərbaycan", "tr" to "Türkçe", "en" to "English", "ru" to "Русский")
            Choices(langs, prefs.language) {
                prefs.language = it
                recreate()
            }

            Section(stringResource(R.string.set_theme))
            Choices(Themes.names.toList(), prefs.theme) { prefs.theme = it; bump() }

            Section(stringResource(R.string.set_font))
            var font by remember { mutableStateOf(if (prefs.fontSize > 0) prefs.fontSize.toFloat() else 28f) }
            Slider(
                value = font, onValueChange = { font = it }, valueRange = 14f..80f,
                onValueChangeFinished = { prefs.fontSize = font.toInt() },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
            )

            Section(stringResource(R.string.set_cursor))
            Choices(
                listOf("0" to stringResource(R.string.cursor_block), "1" to stringResource(R.string.cursor_underline), "2" to stringResource(R.string.cursor_bar)),
                prefs.cursorStyle.toString(),
            ) { prefs.cursorStyle = it.toInt(); bump() }

            Toggle(stringResource(R.string.set_blink), prefs.cursorBlink) { prefs.cursorBlink = it; bump() }
            Toggle(stringResource(R.string.set_extra_keys), prefs.extraKeys) { prefs.extraKeys = it; bump() }
            Toggle(stringResource(R.string.set_keep_screen), prefs.keepScreenOn) { prefs.keepScreenOn = it; bump() }
            Toggle(stringResource(R.string.set_bell), prefs.bellVibrate) { prefs.bellVibrate = it; bump() }
            Toggle(stringResource(R.string.set_back_esc), prefs.backIsEscape) { prefs.backIsEscape = it; bump() }
            Toggle(stringResource(R.string.set_fullscreen), prefs.fullscreen) { prefs.fullscreen = it; bump() }
            Toggle(stringResource(R.string.set_volume_keys), prefs.volumeKeys) { prefs.volumeKeys = it; bump() }

            Section(stringResource(R.string.set_default_env))
            val envs = Rootfs.ready(this@SettingsActivity).map { it.id to it.name } + (Rootfs.ANDROID to stringResource(R.string.android_shell))
            Choices(envs, prefs.defaultEnv) { prefs.defaultEnv = it; bump() }

            Section(stringResource(R.string.set_storage))
            if (Rootfs.hasSharedStorage(this@SettingsActivity)) {
                Text("✓ " + stringResource(R.string.granted), color = Gold)
            } else {
                TextButton(onClick = { requestStorage() }) { Text(stringResource(R.string.grant), color = Gold) }
            }

            Section(stringResource(R.string.set_battery))
            val power = getSystemService(PowerManager::class.java)
            if (power.isIgnoringBatteryOptimizations(packageName)) {
                Text("✓ " + stringResource(R.string.granted), color = Gold)
            } else {
                TextButton(onClick = {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                    }
                }) { Text(stringResource(R.string.grant), color = Gold) }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                Section(stringResource(R.string.set_notifications))
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    Text("✓ " + stringResource(R.string.granted), color = Gold)
                } else {
                    TextButton(onClick = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }) {
                        Text(stringResource(R.string.grant), color = Gold)
                    }
                }
            }

            Spacer(Modifier.padding(8.dp))
            Text(stringResource(R.string.set_custom_hint), color = Muted, fontSize = 13.sp)
            Section(stringResource(R.string.about))
            val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
            Text(stringResource(R.string.about_text, version, DeviceAbi.label, DeviceAbi.deviceAbis), color = Muted, fontSize = 13.sp)
            Text("github.com/nibrascode/nibras-terminal", color = Muted, fontSize = 13.sp)
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }.onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 2)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Gold, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember(value) { mutableStateOf(value) }
    Row(
        Modifier.fillMaxWidth().clickable { on = !on; onChange(on) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = { on = it; onChange(it) })
    }
}

@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    var current by remember(selected) { mutableStateOf(selected) }
    Column {
        options.forEach { (key, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { current = key; onPick(key) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (current == key) "●" else "○", color = if (current == key) Gold else Muted, modifier = Modifier.padding(end = 10.dp))
                Text(label, color = Ink, fontSize = 15.sp)
            }
        }
    }
}
