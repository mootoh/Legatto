package net.mootoh.btwithios.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.UUID;


public class MainActivity extends Activity {
    private static final int REQUEST_ENABLE_BT = 1;

    BluetoothAdapter bluetoothAdapter_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BT not supported 1", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter_ = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter_ == null) {
            Toast.makeText(this, "BT not supported 2", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
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

    private Handler handler_ = new Handler();
    private boolean scanning_ = false;
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onResume() {
        super.onResume();
        if (!bluetoothAdapter_.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        UUID[] uuidToScan = { UUID.fromString("688C7F90-F424-4BC0-8508-AEDE43A4288D") };


        handler_.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter_.stopLeScan(leScanCallback_);
                scanning_ = false;
            }
        }, SCAN_PERIOD);

        bluetoothAdapter_.startLeScan(leScanCallback_);
        scanning_ = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        bluetoothAdapter_.stopLeScan(leScanCallback_);
        scanning_ = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    BluetoothAdapter.LeScanCallback leScanCallback_ = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (! scanning_)
                return;
            if (device.getName().equals("btbt")) {
                Log.d("@@@", "found BT device: " + device.getAddress() + " " + device.getName());
                scanning_ = false;
                bluetoothAdapter_.stopLeScan(this);
                connect(device);
            }
        }
    };

    protected void connect(BluetoothDevice device) {
        device.connectGatt(this, false, new BluetoothGattCallback() {
            BluetoothGattService service_;

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                Log.d("###", "got connection state changed: " + status + ", " + newState);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("###", "connected successfully");

                } else if (status == BluetoothGatt.GATT_FAILURE) {
                    Log.d("###", "connection failed");
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("###", "connected");
                    gatt.discoverServices();
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("###", "disconnected");
                }
            }

            @Override
            // New services discovered
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("###", "service found");
                    for (BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().equals(UUID.fromString("688C7F90-F424-4BC0-8508-AEDE43A4288D"))) {
                            Log.d("###", "found iPhone!");
                            service_ = service;
                            readSome(service, gatt);
                            return;
                        }
                    }
                } else {
                    Log.w("###", "onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d("###", "onCharacteristicRead " + status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte[] value = characteristic.getValue();
                    int x = value[0];
                    Log.d("###", "value = " + x);

                    writeSome(service_, gatt);
                }
            }

            @Override
            public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                    Log.d("###", "onCharacteristicWrite: write not permitted");
                    return;
                }
                Log.d("###", "onCharacteristicWrite " + status);
            }
        });
    }

    private void writeSome(BluetoothGattService service, BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("721AC875-945E-434A-93D8-7AD8C740A51A"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

        chr.setValue("written from android");
        boolean hasWritten = gatt.writeCharacteristic(chr);
        if (!hasWritten) {
            Log.d("###", "failed in write request");
        }
    }

    protected void readSome(BluetoothGattService service, BluetoothGatt gatt) {
        BluetoothGattCharacteristic chr = service.getCharacteristic(UUID.fromString("721AC875-945E-434A-93D8-7AD8C740A51A"));
        if (chr == null) {
            Log.d("###", "no such characteristic");
            return;
        }

        boolean hasRead = gatt.readCharacteristic(chr);
        if (!hasRead) {
            Log.d("###", "failed in read request");
        }
    }
}