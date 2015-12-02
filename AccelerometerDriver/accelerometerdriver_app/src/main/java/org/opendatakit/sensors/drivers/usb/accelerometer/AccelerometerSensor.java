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

package org.opendatakit.sensors.drivers.usb.accelerometer;

import java.util.ArrayList;
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

public class AccelerometerSensor extends AbstractDriverBaseV2 {

	private static final String X_VALUE = "x-value";
	private static final String Y_VALUE = "y-value";
	private static final String Z_VALUE = "z-value";
	private static final String TIMESTAMP = "series-timestamp";
	
	private static final String SAMPLING_RATE = "SR";
	private static final String READ_RATE = "RR";
	private static final String TAREX = "TX";
	private static final String TAREY = "TY";
	private static final String TAREZ = "TZ";
	private static final String OFFSETX = "OX";
	private static final String OFFSETY = "OY";
	private static final String OFFSETZ = "OZ";
	private static final String RANGE = "RA";
	
	private static final String TAG = "AccelerometerSensor";

	public AccelerometerSensor() {
		super();
		
		// configure parameters
		sensorParams.add(new SensorParameter(SAMPLING_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.CONFIG, "Sensor sampling rate"));
		sensorParams.add(new SensorParameter(READ_RATE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.CONFIG, "Rate at which readings are proccessed"));
		sensorParams.add(new SensorParameter(TAREX, SensorParameter.Type.VOID, SensorParameter.Purpose.ACTION, "Tare the accelerometer in the X direction"));
		sensorParams.add(new SensorParameter(TAREY, SensorParameter.Type.VOID, SensorParameter.Purpose.ACTION, "Tare the accelerometer in the Y direction"));
		sensorParams.add(new SensorParameter(TAREZ, SensorParameter.Type.VOID, SensorParameter.Purpose.ACTION, "Tare the accelerometer in the Z direction"));
		sensorParams.add(new SensorParameter(OFFSETX, SensorParameter.Type.BYTE, SensorParameter.Purpose.CONFIG, "Set the offset of the accelometer in the X direction"));
		sensorParams.add(new SensorParameter(OFFSETY, SensorParameter.Type.BYTE, SensorParameter.Purpose.CONFIG, "Set the offset of the accelometer in the Y direction"));
		sensorParams.add(new SensorParameter(OFFSETZ, SensorParameter.Type.BYTE, SensorParameter.Purpose.CONFIG, "Set the offset of the accelometer in the Z direction"));
		sensorParams.add(new SensorParameter(RANGE, SensorParameter.Type.BYTE, SensorParameter.Purpose.CONFIG, "Configure the Accelerometer Range"));
		
		// data reporting parameters
		sensorParams.add(new SensorParameter(X_VALUE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Accelerometer value on X-axis"));
		sensorParams.add(new SensorParameter(Y_VALUE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Accelerometer value on Y-axis"));
		sensorParams.add(new SensorParameter(Z_VALUE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Accelerometer value on Z-axis"));
		sensorParams.add(new SensorParameter(TIMESTAMP, SensorParameter.Type.LONG, SensorParameter.Purpose.DATA, "Timestamp of data"));
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
		if (setting.equals(SAMPLING_RATE)) {
			int samplingRate = params.getInt(SAMPLING_RATE); // sampling rate
			return USBParamUtil.createSamplingRateMsg(samplingRate);
		} else if (setting.equals(READ_RATE)) {
			int readRate = params.getInt(READ_RATE); // reading rate
			return USBParamUtil.createReadRateMsg(readRate);
		} else if (setting.equals(TAREX)) {
			return USBParamUtil.createMsg(TAREX, null);
		} else if (setting.equals(TAREY)) {
			return USBParamUtil.createMsg(TAREY, null);
		} else if (setting.equals(TAREZ)) {
			return USBParamUtil.createMsg(TAREZ, null);
		} else if (setting.equals(OFFSETX)) {
			return USBParamUtil.createOneByteMsg(OFFSETX, params.getByte(OFFSETX));
		} else if (setting.equals(OFFSETY)) {
			return USBParamUtil.createOneByteMsg(OFFSETY, params.getByte(OFFSETY));
		} else if (setting.equals(OFFSETZ)) {
			return USBParamUtil.createOneByteMsg(OFFSETZ, params.getByte(OFFSETZ));
		}else if (setting.equals(RANGE)) {
			return USBParamUtil.createOneByteMsg(RANGE, params.getByte(RANGE));
		}

		throw new ParameterMissingException("Unknown Setting");
	}

	private int constructValue(int high, byte low) {
		int value = high & 0x0f;
		value = (value << 8) & 0xff00;
		value = value | (low & 0xff);

		if ((value & 0x800) > 0) {
			value = 0xfffff000 | value;
		}

		return value;
	}

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings,
			List<SensorDataPacket> rawData, byte[] remainingData) {
		List<Bundle> allData = new ArrayList<Bundle>();
		for (SensorDataPacket pkt : rawData) {
			// should have 15 bytes if doesn't don't know how to parse
			Log.d(TAG, pkt.getPayload().length + " bytes rvcd. numsamples: "
					+ pkt.getSizeOfSeries());


			// 0
			// 1
			// 2
			// 3
			// 4 length
			// 5
			// 6 x value
			// 7
			// 8 y value
			// 9
			// 10 z value			
			long seriesTimestamp = pkt.getTime();
			byte[] sdpPayload = pkt.getPayload();
			
			for(int indexOffset=0; indexOffset < sdpPayload.length; indexOffset += 6) {
				allData.add(extractReading(sdpPayload, indexOffset,seriesTimestamp));
			}
		}
		return new SensorDataParseResponse(allData, null);
	}

	private Bundle extractReading(byte [] data, int beginIndexOffset, long seriesTimestamp) {
		int value;
		Bundle parsedPkt = new Bundle();
		
		parsedPkt.putLong(TIMESTAMP, seriesTimestamp);

		// get x value
		value = constructValue(data[beginIndexOffset+1], data[beginIndexOffset]);
		Log.d(TAG, "X Value: " + value);
		parsedPkt.putInt(X_VALUE, value);

		// get y value
		value = constructValue(data[beginIndexOffset+3], data[beginIndexOffset+2]);
		Log.d(TAG, "Y Value: " + value);
		parsedPkt.putInt(Y_VALUE, value);

		// get z value
		value = constructValue(data[beginIndexOffset+5], data[beginIndexOffset+4]);
		Log.d(TAG, "Z Value: " + value);
		parsedPkt.putInt(Z_VALUE, value);

		return parsedPkt;
	}

}
