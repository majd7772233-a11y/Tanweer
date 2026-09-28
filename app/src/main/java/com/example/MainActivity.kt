package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.TanweerApp
import com.example.ui.TanweerViewModel
import com.example.ui.theme.TanweerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TanweerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TanweerTheme {
                TanweerApp(viewModel = viewModel)
            }
        }
    }
}
