// Copyright 2011 Google Inc. All Rights Reserved.
package com.joseferreyra.connectionp2p

import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.joseferreyra.connectionp2p.DeviceDetailFragment.Companion.copyFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
class FileTransferService : IntentService {
    constructor(name: String?) : super(name) {}
    constructor() : super("FileTransferService") {}

    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    override fun onHandleIntent(intent: Intent?) {
        val context = applicationContext
        if (intent!!.action == ACTION_SEND_FILE) {
            val fileUri = intent.extras!!.getString(EXTRAS_FILE_PATH)
            val host = intent.extras!!.getString(EXTRAS_GROUP_OWNER_ADDRESS)
            val socket = Socket()
            val port = intent.extras!!.getInt(EXTRAS_GROUP_OWNER_PORT)
            try {
                Log.d(WiFiDirectActivity.TAG, "Opening client socket - ")
                socket.bind(null)
                socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT)
                Log.d(WiFiDirectActivity.TAG, "Client socket - " + socket.isConnected)
                val stream = socket.getOutputStream()
                val cr = context.contentResolver
                var input: InputStream? = null
                try {
                    input = cr.openInputStream(Uri.parse(fileUri))
                } catch (e: FileNotFoundException) {
                    Log.d(WiFiDirectActivity.TAG, e.toString())
                }
                copyFile(input!!, stream)
                Log.d(WiFiDirectActivity.TAG, "Client: Data written")
            } catch (e: IOException) {
                Log.e(WiFiDirectActivity.TAG, e.message)
            } finally {
                if (socket != null) {
                    if (socket.isConnected) {
                        try {
                            socket.close()
                        } catch (e: IOException) {
                            // Give up
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT = 5000
        const val ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE"
        const val EXTRAS_FILE_PATH = "file_url"
        const val EXTRAS_GROUP_OWNER_ADDRESS = "go_host"
        const val EXTRAS_GROUP_OWNER_PORT = "go_port"
    }
}