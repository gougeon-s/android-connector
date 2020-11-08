package com.github.gotify.connector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.core.os.bundleOf

/**
 * This Activity is used to register to gotify
 */

private const val gotify_package = "com.github.gotify"
private const val messenger_service = "$gotify_package.service.GotifyRegisterService"

data class Registration(val senderUid: Int, val url: String, val token: String)

interface GotifyBindingHandler {
    fun onConnected(service: GotifyServiceBinding)
    fun onRegistered(service: GotifyServiceBinding, registration: Registration){
        registerGotifyIdInSharedPref(service.context,registration.senderUid)
    }
    fun onUnregistered(service: GotifyServiceBinding)
}

class GotifyServiceBinding(var context: Context, var bindingHandler: GotifyBindingHandler){
    /** Messenger for communicating with service.  */
    private var gService: Messenger? = null
    /** To known if it if bound to the service */
    private var gIsBound = false
    private var waitingForInfo = false

    /**
     * Handler of incoming messages from service.
     */
    private inner class gHandler : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TYPE_CLIENT_STARTED -> {
                    logi("Received MSG_START from service")
                    bindingHandler.onConnected(this@GotifyServiceBinding)
                }
                TYPE_REGISTERED_CLIENT -> {
                    if(waitingForInfo) {
                        waitingForInfo = false
                        val url = msg.data?.getString("url").toString()
                        val token = msg.data?.getString("token").toString()
                        logi("new url: $url")
                        logi("new token: $token")
                        bindingHandler.onRegistered(this@GotifyServiceBinding,
                                Registration(msg.sendingUid,url,token))
                    }
                }
                TYPE_UNREGISTERED_CLIENT -> {
                    logi("App is unregistered")
                    bindingHandler.onUnregistered(this@GotifyServiceBinding)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val gMessenger = Messenger(gHandler())

    /**
     * Class for interacting with the main interface of the service.
     */
    private val gConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            gService = Messenger(service)
            try {
                // Tell the service we have started
                val msg = Message.obtain(null,
                    TYPE_CLIENT_STARTED, 0, 0)
                msg.replyTo = gMessenger
                gService!!.send(msg)
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
            gIsBound = true
            logi("Remote service connected")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            gService = null
            unbindRemoteService()
            logi("Remote service disconnected")
        }
    }

    fun bindRemoteService() {
        val intent = Intent()
        intent.component = ComponentName(gotify_package , messenger_service)
        context.bindService(intent, gConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindRemoteService() {
        if (gIsBound) {
            // Detach our existing connection.
            context.unbindService(gConnection)
            gIsBound = false
        }
    }

    fun registerApp(serviceName: String){
        if(!gIsBound){
            logw("You need to bind fisrt")
            return
        }
        try {
            val msg = Message.obtain(null,
                TYPE_REGISTER_CLIENT, 0, 0)
            msg.replyTo = gMessenger
            msg.data = bundleOf("package" to context.packageName, "service" to serviceName)
            waitingForInfo = true
            gService!!.send(msg)
        } catch (e: RemoteException) {
            waitingForInfo = false
            // There is nothing special we need to do if the service
            // has crashed.
        }
    }

    fun unregisterApp(){
        if(!gIsBound){
            logw("You need to bind first")
            return
        }
        try {
            val msg = Message.obtain(null,
                TYPE_UNREGISTER_CLIENT, 0, 0)
            msg.replyTo = gMessenger
            msg.data = bundleOf("package" to context.packageName)
            gService!!.send(msg)
        } catch (e: RemoteException) {
            // There is nothing special we need to do if the service
            // has crashed.
        }
    }
}