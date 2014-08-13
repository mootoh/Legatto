package net.mootoh.legatto;

import java.net.URL;

/**
 * Created by takayama.motohiro on 5/20/14.
 */
public interface BrowserDelegate {
    void onSessionOpened(Session session);
    void onSessionClosed(Session session);

    void onReceived(Session session, Peer from, byte[] bytes);
    void onReceivedURL(Session session, URL url);
}
