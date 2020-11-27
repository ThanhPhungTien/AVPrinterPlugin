package vn.anvui.avprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONArray
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


/** AvprinterPlugin */
class AvprinterPlugin : FlutterPlugin, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var pairedDevices: Set<BluetoothDevice>
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var mBTDevices = ArrayList<BluetoothDevice>()
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var outputStream: OutputStream
    private lateinit var inputStream: InputStream

    @Volatile
    var stopWorker = false
    var isConnected = false

    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private var thread: Thread? = null


    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "avprinter")

        channel.setMethodCallHandler(this)
        val pm: PackageManager = flutterPluginBinding.applicationContext.packageManager
        val hasBluetooth: Boolean = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        if (hasBluetooth) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        }
        mBTDevices = ArrayList()
    }


    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {

            "getList" -> {
                val myListString = getPairedList()
                result.success(JSONArray(myListString).toString())
            }

            "connectDevice" -> {
                val address = call.argument<String>("address")

                try {
                    connectDevice(address!!, mBTDevices)
                    result.success(true)
                } catch (ex: Exception) {
                    result.success(false)
                    ex.printStackTrace()
                }

            }
            "printImage" -> {
                try {
                    val byteArray = call.argument<ByteArray>("byte")
                    val bitmap: Bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
                    printPhoto(bitmap)
                    result.success(true)
                } catch (e: java.lang.Exception) {
                    result.success(false)
                    e.printStackTrace()
                    Log.e("PrintTools", "the file isn't exists")
                }

            }
            "checkConnection" -> {
                result.success(checkConnection())
            }

            "disconnectBT" -> {
                disconnectBT()
            }


        }

    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        disconnectBT()
    }

    private fun checkConnection(): Boolean {
        return isConnected
    }

    private fun getPairedList(): ArrayList<String> {
        pairedDevices = bluetoothAdapter.bondedDevices
        val list = ArrayList<String>()
        return if (pairedDevices.isEmpty()) {
            list
        } else {
            for (bt in pairedDevices as MutableSet<BluetoothDevice>) {
                list.add("{\"name\":\"" + bt.name + "\",\"address\":\"" + bt.address + "\"}")
                mBTDevices.add(bt)
            }
            list
        }

    }

    // chọn thiết bị trong danh sách đã từng kết nối
    private fun connectDevice(address: String, mBTDevices: ArrayList<BluetoothDevice>) {
        bluetoothAdapter.cancelDiscovery()
        pairedDevices = bluetoothAdapter.bondedDevices
        val index = mBTDevices.indexOfFirst { it.address == address }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mBTDevices[index].createBond()
            isConnected=true
        }
        openBluetoothPrinter(mBTDevices[index])

    }


    // Open Bluetooth Printer
    @Throws(IOException::class)
    fun openBluetoothPrinter(bluetoothDevice: BluetoothDevice) {
        try {
            //Standard uuid from string //
            val uuidSting = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting)
            bluetoothSocket.connect()
            outputStream = bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting).outputStream
            inputStream = bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting).inputStream
            beginListenData()
        } catch (exception: java.lang.Exception) {
            throw  IOException(exception)
        }
    }






    // tạo cổng nghe từ điện thoại với máy in bluetooth
    private fun beginListenData() {
        try {
            stopWorker = false
            readBufferPosition = 0
            readBuffer = ByteArray(1024)
            thread = Thread {
                while (!Thread.currentThread().isInterrupted && !stopWorker) {
                    try {
                        val byteAvailable = inputStream.available()
                        if (byteAvailable > 0) {
                            val packetByte = ByteArray(byteAvailable)
                            inputStream.read(packetByte)
                            for (i in 0 until byteAvailable) {
                                val encodedByte = ByteArray(readBufferPosition)
                                System.arraycopy(
                                        readBuffer, 0,
                                        encodedByte, 0,
                                        encodedByte.size
                                )
                                readBufferPosition = 0
                            }
                        }
                    } catch (ex: java.lang.Exception) {
                        stopWorker = true
                    }
                }
            }
            thread!!.start()
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    // in ảnh từ dữ liệu bitmap truyền vào

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun printPhoto(bitmap: Bitmap) {
        outputStream = bluetoothSocket.outputStream
        val command = Utils.decodeBitmap(bitmap)
        outputStream.write(command)
        outputStream.write(PrinterCommands.ESC_ALIGN_CENTER)

    }

    // Ngắt kết nối với máy in
    @Throws(IOException::class)
    fun disconnectBT() {
        try {
            stopWorker = true
            outputStream.close()
            inputStream.close()
            bluetoothSocket.close()
            isConnected = false
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }
}
