package com.nibras.terminal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nibras.terminal.core.Lang
import com.nibras.terminal.ui.HostPaths
import java.io.File

/** Built-in noVNC viewer for "nibras-desktop" (XFCE over VNC, UserLAnd / AndroNix style). */
class DesktopActivity : Activity() {
    companion object {
        fun open(context: Context, env: String?) = context.startActivity(
            Intent(context, DesktopActivity::class.java).putExtra("env", env).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private lateinit var web: WebView
    private var tries = 0

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.host != "127.0.0.1"
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && tries++ < 15) view.postDelayed({ load() }, 1000)
                    else if (request.isForMainFrame) Toast.makeText(this@DesktopActivity, R.string.desktop_not_running, Toast.LENGTH_LONG).show()
                }
            }
        }
        setContentView(web)
        load()
    }

    private fun load() {
        val env = HostPaths.envId(this, intent.getStringExtra("env"))
        val pass = HostPaths.toHost(this, env, "/tmp/.nibras-vnc-pass")?.takeIf(File::isFile)?.readText()?.trim().orEmpty()
        val url = "http://127.0.0.1:6080/vnc.html?autoconnect=1&resize=remote&reconnect=1&show_dot=1" +
            if (pass.isNotEmpty()) "&password=" + Uri.encode(pass) else ""
        web.loadUrl(url)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
