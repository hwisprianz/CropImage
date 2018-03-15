package cn.z.cropimage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageView mIvResult;
    private Button mBtLoadOther;
    private Button mBtCrop;
    private CropImageView mCiv;

    private File mResultFile;
    private String[] mAssetsFile;
    private int mAssetsFileIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView();
        mAssetsFile = new String[]{"image.jpg", "square0.png", "square1.png", "square2.png"};
        mAssetsFileIndex = 0;
        setViewClickListener();
        load();
    }

    private void findView() {
        mIvResult = findViewById(R.id.iv_result);
        mBtLoadOther = findViewById(R.id.bt_load);
        mBtCrop = findViewById(R.id.bt_crop);
        mCiv = findViewById(R.id.civ_crop);
    }

    private void setViewClickListener() {
        mBtLoadOther.setOnClickListener(this);
        mBtCrop.setOnClickListener(this);
    }

    private void load() {
        if (mAssetsFileIndex < 3) {
            mAssetsFileIndex += 1;
        } else {
            mAssetsFileIndex = 0;
        }
        try {
            mCiv.setSrcImage(getAssets().open(mAssetsFile[mAssetsFileIndex]));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void cropAndShow() {
        if (mResultFile == null) {
            mResultFile = new File(getCacheDir(), "crop.png");
        }
        mCiv.crop(mResultFile, 100);
        Bitmap cropBitmap = BitmapFactory.decodeFile(mResultFile.getPath());
        if (cropBitmap != null) {
            mCiv.invalidate();
            //mIvResult.setImageBitmap(cropBitmap);
            ((ImageView) findViewById(R.id.iv_result)).setImageBitmap(cropBitmap);
            new ImageView(this).setImageBitmap(cropBitmap);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_load:
                load();
                break;
            case R.id.bt_crop:
                cropAndShow();
                break;
        }
    }
}