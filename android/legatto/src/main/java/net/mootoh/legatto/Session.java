package net.mootoh.legatto;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by takayama.motohiro on 8/11/14.
 */
public class Session {
    static final String NOTIFIER_UUID = "42015324-6E63-412D-9B7F-257024D56460";
    static final String OUTPORT_UUID = "721AC875-945E-434A-93D8-7AD8C740A51A";
    static final String PEERS_PORT_UUID = "78BE28F8-B0A4-4164-8A6D-8BC236BF0D01";
    static final String TAG = "legatto.Session";

    private final BluetoothGatt gatt_;
    private final BluetoothGattCharacteristic peersPort_;
    private final BluetoothGattCharacteristic outPort_;
    private final BluetoothGattCharacteristic notifyPort_;

    public void removePeer(byte identifier) {
        for (int i=0; i<peers_.size(); i++) {
            if (peers_.get(i).getIdentifier() == identifier) {
                peers_.remove(i);
                return;
            }
        }
    }

    public void resetPeers() {
        peers_.clear();
    }

    enum SendMode {
        DONE,
        SEND_TO_ALL,
        SEND_TO
    };

    private SendMode sendMode_;
    private byte[] sendingBytes_;
    private int sendingBytesIndex_;
    private byte msg_id = 0;
    private byte sendTo_ = 0;

    private ArrayList<Peer> peers_ = new ArrayList<Peer>();

    protected Session(BluetoothGatt gatt, BluetoothGattService service) {
        gatt_ = gatt;

        notifyPort_ = service.getCharacteristic(UUID.fromString(NOTIFIER_UUID));
        if (notifyPort_ == null)
            throw new RuntimeException("no such characteristic for notification:" + NOTIFIER_UUID);

        observeNotification();

        outPort_ = service.getCharacteristic(UUID.fromString(OUTPORT_UUID));
        if (outPort_ == null) {
            throw new RuntimeException("failed to locate output characteristic");
        }

        peersPort_ = service.getCharacteristic(UUID.fromString(PEERS_PORT_UUID));
        if (peersPort_== null) {
            throw new RuntimeException("failed to locate peers characteristic");
        }
    }

    public void sendToAll(final byte[] bytes) {
        assert sendMode_ == SendMode.DONE;

        sendMode_ = SendMode.SEND_TO_ALL;
        sendingBytes_ = bytes;
        sendingBytesIndex_ = 0;

        sendChunkToAll();
    }

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
            Log.e(TAG, "failed in sending to all");
        }
    }

    private void sendChunkTo() {
        assert sendMode_ == SendMode.SEND_TO;

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
        bb.put(Browser.CMD_SEND_TO);
        bb.put(msg_id);
        bb.put((byte)remaining);
        bb.put((byte)sendTo_);
        bb.put(sendingBytes_, sendingBytesIndex_, toSend);
        outPort_.setValue(bb.array());
        sendingBytesIndex_ += toSend;

        if (! gatt_.writeCharacteristic(outPort_)) {
            Log.e(TAG, "failed in sending to all");
        }
    }

    public void sendTo(final byte[] bytes, final Peer peer) {
        assert sendMode_ == SendMode.DONE;

        sendMode_ = SendMode.SEND_TO;
        sendingBytes_ = bytes;
        sendingBytesIndex_ = 0;
        sendTo_ = peer.getIdentifier();

        sendChunkTo();
    }

    protected void requestAllPeers() {
        if (! gatt_.readCharacteristic(peersPort_)) {
            Log.e(TAG, "failed in retrieving all peers");
        }
    }

    public Peer[] getPeers() {
        Peer[] ret = new Peer[peers_.size()];
        peers_.toArray(ret);
        return ret;
    }

    protected Peer getPeer(byte id) {
        for (Peer peer : peers_) {
            if (peer.getIdentifier() == id)
                return peer;
        }
        return null;
    }

    public void addPeer(Peer peer) {
        peers_.add(peer);
    }

    public void leave() {
        unobserveNotification();
        gatt_.disconnect();
        gatt_.close();
    }

    private void observeNotification() {
        if (! gatt_.setCharacteristicNotification(notifyPort_, true))
            throw new RuntimeException("failed to enable setCharacteristicNotification");

        for (BluetoothGattDescriptor descriptor : notifyPort_.getDescriptors()) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (! gatt_.writeDescriptor(descriptor))
                throw new RuntimeException("failed to enable notification to characteristic");
        }
    }

    private void unobserveNotification() {
        if (! gatt_.setCharacteristicNotification(notifyPort_, false))
            throw new RuntimeException("failed to disable setCharacteristicNotification");

        for (BluetoothGattDescriptor descriptor : notifyPort_.getDescriptors()) {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            if (! gatt_.writeDescriptor(descriptor))
                throw new RuntimeException("failed to disable notification to characteristic");
        }
    }

    protected void onRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        byte[] value = characteristic.getValue();

        if (characteristic.equals(peersPort_)) {
            // received peers
            resetPeers();
            byte offset = 0;
            for (int i=0; i<16; i++) {
                for (byte j=0; j<8; j++) {
                    if ((value[i+1] & (1<<j)) != 0) {
                        Log.d(TAG, "peer " + (offset+j) + " in the session");
                        Peer peer = new Peer((byte)(offset+j));
                        addPeer(peer);
                    }
                }
                offset += 8;
            }
        }
    }

    protected void onWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            Log.d("###", "onCharacteristicWrite: write not permitted");
            return;
        }
        if (sendMode_ == SendMode.SEND_TO_ALL)
            sendChunkToAll();
        else if (sendMode_ == SendMode.SEND_TO)
            sendChunkTo();
    }
}
