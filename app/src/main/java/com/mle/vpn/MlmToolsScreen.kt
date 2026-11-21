package com.mle.vpn

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mle.vpn.profile.MlmServiceHint
import com.mle.vpn.profile.MlmServiceRegistry

private data class LaunchableApp(val packageName: String, val label: String)

@Composable
internal fun MlmToolsScreen(
    selectedPackages: Set<String>,
    onSelectedPackagesChange: (Set<String>) -> Unit,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    var services by remember { mutableStateOf(emptyList<MlmServiceHint>()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { resolve ->
                    val activity = resolve.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == context.packageName) return@mapNotNull null
                    LaunchableApp(activity.packageName, resolve.loadLabel(pm).toString())
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.onSuccess { apps = it }
            .onFailure { loadError = it.message ?: "فهرست برنامه‌ها خوانده نشد." }

        runCatching { MlmServiceRegistry.load(context.assets) }
            .onSuccess { services = it }
            .onFailure { loadError = it.message ?: "فهرست MAE خوانده نشد." }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("ابزارهای MLM / MAE", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            "قواعد سرویس‌ها از منابع عمومی MLM بارگذاری می‌شوند. این داده‌ها به‌تنهایی موتور هوشمند یا پروب زنده نیستند.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("تقسیم ترافیک برنامه‌ها", fontWeight = FontWeight.Bold)
                Text(
                    "برنامه‌های انتخاب‌شده از تونل VPN عبور می‌کنند. اگر چیزی انتخاب نشود، همهٔ برنامه‌ها طبق تنظیمات عادی تونل مسیریابی می‌شوند.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
                )
                Text("انتخاب‌شده: ${selectedPackages.size}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                if (apps.isEmpty()) {
                    Text(loadError ?: "در حال خواندن برنامه‌های نصب‌شده…", fontSize = 12.sp)
                } else {
                    apps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selectedPackages,
                                onCheckedChange = { checked ->
                                    onSelectedPackagesChange(
                                        if (checked) selectedPackages + app.packageName
                                        else selectedPackages - app.packageName
                                    )
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontSize = 13.sp)
                                Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("فهرست سرویس‌های MAE", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("شناسه‌ها و دامنه‌ها از services.json؛ فقط راهنمای مسیریابی است.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        Spacer(Modifier.height(8.dp))
        services.forEach { service ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(service.title, fontWeight = FontWeight.SemiBold)
                    Text(service.id, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        listOfNotNull(
                            if (service.likelyCensored) "احتمال محدودیت شبکه" else null,
                            if (service.likelyGeoRestricted) "محدودیت جغرافیایی احتمالی" else null,
                            if (service.usesUdp) "نیازمند UDP" else null
                        ).joinToString(" • ").ifBlank { "بدون برچسب ویژه" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        "برنامه‌ها: ${service.packages.size} • دامنه‌ها: ${service.domains.size}",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    if (service.domains.isNotEmpty()) {
                        Text(service.domains.take(5).joinToString(", "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Divider()
        Text(
            "سایر منابع (Cloudflare Worker، فهرست بازی‌ها، پروفایل‌های ایران و پل‌های Tor) به‌صورت فایل همراه برنامه هستند؛ هنوز Deploy یا به موتور اتصال وصل نشده‌اند.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}
