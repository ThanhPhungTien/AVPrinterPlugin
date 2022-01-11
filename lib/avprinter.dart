import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:avprinter/enum.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';

class AVPrinter {
  static const MethodChannel _channel = const MethodChannel('avprinter');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<List<BluetoothObject>> get getListDevices async {
    final String devices =
        await (_channel.invokeMethod(PrinterMethod.getList) as Future<String>);

    final List<dynamic> devicesJson = json.decode(devices);

    print('devicesJson => $devicesJson');
    return devicesJson
        .map<BluetoothObject>(
            (dynamic item) => BluetoothObject.fromJson(json.decode(item)))
        .toList();
  }

  static Future<bool> connectDevice(String address) async {
    final bool check = await _channel.invokeMethod(
        PrinterMethod.connectDevice, <String, dynamic>{'address': address});
    return check;
  }

  static Future<bool> printImage(Uint8List byte) async {
    final bool check = await (_channel.invokeMethod<dynamic>(
            PrinterMethod.printImage, <String, dynamic>{'byte': byte})
        as Future<bool>);
    return check;
  }

  static Future<bool> checkConnection() async {
    final bool check = await (_channel
        .invokeMethod<dynamic>(PrinterMethod.checkConnection) as Future<bool>);
    return check;
  }

  static Future<bool> disconnectBT() async {
    final bool check = await (_channel
        .invokeMethod<dynamic>(PrinterMethod.disconnectBT) as Future<bool>);
    return check;
  }

  /// Hàm chuyển Widget thành Uint8List
  static Future<Uint8List?> capturePng(GlobalKey globalKey) async {
    final RenderRepaintBoundary boundary =
        globalKey.currentContext?.findRenderObject() as RenderRepaintBoundary;
    final ui.Image image = await boundary.toImage();
    final ByteData? byteData = await (image.toByteData(
      format: ui.ImageByteFormat.png,
    ));
    final Uint8List? pngBytes = byteData?.buffer.asUint8List();
    return pngBytes;
  }

  /// Hàm tạo ảnh từ widget
  static Future<Uint8List?> createImageFromWidget(
    Widget widget, {
    Duration? wait,
    Size? logicalSize,
    Size? imageSize,
  }) async {
    final RenderRepaintBoundary repaintBoundary = RenderRepaintBoundary();

    logicalSize ??= ui.window.physicalSize / ui.window.devicePixelRatio;
    imageSize ??= ui.window.physicalSize;

    assert(logicalSize.aspectRatio == imageSize.aspectRatio);

    final RenderView renderView = RenderView(
      child: RenderPositionedBox(
        alignment: Alignment.center,
        child: repaintBoundary,
      ),
      configuration: ViewConfiguration(
        size: logicalSize,
        devicePixelRatio: 1.0,
      ),
      window: ui.window,
    );

    final PipelineOwner pipelineOwner = PipelineOwner();
    final BuildOwner buildOwner = BuildOwner();

    pipelineOwner.rootNode = renderView;
    renderView.prepareInitialFrame();

    final RenderObjectToWidgetElement<RenderBox> rootElement =
        RenderObjectToWidgetAdapter<RenderBox>(
            container: repaintBoundary,
            child: Directionality(
              textDirection: TextDirection.ltr,
              child: widget,
            )).attachToRenderTree(buildOwner);

    buildOwner.buildScope(rootElement);

    if (wait != null) {
      await Future<dynamic>.delayed(wait);
    }

    buildOwner.buildScope(rootElement);
    buildOwner.finalizeTree();

    pipelineOwner.flushLayout();
    pipelineOwner.flushCompositingBits();
    pipelineOwner.flushPaint();

    final ui.Image image = await repaintBoundary.toImage(
        pixelRatio: imageSize.width / logicalSize.width);
    final ByteData? byteData =
        await (image.toByteData(format: ui.ImageByteFormat.png));

    return byteData?.buffer.asUint8List();
  }
}
