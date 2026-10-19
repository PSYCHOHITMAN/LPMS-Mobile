package com.pointblank.lpms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pointblank.lpms.ui.LpmsApp
import com.pointblank.lpms.ui.theme.LpmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LpmsTheme {
                LpmsApp()
            }
        }
    }
}
