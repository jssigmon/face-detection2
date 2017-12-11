package com.boz.detection;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener{
    //This is the startup activity for the app.

    //Variables for the spinner camera options.
    private String mode = "Block";
    private float window = 0.8f;
    private int block = 5;


    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_layout);

        //Initialize the spinners.
        Spinner modes = (Spinner) findViewById(R.id.mode);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.mode_array,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modes.setAdapter(adapter);
        modes.setOnItemSelectedListener(this);

        Integer[] blockThresh = new Integer[]{5,7,9,11,13};
        Spinner blocks = (Spinner) findViewById(R.id.block);
        ArrayAdapter<Integer> blockAdapter = new ArrayAdapter(this,
                android.R.layout.simple_spinner_item,blockThresh);
        blockAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        blocks.setAdapter(blockAdapter);
        blocks.setOnItemSelectedListener(this);

        Float[] windowThresh = new Float[]{0.25f,0.5f,0.8f};
        Spinner windowSp = (Spinner) findViewById(R.id.window);
        ArrayAdapter<Float> windowAdapter = new ArrayAdapter(this,
                android.R.layout.simple_spinner_item,windowThresh);
        windowAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        windowSp.setAdapter(windowAdapter);
        windowSp.setOnItemSelectedListener(this);
    }

    public void camLaunch(View v){
        //Create the new intent, fill it with the settings, and launch.
        Intent cam = new Intent(this,CamActivity.class);
        cam.putExtra("mode",mode);
        cam.putExtra("window",window);
        cam.putExtra("block",block);
        startActivity(cam);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id){
        //Handles the spinner option selections.
        switch(parent.getId()){
            case R.id.mode:
                mode = parent.getItemAtPosition(pos).toString();
                break;
            case R.id.window:
                window = Float.parseFloat(parent.getItemAtPosition(pos).toString());
                break;
            case R.id.block:
                block = Integer.parseInt(parent.getItemAtPosition(pos).toString());
                break;
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        //Left purposefully blank.
    }

}
