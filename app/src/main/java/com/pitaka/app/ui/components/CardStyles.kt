package com.pitaka.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CardStyle(
    val id: String,
    val name: String,
    val base: Color,
    val accent: Color,
    val accent2: Color,
    val pattern: Pattern
)

enum class Pattern { SUN_WAVES, FLOWER, TURTLE, HIBISCUS, TOUCAN, MOUNTAIN, FISH, SHELL, BAMBOO, DIAMOND, WHALE, BUTTERFLY, CORAL, SUNSET, BIRD, PALM, STAR, LEAF, SEAHORSE, FLAME, SEA_WAVES, FLORAL, OCEAN_FISH, LEAF_BURST, ABSTRACT }

private val palette = listOf(
    CardStyle("sun_waves","Sun & Waves",Color(0xFF0754A6),Color(0xFFFFB91D),Color(0xFF18A9D6),Pattern.SUN_WAVES),
    CardStyle("white_flower","White Flower",Color(0xFFFFA817),Color(0xFFFFF4D6),Color(0xFF1D7A46),Pattern.FLOWER),
    CardStyle("sea_turtle","Sea Turtle",Color(0xFF079E99),Color(0xFF073B82),Color(0xFF56D5D0),Pattern.TURTLE),
    CardStyle("hibiscus","Hibiscus",Color(0xFFC81727),Color(0xFFFF3E56),Color(0xFFFFD34D),Pattern.HIBISCUS),
    CardStyle("toucan","Toucan",Color(0xFF06499A),Color(0xFFFFB91D),Color(0xFF1F7A3A),Pattern.TOUCAN),
    CardStyle("green_hills","Green Hills",Color(0xFF197A49),Color(0xFFB7D83C),Color(0xFFFFC62D),Pattern.MOUNTAIN),
    CardStyle("clownfish","Clownfish",Color(0xFFEF4D19),Color(0xFFFF9C18),Color(0xFF0B7BB5),Pattern.FISH),
    CardStyle("shell","Shell",Color(0xFFE82C43),Color(0xFFFFE0B2),Color(0xFFFFA98E),Pattern.SHELL),
    CardStyle("bamboo","Bamboo",Color(0xFF076C4B),Color(0xFFC8DB37),Color(0xFF75A52A),Pattern.BAMBOO),
    CardStyle("diamond","Diamond",Color(0xFFC91A24),Color(0xFFFFC83D),Color(0xFF242424),Pattern.DIAMOND),
    CardStyle("whale","Whale Shark",Color(0xFF0EA1A7),Color(0xFF063B78),Color(0xFFBCE9E7),Pattern.WHALE),
    CardStyle("butterfly","Butterfly",Color(0xFF50308E),Color(0xFFFF9F17),Color(0xFF20B4D0),Pattern.BUTTERFLY),
    CardStyle("coral","Coral Reef",Color(0xFF0D57A4),Color(0xFF13B9AE),Color(0xFFEF7B20),Pattern.CORAL),
    CardStyle("sunset","Sunset",Color(0xFFE94820),Color(0xFFFFC928),Color(0xFF8B284A),Pattern.SUNSET),
    CardStyle("bird","Bird & Leaves",Color(0xFF168D4C),Color(0xFFFFCF39),Color(0xFFEF4B28),Pattern.BIRD),
    CardStyle("mountain","Mountain",Color(0xFF2A9FD2),Color(0xFFFFF4D0),Color(0xFF267A55),Pattern.MOUNTAIN),
    CardStyle("starfish","Starfish",Color(0xFF087BAA),Color(0xFFFF7040),Color(0xFF4AD0D0),Pattern.STAR),
    CardStyle("leaf_burst","Leaf Burst",Color(0xFFFFB617),Color(0xFFE52842),Color(0xFF7C1F50),Pattern.LEAF),
    CardStyle("seahorse","Seahorse",Color(0xFFC9212C),Color(0xFFFFC92E),Color(0xFF0A7B6B),Pattern.SEAHORSE),
    CardStyle("flame_leaf","Flame Leaf",Color(0xFFFFAF16),Color(0xFFE82D37),Color(0xFF7A1C45),Pattern.FLAME),
    CardStyle("deep_waves","Deep Waves",Color(0xFF07509A),Color(0xFF14B7D8),Color(0xFF72E5EF),Pattern.SEA_WAVES),
    CardStyle("tropical_flower","Tropical Flower",Color(0xFF079BA8),Color(0xFFFFE9C0),Color(0xFFEF6A24),Pattern.FLORAL),
    CardStyle("reef_fish","Reef Fish",Color(0xFFE82E54),Color(0xFFFFC738),Color(0xFF0A7C94),Pattern.OCEAN_FISH),
    CardStyle("leaf_wave","Leaf & Wave",Color(0xFF147A4B),Color(0xFFB8D72F),Color(0xFF14A8C8),Pattern.LEAF_BURST),
    CardStyle("abstract_batik","Abstract Batik",Color(0xFF5A3191),Color(0xFFF2C43D),Color(0xFFE53C43),Pattern.ABSTRACT),
    CardStyle("rainbow_waves","Rainbow Waves",Color(0xFF0752A0),Color(0xFFFFB817),Color(0xFF19B8C8),Pattern.SUN_WAVES),
    CardStyle("floral_red","Floral Red",Color(0xFFE12631),Color(0xFFFFF0C8),Color(0xFF248149),Pattern.FLOWER),
    CardStyle("ocean_turtle","Ocean Turtle",Color(0xFF57329A),Color(0xFF17B9D1),Color(0xFF24A9A2),Pattern.TURTLE),
    CardStyle("golden_flower","Golden Flower",Color(0xFFFFB21A),Color(0xFFE73434),Color(0xFF1C5F43),Pattern.LEAF),
    CardStyle("batik_geometry","Batik Geometry",Color(0xFFF08A16),Color(0xFFE42D3B),Color(0xFF242424),Pattern.DIAMOND)
)

fun cardStyles(): List<CardStyle> = palette
fun cardStyleById(id: String): CardStyle = palette.firstOrNull { it.id == id } ?: palette.first()

@Composable
fun BatikCardSurface(
    style: String,
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val selected = if (style == "solid") CardStyle("solid","Solid",baseColor,Color.White,Color.Black,Pattern.ABSTRACT) else cardStyleById(style)
    Box(
        modifier = modifier.clip(RoundedCornerShape(22.dp))
            .background(selected.base)
    ) {
        Canvas(Modifier.matchParentSize()) { drawPattern(selected.pattern, selected.base, selected.accent, selected.accent2) }
        Column(Modifier.fillMaxSize().padding(18.dp), content = content)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPattern(pattern: Pattern, base: Color, a: Color, b: Color) {
    val w=size.width; val h=size.height
    when(pattern) {
        Pattern.SUN_WAVES -> {
            drawCircle(a, radius=minOf(w,h)*.30f, center=Offset(w*.82f,h*.18f))
            repeat(6){ i -> drawArc(b.copy(alpha=.85f),180f,90f,false,Rect(-w*.25f,h*(.40f+i*.11f),w*1.15f,h*(.72f+i*.11f)),style=Stroke(w*.10f)) }
        }
        Pattern.FLOWER, Pattern.FLORAL, Pattern.GOLDEN_FLOWER -> {
            val c=Offset(w*.78f,h*.50f); repeat(8){i->val ang=2*PI*i/8;drawOval(a,Rect(c.x+cos(ang).toFloat()*w*.06f-w*.07f,c.y+sin(ang).toFloat()*h*.20f-h*.11f,c.x+cos(ang).toFloat()*w*.06f+w*.07f,c.y+sin(ang).toFloat()*h*.20f+h*.11f))};drawCircle(b,radius=w*.06f,center=c)
        }
        Pattern.TURTLE, Pattern.WHALE -> {
            val c=Offset(w*.73f,h*.52f);drawOval(a,Rect(c.x-w*.16f,c.y-h*.24f,c.x+w*.16f,c.y+h*.24f));drawCircle(b,w*.06f,Offset(c.x+w*.19f,c.y-h*.12f));repeat(4){i->val ang=PI/2*i;drawOval(b,Rect(c.x+cos(ang).toFloat()*w*.16f-w*.07f,c.y+sin(ang).toFloat()*h*.16f-h*.04f,c.x+cos(ang).toFloat()*w*.16f+w*.07f,c.y+sin(ang).toFloat()*h*.16f+h*.04f))}
        }
        Pattern.HIBISCUS -> { val c=Offset(w*.78f,h*.50f);repeat(5){i->val ang=2*PI*i/5;drawOval(a,Rect(c.x+cos(ang).toFloat()*w*.07f-w*.13f,c.y+sin(ang).toFloat()*h*.12f-h*.07f,c.x+cos(ang).toFloat()*w*.07f+w*.13f,c.y+sin(ang).toFloat()*h*.12f+h*.07f))};drawCircle(b,w*.055f,c) }
        Pattern.TOUCAN, Pattern.BIRD -> { drawOval(a,Rect(w*.64f,h*.20f,w*.90f,h*.78f));drawOval(b,Rect(w*.72f,h*.30f,w*1.03f,h*.48f));drawCircle(Color.Black,w*.018f,Offset(w*.79f,h*.34f));repeat(3){i->drawOval(b,Rect(w*(.55f+i*.10f),h*.68f,w*(.72f+i*.10f),h*.95f))} }
        Pattern.MOUNTAIN -> { val p=Path().apply{moveTo(w*.50f,h*.18f);lineTo(w*.12f,h*.90f);lineTo(w*.88f,h*.90f);close()};drawPath(p,a);drawCircle(b,w*.12f,Offset(w*.82f,h*.18f)) }
        Pattern.FISH, Pattern.OCEAN_FISH -> {drawOval(a,Rect(w*.60f,h*.28f,w*.88f,h*.72f));val p=Path().apply{moveTo(w*.60f,h*.50f);lineTo(w*.42f,h*.30f);lineTo(w*.42f,h*.70f);close()};drawPath(p,b);drawCircle(Color.White,w*.025f,Offset(w*.79f,h*.42f)) }
        Pattern.SHELL -> {repeat(7){i->drawArc(a,180f,-160f, false, Rect(w*.58f,h*.10f+i*h*.055f,w*.94f,h*.90f-i*h*.03f),style=Stroke(w*.025f))} }
        Pattern.BAMBOO -> {repeat(3){i->drawRoundRect(a,Rect(w*(.64f+i*.08f),h*.08f,w*(.70f+i*.08f),h*.94f),w*.03f);repeat(4){j->drawOval(b,Rect(w*(.53f+i*.08f),h*(.20f+j*.20f),w*(.70f+i*.08f),h*(.34f+j*.20f)))}}}
        Pattern.DIAMOND, Pattern.ABSTRACT -> {repeat(3){i->val cx=w*(.68f+i*.13f);val p=Path().apply{moveTo(cx,h*.08f);lineTo(cx+w*.10f,h*.50f);lineTo(cx,h*.92f);lineTo(cx-w*.10f,h*.50f);close()};drawPath(p,if(i%2==0)a else b,style=Stroke(w*.035f))}}
        Pattern.BUTTERFLY -> {drawOval(a,Rect(w*.60f,h*.22f,w*.80f,h*.52f));drawOval(b,Rect(w*.77f,h*.22f,w*.97f,h*.52f));drawOval(b,Rect(w*.60f,h*.48f,w*.80f,h*.78f));drawOval(a,Rect(w*.77f,h*.48f,w*.97f,h*.78f));drawLine(Color.Black,Offset(w*.785f,h*.38f),Offset(w*.785f,h*.63f),w*.015f)}
        Pattern.CORAL -> {repeat(5){i->drawLine(a,Offset(w*(.65f+i*.06f),h*.95f),Offset(w*(.55f+i*.09f),h*.25f),w*.035f);drawLine(b,Offset(w*(.55f+i*.09f),h*.45f),Offset(w*(.45f+i*.09f),h*.28f),w*.025f)}}
        Pattern.SUNSET, Pattern.SEA_WAVES -> {drawCircle(a,w*.20f,Offset(w*.78f,h*.20f));repeat(5){i->drawArc(b.copy(alpha=.8f),180f,180f,false,Rect(-w*.15f,h*(.45f+i*.10f),w*1.10f,h*(.72f+i*.10f)),style=Stroke(w*.075f))}}
        Pattern.STAR -> {val c=Offset(w*.78f,h*.52f);repeat(5){i->val ang=-PI/2+2*PI*i/5;drawLine(a,c,Offset(c.x+cos(ang).toFloat()*w*.20f,c.y+sin(ang).toFloat()*h*.20f),w*.08f)}}
        Pattern.LEAF, Pattern.LEAF_BURST -> {repeat(5){i->drawOval(a,Rect(w*(.55f+i*.07f),h*(.12f+i*.10f),w*(.72f+i*.07f),h*(.50f+i*.10f)))}}
        Pattern.SEAHORSE -> {drawArc(a,0f,300f,false,Rect(w*.62f,h*.18f,w*.86f,h*.78f),style=Stroke(w*.055f));drawCircle(b,w*.055f,Offset(w*.82f,h*.20f));repeat(4){i->drawLine(b,Offset(w*(.63f+i*.07f),h*.72f),Offset(w*(.58f+i*.07f),h*.88f),w*.025f)}}
        Pattern.FLAME -> {repeat(4){i->drawOval(a,Rect(w*(.58f+i*.07f),h*(.15f+i*.05f),w*(.73f+i*.07f),h*(.75f+i*.04f)))}}
    }
}

@Composable
fun CardStylePicker(selected: String, onSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = if (selected == "solid") null else cardStyleById(selected)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Card Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { open = true }) { Text(if (current == null) "Choose" else "Change") }
        }
        BatikCardSurface(
            style = selected,
            baseColor = current?.base ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(130.dp)
        ) {
            Text("PITAKA",color=Color.White.copy(alpha=.8f),style=MaterialTheme.typography.labelSmall)
            Text(if(current==null) "Solid Color" else current.name,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("Preview",color=Color.White.copy(alpha=.85f),style=MaterialTheme.typography.labelSmall)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Select Card Style") },
            text = {
                LazyVerticalGrid(columns=GridCells.Fixed(3), modifier=Modifier.heightIn(max=480.dp), verticalArrangement=Arrangement.spacedBy(8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(cardStyles()) { style ->
                        BatikCardSurface(style.id,style.base,Modifier.height(96.dp).clickable{onSelected(style.id);open=false}) {
                            Text("PITAKA",color=Color.White.copy(alpha=.8f),style=MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(style.name,color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall,maxLines=2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick={open=false}) { Text("Done") } }
        )
    }
}
