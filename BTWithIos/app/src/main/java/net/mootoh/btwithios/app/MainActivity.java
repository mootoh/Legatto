package net.mootoh.btwithios.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;


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
                if (browser_.isReady())
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
        appendText("ios: " + str);
    }

    class ImageDownloadTask extends AsyncTask<URL, Integer, Bitmap> {
        private final ImageView imageView;

        ImageDownloadTask(ImageView iv) {
            imageView = iv;
        }

        @Override
        protected Bitmap doInBackground(URL... params) {
            URL url = params[0];
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            imageView.setImageBitmap(bitmap);
        }
    }
    @Override
    public void didReceiveURL(final URL url) {
        Log.d(TAG, "url received: " + url);
        if (url.getPath().endsWith(".jpg")) {
            final MainActivity self = this;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final ImageView iv = new ImageView(self, null);
                    ImageDownloadTask task = new ImageDownloadTask(iv);
                    task.execute(url);
                    final LinearLayout layout = (LinearLayout)findViewById(R.id.rootLinearLayout);
                    layout.addView(iv);

                    iv.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            layout.removeView(iv);
                            return true;
                        }
                    });
                }
            });
        }
    }

    private void sendSome() {
        browser_.send("written from android".getBytes());
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
}