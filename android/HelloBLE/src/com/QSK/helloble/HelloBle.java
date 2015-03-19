package com.QSK.helloble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import com.QSK.bleProfiles.HelloBLEService;

import android.os.Bundle;
import android.os.IBinder;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.Spinner;


public class HelloBle extends Activity {
	// bluetooth request codes for initializing connection
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_SELECT_DEVICE = 0;
	
	private static String TAG = HelloBle.class.getSimpleName();

	// Tuning hash map
	final int g_size = 6;
	private LinkedHashMap<String, List<Double>> note_table = new LinkedHashMap<String, List<Double>>();
	private List<LinkedHashMap<Double, String>> tuning_map = new ArrayList<LinkedHashMap<Double, String>>();
	private List<List<String>> strings_list = new ArrayList<List<String>>();
	
	// bluetooth datatypes
	BluetoothAdapter mBtAdapter = null;
	private HelloBLEService mBluetoothLeService;
	private BluetoothDevice mDevice = null;
	private List<BluetoothGattService> mGattServices = null;
	private BluetoothGattCharacteristic mGattCharacteristic = null;
	private boolean mConnected = false;
		
	// motor parameters
	private boolean isTuning = false;
	private double integral = 0;
	
	// recording parameters
	private final int samplingFreq = 22050;
	private final int bufferSize = 1024;
	private int numSamples;
	private long startTimestamp = 0;
	private long lastTimestamp = 0;
	
	// controller parameters
	// strings 5/6 k=10, k_i =0
	// string 3/4 k=16, k_i=2
	// string 1/2 k =100, k_i =4
	private int k =16; // 12 is good for the highest 2 strings (0 k_i) //16;
	private float k_i = 2f; //4f;
	private double refFreq = 0;
	
	AudioDispatcher dispatcher;
	PitchProcessor pitch = new PitchProcessor(PitchEstimationAlgorithm.YIN,
											  samplingFreq,
											  bufferSize, 
											  new PitchDetectionHandler() 
	{
		@Override
		public void handlePitch(PitchDetectionResult pitchDetectionResult, AudioEvent audioEvent) {
			float pitchInHz = pitchDetectionResult.getPitch();
			pitchInHz = (pitchInHz == -1) ? 0 : pitchInHz;
			
			final float freq = pitchInHz;
			runOnUiThread(new Runnable() {
			     @Override
			     public void run() {
			    	TextView text = (TextView) findViewById(R.id.textView_freq);
					text.setText(String.format("%.2f", freq));
			    }
			});
			
			long delta_t = System.currentTimeMillis() - lastTimestamp;
			// filter out samples where pitch wasn't detected
			if (isTuning && (delta_t > 15) && (pitchInHz != 0)) {
				// ignore first 3 samples
				numSamples++;
				if (numSamples <= 3) {
					return;
				}
				
				pitchInHz = (pitchInHz > 1.8 * refFreq) ? pitchInHz / 2 : pitchInHz;	// filter overtones
				pitchInHz = (pitchInHz > 2.7 * refFreq) ? pitchInHz / 3 : pitchInHz;
				pitchInHz = (pitchInHz < 0.55 * refFreq) ? pitchInHz * 2 : pitchInHz;	// filter undertones
				
				//Log.i("sample", String.format("%f", pitchInHz));
				
				double e = refFreq - pitchInHz;
				
				// check if guitar is tuned
				if (Math.abs(e) < 1) {
					controlMotor((byte)0, (byte)0);
					isTuning = false;
					lastTimestamp = System.currentTimeMillis();
					
					final double duration = (lastTimestamp - startTimestamp) / 1000.0;
					runOnUiThread(new Runnable() {
					     @Override
					     public void run() {
					    	 ((Button)findViewById(R.id.button_record)).setText("Start");
						     Toast.makeText(getApplicationContext(), String.format("Tuning completed in %.3f s", duration), 
						    		 Toast.LENGTH_LONG).show();
					    }
					});
					
					Log.i("done", String.format("%d", lastTimestamp));
					return;
				}
				
				integral = integral + e * delta_t / 1000.0;
				byte cw = (e < 0) ? (byte)0 : 1;
				
				double k_comp = (cw == 0) ? k*0.8 : k;
				double k_i_comp = (cw == 0) ? k_i*0.8 : k_i;
				
				e = k_comp * Math.abs(e) + k_i_comp * integral;
				byte u = (e > 255) ? (byte)255 : (byte)e;	// saturate output

				controlMotor(cw, u);
				
				lastTimestamp = System.currentTimeMillis();	
			}
		}
	});
	
	private final void recordAudio(boolean start) {
		if (start) {
	        // init audio dispatcher
			dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(samplingFreq, bufferSize, bufferSize/4);
			dispatcher.addAudioProcessor(pitch);
			new Thread(dispatcher, "Audio Dispatcher").start();			
		}
		else {
			dispatcher.stop();
			controlMotor((byte)0, (byte)0);
		}
	}
	
	// BLE functions
	private final void controlMotor(byte ccw, byte duty) {
    	if (mBluetoothLeService != null && mGattCharacteristic != null) {
    		byte[] data = new byte[2];
    		data[0] = ccw;
    		data[1] = duty;	// 8-bit duty cycle
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
    
    
    // UI functions
	void InitUIHandlers()
	{		
		// Record
		((Button)findViewById(R.id.button_record)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {				
				if (isTuning) {
					isTuning = false;
					controlMotor((byte)0, (byte)0);	// stop motor
					((Button)findViewById(R.id.button_record)).setText("Start");
				}
				else {
					// reinitialize parameters
					isTuning = true;
					numSamples = 0;
					integral = 0;
					startTimestamp = System.currentTimeMillis();
					((Button)findViewById(R.id.button_record)).setText("Stop");
				}
			}
	    });
	}
	
	public void popup() {
		// popup menu for string selection
		AlertDialog.Builder stringSelectionDialogBuilder = new AlertDialog.Builder(
				this);
		// getting string names (string1 to string 6)
		String[] stringArray = new String[g_size];
		for (int i = 0; i < g_size; i++) {
			stringArray[i] = String.format("String %d", i + 1);
		}
		stringSelectionDialogBuilder.setTitle("Pick a string");
		stringSelectionDialogBuilder.setItems(stringArray,
				new DialogInterface.OnClickListener() {
					// string selection
					public void onClick(DialogInterface dialog, int which) {
						// popup menu for note selection
						AlertDialog.Builder noteSelectionDialogBuilder = new AlertDialog.Builder(
								HelloBle.this);
						// notes list from tuning_map based on string selected
						noteSelectionDialogBuilder.setTitle("Pick a Note");
						final int tuning_size = tuning_map.get(which).size();
						String[] tuningsArray = tuning_map.get(which).values()
								.toArray(new String[tuning_size - 1]);
						final int string_selection = which;
						setGainValues(string_selection);
						noteSelectionDialogBuilder.setItems(tuningsArray,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int note_selection) {
										// setting the tuning freq based on
										// choice
										set_tuning_freq(string_selection,
												note_selection);
									}
								});

						AlertDialog tuningDialog = noteSelectionDialogBuilder
								.create();
						tuningDialog.show();
						tuningDialog.getWindow().setLayout(
								getWindowManager().getDefaultDisplay()
										.getWidth() / 2,
								ViewGroup.LayoutParams.WRAP_CONTENT);
					}
				});

		AlertDialog alertDialog = stringSelectionDialogBuilder.create();
		alertDialog.show();
		alertDialog.getWindow().setLayout(
				getWindowManager().getDefaultDisplay().getWidth() / 2,
				ViewGroup.LayoutParams.WRAP_CONTENT);
	}

	private void setup_tuning_variables() {
		// notes and frequencies from
		// http://www.seventhstring.com/resources/notefrequencies.html
		note_table.put("C",
				Arrays.asList(16.35, 32.7, 65.41, 130.8, 261.6, 523.3));
		note_table.put("C#",
				Arrays.asList(17.32, 34.65, 69.3, 138.6, 277.2, 544.4));
		note_table.put("D",
				Arrays.asList(18.35, 36.71, 73.42, 146.8, 293.7, 587.3));
		note_table.put("Eb",
				Arrays.asList(19.45, 38.89, 77.78, 155.6, 311.1, 622.3));
		note_table.put("E",
				Arrays.asList(20.6, 41.2, 82.41, 164.8, 329.6, 659.3));
		note_table.put("F",
				Arrays.asList(21.83, 43.65, 87.31, 174.6, 349.2, 698.5));
		note_table.put("F#",
				Arrays.asList(23.12, 46.25, 92.5, 185.0, 370.0, 740.0));
		note_table.put("G",
				Arrays.asList(24.5, 49.0, 98.0, 196.0, 392.0, 784.0));
		note_table.put("G#",
				Arrays.asList(25.96, 51.91, 103.8, 207.7, 415.3, 830.6));
		note_table.put("A",
				Arrays.asList(27.5, 55.0, 110.0, 220.0, 440.0, 880.0));
		note_table.put("Bb",
				Arrays.asList(29.14, 58.27, 116.5, 233.1, 466.2, 932.3));
		note_table.put("B",
				Arrays.asList(30.87, 61.74, 123.5, 246.9, 493.9, 987.8));

		// string 1 notes (lowest string)
		strings_list.add(Arrays.asList("C#2", "D2", "Eb2", "E2", "F2", "F#2", "G2"));
		// string 2 notes
		strings_list.add(Arrays.asList("F#2","G2","G#2","A2","Bb2","B2","C3"));
		// string 3 notes
		strings_list.add(Arrays.asList("B2","C3","C#3","D3","Eb3","E3","F3"));
		// string 4 notes
		strings_list.add(Arrays.asList("E3","F3","F#3","G3","G#3","A3","Bb3"));
		// string 5 notes
		strings_list.add(Arrays.asList("G#3","A3","Bb3","B3","C4","C#4","D4"));
		// string 6 notes (highest string)
		strings_list.add(Arrays.asList("C#4", "D4", "Eb4", "E4", "F4", "F#4", "G4"));

		for (int i = 0; i < g_size; i++) {
			tuning_map.add(new LinkedHashMap<Double, String>());
			for (String n : strings_list.get(i)) {
				int cut_at = n.length() - 1;
				String note = n.substring(0, cut_at);
				int row = Integer.valueOf(n.substring(cut_at));
				double freq = note_table.get(note).get(row);
				String msg = String.format("%1$s - %2$.2f Hz", note + row, freq);
				tuning_map.get(i).put(freq, msg);
			}
		}
	}

	private void set_tuning_freq(int string_selection, int note_selection) {
		String detailed_note = strings_list.get(string_selection).get(
				note_selection);
		// getting the note selection
		int cut_at = detailed_note.length() - 1;
		String note = detailed_note.substring(0, cut_at);
		int row = Integer.valueOf(detailed_note.substring(cut_at));
		refFreq = note_table.get(note).get(row);
		TextView text = (TextView) findViewById(R.id.textView_ref);
		text.setText(String.format("%.2f", refFreq));
	}
	
	private void setGainValues(int stringSelection){
		// strings 5/6 k=10, k_i =0
		// string 3/4 k=16, k_i=2
		// string 1/2 k =100, k_i =4
		if (stringSelection <= 1){
			k = 100;
			k_i = 3;
		} else if (stringSelection == 2){
			k = 13;
			k_i = 0.5f;
		} else if (stringSelection == 3){
			k = 13;
			k_i = 0.5f;
		} else if (stringSelection <= 5) {
			k = 16;
			k_i = 0;
		}
	}
	
	
	private void setUiState() {
		ActionBar ab = getActionBar();
		
		int res = (mConnected) ? R.string.connected : R.string.disconnected;
		ab.setSubtitle(res);
    }
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_hello_ble);
		
		setup_tuning_variables();
		
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
        recordAudio(true);
        Log.i("event", "resume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        recordAudio(false);
        unregisterReceiver(mGattUpdateReceiver);
        Log.i("event", "pause");
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
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
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle presses on the action bar items
	    switch (item.getItemId()) {
	        case R.id.action_bluetooth:
	            Intent newIntent = new Intent(HelloBle.this, DeviceListActivity.class);
	            startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
	            return true;
	        case R.id.action_tuning:
	        	popup();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
}
