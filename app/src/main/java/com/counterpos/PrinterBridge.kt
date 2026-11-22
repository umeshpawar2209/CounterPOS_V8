package com.counterpos

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Base64
import android.webkit.JavascriptInterface

/**
 * Sends raw ESC/POS bytes (already built by the POS web page) to a USB printer.
 * No third-party printing library: only the standard Android USB host API.
 */
class PrinterBridge(
    private val act: Activity,
    private val onStatus: (String) -> Unit
) {
    private val usb = act.getSystemService(Context.USB_SERVICE) as UsbManager
    private val action = "com.counterpos.USB_PERMISSION"
    private val lock = Any()
    @Volatile private var pending: ByteArray? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != action) return
            val dev: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                i.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                i.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val data = pending
            pending = null
            if (!granted) {
                onStatus("USB permission was not allowed")
            } else if (dev != null && data != null) {
                write(dev, data)
            }
        }
    }

    init {
        val f = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            act.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            act.registerReceiver(receiver, f)
        }
    }

    fun close() {
        try { act.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun pick(dev: UsbDevice, cls: Int): Pair<UsbInterface, UsbEndpoint>? {
        for (i in 0 until dev.interfaceCount) {
            val f = dev.getInterface(i)
            if (f.interfaceClass != cls) continue
            for (e in 0 until f.endpointCount) {
                val ep = f.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT
                ) return Pair(f, ep)
            }
        }
        return null
    }

    private fun find(): UsbDevice? {
        val all = usb.deviceList.values
        return all.firstOrNull { pick(it, UsbConstants.USB_CLASS_PRINTER) != null }
            ?: all.firstOrNull { pick(it, UsbConstants.USB_CLASS_VENDOR_SPEC) != null }
    }

    private fun write(dev: UsbDevice, data: ByteArray) {
        Thread {
            synchronized(lock) {
                var conn: android.hardware.usb.UsbDeviceConnection? = null
                var iface: UsbInterface? = null
                try {
                    val pe = pick(dev, UsbConstants.USB_CLASS_PRINTER)
                        ?: pick(dev, UsbConstants.USB_CLASS_VENDOR_SPEC)
                    if (pe == null) {
                        onStatus("Printer has no output endpoint")
                        return@synchronized
                    }
                    iface = pe.first
                    val ep = pe.second
                    conn = usb.openDevice(dev)
                    if (conn == null) {
                        onStatus("Cannot open the printer")
                        return@synchronized
                    }
                    if (!conn.claimInterface(iface, true)) {
                        onStatus("Cannot claim the printer interface")
                        return@synchronized
                    }
                    var off = 0
                    while (off < data.size) {
                        val len = minOf(4096, data.size - off)
                        val n = conn.bulkTransfer(ep, data, off, len, 5000)
                        if (n <= 0) {
                            onStatus("Printing failed (USB write error)")
                            return@synchronized
                        }
                        off += n
                    }
                } catch (e: Exception) {
                    onStatus("Printing failed: " + (e.message ?: "error"))
                } finally {
                    try { if (conn != null && iface != null) conn.releaseInterface(iface) } catch (_: Exception) {}
                    try { conn?.close() } catch (_: Exception) {}
                }
            }
        }.start()
    }

    @JavascriptInterface
    fun isReady(): Boolean = find() != null

    @JavascriptInterface
    fun name(): String = find()?.productName ?: "USB printer"

    /** Returns "ok" when the job was accepted, otherwise an error message for the user. */
    @JavascriptInterface
    fun print(b64: String): String {
        val data = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (e: Exception) {
            return "Bad print data"
        }
        val dev = find() ?: return "No USB printer found. Connect it with the OTG cable and try again."
        if (usb.hasPermission(dev)) {
            write(dev, data)
            return "ok"
        }
        pending = data
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(act, 0, Intent(action).setPackage(act.packageName), flags)
        usb.requestPermission(dev, pi)
        return "ok"
    }

    @JavascriptInterface
    fun share(text: String) {
        act.runOnUiThread {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            act.startActivity(Intent.createChooser(i, "Share"))
        }
    }
}
