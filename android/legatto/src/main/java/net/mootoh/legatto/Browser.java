package net.mootoh.legatto;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

/**
 * Looks for active BT LE advertisers, and manages the connections between them.
 */
public class Browser {
    static final String SERVICE_UUID = "688C7F90-F424-4BC0-8508-AEDE43A4288D";
    static final long SCAN_PERIOD = 10000;
    static final String TAG = "legatto.Browser";

    static final int CMD_BRODCAST_JOINED_PEER = 0x01;
    static final int HEADER_KEY_NORMAL = 0x03;
    static final int HEADER_KEY_URL    = 0x05;

    private final Context context_;
    private BluetoothAdapter bluetoothAdapter_;
    private BrowserDelegate delegate_;
    private HashMap<BluetoothGatt, Session> sessions_ = new HashMap<BluetoothGatt, Session>();

    /**
     * Setup BT LE with current context.
     * @throws java.lang.RuntimeException if BT is not available on device.
     */
    public Browser(final Context context) {
        this.context_ = context;
        prepareBluetooth();
    }

    private void prepareBluetooth() {
        if (!context_.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            throw new RuntimeException("Bluetooth LE is not enabled in manifest");
        }

        // Initializes a Bluetooth adapter. For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager)context_.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter_ = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter_ == null) {
            throw new RuntimeException("Cannot find Bluetooth Adapter");
        }
    }

    public boolean isEnabled() {
        return bluetoothAdapter_.isEnabled();
    }

    public void startScan() {
        bluetoothAdapter_.startLeScan(leScanCallback_);
    }

    public void stopScan() {
        bluetoothAdapter_.stopLeScan(leScanCallback_);
    }

    public void setDelegate(BrowserDelegate dg) {
        delegate_ = dg;
    }

    private BluetoothAdapter.LeScanCallback leScanCallback_ = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            stopScan();
            connect(device);
        }
    };

    protected void connect(BluetoothDevice device) {
        device.connectGatt(context_, false, new BluetoothGattCallback() {
            // will start discovery if connected successfully
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (delegate_ != null) {
                        Session session = sessions_.get(gatt);
                        if (session != null)
                            delegate_.onSessionClosed(session);
                    }
                    sessions_.remove(gatt);
                }
            }

            // will setup in/out port
            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    for (final BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().equals(UUID.fromString(SERVICE_UUID))) {
                            Session session = new Session(gatt, service);
                            sessions_.put(gatt, session);
                            return;
                        }
                    }
                }
            }

            // Read
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicRead " + status);
                Session session = sessions_.get(gatt);
                session.onRead(gatt, characteristic, status);
            }

            // Write
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicWrite " + status);
                Session session = sessions_.get(gatt);
                if (session.onWrite(characteristic, status)) {
                    if (delegate_ != null)
                        delegate_.onSessionReady(session);
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
                byte[] value = characteristic.getValue();
                Log.d(TAG, "onCharacteristicChanged : " + value.length);

                switch (value[0]) {
                    case CMD_BRODCAST_JOINED_PEER:
                        ByteBuffer bb = ByteBuffer.wrap(value);
                        long msb = bb.getLong(1);
                        long lsb = bb.getLong(9);
                        UUID uuid = new UUID(msb, lsb);
                        Log.d(TAG, "new peer has joined on this session: " + uuid.toString());

                        Session session = sessions_.get(gatt);
                        session.openPorts();

                        return;
                }

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
                        Session session = sessions_.get(gatt);

                        if (currentMode == HEADER_KEY_NORMAL)
                            delegate_.onReceived(session, buf.array());
                        if (currentMode == HEADER_KEY_URL) {
                            String urlString = new String(buf.array());
                            URL url = null;
                            try {
                                url = new URL(urlString);
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                            delegate_.onReceivedURL(session, url);
                        }
                        currentMode = 0;
                    }
                }
            }
        });
    }
}
