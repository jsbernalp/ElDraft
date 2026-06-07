package com.eldraft.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eldraft.android.ui.ElDraftApp
import com.eldraft.android.ui.theme.ElDraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElDraftTheme {
                ElDraftApp()
            }
        }
    }
}
