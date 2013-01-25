package org.opendatakit.sensors.drivers.usb.accelerometer;

import android.app.Activity;
import android.os.Bundle;

public class AccelerometerDriverV2Activity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}