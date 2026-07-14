package io.github.airdaydreamers.melddrive.fake

import java.net.ServerSocket

/**
 * A lightweight mock/fake SMB server running on port 4445 within the test process.
 * Real TCP socket connections are established and immediately closed to verify networking.
 */
class FakeSmbServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var thread: Thread? = null

    fun start() {
        isRunning = true
        thread = Thread {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isRunning) {
                    val socket = serverSocket?.accept()
                    socket?.close()
                }
            } catch (_: Exception) {}
        }.apply { start() }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        try {
            thread?.join(1000)
        } catch (_: Exception) {}
    }
}
