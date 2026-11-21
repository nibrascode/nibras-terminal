package com.nibras.terminal

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile: open a new terminal session. */
class NewSessionTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = getString(R.string.shortcut_new)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val i = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_SESSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, 61, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
