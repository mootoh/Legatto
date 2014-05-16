package net.mootoh.btwithios.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;


public class MainActivity extends Activity implements XNBrowserDelegate {
    public static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private XNBrowser browser_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView)findViewById(R.id.recvTextView);
        tv.setMovementMethod(new ScrollingMovementMethod());

        browser_ = new XNBrowser(this);
        browser_.setDelegate(this);

        final EditText et = (EditText)findViewById(R.id.sendEditText);
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String str = v.getText().toString();
                browser_.send(str.getBytes());
                appendText("me: " + str);
                et.getEditableText().clear();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (! browser_.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        browser_.startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        browser_.stopScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void didGetReady() {
        appendText("--- connected");
    }

    @Override
    public void didDisconnect() {
        appendText("--- disconnected");
    }

    @Override
    public void didReceive(byte[] bytes) {
        String str = "";
        try {
            str = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "didReceive: " + str);
        appendText("   " + str);
    }

    private void sendSome() {
        browser_.send("written from android".getBytes());
    }

    private void appendText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = (TextView) findViewById(R.id.recvTextView);
                tv.append("\n" + text);
            }
        });
    }
}