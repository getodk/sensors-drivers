package org.opendatakit.sensors.drivers.usb.forcedriver3x;

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

public class ForceSensor3Axis extends AbstractDriverBaseV2 {

	private static final String TAG = "ForceSensor3Axis";

	public ForceSensor3Axis() {
		
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
		
		for(SensorDataPacket pkt: rawData) {
			Log.d(TAG, pkt.getPayload().length + " bytes rvcd. numsamples: "
					+ pkt.getSizeOfSeries());
			
			long seriesTimestamp = pkt.getTime();
			byte[] sdpPayload = pkt.getPayload();
			
			for(int indexOffset=0; indexOffset < sdpPayload.length; indexOffset += 6) {
				allData.add(extractReading(sdpPayload, indexOffset,seriesTimestamp));
			}
		}
		return new SensorDataParseResponse(allData, null);
	}

	private Bundle extractReading(byte [] data, int beginIndexOffset, long seriesTimestamp) {
		Bundle parsedPkt = new Bundle();	
		
		parsedPkt.putLong("series-timestamp", seriesTimestamp);
		int value;
//		int value = data[beginIndexOffset+1] & 0xff;
//		Log.d(TAG, "X: got high byte: " + value + " low byte: " + (data[beginIndexOffset+0] & 0xff));
//		value = (value << 8) & 0xff00;
//		value = value | (data[beginIndexOffset+0] & 0xff);
//		parsedPkt.putInt("force-x", value);
//		
//		Log.d(TAG, "returning force value: " + value);
		
		// get x value
//		Log.d(TAG, "X: got high byte: " + (data[beginIndexOffset+1] & 0xff) + " low byte: " + (data[beginIndexOffset+0] & 0xff));
		value = constructValue(data[beginIndexOffset+1], data[beginIndexOffset]);
//		Log.d(TAG, "X Value: " + value);
		parsedPkt.putInt("z-value", value);

		// get y value
//		Log.d(TAG, "Y: got high byte: " + (data[beginIndexOffset+3] & 0xff) + " low byte: " + (data[beginIndexOffset+2] & 0xff));
		value = constructValue(data[beginIndexOffset+3], data[beginIndexOffset+2]);
//		Log.d(TAG, "Y Value: " + value);
		parsedPkt.putInt("y-value", value);

		// get z value
//		Log.d(TAG, "Z: got high byte: " + (data[beginIndexOffset+5] & 0xff) + " low byte: " + (data[beginIndexOffset+4] & 0xff));
		value = constructValue(data[beginIndexOffset+5], data[beginIndexOffset+4]);
//		Log.d(TAG, "Z Value: " + value);
		parsedPkt.putInt("x-value", value);

		return parsedPkt;
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
	
    public static int byteToIntUnsigned(byte toConvert){
    	int toReturn = (int) (toConvert & 0x7F);
    	if((int) toConvert < 0){
			toReturn = 128 + toReturn;
		}
    	return toReturn;
	}
}
