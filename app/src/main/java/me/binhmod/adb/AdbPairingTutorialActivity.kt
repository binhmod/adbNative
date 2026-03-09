package me.binhmod.adb

import android.app.AppOpsManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import me.binhmod.adb.databinding.AdbPairingTutorialActivityBinding
import me.binhmod.adb.starter.StarterActivity
import me.binhmod.adb.EnvironmentUtils
import rikka.compatibility.DeviceCompatibility

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingTutorialActivity : AppCompatActivity() {

    private lateinit var binding: AdbPairingTutorialActivityBinding

    private var notificationEnabled: Boolean = false

    companion object {
        private const val TAG = "AdbPairingTutorial"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = AdbPairingTutorialActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        notificationEnabled = isNotificationEnabled()

        if (notificationEnabled) {
            startPairingService()
        }

        binding.apply {
            syncNotificationEnabled()

            // rikka compatibility
            if (DeviceCompatibility.isMiui()) {
                miui.isVisible = true
            }

            developerOptions.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                }
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
            }

            notificationOptions.setOnClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                }
            }

            // StarterActivity
            openStarterButton.setOnClickListener {
                openStarterWithAdb()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val newNotificationEnabled = isNotificationEnabled()
        if (newNotificationEnabled != notificationEnabled) {
            notificationEnabled = newNotificationEnabled
            syncNotificationEnabled()

            if (newNotificationEnabled) {
                startPairingService()
            }
        }
    }

    private fun syncNotificationEnabled() {
        binding.apply {
            step1.isVisible = notificationEnabled
            step2.isVisible = notificationEnabled
            step3.isVisible = notificationEnabled
            network.isVisible = notificationEnabled
            notification.isVisible = notificationEnabled
            notificationDisabled.isGone = notificationEnabled
            openStarterButton.isVisible = notificationEnabled
        }
    }

    private fun isNotificationEnabled(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(AdbPairingService.notificationChannel)

        return nm.areNotificationsEnabled() &&
                (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    private fun startPairingService() {
        val intent = AdbPairingService.startIntent(this)

        try {
            startForegroundService(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "startForegroundService", e)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                val mode = getSystemService(AppOpsManager::class.java)
                    .noteOpNoThrow(
                        "android:start_foreground",
                        android.os.Process.myUid(),
                        packageName,
                        null,
                        null
                    )

                if (mode == AppOpsManager.MODE_ERRORED) {
                    Toast.makeText(
                        this,
                        "OP_START_FOREGROUND is denied.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                startService(intent)
            }
        }
    }

    private fun openStarterWithAdb() {
    val port = EnvironmentUtils.getAdbTcpPort()
    val intent = Intent(this, StarterActivity::class.java).apply {
        putExtra(StarterActivity.EXTRA_HOST, "127.0.0.1")
        putExtra(StarterActivity.EXTRA_PORT, port)
    }
    startActivity(intent)
}
}