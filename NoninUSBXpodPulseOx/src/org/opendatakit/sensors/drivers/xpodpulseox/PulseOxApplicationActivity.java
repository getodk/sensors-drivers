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

import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.widget.Widget;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XLayoutStyle;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.YLayoutStyle;

public class PulseOxApplicationActivity extends BaseActivity {

	// Tag for Logging
	private static final String TAG = "PulseOxApp";
	private static final String OX_SENSOR_ID_STR = "tempSensorID";
	
	private static final int MAX_DATAPOINTS = 2000;

	private String pulseOxId;

	private TextView pulseTxt;
	private TextView oxTxt;

	private Integer mAnswerOx;
	private Integer mAnswerPulse;

	private DataProcessor pulseOxProcessor;

	private boolean isConnected;

	//plots for plotting the data from the oxygen sensor
	private SimpleXYSeries oxygenSeries;
	private XYPlot dataPlot;
	private int dataPointCounter = 0;
		
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		pulseTxt = (TextView) findViewById(R.id.pulseReading);
		oxTxt = (TextView) findViewById(R.id.oxygenReading);
		dataPlot = (XYPlot) findViewById (R.id.dataPlot);
		
 	    Widget domainLabelWidget = dataPlot.getDomainLabelWidget();
 		
        dataPlot.position(domainLabelWidget,                     // the widget to position
                                 0,                                    // x position value, in this case 45 pixels
                                 XLayoutStyle.ABSOLUTE_FROM_LEFT,       // how the x position value is applied, in this case from the left
                                 0,                                     // y position value
                                 YLayoutStyle.ABSOLUTE_FROM_BOTTOM,     // how the y position is applied, in this case from the bottom
                                 AnchorPosition.LEFT_BOTTOM);           // point to use as the origin of the widget being positioned

        dataPlot.setRangeBoundaries(87, 99, BoundaryMode.GROW);
        // get rid of the visual aids for positioning:
        dataPlot.disableAllMarkup();
		
		oxygenSeries = new SimpleXYSeries("Oxygen");
 		dataPlot.addSeries(oxygenSeries,new LineAndPointFormatter(Color.BLUE, Color.BLUE, Color.TRANSPARENT));
 		dataPointCounter = 0;
 		
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


		Log.d(TAG, "on create");
	}
	
	protected void onResume() {
		super.onResume();
		pulseOxProcessor = new DataProcessor(this);
		pulseOxProcessor.execute();
	}
	
    protected void onPause() {
        super.onPause();

		// stop the processor of data
		pulseOxProcessor.cancel(true);
    }
    
	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");


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

	public void recordButtonAction(View view) {
		Log.d(TAG, "record button pressed");
		returnValuetoCaller();
	}
	
	private void connectPulseOx() {
		
		if (pulseOxId == null) {
			Log.e(TAG, "ERROR: Somehow tried to connect when no ID is present");
		}

		try {
			if (!isConnected(pulseOxId)) {
				Log.d(TAG, "connecting to sensor: " + pulseOxId);
				sensorConnect(pulseOxId, false);
				pulseTxt.setText("IN CONNECTING");
			}
			if (isConnected(pulseOxId)) {
				Log.d(TAG, "starting pulse ox sensor: " + pulseOxId);
				isConnected = true;
				startSensor(pulseOxId);
				pulseTxt.setText("IN STARTING");
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
								
								if (!connected) {
									pulseTxt.setText("DEVICE READS NOT CONNECTED");
									continue;
								}
							}
							if (b.containsKey("usable")) {
								boolean usable = b.getBoolean("usable");
								if (usable) {
									pulseTxt.setTextColor(Color.MAGENTA);
									oxTxt.setTextColor(Color.MAGENTA);
								} else {
									pulseTxt.setTextColor(Color.GREEN);
									oxTxt.setTextColor(Color.BLUE);
									int ox = b.getInt("ox");
	    							oxygenSeries.addLast(dataPointCounter, ox);
	    			    			dataPointCounter++;
	    			    			dataPlot.redraw();
	    							if(dataPointCounter > MAX_DATAPOINTS) { 
	    								oxygenSeries.removeFirst();
	    							}

								}
	    						
							}
								
							if (b.containsKey("pulse")) {
								int pulse = b.getInt("pulse");
								if(pulse == 511) {
									pulseTxt.setText("Error");
								} else {
									pulseTxt.setText(Integer.toString(pulse));
								}
								mAnswerPulse = pulse;
								Log.d(TAG, "Got new pulse: " + pulse);
							}
							if (b.containsKey("ox")) {
								int ox = b.getInt("ox");
								if(ox == 127) {
									oxTxt.setText("Error");
								} else {
									oxTxt.setText(Integer.toString(ox));
								}
								mAnswerOx = ox;
								//vibrator.vibrate(75);
								Log.d(TAG, "Got new oxygen: " + ox);
							}
						}

					}
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			returnValuetoCaller();
			break;
		}

		return super.onKeyDown(keyCode, event);
	}

	private void returnValuetoCaller() {
		Intent intent = new Intent();
		intent.putExtra(NoninPacket.OX, mAnswerOx);
		intent.putExtra(NoninPacket.PULSE, mAnswerPulse);
		setResult(RESULT_OK, intent);
		finish();
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