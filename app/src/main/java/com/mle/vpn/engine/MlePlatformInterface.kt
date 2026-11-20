package com.mle.vpn.engine

import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.net.NetworkCapabilities
import android.net.IpPrefix
import android.os.Build
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import android.system.OsConstants
import io.nekohasekai.libbox.*
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Android implementation of sing-box PlatformInterface.
 *
 * Optional root-only facilities are intentionally disabled. The VPN TUN,
 * socket protection, connection ownership and interface discovery are native
 * Android implementations.
 */
class MlePlatformInterface(private val service: MleVpnService) : PlatformInterface {
    private val connectivity = service.getSystemService(ConnectivityManager::class.java)
    private val networkCallbacks = ConcurrentHashMap<InterfaceUpdateListener, ConnectivityManager.NetworkCallback>()

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun autoDetectInterfaceControl(fd: Int) {
        check(service.protect(fd)) { "Unable to protect sing-box transport socket from VPN routing loop." }
    }

    override fun openTun(options: TunOptions): Int {
        check(VpnService.prepare(service) == null) { "VPN permission has not been granted." }
        val builder = service.Builder().setSession("MLE VPN").setMtu(options.mtu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val ipv4 = options.inet4Address
        while (ipv4.hasNext()) {
            val address = ipv4.next()
            builder.addAddress(address.address(), address.prefix())
        }
        val ipv6 = options.inet6Address
        while (ipv6.hasNext()) {
            val address = ipv6.next()
            builder.addAddress(address.address(), address.prefix())
        }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dns = options.dnsServerAddress
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val v4Routes = options.inet4RouteAddress
                if (v4Routes.hasNext()) {
                    while (v4Routes.hasNext()) {
                        val route = v4Routes.next()
                        builder.addRoute(IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                    }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }
                val v6Routes = options.inet6RouteAddress
                if (v6Routes.hasNext()) {
                    while (v6Routes.hasNext()) {
                        val route = v6Routes.next()
                        builder.addRoute(IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                    }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }
                val v4Excluded = options.inet4RouteExcludeAddress
                while (v4Excluded.hasNext()) {
                    val route = v4Excluded.next()
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                }
                val v6Excluded = options.inet6RouteExcludeAddress
                while (v6Excluded.hasNext()) {
                    val route = v6Excluded.next()
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                }
            } else {
                val v4Routes = options.inet4RouteRange
                while (v4Routes.hasNext()) {
                    val route = v4Routes.next()
                    builder.addRoute(route.address(), route.prefix())
                }
                val v6Routes = options.inet6RouteRange
                while (v6Routes.hasNext()) {
                    val route = v6Routes.next()
                    builder.addRoute(route.address(), route.prefix())
                }
            }
            val include = options.includePackage
            while (include.hasNext()) runCatching { builder.addAllowedApplication(include.next()) }
            val exclude = options.excludePackage
            while (exclude.hasNext()) runCatching { builder.addDisallowedApplication(exclude.next()) }
        }

        val descriptor = checkNotNull(builder.establish()) { "Android failed to establish the VPN TUN interface." }
        service.holdTunDescriptor(descriptor)
        return descriptor.fd
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Libbox.findConnectionOwner(ipProtocol, sourceAddress, sourcePort, destinationAddress, destinationPort)
        }
        val uid = connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
        check(uid != Process.INVALID_UID) { "Connection owner was not found." }
        val packages = service.packageManager.getPackagesForUid(uid).orEmpty()
        return ConnectionOwner().also {
            it.userId = uid
            it.userName = packages.firstOrNull() ?: ""
            it.setAndroidPackageNames(MleStringIterator(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = reportNetwork(listener, network)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = reportNetwork(listener, network)
        }
        networkCallbacks.put(listener, callback)?.let { old -> runCatching { connectivity.unregisterNetworkCallback(old) } }
        connectivity.registerDefaultNetworkCallback(callback)
    }

    private fun reportNetwork(listener: InterfaceUpdateListener, network: Network) {
        val props = connectivity.getLinkProperties(network) ?: return
        val caps = connectivity.getNetworkCapabilities(network) ?: return
        val name = props.interfaceName ?: return
        val index = runCatching { NetworkInterface.getByName(name)?.index ?: 0 }.getOrDefault(0)
        listener.updateDefaultInterface(
            name, index,
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            false
        )
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        networkCallbacks.remove(listener)?.let { callback -> runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val items = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
        val allNetworks = connectivity.allNetworks
        val nativeInterfaces = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
        for (network in allNetworks) {
            val props = connectivity.getLinkProperties(network) ?: continue
            val caps = connectivity.getNetworkCapabilities(network) ?: continue
            val name = props.interfaceName ?: continue
            val native = nativeInterfaces.firstOrNull { it.name == name } ?: continue
            val item = io.nekohasekai.libbox.NetworkInterface().apply {
                this.name = name
                index = native.index
                mtu = runCatching { native.mtu }.getOrDefault(1500)
                addresses = MleStringIterator(native.interfaceAddresses.map {
                    val address = if (it.address is Inet6Address) it.address.hostAddress.substringBefore("%") else it.address.hostAddress
                    "$address/${it.networkPrefixLength}"
                })
                dnsServer = MleStringIterator(props.dnsServers.mapNotNull { it.hostAddress })
                gateway = MleStringIterator(props.routes.mapNotNull { it.gateway?.hostAddress })
                type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                flags = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) OsConstants.IFF_UP or OsConstants.IFF_RUNNING else 0
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            items.add(item)
        }
        return MleNetworkInterfaceIterator(items.iterator())
    }

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {
        Log.i(TAG, "sing-box notification: " + notification.title)
    }
    override fun cancelNotification(identifier: String, typeID: Int) = Unit

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun clearDNSCache() = Unit
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) = Unit
    override fun registerMyInterface(name: String?) = Unit

    override fun usePlatformShell(): Boolean = false
    override fun checkPlatformShell() = Unit
    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int): ShellSession {
        error("Platform shell is not supported by MLE VPN.")
    }
    override fun lookupUser(username: String?): PlatformUser = PlatformUser().also {
        it.username = username ?: service.packageName
        it.uid = Process.myUid()
        it.gid = Process.myUid()
        it.homeDir = service.filesDir.absolutePath
    }
    override fun lookupSFTPServer(): String = ""
    override fun readSystemSSHHostKey(): String = ""
    override fun tailscaleHostname(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
    override fun usePlatformBridge(): Boolean = false
    override fun createBridge(options: BridgeOptions?): BridgeSession = error("Platform bridge is not supported.")
}

internal class MleStringIterator(private val values: List<String>) : StringIterator {
    private var index = 0
    override fun len(): Int = values.size
    override fun hasNext(): Boolean = index < values.size
    override fun next(): String = values[index++]
}

private const val TAG = "MLE-Platform"

private class MleNetworkInterfaceIterator(
    private val values: Iterator<io.nekohasekai.libbox.NetworkInterface>
) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = values.hasNext()
    override fun next(): io.nekohasekai.libbox.NetworkInterface = values.next()
}
