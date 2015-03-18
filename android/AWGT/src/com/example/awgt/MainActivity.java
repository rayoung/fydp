package com.example.awgt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import com.QSK.bleProfiles.HelloBLEService;
import com.QSK.helloble.DeviceListActivity;
import com.QSK.helloble.HelloBle;
import com.example.awgt.util.SystemUiHider;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class MainActivity extends Activity {
	// start recording button
	private Button btnStartRecording;

	// recording toggle
	private boolean recording = false;

	// Tuning hash map
	final int g_size = 6;
	private LinkedHashMap<String, List<Double>> note_table = new LinkedHashMap<String, List<Double>>();
	private List<LinkedHashMap<Double, String>> tuning_map = new ArrayList<LinkedHashMap<Double, String>>();
	private List<List<String>> strings_list = new ArrayList<List<String>>();

	// bluetooth request codes for initializing connection
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_SELECT_DEVICE = 0;

	private static String TAG = HelloBle.class.getSimpleName();

	// bluetooth datatypes
	BluetoothAdapter mBtAdapter = null;
	private HelloBLEService mBluetoothLeService;
	private BluetoothDevice mDevice = null;
	private List<BluetoothGattService> mGattServices = null;
	private BluetoothGattCharacteristic mGattCharacteristic = null;
	private boolean mConnected = false;

	// motor parameters
	private byte motorDirection = 0; // 1 - CW, 0 - CCW
	private byte motorDuty = 0; // 0-255
	private boolean startTuning = false;
	private double integral = 0;

	// recording parameters
	private final int samplingFreq = 44100;
	private final int bufferSize = 2048;
	private LinkedList<Double> freqFilter = new LinkedList<Double>();
	private int numSamples;
	private long lastTimestamp = 0;

	// controller parameters
	// strings 5/6 k=10, k_i =0
	// string 3/4 k=16, k_i=2
	// string 1/2 k =100, k_i =4
	private int k = 16; // 12 is good for the highest 2 strings (0 k_i) //16;
	private double k_i = 2; // 4f;
	private double refFreq = -100;

	/**
	 * Whether or not the system UI should be auto-hidden after
	 * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
	 */
	private static final boolean AUTO_HIDE = false;

	/**
	 * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
	 * user interaction before hiding the system UI.
	 */
	private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

	/**
	 * If set, will toggle the system UI visibility upon interaction. Otherwise,
	 * will show the system UI visibility upon interaction.
	 */
	private static final boolean TOGGLE_ON_CLICK = false;

	/**
	 * The flags to pass to {@link SystemUiHider#getInstance}.
	 */
	private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

	/**
	 * The instance of the {@link SystemUiHider} for this activity.
	 */
	private SystemUiHider mSystemUiHider;

	AudioDispatcher dispatcher;
	PitchProcessor pitch = new PitchProcessor(PitchEstimationAlgorithm.YIN,
			samplingFreq, bufferSize, new PitchDetectionHandler() {
				@Override
				public void handlePitch(
						PitchDetectionResult pitchDetectionResult,
						AudioEvent audioEvent) {
					double pitchInHz = pitchDetectionResult.getPitch();
					pitchInHz = (pitchInHz == -1) ? 0 : pitchInHz;

					final double freq = pitchInHz;
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							TextView text = (TextView) findViewById(R.id.fullscreen_content);
							text.setText(String.format("%.2f", freq));
						}
					});

					long delta_t = System.currentTimeMillis() - lastTimestamp;
					// filter out samples where pitch wasn't detected
					if (startTuning && (delta_t > 20) && (pitchInHz != 0)) {
						// ignore first 3 samples
						numSamples++;
						if (numSamples <= 3) {
							return;
						}

						pitchInHz = (pitchInHz > 1.8 * refFreq) ? pitchInHz / 2
								: pitchInHz; // filter overtones
						pitchInHz = (pitchInHz < 0.55 * refFreq) ? pitchInHz * 2
								: pitchInHz; // filter undertones

						// Log.i("sample", String.format("%f", pitchInHz));

						double e = refFreq - pitchInHz;

						// check if guitar is tuned
						if (Math.abs(e) < 1) {
							controlMotor((byte) 0, (byte) 0);
							startTuning = false;
							lastTimestamp = System.currentTimeMillis();
							Log.i("done", String.format("%d", lastTimestamp));
							return;
						}

						integral = integral + e * (double) delta_t / 1000;
						byte cw = (e < 0) ? (byte) 0 : 1;

						e = k * Math.abs(e) + k_i * integral;
						byte u = (e > 255) ? (byte) 255 : (byte) e; // saturate
																	// output

						controlMotor(cw, u);

						lastTimestamp = System.currentTimeMillis();
					}
				}
			});

	// BLE functions
	private final void controlMotor(byte ccw, byte duty) {
		if (mBluetoothLeService != null && mGattCharacteristic != null) {
			byte[] data = new byte[2];
			data[0] = ccw;
			data[1] = duty; // 8-bit duty cycle
			mGattCharacteristic.setValue(data);
			mBluetoothLeService.writeCharacteristic(mGattCharacteristic);
		}
	}

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			Log.d(TAG, "onServiceConnected");
			mBluetoothLeService = ((HelloBLEService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			if (mDevice != null) {
				mBluetoothLeService.connect(mDevice.getAddress());
			} else {
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
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read
	// or notification operations.
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
			} else if (HelloBLEService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				mGattServices = mBluetoothLeService.getSupportedGattServices();
				for (BluetoothGattService s : mGattServices) {
					// look for tuning service and store characteristic
					if (s.getUuid().equals(mBluetoothLeService.UUID_SERVICE)) {
						mGattCharacteristic = s
								.getCharacteristic(mBluetoothLeService.UUID_CHARACTERISTIC);
						break;
					} else {
						continue;
					}
				}
			} else if (HelloBLEService.ACTION_DATA_AVAILABLE.equals(action)) {
				// data read from device
				// String data =
				// intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
				// ((TextView)
				// findViewById(R.id.textView_username)).setText(data);
			}
		}
	};

	private void setUiState() {
		findViewById(R.id.send_data).setEnabled(mConnected);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		final View controlsView = findViewById(R.id.fullscreen_content_controls);
		final View contentView = findViewById(R.id.fullscreen_content);
		btnStartRecording = (Button) findViewById(R.id.start_recording);

		// Set up an instance of SystemUiHider to control the system UI for
		// this activity.
		mSystemUiHider = SystemUiHider.getInstance(this, contentView,
				HIDER_FLAGS);
		mSystemUiHider.setup();
		mSystemUiHider
				.setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
					// Cached values.
					int mControlsHeight;
					int mShortAnimTime;

					@Override
					@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
					public void onVisibilityChange(boolean visible) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
							// If the ViewPropertyAnimator API is available
							// (Honeycomb MR2 and later), use it to animate the
							// in-layout UI controls at the bottom of the
							// screen.
							if (mControlsHeight == 0) {
								mControlsHeight = controlsView.getHeight();
							}
							if (mShortAnimTime == 0) {
								mShortAnimTime = getResources().getInteger(
										android.R.integer.config_shortAnimTime);
							}
							controlsView
									.animate()
									.translationY(visible ? 0 : mControlsHeight)
									.setDuration(mShortAnimTime);
						} else {
							// If the ViewPropertyAnimator APIs aren't
							// available, simply show or hide the in-layout UI
							// controls.
							controlsView.setVisibility(visible ? View.VISIBLE
									: View.GONE);
						}
					}
				});

		// Set up the user interaction to manually show or hide the system UI.
		contentView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (TOGGLE_ON_CLICK) {
					mSystemUiHider.toggle();
				} else {
					mSystemUiHider.show();
				}
			}
		});

		// Upon interacting with UI controls, delay any scheduled hide()
		// operations to prevent the jarring behavior of controls going away
		// while interacting with the UI.
		btnStartRecording.setOnTouchListener(mDelayHideTouchListener);

		setup_tuning_variables();

		/* Ensure Bluetooth is enabled */
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBtAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available - exiting...",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		if (!mBtAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		} else {

		}

		setUiState();

		Intent gattServiceIntent = new Intent(this, HelloBLEService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		// init audio dispatcher
		dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(samplingFreq,
				bufferSize, bufferSize / 4);
		new Thread(dispatcher, "Audio Dispatcher").start();
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);

		// Trigger the initial hide() shortly after the activity has been
		// created, to briefly hint to the user that UI controls
		// are available.
		// delayedHide(100);
	}

	/**
	 * Touch listener to use for in-layout UI controls to delay hiding the
	 * system UI. This is to prevent the jarring behavior of controls going away
	 * while interacting with activity UI.
	 */
	View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View view, MotionEvent motionEvent) {
			if (AUTO_HIDE) {
				delayedHide(AUTO_HIDE_DELAY_MILLIS);
			}
			return false;
		}
	};

	Handler mHideHandler = new Handler();
	Runnable mHideRunnable = new Runnable() {
		@Override
		public void run() {
			mSystemUiHider.hide();
		}
	};

	/**
	 * Schedules a call to hide() in [delay] milliseconds, canceling any
	 * previously scheduled calls.
	 */
	private void delayedHide(int delayMillis) {
		mHideHandler.removeCallbacks(mHideRunnable);
		mHideHandler.postDelayed(mHideRunnable, delayMillis);
	}

	// Everything below is added

	// Create action bar menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.action_bluetooth:
			enableBluetooth();
			return true;
		case R.id.action_tuning:
			popup();
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
		stopRecording();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		if (mBluetoothLeService != null) {
			if (mDevice != null) {
				final boolean result = mBluetoothLeService.connect(mDevice
						.getAddress());
				Log.d(TAG, "Connect request result=" + result);
			}
		}
	}

	@Override
	protected void onDestroy() {
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
		dispatcher.stop();
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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

	private void enableBluetooth() {
		stopRecording();
		Intent newIntent = new Intent(MainActivity.this,
				DeviceListActivity.class);
		startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
	}

	public void toggleRecording(View view) {
		if (recording == true) {
			stopRecording();
		} else {
			startRecording();
		}
	}

	private void stopRecording() {
		recording = false;
		btnStartRecording.setText(R.string.start_recording);
		dispatcher.removeAudioProcessor(pitch);
		// freqFilter.clear();
		controlMotor((byte) 0, (byte) 0);
	}

	private void startRecording() {
		if (refFreq <= 0) {
			Toast.makeText(this, "Select a desired tuning frequency.",
					Toast.LENGTH_LONG).show();
			popup();
		} else {
			recording = true;
			btnStartRecording.setText(R.string.stop_recording);
			dispatcher.addAudioProcessor(pitch);
		}
	}

	public void popup() {
		// popup menu for string selection
		AlertDialog.Builder stringSelectionDialogBuilder = new AlertDialog.Builder(
				this);
		// getting string names (string1 to string 6)
		String[] stringArray = new String[g_size];
		for (int i = 0; i < g_size; i++) {
			stringArray[i] = String.format(getString(R.string.string_name),
					i + 1);
		}
		stringSelectionDialogBuilder.setTitle(R.string.pick_string);
		stringSelectionDialogBuilder.setItems(stringArray,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// popup menu for note selection
						AlertDialog.Builder noteSelectionDialogBuilder = new AlertDialog.Builder(
								MainActivity.this);
						// notes list from tuning_map based on string selected
						noteSelectionDialogBuilder.setTitle(R.string.pick_note);
						final int tuning_size = tuning_map.get(which).size();
						String[] tuningsArray = tuning_map.get(which).values()
								.toArray(new String[tuning_size - 1]);
						final int string_selection = which;
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
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));
		// string 2 notes
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));
		// string 3 notes
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));
		// string 4 notes
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));
		// string 5 notes
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));
		// string 6 notes (highest string)
		strings_list.add(Arrays.asList("C2", "C#2", "D2", "Eb2", "E2"));

		for (int i = 0; i < g_size; i++) {
			tuning_map.add(new LinkedHashMap<Double, String>());
			for (String n : strings_list.get(i)) {
				int cut_at = n.length() - 1;
				String note = n.substring(0, cut_at);
				int row = Integer.valueOf(n.substring(cut_at));
				double freq = note_table.get(note).get(row);
				String msg = String.format(getString(R.string.string_message),
						note + row, freq);
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
		Toast.makeText(
				getApplicationContext(),
				String.format("Tuning string %1$d to %2$.2f Hz",
						string_selection + 1, refFreq), Toast.LENGTH_LONG)
				.show();
	}
}