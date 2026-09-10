package com.v2ray.ang.contracts

import android.app.Service

interface ServiceControl {
    /**
     * Gets the service instance.
     * @return The service instance.
     */
    fun getService(): Service

    /**
     * Starts the service.
     */
    fun startService()

    /**
     * Stops the service.
     */
    fun stopService()

    /**
     * Restarts the running core without tearing down the foreground service itself
     * (notification/VPN interface stay alive). Must be used instead of stopService()+startService()
     * for any restart that can be triggered while the app is backgrounded (e.g. a subscription
     * auto-update) — Android 12+ refuses a brand-new foreground-service start from a background
     * process, so a full stop-then-start silently fails there. See CoreVpnService.restartService().
     */
    fun restartService()

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    fun vpnProtect(socket: Int): Boolean
}
