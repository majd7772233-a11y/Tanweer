package com.magd.tanweer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.notifications.NotificationLaunchStore
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.data.remote.NetworkModule
import com.magd.tanweer.util.DeviceIdentity
import com.magd.tanweer.ui.TanweerApp
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.theme.TanweerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TanweerViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                TanweerNotificationManager.scheduleDailyReminder(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NetworkModule.setDeviceId(DeviceIdentity.getOrCreate(applicationContext))
        enableEdgeToEdge()
        TanweerNotificationManager.initChannel(this)
        TanweerNotificationManager.scheduleDailyReminder(this)
        // Keep periodic version/catalog requests off the critical path of a notification launch.
        if (intent?.hasExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO) != true) {
            TanweerNotificationManager.performBackgroundMaintenance(this)
        }
        checkAndRequestNotificationPermission()

        // Configure Coil image loader to transparently carry authentication headers for school media
        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient(com.magd.tanweer.data.remote.NetworkModule.okHttpClient)
            .crossfade(true)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Exception) {}

        stageNotificationIntent(intent)
        setContent {
            val fontScale by viewModel.fontSizeScale.collectAsStateWithLifecycle()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()

            TanweerTheme(
                fontScale = fontScale,
                themeMode = appTheme
            ) {
                TanweerApp(viewModel = viewModel)
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            val prefs = getSharedPreferences("tanweer_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("notifications_permission_granted", hasPermission).apply()

            if (!hasPermission) {
                val promptCount = prefs.getInt("notif_permission_prompt_count", 0)
                val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                // Allow prompting when appropriate without permanently hiding without user knowledge
                if (promptCount < 2 || shouldShowRationale) {
                    prefs.edit().putInt("notif_permission_prompt_count", promptCount + 1).apply()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            getSharedPreferences("tanweer_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("notifications_permission_granted", hasPermission)
                .apply()
        }
        viewModel.onAppResumed()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Stage cold-start routes before the lazy ViewModel can begin startup synchronization. */
    private fun stageNotificationIntent(intent: android.content.Intent?) {
        val destination = intent?.getStringExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO) ?: return
        intent.removeExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO)
        NotificationLaunchStore.stage(destination)
    }

    /** Existing singleTop activities receive notification taps here without a task restart. */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val destination = intent?.getStringExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO) ?: return
        intent.removeExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO)
        viewModel.requestNotificationNavigation(destination)
    }
}
