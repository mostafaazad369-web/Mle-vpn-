package com.mle.vpn.vpn

import android.net.VpnService
import android.content.Intent
import android.os.IBinder

/**
 * The sole Android VPN-service owner.
 *
 * This initial rebuild intentionally does not establish a TUN interface:
 * a TUN without a live packet forwarder would black-hole traffic and create
 * a misleading connected state. A concrete TunnelEngine must be injected
 * and pass device-level leak/connectivity tests before start is implemented.
 */
class MleVpnService : VpnService() {
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
