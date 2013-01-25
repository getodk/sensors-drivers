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

package org.opendatakit.sensors.drivers.usb.temperature;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opendatakit.sensors.DataSeries;
import org.opendatakit.sensors.ParameterMissingException;
import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.SensorParameter;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;
import org.opendatakit.sensors.drivers.USBParamUtil;

import android.os.Bundle;
import android.util.Log;

public class UsbTemperatureSensor extends AbstractDriverBaseV2 {

	private static final String SAMPLING_RATE = "SR";
	private static final String READ_RATE = "RR";
	private static final String ALARM_THRESHOLD = "AT";

	private static final String RAW_LOW = "raw_low";
	private static final String RAW_HI = "raw_hi";
	
	private static final String TAG = "TemperatureSensor";
	private float SENSOR_RESOLUTION = 0.0625F; // 12 bit precision ds18b20
												// sensor
	private int DATABITSMASK = 0x7FF; // 11 bits of data excluding the sign bit
	private int SIGNBITMASK = 0x800; // 12th bit is the sign bit
	private char signchr = '+';

	public UsbTemperatureSensor() {
		super();
		
		// configure parameters
		sensorParams.add(new SensorParameter(SAMPLING_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.CONFIG, "Sensor sampling rate"));
		sensorParams.add(new SensorParameter(READ_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.CONFIG, "Rate at which readings are proccessed"));
		sensorParams.add(new SensorParameter(ALARM_THRESHOLD, SensorParameter.Type.INTEGER, SensorParameter.Purpose.CONFIG, "Alarm threshold value"));

		// data reporting parameters
		sensorParams.add(new SensorParameter(DataSeries.SERIES_TIMESTAMP, SensorParameter.Type.LONG, SensorParameter.Purpose.DATA, "Series Timestamp"));
		sensorParams.add(new SensorParameter(DataSeries.SAMPLE, SensorParameter.Type.STRING, SensorParameter.Purpose.DATA, "Data Sample"));
		sensorParams.add(new SensorParameter(RAW_LOW, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Low raw byte value"));
		sensorParams.add(new SensorParameter(RAW_HI, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "High raw byte value"));
	}

	@Override
	public byte[] startCmd() {
		byte[] payload = new byte[1];
		payload[0] = DataSeries.START_SENSOR;
		return payload;
	}

	@Override
	public byte[] stopCmd() {
		byte[] payload = new byte[1];
		payload[0] = DataSeries.STOP_SENSOR;
		return payload;
	}

	@Override
	public byte[] configureCmd(String setting, Bundle params) 
	throws ParameterMissingException {
		if(setting.equals(SAMPLING_RATE)) {
			int samplingRate = params.getInt(SAMPLING_RATE); //sampling rate
			return USBParamUtil.createSamplingRateMsg(samplingRate);
		} else if(setting.equals(READ_RATE)) {
			int readRate = params.getInt(READ_RATE); //reading rate
			return USBParamUtil.createReadRateMsg(readRate);
		} else if(setting.equals(ALARM_THRESHOLD)) {
			int readRate = params.getInt(ALARM_THRESHOLD); //alarm threshold
			return USBParamUtil.createAlertThresholdMsg(readRate);
		}
		throw new ParameterMissingException("Unknown Setting");
	}

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte [] remainingData) {
		// returning ALL data for now
		// returns a comma separated string of readings
		// cntr++;
		// Log.e(TAG," getSensorData. cntr: " + cntr);
		List<Bundle> allData = new ArrayList<Bundle>();

		for (Iterator<SensorDataPacket> iter = rawData.iterator(); iter
				.hasNext();) {
			List<Bundle> data = parsePayload(iter.next());
			allData.addAll(data);
			iter.remove();// XXX delete from local buffer??
		}

		return new SensorDataParseResponse(allData, null);
	}

	public List<Bundle> parsePayload(SensorDataPacket sdp) {
		// 4 bytes timestamp, 1 byte numSamples, 6 bytes each for each temp sample: 
		//4 bytes timestamp, 2 byte temp reading
				
		byte [] dataseries = sdp.getPayload();
		long seriesTimestamp = sdp.getTime();
		int numSamples = sdp.getSizeOfSeries();	
		
		List<Bundle> samples = getTempSamples(dataseries,numSamples);
		Log.d(TAG, "numSamples: " + numSamples + " timestamp: " + seriesTimestamp 
				+ " parsed bundles: " + samples.size());
		
		return samples;
		
//		String tempStrCSV = getTempStrCSV(dataseries, numSamples);
//		Log.d(TAG, "CSV: " + tempStrCSV);
//
//		aBundle.putString(DataSeries.MSG_TYPE, "report"); // another type would
//															// be alert.
//		aBundle.putString(DataSeries.SENSOR_TYPE, "temp sensor");// this seems
//																	// redundant
//		aBundle.putLong(DataSeries.TIMESTAMP, seriesTimestamp);
//		aBundle.putInt(DataSeries.NUM_SAMPLES, numSamples); // this seems
//															// redundant
//		aBundle.putString(DataSeries.DATA_AS_CSV, tempStrCSV);
//		return aBundle;
	}
	
	List<Bundle> getTempSamples(byte[] tempBuff, int numSamples) {
		
		int msByte, lsByte;
		long sampleTimestamp;
		List<Bundle> tempSamples = new ArrayList<Bundle>();		

		for (int i = 0; i < numSamples; i++) {
			msByte = (tempBuff[(6 * i)] & 0xff); 	// mask off sign bit and															 
																// prevent sign bit extension
																// due to promotion			
			lsByte = (tempBuff[1 + (6 * i)] & 0xff);

			sampleTimestamp = ((tempBuff[5 + (6*i)] & 0xff) << 24)
					| ((tempBuff[4 + (6*i)] & 0xff) << 16)
					| ((tempBuff[3 + (6*i)] & 0xff) << 8) 
					| (tempBuff[2 + (6*i)] & 0xff);						
						
			int concat = ((msByte << 8) | lsByte); // 16 bit scratchpad register
													// value
			concat = concat & 0xffff;
			if ((concat & SIGNBITMASK) == SIGNBITMASK) {
				// negative temp
				concat = ~concat + 1;// (concat ^ 0xffff) + 1;
				signchr = '-';
			}
			int databits = DATABITSMASK & concat;

			float temp = SENSOR_RESOLUTION * databits;
			// System.out.printf("temp is: %f\n",temp);

			String tempstr = signchr + Float.toString(temp);
			
			Log.d(TAG, "timestamp: " + sampleTimestamp + " temp raw bytes: hi: " + msByte + " lo: " 
					+ lsByte + " decoded: " + tempstr);
			
			Bundle sample = new Bundle();
			
			sample.putString(DataSeries.MSG_TYPE, "report"); // another type would
															// be alert.
			
			sample.putLong(DataSeries.SERIES_TIMESTAMP, sampleTimestamp);
			sample.putString(DataSeries.SAMPLE, tempstr);
			sample.putInt(RAW_HI, msByte);
			sample.putInt(RAW_LOW, lsByte);
			
			tempSamples.add(sample);						
		}

		return tempSamples;
	}

	String getTempStrCSV(byte[] tempBuff, int numSamples) {
		int msByte, lsByte;
		StringBuffer tempCSVBuff = new StringBuffer("");

		for (int i = 0; i < numSamples; i++) {
			msByte = (tempBuff[(2 * i)] & 0xff); // mask off sign
																// bit and
																// prevent sign
																// bit extension
																// due to
																// promotion
			lsByte = (tempBuff[(2 * i) + 1] & 0xff);
			Log.d(TAG, "temp raw bytes: hi: " + msByte + " lo: " + lsByte);
			int concat = ((msByte << 8) | lsByte); // 16 bit scratchpad register
													// value

			concat = concat & 0xffff;
			if ((concat & SIGNBITMASK) == SIGNBITMASK) {
				// negative temp
				concat = ~concat + 1;// (concat ^ 0xffff) + 1;
				signchr = '-';
			}
			int databits = DATABITSMASK & concat;

			float temp = SENSOR_RESOLUTION * databits;
			// System.out.printf("temp is: %f\n",temp);

			String tempstr = signchr + Float.toString(temp);
			Log.d(TAG, "converted temp value: " + tempstr);
			tempCSVBuff.append(tempstr);
			tempCSVBuff.append(',');
		}
		return tempCSVBuff.toString();
	}
}
