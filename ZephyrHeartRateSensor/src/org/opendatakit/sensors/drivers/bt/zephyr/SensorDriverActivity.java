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

package org.opendatakit.sensors.drivers.bt.zephyr;

import java.util.List;

import org.opendatakit.sensors.service.BaseActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SensorDriverActivity extends BaseActivity {
	
	private static final String HR_SENSOR_ID_STR = "ZEPHYR_SENSOR_ID";
	private static final String TAG = "SensorDriverActivity";
	private static final int SENSOR_CONNECTION_COUNTER = 10;
	
	private String sensorID = null;
	private boolean isConnectedToSensor = false;
	private boolean isStarted = false;
	private volatile int heartRate, beatCount;
	private DataProcessor sensorDataProcessor;
	private Button connectButton, startButton, recordButton;
	private TextView heartRateField;
	
	private ConnectionThread connectionThread;
	
	/** Called when the activity is first created. */
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mainview);
		
		connectButton = (Button)findViewById(R.id.connectButton);
		startButton = (Button)findViewById(R.id.startButton);
		recordButton = (Button)findViewById(R.id.recordButton);
		heartRateField = (TextView) findViewById(R.id.heartRateField);
		
		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);
		
		if(appPreferences.contains(HR_SENSOR_ID_STR)) {
			sensorID = appPreferences.getString(HR_SENSOR_ID_STR, null);
			
			if(sensorID != null) {
				Log.d(TAG,"restored sensorID: " + sensorID);
			}
		}
		
	}
	
	protected void onResume() {
		super.onResume();
		
		sensorDataProcessor = new DataProcessor();
		sensorDataProcessor.execute();
		
		connectButton.setEnabled(false);
		startButton.setEnabled(false);	
		recordButton.setEnabled(false);
		
		
		if(!isConnectedToSensor) {
			connectButton.setEnabled(true);
			startButton.setEnabled(false);	
			recordButton.setEnabled(false);
		}
		else if (isStarted) { 
			connectButton.setEnabled(false);
			startButton.setEnabled(false);	
			recordButton.setEnabled(true);
		}
		else {
			connectButton.setEnabled(false);
			startButton.setEnabled(true);	
			recordButton.setEnabled(false);
		}
			
	}
	
    protected void onPause() {
        super.onPause();
		sensorDataProcessor.cancel(true);
    }
	
	public void connectAction(View view) {

		if(sensorID == null) {
			launchSensorDiscovery();
			return;
		}

		if (!isConnectedToSensor) {			
			connectToSensor();
		}
	}
	
	public void startAction(View view) {
		
		try {
			startSensor(sensorID);
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
		
		startButton.setEnabled(false);
		recordButton.setEnabled(true);
		isStarted = true;
		
	}
	
	public void recordAction(View view) {		
		returnSensorDataToCaller();
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		returnSensorDataToCaller();
	}
	
	@Override
	public void onDestroy() {
		
		try {
			stopSensor(sensorID);
			isStarted = false;
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
		
		super.onDestroy();
	}
	
	private void returnSensorDataToCaller() {
		Intent intent = new Intent();
		intent.putExtra(ZephyrHRSensor.HEART_RATE, heartRate);
		intent.putExtra(ZephyrHRSensor.BEAT_COUNT, beatCount);
		setResult(RESULT_OK, intent);
		
		finish();
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
					sensorID = data.getStringExtra("sensor_id");

					// update sensor id stored in preferences
					SharedPreferences.Editor prefsEditor = getPreferences(MODE_PRIVATE).edit();
					prefsEditor.putString(HR_SENSOR_ID_STR, sensorID);
					prefsEditor.commit();

					// connect to sensor
					connectToSensor();
				} else {
					Log.d(TAG, "activity result returned without sensorID");
				}
			}
		}
	}
	
	private void connectToSensor() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}
		
		runOnUiThread(new Runnable() {
			public void run() {
				connectButton.setEnabled(false);
			}
		});

		connectionThread = new ConnectionThread();
		connectionThread.start();
	}
	
	private void processData() {

		if (!isConnectedToSensor) {
			return;
		}				

		runOnUiThread(new Runnable() {
			public void run() {
				List<Bundle> sensorDataBundles = null;

				try {
					Log.d(TAG,"getSensorData");
					sensorDataBundles = getSensorData(sensorID, 1);
				}
				catch(RemoteException rex) {
					rex.printStackTrace();
				}

				if(sensorDataBundles != null) {
					for(Bundle aBundle : sensorDataBundles) {
						heartRate = aBundle.getInt(ZephyrHRSensor.HEART_RATE);
						beatCount = aBundle.getInt(ZephyrHRSensor.BEAT_COUNT);
						
						heartRateField.setText(String.valueOf(heartRate));
					}
				}
			}
		});
	}
	
	private class DataProcessor extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			while (!isCancelled()) {
				processData();
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}
	
	private class ConnectionThread extends Thread {
		private String TAG = "ConnectionThread";
		private boolean isConnThreadRunning = false;		

		public ConnectionThread() {
			super("Printer Connection Thread");
		}

		@Override
		public void start() {
			isConnThreadRunning = true;
			super.start();
		}

		public void stopConnectionThread() {
			isConnThreadRunning = false;

			try {
				this.interrupt();
				isConnectedToSensor = false;
				Thread.sleep(250);
			}
			catch(InterruptedException iex) {
				Log.d(TAG,"stopConnectionThread got interrupted");
			}
		}

		public void run() {
			int connectCntr = 0;

			try {
				sensorConnect(sensorID, false);

				while (isConnThreadRunning && (connectCntr++ < SENSOR_CONNECTION_COUNTER)) {
					try {

						if(isConnected(sensorID)) {
							isConnectedToSensor = true;
							break;
						}
						Log.d(TAG,"connectThread waiting to connect to sensor");
						Thread.sleep(1000);
					}
					catch(InterruptedException iex) {
						Log.d(TAG, "interrupted");
					}
				}

				Log.d(TAG,"connectThread connect status: " + isConnectedToSensor);
				
				runOnUiThread(new Runnable() {
					public void run() {
						if(!isConnectedToSensor) {
							connectButton.setEnabled(true);
							startButton.setEnabled(false);							
						}
						else {
							connectButton.setEnabled(false);
							startButton.setEnabled(true);							
						}
					}
				});

			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}
}
