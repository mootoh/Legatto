package net.mootoh.legatto;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.UUID;

class BTLEThread extends Thread {
    Handler handler_;
    long executeAt = 0;

    public void run() {
        Looper.prepare();
        handler_ = new Handler();
        Looper.loop();
    }

    public void post(Runnable r) {
        executeAt = (executeAt == 0 ) ? SystemClock.uptimeMillis() : executeAt + 100;
        handler_.postAtTime(r, executeAt);
    }
}

/**
 * Created by takayama.motohiro on 8/11/14.
 */
public class Session {
    static final String NOTIFIER_UUID = "42015324-6E63-412D-9B7F-257024D56460";
    static final String CONTROLLER_UUID = "5AE0C50F-2C8E-4336-AEAC-F1AF0A325006";
    static final String TAG = "legatto.Session";

    private BluetoothGatt gatt_;
    private BluetoothGattCharacteristic controlPort_;
    private BluetoothGattCharacteristic outPort_;
    private BTLEThread btleThread_ = new BTLEThread();
    private boolean hasIdentifierSet = false;
    private String identifier = "an";

    protected Session(BluetoothGatt gatt, BluetoothGattService service) {
        gatt_ = gatt;
        btleThread_.start();

//        openPortForOutput(service);
        observeNotification(service);
    }

    public void send(final byte[] bytes) {
        btleThread_.post(new Runnable() {
            @Override
            public void run() {
                outPort_.setValue(bytes);
                boolean hasWritten = gatt_.writeCharacteristic(outPort_);
                if (!hasWritten) {
                    Log.d(TAG, "failed in write request");
                }
                Log.d(TAG, "writeSome finished");
            }
        });
    }

    private void openPortForOutput(BluetoothGattService service) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("721AC875-945E-434A-93D8-7AD8C740A51A"));
        if (chr == null) {
            throw new RuntimeException("failed to locate output characteristic");
        }
        outPort_ = chr;
    }

    private void observeNotification(final BluetoothGattService service) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString(NOTIFIER_UUID));
        if (chr == null) {
            Log.d("###", "no such characteristic for notification:" + NOTIFIER_UUID);
            return;
        }
        if (!gatt_.setCharacteristicNotification(chr, true)) {
            throw new RuntimeException("failed to setCharacteristicNotification to gatt");
        }

        boolean enabled = false;
        for (BluetoothGattDescriptor descriptor : chr.getDescriptors()) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (gatt_.writeDescriptor(descriptor)) {
                enabled = true;
            }
        }
        if (!enabled) {
            Log.d("---", "failed to enable notification to characteristic");
            throw new RuntimeException("failed to enable notification to characteristic");
        }
    }

    private void setIdentifier(final BluetoothGattService service, final BluetoothGatt gatt) {
        trySetIdentifier(service, gatt);
//        btleThread_.post(new Runnable() {
//            @Override
//            public void run() {
                if (!hasIdentifierSet) {
                    trySetIdentifier(service, gatt);
                }
//            }
//        });
    }
    private void trySetIdentifier(final BluetoothGattService service, final BluetoothGatt gatt) {
//        btleThread_.post(new Runnable() {
//            @Override
//            public void run() {
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
//            }
//        });
    }

    public boolean isReady() {
        return hasIdentifierSet;
    }

    public void onRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
    }

    protected boolean onWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            Log.d("###", "onCharacteristicWrite: write not permitted");
            return false;
        }

        if (status == BluetoothGatt.GATT_SUCCESS && characteristic.equals(controlPort_)) {
            hasIdentifierSet = true;
            return true;
        }
        return false;
    }

    private void readSome(BluetoothGattService service, final BluetoothGatt gatt) {
        final BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("9321525D-08B6-4BDC-90C7-0C2B6234C52B"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

        btleThread_.post(new Runnable() {
            @Override
            public void run() {
                boolean hasRead = gatt.readCharacteristic(chr);
                if (!hasRead) {
                    Log.d("###", "failed in read request");
                }
            }
        });
    }

    public void openPorts() {

    }
}
