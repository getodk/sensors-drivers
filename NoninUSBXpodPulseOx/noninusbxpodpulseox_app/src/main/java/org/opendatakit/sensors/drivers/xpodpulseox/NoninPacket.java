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

import android.os.Bundle;


class NoninPacket {

   static final String OX = "ox";
   static final String PULSE = "pulse";
   static final String UNUSABLE = "usable";
   static final String CONNECTED = "connected";
   static final String PLETHYSMOGRAPHIC = "plethysmographic";

   static final int PACKET_SIZE = 25;
   private NoninFrame [] frames;

   public NoninPacket(List<NoninFrame> pktFrames) throws NoninParseException {
      if(pktFrames.size() != PACKET_SIZE) {
         throw new NoninParseException("Packet has incorrect number of frames!");
      }

      frames = pktFrames.toArray(new NoninFrame[PACKET_SIZE]);

      if(!frames[0].isFirstFrame()) {
         throw new NoninParseException("Packet's first frame is not a first frame!");
      }

      for(int i = 1; i < frames.length; i++) {
         if(frames[i].isFirstFrame()) {
            throw new NoninParseException("Got a first frame not in first position!");
         }
      }
   }

   int getLastUnusedByte() {
      return frames[PACKET_SIZE-1].getEndByteIndex();
   }

   boolean sensorConnected() {
      for(NoninFrame frame : frames) {
         if(!frame.sensorConnected()) {
            return false;
         }
      }
      return true;
   }

   boolean unusableData() {
      for(NoninFrame frame : frames) {
         if(frame.unusableData()) {
            return true;
         }
      }
      return false;
   }


   int getPluseRate() {
      byte msb = frames[19].getFrameValue();
      byte lsb = frames[20].getFrameValue();
      // NOTE: the first bit of the LSB byte is 0 so shift only 7 instead of 8
      return ((msb << 7) | (lsb)) & 0x1ff;
   }

   int getExtendedPluseRate() {
      byte msb = frames[21].getFrameValue();
      byte lsb = frames[22].getFrameValue();
      // NOTE: the first bit of the LSB byte is 0 so shift only 7 instead of 8
      return ((msb << 7) | (lsb)) & 0x1ff;
   }

   int getOxygenLevel() {
      byte b = frames[8].getFrameValue();
      return b & 0x7f;
   }

   int getExtendedOxygenLevel() {
      byte b = frames[16].getFrameValue();
      return b & 0x7f;
   }

   int [] getPlethysmographic() {
      int [] readings = new int[PACKET_SIZE];
      for(int i = 0; i < PACKET_SIZE; i++) {
         readings[i] = 0xff & frames[i].getPlethysmographic();
      }
      return readings;
   }

   Bundle getParsedDataBundle() {
      Bundle parsedPkt = new Bundle();
      parsedPkt.putBoolean(CONNECTED, sensorConnected());
      parsedPkt.putBoolean(UNUSABLE, unusableData());
      parsedPkt.putInt(PULSE, getExtendedPluseRate());
      parsedPkt.putInt(OX, getExtendedOxygenLevel());
      parsedPkt.putIntArray(PLETHYSMOGRAPHIC, getPlethysmographic());
      return parsedPkt;
   }
}
