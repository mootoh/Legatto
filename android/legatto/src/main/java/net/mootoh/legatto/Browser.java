package net.mootoh.legatto;

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
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.UUID;

class BTLEHandler {
    Handler handler_ = new Handler();
    long executeAt = 0;

    public void post(Runnable r) {
        executeAt = (executeAt == 0 ) ? SystemClock.uptimeMillis() : executeAt + 100;
        handler_.postAtTime(r, executeAt);
    }
}

/**
 * Created by takayama.motohiro on 5/14/14.
 */
public class Browser {
    static final String NOTIFIER_UUID = "42015324-6E63-412D-9B7F-257024D56460";
    static final String SERVICE_UUID = "688C7F90-F424-4BC0-8508-AEDE43A4288D";
    static final String CONTROLLER_UUID = "5AE0C50F-2C8E-4336-AEAC-F1AF0A325006";
    static final long SCAN_PERIOD = 10000;
    static final String TAG = "Browser";

    static final int HEADER_KEY_NORMAL = 0x03;
    static final int HEADER_KEY_URL    = 0x05;

    private String identifier = "an";

    private final Context context_;
    private BluetoothAdapter bluetoothAdapter_;
    private Handler handler_ = new Handler();
    private BTLEHandler btHandler_ = new BTLEHandler();
    private boolean scanning_ = false;
    private BrowserDelegate delegate_;
    private BluetoothGattCharacteristic controlPort_;
    private BluetoothGattCharacteristic outpPort_;
    private BluetoothGatt gatt_;
    private boolean hasIdentifierSet = false;

    public Browser(final Context context) {
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

    public void stopScan() {
        bluetoothAdapter_.stopLeScan(leScanCallback_);
        scanning_ = false;
    }

    public boolean isReady() {
//        return inputPortConnected_;
        return hasIdentifierSet;
    }

    public void setDelegate(BrowserDelegate dg) {
        delegate_ = dg;
    }

    private BluetoothAdapter.LeScanCallback leScanCallback_ = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (! scanning_)
                return;
            if (device.getName().equals("btbt")) {
                stopScan();
                connect(device);
            }
        }
    };

    protected void connect(BluetoothDevice device) {
        device.connectGatt(context_, false, new BluetoothGattCallback() {
            BluetoothGattService service_;

            // will start discovery on connected successfully
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (status == BluetoothGatt.GATT_SUCCESS) {

                } else if (status == BluetoothGatt.GATT_FAILURE) {
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt_ = gatt;
                    gatt.discoverServices();
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (delegate_ != null) {
                        delegate_.didDisconnect();
                    }

                    gatt_ = null;
                }
            }

            // will setup in/out port
            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    for (final BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().equals(UUID.fromString(SERVICE_UUID))) {
                            service_ = service;

                            openPortForOutput(service, gatt);
                            openPortForInput(service, gatt);
                            return;
                        }
                    }
                }
            }

            // Read
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicRead " + status);
            }

            // Write
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicWrite " + status);

                if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                    Log.d("###", "onCharacteristicWrite: write not permitted");
                    return;
                }

                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.equals(controlPort_)) {
                    hasIdentifierSet = true;
                    if (delegate_ != null) {
                        delegate_.didGetReady();
                    }
                }
            }

            int notificationStatus = 0;
            int currentMode = 0;
            int hasRead = 0;
            int toRead = 0;
            ByteBuffer buf;

            // Notification
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (notificationStatus == 0) { // message begins, header first
                    byte[] header = characteristic.getValue();
                    assert header[0] == HEADER_KEY_NORMAL || header[0] == HEADER_KEY_URL;
                    currentMode = header[0];
                    hasRead = 0;
                    toRead = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1);
                    buf = ByteBuffer.allocate(toRead);

                    notificationStatus = 1;
                    return;
                }

                // body
                byte[] val = characteristic.getValue();
                hasRead += val.length;
                buf.put(val);

                if (hasRead >= toRead) {
                    notificationStatus = 0;

                    if (delegate_ != null) {
                        if (currentMode == HEADER_KEY_NORMAL)
                            delegate_.didReceive(buf.array());
                        if (currentMode == HEADER_KEY_URL) {
                            String urlString = new String(buf.array());
                            URL url = null;
                            try {
                                url = new URL(urlString);
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                            delegate_.didReceiveURL(url);
                        }
                        currentMode = 0;
                    }
                }
            }
        });
    }

    private void openPortForInput(final BluetoothGattService service, final BluetoothGatt gatt) {
        // Observe
        btHandler_.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "observing..............");
                BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(NOTIFIER_UUID));
                if (chr == null) {
                    Log.d("###", "no such characteristic for notification:" + NOTIFIER_UUID);
                    return;
                }
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
                    Log.d("---", "failed to enable notification to characteristic");
                    throw new RuntimeException("failed to enable notification to characteristic");
                }
            }
        });

        setIdentifier(service, gatt);
    }

    private void setIdentifier(final BluetoothGattService service, final BluetoothGatt gatt) {
        trySetIdentifier(service, gatt);
        btHandler_.post(new Runnable() {
            @Override
            public void run() {
                if (! hasIdentifierSet) {
                    trySetIdentifier(service, gatt);
                }
            }
        });
    }
    private void trySetIdentifier(final BluetoothGattService service, final BluetoothGatt gatt) {
        btHandler_.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "controller..............");
                BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(CONTROLLER_UUID));
                if (chr == null) {
                    Log.d("###", "no such characteristic for controller:" + CONTROLLER_UUID);
                    return;
                }
                controlPort_ = chr;

                ByteBuffer bb = ByteBuffer.allocate(1 + identifier.length());
                byte code = 0x05;
                bb.put(code);
                bb.put(identifier.getBytes());
                chr.setValue(bb.array());

                boolean hasWrite = gatt.writeCharacteristic(chr);
                if (!hasWrite) {
                    Log.d("###", "failed in write request to controller");
                }
            }
        });
    }

    private void openPortForOutput(BluetoothGattService service, BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("721AC875-945E-434A-93D8-7AD8C740A51A"));
        if (chr == null) {
            throw new RuntimeException("failed to locate output characteristic");
        }
        outpPort_ = chr;
    }

    private void readSome(BluetoothGattService service, final BluetoothGatt gatt) {
        final BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("9321525D-08B6-4BDC-90C7-0C2B6234C52B"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

        btHandler_.post(new Runnable() {
            @Override
            public void run() {
                boolean hasRead = gatt.readCharacteristic(chr);
                if (!hasRead) {
                    Log.d("###", "failed in read request");
                }
            }
        });
    }

    public void send(final byte[] bytes) {
        btHandler_.post(new Runnable() {
            @Override
            public void run() {
                outpPort_.setValue(bytes);
                boolean hasWritten = gatt_.writeCharacteristic(outpPort_);
                if (!hasWritten) {
                    Log.d(TAG, "failed in write request");
                }
                Log.d(TAG, "writeSome finished");
            }
        });
    }
}
