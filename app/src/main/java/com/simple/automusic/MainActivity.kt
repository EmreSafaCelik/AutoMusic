package com.simple.automusic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.spotify.android.appremote.api.SpotifyAppRemote
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

        // Permission requests
        requestPermissions()

        // Get shared preferences
        val sp = getSharedPreferences("chosen_devices", MODE_PRIVATE)

        // Set Spotify to debug mode and authorize
        SpotifyAppRemote.setDebugMode(true)
        Log.v(TAG, "Debug mode on for SpotifyAppRemote")

        // Setting broadcast receiver
        receiver = BluetoothConnectionReceiver()
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(receiver, filter)
        Log.v(TAG, "receiver registered")


        // Getting paired devices
        val mDeviceGroup = findViewById<LinearLayout>(R.id.device_group)
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mBluetoothAdapter.isEnabled) {
            for (i in mBluetoothAdapter.bondedDevices) {
                val mDevice = CheckBox(this)
                mDevice.id = View.generateViewId()
                mDevice.text = i.name
                if (sp.getStringSet("mac-list", mutableSetOf())!!.contains(i.address)) {
                    mDevice.isChecked = true
                }
                mDevice.setOnClickListener {
                    if (mDevice.isChecked()) {
                        with(sp.edit()) {
                            val newSet = sp.getStringSet("mac-list", mutableSetOf())?.toMutableSet()
                            newSet?.add(i.address)
                            Log.v(TAG, newSet.toString())
                            putStringSet("mac-list", newSet)
                            apply()
                        }
                    } else {
                        with (sp.edit()) {
                            val newSet = sp.getStringSet("mac-list", mutableSetOf())?.toMutableSet()
                            newSet?.remove(i.address)
                            Log.v(TAG, newSet.toString())
                            putStringSet("mac-list", newSet)
                            apply()
                        }
                    }
                }
                mDeviceGroup.addView(mDevice)
            }
        } else {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Bluetooth Off")
            alertDialogBuilder.setMessage("Please turn on Bluetooth and restart the app")
            alertDialogBuilder.setNegativeButton("QUIT") { dialog , which ->
                System.exit(-1)
            }
            alertDialogBuilder.show()
        }
    }


    override fun onStart() {
        super.onStart()

        // Get shared preferences
        val sp = getSharedPreferences("chosen_device", MODE_PRIVATE)

        // Authorize Spotify
        if (sp.getBoolean("spotify-authorized", false)) {
            val builder = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setScopes(Array(1) {"user-read-private"})
            val request = builder.build()
            AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.v(TAG, "onActivityResult")
        val response = AuthorizationClient.getResponse(resultCode, data)

        // Get shared preferences
        val sp = getSharedPreferences("chosen_device", MODE_PRIVATE)

        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                Log.v(TAG, response.accessToken.toString())
                Toast.makeText(this, response.accessToken.toString(), Toast.LENGTH_LONG).show()
                sp.edit().putBoolean("spotify-authorized", true).apply()
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



    override fun onDestroy() {
        super.onDestroy()
        SpotifyAppRemote.disconnect(mSpotifyAppRemote)
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