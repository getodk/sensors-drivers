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
package org.opendatakit.sensors.drivers.xpodpulseox;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.sensors.SensorDataPacket;
import org.opendatakit.sensors.SensorDataParseResponse;
import org.opendatakit.sensors.SensorParameter;
import org.opendatakit.sensors.drivers.AbstractDriverBaseV2;

import android.os.Bundle;
import android.util.Log;

public class NoninXpodPulseOx extends AbstractDriverBaseV2 {

	private static final String TAG = "XpodPulseOxSensor";

	public NoninXpodPulseOx() {
		super();
		Log.e(TAG, "Nonin Xpod PulseOx Sensor Driver constructed");
		sensorParams.add(new SensorParameter(NoninPacket.CONNECTED, SensorParameter.Type.BOOLEAN, SensorParameter.Purpose.DATA, "is PulseOx Sensor Connected"));
		sensorParams.add(new SensorParameter(NoninPacket.UNUSABLE, SensorParameter.Type.BOOLEAN, SensorParameter.Purpose.DATA, "is PulseOx Sensor data usable (good signals)"));
		sensorParams.add(new SensorParameter(NoninPacket.PULSE, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Pulse"));
		sensorParams.add(new SensorParameter(NoninPacket.OX, SensorParameter.Type.INTEGER, SensorParameter.Purpose.DATA, "Blood Oxygen Level"));
	}

	@Override
	public SensorDataParseResponse getSensorData(long maxNumReadings,
			List<SensorDataPacket> rawSensorData, byte[] remainingData) {

		Log.i(TAG, "Starting to parse data");
		List<Bundle> allData = new ArrayList<Bundle>();
		List<Byte> dataBuffer = new ArrayList<Byte>();

		// Copy over the remaining bytes
		if (remainingData != null) {
			for (Byte b : remainingData) {
				dataBuffer.add(b);
			}
		}

		// Add the new raw data
		for (SensorDataPacket pkt : rawSensorData) {
			byte[] payload = pkt.getPayload();
			for (int i = 0; i < payload.length; i++) {
				dataBuffer.add(payload[i]);
			}
		}

		List<NoninFrame> frames = attemptToCreateFrames(dataBuffer);
		List<NoninPacket> packets = attemptToCreatePackets(frames);

		int indexUnusedByte2 = -1;
		for (NoninPacket pkt : packets) {
			if (indexUnusedByte2 < pkt.getLastUnusedByte()) {
				indexUnusedByte2 = pkt.getLastUnusedByte();
				Log.e(TAG,
						"INDEX UNUSED BYTE: "
								+ Integer.toString(indexUnusedByte2));
			}
			allData.add(pkt.getParsedDataBundle());
		}

		// remove used bytes
		if (indexUnusedByte2 > 0) {
			dataBuffer.subList(0, indexUnusedByte2).clear();
		}

		// Copy data back into remaining buffer
		byte[] newRemainingData = new byte[dataBuffer.size()];
		for (int i = 0; i < dataBuffer.size(); i++) {
			newRemainingData[i] = dataBuffer.get(i);
		}

		Log.i(TAG, "Finished parsing data");

		return new SensorDataParseResponse(allData, newRemainingData);
	}

	private List<NoninFrame> attemptToCreateFrames(List<Byte> bytes) {

		List<NoninFrame> frames = new ArrayList<NoninFrame>();

		if (bytes.size() < NoninFrame.FRAME_SIZE * 2) {
			return frames; // not enough bytes to make frames
		}

		for (int i = 0; i < bytes.size() - NoninFrame.FRAME_SIZE; i++) {
			int exclusiveEndByte = i + NoninFrame.FRAME_SIZE;
			if ((bytes.get(i).byteValue() == NoninFrame.START_BYTE)
					&& (bytes.get(exclusiveEndByte).byteValue() == NoninFrame.START_BYTE)) {

				List<Byte> byteList = bytes.subList(i, exclusiveEndByte);

				try {
					NoninFrame frame = new NoninFrame(byteList,
							exclusiveEndByte);
					if (frame != null) {
						frames.add(frame);
						i = exclusiveEndByte - 1;
					}
				} catch (NoninParseException e) {
					// failed parse move on to the set of bytes to make a frame
					Log.w(TAG, e.getMessage());
				}
			}
		}

		return frames;
	}

	private List<NoninPacket> attemptToCreatePackets(List<NoninFrame> frames) {
		List<NoninPacket> packets = new ArrayList<NoninPacket>();
		if (frames.size() < NoninPacket.PACKET_SIZE * 2) {
			return packets; // not enough frames to make packets
		}


		for (int i = 0; i < frames.size() - NoninPacket.PACKET_SIZE; i++) {
			int exclusiveEndFrame = i + NoninPacket.PACKET_SIZE;
			if (frames.get(i).isFirstFrame()
					&& frames.get(exclusiveEndFrame).isFirstFrame()) {
				List<NoninFrame> frameList = frames.subList(i,
						exclusiveEndFrame);
				try {
					NoninPacket packet = new NoninPacket(frameList);
					if (packet != null) {
						packets.add(packet);
						i = exclusiveEndFrame - 1;
					}
				} catch (NoninParseException e) {
					// failed parse move on to the set of frames to make a
					// packet
					Log.w(TAG, e.getMessage());
				}
			}
		}

		return packets;
	}

}