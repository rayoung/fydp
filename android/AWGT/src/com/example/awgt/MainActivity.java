package com.example.awgt;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
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
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

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
	
	// bluetooth adapter
	private final BluetoothAdapter BA = BluetoothAdapter.getDefaultAdapter();
	
	private final int samplingRate = 22050;
	private final int blockSize = 1024;
	
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
	PitchProcessor pitch = new PitchProcessor(PitchEstimationAlgorithm.YIN, samplingRate, blockSize, new PitchDetectionHandler() 
	{
		@Override
		public void handlePitch(PitchDetectionResult pitchDetectionResult,
				AudioEvent audioEvent) {
			float pitchInHz = pitchDetectionResult.getPitch();
			// pitchDetectionResult.getPitch() defaults to -1, so if it does not find a pitch
			if (pitchInHz < 0)
			{
				pitchInHz = 0;
			}
			final float freq = pitchInHz;
			runOnUiThread(new Runnable() {
			     @Override
			     public void run() {
			    	TextView text = (TextView) findViewById(R.id.fullscreen_content);
					text.setText(String.format("%.2f", freq));
			    }
			});
		}
	});
	
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
		
		dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(samplingRate,blockSize,0);	
		findViewById(R.id.send_data).setEnabled(false);
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

	@Override
	public void onPause() 
	{
		stopRecording();
		super.onPause();
	}

   @Override
    public void onResume() {
        super.onResume();
        Log.i("MAIN", "+ ON RESUME+");
    }

	private void enableBluetooth() 
	{
		if (recording == true) 
		{
			recording = false;
			btnStartRecording.setText(R.string.start_recording);
			dispatcher.removeAudioProcessor(pitch);
		} 
		Intent copy = new Intent(this, HelloBle.class);
		startActivity(copy);
	}
	
	public void toggleRecording(View view)
	{
		if (recording == true) 
		{
			stopRecording();
		}
		else
		{
			startRecording();
		}
	}
	
	private void stopRecording()
	{
		recording = false;
		btnStartRecording.setText(R.string.start_recording);
		dispatcher.removeAudioProcessor(pitch);
		Toast.makeText(getApplicationContext(), "done", Toast.LENGTH_LONG)
				.show();
			
	}
	private void startRecording() 
	{
		recording = true;
		btnStartRecording.setText(R.string.stop_recording);
		dispatcher.addAudioProcessor(pitch);
		new Thread(dispatcher,"Audio Dispatcher").start();
	}
}



