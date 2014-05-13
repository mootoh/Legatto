package net.mootoh.btwithios.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Created by takayama.motohiro on 5/14/14.
 */
public class XNBrowser {
    static final String NOTIFIER_UUID = "42015324-6E63-412D-9B7F-257024D56460";
    static final String SERVICE_UUID = "688C7F90-F424-4BC0-8508-AEDE43A4288D";
    static final String CONTROLLER_UUID = "5AE0C50F-2C8E-4336-AEAC-F1AF0A325006";
    static final long SCAN_PERIOD = 10000;

    private final Context context_;
    private BluetoothAdapter bluetoothAdapter_;
    private Handler handler_ = new Handler();
    private boolean scanning_ = false;

    public XNBrowser(final Context context) {
        this.context_ = context;
        prepareBluetooth();
    }

    private boolean prepareBluetooth() {
        if (!context_.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(context_, "BT not supported 1", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) context_.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter_ = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter_ == null) {
            Toast.makeText(context_, "BT not supported 2", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    public boolean isEnabled() {
        return bluetoothAdapter_.isEnabled();
    }

    public void startScan() {
        UUID[] uuidToScan = { UUID.fromString(SERVICE_UUID) };

        // stop the discovery when timeout
        handler_.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, SCAN_PERIOD);

        bluetoothAdapter_.startLeScan(leScanCallback_);
        scanning_ = true;

    }

    void stopScan() {
        bluetoothAdapter_.stopLeScan(leScanCallback_);
        scanning_ = false;
    }


    BluetoothAdapter.LeScanCallback leScanCallback_ = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (! scanning_)
                return;
            if (device.getName().equals("btbt")) {
                Log.d("@@@", "found BT device: " + device.getAddress() + " " + device.getName());
                stopScan();
                connect(device);
            }
        }
    };


    protected void connect(BluetoothDevice device) {
        device.connectGatt(context_, false, new BluetoothGattCallback() {
            BluetoothGattService service_;

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d("###", "got connection state changed: " + status + ", " + newState);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("###", "GATT success");

                } else if (status == BluetoothGatt.GATT_FAILURE) {
                    Log.d("###", "GATT failure");
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("###", "GATT connected");
                    gatt.discoverServices();
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("###", "GATT disconnected");
                }
            }

            @Override
            // New services discovered
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("###", "service discovered");
                    for (final BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().equals(UUID.fromString(SERVICE_UUID))) {
                            Log.d("###", "found iPhone!");
                            service_ = service;

                            openPortForOutput(service, gatt);
                            openPortForInput(service, gatt);
                        }
                    }
                } else {
                    Log.w("###", "onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicRead " + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] value = characteristic.getValue();
                    int x = value[0];
                    Log.d("###", "value = " + x);
                    if (x == 1) {
                        Log.d("###", "inputPortConnected!!!");
                        inputPortConnected = true;
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                    Log.d("###", "onCharacteristicWrite: write not permitted");
                    return;
                }
                Log.d("###", "onCharacteristicWrite " + status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d("###", "onCharacteristicChanged ");
                byte[] value = characteristic.getValue();
                int x = value[0];
                Log.d("###", "value = " + x);
            }
        });
    }

    private boolean inputPortConnected = false;
    static int counter = 0;

    private void openPortForInput(final BluetoothGattService service, final BluetoothGatt gatt) {
        if (inputPortConnected)
            return;

        handler_.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (counter++ % 2 == 0)
                    tryRead();
                else
                    tryObserve();
                openPortForInput(service, gatt);
            }

            private void tryRead() {
                BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(CONTROLLER_UUID));
                if (chr == null) {
                    Log.d("###", "no such characteristic for read:" + CONTROLLER_UUID);
                    return;
                }

                boolean hasRead = gatt.readCharacteristic(chr);
                if (!hasRead) {
                    Log.d("###", "failed in read request");
                }
            }

            private void tryObserve() {
                BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(NOTIFIER_UUID));
                if (! gatt.setCharacteristicNotification(chr, true)) {
                    throw new RuntimeException("failed to setCharacteristicNotification to gatt");
                }

                boolean enabled = false;
                for (BluetoothGattDescriptor descriptor : chr.getDescriptors()) {
                    Log.d("---", "descriptor: " + descriptor.getUuid());
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    if (gatt.writeDescriptor(descriptor)) {
                        enabled = true;
                    }
                }
                if (! enabled) {
//                    throw new RuntimeException("failed to enable notification to characteristic");
                    Log.d("---", "failed to enable notification to characteristic");
                }
            }
        }, 1000);
    }

    private void openPortForOutput(BluetoothGattService service, BluetoothGatt gatt) {
        // do nothing
    }

    private void observe(BluetoothGattService service, BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(NOTIFIER_UUID));
        if (! gatt.setCharacteristicNotification(chr, true)) {
            Log.d("###", "failed to setCharacteristicNotification");
            return;
        }
        for (BluetoothGattDescriptor descriptor : chr.getDescriptors()) {
            Log.d("###", "descriptor: " + descriptor.getUuid());
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (! gatt.writeDescriptor(descriptor)) {
                Log.d("###", "failed to write to the descriptor");
            } else {
                Log.d("###", "succeeded to write ENABLE_NOTIFICATION_VALUE");
            }
        }
    }


    static int count = 0;

    private void writeSome(final BluetoothGattService service, final BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("721AC875-945E-434A-93D8-7AD8C740A51A"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

//        chr.setValue("written from android " + count++);
        ByteBuffer bb = ByteBuffer.allocate(40);
        byte control = 0x01;
        bb.put(control);
        bb.put("written from android".getBytes());
        chr.setValue(bb.array());
        boolean hasWritten = gatt.writeCharacteristic(chr);
        if (!hasWritten) {
            Log.d("###", "failed in write request");
        }
        Log.d("###", "writeSome finished");
/*
        handler_.postDelayed(new Runnable() {
            @Override
            public void run() {
                writeSome(service, gatt);
            }
        }, 3000);
*/
    }

    protected void readSome(BluetoothGattService service, BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("9321525D-08B6-4BDC-90C7-0C2B6234C52B"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

        boolean hasRead = gatt.readCharacteristic(chr);
        if (!hasRead) {
            Log.d("###", "failed in read request");
        }
    }
}