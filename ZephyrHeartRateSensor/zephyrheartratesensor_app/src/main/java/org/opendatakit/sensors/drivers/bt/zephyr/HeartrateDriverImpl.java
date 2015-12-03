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

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.SensorParameter;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import android.os.Bundle;
import android.util.Log;

/*
 * Sensor drivers are implemented as Android Services and are accessed by ODK Sensors over the service interface. 
 * The generic classes that handle Android Service semantics are already implemented and available in the 
 * org.opendatakit.sensors.drivers package of the Sensors framework. 
 * 
 * Sensor driver developers are responsible for implementing a class (known as the driverImpl class) that decodes 
 * raw sensor data buffers received from sensors into higher level key-value pairs, and encodes key-value pairs 
 * into a buffer in a sensor-specific format that is sent to the sensor. The Sensors framework mediates the communications 
 * between physical sensors and their drivers. The driverImpl class needs to either implement the 
 * org.opendatakit.sensors.Driver interface or extend the org.opendatakit.sensors.drivers.AbstractDriverBaseV2 class (a convenience
 * class that implements the Driver interface) provided by ODKSensors. Please look at org.opendatakit.sensors.Driver.java for
 * a description of each method in the interface. 
 * 
 * ODK Sensors uses some metadata defined in the AndroidManifest.xml of the Android Application that implements a sensor driver
 * to discover and instantiate drivers. Please look at the manifest file of this project for meta-data elements that need to 
 * be defined by driver developers.
 *  
 */

/*
 * This file contains the driverImpl class for a sensor driver that communicates with Zephyr Heartrate monitors. The meta-data element 
 * called "ODK_sensors_driverImplClassname" in the manifest file refers to this class.
 * 
 */

public class HeartrateDriverImpl extends AbstractDriverBaseV2  {

	public static final String BEAT_COUNT = "BC";
	public  static final String HEART_RATE = "HR";
	
	private static final int ZEPHYR_PDU_SIZE = 60;
	private static final String TAG = "ZephyrHRSensorV2";

	public HeartrateDriverImpl() {
		super();
		
		// data reporting parameters. These are the key-value pairs returned by this driver.
		sensorParams.add(new SensorParameter(HEART_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Heart Rate"));
		sensorParams.add(new SensorParameter(BEAT_COUNT, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Beat counter that rolls over"));
		Log.d(TAG," constructed" );
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.opendatakit.sensors.drivers.AbstractDriverBaseV2#getSensorData(long, java.util.List, byte[])
	 */

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte[] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();		
		Log.d(TAG," sensor driver get dataV2. sdp list sz: " + rawData.size());
		List<Byte> dataBuffer = new ArrayList<Byte>();
		
		// Copy over the remaining bytes
		if(remainingData != null) {
			for (Byte b : remainingData) {
				dataBuffer.add(b);
			}
		}
		// Add the new raw data
		for(SensorDataPacket pkt: rawData) {
			byte [] payload = pkt.getPayload();
			Log.d(TAG, " sdp length: " + payload.length);

			for (int i = 0; i < payload.length; i++) {
				dataBuffer.add(payload[i]);
			}			
		}

		// Parse all data into packet sizes of 60 bytes
		int masked;
		while (dataBuffer.size() >= ZEPHYR_PDU_SIZE) {
			Log.d(TAG,"dataBuffer size: " + dataBuffer.size());	
			Bundle parsedPkt = new Bundle();
			allData.add(parsedPkt);

			for(int i = 0; i < ZEPHYR_PDU_SIZE; i++) {	
				byte b = dataBuffer.remove(0);	

				if(i == 12) {
					masked = b & 0xff;					
					parsedPkt.putInt(HEART_RATE, masked);
					Log.d(TAG,"V2 HR: " + masked);
				}
				else if(i == 13) {
					masked = b & 0xff;
					parsedPkt.putInt(BEAT_COUNT, masked);
					Log.d(TAG,"V2 BC: " + masked);
				}
			}
		}

		// Copy data back into remaining buffer
		byte[] newRemainingData = new byte[dataBuffer.size()];
		for (int i = 0; i < dataBuffer.size(); i++) {
			newRemainingData[i] = dataBuffer.get(i);
		}
		
		Log.d(TAG,"all done dataBuffer size: " + dataBuffer.size());		
		return new SensorDataParseResponse(allData, newRemainingData);	
	}	
}
