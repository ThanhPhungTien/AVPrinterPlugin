import 'dart:developer';

import 'package:avprinter/device/device_cubit.dart';
import 'package:avprinter/enum.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

class DeviceView extends StatefulWidget {
  const DeviceView({Key? key}) : super(key: key);

  @override
  State<DeviceView> createState() => _DeviceViewState();
}

class _DeviceViewState extends State<DeviceView> {
  DeviceCubit bloc = DeviceCubit();

  @override
  void initState() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      bloc.getDevices();
    });
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      child: BlocBuilder<DeviceCubit, DeviceState>(
        bloc: bloc,
        builder: (context, state) {
          if (state is DeviceStateNormal) {
            log('address ${state.address}');
            return ListView.builder(
              padding: const EdgeInsets.all(8),
              shrinkWrap: true,
              itemCount: state.divices.length,
              itemBuilder: (BuildContext context, int index) {
                BluetoothObject item = state.divices[index];
                return Material(
                  color: Colors.white,
                  elevation: 2,
                  child: ListTile(
                    onTap: () {
                      bloc.selectDivice(item.address);
                    },
                    selected: item.address == state.address,
                    selectedColor: Colors.blue,
                    title: Text(item.name),
                    subtitle: Text(item.address),
                  ),
                );
              },
            );
          } else if (state is DeviceStateError) {
            return Center(
              child: Column(
                children: [
                  Text(state.message),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () => bloc.getDevices(),
                    child: Text('Thử lại'),
                  ),
                ],
              ),
            );
          }
          return Center(
            child: CircularProgressIndicator(),
          );
        },
      ),
    );
  }
}
