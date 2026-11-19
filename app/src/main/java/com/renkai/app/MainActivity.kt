package com.renkai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.renkai.app.ui.RenkaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ekstra intent untuk uji otomatis (emulator CI): langsung putar judul/episode.
        val autoTitle = intent?.getStringExtra("rk_title")
        val autoEp = intent?.getIntExtra("rk_ep", 1) ?: 1
        setContent {
            RenkaiTheme {
                RenkaiApp(autoTitle = autoTitle, autoEp = autoEp)
            }
        }
    }
}
