package com.punyplatipus.podouvir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.punyplatipus.podouvir.ui.navigation.MainScaffold
import com.punyplatipus.podouvir.ui.theme.PodOuvirTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodOuvirTheme {
                MainScaffold()
            }
        }
    }
}
