import 'package:avprinter/avprinter.dart';
import 'package:avprinter/enum.dart';
import 'package:bloc/bloc.dart';
import 'package:meta/meta.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'device_state.dart';

class DeviceCubit extends Cubit<DeviceState> {
  DeviceCubit() : super(DeviceStateLoading());

  List<BluetoothObject> _data = <BluetoothObject>[];

  Future<void> getDevices() async {
    try {
      emit(DeviceStateLoading());
      _data = await AVPrinter.getListDevices;
      SharedPreferences prefs = await SharedPreferences.getInstance();
      String address = prefs.getString(PrinterConstant.selectDivice) ?? '';
      emit(DeviceStateNormal(_data, address));
    } on Exception catch (e) {
      emit(DeviceStateError(e.toString()));
    }
  }

  Future<void> selectDivice(String address) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    prefs.setString(PrinterConstant.selectDivice, address);
    emit(DeviceStateNormal(_data, address));
  }
}
