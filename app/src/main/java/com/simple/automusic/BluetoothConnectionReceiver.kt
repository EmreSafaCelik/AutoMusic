package com.simple.automusic

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.sdk.android.auth.LoginActivity.REQUEST_CODE

class BluetoothConnectionReceiver : BroadcastReceiver() {
    val TAG = "BluetoothConnectionReceiver"
    val CLIENT_ID = "1b5bee9d082c45169e7237ee529fb691"
    val REDIRECT_URI = "com.simple.automusic://auth"
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, action.toString())
        val sp = context.getSharedPreferences("chosen_devices", AppCompatActivity.MODE_PRIVATE)

        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            // Bluetooth device connected
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            if (device != null) {
                if (sp.getStringSet("mac-list", mutableSetOf())!!.contains(device.address)) {
                    val mediaPlayer = MediaPlayer.create(context, R.raw.jarvis_merhaba)
                    mediaPlayer.start()
                    SpotifyAppRemote.connect(context,
                        ConnectionParams.Builder(CLIENT_ID)
                            .setRedirectUri(REDIRECT_URI)
                            .showAuthView(true)
                            .build(),
                        object : Connector.ConnectionListener {
                            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                                mSpotifyAppRemote = spotifyAppRemote
                                // Now you can start interacting with App Remote
                                mSpotifyAppRemote!!.playerApi.play("spotify:user:anonymised:collection")
                            }

                            // Something went wrong when attempting to connect! Handle errors here
                            override fun onFailure(throwable: Throwable) {
                                Log.e(TAG, throwable.message, throwable)
                            }
                        }
                    )
                }
                Log.v(TAG, device.name)
            }
        }
    }
}

