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

import java.util.List;

class NoninFrame {
	public static final int FRAME_SIZE = 5;
	
	public static final int START_BYTE = 0x01;
	
	private static final int SNSD_BIT = 6;
	private static final int SNSA_BIT = 3;
	private static final int SYNC_BIT = 0;
	
	private byte status;
	private byte flat;

	private int endByteIndex;

	NoninFrame(List<Byte> byteArray, int endIndex) throws NoninParseException {
		if (byteArray.size() != FRAME_SIZE) {
			throw new NoninParseException("Incorrect Number of Bytes in Frame");
		}

		// convert to byte array
		byte [] frame = new byte[FRAME_SIZE];
		for(int j=0; j < FRAME_SIZE; j++) {
			frame[j] = byteArray.get(j);
		}
		
		if(frame[0] != START_BYTE) {
			throw new NoninParseException("Frame does not begin with Start Byte!");
		}
		
		if((frame[1] & 0x80) != 0x80) {
			throw new NoninParseException("Status Byte does not have Bit 7 set to 1!");
		}
		
		if((frame[3] & 0x80) != 0x00) {
			throw new NoninParseException("Flat Byte does not have Bit 7 set to 0!");
		}
		
		int checksum = frame[0] + frame[1] + frame[2] + frame[3];
		checksum = checksum % 256;
		checksum = checksum & 0xff;
		
		int packetChecksum = frame[4];
		packetChecksum = packetChecksum & 0xff;
		if(packetChecksum != checksum) {
			throw new NoninParseException("Checksum Failure!");
		}

		status = frame[1];
		flat = frame[3];
		endByteIndex = endIndex;
	}
	
	
	private boolean bitIsSet(byte value, int position) {
		int tmp = value >> position;
		return ((tmp & 0x01) == 0x01);
	}
	
	boolean isFirstFrame() {
		return bitIsSet(status, SYNC_BIT);
	}
	
	boolean sensorConnected() {
		return !bitIsSet(status, SNSD_BIT);
	}
	
	boolean unusableData() {
		return bitIsSet(status, SNSA_BIT);
	}
	
	byte getFrameValue() {
		return flat;
	}

	int getEndByteIndex() {
		return endByteIndex;
	}
	
	
}