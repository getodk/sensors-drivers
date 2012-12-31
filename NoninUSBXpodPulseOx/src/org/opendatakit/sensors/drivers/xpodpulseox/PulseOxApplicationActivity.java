/*
 * Copyright (C) 2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.sensors.drivers.xpodpulseox;

import java.util.List;

import org.opendatakit.sensors.service.BaseActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

public class PulseOxApplicationActivity extends BaseActivity {

	// Tag for Logging
	private static final String TAG = "PulseOxApp";
	private static final String OX_SENSOR_ID_STR = "tempSensorID";

	private String pulseOxId;

	private TextView pulseTxt;
	private TextView oxTxt;

	private Integer mAnswerOx;
	private Integer mAnswerPulse;

	private DataProcessor pulseOxProcessor;

	private boolean isConnected;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		pulseTxt = (TextView) findViewById(R.id.pulseReading);
		oxTxt = (TextView) findViewById(R.id.oxygenReading);

		// restore stored preferences if any
		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);
		if (appPreferences.contains(OX_SENSOR_ID_STR)) {
			pulseOxId = appPreferences.getString(OX_SENSOR_ID_STR, null);
			if (pulseOxId != null) {
				Log.d(TAG, "restored pulseOxId: " + pulseOxId);
			}
		}

		isConnected = false;
		mAnswerOx = 0;
		mAnswerPulse = 0;
		pulseOxProcessor = new DataProcessor(this);
		pulseOxProcessor.execute();

		Log.d(TAG, "on create");
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");

		// stop the processor of data
		pulseOxProcessor.cancel(true);
		super.onDestroy();
	}

	public void connectButtonAction(View view) {
		Log.d(TAG, "connect button pressed");

		if (pulseOxId == null) {
			launchSensorDiscovery();
		} else {
			connectPulseOx();
		}
	}

	private void connectPulseOx() {

		if (pulseOxId == null) {
			Log.e(TAG, "ERROR: Somehow tried to connect when no ID is present");
		}

		try {
			if (!isConnected(pulseOxId)) {
				Log.d(TAG, "connecting to sensor: " + pulseOxId);
				sensorConnect(pulseOxId, false);
			}
			if (isConnected(pulseOxId)) {
				Log.d(TAG, "starting pulse ox sensor: " + pulseOxId);
				isConnected = true;
				startSensor(pulseOxId);
			} else {
				Log.d(TAG, "Trouble in connecting to pulseOx sensor");
			}
		} catch (RemoteException rex) {
			rex.printStackTrace();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		super.onActivityResult(requestCode, resultCode, data);

		Log.d(TAG, "onActivityResult resultCode" + resultCode
				+ "  and  requestCode" + requestCode);

		if (requestCode == SENSOR_DISCOVERY_RETURN) {
			// from addSensorActvitity
			if (resultCode == RESULT_OK) {
				// Get sensor id and state from result
				if (data.hasExtra("sensor_id")) {
					pulseOxId = data.getStringExtra("sensor_id");

					// update sensor id stored in preferences
					SharedPreferences.Editor prefsEditor = getPreferences(
							MODE_PRIVATE).edit();
					prefsEditor.putString(OX_SENSOR_ID_STR, pulseOxId);
					prefsEditor.commit();

					// connect to sensor
					connectPulseOx();
				} else {
					Log.d(TAG, "activity result returned without sensorID");
				}
			}
		}
	}

	private void processData() {

		// ensure sensor has been connected
		if (!isConnected) {
			return;
		}
		runOnUiThread(new Runnable() {
			public void run() {
				try {
					List<Bundle> data = getSensorData(pulseOxId, 1);
					if (data != null) {
						for (Bundle b : data) {
							if (b.containsKey("connected")) {
								boolean connected = b.getBoolean("connected");
								if (!connected)
									continue;
							}
							if (b.containsKey("usable")) {
								boolean usable = b.getBoolean("usable");
								if (usable) {
									pulseTxt.setTextColor(Color.MAGENTA);
									oxTxt.setTextColor(Color.MAGENTA);
								} else {
									pulseTxt.setTextColor(Color.GREEN);
									oxTxt.setTextColor(Color.BLUE);
								}
							}
							if (b.containsKey("pulse")) {
								int pulse = b.getInt("pulse");
								pulseTxt.setText(Integer.toString(pulse));
								mAnswerPulse = pulse;
								Log.d(TAG, "Got new pulse: " + pulse);
							}
							if (b.containsKey("ox")) {
								int ox = b.getInt("ox");
								oxTxt.setText(Integer.toString(ox));
								mAnswerOx = ox;
								//vibrator.vibrate(75);
								Log.d(TAG, "Got new oxygen: " + ox);
							}
						}

					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Intent intent = new Intent();
			intent.putExtra(NoninPacket.OX, mAnswerOx);
			intent.putExtra(NoninPacket.PULSE, mAnswerPulse);
			setResult(RESULT_OK, intent);
			finish();
			break;
		}

		return super.onKeyDown(keyCode, event);
	}

	private class DataProcessor extends AsyncTask<Void, Void, Void> {

		private PulseOxApplicationActivity app;

		public DataProcessor(PulseOxApplicationActivity app) {
			this.app = app;
		}

		@Override
		protected Void doInBackground(Void... params) {
			while (!isCancelled()) {
				app.processData();
				try {
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return null;
		}

	}
}