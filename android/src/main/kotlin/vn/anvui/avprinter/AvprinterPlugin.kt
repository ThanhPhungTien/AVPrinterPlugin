package vn.anvui.avprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.NonNull
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
    private val myUUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
//    private val starPosAddress = "11:22:33:44:55:66"
//    private val sunmiAddress = "00:11:22:33:44:55"

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
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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

//    private fun getDevice(): BluetoothDevice? {
//
//        var device: BluetoothDevice? = getSunmiDevice()
//
//        if (device == null) {
//            Log.e("getDevice", "deviceNUll")
//
//            device = getStarPosDevice()
//        }
//
//        return device
//    }

//    private fun getSunmiDevice(): BluetoothDevice? {
//        return try {
//            bluetoothAdapter.getRemoteDevice(sunmiAddress)
//        } catch (e: java.lang.Exception) {
//            null
//        }
//    }
//
//    private fun getStarPosDevice(): BluetoothDevice? {
//        return try {
//            bluetoothAdapter.getRemoteDevice(starPosAddress)
//        } catch (e: java.lang.Exception) {
//            null
//        }
//    }

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
            isConnected = connectDevice(address)
        }

        if (isConnected) {
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


}
//
//object Utils {
//    // UNICODE 0x23 = #
//    val UNICODE_TEXT = byteArrayOf(
//        0x23, 0x23, 0x23,
//        0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
//        0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x23,
//        0x23, 0x23, 0x23
//    )
//    private const val hexStr = "0123456789ABCDEF"
//    private val binaryArray = arrayOf(
//        "0000", "0001", "0010", "0011",
//        "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
//        "1100", "1101", "1110", "1111"
//    )
//
//    fun decodeBitmap(bmp: Bitmap): ByteArray? {
//        val bmpWidth = bmp.width
//        val bmpHeight = bmp.height
//        val list: MutableList<String> =
//            java.util.ArrayList() //binaryString list
//        var sb: StringBuffer
//        var bitLen = bmpWidth / 8
//        val zeroCount = bmpWidth % 8
//        val zeroStr = StringBuilder()
//        if (zeroCount > 0) {
//            bitLen = bmpWidth / 8 + 1
//            for (i in 0 until 8 - zeroCount) {
//                zeroStr.append("0")
//            }
//        }
//        for (i in 0 until bmpHeight) {
//            sb = StringBuffer()
//            for (j in 0 until bmpWidth) {
//                val color = bmp.getPixel(j, i)
//                val r = color shr 16 and 0xff
//                val g = color shr 8 and 0xff
//                val b = color and 0xff
//
//                // if color close to white，bit='0', else bit='1'
//                if (r > 160 && g > 160 && b > 160) sb.append("0") else sb.append(
//                    "1"
//                )
//            }
//            if (zeroCount > 0) {
//                sb.append(zeroStr)
//            }
//            list.add(sb.toString())
//        }
//        val bmpHexList = binaryListToHexStringList(list)
//        val commandHexString = "1D763000"
//        var widthHexString = Integer
//            .toHexString(if (bmpWidth % 8 == 0) bmpWidth / 8 else bmpWidth / 8 + 1)
//        if (widthHexString.length > 2) {
//            Log.e("decodeBitmap error", " width is too large")
//            return null
//        } else if (widthHexString.length == 1) {
//            widthHexString = "0$widthHexString"
//        }
//        widthHexString += "00"
//        var heightHexString = Integer.toHexString(bmpHeight)
//        if (heightHexString.length > 2) {
//            Log.e("decodeBitmap error", " height is too large")
//            return null
//        } else if (heightHexString.length == 1) {
//            heightHexString = "0$heightHexString"
//        }
//        heightHexString += "00"
//        val commandList: MutableList<String> = java.util.ArrayList()
//        commandList.add(commandHexString + widthHexString + heightHexString)
//        commandList.addAll(bmpHexList)
//        return hexList2Byte(commandList)
//    }
//
//    fun binaryListToHexStringList(list: List<String>): List<String> {
//        val hexList: MutableList<String> = java.util.ArrayList()
//        for (binaryStr in list) {
//            val sb = StringBuilder()
//            var i = 0
//            while (i < binaryStr.length) {
//                val str = binaryStr.substring(i, i + 8)
//                val hexString = myBinaryStrToHexString(str)
//                sb.append(hexString)
//                i += 8
//            }
//            hexList.add(sb.toString())
//        }
//        return hexList
//    }
//
//    fun myBinaryStrToHexString(binaryStr: String): String {
//        val hex = StringBuilder()
//        val f4 = binaryStr.substring(0, 4)
//        val b4 = binaryStr.substring(4, 8)
//        for (i in binaryArray.indices) {
//            if (f4 == binaryArray[i]) hex.append(hexStr[i])
//        }
//        for (i in binaryArray.indices) {
//            if (b4 == binaryArray[i]) hex.append(hexStr[i])
//        }
//        return hex.toString()
//    }
//
//    fun hexList2Byte(list: List<String>): ByteArray {
//        val commandList: MutableList<ByteArray?> = java.util.ArrayList()
//        for (hexStr in list) {
//            commandList.add(hexStr.decodeHex())
//        }
//        return sysCopy(commandList)
//    }
//
//    private fun String.decodeHex(): ByteArray {
//        check(length % 2 == 0) { "Must have an even length" }
//
//        return chunked(2)
//            .map { it.toInt(16).toByte() }
//            .toByteArray()
//    }
//
////    fun hexStringToBytes(hexString: String?): ByteArray? {
////
////        var hexString = hexString
////        if (hexString == null || hexString == "") {
////            return null
////        }
////        hexString = hexString.toUpperCase()
////        val length = hexString.length / 2
////        val hexChars = hexString.toCharArray()
////        val d = ByteArray(length)
////        for (i in 0 until length) {
////            val pos = i * 2
////            d[i] = (charToByte(hexChars[pos]) shl 4 or charToByte(
////                hexChars[pos + 1]
////            )) as Byte
////        }
////        return d
////    }
//
//    fun sysCopy(srcArrays: List<ByteArray?>): ByteArray {
//        var len = 0
//        for (srcArray in srcArrays) {
//            len += srcArray!!.size
//        }
//        val destArray = ByteArray(len)
//        var destLen = 0
//        for (srcArray in srcArrays) {
//            System.arraycopy(srcArray, 0, destArray, destLen, srcArray!!.size)
//            destLen += srcArray.size
//        }
//        return destArray
//    }
//
//    private fun charToByte(c: Char): Byte {
//        return "0123456789ABCDEF".indexOf(c).toByte()
//    }
//}
//
//
//object PrinterCommands {
//    const val HT: Byte = 0x9
//    const val LF: Byte = 0x0A
//    const val CR: Byte = 0x0D
//    const val FF: Byte = 0x0C
//    const val ESC: Byte = 0x1B
//    const val DLE: Byte = 0x10
//    const val GS: Byte = 0x1D
//    const val FS: Byte = 0x1C
//    const val STX: Byte = 0x02
//    const val US: Byte = 0x1F
//    const val CAN: Byte = 0x18
//    const val CLR: Byte = 0x0C
//    const val EOT: Byte = 0x04
//    val INIT = byteArrayOf(27, 64)
//    var FEED_LINE = byteArrayOf(10)
//    var SELECT_FONT_A = byteArrayOf(20, 33, 0)
//    var SET_BAR_CODE_HEIGHT = byteArrayOf(29, 104, 100)
//    var PRINT_BAR_CODE_1 = byteArrayOf(29, 107, 2)
//    var SEND_NULL_BYTE = byteArrayOf(0x00)
//    var SELECT_PRINT_SHEET = byteArrayOf(0x1B, 0x63, 0x30, 0x02)
//    var FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 66, 0x00)
//    var SELECT_CYRILLIC_CHARACTER_CODE_TABLE = byteArrayOf(0x1B, 0x74, 0x11)
//    var SELECT_BIT_IMAGE_MODE = byteArrayOf(0x1B, 0x2A, 33, -128, 0)
//    var SET_LINE_SPACING_24 = byteArrayOf(0x1B, 0x33, 24)
//    var SET_LINE_SPACING_30 = byteArrayOf(0x1B, 0x33, 30)
//    var TRANSMIT_DLE_PRINTER_STATUS = byteArrayOf(0x10, 0x04, 0x01)
//    var TRANSMIT_DLE_OFFLINE_PRINTER_STATUS = byteArrayOf(0x10, 0x04, 0x02)
//    var TRANSMIT_DLE_ERROR_STATUS = byteArrayOf(0x10, 0x04, 0x03)
//    var TRANSMIT_DLE_ROLL_PAPER_SENSOR_STATUS = byteArrayOf(0x10, 0x04, 0x04)
//    val ESC_FONT_COLOR_DEFAULT = byteArrayOf(0x1B, 'r'.toByte(), 0x00)
//    val FS_FONT_ALIGN = byteArrayOf(
//        0x1C, 0x21, 1, 0x1B,
//        0x21, 1
//    )
//    val ESC_ALIGN_LEFT = byteArrayOf(0x1b, 'a'.toByte(), 0x00)
//    val ESC_ALIGN_RIGHT = byteArrayOf(0x1b, 'a'.toByte(), 0x02)
//    val ESC_ALIGN_CENTER = byteArrayOf(0x1b, 'a'.toByte(), 0x01)
//    val ESC_CANCEL_BOLD = byteArrayOf(0x1B, 0x45, 0)
//
//    /** */
//    val ESC_HORIZONTAL_CENTERS = byteArrayOf(0x1B, 0x44, 20, 28, 0)
//    val ESC_CANCLE_HORIZONTAL_CENTERS = byteArrayOf(0x1B, 0x44, 0)
//
//    /** */
//    val ESC_ENTER = byteArrayOf(0x1B, 0x4A, 0x40)
//    val PRINTE_TEST = byteArrayOf(0x1D, 0x28, 0x41)
//}
//
//
