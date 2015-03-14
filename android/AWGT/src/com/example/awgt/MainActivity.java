package com.example.awgt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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

	// Tuning variable

	private double tune_to_freq = 0;

	// parameters
	private final int samplingFreq = 44100;
	private final int bufferSize = 2048;

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
					float pitchInHz = pitchDetectionResult.getPitch();
					float prob = pitchDetectionResult.getProbability();

					Log.i("sample",
							String.format("f = %f, p = %f", pitchInHz, prob));

					if (pitchInHz < 0) {
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

		dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(samplingFreq,
				bufferSize, bufferSize / 4);
		new Thread(dispatcher, "Audio Dispatcher").start();
		findViewById(R.id.send_data).setEnabled(false);
		setup_tuning_variables();
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
	public void onPause() {
		stopRecording();
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i("MAIN", "+ ON RESUME+");
	}

	@Override
	public void onDestroy() {
		dispatcher.stop();
		super.onDestroy();
	}

	private void enableBluetooth() {
		if (recording == true) {
			recording = false;
			btnStartRecording.setText(R.string.start_recording);
			dispatcher.removeAudioProcessor(pitch);
		}
		Intent copy = new Intent(this, HelloBle.class);
		startActivity(copy);
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
		// Toast.makeText(getApplicationContext(), "done",
		// Toast.LENGTH_LONG).show();
	}

	private void startRecording() {
		recording = true;
		btnStartRecording.setText(R.string.stop_recording);
		dispatcher.addAudioProcessor(pitch);
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
										// setting the tuning freq based on choice
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

	public void setup_tuning_variables() {
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
		tune_to_freq = note_table.get(note).get(row);
		Toast.makeText(
				getApplicationContext(),
				String.format("Tuning string %1$d to %2$.2f Hz",
						string_selection + 1, tune_to_freq), Toast.LENGTH_LONG)
				.show();
	}
}