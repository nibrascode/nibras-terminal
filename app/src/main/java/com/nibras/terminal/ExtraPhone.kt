package com.nibras.terminal

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Phone commands added on top of the Termux:API set (audio, wallpaper, telephony, Wi-Fi scan). */
object ExtraPhone {
    fun audioInfo(c: Context): String {
        val am = c.getSystemService(AudioManager::class.java)
        val o = JSONObject()
        o.put("PROPERTY_OUTPUT_SAMPLE_RATE", am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))
        o.put("PROPERTY_OUTPUT_FRAMES_PER_BUFFER", am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER))
        o.put("BLUETOOTH_A2DP_IS_ON", am.isBluetoothA2dpOn)
        o.put("WIREDHEADSET_IS_CONNECTED", am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        })
        o.put("RINGER_MODE", when (am.ringerMode) { AudioManager.RINGER_MODE_SILENT -> "silent"; AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; else -> "normal" })
        o.put("MUSIC_ACTIVE", am.isMusicActive)
        return o.toString(2)
    }

    /** args: "-f <host file>" sets home+lock wallpaper; "-l" lock only. */
    fun wallpaper(c: Context, file: File?, lockOnly: Boolean): String {
        if (file == null || !file.isFile) return "usage: nibras-wallpaper [-l] <image file>"
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return "not an image: ${file.name}"
        val wm = WallpaperManager.getInstance(c)
        val flag = if (lockOnly) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        wm.setBitmap(bmp, null, true, flag)
        return "wallpaper set"
    }

    fun telephony(c: Context): String {
        val tm = c.getSystemService(TelephonyManager::class.java)
        val o = JSONObject()
        o.put("phone_type", when (tm.phoneType) { TelephonyManager.PHONE_TYPE_GSM -> "gsm"; TelephonyManager.PHONE_TYPE_CDMA -> "cdma"; TelephonyManager.PHONE_TYPE_SIP -> "sip"; else -> "none" })
        o.put("network_operator", tm.networkOperator)
        o.put("network_operator_name", tm.networkOperatorName)
        o.put("network_country_iso", tm.networkCountryIso)
        o.put("network_roaming", tm.isNetworkRoaming)
        o.put("sim_state", when (tm.simState) { TelephonyManager.SIM_STATE_READY -> "ready"; TelephonyManager.SIM_STATE_ABSENT -> "absent"; TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"; else -> "unknown" })
        o.put("sim_operator", tm.simOperator)
        o.put("sim_operator_name", tm.simOperatorName)
        o.put("sim_country_iso", tm.simCountryIso)
        o.put("data_enabled", runCatching { tm.isDataEnabled }.getOrNull())
        o.put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        return o.toString(2)
    }

    @Suppress("DEPRECATION")
    fun wifiScan(c: Context): String {
        val wm = c.applicationContext.getSystemService(WifiManager::class.java)
        if (!wm.isWifiEnabled) return "Wi-Fi is off"
        val a = JSONArray()
        wm.scanResults.sortedByDescending { it.level }.forEach { r ->
            a.put(JSONObject().put("bssid", r.BSSID).put("ssid", r.SSID).put("frequency_mhz", r.frequency)
                .put("rssi", r.level).put("capabilities", r.capabilities))
        }
        return a.toString(2)
    }
}
