package com.example.catchmestreaming.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    
    /**
     * Gets the local IP address of the device when connected to WiFi
     * @param context Android context for system services
     * @return IP address as string or null if not available
     */
    fun getLocalIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        
        // Check if connected via WiFi
        if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return null
        }
        
        // Try to get IP from WiFi manager first (more reliable for WiFi)
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.let { wifi ->
            val wifiInfo = wifi.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            if (ipAddress != 0) {
                return formatIpAddress(ipAddress)
            }
        }
        
        // Fallback to NetworkInterface approach
        return getIpFromNetworkInterface()
    }
    
    /**
     * Gets the gateway (router) IP address from DHCP info
     * @param context Android context for system services
     * @return Gateway IP address as string or null if not available
     */
    fun getGatewayIpAddress(context: Context): String? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo.gateway != 0) {
            formatIpAddress(dhcpInfo.gateway)
        } else null
    }
    
    /**
     * Generates RTSP URL with given parameters
     * @param ipAddress Device or server IP address
     * @param port RTSP port (default 8554)
     * @param streamPath Stream path (default "stream")
     * @return Complete RTSP URL
     */
    fun generateRtspUrl(
        ipAddress: String, 
        port: Int = 8554, 
        streamPath: String = "stream"
    ): String {
        return "rtsp://$ipAddress:$port/$streamPath"
    }
    
    /**
     * Gets the best available IP address for RTSP streaming
     * Priority: Device IP > Gateway IP > fallback
     * @param context Android context
     * @return IP address to use for RTSP URL
     */
    fun getBestIpForStreaming(context: Context): String {
        // Try device IP first
        getLocalIpAddress(context)?.let { deviceIp ->
            return deviceIp
        }
        
        // Fallback to gateway IP (useful for some network configurations)
        getGatewayIpAddress(context)?.let { gatewayIp ->
            return gatewayIp
        }
        
        // Last resort fallback
        return "192.168.1.100"
    }
    
    private fun formatIpAddress(ipAddress: Int): String {
        return "${ipAddress and 0xFF}.${(ipAddress shr 8) and 0xFF}.${(ipAddress shr 16) and 0xFF}.${(ipAddress shr 24) and 0xFF}"
    }
    
    private fun getIpFromNetworkInterface(): String? {
        try {
            for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                for (address in networkInterface.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.w("NetworkUtil", "Failed to get IP from NetworkInterface", e)
        }
        return null
    }
}