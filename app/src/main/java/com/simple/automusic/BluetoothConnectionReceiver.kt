package com.simple.automusic

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

class BluetoothConnectionReceiver : BroadcastReceiver() {
    val TAG = "BluetoothConnectionReceiver"

    // Constants for SpotifyAppRemote
    val CLIENT_ID = "1b5bee9d082c45169e7237ee529fb691"
    val REDIRECT_URI = "com.simple.automusic://auth"
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    override fun onReceive(context: Context, intent: Intent) {
        // action is ACL_CONNECTED or ACL_DISCONNECTED
        val action = intent.action
        Log.d(TAG, action.toString())

        val sp = context.getSharedPreferences("preferences", AppCompatActivity.MODE_PRIVATE)

        // Bluetooth device connected
        if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            if (device != null) {
                if (sp.getStringSet("mac-list", mutableSetOf())!!.contains(device.address)) {
                    // Wait specified amount by user
                    Thread.sleep(sp.getInt("delay_time", 3000).toLong())

                    // Play set audio
                    val mediaPlayer = MediaPlayer.create(context, R.raw.jarvis_merhaba)
                    mediaPlayer.start()

                    // Connect to spotify and play as chosen by user
                    SpotifyAppRemote.connect(context,
                        ConnectionParams.Builder(CLIENT_ID)
                            .setRedirectUri(REDIRECT_URI)
                            .showAuthView(true)
                            .build(),
                        object : Connector.ConnectionListener {
                            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                                mSpotifyAppRemote = spotifyAppRemote
                                val mPlayerApi = mSpotifyAppRemote?.playerApi

                                if (sp.getBoolean("shuffle", false)) {
                                    mPlayerApi?.setShuffle(true)
                                } else {
                                    mPlayerApi?.setShuffle(false)
                                }

                                if (sp.getBoolean("start_player_state", false)) {
                                    mPlayerApi?.playerState?.setResultCallback { playerState ->
                                        if (playerState.isPaused)
                                            mPlayerApi.resume()
                                    }
                                } else if (sp.getBoolean("start_favorites", false)) {
                                    mPlayerApi?.play("spotify:user:anonymised:collection")
                                } else {
                                    mPlayerApi?.play(sp.getString("start_link", "spotify:user:anonymised:collection"))
                                }
                            }

                            // Something went wrong when attempting to connect! Handle errors here
                            override fun onFailure(throwable: Throwable) {
                                Log.e(TAG, throwable.message, throwable)
                            }
                        }
                    )
                }
                SpotifyAppRemote.disconnect(mSpotifyAppRemote)
            }
        }
    }
}

