package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType

/**
 * YouTube sends video as QUIC on UDP/443. WebSocket cannot carry it, so those
 * profiles blackhole the packets and the player falls back to TCP. Hysteria2 can
 * carry them, and a silent blackhole there freezes playback after the first buffer.
 */
fun ProfileItem.canCarryQuic(): Boolean {
    return configType == EConfigType.HYSTERIA2
        || configType == EConfigType.HYSTERIA
        || configType == EConfigType.WIREGUARD
}

fun shouldBlackholeUdp443(profiles: List<ProfileItem>): Boolean {
    if (profiles.isEmpty()) return true
    return profiles.any { !it.canCarryQuic() }
}
