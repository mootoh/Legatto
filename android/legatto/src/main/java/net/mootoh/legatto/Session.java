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
    private BluetoothGattCharacteristic outPort_;
    private BTLEThread btleThread_ = new BTLEThread();

    protected Session(BluetoothGatt gatt, BluetoothGattService service) {
        gatt_ = gatt;
        btleThread_.start();

        observeNotification(service);
        openPortForOutput(service);
    }

    enum SendMode {
        DONE,
        SEND_TO_ALL,
        SEND_TO
    };

    private SendMode sendMode_;
    private byte[] sendingBytes_;
    private int sendingBytesIndex_;

    public void sendToAll(final byte[] bytes) {
        sendMode_ = SendMode.SEND_TO_ALL;
        sendingBytes_ = bytes;
        sendingBytesIndex_ = 0;

        sendChunkToAll();
    }

    byte msg_id = 0;

    private void sendChunkToAll() {
        assert sendMode_ == SendMode.SEND_TO_ALL;

        int remaining = sendingBytes_.length - sendingBytesIndex_;
        if (remaining <= 0) {
            sendMode_ = SendMode.DONE;
            sendingBytesIndex_ = 0;
            sendingBytes_ = null;
            msg_id++;
            return;
        }

        int toSend = Math.min(remaining, 20-3);

        ByteBuffer bb = ByteBuffer.allocate(3 + toSend);
        bb.put(Browser.CMD_SEND_TO_ALL);
        bb.put(msg_id);
        bb.put((byte)remaining);
        bb.put(sendingBytes_, sendingBytesIndex_, toSend);
        outPort_.setValue(bb.array());
        sendingBytesIndex_ += toSend;

        if (! gatt_.writeCharacteristic(outPort_)) {
            Log.d(TAG, "failed in sending to all");
        }
    }

    public void sendTo(final byte[] bytes, final Peer peer) {

    }

    public Peer[] getPeers() {
        return null;
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
        if (chr == null)
            throw new RuntimeException("no such characteristic for notification:" + NOTIFIER_UUID);
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
        if (!enabled)
            throw new RuntimeException("failed to enable notification to characteristic");
    }

    protected void onRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
    }

    protected void onWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            Log.d("###", "onCharacteristicWrite: write not permitted");
            return;
        }
        if (sendMode_ == SendMode.SEND_TO_ALL)
            sendChunkToAll();
    }

    public void openPorts() {
    }
}
