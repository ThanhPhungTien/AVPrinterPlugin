import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:avprinter/enum.dart';
import 'package:flutter/services.dart';

class Avprinter {
  static const MethodChannel _channel = const MethodChannel('avprinter');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<List<String>> get getListDevices async {
    final String devices = await _channel.invokeMethod(PrinterMethod.getList);
    return List.castFrom(json.decode(devices));
  }

  static Future<bool> connectDevice(String address) async {
    final bool check = await _channel.invokeMethod(
        PrinterMethod.connectDevice, <String, dynamic>{'address': address});
    return check ?? false;
  }

  static Future<bool> printImage(Uint8List byte) async {
    final bool check = await _channel.invokeMethod<dynamic>(
        PrinterMethod.printImage, <String, dynamic>{'byte': byte});
    return check ?? false;
  }
  static Future<bool> checkConnection() async {
    final bool check = await _channel.invokeMethod<dynamic>(
        PrinterMethod.checkConnection);
    return check ?? false;
  }

  static Future<bool> disconnectBT() async {
    final bool check = await _channel.invokeMethod<dynamic>(
        PrinterMethod.disconnectBT);
    return check ?? false;
  }
  
  
}
