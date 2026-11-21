package com.mle.vpn.engine.openvpn

import org.junit.Assert.assertEquals
import org.junit.Test

class MleOpenVpnNativeContractTest {
    @Test
    fun nativeEntryPointsKeepStableJvmDescriptors() {
        val native = MleOpenVpnNative::class.java
        assertEquals(Int::class.javaPrimitiveType, native.getDeclaredMethod("apiVersion").returnType)
        assertEquals(String::class.java, native.getDeclaredMethod("evaluate", String::class.java).returnType)
        assertEquals(Long::class.javaPrimitiveType, native.getDeclaredMethod("create").returnType)
        assertEquals(
            String::class.java,
            native.getDeclaredMethod(
                "run",
                Long::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
                OpenVpnCallbacks::class.java
            ).returnType
        )
        assertEquals(
            Void.TYPE,
            native.getDeclaredMethod("stop", Long::class.javaPrimitiveType).returnType
        )
        assertEquals(
            Void.TYPE,
            native.getDeclaredMethod("release", Long::class.javaPrimitiveType).returnType
        )
    }

    @Test
    fun callbacksKeepTheDescriptorsUsedByTheUpstreamBridge() {
        val callbacks = OpenVpnCallbacks::class.java
        val intType = Int::class.javaPrimitiveType
        val booleanType = Boolean::class.javaPrimitiveType
        val longType = Long::class.javaPrimitiveType

        listOf("configureTun", "addAddress", "addRoute", "addDns", "addSearchDomain").forEach { name ->
            val method = callbacks.getDeclaredMethod(name, String::class.java, intType)
            assertEquals(booleanType, method.returnType)
        }
        assertEquals(intType, callbacks.getDeclaredMethod("establishTun").returnType)
        assertEquals(
            Void.TYPE,
            callbacks.getDeclaredMethod("onCoreEvent", String::class.java, booleanType).returnType
        )
        assertEquals(
            Void.TYPE,
            callbacks.getDeclaredMethod("onCoreTraffic", longType, longType).returnType
        )
    }
}
