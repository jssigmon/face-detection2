package com.boz.detection;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class VerifyActivity extends Activity {

    String photopath;
    Bitmap frame;
    Bitmap trackFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //Set the content view.
        setContentView(R.layout.verify_layout);

        //Unpack the files.
        try{
            frame = BitmapFactory.decodeStream(this.openFileInput("frame"));
            trackFrame = BitmapFactory.decodeStream(this.openFileInput("trackframe"));
        } catch (Exception e){
            e.printStackTrace();
            this.finish();
        }

        //Set the background image.
        ImageView preview = (ImageView) findViewById(R.id.preview);
        preview.setImageBitmap(trackFrame);
    }

    private File createImageFile() throws IOException {
        //Example taken from Android documentation.
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = timeStamp + "_";
        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        Log.v("ExternalStorage","Fetched dir");
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        Log.v("ExternalStorage","Made image");
        photopath = image.getAbsolutePath();
        return image;

    }

    public void saveFunc(View v){
        //Save the frame.
        File savefile = null;
        try{
            savefile = createImageFile();
        } catch (Exception e){
            e.printStackTrace();
        }
        if (savefile != null){
            Log.v("ExternalStorage","Save file ready");
            FileOutputStream outstream;
            try {
                outstream = new FileOutputStream(savefile);
                frame.compress(Bitmap.CompressFormat.JPEG,100,outstream);
                outstream.flush();
                outstream.close();
                //Scan the image into the gallery.
                MediaScannerConnection.scanFile(this,new String[] { savefile.getAbsolutePath() }, null,new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
                //Toast toast = Toast.makeText(getApplicationContext(),"Saved photo.",Toast.LENGTH_SHORT);
                //toast.show();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        this.finish();
    }
}
