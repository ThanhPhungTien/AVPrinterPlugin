import 'package:json_annotation/json_annotation.dart';

class PrinterMethod {
  static const String getList = "getList";
  static const String printImage = "printImage";
  static const String connectDevice = "connectDevice";
  static const String checkConnection = "checkConnection";
  static const String disconnectBT = "disconnectBT";
}

@JsonSerializable(explicitToJson: true)
class BluetoothObject {
  BluetoothObject({this.name, this.address});

  factory BluetoothObject.fromJson(Map<String, dynamic> data) {
    if (data == null) return BluetoothObject();
    return BluetoothObject(name: data['name'], address: data['address']);
  }
  final String name;
  final String address;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'name': name,
      'address': address,
    };
  }
}
