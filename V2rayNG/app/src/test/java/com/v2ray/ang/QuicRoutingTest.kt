package com.v2ray.ang

import com.v2ray.ang.core.shouldBlackholeUdp443
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuicRoutingTest {

    @Test
    fun hysteria2CarriesQuic() {
        val profile = ProfileItem.create(EConfigType.HYSTERIA2)
        assertFalse(shouldBlackholeUdp443(listOf(profile)))
    }

    @Test
    fun vlessWebSocketStillBlackholesQuic() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply { network = "ws" }
        assertTrue(shouldBlackholeUdp443(listOf(profile)))
    }

    @Test
    fun mixedGroupKeepsTheBlackhole() {
        val hysteria = ProfileItem.create(EConfigType.HYSTERIA2)
        val websocket = ProfileItem.create(EConfigType.VLESS).apply { network = "ws" }
        assertTrue(shouldBlackholeUdp443(listOf(hysteria, websocket)))
    }

    @Test
    fun emptySelectionBlackholes() {
        assertTrue(shouldBlackholeUdp443(emptyList()))
    }
}
