part of 'device_cubit.dart';

@immutable
abstract class DeviceState {}

class DeviceStateLoading extends DeviceState {}

class DeviceStateNormal extends DeviceState {
  final List<BluetoothObject> divices;
  final String address;

  DeviceStateNormal(this.divices, this.address);
}

class DeviceStateError extends DeviceState {
  final String message;

  DeviceStateError(this.message);
}
