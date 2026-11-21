package com.mle.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mle.vpn.engine.catalog.Integration
import com.mle.vpn.engine.catalog.MleEngineCatalog

private val EngineGlyphs = mapOf(
    "vless" to "🔗", "vmess" to "🛰️", "trojan" to "🐴",
    "shadowsocks" to "🧩", "wireguard" to "🛡️", "warp" to "☁️",
    "masque" to "🎭", "warp_on_warp" to "🌀", "amneziawg" to "🔐",
    "hysteria2" to "🚀", "openvpn" to "🌐", "npv" to "📦",
    "outline" to "🗝️", "psiphon" to "🦋", "tor" to "🧅",
    "geph" to "🪁", "lantern" to "🏮", "urnetwork" to "🌍",
    "proton" to "⚛️", "windscribe" to "💨", "fenix" to "🔥",
    "vpngate" to "🚪", "quick" to "⚡", "flux" to "🌊",
    "mae" to "🧠", "github" to "🐙", "telegram-proxy" to "✈️",
    "iran" to "🇮🇷", "fronting" to "🎯", "cloudflare" to "☂️",
    "cfdoctor" to "🩺", "configstudio" to "🧪", "scout" to "🔎",
    "game" to "🎮", "gereh" to "🪢"
)

private fun integrationLabel(value: Integration): String = when (value) {
    Integration.EXISTING_SING_BOX_PATH -> "مسیر فعلی sing-box"
    Integration.ADAPTER_REQUIRED -> "نیازمند اتصال‌دهنده"
    Integration.NATIVE_OR_PROVIDER_REQUIRED -> "نیازمند هسته یا سرویس رسمی"
    Integration.USER_CONFIG_OR_ACCOUNT_REQUIRED -> "نیازمند کانفیگ یا حساب کاربر"
}

@Composable
fun EngineCatalogScreen(padding: androidx.compose.foundation.layout.PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp)
    ) {
        Text("موتورهای اتصال", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(
            "برای هر موتور نماد جداگانه در نظر گرفته شده است. نمایش در فهرست به معنی آماده‌بودن اتصال آن نیست.",
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        EngineGlyphs[engine.iconKey] ?: "🔧",
                        modifier = Modifier.size(42.dp).background(Color(0xFF29314A), RoundedCornerShape(12.dp))
                            .padding(7.dp),
                        fontSize = 23.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(engine.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(integrationLabel(engine.integration), color = Color(0xFF42E8D5), fontSize = 10.sp)
                        Text(engine.detail, color = Color(0xFF929BB5), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
