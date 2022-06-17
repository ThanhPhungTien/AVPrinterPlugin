import 'package:avprinter/avprinter.dart';
import 'package:avprinter/enum.dart';
import 'package:bloc/bloc.dart';
import 'package:meta/meta.dart';

part 'device_state.dart';

class DeviceCubit extends Cubit<DeviceState> {
  DeviceCubit() : super(DeviceStateLoading());

  Future<void> getDevices() async {
    try {
      emit(DeviceStateLoading());
      List<BluetoothObject> data = await AVPrinter.getListDevices;
      emit(DeviceStateNormal(data));
    } on Exception catch (e) {
      emit(DeviceStateError(e.toString()));
    }
  }
}
