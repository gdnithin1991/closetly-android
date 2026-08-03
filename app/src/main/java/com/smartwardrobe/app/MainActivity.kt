package com.smartwardrobe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.smartwardrobe.app.ui.ClosetlyApp
import com.smartwardrobe.app.ui.closetlyColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = closetlyColors()) {
                ClosetlyApp()
            }
        }
    }
}
