package com.example.awgt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.jtransforms.fft.DoubleFFT_1D;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.awgt.util.SystemUiHider;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class CopyOfMainActivity extends Activity {
	// start recording button
	private Button btnStartRecording;

	// get phone's bluetooth adapter
	private final BluetoothAdapter BA = BluetoothAdapter.getDefaultAdapter();

	// get initial paired devices
	final Set<BluetoothDevice> pairedDevices = BA.getBondedDevices();

	// create variables for audio recording
	private final int channel_config = AudioFormat.CHANNEL_IN_MONO;
	private final int aud_format = AudioFormat.ENCODING_PCM_16BIT;
	private final int sampleRate = 22050;
	private final int minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
			channel_config, aud_format);
	private int bufferSize;
	{
		if (1024 > minBufferSize)
		{
			bufferSize = 1024;
		}
		else
		{
			bufferSize = minBufferSize;
		}
	}
	
	private AudioRecord micInput = new AudioRecord(AudioSource.MIC, sampleRate,
			channel_config, aud_format, bufferSize);

	// recording toggle
	private boolean recording = false;
	
	// Tolerable error in Autocorrelation
	private final double SIMILARITY = 0.95;
	
	// recording thread
	Runnable rec_thread = new Runnable() {
		public void run() {
			recording_loop();
		}
	};

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

		// initially disable the start recording button depending on bonded
		// devices
		if (pairedDevices.isEmpty() || pairedDevices == null) {
			findViewById(R.id.send_data).setEnabled(false);
		}

		// set up broadcast receiver to change start recording button when
		// bluetooth connects
		IntentFilter filterConnected = new IntentFilter(
				BluetoothDevice.ACTION_ACL_CONNECTED);
		IntentFilter filterDisconnected = new IntentFilter(
				BluetoothDevice.ACTION_ACL_DISCONNECTED);
		IntentFilter filterDisconnecting = new IntentFilter(
				BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);

		this.registerReceiver(btReceiver, filterConnected);
		this.registerReceiver(btReceiver, filterDisconnected);
		this.registerReceiver(btReceiver, filterDisconnecting);
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
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void enableBluetooth() {
		/*Intent bluetooth = new Intent(
				android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
		startActivity(bluetooth);*/
		if (recording == true) 
		{
			recording = false;
			btnStartRecording.setText(R.string.start_recording);
			micInput.stop();
			Toast.makeText(getApplicationContext(), "done", Toast.LENGTH_LONG)
					.show();
		} 
		Intent copy = new Intent(this, CopyOfMainActivity.class);
		startActivity(copy);
	}

	// create bluetooth broadcast receiver to check when there is a change to
	// bluetooth
	private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			// enable mic button
			Button btn_enable_send = (Button) findViewById(R.id.send_data);

			// disable start recording button if there is no bluetooth adapter
			// or if it is not connected
			if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
				btn_enable_send.setEnabled(true);
			} else {
				btn_enable_send.setEnabled(false);
			}
		}
	};

	public void startRecording(View view) 
	{
		
		if (recording == true) 
		{
			recording = false;
			btnStartRecording.setText(R.string.start_recording);
			micInput.stop();
			Toast.makeText(getApplicationContext(), "done", Toast.LENGTH_LONG)
					.show();
		} 
		else 
		{
			// check if audio recorder is working
			if (micInput.getState() != AudioRecord.STATE_INITIALIZED)
				Toast.makeText(getApplicationContext(), R.string.error_audio,
						Toast.LENGTH_LONG).show();
			else 
			{
				recording = true;
				btnStartRecording.setText(R.string.stop_recording);
				new Thread(rec_thread).start();
			}
		}
		//test_fft();
	}

	private void recording_loop()
    {	
    	final TextView txtViewFreq = (TextView)findViewById(R.id.fullscreen_content); 
    	//storage for micInput
    	final int blockSize = bufferSize/2;
    	short[] bufferRead_short = new short[blockSize];
    	// toTransform is used for the fft transform. It is twice as long due to having an imaginary component
    	double[] toTransform = new double[2*blockSize];
    	DoubleFFT_1D fftInput = new DoubleFFT_1D(blockSize);

    	micInput.startRecording();
    	
    	while (recording)
    	{
    		System.gc();
        	//max index and frequency value
        	double max_val = -1.0;
        	double max_index = 0;
    		int bufferReadLength = micInput.read(bufferRead_short, 0, blockSize);
    		for (int i = 0; i < blockSize && i < bufferReadLength; i++) 
    		{
    			toTransform[i] = (double) bufferRead_short[i] / 32768.0; // divided by 32768 due to 16 bit PCM
            }
    		
    		Log.i("START FFT STUFF", "START FFT STUFF");
    		// from the docs, the Re component is stored in the even index, Im in the odd
    		// after transform: toTransform[2*k] = Re[k], toTransform[2*k+1] = Im[k]
            fftInput.realForwardFull(toTransform);
            // get fft spectrum
            double[] spectrum = new double[2*blockSize];
            for (int i = 1; i < toTransform.length; i++) 
            {
            	// compute F(f)*conj(F(f))
            	if (i%2 == 0)
            	{
            		// Re
            		spectrum[i] = toTransform[i] * toTransform[i] - toTransform[i+1] *toTransform[i+1];
            		
            		// get max index of the Re component to find out dominant frequency
                	if (spectrum[i] > max_val && spectrum[i] > 100)
                	{
                		max_val = spectrum[i];
                		max_index = i;
                	}
            	}
            	else
            	{
            		// Im
            		spectrum[i] = 2 * toTransform[i] * toTransform[i-1];
            	}
            }
            
            final double dom_freq = (sampleRate * max_index) / (blockSize * 2); // dominant frequency from fft
            fftInput.realInverse(spectrum,true);
    		/*double[] spectrum = new double[blockSize];
            int n = blockSize;
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    spectrum[j] += toTransform[i] * toTransform[(n + i - j) % n];
                }
            }*/
            
        	List<Integer> peak_indices = new ArrayList<Integer>();
        	List<Double> peak_values = new ArrayList<Double>();
        	// ac_index and ac_peak store max index and max peak of the autocorrelation
        	double ac_peak = 0.0;
        	double threshold = spectrum[0] * 0.6;
        	double autocorr_freq = 0;
        	long fund_freq = 0;

            // finding the secondary peak 
            boolean decreasing = false;
            int loop_count = 1;
            int increase_count = 0;

            // get the index and values of local maxima
            while (peak_values.size() <= 20 && loop_count < (spectrum.length - 1)) 
            {
            	if (spectrum[loop_count] > threshold || spectrum[loop_count - 1] > threshold)
            	{
	            	// spectrum is decreasing and it wasn't previously decreasing
	            	if (spectrum[loop_count] > spectrum[loop_count + 1] && !decreasing && increase_count >= 0)
	            	{
	            		peak_indices.add(loop_count);
	            		peak_values.add(ac_peak);
	            		if (spectrum[loop_count] > ac_peak)
	            		{
	            			ac_peak = spectrum[loop_count];
	            			threshold = ac_peak * 0.6;
	            			
	            		}
	            		decreasing = true;
	            	}
	            	// spectrum is increasing and is previously decreasing
	            	else if (spectrum[loop_count] < spectrum[loop_count + 1] && decreasing)
	            	{
	            		increase_count = 0;
	            		decreasing = false;
	            	}
	            	// on the increase
	            	else if (spectrum[loop_count] < spectrum[loop_count + 1] && !decreasing)
	            	{
	            		increase_count++;
	            	}
            	}
            	loop_count++;
            }
            
            // get the autocorrelation
            int index_diff = 0;
            
            if (!peak_values.isEmpty())
            {
            	autoloop:
        		for(int i=0; i<peak_values.size() - 1; i++)
	            {	
        			int current_index = peak_indices.get(i);
        			
        			if (peak_values.get(i) == ac_peak)
        			{
        				for(int j=i+1; i<peak_values.size()- 2; j++)
        				{
        					int next_index = peak_indices.get(j);
        					if (peak_values.get(j) == ac_peak)
        					{
        						index_diff = Math.abs(next_index - current_index);
    			            	autocorr_freq = sampleRate / index_diff;
    			            	break autoloop;
        					}
        				}
        			}
	            }
            }

            Log.i("DONE FFT STUFF", "DONE FFT STUFF");
            final double ac_freq = autocorr_freq; // frequency from autocorrelation
            final double freq = fund_freq; //used to display fund_freq              
            
            final double max_value = max_val;
            runOnUiThread(new Runnable() 
            {
                @Override
                public void run() 
                {
                	txtViewFreq.setText(String.format("AC: %.2f", ac_freq));
                	//txtViewFreq.setText(String.format("Dom:%.2f, Val:%.2f", dom_freq, max_value));
                	//txtViewFreq.setText(String.format("Dom:%.2f \n AC: %.2f \n  Fund: %.2f", dom_freq, ac_freq, freq));
            		//txtViewFreq.setText(String.format("Dom:%.2f \n ACFFT: %.2f \n ACBrute: %.2f \n Fund: %.2f", dom_freq, ac_freq, ac_freq_brute, freq));
                }
            });
    	}
    }
	
	private boolean Similar(double a, double b)
	{
		double ratio = a/b;
		if(ratio >= SIMILARITY && ratio <= (1/SIMILARITY))
		{
			return true;
		}
		else
		{
			return false;
		}
	}
}