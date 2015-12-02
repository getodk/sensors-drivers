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
package org.opendatakit.sensors.drivers.usb.force;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.sensors.DataSeries;
import org.opendatakit.sensors.ParameterMissingException;
import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;
import org.opendatakit.sensors.drivers.USBParamUtil;

import android.os.Bundle;
import android.util.Log;

public class ForceSensor extends AbstractDriverBaseV2 {

	private static final String TAG = "ForceSensor";

	public ForceSensor() {
		
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
		if(setting.equals("SR")) {
			int samplingRate = params.getInt("SR"); //sampling rate
			return USBParamUtil.createSamplingRateMsg(samplingRate);
		} else if(setting.equals("RR")) {
			int readRate = params.getInt("RR"); //reading rate
			return USBParamUtil.createReadRateMsg(readRate);
		}
		throw new ParameterMissingException("Unknown Setting");
	}
	
	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings, List<SensorDataPacket> rawData, byte [] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();
//		Log.d(TAG," no. of SDPs: " + rawData.size());
		
		for(SensorDataPacket pkt: rawData) {
			Log.d(TAG, pkt.getPayload().length + " bytes rvcd. numsamples: "
					+ pkt.getSizeOfSeries() + " series timestamp: " + pkt.getTime());
//			Bundle tsBundle = new Bundle();
//			tsBundle.putLong("series-timestamp", pkt.getTime());
//			allData.add(tsBundle);
			long seriesTimestamp = pkt.getTime();
			byte[] sdpPayload = pkt.getPayload();
			
			for(int indexOffset=0; indexOffset < sdpPayload.length; indexOffset += 2) {
				allData.add(extractReading(sdpPayload, indexOffset,seriesTimestamp));
			}
		}
		return new SensorDataParseResponse(allData, null);
	}

	private Bundle extractReading(byte [] data, int beginIndexOffset, long seriesTimestamp) {
		Bundle parsedPkt = new Bundle();		
		int value = data[beginIndexOffset+1] & 0xff;
//		Log.d(TAG, "got high byte: " + value + " low byte: " + (data[beginIndexOffset+0] & 0xff));
		value = (value << 8) & 0xff00;
		value = value | (data[beginIndexOffset+0] & 0xff);
		parsedPkt.putInt("force", value);
		parsedPkt.putLong("series-timestamp", seriesTimestamp);
		
//		long sampleTimestamp = ((data[beginIndexOffset+5] & 0xff) << 24)
//				| ((data[beginIndexOffset+4] & 0xff) << 16)
//				| ((data[beginIndexOffset+3] & 0xff) << 8) 
//				| (data[beginIndexOffset+2] & 0xff);
//		parsedPkt.putLong(DataSeries.TIMESTAMP, sampleTimestamp);		
		
		
//		Log.d(TAG, "returning force value: " + value + " timestamp: " + sampleTimestamp);
//		Log.d(TAG, "returning force value: " + value);
		return parsedPkt;
	}
	
    public static int byteToIntUnsigned(byte toConvert){
    	int toReturn = (int) (toConvert & 0x7F);
    	if((int) toConvert < 0){
			toReturn = 128 + toReturn;
		}
    	return toReturn;
	}
}
