package com.example.beacon;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "MainActivity";

    private BluetoothLeScanner bluetoothLeScanner;
    private TextView locationTextView;
    private TextView debugTextView;
    private Point previousPosition = new Point(0, 0); // 초기 위치

    // 비콘들의 MAC 주소와 위치, txPower 설정
    private final BeaconInfo beacon1 = new BeaconInfo(new Point(0, 0), -59, "C3:00:00:19:2F:4B");
    private final BeaconInfo beacon2 = new BeaconInfo(new Point(5, 0), -59, "C3:00:00:19:2F:33"); // 두 번째 비콘의 MAC 주소 입력
    private final BeaconInfo beacon3 = new BeaconInfo(new Point(2.5, 5), -59, "C3:00:00:19:2F:34"); // 세 번째 비콘의 MAC 주소 입력

    private final Map<String, BeaconInfo> beaconsMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        locationTextView = findViewById(R.id.locationTextView);
        debugTextView = findViewById(R.id.debugTextView);

        // 비콘 정보 맵에 추가
        beaconsMap.put(beacon1.macAddress, beacon1);
        beaconsMap.put(beacon2.macAddress, beacon2);
        beaconsMap.put(beacon3.macAddress, beacon3);

        // 위치 권한 확인 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_FINE_LOCATION);
        } else {
            initBluetoothScanner();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initBluetoothScanner();
            } else {
                Log.d(TAG, "Permissions not granted");
            }
        }
    }

    private void initBluetoothScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted for Bluetooth scanning");
            return;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Log.d(TAG, "Bluetooth not supported on this device");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                Log.d(TAG, "BluetoothLeScanner initialized");
                startScanning();
            } else {
                Log.d(TAG, "Bluetooth LE not supported on this device");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            initBluetoothScanner();
        }
    }

    private void startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted for Bluetooth scanning");
            return;
        }

        try {
            List<ScanFilter> filters = new ArrayList<>();
            for (String macAddress : beaconsMap.keySet()) {
                filters.add(new ScanFilter.Builder().setDeviceAddress(macAddress).build());
            }

            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            bluetoothLeScanner.startScan(filters, scanSettings, scanCallback);
            Log.d(TAG, "Started scanning for beacons");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission not granted for Bluetooth scanning", e);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            String deviceAddress = result.getDevice().getAddress();
            int rssi = result.getRssi();

            Log.d(TAG, "Found beacon: " + deviceAddress + " with RSSI: " + rssi);

            // 비콘의 MAC 주소를 기반으로 거리 계산
            BeaconInfo beaconInfo = beaconsMap.get(deviceAddress);
            if (beaconInfo != null) {
                double distance = calculateDistance(beaconInfo.txPower, rssi);
                beaconInfo.distance = distance;
                beaconInfo.rssi = rssi;

                Log.d(TAG, "Updated beacon: " + deviceAddress + " distance: " + distance);
                // 모든 비콘의 거리와 RSSI 정보를 UI에 업데이트
                updateUI();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "Scan failed with error: " + errorCode);
        }
    };

    private void updateUI() {
        StringBuilder debugInfo = new StringBuilder();
        for (BeaconInfo beaconInfo : beaconsMap.values()) {
            debugInfo.append("Beacon: ").append(beaconInfo.macAddress)
                    .append(", RSSI: ").append(beaconInfo.rssi)
                    .append(", Distance: ").append(String.format("%.2f", beaconInfo.distance)).append("\n");
        }

        // UI 업데이트
        runOnUiThread(() -> debugTextView.setText(debugInfo.toString()));

        if (beaconsMap.values().stream().allMatch(b -> b.distance != -1)) {
            double distance1 = beaconsMap.get(beacon1.macAddress).distance;
            double distance2 = beaconsMap.get(beacon2.macAddress).distance;
            double distance3 = beaconsMap.get(beacon3.macAddress).distance;

            Point userPosition = trilateration(beacon1.position, distance1, beacon2.position, distance2, beacon3.position, distance3);
            double movedDistance = calculateDistance(previousPosition, userPosition);
            double movedDirection = calculateBearing(previousPosition, userPosition);
            previousPosition = userPosition;

            runOnUiThread(() -> {
                locationTextView.setText(
                        getString(R.string.location_format, userPosition.x, userPosition.y, movedDistance, movedDirection));
            });

            Log.d(TAG, "Updated user position: (" + userPosition.x + ", " + userPosition.y + ")");
        }
    }

    public double calculateDistance(int txPower, double rssi) {
        if (rssi == 0) {
            return -1.0; // 신호가 없으면 거리 측정 불가
        }
        // 여기서 txPower와 RSSI는 부호가 반대이므로, 절대값을 사용하여 비율을 계산합니다.
        double ratio = Math.abs(rssi) * 1.0 / Math.abs(txPower);
        double distance;
        if (ratio < 1.0) {
            distance = Math.pow(ratio, 10);
        } else {
            distance = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
        }
        Log.d(TAG, "Calculated distance: " + distance);
        return distance;
    }

    public Point trilateration(Point p1, double d1, Point p2, double d2, Point p3, double d3) {
        double A = 2 * p2.x - 2 * p1.x;
        double B = 2 * p2.y - 2 * p1.y;
        double C = Math.pow(d1, 2) - Math.pow(d2, 2) - Math.pow(p1.x, 2) + Math.pow(p2.x, 2) - Math.pow(p1.y, 2) + Math.pow(p2.y, 2);
        double D = 2 * p3.x - 2 * p2.x;
        double E = 2 * p3.y - 2 * p2.y;
        double F = Math.pow(d2, 2) - Math.pow(d3, 2) - Math.pow(p2.x, 2) + Math.pow(p3.x, 2) - Math.pow(p2.y, 2) + Math.pow(p3.y, 2);

        double x = (C * E - F * B) / (E * A - B * D);
        double y = (C * D - A * F) / (B * D - A * E);

        // Add a fallback mechanism in case of NaN values
        if (Double.isNaN(x) || Double.isNaN(y)) {
            Log.d(TAG, "Trilateration resulted in NaN coordinates, using fallback position");
            x = (p1.x + p2.x + p3.x) / 3;
            y = (p1.y + p2.y + p3.y) / 3;
        }

        return new Point(x, y);
    }

    public double calculateDistance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    public double calculateBearing(Point p1, Point p2) {
        double angle = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
        return angle >= 0 ? angle : angle + 360;
    }

    static class Point {
        double x, y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class BeaconInfo {
        Point position;
        int txPower;
        String macAddress;
        double distance = -1; // 거리 초기값
        int rssi; // RSSI 값

        public BeaconInfo(Point position, int txPower, String macAddress) {
            this.position = position;
            this.txPower = txPower;
            this.macAddress = macAddress;
        }
    }
}
