package org.opendatakit.sensors.drivers.usb.forcedriver3x;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class ForceDriver3x extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_force_driver3x);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_force_driver3x, menu);
        return true;
    }
}
