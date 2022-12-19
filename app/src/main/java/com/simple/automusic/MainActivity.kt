package com.simple.automusic

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class MainActivity : AppCompatActivity() {

    // Set variables for SpotifySDK
    val REQUEST_CODE = 1337
    val CLIENT_ID = "1b5bee9d082c45169e7237ee529fb691"
    val REDIRECT_URI = "com.simple.automusic://auth"
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    // Set Log tag and BroadcastReceiver
    lateinit var receiver: BluetoothConnectionReceiver
    var TAG = "MainActivityBluetooth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sp = getSharedPreferences("chosen_device", MODE_PRIVATE)

        // Permission requests
        requestPermissions()

        // Set Spotify to debug mode and authorize
        SpotifyAppRemote.setDebugMode(true)
        Log.v(TAG, "Debug mode on for SpotifyAppRemote")

        // Play song button
        val btnPlaySong = findViewById<Button>(R.id.buttonPlaySong)
        btnPlaySong.setOnClickListener {
            if (sp.getString("first_param", "default_param") == "default_param") {
                sp.edit().putString("first_param", "second_param").apply()
            }
        }

        // Authorize button
        val btnOpenSpotify = findViewById<Button>(R.id.buttonOpenSpotify)
        btnOpenSpotify.setOnClickListener {
            Log.d(TAG, "clicked on authorize")
            val builder = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setScopes(Array(1) {"user-read-private"})
            val request = builder.build()
            AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
//            unregisterReceiver(receiver)
        }

        // Setting broadcast receiver
//        receiver = BluetoothConnectionReceiver()
//        val filter = IntentFilter()
//        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
//        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
//        registerReceiver(receiver, filter)
//        Log.v(TAG, "receiver registered")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.v(TAG, "onActivityResult")
        val response = AuthorizationClient.getResponse(resultCode, data)

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.v(TAG, response.accessToken.toString())
                Toast.makeText(this, response.accessToken.toString(), Toast.LENGTH_LONG).show()

            }
            AuthorizationResponse.Type.ERROR -> {
                Log.v(TAG, response.error.toString())
                Toast.makeText(this, response.error.toString(), Toast.LENGTH_LONG).show()
            }
            else -> {
                Log.v(TAG, "unexpected on onActivityResult")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote)
    }

    private fun connected() {
        // Subscribe to PlayerState
        mSpotifyAppRemote!!.playerApi.play("spotify:user:anonymised:collection")
        Log.d(TAG, "connected")
    }

    private fun hasBluetoothConnectionPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasInternetPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        var permissionsToRequest =  mutableListOf<String>()
        if (!hasBluetoothConnectionPermission()) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (!hasInternetPermission()) {
            permissionsToRequest.add(Manifest.permission.INTERNET)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.isNotEmpty()) {
            for (i in grantResults.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Bluetooth", "permissions: ${permissions[i]}")
                }
            }
        }
    }
}