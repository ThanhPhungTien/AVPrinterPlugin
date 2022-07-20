package vn.anvui.avprinter

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Activity.RESULT_OK
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import org.json.JSONArray
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


/** AvprinterPlugin */
class AvprinterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {
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
    private val myUUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Volatile
    var stopWorker = false
    private var isConnected = false
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private var thread: Thread? = null
    private var address = ""

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {

        channel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "avprinter")
        channel.setMethodCallHandler(this)
        val pm: PackageManager =
            flutterPluginBinding.applicationContext.packageManager
        val hasBluetooth: Boolean =
            pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        if (hasBluetooth) {
            val bluetoothManager = flutterPluginBinding.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
        }

        mBTDevices = ArrayList()
    }

    override fun onMethodCall(
        @NonNull call: MethodCall,
        @NonNull result: Result
    ) {


        when (call.method) {

            "getList" -> {
                val myListString = getPairedList()
                result.success(JSONArray(myListString).toString())
            }

            "connectDevice" -> {
                val address = call.argument<String>("address")

                try {
                    result.success(address?.let { connectDevice(it) })
                } catch (ex: Exception) {
                    result.success(false)
                    Log.e("ConnectDevice Error", ex.message.toString())
                    ex.printStackTrace()
                }

            }

            "printImage" -> {
                try {
                    val byteArray = call.argument<ByteArray>("byte")
                    val bitmap: Bitmap = BitmapFactory.decodeByteArray(
                        byteArray,
                        0,
                        byteArray!!.size
                    )
                    result.success(printPhoto(bitmap))
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

    private fun connectDevice(address: String): Boolean {
        this.address = address
        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
        if (bluetoothDevice != null) {
            Log.e("Bluetooth", "Has Device")
            pairedDevices = bluetoothAdapter.bondedDevices
            bluetoothDevice.createBond()
            isConnected = true
            openBluetoothPrinter(bluetoothDevice)
            return true
        }

        Log.e("Bluetooth", "No Device")
        return false
    }

    // Open Bluetooth Printer
    @Throws(IOException::class)
    fun openBluetoothPrinter(bluetoothDevice: BluetoothDevice) {
        try {
            bluetoothAdapter.cancelDiscovery()
            bluetoothSocket =
                bluetoothDevice.createRfcommSocketToServiceRecord(myUUID)
            bluetoothSocket.connect()
            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream
            beginListenData()
        } catch (exception: java.lang.Exception) {
            exception.printStackTrace()
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
                        ex.printStackTrace()
                    }
                }
            }
            thread!!.start()
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    // in ảnh từ dữ liệu bitmap truyền vào

    private fun printPhoto(bitmap: Bitmap): Boolean {

        if (!isConnected) {
            Log.e("Bluetooth", "is Not Connected then Connect $address")
            isConnected = connectDevice(address)
        }

        if (isConnected) {
            Log.e("Bluetooth", "Connected device")
            outputStream = bluetoothSocket.outputStream
            val command = Utils.decodeBitmap(bitmap)
            outputStream.write(command)

            outputStream.write(PrinterCommands.ESC_ALIGN_CENTER)
            Log.e("Bluetooth", "Print Photo done")
            return true
        }



        return false
    }

    // Ngắt kết nối với máy in
    @Throws(IOException::class)
    fun disconnectBT() {
        Log.e("Bluetooth", "Disconnect")
        try {
            stopWorker = true
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            bluetoothSocket.close()
            isConnected = false

            Log.e("Bluetooth", "Disconnect Done")
        } catch (ex: java.lang.Exception) {
            Log.e("Bluetooth", "Disconnect Error")
            ex.printStackTrace()
        }
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.activity.requestPermissions(
                arrayOf(BLUETOOTH_CONNECT), 6969,
            )

        }


    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {

    }

    override fun onDetachedFromActivity() {

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == 6969) {
            if (permissions.contains(BLUETOOTH_CONNECT) ) {
                return grantResults.contains(RESULT_OK)
            }
        }
        return false

    }


}
