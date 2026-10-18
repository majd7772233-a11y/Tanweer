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
import com.magd.tanweer.data.notifications.TanweerNotificationManager
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
        enableEdgeToEdge()
        TanweerNotificationManager.initChannel(this)
        TanweerNotificationManager.scheduleDailyReminder(this)
        checkAndRequestNotificationPermission()
        handleNotificationIntent(intent)
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

            if (!hasPermission) {
                val prefs = getSharedPreferences("tanweer_prefs", MODE_PRIVATE)
                val hasPrompted = prefs.getBoolean("has_prompted_notif_permission", false)
                if (!hasPrompted) {
                    prefs.edit().putBoolean("has_prompted_notif_permission", true).apply()
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val destination = intent?.getStringExtra(TanweerNotificationManager.EXTRA_NAVIGATE_TO) ?: return
        when (destination) {
            "HOMEWORK" -> {
                viewModel.setTab(com.magd.tanweer.ui.NavigationTab.HOMEWORK)
                viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.NONE)
            }
            "EXAMS" -> {
                viewModel.setTab(com.magd.tanweer.ui.NavigationTab.EXAMS)
                viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.NONE)
            }
            "SCHEDULE" -> {
                viewModel.setTab(com.magd.tanweer.ui.NavigationTab.MORE)
                viewModel.setSubScreen(com.magd.tanweer.ui.SubScreen.SCHEDULE)
            }
        }
    }
}
