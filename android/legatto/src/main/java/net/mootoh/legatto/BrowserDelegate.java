package net.mootoh.legatto;

import android.bluetooth.BluetoothDevice;

import java.net.URL;

/**
 * Created by takayama.motohiro on 5/20/14.
 */
public interface BrowserDelegate {
    void onSessionReady(Session session);
    void onSessionClosed(Session session);

    void onReceived(Session session, byte[] bytes);
    void onReceivedURL(Session session, URL url);
}
