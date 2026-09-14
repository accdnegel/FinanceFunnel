package com.pitaka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pitaka.app.navigation.PitakaNavGraph
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.PitakaViewModelFactory
import com.pitaka.app.ui.theme.PitakaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: PitakaViewModel by viewModels { PitakaViewModelFactory(application) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PitakaTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(1800); showSplash = false }
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSplash) PitakaSplashScreen() else PitakaNavGraph(viewModel)
                }
            }
        }
    }
}

@Composable
private fun PitakaSplashScreen() {
    val transition = rememberInfiniteTransition(label = "wave")
    val offset by transition.animateFloat(
        initialValue = 18f, targetValue = -18f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoOffset"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(com.pitaka.app.R.drawable.pitaka_splash),
            contentDescription = "Pitaka logo",
            modifier = Modifier.fillMaxWidth().padding(24.dp).offset(y = offset.dp).alpha(0.98f),
            contentScale = ContentScale.Fit
        )
    }
}
