package com.simple.automusic

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.util.*

class MainActivity : AppCompatActivity() {

    // Set variables for SpotifySDK
    val CLIENT_ID = "1b5bee9d082c45169e7237ee529fb691"
    val REDIRECT_URI = "com.simple.automusic://auth"
    private var mSpotifyAppRemote: SpotifyAppRemote? = null

    // Set Log tag and BroadcastReceiver
    lateinit var receiver: BluetoothConnectionReceiver
    val TAG = "MainActivityBluetooth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        // Set Spotify to debug mode and authorize - uncomment if you need to
//        SpotifyAppRemote.setDebugMode(true)
//        Log.v(TAG, "Debug mode on for SpotifyAppRemote")

        // Get shared preferences
        val sp = getSharedPreferences("preferences", MODE_PRIVATE)

        // Requests permissions whenever the app starts.
        requestPermissions()

        // Getting paired devices
        val mDeviceGroup = findViewById<LinearLayout>(R.id.device_group)
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        var startBluetoothForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // initialized to be able to initialize checkBluetooth()
            // will be assigned useful content later
        }

        // Prompts to start bluetooth if closed. If on, gets paired devices as radio buttons.
        fun checkBluetooth () {
            requestPermissions()
            if (mBluetoothAdapter != null)
                if (mBluetoothAdapter.isEnabled) {
                    mDeviceGroup.removeAllViews()
                    for (i in mBluetoothAdapter.bondedDevices) {
                        val mDeviceBox = CheckBox(this)
                        mDeviceBox.id = View.generateViewId()
                        mDeviceBox.text = i.name
                        if (sp.getStringSet("mac-list", mutableSetOf())!!.contains(i.address)) {
                            mDeviceBox.isChecked = true
                        }
                        mDeviceBox.setOnClickListener {
                            val newSet = sp.getStringSet("mac-list", mutableSetOf())?.toMutableSet()
                            if (mDeviceBox.isChecked) {
                                newSet?.add(i.address)
                            } else {
                                newSet?.remove(i.address)
                            }
                            sp.edit().putStringSet("mac-list", newSet).apply()
                        }
                        mDeviceGroup.addView(mDeviceBox)
                    }
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Bluetooth Off")
                        .setMessage("Please turn on Bluetooth")
                        .setCancelable(false)
                        .setPositiveButton("Turn on") { _, _ ->
                            if (!mBluetoothAdapter.isEnabled) {
                                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                startBluetoothForResult.launch(enableBtIntent)
                            }
                        }
                        .show()
                }
        }

        startBluetoothForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                checkBluetooth()
        }

        // Checks and sets battery optimizations then sends to check bluetooth
        val batteryOptimizationsForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkBluetooth()
        }
        fun setBatteryOptimizations() {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                checkBluetooth()
            } else {
                // Give an alert to prompt user to set battery optimizations
                AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle("Battery Optimizations Not Set")
                    .setMessage("Please turn off Battery Optimizations")
                    .setPositiveButton("DO IT") { _, _ ->
                        val intent =
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        batteryOptimizationsForResult.launch(intent)
                    }
                    .show()
            }
        }

        // Sends to check and set autolaunch options, then sends to check and set battery options
        val autoLaunchForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setBatteryOptimizations()
        }
        fun setAutomaticLaunchSettings(launchForResult: ActivityResultLauncher<Intent>) {
            var intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                val componentName = intent.component
                val state = packageManager.getComponentEnabledSetting(componentName!!)
                if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    // Automatic launch is unauthorized
                    Log.d(TAG, "in automatic launch unauthorized")
                    val alertDialogBuilder = AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setMessage("Please turn on quick/automatic launch")
                    val resolveInfo = packageManager.resolveActivity(Intent(Settings.ACTION_QUICK_LAUNCH_SETTINGS), 0)
                    if (resolveInfo == null) {
                        alertDialogBuilder.setTitle("Automatic Launch Not Allowed")
                            .setPositiveButton("DO IT") { _, _ ->
                                intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                launchForResult.launch(intent)
                            }
                    } else {
                        alertDialogBuilder.setTitle("Quick Launch Not Allowed")
                            .setPositiveButton("DO IT") { _, _ ->
                                intent =
                                    Intent(Settings.ACTION_QUICK_LAUNCH_SETTINGS).apply {
                                        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                                    }
                                launchForResult.launch(intent)
                            }
                    }
                    alertDialogBuilder.show()
                }
            }
        }

        // Confirms spotify authorization and send to automatic launch options
        val authorizationForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result : ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                val response = AuthorizationClient.getResponse(result.resultCode, intent)
                when (response.type) {
                    AuthorizationResponse.Type.TOKEN -> {
                        Log.v(TAG, response.accessToken.toString())
                        Toast.makeText(this, "Authorization successful", Toast.LENGTH_LONG).show()
                        setAutomaticLaunchSettings(autoLaunchForResult)
                        sp.edit().putBoolean("spotify-authorized", true).apply()
                    }
                    AuthorizationResponse.Type.ERROR -> {
                        Log.d(TAG, response.error.toString())
                        setAutomaticLaunchSettings(autoLaunchForResult)
                        Toast.makeText(this, "something is wrong in authorization", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Log.v(TAG, "unexpected on authorizationForResult")
                    }
                }
            }
        }

        // When pressed, start a chain that checks and lets user set if necessary these:
        // Spotify authorization, automatic/quick launch settings, battery optimizations,
        // Bluetooth permissions, whether Bluetooth is turned on on device right now.
        val mTroubleShootButton = findViewById<Button>(R.id.troubleshoot_button)
        mTroubleShootButton.setOnClickListener {
            val request = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setScopes(Array(1) {"user-read-private"}).build()
            val intent: Intent = AuthorizationClient.createLoginActivityIntent(this, request)
            authorizationForResult.launch(intent)
        }

        // Set broadcast receiver
        receiver = BluetoothConnectionReceiver()
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(receiver, filter)

        // Set delay, editDelay and DelayButton
        val mEditDelay = findViewById<EditText>(R.id.edit_delay)
        val mDelayButton = findViewById<Button>(R.id.delay_btn)
        if (sp.getInt("delay_time", 0) == 0) {
            sp.edit().putInt("delay_time", 3000).apply()
        }
        mDelayButton.setOnClickListener {
            sp.edit().putInt("delay_time", mEditDelay.text.toString().toInt()).apply()
        }
        mEditDelay.setText(sp.getInt("delay_time", 3000).toString())

        // This function converts Spotify URLs to URIs. Will be useful very soon
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

        // Set first launch preferences
        val mStartLink = findViewById<EditText>(R.id.start_link)
        if (sp.getBoolean("first_run", true)) {
            sp.edit().putBoolean("first_run", false).apply()
            setStartLink(mStartLink.text.toString())
            sp.edit().putBoolean("start_favorites", false).apply()
            sp.edit().putBoolean("start_player_state", true).apply()
            sp.edit().putBoolean("shuffle", false).apply()

            val builder = AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)
                .setScopes(Array(1) {"user-read-private"})
            val request = builder.build()
            val intent: Intent = AuthorizationClient.createLoginActivityIntent(this, request)
            authorizationForResult.launch(intent)
        } else {
            checkBluetooth()
        }

        // Set the last entered start URI
        mStartLink.setText(sp.getString("start_link", "put spotify url"))

        // Set the start link (initialized above)
        val mLinkButton = findViewById<Button>(R.id.link_btn)
        mLinkButton.setOnClickListener {
            setStartLink(mStartLink.text.toString())
        }

        // Handle start_favorites box, when it is checked,
        // link shouldn't be editable and the button shouldn't be pressable
        val mStartFavoritesBox = findViewById<CheckBox>(R.id.start_favorites)
        mStartFavoritesBox.isChecked = sp.getBoolean("start_favorites", false)
        mStartFavoritesBox.setOnClickListener {
            sp.edit().putBoolean("start_favorites", !sp.getBoolean("start_favorites", true)).apply()
            mStartLink.isEnabled = !sp.getBoolean("start_favorites", false)
            mLinkButton.isEnabled = !sp.getBoolean("start_favorites", false)
        }

        // Handle start_player_state box, when it is checked, start_favorites should be disabled,
        // along with link_btn and start_link
        val mStartPlayerState = findViewById<CheckBox>(R.id.start_player_state)
        mStartPlayerState.isChecked = sp.getBoolean("start_player_state", true)
        mStartPlayerState.setOnClickListener {
            sp.edit().putBoolean("start_player_state", !sp.getBoolean("start_player_state", true)).apply()
            mStartLink.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
            mLinkButton.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
            mStartFavoritesBox.isEnabled = !sp.getBoolean("start_player_state", false)
        }

        // Set disabled on launch if they are
        mStartLink.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
        mLinkButton.isEnabled = (!sp.getBoolean("start_favorites", false) && !sp.getBoolean("start_player_state", false))
        mStartFavoritesBox.isEnabled = !sp.getBoolean("start_player_state", false)

        // Handle shuffling checkbox
        val mShuffleBox = findViewById<CheckBox>(R.id.shuffle)
        mShuffleBox.isChecked = sp.getBoolean("shuffle", false)
        mShuffleBox.setOnClickListener {
            sp.edit().putBoolean("shuffle", !sp.getBoolean("shuffle", false)).apply()
            mShuffleBox.isChecked = sp.getBoolean("shuffle", false)
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
        val permissionsToRequest =  mutableListOf<String>()
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
                    Log.d(TAG, "permissions: ${permissions[i]}")
                }
            }
        }
    }
}