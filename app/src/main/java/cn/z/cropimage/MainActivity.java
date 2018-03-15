package cn.z.cropimage;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int a = 175;
        double b = 0.15891125f;
        a -= b;
        Log.i("CropView", "onCreate: a = " + a);


        //File imageFile = new File(System.getenv("EXTERNAL_STORAGE"), "t.png");
        //File imageFile = new File(System.getenv("EXTERNAL_STORAGE"), "image.jpg");
        //File imageFile = new File(System.getenv("EXTERNAL_STORAGE"), "Square.png");
        final File imageFile = new File(getCacheDir(), "temp");
        try {
            InputStream is = getAssets().open("square0.png");
            //InputStream is = getAssets().open("image.jpg");
            OutputStream os = new FileOutputStream(imageFile);
            int byteRead = 0;
            byte[] buffer = new byte[1024];
            while ((byteRead = is.read(buffer, 0, 1024)) != -1) {
                os.write(buffer, 0, byteRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Uri imageUri = Uri.fromFile(imageFile);
        Log.i("CropView", "onCreate: uri = " + imageUri);
        final CropImage cropView = findViewById(R.id.crop_view);
        cropView.setSrcImage(imageFile);


        final File resultFile = new File(getCacheDir(), "headCrop.png");

        findViewById(R.id.crop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cropView.crop(resultFile, 100);
            }
        });

        findViewById(R.id.load).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    cropView.setSrcImage(getAssets().open("square2.png"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


    }


}