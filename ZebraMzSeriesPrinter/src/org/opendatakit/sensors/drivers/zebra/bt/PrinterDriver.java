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

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import android.os.Bundle;
import android.util.Log;

public class PrinterDriver extends AbstractDriverBaseV2  {

	public static final String TAG = "PrinterDriver";
	
	public PrinterDriver() {		
		Log.d(TAG," constructed.");
	}

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte[] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();		
		
		return new SensorDataParseResponse(allData, null);	
	}

	@Override
	public byte[] sendDataToSensor(Bundle dataToFormat) {
		Log.d(TAG,"sendDataToSensor entered");
		String printCmd = new String();
		
		int labelLength = dataToFormat.getInt("LABEL-HEIGHT");
		if(labelLength == 0) {
			Log.d(TAG,"LABEL-HEIGHT not specified. returning null");
			return null; //rather than return null, driver should throw exception if certain required fields are missing.
		}
		
		printCmd = "! 0 200 200 " + labelLength + " 1\r\n ON-FEED IGNORE\r\n";				

		int yValue = 10;
		String barcode = dataToFormat.getString("BARCODE");
		if(barcode != null) {
			printCmd += "BARCODE 128 1 1 50 0 " + yValue + " " + barcode + "\r\n";		
			yValue += 80;
		}
		
		Bundle textStrings = dataToFormat.getBundle("TEXT-STRINGS");
		if(textStrings != null) {
			
			for(int i = 0; i < textStrings.size(); i++) {
				String str = textStrings.getString(Integer.toString(i+1));
				if(str == null) {
					Log.d(TAG,"Reguired string #" + (i+1) + " not specified. returning null");
					return null;
				}
				//else 
				printCmd += "TEXT 7 0 0 " + yValue + " " + str +  " \r\n";
				yValue +=30;
			}
		}
								
		printCmd += "PRINT \r\n";
		Log.d(TAG,"sendDataToSensor returning");
		
		return printCmd.getBytes();
	}

}
