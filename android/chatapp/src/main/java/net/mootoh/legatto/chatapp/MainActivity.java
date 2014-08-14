package net.mootoh.legatto.chatapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import net.mootoh.legatto.Browser;
import net.mootoh.legatto.BrowserDelegate;
import net.mootoh.legatto.Peer;
import net.mootoh.legatto.Session;

import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity implements BrowserDelegate {
    public static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private Browser browser_;
    private Session session_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView)findViewById(R.id.recvTextView);
        tv.setMovementMethod(new ScrollingMovementMethod());

        browser_ = new Browser(this, this);

        final EditText et = (EditText)findViewById(R.id.sendEditText);
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                String str = v.getText().toString();
                if (session_ != null)
                    session_.sendToAll(str.getBytes());
                appendText("me: " + str);
                et.getEditableText().clear();
                return true;
            }
        });

        connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        switch (id) {
            case R.id.action_leave:
                session_.leave();
                return true;
            case R.id.action_connect:
                connect();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSessionOpened(Session session) {
        appendText("--- onSessionOpened");
        session_ = session;
    }

    @Override
    public void onSessionClosed(Session session) {
        appendText("--- onSessionClosed");
        session_ = null;
    }

    @Override
    public void onPeerJoined(Session session, Peer peer) {
        appendText("--- peer joined: " + peer);
    }

    @Override
    public void onPeerLeft(Session session, Peer peer) {
        appendText("--- peer left: " + peer);
    }

    @Override
    public void onReceived(Session session, Peer from, byte[] bytes) {
        String str = "";
        try {
            str = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        appendText("ios: " + str);
    }

    private void appendText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView tv = (TextView) findViewById(R.id.recvTextView);
                tv.append("\n" + text);

                // scroll to bottom
                final ScrollView sv = (ScrollView)findViewById(R.id.scroller);
                sv.post(new Runnable() {
                    @Override
                    public void run() {
                        sv.smoothScrollTo(0,tv.getBottom());
                    }
                });
            }
        });
    }

    private void connect() {
        if (! browser_.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        browser_.startScan();
    }
}