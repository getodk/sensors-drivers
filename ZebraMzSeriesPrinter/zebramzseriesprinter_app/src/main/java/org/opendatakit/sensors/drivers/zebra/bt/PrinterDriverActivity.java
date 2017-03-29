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

/**
 *
 * @author wbrunette@gmail.com
 * @author rohitchaudhri@gmail.com
 *
 */

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
import android.widget.Button;

public class PrinterDriverActivity extends BaseActivity {

	/** Called when the activity is first created. */

	private static final String TAG = "PrintActivity";
	private static final String PRINTER_ID_STR = "printerID";
	private static final int SENSOR_CONNECTION_COUNTER = 10;


	private Button reconnectPrinterButton;
	private String printerID = null;
	static Bundle printDataBundle = null;

	private ConnectionThread connectionThread;

	@Override
	public void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		setContentView(R.layout.printview);

		reconnectPrinterButton = (Button)findViewById(R.id.reconnectPrinter);
		reconnectPrinterButton.setEnabled(false);

		SharedPreferences appPreferences = getPreferences(MODE_PRIVATE);

		if(appPreferences.contains(PRINTER_ID_STR)) {
			printerID = appPreferences.getString(PRINTER_ID_STR, null);
			if(printerID != null) {
				Log.d(TAG,"restored printerID: " + printerID);
				reconnectPrinterButton.setEnabled(true);
			}
		}
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
				sendDataToSensor(printerID, printDataBundle);
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

	@Override
	public void onBackPressed() {
		Log.d(TAG,"onBackPressed");
		activityShutdownActions();
	}

	public void exitApplication(View view) {
		activityShutdownActions();
	}

	private void startConnectionThread() {
		if(connectionThread != null && connectionThread.isAlive()) {
			connectionThread.stopConnectionThread();
		}

		connectionThread = new ConnectionThread();
		connectionThread.start();
	}

	private void showDiscoveryDialogMsg() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		// Positive Button & Negative Button & handler
		alert.setMessage(getString(R.string.click_to_install_printer));
		alert.setPositiveButton(getString(R.string.ok), discoveryDiagListener);
		alert.setNegativeButton(getString(R.string.cancel), discoveryDiagListener);
		alert.show();
	}

	private final android.content.DialogInterface.OnClickListener discoveryDiagListener = new DialogInterface.OnClickListener() {

		public void onClick(DialogInterface dialog, int whichButton) {

			Log.d(TAG,"discovery dialog callback");
			// Lookup Views And Selector Item

			switch (whichButton) {

			// On Positive Set The Text W/ The ListItem
			case DialogInterface.BUTTON_POSITIVE:
				launchSensorDiscovery(null);
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

		finish();
		super.onDestroy();
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

					reconnectPrinterButton.setEnabled(true);
					startConnectionThread();
				}
				else {
					Log.d(TAG,"activity result returned without sensorID");
				}
			}
		}
	}

	private class ConnectionThread extends Thread {
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
				sensorConnect(printerID);

				while (isConnThreadRunning && (connectCntr++ < SENSOR_CONNECTION_COUNTER)) {
					try {

						if(isConnected(printerID)) {
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
					sendDataToSensor(printerID, printDataBundle);
				}
			}
			catch(RemoteException rex) {
				rex.printStackTrace();
			}
		}
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

			Log.d(TAG,"bcast received");
			if(intent.getAction().equals(PRINT_INTENT_ACTION_DATA)) {
				Log.d(TAG,"got PRINT_INTENT_ACTION_DATA");
				printDataBundle = intent.getBundleExtra("DATA");

				if(printDataBundle != null) {
					Log.d(TAG,"label height: " + printDataBundle.getInt("LABEL-HEIGHT"));
				}
				else {
					Log.e(TAG,"print data bundle missing!");
				}
			}
		}
	}
}
