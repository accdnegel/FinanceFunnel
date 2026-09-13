package com.pitaka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pitaka.app.navigation.PitakaNavGraph
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.PitakaViewModelFactory
import com.pitaka.app.ui.theme.PitakaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PitakaViewModel by viewModels {
        PitakaViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PitakaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PitakaNavGraph(viewModel = viewModel)
                }
            }
        }
    }
}
