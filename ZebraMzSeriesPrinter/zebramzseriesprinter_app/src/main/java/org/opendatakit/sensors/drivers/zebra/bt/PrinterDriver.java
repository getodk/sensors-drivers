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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import android.os.Bundle;
import android.util.Log;

public class PrinterDriver extends AbstractDriverBaseV2  {

	public static final String TAG = "PrinterDriver";

	private static final int BARCODE_HEIGHT_DPI = 50;
	private static final int TEXT_HEIGHT_DPI = 25;
	private static final int QRCODE_MODULE_DPI = 6;

	public PrinterDriver() {
		Log.d(TAG," constructed.");
	}

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte[] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();

		return new SensorDataParseResponse(allData, new byte[]{});
	}

	@Override
	public byte[] sendDataToSensor(Bundle dataToFormat) {
		Log.d(TAG,"sendDataToSensor entered");

		int labelHeight = dataToFormat.getInt("LABEL-HEIGHT");
		String barcode = dataToFormat.getString("BARCODE");
		String qrcode = dataToFormat.getString("QRCODE");
		String[] strings = dataToFormat.getStringArray("TEXT-STRINGS");

		if ( barcode == null && qrcode == null && strings == null || strings.length == 0 ) {
			Log.d(TAG, "No data received by printer driver");
			return null;
		}

		int qrCodeHeight = 0;
		if (qrcode != null && qrcode.length() > 0) {

			// for Medium ECC, Alphanumeric
			int[] capacity = {
					 20,  38,  61,  90, 122, 154, 178, 221, 262, 311,
					366, 419, 483, 528, 600, 656, 734, 816, 909, 970,
					1035, 1134, 1248, 1326, 1451, 1542, 1637, 1732, 1839, 1994,
					2113, 2238, 2369, 2506, 2632, 2780, 2894, 3054, 3220, 3391 };

			// assume 20% overhead in string (conservative?)
			int storage = qrcode.length() + (qrcode.length()/5);
			int level;
			for (level = 0; level < capacity.length ; ++level ) {
				if ( capacity[level] > storage ) {
					break;
				}
			}
			int modules = 21 + 4*(level+1);

			qrCodeHeight = modules*QRCODE_MODULE_DPI + TEXT_HEIGHT_DPI;
		}

		if(labelHeight == 0) {

			labelHeight = 5;

			if(barcode != null && barcode.length() > 0) {
				labelHeight += BARCODE_HEIGHT_DPI;
			}

			if (qrcode != null && qrcode.length() > 0) {
				labelHeight += qrCodeHeight;
			}

			if (strings != null) {
				labelHeight += strings.length * TEXT_HEIGHT_DPI;
			}

			Log.d(TAG,"calculated label height: " + labelHeight);
		}

		StringBuilder printCmd = new StringBuilder();
		printCmd.append("! 0 200 200 " + labelHeight + " 1\r\n ON-FEED IGNORE\r\n ENCODING UTF-8\r\n");

		int yValue = 5;
		if(barcode != null && barcode.length() > 0) {
			printCmd.append("BARCODE 128 1 1 45 0 " + yValue + " " + barcode + "\r\n");
			yValue += BARCODE_HEIGHT_DPI;
		}

		if (qrcode != null && qrcode.length() > 0) {
			printCmd.append("BARCODE QR 0 " + yValue + " M 2 U " + QRCODE_MODULE_DPI + "\r\n");
			printCmd.append("MA,").append(qrcode).append("\r\n");
			printCmd.append("ENDQR\r\n");
			yValue += qrCodeHeight;
		}

		if (strings != null) {
			for(String str : strings) {
				printCmd.append("TEXT 7 0 0 " + yValue + " " + str +  " \r\n");
				yValue +=TEXT_HEIGHT_DPI;
			}
		}

		printCmd.append("PRINT \r\n");
		Log.d(TAG,"sendDataToSensor returning");

		byte[] bytes = null;
		try {
			bytes = printCmd.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return bytes;
	}

}
