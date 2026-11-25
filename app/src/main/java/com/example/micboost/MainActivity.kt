package com.example.micboost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.abs

enum class UsageMode(val label: String, val sub: String, val defaultGain: Float, val icon: ImageVector) {
    GAME("โหมดเล่นเกม", "บูสแรง + กันเสียงแตก", 5.0f, Icons.Rounded.SportsEsports),
    CALL("โหมดโทรคุย", "เสียงใส + ตัดเสียงก้อง", 2.5f, Icons.Rounded.Call)
}

class MainActivity : ComponentActivity() {
    private val engine = MicBoostEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioMicApp(engine = engine)
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, MicForegroundService::class.java))
        engine.stop()
        super.onDestroy()
    }
}

@Composable
fun StudioMicApp(engine: MicBoostEngine) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    var mode by remember { mutableStateOf(UsageMode.GAME) }
    var isActive by remember { mutableStateOf(false) }
    var pttMode by remember { mutableStateOf(false) }
    var gain by remember { mutableFloatStateOf(UsageMode.GAME.defaultGain) }
    var limiterOn by remember { mutableStateOf(true) }
    var monitorOn by remember { mutableStateOf(false) }
    var peakLevel by remember { mutableFloatStateOf(0f) }
    var clipWarning by remember { mutableStateOf(false) }

    val pttInteraction = remember { MutableInteractionSource() }
    val isPttPressed by pttInteraction.collectIsPressedAsState()

    val animatedPeak by animateFloatAsState(
        targetValue = if (isActive) peakLevel else 0f,
        animationSpec = tween(durationMillis = 70),
        label = "peakAnim"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            !isActive -> Color(0xFF2E3545)
            clipWarning -> Color(0xFFFF4D6D)
            else -> Color(0xFF00F5A0)
        },
        label = "ringColor"
    )

    val presets = listOf(1.0f, 2.5f, 5.0f, 8.0f)

    fun syncService(active: Boolean) {
        val svcIntent = Intent(context, MicForegroundService::class.java)
        if (active) {
            svcIntent.putExtra("status", "${mode.label} • Gain ${String.format("%.1f", gain)}x")
            ContextCompat.startForegroundService(context, svcIntent)
        } else {
            context.stopService(svcIntent)
        }
    }

    fun applyMode(newMode: UsageMode) {
        mode = newMode
        gain = newMode.defaultGain
        engine.boostGain = gain
        if (newMode == UsageMode.CALL) {
            monitorOn = false
            engine.setMonitorEnabled(false)
        }
        if (isActive) syncService(true)
    }

    fun startEngine() {
        engine.boostGain = gain
        engine.limiterEnabled = limiterOn
        engine.monitorToEar = if (mode == UsageMode.CALL) false else monitorOn
        engine.start { peak, clipped ->
            val allowed = !pttMode || isPttPressed
            if (allowed) {
                peakLevel = peak
                if (clipped) clipWarning = true
            } else {
                peakLevel = 0f
            }
        }
        isActive = engine.isRunning
        if (isActive) syncService(true)
    }

    fun stopEngine() {
        engine.stop()
        syncService(false)
        isActive = false
        peakLevel = 0f
        clipWarning = false
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B0E14),
            Color(0xFF121824),
            Color(0xFF090B10)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MIC BOOST STUDIO",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "PRO GAMING & VOICE ENGINE",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isActive) Color(0x2200F5A0) else Color(0xFF1E2430),
                    border = BorderStroke(1.dp, if (isActive) Color(0xFF00F5A0) else Color(0xFF2E3748))
                ) {
                    Text(
                        text = if (isActive) "● LIVE" else "○ STANDBY",
                        color = if (isActive) Color(0xFF00F5A0) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (!hasMicPermission) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("แตะเพื่ออนุญาตสิทธิ์ไมโครโฟน", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UsageMode.entries.forEach { item ->
                    val selected = mode == item
                    val cardBg = if (selected) Color(0xFF1A2436) else Color(0xFF121722)
                    val borderCol = if (selected) Color(0xFF38BDF8) else Color(0xFF222A3A)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        border = BorderStroke(1.5.dp, borderCol),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { applyMode(item) }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = item.label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = item.sub,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                val pulseScale = 1f + (animatedPeak * 0.22f)
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(ringColor.copy(alpha = 0.10f))
                        .border(2.dp, ringColor.copy(alpha = 0.45f), CircleShape)
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF151B27),
                    border = BorderStroke(3.dp, ringColor),
                    modifier = Modifier
                        .size(134.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (!hasMicPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (isActive) {
                                stopEngine()
                            } else {
                                startEngine()
                            }
                        }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = "Mic Power",
                            tint = ringColor,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isActive) String.format("%.1fx", gain) else "TAP START",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131924),
                border = BorderStroke(1.dp, Color(0xFF222B3C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BOOST GAIN",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (clipWarning) "PEAK LIMITING" else String.format("%.1fx POWER", gain),
                            color = if (clipWarning) Color(0xFFFF4D6D) else Color(0xFF00F5A0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = gain,
                        onValueChange = {
                            gain = it
                            engine.boostGain = it
                            clipWarning = false
                            if (isActive) syncService(true)
                        },
                        valueRange = 1.0f..8.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = Color(0xFF253044)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { level ->
                            val isSel = abs(gain - level) < 0.15f
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) Color(0xFF38BDF8) else Color(0xFF1B2332),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        gain = level
                                        engine.boostGain = level
                                        clipWarning = false
                                        if (isActive) syncService(true)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${level}x",
                                        color = if (isSel) Color(0xFF090D16) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131924),
                border = BorderStroke(1.dp, Color(0xFF222B3C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SettingSwitchRow(
                        icon = Icons.Rounded.Shield,
                        title = "กันเสียงแตก (Limiter)",
                        checked = limiterOn,
                        onCheckedChange = {
                            limiterOn = it
                            engine.limiterEnabled = it
                        }
                    )
                    HorizontalDivider(color = Color(0xFF1E2636))
                    SettingSwitchRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "โหมดกดค้างพูด (PTT)",
                        checked = pttMode,
                        onCheckedChange = { pttMode = it }
                    )
                    if (mode == UsageMode.GAME) {
                        HorizontalDivider(color = Color(0xFF1E2636))
                        SettingSwitchRow(
                            icon = Icons.Rounded.Headphones,
                            title = "ฟังเสียงตัวเองผ่านหูฟัง",
                            checked = monitorOn,
                            onCheckedChange = {
                                monitorOn = it
                                engine.setMonitorEnabled(it)
                            }
                        )
                    }
                }
            }

            if (pttMode && isActive) {
                Button(
                    onClick = {},
                    interactionSource = pttInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPttPressed) Color(0xFF00C875) else Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (isPttPressed) "กำลังรับเสียง (ปล่อยเพื่อหยุด)" else "กดค้างเพื่อพูด (PTT)",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
            )
            Text(text = title, color = Color.White, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
