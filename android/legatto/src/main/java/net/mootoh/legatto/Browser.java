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
import android.util.Log;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Looks for active BT LE advertisers, and manages the connections between them.
 */
public class Browser {
    static final String SERVICE_UUID = "688C7F90-F424-4BC0-8508-AEDE43A4288D";
    static final String TAG = "legatto.Browser";

    static final byte CMD_BROADCAST_PEER_JOINED = 0x01;
    static final byte CMD_BROADCAST_PEER_LEFT   = 0x02;
    static final byte CMD_SEND_TO_ALL = 0x03;
    static final byte CMD_BROADCAST_MESSAGE = 0x04;

    private static final byte MTU = 20;

    private final Context context_;
    private final BluetoothAdapter bluetoothAdapter_;
    private final BrowserDelegate delegate_;
    private final Set<Session> sessions_ = new HashSet<Session>();
    Map<Byte, ByteBuffer> buffers = new HashMap();

    /**
     * Setup BT LE with current context.
     * @throws java.lang.RuntimeException if BT is not available on device.
     */
    public Browser(final Context context, final BrowserDelegate delegate) {
        context_ = context;
        delegate_ = delegate;

        // Initializes a Bluetooth adapter. For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
        if (!context_.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            throw new RuntimeException("Bluetooth LE is not enabled in manifest");
        }
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

    private final BluetoothAdapter.LeScanCallback leScanCallback_ = new BluetoothAdapter.LeScanCallback() {
        // scan -> connect
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, "bt device name: " + device.getName());
            if (device.getName() != null && device.getName().equals("btbt")) {
                stopScan();
                connect(device);
            }
        }
    };

    // connected -> discover service -> create a session
    // disconnected -> delete the seession
    protected void connect(BluetoothDevice device) {
        device.connectGatt(context_, false, new BluetoothGattCallback() {
            Session session_;

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if (session_ == null)
                            break;
                        if (delegate_ != null)
                            delegate_.onSessionClosed(session_);
                        sessions_.remove(session_);
                        break;
                    default:
                        Log.d(TAG, "BTGatt.onConnectionStateChange: " + status + "->" + newState);
                        break;
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS)
                    return;

                stopScan();

                for (final BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(UUID.fromString(SERVICE_UUID))) {
                        session_ = new Session(gatt, service);
                        sessions_.add(session_);

                        if (delegate_ != null)
                            delegate_.onSessionOpened(session_);
                        return;
                    }
                }
            }

            // Read
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                session_.onRead(gatt, characteristic, status);
            }

            // Write
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                session_.onWrite(characteristic, status);
            }

            // Notification
            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                byte[] value = characteristic.getValue();
                Log.d(TAG, "onCharacteristicChanged : " + value.length);

                switch (value[0]) {
                    case CMD_BROADCAST_PEER_JOINED: {
                        // cmd[1B], UUID[16B]
                        ByteBuffer bb = ByteBuffer.wrap(value);
                        long msb = bb.getLong(1);
                        long lsb = bb.getLong(9);
                        UUID uuid = new UUID(msb, lsb);

                        Peer peer = new Peer(uuid);
                        if (delegate_ != null) {
                            delegate_.onPeerJoined(session_, peer);
                        }
                        return;
                    }
                    case CMD_BROADCAST_PEER_LEFT: {
                        // cmd[1B], UUID[16B]
                        break;
                    }
                    case CMD_BROADCAST_MESSAGE: {
                        // cmd[1B], messageID[1B], remaining message size[1B], message payload[<=17B]
                        byte id = value[1];
                        byte remaining = value[2];

                        ByteBuffer bb = buffers.get(id);
                        if (bb == null) {
                            bb = ByteBuffer.allocate(remaining);
                            buffers.put(id, bb);
                        }

                        bb.put(Arrays.copyOfRange(value, 3, value.length));

                        if (remaining <= MTU - 3) { // 1 for cmd, 1 for length
                            // received completely
                            if (delegate_ != null)
                                delegate_.onReceived(session_, null, bb.array());
                            bb.clear();
                            buffers.remove(id);
                        }
                    }
                }
            }
        });
    }
}
