package com.boz.detection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.WindowManager;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class CamActivity extends Activity implements CvCameraViewListener2 {
    //The main camera activity. Mode varies by settings from the previous activity.

    private Mat currentFrame; //Current (color) camera frame.
    private Mat greyscaleFrame; //Current (greyscale) camera frame.

    private CameraBridgeViewBase camPort; //OpenCV camera link.

    private File cascadeXML;
    private CascadeClassifier catcascade;

    private int[] frameWindow = new int[30]; //Boolean indication of what frames have had hits.
    private float windowThresh = 0.8f; //% of frames in the 30-frame window that need hits to snap.

    private int frameCounter = 0; //Keeps track of consecutive frame hits.
    private int frameThresh = 5; //No. consecutive frames with hits needed to snap.

    //Booleans indicating which mode we're in.
    private int trackerMode = 0;
    private int consecMode = 0;
    private int windowMode = 0;

    private BaseLoaderCallback libCallback = new BaseLoaderCallback(this) {
        @Override
        //Callback object used for handling the OpenCV Manager and library loading.
        //This method also houses some items that happen on resumption.
        //This code is patterned after the Android OpenCV samples which make use of
        //OpenCV Manager.
        public void onManagerConnected(int status) {
            switch(status){
                case LoaderCallbackInterface.SUCCESS: {
                    //Successful load.
                    //Load the native tracker library.
                    System.loadLibrary("detection_based_tracker");

                    try {
                        //Attempt loading the cascade file from raw data.
                        //This is borrowed from similar OpenCV examples.
                        InputStream is = getResources().openRawResource(R.raw.cat_cascade_v4_12);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        cascadeXML = new File(cascadeDir, "cat_cascade_v4_12.xml");
                        FileOutputStream os = new FileOutputStream(cascadeXML);
                        byte[] filebuffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(filebuffer)) != -1) {
                            os.write(filebuffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        //Create a classifier from the pre-trained XML file.
                        //Clean up.
                        catcascade = new CascadeClassifier(cascadeXML.getAbsolutePath());
                        cascadeDir.delete();
                    } catch (IOException e){
                        e.printStackTrace();
                    }

                    //And since this is also called on resumption, we turn on the camera.
                    camPort.enableView();
                } break;

                default:{
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState){
        //Initialize the activity.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cam_layout);

        //Set the proper mode with the spinner value.
        String mode = getIntent().getStringExtra("mode");
        if (mode.equals("Block")){
            consecMode = 1;
            windowMode = 0;
            trackerMode = 0;
        }else if (mode.equals("Window")){
            consecMode = 0;
            windowMode = 1;
            trackerMode = 0;
        }else {
            //Tracker.
            consecMode = 0;
            windowMode = 0;
            trackerMode = 1;

        }

        //Set the other thresholds.
        //Defaults to >=80% of 30 frames in window mode and 5 consecutive frames in block mode.
        windowThresh = getIntent().getFloatExtra("window",0.8f);
        frameThresh = getIntent().getIntExtra("block",5);


        //Make sure the screen doesn't turn off mid-camera usage.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Start up the OpenCV camera object.
        camPort = (CameraBridgeViewBase) findViewById(R.id.cam_screen);
        camPort.setVisibility(CameraBridgeViewBase.VISIBLE);

        //Bind camPort to the activity so we catch and handle frames.
        camPort.setCvCameraViewListener(this);
    }

    @Override
    public void onDestroy(){
        //Destroy and turn off the camera.
        super.onDestroy();
        camPort.disableView();
    }

    @Override
    public void onPause(){
        //Release unused system resources while the activity is paused.
        //Also disable the camera.
        super.onPause();
        if (camPort != null){
            camPort.disableView();
        }
    }

    @Override
    public void onResume(){
        //Called on resuming the activity *and* starting it for the first time.
        //This code is patterned directly after the OpenCV samples,
        //including the use of OpenCV Manager for library management.
        //N.B. My experiences using OpenCV Manager were highly mixed;
        //despite the software knowing my application called for 32-bit libraries,
        //it continually pulled 64-bit libraries.
        //This is why certain libraries are included with the app.
        super.onResume();
        if(!OpenCVLoader.initDebug()){
            //The package is not onboard and OpenCVLoader is used.
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, libCallback);
        } else {
            //The package is onboard.
            libCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    public void onCameraViewStarted(int width, int height) {
        //Initialize the frame variables.
        currentFrame = new Mat();
        greyscaleFrame = new Mat();
        //Reset the frame window and counter.
        frameCounter = 0;
        Arrays.fill(frameWindow,0);
    }

    public void onCameraViewStopped() {
        //Deallocate the memory used by these frames.
        currentFrame.release();
        greyscaleFrame.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame){
        //Processes a camera frame and returns one for display.
        //The processing code is patterned after the OpenCV Android examples.
        greyscaleFrame = inputFrame.gray();

        //Temporary model verification with bounding boxes.
        MatOfRect faces = new MatOfRect();

        //Scale relative object size to camera frame size.
        float relative = 0.2f;
        float absolute = 0;
        if (Math.round(greyscaleFrame.rows()*relative) > 0) {
            absolute = Math.round(greyscaleFrame.rows()*relative);
        }

        //Detect using the cascade classifier.
        if (catcascade != null) {
            catcascade.detectMultiScale(greyscaleFrame, faces, 1.1, 2, 2,
                    new Size(absolute, absolute), new Size());
        }

        Rect[] facesArray = faces.toArray();

        if (facesArray.length > 0){
            //Update the frameWindow array.
            int[] tempwindow = new int[30];
            System.arraycopy(frameWindow,1,tempwindow,0,frameWindow.length-1);
            tempwindow[tempwindow.length-1] = 1;
            System.arraycopy(tempwindow,0,frameWindow,0,frameWindow.length);

            //Update the frame counter.
            frameCounter++;
        } else {
            //Update the frameWindow array.
            int[] tempwindow = new int[30];
            System.arraycopy(frameWindow,1,tempwindow,0,frameWindow.length-1);
            tempwindow[tempwindow.length-1] = 0;
            System.arraycopy(tempwindow,0,frameWindow,0,frameWindow.length);

            //Update the frame counter.
            frameCounter = 0;
        }

        int sum = 0;
        for (int i : frameWindow){
            sum += i;
        }
        float thresh = windowThresh*30;

        currentFrame = inputFrame.rgba();
        if ((frameCounter >= frameThresh && consecMode == 1) ||
                (sum >= thresh && windowMode == 1)){
            //Window mode active with more than the frame threshold,
            //or block mode active with more than the consecutive frame threshold.
            //Create a duplicate frame to show tracker boxes on the next screen.
            Mat frameDup = currentFrame.clone();
            for (int i = 0; i < facesArray.length; i++)
                Imgproc.rectangle(frameDup, facesArray[i].tl(), facesArray[i].br(),
                        new Scalar(0, 255, 0, 255), 3);
            //Create the verification activity and two new bitmaps.
            Intent verify = new Intent(this,VerifyActivity.class);
            Bitmap frame = Bitmap.createBitmap(currentFrame.width(),currentFrame.height(),
                    Bitmap.Config.ARGB_8888);
            Bitmap trackFrame = Bitmap.createBitmap(currentFrame.width(),currentFrame.height(),
                    Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(currentFrame,frame);
            Utils.matToBitmap(frameDup,trackFrame);

            //Save the images locally to be accessed by the new activity.
            ByteArrayOutputStream framestream = new ByteArrayOutputStream();
            String filename = "frame";
            frame.compress(Bitmap.CompressFormat.JPEG,100,framestream);
            byte[] bytesF = framestream.toByteArray();
            FileOutputStream fo;
            try{
                fo = openFileOutput(filename,Context.MODE_PRIVATE);
                fo.write(bytesF);
                fo.close();
            } catch (Exception e){
                e.printStackTrace();
            }

            filename = "trackframe";
            ByteArrayOutputStream trackstream = new ByteArrayOutputStream();
            trackFrame.compress(Bitmap.CompressFormat.JPEG,100,trackstream);
            byte[] bytesT = trackstream.toByteArray();
            try{
                fo = openFileOutput(filename,Context.MODE_PRIVATE);
                fo.write(bytesT);
                fo.close();
            } catch (Exception e){
                e.printStackTrace();
            }

            //Run the next activity.
            startActivity(verify);

        } else if (trackerMode == 1){
            //Add bounding boxes to the frame for tracker mode.
            for (int i = 0; i < facesArray.length; i++)
                Imgproc.rectangle(currentFrame, facesArray[i].tl(), facesArray[i].br(),
                        new Scalar(0, 255, 0, 255), 3);
        }
        //If we haven't launched a new activity, we just return the given frame.
        return currentFrame;
    }

}
