package com.example.shortslimiter

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

class ShortsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession("ShortsLimiter")
            .addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")

        try {
            builder.addAllowedApplication(TARGET_PACKAGE)
        } catch (e: Exception) {
        }

        vpnInterface = builder.establish() ?: return

        workerThread = Thread {
            try {
                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteArray(32767)
                while (!Thread.currentThread().isInterrupted) {
                    input.read(buffer)
                }
            } catch (e: Exception) {
            }
        }
        workerThread?.start()
    }

    private fun stopVpn() {
        workerThread?.interrupt()
        workerThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val TARGET_PACKAGE = "com.google.android.youtube"
        const val ACTION_STOP = "com.example.shortslimiter.STOP_VPN"
    }
}
