package com.example.jieqi.sync
import java.net.Socket

class BridgeClient(private val host: String, private val port: Int = 9527) {
    private var socket: Socket? = null
    fun connect() { socket = Socket(host, port) }
    fun send(json: String) {
        socket?.getOutputStream()?.write((json + "\n").toByteArray())
    }
    fun close() { socket?.close() }
}
