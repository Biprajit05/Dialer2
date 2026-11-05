package com.mydialer.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var hasLog by mutableStateOf(false)
    private var refreshTick by mutableStateOf(0)
    private var dialNumber by mutableStateOf("")

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshState() }
    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        requestMissingPermissions()
        setContent { DialerTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun readIntent(i: Intent?) {
        val d = i?.data
        if (d != null && d.scheme == "tel") dialNumber = d.schemeSpecificPart
    }

    private fun refreshState() {
        val rm = getSystemService(RoleManager::class.java)
        isDefault = rm.isRoleHeld(RoleManager.ROLE_DIALER)
        hasLog = checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        refreshTick++
    }

    private fun requestMissingPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    private fun setAsDefault() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    private fun placeCall(number: String) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions()
            return
        }
        try {
            getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (_: SecurityException) { }
    }

    @Composable
    private fun Root() {
        var tab by remember { mutableStateOf(0) }
        val navColors = NavigationBarItemDefaults.colors(
            indicatorColor = Green.copy(alpha = 0.18f),
            selectedIconColor = Green, selectedTextColor = Green,
            unselectedIconColor = TextDim, unselectedTextColor = TextDim
        )
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Surface2) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 }, colors = navColors,
                        icon = { Icon(Icons.Rounded.Dialpad, null) }, label = { Text("Keypad") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 }, colors = navColors,
                        icon = { Icon(Icons.Rounded.History, null) }, label = { Text("Recents") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (tab == 0) {
                    KeypadScreen(dialNumber, isDefault, ::setAsDefault, ::placeCall)
                } else {
                    RecentsScreen(hasLog, refreshTick, ::requestMissingPermissions, ::placeCall)
                }
            }
        }
    }
}

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to ""
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    initial: String,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onCall: (String) -> Unit
) {
    var number by remember(initial) { mutableStateOf(initial) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isDefault) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Surface2).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Set as default phone app to use the full-screen call UI.",
                    color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onSetDefault,
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
                ) { Text("Set") }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                number.ifEmpty { " " },
                fontSize = if (number.length > 12) 28.sp else 40.sp,
                fontWeight = FontWeight.Light, color = Color.White, maxLines = 1
            )
        }

        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (d, l) ->
                    Column(
                        Modifier.size(74.dp).clip(CircleShape).background(Surface2)
                            .combinedClickable(
                                onClick = { number += d },
                                onLongClick = if (d == "0") ({ number += "+" }) else null
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(d, fontSize = 28.sp, color = Color.White)
                        if (l.isNotEmpty()) Text(l, fontSize = 9.sp, color = TextDim, letterSpacing = 1.sp)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(74.dp))
            RoundBtn(Icons.Rounded.Call, Green, 76.dp) { onCall(number) }
            Box(
                Modifier.size(74.dp).clip(CircleShape).combinedClickable(
                    onClick = { number = number.dropLast(1) },
                    onLongClick = { number = "" }
                ),
                contentAlignment = Alignment.Center
            ) {
                if (number.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = TextDim, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

data class Recent(val number: String, val name: String?, val type: Int, val date: Long)

private fun loadRecents(ctx: Context): List<Recent> {
    val out = mutableListOf<Recent>()
    try {
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 150) {
                out += Recent(c.getString(0) ?: "", c.getString(1), c.getInt(2), c.getLong(3))
            }
        }
    } catch (_: Exception) { }
    return out
}

@Composable
fun RecentsScreen(hasPermission: Boolean, tick: Int, onGrant: () -> Unit, onCall: (String) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(emptyList<Recent>()) }

    LaunchedEffect(hasPermission, tick) {
        if (hasPermission) items = withContext(Dispatchers.IO) { loadRecents(ctx) }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Recents", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
        )
        when {
            !hasPermission -> Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Allow call log access to see recent calls", color = TextDim)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
                ) { Text("Allow") }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent calls", color = TextDim)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items) { r -> RecentRow(r) { onCall(r.number) } }
            }
        }
    }
}

@Composable
private fun RecentRow(r: Recent, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val missed = r.type == CallLog.Calls.MISSED_TYPE || r.type == CallLog.Calls.REJECTED_TYPE
    val icon = when (r.type) {
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Rounded.CallMade
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Rounded.CallReceived
        else -> Icons.AutoMirrored.Rounded.CallMissed
    }
    val whenText = DateUtils.formatDateTime(
        ctx, r.date,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (missed) Red else Green, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val title = r.name?.takeIf { it.isNotBlank() } ?: r.number.ifBlank { "Unknown" }
            Text(
                title, fontSize = 17.sp, maxLines = 1,
                color = if (missed) Red else Color.White, fontWeight = FontWeight.Medium
            )
            Text(
                if (r.name.isNullOrBlank()) whenText else "${r.number} · $whenText",
                fontSize = 13.sp, color = TextDim, maxLines = 1
            )
        }
        Icon(Icons.Rounded.Call, null, tint = TextDim, modifier = Modifier.size(20.dp))
    }
}
