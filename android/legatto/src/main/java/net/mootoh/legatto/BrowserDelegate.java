package net.mootoh.legatto;

import java.net.URL;

/**
 * Created by takayama.motohiro on 5/20/14.
 */
public interface BrowserDelegate {
    void didGetReady();
    void didReceive(byte[] bytes);
    void didReceiveURL(URL url);
    void didDisconnect();
}
