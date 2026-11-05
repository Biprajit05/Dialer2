package com.mydialer.app

import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { DialerTheme { InCallScreen { finish() } } }
    }
}

@Composable
fun InCallScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val call by CallManager.call.collectAsState()
    val state by CallManager.state.collectAsState()
    val muted by CallManager.muted.collectAsState()
    val speaker by CallManager.speaker.collectAsState()
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf(0L) }

    LaunchedEffect(call) {
        val c = call
        if (c == null) {
            delay(900)
            onClose()
        } else {
            val num = c.details.handle?.schemeSpecificPart ?: "Unknown"
            title = num
            val name = withContext(Dispatchers.IO) { lookupName(ctx, num) }
            if (name != null) { title = name; subtitle = num }
        }
    }

    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE) {
            val start = call?.details?.connectTimeMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()
            while (true) {
                seconds = (System.currentTimeMillis() - start) / 1000
                delay(500)
            }
        }
    }

    val ringing = call != null && state == Call.STATE_RINGING
    val calling = call != null && (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_NEW)

    val status = when {
        call == null || state == Call.STATE_DISCONNECTED -> "Call ended"
        ringing -> "Incoming call"
        calling -> "Calling…"
        state == Call.STATE_ACTIVE ->
            if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
            else "%02d:%02d".format(seconds / 60, seconds % 60)
        state == Call.STATE_HOLDING -> "On hold"
        state == Call.STATE_DISCONNECTING -> "Ending…"
        else -> ""
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseScale"
    )
    val pulsing = ringing || calling

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16224A), Color(0xFF0B0F1A), Color(0xFF1E0F33)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.7f))

            Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                if (pulsing) {
                    Box(
                        Modifier.size(130.dp)
                            .graphicsLayer {
                                scaleX = pulse; scaleY = pulse
                                alpha = ((1.5f - pulse) / 0.5f) * 0.35f
                            }
                            .background(Green, CircleShape)
                    )
                }
                Box(
                    Modifier.size(130.dp).clip(CircleShape).background(
                        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = title.firstOrNull { it.isLetter() }?.uppercase()
                    if (initial != null) {
                        Text(initial, fontSize = 52.sp, fontWeight = FontWeight.Light, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(title, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 16.sp, color = TextDim)
            }
            Spacer(Modifier.height(10.dp))
            Text(status, fontSize = 18.sp, color = if (ringing) Green else TextDim, letterSpacing = 1.sp)

            Spacer(Modifier.weight(1f))

            if (ringing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Labeled("Decline") { RoundBtn(Icons.Rounded.CallEnd, Red, 80.dp) { CallManager.hangup() } }
                    Labeled("Answer") { RoundBtn(Icons.Rounded.Call, Green, 80.dp) { CallManager.answer() } }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute", muted) { CallManager.toggleMute() }
                    Toggle(Icons.Rounded.VolumeUp, "Speaker", speaker) { CallManager.toggleSpeaker() }
                }
                Spacer(Modifier.height(36.dp))
                RoundBtn(Icons.Rounded.CallEnd, Red, 80.dp) { CallManager.hangup() }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Labeled(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextDim, fontSize = 13.sp)
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Labeled(label) {
        RoundBtn(
            icon,
            bg = if (on) Color.White else Color.White.copy(alpha = 0.12f),
            size = 68.dp,
            tint = if (on) Bg else Color.White,
            onClick = onClick
        )
    }
}
