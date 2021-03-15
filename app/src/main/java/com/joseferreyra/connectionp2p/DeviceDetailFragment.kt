/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joseferreyra.connectionp2p

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.*
import java.net.ServerSocket

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
class DeviceDetailFragment : androidx.fragment.app.Fragment(), WifiP2pManager.ConnectionInfoListener {
    private var mContentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    var progressDialog: ProgressDialog? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mContentView = inflater.inflate(R.layout.device_detail, null)
        mContentView!!.findViewById<View>(R.id.btn_connect).setOnClickListener {
            val config = WifiP2pConfig()
            config.deviceAddress = device?.deviceAddress
            config.wps.setup = WpsInfo.PBC
            if (progressDialog != null && progressDialog!!.isShowing()) {
                progressDialog!!.dismiss()
            }
            progressDialog = ProgressDialog.show(activity, "Press back to cancel",
                    "Connecting to :" + device?.deviceAddress, true, true
                    //                        new DialogInterface.OnCancelListener() {
                    //
                    //                            @Override
                    //                            public void onCancel(DialogInterface dialog) {
                    //                                ((DeviceActionListener) getActivity()).cancelDisconnect();
                    //                            }
                    //                        }
            )
            (activity as DeviceListFragment.DeviceActionListener).connect(config)
        }
        mContentView!!.findViewById<View>(R.id.btn_disconnect).setOnClickListener { (activity as DeviceListFragment.DeviceActionListener).disconnect() }
        mContentView!!.findViewById<View>(R.id.btn_start_client).setOnClickListener { // Allow user to pick an image from Gallery or other
            // registered apps
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE)
        }
        return mContentView
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.

        

        val uri: Uri? = data?.getData()
        val statusText: TextView = mContentView!!.findViewById<View>(R.id.status_text) as TextView
        statusText.setText("Sending: $uri")
        Log.d(WiFiDirectActivity.TAG, "Intent----------- $uri")
        val serviceIntent = Intent(activity, FileTransferService::class.java)
        serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE)
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString())
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                info?.groupOwnerAddress?.getHostAddress())
        serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988)
        activity?.startService(serviceIntent)
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (progressDialog != null && progressDialog!!.isShowing()) {
            progressDialog!!.dismiss()
        }
        this.info = info
        this.view?.visibility = View.VISIBLE

        // The owner IP is now known.
        var view: TextView = mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.setText(resources.getString(R.string.group_owner_text) + if (info.isGroupOwner == true) resources.getString(R.string.yes) else resources.getString(R.string.no))

        // InetAddress from WifiP2pInfo struct.
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress())

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            // FileServerAsyncTask(activity, mContentView!!.findViewById(R.id.status_text)).execute()
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            mContentView!!.findViewById<View>(R.id.btn_start_client).visibility = View.VISIBLE
            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).setText(resources
                    .getString(R.string.client_text))
        }

        // hide the connect button
        mContentView!!.findViewById<View>(R.id.btn_connect).visibility = View.GONE
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    fun showDetails(device: WifiP2pDevice) {
        this.device = device
        this.view!!.visibility = View.VISIBLE
        var view: TextView = mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.setText(device.deviceAddress)
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText(device.toString())
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        mContentView!!.findViewById<View>(R.id.btn_connect).visibility = View.VISIBLE
        var view: TextView = mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.status_text) as TextView
        view.setText(R.string.empty)
        mContentView!!.findViewById<View>(R.id.btn_start_client).visibility = View.GONE
        view!!.visibility = View.GONE
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    class FileServerAsyncTask(private val context: Context, statusText: View) : AsyncTask<Void?, Void?, String?>() {
        private val statusText: TextView
        protected override fun doInBackground(vararg p0: Void?): String? {
            return try {
                val serverSocket = ServerSocket(8988)
                Log.d(WiFiDirectActivity.TAG, "Server: Socket opened")
                val client = serverSocket.accept()
                Log.d(WiFiDirectActivity.TAG, "Server: connection done")
                val f = File(context.getExternalFilesDir("received"),
                        "wifip2pshared-" + System.currentTimeMillis()
                                + ".jpg")
                val dirs = File(f.parent)
                if (!dirs.exists()) dirs.mkdirs()
                f.createNewFile()
                Log.d(WiFiDirectActivity.TAG, "server: copying files $f")
                val inputstream = client.getInputStream()
                copyFile(inputstream, FileOutputStream(f))
                serverSocket.close()
                f.absolutePath
            } catch (e: IOException) {
                Log.e(WiFiDirectActivity.TAG, e.message)
                null
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        protected override fun onPostExecute(result: String?) {
            if (result != null) {
                statusText.setText("File copied - $result")
                val recvFile = File(result)
                val fileUri: Uri = FileProvider.getUriForFile(
                        context,
                        "com.example.android.wifidirect.fileprovider",
                        recvFile)
                val intent = Intent()
                intent.setAction(Intent.ACTION_VIEW)
                intent.setDataAndType(fileUri, "image/*")
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        protected override fun onPreExecute() {
            statusText.setText("Opening a server socket")
        }

        /**
         * @param context
         * @param statusText
         */
        init {
            this.statusText = statusText as TextView
        }
    }

    companion object {
        protected const val CHOOSE_FILE_RESULT_CODE = 20

        @JvmStatic
        fun copyFile(inputStream: InputStream, out: OutputStream): Boolean {
            val buf = ByteArray(1024)
            var len: Int
            try {
                while (inputStream.read(buf).also { len = it } != -1) {
                    out.write(buf, 0, len)
                }
                out.close()
                inputStream.close()
            } catch (e: IOException) {
                Log.d(WiFiDirectActivity.TAG, e.toString())
                return false
            }
            return true
        }
    }
}