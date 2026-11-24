package com.matiassoto.ioslauncher.lost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matiassoto.ioslauncher.ui.InterFamily

/** Full-screen "this phone is lost" screen with a big stop button. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setContent {
            val ringing by AlarmPlayer.ringing.collectAsState()
            LaunchedEffect(ringing) { if (!ringing) finish() }
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFB3261E), Color(0xFF1A0505))))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(24.dp))
                Text("This phone is lost", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = InterFamily)
                Spacer(Modifier.height(12.dp))
                Text(
                    "The owner is trying to find it. If you found this phone, please contact its owner.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, textAlign = TextAlign.Center, fontFamily = InterFamily
                )
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { AlarmPlayer.stop(this@RingActivity); finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text("Stop alarm", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily) }
            }
        }
    }
}
