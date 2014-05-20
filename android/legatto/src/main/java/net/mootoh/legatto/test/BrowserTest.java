package net.mootoh.legatto.test;

import android.app.Activity;
import android.test.AndroidTestCase;
import android.util.Log;

import net.mootoh.legatto.Browser;

class TestActivity extends Activity {

}

/**
 * Created by takayama.motohiro on 5/19/14.
 */
public class BrowserTest extends AndroidTestCase {
    private static final String TAG = "BrowseTest";

    @Override
    public void setUp() {
        Log.d(TAG, "setting up...");
    }

    public void testYay() throws Exception {
        Log.d(TAG, "testing...");
        Browser browser = new Browser(getContext());
        assert(browser.isEnabled());
    }
}