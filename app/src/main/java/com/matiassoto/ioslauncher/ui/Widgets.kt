package com.matiassoto.ioslauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WidgetShape = RoundedCornerShape(24.dp)

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - (now % 1000))
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val date = Date(now)
    val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(date).uppercase()
    val dayNum = SimpleDateFormat("d", Locale.getDefault()).format(date)
    val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(date)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(date)
    BoxWithConstraints(
        modifier
            .glass(WidgetShape)
            .clickable {
                runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(14.dp)
    ) {
        val s = maxWidth.value / 140f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(day, color = IosRed, fontSize = (12 * s).sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                Text(dayNum, color = Color.White, fontSize = (34 * s).sp, fontWeight = FontWeight.Bold, lineHeight = (36 * s).sp)
                Text(month, color = Color.White.copy(alpha = 0.8f), fontSize = (12 * s).sp, fontWeight = FontWeight.Medium)
            }
            Text(time, color = Color.White, fontSize = (30 * s).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun BatteryWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && sc > 0) level = (l * 100f / sc).toInt()
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    val color = when {
        charging -> IosGreen
        level in 0..20 -> IosRed
        else -> IosGreen
    }
    BoxWithConstraints(
        modifier
            .glass(WidgetShape)
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(14.dp)
    ) {
        val s = maxWidth.value / 140f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size((84 * s).dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx() * s
                    val inset = stroke / 2
                    drawArc(
                        Color.White.copy(alpha = 0.22f), 0f, 360f, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    if (level >= 0) drawArc(
                        color, -90f, 360f * level / 100f, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(Icons.Outlined.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size((30 * s).dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) Icon(Icons.Filled.Bolt, null, tint = IosGreen, modifier = Modifier.size((18 * s).dp))
                Text(
                    if (level >= 0) "$level%" else "--",
                    color = Color.White, fontSize = (22 * s).sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
