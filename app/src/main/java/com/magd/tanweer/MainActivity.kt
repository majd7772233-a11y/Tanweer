package com.magd.tanweer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magd.tanweer.data.notifications.TanweerNotificationManager
import com.magd.tanweer.ui.TanweerApp
import com.magd.tanweer.ui.TanweerViewModel
import com.magd.tanweer.ui.theme.TanweerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TanweerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TanweerNotificationManager.initChannel(this)
        TanweerNotificationManager.scheduleDailyReminder(this)
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
}
