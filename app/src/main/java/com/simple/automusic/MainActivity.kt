package com.simple.automusic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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

    // Checks and sets battery optimizations
    fun setBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Already whitelisted
        } else {
            // Give an alert to prompt user to set battery optimizations
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setCancelable(false)
            alertDialogBuilder.setTitle("Battery Optimizations Not Set")
            alertDialogBuilder.setMessage("Please turn off Battery Optimizations")
            alertDialogBuilder.setPositiveButton("DO IT") { dialog , which ->
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                startActivity(intent)
            }
            alertDialogBuilder.show()
        }
    }

    // Cheks and sets automatic launch approval
    fun setAutomaticLaunchSettings() {
        var intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            val componentName = intent.component
            val state = packageManager.getComponentEnabledSetting(componentName!!)
            Log.v(TAG, componentName.toString())
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                // Automatic launch is already authorized
            } else {
                // Automatic launch is unauthorized
                val alertDialogBuilder = AlertDialog.Builder(this)
                alertDialogBuilder.setCancelable(false)
                alertDialogBuilder.setMessage("Please turn on quick/automatic launch")
                val resolveInfo = packageManager.resolveActivity(Intent(Settings.ACTION_QUICK_LAUNCH_SETTINGS), 0)
                Log.v(TAG, resolveInfo.toString())
                if (resolveInfo == null) {
                    alertDialogBuilder.setTitle("Automatic Launch Not Allowed")
                    alertDialogBuilder.setPositiveButton("DO IT") { dialog , which ->
                        intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    alertDialogBuilder.show()
                } else {
                    alertDialogBuilder.setTitle("Quick Launch Not Allowed")
                    alertDialogBuilder.setPositiveButton("DO IT") { dialog , which ->
                        intent =
                            Intent(Settings.ACTION_QUICK_LAUNCH_SETTINGS).apply {
                                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                            }
                        startActivity(intent)
                    }
                    alertDialogBuilder.show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permission requests
        requestPermissions()

        // Get shared preferences
        val sp = getSharedPreferences("chosen_devices", MODE_PRIVATE)

        val mSpotifyAuthorizeButton = findViewById<Button>(R.id.authorize_spotify)
        mSpotifyAuthorizeButton.setOnClickListener {
            val builder = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setScopes(Array(1) {"user-read-private"})
            val request = builder.build()
            AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
        }

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

        // Set delay editText and button
        val mEditDelay = findViewById<EditText>(R.id.edit_delay)
        val mDelayBtn = findViewById<Button>(R.id.delay_btn)
        if (sp.getInt("delay_time", 0) == 0) {
            with(sp.edit()) {
                putInt("delay_time", 3000)
                apply()
            }
        }
        mDelayBtn.setOnClickListener {
            with(sp.edit()) {
                putInt("delay_time", mEditDelay.text.toString().toInt())
                apply()
            }
        }
        mEditDelay.setText(sp.getInt("delay_time", 3000).toString())

        // Set first launch and start_favorites checkbox using it
        val mLinkButton = findViewById<Button>(R.id.link_btn)
        val mStartLink = findViewById<EditText>(R.id.start_link)
        val mStartFavoritesBox = findViewById<CheckBox>(R.id.start_favorites)
        val mStartPlayerState = findViewById<CheckBox>(R.id.start_player_state)
        val mShuffleBox = findViewById<CheckBox>(R.id.shuffle)

        fun setStartLink(startLink: String) {
            // URL: https://open.spotify.com/playlist/0vvXsWCC9xrXsKd4FyS8kM?si=02c8ec2afd9d40cd
            // to
            // URI: spotify:playlist:0vvXsWCC9xrXsKd4FyS8kM?si=02c8ec2afd9d40cd
            try {
                val parts = startLink.split("/")
                sp.edit().putString("start_link", "spotify:${parts[3]}:${parts[4].split("?")[0]}").apply()
            } catch (ex: Exception) {
                Log.d(TAG, "bad url")
            }
        }

        if (sp.getBoolean("first_run", true)) {
            sp.edit().putBoolean("first_run", false).apply()
            setStartLink(mStartLink.text.toString())
            sp.edit().putBoolean("start_favorites", false).apply()
            sp.edit().putBoolean("start_player_state", true).apply()
            sp.edit().putBoolean("shuffle", false).apply()
        }

        mStartLink.setText(sp.getString("start_link", "put spotify url"))

        mStartFavoritesBox.isChecked = sp.getBoolean("start_favorites", false)
        mStartFavoritesBox.setOnClickListener {
            sp.edit().putBoolean("start_favorites", !sp.getBoolean("start_favorites", true)).apply()
            mStartLink.isEnabled = !sp.getBoolean("start_favorites", false)
            mLinkButton.isEnabled = !sp.getBoolean("start_favorites", false)
        }

        mStartPlayerState.isChecked = sp.getBoolean("start_player_state", true)
        mStartPlayerState.setOnClickListener {
            sp.edit().putBoolean("start_player_state", !sp.getBoolean("start_player_state", true)).apply()
            mStartLink.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
            mLinkButton.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
            mStartFavoritesBox.isEnabled = !sp.getBoolean("start_player_state", false)
        }

        mStartLink.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
        mLinkButton.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
        mStartFavoritesBox.isEnabled = !sp.getBoolean("start_player_state", false)

        // Set shuffling checkbox
        mShuffleBox.isChecked = sp.getBoolean("shuffle", false)
        mShuffleBox.setOnClickListener {
            sp.edit().putBoolean("shuffle", !sp.getBoolean("shuffle", false)).apply()
            mShuffleBox.isChecked = sp.getBoolean("shuffle", false)
        }



        // Set the start link (initialized above)
        mLinkButton.setOnClickListener {
            setStartLink(mStartLink.text.toString())
        }


        // Set autoamatic launch settings
        if (!sp.getBoolean("settingsSet", false)) {
            setAutomaticLaunchSettings()
        }

        // Getting paired devices
        val mDeviceGroup = findViewById<LinearLayout>(R.id.device_group)
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (mBluetoothAdapter != null)
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
            alertDialogBuilder.setMessage("Please turn on Bluetooth")
            alertDialogBuilder.setPositiveButton("Turn on") { dialog , which ->
                val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
                val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.getAdapter()
                if (bluetoothAdapter == null) {
                    // Device doesn't support Bluetooth
                } else {
                    if (bluetoothAdapter?.isEnabled == false) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        startActivityForResult(enableBtIntent, REQUEST_CODE + 1)
                    }
                }
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


    override fun onResume() {
        super.onResume()
        val sp = getSharedPreferences("chosen_devices", MODE_PRIVATE)
        if (!sp.getBoolean("settingsSet", false)) {
            setBatteryOptimizations()
            with(sp.edit()) {
                putBoolean("settingsSet", true)
                apply()
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.v(TAG, "onActivityResult with result code: " + requestCode.toString())
        // Get shared preferences
        val sp = getSharedPreferences("chosen_device", MODE_PRIVATE)

        if (requestCode == REQUEST_CODE + 1) {
            val mDeviceGroup = findViewById<LinearLayout>(R.id.device_group)
            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            if (mBluetoothAdapter != null)
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
                }
        } else if (requestCode == REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, data)
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