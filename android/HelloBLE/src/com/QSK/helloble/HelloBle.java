package com.QSK.helloble;

import java.util.List;

import com.QSK.bleProfiles.HelloBLEService;

import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.ToggleButton;


public class HelloBle extends Activity {

	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_SELECT_DEVICE = 0;
		
	private static String TAG = HelloBle.class.getSimpleName();
	
	private HelloBLEService mBluetoothLeService;
	private BluetoothDevice mDevice = null;
	private List<BluetoothGattService> mGattServices = null;
	private BluetoothGattCharacteristic mGattCharacteristic = null;
	private boolean mConnected = false;
	private byte motorDirection = 0;
	private byte motorDuty = 0;
	BluetoothAdapter mBtAdapter = null;
	
	private final void SetMotorVelocity() {
    	if (mBluetoothLeService != null && mGattCharacteristic != null) {
    		byte[] data = new byte[2];
    		data[0] = motorDirection;	// cw
    		data[1] = motorDuty;	// 8-bit duty cycle
    		mGattCharacteristic.setValue(data);
    		mBluetoothLeService.writeCharacteristic(mGattCharacteristic);
    	}
	}
	
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
        	Log.d(TAG, "onServiceConnected");
            mBluetoothLeService = ((HelloBLEService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            if (mDevice != null) {
            	mBluetoothLeService.connect(mDevice.getAddress());
            }
            else {
            	Log.e(TAG, "mDevice is null");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
	
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(HelloBLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(HelloBLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(HelloBLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(HelloBLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
    
	void InitUIHandlers()
	{		
		// Select Device
		((Button)findViewById(R.id.button_selectdevice)).setOnClickListener(new View.OnClickListener() {
	        public void onClick(View v) {
	            Intent newIntent = new Intent(HelloBle.this, DeviceListActivity.class);
	            startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
	        }
	    });
		
		((SeekBar)findViewById(R.id.seekBar_motor)).setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
 
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
				motorDuty = (byte)progress;
				SetMotorVelocity();
			}
 
			public void onStartTrackingTouch(SeekBar seekBar) {				
				// TODO Auto-generated method stub
			}
 
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub		
			}
		});
		
		// checked means CCW, unchecked means CW
		((ToggleButton)findViewById(R.id.toggleButton_direction)).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				motorDirection = ((ToggleButton)v).isChecked() ? (byte)1 : (byte)0;
				SetMotorVelocity();
			}
		});
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_hello_ble);
		
		InitUIHandlers();
		
		/* Ensure Bluetooth is enabled */
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBtAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available - exiting...",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		if (!mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {

        }
				
		setUiState();
		
        Intent gattServiceIntent = new Intent(this, HelloBLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}
	
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (HelloBLEService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                setUiState();
            } else if (HelloBLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                setUiState();
            } else if (HelloBLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
            	mGattServices = mBluetoothLeService.getSupportedGattServices();
            	for (BluetoothGattService s : mGattServices) {
            		// look for tuning service and store characteristic
            		if (s.getUuid().equals(mBluetoothLeService.UUID_SERVICE)) {
            			mGattCharacteristic = s.getCharacteristic(mBluetoothLeService.UUID_CHARACTERISTIC);
            			break;
            		}
            		else {
            			continue;
            		}
            	}
            } else if (HelloBLEService.ACTION_DATA_AVAILABLE.equals(action)) {
            	// data read from device
            	//String data = intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
				//((TextView) findViewById(R.id.textView_username)).setText(data);
            }
        }
    };
	
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
        	if (mDevice != null) {
                final boolean result = mBluetoothLeService.connect(mDevice.getAddress());
                Log.d(TAG, "Connect request result=" + result);	
        	}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }
    
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d("ActivityResult", Integer.toString(requestCode));
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode != Activity.RESULT_OK) {
				finish();
			} else {
				mBtAdapter.startDiscovery();
			}
		} else if (requestCode == REQUEST_SELECT_DEVICE) {
			if (resultCode == Activity.RESULT_OK && data != null) {
				String deviceAddress = data
						.getStringExtra(BluetoothDevice.EXTRA_DEVICE);

				mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
						deviceAddress);
				setUiState();
			} else {
				Toast.makeText(this, "failed to select the device - try again",
						Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_hello_ble, menu);
		return true;
	}
	
	private void setUiState() {
		((Button)findViewById(R.id.button_selectdevice)).setEnabled(!mConnected);
		
    	if (mDevice != null) {
    		((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName());
    	}

    	int res = (mConnected) ? R.string.connected : R.string.disconnected;
    	((TextView) findViewById(R.id.statusName)).setText(res);
    }
    
    @Override
    public void onDestroy() {
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    	super.onDestroy();
    }
}
