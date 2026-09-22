package com.pitaka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.pitaka.app.navigation.PitakaNavGraph
import com.pitaka.app.ui.PitakaViewModel
import com.pitaka.app.ui.PitakaViewModelFactory
import com.pitaka.app.ui.theme.PitakaTheme
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: PitakaViewModel by viewModels { PitakaViewModelFactory(application) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PitakaTheme {
            var showSplash by remember { mutableStateOf(savedInstanceState == null) }
            Surface(Modifier.fillMaxSize()) {
                if (showSplash) PitakaSplashScreen { showSplash=false } else PitakaNavGraph(viewModel)
            }
        }}
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
    clipPath(path){drawContent()}
}