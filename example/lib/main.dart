import 'package:avprinter/device/device_view.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: DeviceView(),
    );
  }

  void open() {
    print('haha');
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (BuildContext context) => const DeviceView(),
      ),
    );
  }
}
