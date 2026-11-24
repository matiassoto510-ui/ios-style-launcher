package com.matiassoto.ioslauncher.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.matiassoto.ioslauncher.LauncherViewModel
import com.matiassoto.ioslauncher.admin.LockManager
import com.matiassoto.ioslauncher.data.SecurityStore
import com.matiassoto.ioslauncher.lost.AlarmPlayer
import com.matiassoto.ioslauncher.lost.LostModeService
import com.matiassoto.ioslauncher.lost.RingActivity

private sealed class Prompt {
    data class VerifyPin(val title: String, val onOk: () -> Unit) : Prompt()
    data class CreatePin(val then: (() -> Unit)?) : Prompt()
    data class CodeWord(val then: (() -> Unit)?) : Prompt()
    data class Confirm(val title: String, val message: String, val action: String, val onOk: () -> Unit) : Prompt()
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsOverlay(vm: LauncherViewModel, visible: Boolean) {
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        SettingsContent(vm)
    }
}

@Composable
private fun SettingsContent(vm: LauncherViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    val store = remember { SecurityStore(context) }
    val lock = remember { LockManager(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var prompt by remember { mutableStateOf<Prompt?>(null) }
    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_LONG).show()

    LaunchedEffect(Unit) { vm.resumeEvents.collect { refresh++ } }
    DisposableEffect(dark) {
        val win = activity?.window
        val ctl = win?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = !dark
        onDispose { ctl?.isAppearanceLightStatusBars = false }
    }
    BackHandler { if (prompt != null) prompt = null else vm.settingsOpen = false }

    val bg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val fg = if (dark) Color.White else Color.Black
    val sub = fg.copy(alpha = 0.55f)

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri, view.rootView.width.coerceAtLeast(720), view.rootView.height.coerceAtLeast(1280))
    }

    // Lost phone permissions: foreground set first, then background location separately (Android rule).
    val bgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val lostPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        refresh++
        val smsDenied = res[Manifest.permission.RECEIVE_SMS] == false || res[Manifest.permission.SEND_SMS] == false
        if (smsDenied) toast("SMS permission denied. On Android 13+, open App info \u22EE > Allow restricted settings, then try again.")
        if (Build.VERSION.SDK_INT >= 29 && granted(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            toast("Next: choose \u201CAllow all the time\u201D for location so the phone can answer LOCATE while locked.")
            bgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    fun requestLostPermissions() {
        val list = mutableListOf(
            Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        lostPerms.launch(list.toTypedArray())
    }
    fun openAppInfo() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Runs [action] only after the owner's PIN is verified (creating one first if none exists). */
    fun withPin(title: String, action: () -> Unit) {
        prompt = if (!store.hasPin) Prompt.CreatePin(then = action) else Prompt.VerifyPin(title, action)
    }

    @Suppress("UNUSED_VARIABLE") val tick = refresh
    val isOwner = lock.isDeviceOwner
    val lockOn = store.lockMode
    val kioskOn = store.kioskMode
    val lostOn = store.lostPhoneEnabled

    Box(Modifier.fillMaxSize().background(bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Done", color = IosBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { vm.settingsOpen = false }.padding(8.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Section("WALLPAPER", sub) {
                    Group(card) {
                        NavRow(Icons.Outlined.Image, Color(0xFF32ADE6), "Choose from Gallery", fg) {
                            pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Divider(fg)
                        NavRow(Icons.Outlined.Restore, Color(0xFF8E8E93), "Use Default Wallpaper", fg) { vm.resetWallpaper() }
                    }
                }
                Section("HOME SCREEN", sub) {
                    Group(card) {
                        NavRow(Icons.Outlined.Apps, Color(0xFF5856D6), "Edit Home Screen", fg) { vm.settingsOpen = false; vm.editMode = true }
                        Divider(fg)
                        NavRow(Icons.Outlined.Home, IosBlue, "Set as Default Home App", fg) {
                            val ok = runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                            if (!ok) runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        Divider(fg)
                        NavRow(Icons.Outlined.Restore, IosRed, "Reset Home Screen Layout", fg) {
                            prompt = Prompt.Confirm("Reset Home Screen?", "Apps go back to alphabetical order and the default dock.", "Reset") { vm.resetLayout() }
                        }
                    }
                }
                Section("SECURITY", sub) {
                    Group(card) {
                        NavRow(Icons.Outlined.Password, Color(0xFF8E8E93), if (store.hasPin) "Change Launcher PIN" else "Set Launcher PIN", fg) {
                            if (store.hasPin) withPin("Enter current PIN") { prompt = Prompt.CreatePin(null) }
                            else prompt = Prompt.CreatePin(null)
                        }
                    }
                    Footer("The PIN protects Lock mode and Lost phone settings.", sub)
                }
                Section("LOCK MODE", sub) {
                    Group(card) {
                        InfoRow(Icons.Outlined.Shield, Color(0xFF34C759), "Device owner", if (isOwner) "Yes" else "No", fg, sub)
                        Divider(fg)
                        SwitchRow(Icons.Outlined.Lock, Color(0xFFFF9500), "Lock mode", lockOn, fg) { want ->
                            withPin(if (want) "Enter PIN to turn on Lock mode" else "Enter PIN to turn off Lock mode") {
                                val err = if (want) lock.enableLockMode() else lock.disableLockMode(activity)
                                if (err != null) toast(err) else toast(if (want) "Lock mode on" else "Lock mode off")
                                refresh++
                            }
                        }
                        if (isOwner && lockOn) {
                            Divider(fg)
                            SwitchRow(Icons.Outlined.PhonelinkLock, Color(0xFFFF2D55), "Kiosk (pin launcher)", kioskOn, fg) { want ->
                                withPin("Enter PIN") {
                                    val allow = (vm.grid + vm.dock).mapNotNull { vm.apps[it]?.packageName }
                                    lock.setKiosk(activity, want, allow)?.let { toast(it) }
                                    refresh++
                                }
                            }
                        }
                        if (isOwner) {
                            Divider(fg)
                            NavRow(Icons.Outlined.Shield, IosRed, "Remove device owner", IosRed) {
                                withPin("Enter PIN to remove device owner") {
                                    prompt = Prompt.Confirm(
                                        "Remove device owner?",
                                        "Turns off Lock mode, unblocks uninstalling, and gives up device-owner rights. You can set it up again later with adb.",
                                        "Remove",
                                    ) {
                                        val err = lock.removeDeviceOwner(activity)
                                        toast(err ?: "Device owner removed")
                                        refresh++
                                    }
                                }
                            }
                        }
                    }
                    Footer(
                        if (isOwner) "Lock mode keeps this launcher as the boot Home app and blocks uninstalling it. Kiosk pins the launcher; only apps on your Home Screen can open."
                        else "To use Lock mode, make this app device owner with adb first. See LOCK_MODE_SETUP.md.",
                        sub,
                    )
                }
                Section("LOST PHONE", sub) {
                    Group(card) {
                        SwitchRow(Icons.Outlined.LocationOn, Color(0xFF34C759), "Lost phone mode", lostOn, fg) { want ->
                            withPin(if (want) "Enter PIN to turn on Lost phone mode" else "Enter PIN to turn off Lost phone mode") {
                                if (want) {
                                    val enable: () -> Unit = {
                                        store.lostPhoneEnabled = true
                                        LostModeService.start(context)
                                        requestLostPermissions()
                                        refresh++
                                    }
                                    if (!store.hasCodeWord) prompt = Prompt.CodeWord(then = enable) else enable()
                                } else {
                                    store.lostPhoneEnabled = false
                                    LostModeService.stop(context)
                                    refresh++
                                }
                            }
                        }
                        Divider(fg)
                        NavRow(Icons.Outlined.Password, Color(0xFF8E8E93), if (store.hasCodeWord) "Change code word" else "Set code word", fg) {
                            withPin("Enter PIN") { prompt = Prompt.CodeWord(null) }
                        }
                        Divider(fg)
                        val smsOk = granted(context, Manifest.permission.RECEIVE_SMS) && granted(context, Manifest.permission.SEND_SMS)
                        val locOk = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        val bgOk = Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        val notifOk = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)
                        fun mark(b: Boolean) = if (b) "\u2713" else "\u2717"
                        InfoRow(
                            Icons.Outlined.Info, IosBlue, "Permissions",
                            "SMS ${mark(smsOk)}  Loc ${mark(locOk)}  Bg ${mark(bgOk)}  Notif ${mark(notifOk)}", fg, sub,
                            onClick = { if (smsOk && locOk && bgOk && notifOk) openAppInfo() else requestLostPermissions() },
                        )
                        Divider(fg)
                        NavRow(Icons.Outlined.Shield, Color(0xFF8E8E93), "Open App info (restricted settings)", fg) { openAppInfo() }
                        Divider(fg)
                        NavRow(Icons.Outlined.NotificationsActive, IosRed, "Test alarm", fg) {
                            AlarmPlayer.start(context)
                            runCatching { context.startActivity(Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                    Footer(
                        "Off by default. When on, a notification stays visible. From any phone, text \u201C<code word> LOCATE\u201D to get a Google Maps link and battery %, or \u201C<code word> RING\u201D to play a loud alarm for 60 s (overrides silent). " +
                            "Keep Google Find My Device and Samsung Find turned on as your main backup. On Android 13+, sideloaded apps may need App info \u22EE > Allow restricted settings before SMS permission can be granted.",
                        sub,
                    )
                }
                Section("ABOUT", sub) {
                    Group(card) {
                        InfoRow(Icons.Outlined.Info, Color(0xFF8E8E93), "iOS Style Launcher", "1.0", fg, sub)
                    }
                    Footer("Original code. Icons: Material Symbols (Apache 2.0). Font: Inter (SIL Open Font License 1.1). Not affiliated with Apple.", sub)
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        prompt?.let { p ->
            PromptDialog(p, store, dark, onDismiss = { prompt = null }, onReplace = { prompt = it }, refresh = { refresh++ })
        }
    }
}

@Composable
private fun PromptDialog(
    p: Prompt, store: SecurityStore, dark: Boolean,
    onDismiss: () -> Unit, onReplace: (Prompt?) -> Unit, refresh: () -> Unit,
) {
    val card = if (dark) Color(0xFF2C2C2E) else Color.White
    val fg = if (dark) Color.White else Color.Black
    var text by remember(p) { mutableStateOf("") }
    var first by remember(p) { mutableStateOf<String?>(null) }
    var error by remember(p) { mutableStateOf<String?>(null) }

    val title: String
    val message: String
    val isSecret: Boolean
    val numeric: Boolean
    val showField: Boolean
    var okLabel = "OK"
    when (p) {
        is Prompt.VerifyPin -> { title = p.title; message = "Launcher PIN"; isSecret = true; numeric = true; showField = true }
        is Prompt.CreatePin -> {
            title = if (first == null) "Create a launcher PIN" else "Confirm PIN"
            message = "4\u20138 digits. Needed to change Lock mode and Lost phone settings."
            isSecret = true; numeric = true; showField = true
        }
        is Prompt.CodeWord -> {
            title = "Lost phone code word"
            message = "4\u201332 letters or digits, no spaces. Text \u201C<code word> LOCATE\u201D or \u201C<code word> RING\u201D to this phone."
            isSecret = false; numeric = false; showField = true; okLabel = "Save"
        }
        is Prompt.Confirm -> { title = p.title; message = p.message; isSecret = false; numeric = false; showField = false; okLabel = p.action }
    }

    fun submit() {
        when (p) {
            is Prompt.VerifyPin -> if (store.checkPin(text)) { onReplace(null); p.onOk() } else { error = "Wrong PIN"; text = "" }
            is Prompt.CreatePin -> {
                if (!Regex("^\\d{4,8}$").matches(text)) { error = "Use 4\u20138 digits"; return }
                if (first == null) { first = text; text = ""; error = null }
                else if (first == text) { store.setPin(text); refresh(); onReplace(null); p.then?.invoke() }
                else { error = "PINs didn't match"; first = null; text = "" }
            }
            is Prompt.CodeWord -> {
                if (!Regex("^[A-Za-z0-9]{4,32}$").matches(text.trim())) { error = "4\u201332 letters or digits, no spaces"; return }
                store.setCodeWord(text.trim()); refresh(); onReplace(null); p.then?.invoke()
            }
            is Prompt.Confirm -> { onReplace(null); p.onOk() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(300.dp).clip(RoundedCornerShape(16.dp)).background(card)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            Text(message, color = fg.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (showField) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, singleLine = true,
                    visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Ascii),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = IosRed) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = fg.copy(alpha = 0.15f), thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                Text("Cancel", color = IosBlue, fontSize = 17.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable { onDismiss() }.padding(vertical = 12.dp))
                Box(Modifier.width(0.5.dp).height(48.dp).background(fg.copy(alpha = 0.15f)))
                Text(okLabel, color = if (p is Prompt.Confirm) IosRed else IosBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable { submit() }.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, sub: Color, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, color = sub, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        content()
    }
}

@Composable
private fun Footer(text: String, sub: Color) {
    Text(text, color = sub, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
}

@Composable
private fun Group(card: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(card)) { content() }
}

@Composable
private fun Divider(fg: Color) {
    HorizontalDivider(color = fg.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(tint), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun NavRow(icon: ImageVector, tint: Color, label: String, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = fg.copy(alpha = 0.3f))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, tint: Color, label: String, value: String, fg: Color, sub: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = sub, fontSize = 15.sp)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, tint: Color, label: String, checked: Boolean, fg: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = IosGreen, checkedThumbColor = Color.White, checkedBorderColor = IosGreen),
        )
    }
}
