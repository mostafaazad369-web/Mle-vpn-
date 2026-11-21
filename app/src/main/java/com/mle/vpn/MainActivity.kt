package com.mle.vpn

import android.os.Bundle
import android.app.Activity
import android.net.VpnService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.mle.vpn.profile.ImportedProfile
import com.mle.vpn.profile.ProfileImporter
import com.mle.vpn.profile.ProfileProtocol
import com.mle.vpn.profile.ProfileFailoverPlanner
import com.mle.vpn.profile.ProfileParseResult
import com.mle.vpn.profile.ProfileVault
import com.mle.vpn.profile.SubscriptionFetcher
import com.mle.vpn.profile.PublicNodeSource
import com.mle.vpn.profile.PublicNodeSourceCatalog
import com.mle.vpn.profile.PublicSubscriptionFetcher
import com.mle.vpn.profile.SingBoxOutboundBuilder
import com.mle.vpn.profile.SingBoxConfigBuilder
import com.mle.vpn.engine.EndpointProbe
import com.mle.vpn.engine.MleVpnService
import com.mle.vpn.engine.MleVpnStatus
import com.mle.vpn.engine.MleRouteHealthStore
import com.mle.vpn.engine.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF101426)
private val Panel = Color(0xFF1A2036)
private val Mint = Color(0xFF42E8D5)
private val Violet = Color(0xFF8D7CFF)
private val Muted = Color(0xFF929BB5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MleApp() }
    }
}

@Composable
private fun MleApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedEngine by remember { mutableStateOf(context.getSharedPreferences("mle_settings", 0).getString("engine_id", "sing-box") ?: "sing-box") }
    var automaticFailover by remember {
        mutableStateOf(context.getSharedPreferences("mle_settings", 0).getBoolean("automatic_failover", true))
    }
    var selectedServer by remember { mutableStateOf("بهترین موقعیت") }
    var showNotice by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var exportProfile by remember { mutableStateOf<ImportedProfile?>(null) }
    var vpnPermissionGranted by remember { mutableStateOf(false) }
    var vpnPermissionMessage by remember { mutableStateOf("اجازه VPN هنوز بررسی نشده است.") }
    val scope = rememberCoroutineScope()
    val wireGuardFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 64 * 1024) {
                                    "فایل WireGuard نباید بیشتر از ۶۴ کیلوبایت باشد."
                                }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray().toString(Charsets.UTF_8)
                        } ?: error("خواندن فایل انجام نشد.")
                    }
                }.onSuccess { text ->
                    importText = text
                    importError = null
                    showImport = true
                }.onFailure { error ->
                    importError = error.message ?: "خواندن فایل WireGuard ناموفق بود."
                    showImport = true
                }
            }
        }
    }
    val vault = remember { ProfileVault(context) }
    var profiles by remember { mutableStateOf(emptyList<ImportedProfile>()) }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var probeResults by remember { mutableStateOf(emptyMap<String, String>()) }
    var telegramSources by remember { mutableStateOf(emptyList<String>()) }
    var fetchingSource by remember { mutableStateOf<String?>(null) }
    var sourceStatus by remember { mutableStateOf<String?>(null) }
    var selectedPackages by remember { mutableStateOf(context.getSharedPreferences("mle_settings", 0).getStringSet("split_tunnel_packages", emptySet()).orEmpty().toSet()) }
    val connectionStatus by MleVpnStatus.status.collectAsState()

    fun startSelectedProfile() {
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
        if (profile == null) {
            showImport = true
            return
        }
        // WARP is a WireGuard data plane and is currently available through sing-box only.
        val connectionEngineId = if (profile.protocol == ProfileProtocol.WARP) "sing-box" else selectedEngine
        val orderedProfiles = if (automaticFailover) {
            val bounded = ProfileFailoverPlanner.order(
                profiles,
                profile.id,
                maxAttempts = 10
            )
            val rankedIds = MleRouteHealthStore(context).rank(
                bounded.filterNot { it.id == profile.id }.map { "$connectionEngineId:${it.id}" }
            )
            val ranked = rankedIds.mapNotNull { key ->
                bounded.firstOrNull { "$connectionEngineId:${it.id}" == key }
            }
            listOfNotNull(bounded.firstOrNull { it.id == profile.id }) + ranked
        } else listOf(profile)
        val validCandidates = orderedProfiles.mapNotNull { candidate ->
            runCatching {
                val json = if (connectionEngineId == "xray") XrayConfigBuilder.build(candidate) else SingBoxConfigBuilder.build(candidate, selectedPackages.toList())
                candidate to json.toString()
            }.getOrNull()
        }
        if (validCandidates.isEmpty()) {
            Toast.makeText(context, "هیچ‌یک از پروفایل‌های انتخاب‌شده کانفیگ قابل‌اجرا نداشت.", Toast.LENGTH_LONG).show()
            return
        }
        val configCandidates = validCandidates.map { it.second }
        val candidateProfileIds = validCandidates.map { it.first.id }
        val serviceIntent = Intent(context, MleVpnService::class.java).apply {
            action = MleVpnService.ACTION_START
            putExtra(MleVpnService.EXTRA_CONFIG, configCandidates.first())
            putExtra(MleVpnService.EXTRA_ENGINE_ID, connectionEngineId)
            putStringArrayListExtra(MleVpnService.EXTRA_CONFIG_CANDIDATES, ArrayList(configCandidates))
            putStringArrayListExtra(MleVpnService.EXTRA_PROFILE_IDS, ArrayList(candidateProfileIds))
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            .onSuccess {
                vpnPermissionGranted = true
                vpnPermissionMessage = "درخواست راه‌اندازی موتور ${if (connectionEngineId == "xray") "Xray" else "sing-box"} ارسال شد. وضعیت اتصال پس از شروع سرویس مشخص می‌شود."
                showNotice = true
            }
            .onFailure { error ->
                vpnPermissionGranted = false
                MleVpnStatus.failed(error.message ?: "راه‌اندازی سرویس VPN ناموفق بود.")
                vpnPermissionMessage = error.message ?: "راه‌اندازی سرویس VPN ناموفق بود."
                showNotice = true
            }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnPermissionGranted = true
            startSelectedProfile()
        } else {
            vpnPermissionGranted = false
            MleVpnStatus.disconnected()
            vpnPermissionMessage = "درخواست اجازه VPN لغو شد؛ تونلی فعال نشده است."
            showNotice = true
        }
    }
    LaunchedEffect(Unit) {
        profiles = vault.loadAll()
        selectedProfileId = profiles.firstOrNull()?.id
        selectedServer = profiles.firstOrNull()?.displayName ?: "کانفیگی انتخاب نشده"
        telegramSources = runCatching { com.mle.vpn.profile.TelegramSourceCatalog.load(context.assets) }.getOrDefault(emptyList())
        selectedPackages = context.getSharedPreferences("mle_settings", 0).getStringSet("split_tunnel_packages", emptySet()).orEmpty().toSet()
    }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Navy, surface = Panel, primary = Mint,
        secondary = Violet, onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = Navy, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Mint) {
                val tabs = listOf("خانه" to Icons.Default.Home, "سرورها" to Icons.Default.Public, "کانفیگ‌ها" to Icons.Default.List, "تنظیمات" to Icons.Default.Settings, "MAE" to Icons.Default.Apps)
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mint, selectedTextColor = Mint,
                            indicatorColor = Mint.copy(alpha = .12f),
                            unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }) { padding ->
            when (selectedTab) {
                0 -> HomeScreen(
                    server = selectedServer,
                    profileCount = profiles.size,
                    connectionDetail = connectionStatus.detail,
                    connectionPhase = connectionStatus.phase,
                    onServers = { selectedTab = 1 },
                    onConnect = {
                        if (profiles.isEmpty()) {
                            showImport = true
                        } else {
                            val permissionIntent = VpnService.prepare(context)
                            if (permissionIntent == null) startSelectedProfile()
                            else vpnPermissionLauncher.launch(permissionIntent)
                        }
                    },
                    onDisconnect = {
                        runCatching {
                            context.startService(Intent(context, MleVpnService::class.java).apply {
                                action = MleVpnService.ACTION_STOP
                            })
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "قطع اتصال انجام نشد", Toast.LENGTH_LONG).show()
                        }
                    },
                    padding = padding
                )
                1 -> ServerScreen(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onSelect = { profile ->
                        selectedProfileId = profile.id
                        selectedServer = profile.displayName
                    },
                    onAdd = { showImport = true },
                    padding = padding
                )
                2 -> ProfilesScreen(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    probeResults = probeResults,
                    onSelect = {
                        selectedProfileId = it.id
                        selectedServer = it.displayName
                    },
                    onAdd = { showImport = true },
                    onExport = { profile -> exportProfile = profile },
                    onProbe = { profile ->
                        probeResults = probeResults + (profile.id to "در حال بررسی درگاه...")
                        scope.launch {
                            val result = EndpointProbe.check(profile)
                            val message = if (result.reachable) {
                                "${result.message} • ${result.latencyMs} ms (این تست، اتصال VPN را تأیید نمی‌کند)"
                            } else {
                                result.message
                            }
                            probeResults = probeResults + (profile.id to message)
                        }
                    },
                    onDelete = { id ->
                        vault.delete(id)
                        profiles = vault.loadAll()
                        if (selectedProfileId == id) selectedProfileId = profiles.firstOrNull()?.id
                        probeResults = probeResults - id
                    },
                    padding = padding
                )
                3 -> SettingsScreen(
                    padding = padding,
                    automaticFailover = automaticFailover,
                    selectedEngine = selectedEngine,
                    onEngineChange = { engine ->
                        selectedEngine = engine
                        context.getSharedPreferences("mle_settings", 0).edit().putString("engine_id", engine).apply()
                    },
                    onAutomaticFailoverChange = {
                        automaticFailover = it
                        context.getSharedPreferences("mle_settings", 0).edit()
                            .putBoolean("automatic_failover", it).apply()
                    }
                )
                else -> MlmToolsScreen(
                    selectedPackages = selectedPackages,
                    onSelectedPackagesChange = { packages ->
                        selectedPackages = packages
                        context.getSharedPreferences("mle_settings", 0).edit()
                            .putStringSet("split_tunnel_packages", packages.toSet()).apply()
                    },
                    padding = padding
                )
            }
        }
        exportProfile?.let { profile ->
            AlertDialog(
                onDismissRequest = { exportProfile = null },
                containerColor = Panel,
                title = { Text("پیکربندی sing-box", color = Mint, fontWeight = FontWeight.Bold) },
                text = { Text("پیکربندی JSON برای بازبینی و خروجی ساخته می‌شود. این کار به‌تنهایی تونل VPN را اجرا نمی‌کند.", color = Color.White) },
                confirmButton = {
                    TextButton(onClick = {
                        val json = SingBoxConfigBuilder.build(profile).toString(2)
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("MLE sing-box config", json))
                        Toast.makeText(context, "پیکربندی در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                        exportProfile = null
                    }) { Text("کپی JSON", color = Mint) }
                },
                dismissButton = {
                    TextButton(onClick = { exportProfile = null }) { Text("بستن", color = Muted) }
                }
            )
        }
        if (showImport) {
            AlertDialog(
                onDismissRequest = { showImport = false; importError = null },
                containerColor = Panel,
                title = { Text("افزودن کانفیگ", color = Mint, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("لینک اشتراک، متن Base64 یا فایل WireGuard / Cloudflare WARP با پسوند .conf را وارد کن. پروفایل‌های WARP شناسایی و با موتور sing-box اجرا می‌شوند؛ ثبت‌نام حساب WARP خودکار انجام نمی‌شود.", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { wireGuardFilePicker.launch(arrayOf("text/*", "application/octet-stream", "application/wireguard")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
                        ) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text("انتخاب فایل WireGuard / WARP (.conf)")
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("https://example.com/subscription\nvless:// ...\nیا متن اشتراک Base64") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Mint, unfocusedBorderColor = Muted,
                                cursorColor = Mint
                            )
                        )
                        importError?.let { Text(it, color = Color(0xFFFF7B86), fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !importing, onClick = {
                        importing = true
                        importError = null
                        scope.launch {
                            runCatching {
                                val source = SubscriptionFetcher.fetchIfUrl(importText) ?: importText
                                val parsed = ProfileImporter.parseBatch(source)
                                val convertible = parsed.profiles.filter { profile ->
                                    runCatching {
                                        if (selectedEngine == "xray") XrayConfigBuilder.build(profile)
                                        else SingBoxOutboundBuilder.build(profile)
                                    }.isSuccess
                                }
                                parsed.copy(
                                    profiles = convertible,
                                    rejectedCount = parsed.rejectedCount + (parsed.profiles.size - convertible.size)
                                )
                            }.onSuccess { batch ->
                                if (batch.profiles.isEmpty()) {
                                    importError = "کانفیگ قابل‌قبولی پیدا نشد؛ ${batch.rejectedCount} مورد رد شد."
                                } else {
                                    batch.profiles.forEach(vault::save)
                                    profiles = vault.loadAll()
                                    if (selectedProfileId == null) {
                                        val firstProfile = profiles.firstOrNull()
                                        selectedProfileId = firstProfile?.id
                                        selectedServer = firstProfile?.displayName ?: "کانفیگی انتخاب نشده"
                                    }
                                    importText = ""
                                    importError = null
                                    showImport = false
                                }
                            }.onFailure { error ->
                                importError = error.message ?: "ورود کانفیگ انجام نشد."
                            }
                            importing = false
                        }
                    }) { Text(if (importing) "در حال دریافت..." else "دریافت و ذخیره امن", color = Mint) }
                },
                dismissButton = {
                    TextButton(onClick = { showImport = false; importError = null }) {
                        Text("انصراف", color = Muted)
                    }
                }
            )
        }
        if (showNotice) {
            AlertDialog(
                onDismissRequest = { showNotice = false },
                containerColor = Panel,
                title = { Text(if (vpnPermissionGranted) "مجوز VPN صادر شد" else "وضعیت مجوز VPN", color = Mint, fontWeight = FontWeight.Bold) },
                text = { Text(vpnPermissionMessage, color = Color.White) },
                confirmButton = { TextButton(onClick = { showNotice = false }) { Text("متوجه شدم", color = Mint) } }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    server: String,
    profileCount: Int,
    connectionDetail: String,
    connectionPhase: com.mle.vpn.engine.MleConnectionPhase,
    onServers: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("خوش آمدی", color = Muted, fontSize = 13.sp)
                Text("MLE VPN", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(48.dp).background(Mint.copy(alpha = .12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("M", color = Mint, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("اتصال امن،\nبدون مرز", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 38.sp)
        Spacer(Modifier.height(9.dp))
        Text("کنترل ساده و سریع اتصال اینترنت", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(230.dp).border(1.dp, Mint.copy(alpha = .25f), CircleShape).padding(15.dp).border(1.dp, Violet.copy(alpha = .35f), CircleShape).padding(15.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Mint.copy(alpha = .07f), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PowerSettingsNew, null, tint = Mint, modifier = Modifier.size(58.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (connectionPhase) {
                            com.mle.vpn.engine.MleConnectionPhase.DISCONNECTED -> "قطع"
                            com.mle.vpn.engine.MleConnectionPhase.STARTING -> "در حال اتصال"
                            com.mle.vpn.engine.MleConnectionPhase.TUNNEL_ACTIVE -> "در حال آزمایش اینترنت"
                            com.mle.vpn.engine.MleConnectionPhase.INTERNET_VERIFIED -> "اینترنت وصل است"
                            com.mle.vpn.engine.MleConnectionPhase.NO_INTERNET -> "تونل فعال؛ اینترنت قطع"
                            com.mle.vpn.engine.MleConnectionPhase.ERROR -> "خطا"
                        },
                        color = if ((connectionPhase == com.mle.vpn.engine.MleConnectionPhase.ERROR || connectionPhase == com.mle.vpn.engine.MleConnectionPhase.NO_INTERNET)) Color(0xFFFF7B86) else if (connectionPhase == com.mle.vpn.engine.MleConnectionPhase.INTERNET_VERIFIED) Color(0xFF62E6A7) else Mint,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(connectionDetail, color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text(if (profileCount > 0) "$profileCount کانفیگ ذخیره‌شده" else "ابتدا کانفیگ اضافه کن", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy)) {
            Icon(Icons.Default.PowerSettingsNew, null)
            Spacer(Modifier.width(9.dp))
            Text("اتصال به سرور", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.StopCircle, null, tint = Color(0xFFFF7B86))
            Spacer(Modifier.width(8.dp))
            Text("قطع اتصال VPN", color = Color(0xFFFF7B86))
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("موقعیت سرور", color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = onServers) { Text("همه سرورها", color = Mint) }
        }
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp)).clickable(onClick = onServers).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Violet.copy(alpha = .16f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Public, null, tint = Violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("انتخاب خودکار موقعیت", color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = Muted)
        }
        Spacer(Modifier.height(22.dp))
        Text("نسخه 1.0.0 • موتور sing-box در مرحله آزمایش", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ServerScreen(
    profiles: List<ImportedProfile>,
    selectedProfileId: String?,
    onSelect: (ImportedProfile) -> Unit,
    onAdd: () -> Unit,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp)
    ) {
        Text("سرورهای من", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("این فهرست از کانفیگ‌های واقعی ذخیره‌شده ساخته می‌شود؛ موقعیت‌های نمایشی حذف شده‌اند.", color = Muted,
            fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("افزودن سرور یا اشتراک")
        }
        Spacer(Modifier.height(16.dp))
        if (profiles.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز سرور ذخیره‌شده‌ای وجود ندارد. دریافت خودکار مسیرهای جایگزین در پس‌زمینه انجام می‌شود؛ همچنین می‌توانی کانفیگ خودت را وارد کنی.", color = Muted)
            }
        } else {
            profiles.forEach { profile ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp)
                        .background(if (profile.id == selectedProfileId) Mint.copy(alpha = .10f) else Panel, RoundedCornerShape(15.dp))
                        .border(1.dp, if (profile.id == selectedProfileId) Mint.copy(alpha = .5f) else Color.Transparent, RoundedCornerShape(15.dp))
                        .clickable { onSelect(profile) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Public, null, tint = if (profile.id == selectedProfileId) Mint else Violet)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${profile.protocol} • ${profile.host}:${profile.port}", color = Muted, fontSize = 11.sp)
                    }
                    if (profile.id == selectedProfileId) Icon(Icons.Default.CheckCircle, null, tint = Mint)
                }
            }
        }
    }
}

@Composable
private fun TelegramSourcesScreen(
    sources: List<String>,
    publicSources: List<PublicNodeSource>,
    fetchingSource: String?,
    status: String?,
    onFetchPublic: (PublicNodeSource) -> Unit,
    onFetch: (String) -> Unit,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp)
    ) {
        Text("منابع تلگرام", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("${sources.size} منبع عمومی در فهرست؛ دریافت فقط با انتخاب خودت انجام می‌شود.", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        Text("این نام‌ها از فهرست اولیه وارد شده‌اند و فعال یا معتبر بودن همه آن‌ها تضمین نشده است. فقط پیش‌نمایش عمومی t.me خوانده می‌شود؛ کانال خصوصی یا ورود به حساب تلگرام پشتیبانی نمی‌شود.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 16.dp))
        status?.let {
            Text(it, color = Mint, fontSize = 12.sp, modifier = Modifier.fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp)).padding(12.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text("منابع عمومی کانفیگ (مخازن MLM/FLUX و Amnezia)", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
        Text("این منابع عمومی و متغیرند؛ فقط کانفیگ‌هایی که مبدل موتور انتخابی می‌پذیرد ذخیره می‌شوند. سلامت نهایی با آزمایش HTTPS از داخل تونل مشخص می‌شود.", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp))
        publicSources.forEach { source ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(source.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(source.description, color = Muted, fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { onFetchPublic(source) },
                    enabled = fetchingSource == null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
                ) {
                    if (fetchingSource == source.id) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Mint, strokeWidth = 2.dp)
                    } else {
                        Text("دریافت", fontSize = 11.sp)
                    }
                }
            }
        }
        Text("منابع تلگرام", color = Mint, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
        sources.forEach { channel ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .background(Panel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("@$channel", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("پیش‌نمایش عمومی تلگرام", color = Muted, fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { onFetch(channel) },
                    enabled = fetchingSource == null,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
                ) {
                    if (fetchingSource == channel) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Mint, strokeWidth = 2.dp)
                    } else {
                        Text("دریافت", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    automaticFailover: Boolean,
    onAutomaticFailoverChange: (Boolean) -> Unit,
    selectedEngine: String,
    onEngineChange: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(22.dp)) {
        Text("تنظیمات", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("موتور اتصال", color = Muted, fontSize = 12.sp)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)
                .background(Panel, RoundedCornerShape(16.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = selectedEngine == "sing-box",
                onClick = { onEngineChange("sing-box") },
                label = { Text("sing-box") }
            )
            FilterChip(
                selected = selectedEngine == "xray",
                onClick = { onEngineChange("xray") },
                label = { Text("Xray") }
            )
        }
        Text(
            if (selectedEngine == "xray") "Xray: VLESS، VMess، Trojan و Shadowsocks با گونه‌های تبدیل پشتیبانی‌شده."
            else "sing-box: پروتکل‌های پشتیبانی‌شده در مبدل کانفیگ.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("اتصال خودکار به کانفیگ جایگزین", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("در صورت برقرار نشدن اینترنت، حداکثر ۳ پروفایل بررسی می‌شود.", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = automaticFailover, onCheckedChange = onAutomaticFailoverChange)
        }
        listOf("ظاهر برنامه" to "تم تیره اختصاصی", "زبان" to "فارسی", "درباره برنامه" to "MLE VPN نسخه 1.0.0 • با الهام از معماری FLUX/MAE و ابزار متن‌باز MLM VPN").forEach { item ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.first, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(item.second, color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronLeft, null, tint = Muted)
            }
        }
    }
}


@Composable
private fun ProfilesScreen(
    profiles: List<ImportedProfile>,
    selectedProfileId: String?,
    probeResults: Map<String, String>,
    onSelect: (ImportedProfile) -> Unit,
    onAdd: () -> Unit,
    onExport: (ImportedProfile) -> Unit,
    onProbe: (ImportedProfile) -> Unit,
    onDelete: (String) -> Unit,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp)
    ) {
        Text("کانفیگ‌های من", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("پروفایل‌ها روی همین دستگاه رمزگذاری‌شده ذخیره می‌شوند.", color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("افزودن کانفیگ")
        }
        Spacer(Modifier.height(18.dp))
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("هنوز کانفیگی اضافه نشده است.", color = Muted)
            }
        } else {
            profiles.forEach { profile ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .background(if (selectedProfileId == profile.id) Mint.copy(alpha = .10f) else Panel, RoundedCornerShape(16.dp))
                        .border(1.dp, if (selectedProfileId == profile.id) Mint.copy(alpha = .55f) else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onSelect(profile) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VpnKey, null, tint = Mint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${profile.protocol} • ${profile.host}:${profile.port}", color = Muted, fontSize = 11.sp)
                        if (selectedProfileId == profile.id) Text("انتخاب‌شده برای اتصال", color = Mint, fontSize = 10.sp)
                        probeResults[profile.id]?.let { result ->
                            Text(result, color = if (result.startsWith("درگاه TCP")) Mint else Muted, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = { onProbe(profile) }) {
                        Icon(Icons.Default.Bolt, contentDescription = "آزمون درگاه سرور", tint = Mint)
                    }
                    IconButton(onClick = { onExport(profile) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "خروجی JSON", tint = Violet)
                    }
                    IconButton(onClick = { onDelete(profile.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color(0xFFFF7B86))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("توجه: اتصال از موتور sing-box استفاده می‌کند؛ پایداری آن به کیفیت سرور و نتیجه آزمایش روی دستگاه بستگی دارد.", color = Muted, fontSize = 12.sp)
    }
}
