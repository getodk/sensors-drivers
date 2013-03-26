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

package org.opendatakit.sensors.drivers.zebra.bt;

import org.opendatakit.sensors.service.BaseActivity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

public class PrinterDriverActivity extends BaseActivity {
	
	/** Called when the activity is first created. */
	
	private static final String TAG = "PrintActivity";
	private static final String PRINTER_ID_STR = "printerID";
	private static final int SENSOR_CONNECTION_COUNTER = 10;	
	
	
//	private Button donor2Button, donor3Button;
	static String printerID = null;
	static Bundle printDataBundle = null;
	
	private static ConnectionThread connectionThread;
	static PrinterDriverActivity staticActivityInstance = null;
	
	@Override
	public void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		setContentView(R.layout.printview);		
		
		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);

		if(appPreferences.contains(PRINTER_ID_STR)) {
			printerID = appPreferences.getString(PRINTER_ID_STR, null);
			if(printerID != null) {
				Log.d(TAG,"restored printerID: " + printerID);
			}
		}
		
		staticActivityInstance = this;
		
//		printDataReceiver = new PrinterDataReceiver();
//		printDataReceiver.setParentActivity(this);
	}		
	
	public void print(View view) {
		

		if(printerID == null) {
			showDiscoveryDialogMsg();
			return;
		}

		try {	

			if (!isConnected(printerID)) {			
				startConnectionThread();
				return; 
			}

			if(printDataBundle != null) {
				super.sendDataToSensor(printerID, printDataBundle);
				//			printDataBundle.clear();
				//			printDataBundle = null;
			}
		}
		catch(RemoteException rex) {
			rex.printStackTrace();
		}
	}
	
	public void reconnectPrinter(View view) {
		if(printDataBundle != null) {
			
			printDataBundle.clear();
			printDataBundle = null;
		}
		
		showDiscoveryDialogMsg();
	}
	
	public void exitApplication(View view) {
		activityShutdownActions();
	}
	
	static void startConnectionThread() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}

		connectionThread = new ConnectionThread();
		connectionThread.start();
	}
	
	static void showDiscoveryDialogMsg() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(staticActivityInstance);
		
		// Positive Button & Negative Button & handler
		alert.setMessage("Click OK to install printer driver");
		alert.setPositiveButton("OK", discoveryDiagListener);
		alert.setNegativeButton("Cancel", discoveryDiagListener);
		alert.show();
	}
	
	private static final android.content.DialogInterface.OnClickListener discoveryDiagListener = new DialogInterface.OnClickListener() {

		public void onClick(DialogInterface dialog, int whichButton) {

			Log.d(TAG,"discovery dialog callback");
			// Lookup Views And Selector Item			

			switch (whichButton) {

			// On Positive Set The Text W/ The ListItem
			case DialogInterface.BUTTON_POSITIVE:
				staticActivityInstance.launchSensorDiscovery();
				// Just Leave On Cancel
			case DialogInterface.BUTTON_NEGATIVE:
				dialog.cancel();
				break;
			}
		}
	};		
	
	private void activityShutdownActions() {	
		
		try {
			if(printerID != null)
				stopSensor(printerID);
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}

//		unregisterReceiver(printDataReceiver);
		
		finish();
		super.onDestroy();
	}
	
	public static class PrinterDataReceiver extends BroadcastReceiver {
		
		private static final String TAG = "PrintDataReceiver";
		public static final String PRINT_INTENT_ACTION_DATA = "org.opendatakit.sensors.ZebraPrinter.data";
		
		/**
		 * Called when broadcast is received.
		 * 
		 * @param context
		 *            application context
		 * @param intent
		 *            broadcast intent
		 */
		@Override
		public void onReceive(Context context, Intent intent) {

			Log.d(TAG,"bcasst received");
			if(intent.getAction().equals(PRINT_INTENT_ACTION_DATA)) {
				Log.d(TAG,"got PRINT_INTENT_ACTION_DATA");
				printDataBundle = intent.getBundleExtra("DATA");
				
				if(printDataBundle != null) {
					Log.d(TAG,"label height: " + printDataBundle.getInt("LABEL-HEIGHT"));
					
//					if(printerID == null) {
//						Log.d(TAG,"printerID is null");
//						showDiscoveryDialogMsg();
//						return;
//					}
//			
//					try {	
//			
//						if (!staticActivityInstance.isConnected(printerID)) {			
//							startConnectionThread();
//							return; 
//						}
//			
//						staticActivityInstance.sendDataToSensor(printerID, printDataBundle);
//						printDataBundle.clear();
//						printDataBundle = null;
//					}
//					catch(RemoteException rex) {
//						rex.printStackTrace();
//					}
						
				}
				else {
					Log.e(TAG,"print data bundle missing!");
				}
			}			
		}
	}
	
	static class ConnectionThread extends Thread {
		private String TAG = "Printer ConnectionThread";
		private boolean isConnThreadRunning = false;
		private boolean isConnectedToSensor = false;

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
				staticActivityInstance.sensorConnect(printerID, false);		

				while (isConnThreadRunning && (connectCntr++ < SENSOR_CONNECTION_COUNTER)) {						
					try {					

						if(staticActivityInstance.isConnected(printerID)) {
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

				if(isConnectedToSensor && printDataBundle != null) {
					staticActivityInstance.sendDataToSensor(printerID, printDataBundle);
//					printDataBundle.clear();
//					printDataBundle = null;
				}
			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
	}
	
	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(TAG,"result code: " + resultCode + "  and  requestCode: "+ requestCode);

		if (requestCode == SENSOR_DISCOVERY_RETURN) {
			//from addSensorActvitity
			if (resultCode == RESULT_OK) {	
				// Get sensor id and state from result
				if (data.hasExtra("sensor_id"))
					printerID = data.getStringExtra("sensor_id");
				else
					printerID = null;

				if(printerID != null ) {
					Log.d(TAG, "sensor discovered: " + printerID);

					SharedPreferences.Editor prefsEditor = getPreferences(MODE_PRIVATE).edit();
					prefsEditor.putString(PRINTER_ID_STR, printerID);
					if(prefsEditor.commit()) 
						Log.d(TAG,"saved tempSensorID to preferences");
					else 
						Log.e(TAG,"preferences commit failed for tempSensorID");

					startConnectionThread();
				}
				else {
					Log.d(TAG,"activity result returned without sensorID");
				}
			}
		}
	}
}
