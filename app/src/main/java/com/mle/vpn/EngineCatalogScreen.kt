package com.mle.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mle.vpn.engine.catalog.Integration
import com.mle.vpn.engine.catalog.MleEngineCatalog

/** Short, stable brand marks: each engine gets a distinct visual identity without emoji fallback. */
private val EngineMarks = mapOf(
    "vless" to "VL", "vmess" to "VM", "trojan" to "TR",
    "shadowsocks" to "SS", "wireguard" to "WG", "warp" to "W",
    "masque" to "MQ", "warp_on_warp" to "W²", "amneziawg" to "AWG",
    "hysteria2" to "H2", "tuic" to "TU", "openvpn" to "OVPN", "npv" to "NPV",
    "outline" to "OL", "psiphon" to "PSI", "tor" to "TOR",
    "geph" to "GPH", "lantern" to "LNT", "urnetwork" to "URN",
    "proton" to "PR", "windscribe" to "WS", "fenix" to "FNX",
    "vpngate" to "VG", "quick" to "Q", "flux" to "FLX",
    "mae" to "MAE", "github" to "GH", "telegram-proxy" to "TG",
    "iran" to "IR", "fronting" to "SNI", "cloudflare" to "CF",
    "cfdoctor" to "DOC", "configstudio" to "CFG", "scout" to "SC",
    "game" to "GAME", "gereh" to "GRH"
)

private val EngineColors = mapOf(
    "vless" to Color(0xFF55D6BE), "vmess" to Color(0xFF8B9CFF),
    "trojan" to Color(0xFFFF8A80), "shadowsocks" to Color(0xFFB39DDB),
    "wireguard" to Color(0xFF64B5F6), "warp" to Color(0xFFF6C85F),
    "masque" to Color(0xFF80CBC4), "warp_on_warp" to Color(0xFFFFB74D),
    "amneziawg" to Color(0xFF90CAF9), "hysteria2" to Color(0xFFFF8A65),
    "tuic" to Color(0xFFCE93D8), "openvpn" to Color(0xFFEA6C72),
    "npv" to Color(0xFF4DD0E1), "outline" to Color(0xFFA5D6A7),
    "psiphon" to Color(0xFFFFAB91), "tor" to Color(0xFFB0BEC5),
    "geph" to Color(0xFF80DEEA), "lantern" to Color(0xFFFFCC80),
    "urnetwork" to Color(0xFF9FA8DA), "proton" to Color(0xFFBA68C8),
    "windscribe" to Color(0xFF81C784), "fenix" to Color(0xFFFFB74D),
    "vpngate" to Color(0xFF90CAF9), "quick" to Color(0xFFFFD54F),
    "flux" to Color(0xFF4FC3F7), "mae" to Color(0xFF9575CD),
    "github" to Color(0xFFB0BEC5), "telegram-proxy" to Color(0xFF64B5F6),
    "iran" to Color(0xFF66BB6A), "fronting" to Color(0xFFFF8A65),
    "cloudflare" to Color(0xFFFFB74D), "cfdoctor" to Color(0xFF4DB6AC),
    "configstudio" to Color(0xFFBA68C8), "scout" to Color(0xFF4DD0E1),
    "game" to Color(0xFF7986CB), "gereh" to Color(0xFF80CBC4)
)

private fun integrationLabel(value: Integration): String = when (value) {
    Integration.EXISTING_SING_BOX_PATH -> "مسیر فعلی sing-box"
    Integration.ADAPTER_REQUIRED -> "نیازمند اتصال‌دهنده"
    Integration.NATIVE_OR_PROVIDER_REQUIRED -> "نیازمند هسته یا سرویس رسمی"
    Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED -> "نیازمند کانفیگ یا حساب کاربر"
}

@Composable
private fun EngineMark(iconKey: String) {
    val accent = EngineColors[iconKey] ?: Color(0xFF55D6BE)
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(Color(0xFF29314A), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = EngineMarks[iconKey] ?: iconKey.take(3).uppercase(),
            color = accent,
            fontSize = if ((EngineMarks[iconKey]?.length ?: 3) > 3) 10.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun EngineCatalogScreen(padding: androidx.compose.foundation.layout.PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        Text("موتورهای اتصال", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(
            "هر موتور نشان اختصاصی دارد. نمایش در فهرست به معنی آماده‌بودن اتصال آن نیست.",
            color = Color(0xFF929BB5), fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
        MleEngineCatalog.items.forEach { engine ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2036))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    EngineMark(engine.iconKey)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(engine.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(integrationLabel(engine.integration), color = Color(0xFF42E8D5), fontSize = 10.sp)
                        Text(engine.detail, color = Color(0xFF929BB5), fontSize = 10.sp)
                    }
                }
            }
        }
        Text(
            "منابع و انتساب: بخشی از فهرست و قابلیت‌های در حال انتقال از MLM VPN است. توسعه‌دهنده اصلی: mlmvpn — github.com/mlmvpn. مجوز و جزئیات در اسناد پروژه درج شده است.",
            color = Color(0xFF929BB5),
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp),
            textAlign = TextAlign.Center
        )
    }
}
