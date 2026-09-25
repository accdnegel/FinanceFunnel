package com.pitaka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pitaka.app.data.AppDatabase
import com.pitaka.app.navigation.PitakaNavGraph
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.PitakaViewModelFactory
import com.pitaka.app.ui.theme.PitakaTheme
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PitakaTheme {
                var showSplash by remember { mutableStateOf(savedInstanceState == null) }
                var databaseReady by remember { mutableStateOf(false) }
                var databaseError by remember { mutableStateOf<Throwable?>(null) }
                var viewModelError by remember { mutableStateOf<Throwable?>(null) }

                LaunchedEffect(Unit) {
                    runCatching {
                        // Open Room before constructing the ViewModel. The ViewModel's
                        // repository is created eagerly, so doing this first makes any
                        // migration/open failure observable here.
                        AppDatabase.getInstance(application).openHelper.writableDatabase
                    }.onSuccess {
                        databaseReady = true
                    }.onFailure {
                        databaseError = it
                    }
                }

                Surface(Modifier.fillMaxSize()) {
                    when {
                        showSplash -> PitakaSplashScreen { showSplash = false }
                        databaseError != null -> StartupErrorScreen(databaseError!!)
                        !databaseReady -> StartupCheckingScreen()
                        viewModelError != null -> StartupErrorScreen(viewModelError!!)
                        else -> {
                            // Create the ViewModel only after the database has opened.
                            // If repository/ViewModel initialization still fails, keep the
                            // process alive and expose the exact exception instead of closing.
                            val result = runCatching {
                                androidx.lifecycle.viewmodel.compose.viewModel<PitakaViewModel>(
                                    factory = PitakaViewModelFactory(application)
                                )
                            }
                            result.onFailure { viewModelError = it }
                            result.getOrNull()?.let { vm ->
                                // Compose-time failures inside the first screen are otherwise
                                // reported as a process-level crash after the splash. Keep them
                                // visible so the exact failing screen/component is diagnosable.
                                try {
                                    PitakaNavGraph(vm)
                                } catch (t: Throwable) {
                                    StartupErrorScreen(t)
                                }
                            } ?: if (viewModelError == null) StartupCheckingScreen() else Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupCheckingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Opening Pitaka…")
        }
    }
}

@Composable
private fun StartupErrorScreen(error: Throwable) {
    val message = buildString {
        append(error::class.java.simpleName)
        error.message?.takeIf { it.isNotBlank() }?.let {
            append("\n\n")
            append(it)
        }
        error.cause?.let {
            append("\n\nCause: ")
            append(it::class.java.simpleName)
            it.message?.takeIf { message -> message.isNotBlank() }?.let { message ->
                append(": ")
                append(message)
            }
        }
    }

    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Pitaka could not start", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your local data was not deleted. The database/startup error is shown below.",
                style = MaterialTheme.typography.bodyMedium
            )
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PitakaSplashScreen(onFinished:()->Unit) {
    val progress=remember{Animatable(0f)}
    LaunchedEffect(Unit){progress.animateTo(1f,tween(1300));onFinished()}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Image(painterResource(com.pitaka.app.R.drawable.pitaka_logo),"Pitaka logo",
                Modifier.size(190.dp).waveReveal(progress.value))
            Spacer(Modifier.height(14.dp))
            Text("PITAKA",style=MaterialTheme.typography.headlineMedium)
        }
    }
}
private fun Modifier.waveReveal(progress:Float)=drawWithContent{
    val boundary=size.height*(1f-progress.coerceIn(0f,1f))
    val path=Path().apply{
        moveTo(0f,size.height);lineTo(0f,boundary)
        val steps=24
        for(i in 0..steps){val x=size.width*i/steps;val y=boundary+sin(i.toFloat()/steps*6.28318f)*size.height*.035f;lineTo(x,y)}
        lineTo(size.width,size.height);close()
    }
    clipPath(path){this@drawWithContent.drawContent()}
}