package cn.z.cropimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 裁剪头像的View.
 * {@link #setSrcImage(File)},{@link #setSrcImage(InputStream)}设置原始图片
 * {@link #setMaskColor(int)}           设置遮罩颜色,有默认值
 * {@link #setPreviewQuality(float)}    设置预览质量,有默认值
 * {@link #crop(File, int)}             裁剪图片,裁剪后的文件放在参数file中
 * <p>
 * Created by z
 * on 2018/3/6 0006.
 */
public class CropImageView extends View {

    @SuppressWarnings("all")
    private final String TAG = "CropView";

    @SuppressWarnings("all")
    private final int DEFAULT_SIZE = 100;   //控件默认大小 100px * 100px

    private int mWidth;     //控件宽度
    private int mHeight;    //控件高度

    private int mPreviewRadius;     //遮罩上预览窗口的半径

    private int mMaskColor = 0x90000000;  //遮罩的颜色ARGB

    private Paint mMaskPaint;               //遮罩的画笔
    private Paint mBitmapPaint;             //预览图片画笔
    private Matrix mBitmapDisplayMatrix;    //预览图片显示缩放矩阵

    private BitmapRegionDecoder mBitmapRegionDecoder;   //原始文件的局部解码器
    private Bitmap mBitmapDisPlay;    //用来显示的Bitmap,可能只是原始图片的一部分,也可能是缩放后的图像
    private float mSrcBitmapWidth;    //原始文件的宽度
    private float mSrcBitmapHeight;   //原始文件的高度

    //编码显示图片的设置.每次缩放或移动都会使用这个变量,写在成员位置,避免频繁的进行内存分配回收
    private BitmapFactory.Options mDisplayBitmapOption;

    private float mDisplayScale = 0.5f;    //绘制时的缩放比例(放大比例)
    private float mImageScale;             //对于整张图片用户期望的缩放比例(放大比例)
    private float mSampleSize;             //解码bitmap的比例,这个比例只能是2的整数次幂
    private float mComplementaryScale;     //解码bitmap的比例与希望缩放比例的余量
    private float mDisplayCenterX;         //显示中心的x坐标(原图分辨率的坐标)
    private float mDisplayCenterY;
    private float mDisplayOffsetX;         //显示时候的补偿x坐标
    private float mDisplayOffsetY;

    private Point mLastTouchPoint = new Point();    //上一次的触控点位置,用于单点触控拖拽
    private float mLastDoubleTouchPointDistance;    //上一次的两个触控点距离,用于两点触控缩放

    public CropImageView(Context context) {
        super(context);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CropImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        switch (widthMode) {
            case MeasureSpec.EXACTLY:   //match_parent或者具体值
                mWidth = widthSize;
                break;
            case MeasureSpec.AT_MOST:   //适配内容
                mWidth = DEFAULT_SIZE;  //没有适配内容,图片大小不定,所以给出默认大小
                break;
            case MeasureSpec.UNSPECIFIED:   //未指定
                mWidth = DEFAULT_SIZE;      //未指定,默认大小
                break;
            default:                    //不可能的情况
                mWidth = DEFAULT_SIZE;  //这个分支几乎永远不会执行
        }
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                mHeight = heightSize;
                break;
            case MeasureSpec.AT_MOST:
                mHeight = DEFAULT_SIZE;
                break;
            case MeasureSpec.UNSPECIFIED:
                mHeight = DEFAULT_SIZE;
                break;
            default:
                mHeight = DEFAULT_SIZE;
        }
        mPreviewRadius = (int) ((mWidth <= mHeight ? mWidth / 2 : mHeight / 2) * 0.8);
        initDisplay();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBitmap(canvas);
        drawMask(canvas);
    }

    /**
     * 设置原始图片.
     *
     * @param srcInputStream 原始图片的流
     */
    public void setSrcImage(InputStream srcInputStream) {
        BufferedInputStream srcImageBufferedInputStream;
        srcImageBufferedInputStream = new BufferedInputStream(srcInputStream);  //用一个可以mark的流,因为这个流会被读取两次
        BitmapFactory.Options srcBitmapOption = new BitmapFactory.Options();
        srcBitmapOption.inJustDecodeBounds = true;
        srcImageBufferedInputStream.mark(Integer.MAX_VALUE);    //标记,这个流还会被读一次,文件大于2G会抛异常!
        BitmapFactory.decodeStream(srcImageBufferedInputStream, null, srcBitmapOption);
        mSrcBitmapWidth = srcBitmapOption.outWidth;
        mSrcBitmapHeight = srcBitmapOption.outHeight;
        try {
            srcImageBufferedInputStream.reset();    //重置文件指针
            this.mBitmapRegionDecoder = BitmapRegionDecoder.newInstance(srcImageBufferedInputStream, false);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                srcImageBufferedInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        initDisplay();
    }

    /**
     * 设置原始图片.
     *
     * @param srcFile 原始图片的File对象
     */
    public void setSrcImage(File srcFile) {
        BufferedInputStream srcImageBufferedInputStream = null; //用一个可以mark的流,因为这个流会被读取两次
        try {
            srcImageBufferedInputStream = new BufferedInputStream(new FileInputStream(srcFile));
            setSrcImage(srcImageBufferedInputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (srcImageBufferedInputStream != null) {
                try {
                    srcImageBufferedInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化显示.
     */
    private void initDisplay() {
        if (mSrcBitmapWidth == 0 || mSrcBitmapHeight == 0 || mPreviewRadius == 0) {
            return;
        }
        int mDisplayBitmapWidth;
        if (mSrcBitmapWidth > mSrcBitmapHeight) {
            mDisplayBitmapWidth = (int) (mSrcBitmapWidth * ((mPreviewRadius * 2) / mSrcBitmapHeight));
            mImageScale = mDisplayBitmapWidth / mSrcBitmapWidth;
            mDisplayCenterX = (int) (mSrcBitmapWidth / 2);
            mDisplayCenterY = (int) (mSrcBitmapHeight / 2);
        } else {
            mDisplayBitmapWidth = mPreviewRadius * 2;
            mImageScale = mDisplayBitmapWidth / mSrcBitmapWidth;
            mDisplayCenterX = (int) (mSrcBitmapWidth / 2);
            mDisplayCenterY = (int) (mSrcBitmapHeight / 2);
        }
        onScale();
        invalidate();
    }

    /**
     * 绘制mBitmapDisplay.
     * 这个函数仅仅能将mBitmapDisplay根据mDisplayScale绘制到视图的中心;其他的,例如图片的大小,截取的部
     * 分,该函数并不关心
     *
     * @param canvas canvas
     */
    private void drawBitmap(Canvas canvas) {
        if (mBitmapPaint == null) {
            mBitmapPaint = new Paint();
        }
        if (mBitmapDisplayMatrix == null) {
            mBitmapDisplayMatrix = new Matrix();
        } else {
            mBitmapDisplayMatrix.reset();
        }
        if (mBitmapDisPlay == null) {
            return;
        }
        float scale = mComplementaryScale / mDisplayScale;
        //先缩放,再移动位置;顺序不可变化,否则需要重新计算移动的坐标,而且缩放中心也需要计算!!!
        float dx = (mWidth - mBitmapDisPlay.getWidth() * scale) / 2 - mDisplayOffsetX * scale;
        float dy = (mHeight - mBitmapDisPlay.getHeight() * scale) / 2 - mDisplayOffsetY * scale;
        mBitmapDisplayMatrix.postScale(scale, scale, 0, 0); //先缩放
        mBitmapDisplayMatrix.postTranslate(dx, dy);                 //再移动位置
        canvas.drawBitmap(mBitmapDisPlay, mBitmapDisplayMatrix, mBitmapPaint);
    }

    private void drawMask(Canvas canvas) {
        if (mMaskPaint == null) {
            mMaskPaint = new Paint();
        }
        mMaskPaint.setColor(Color.TRANSPARENT);  //设置画笔颜色为透明
        mMaskPaint.setStyle(Paint.Style.FILL);   //画笔模式填充
        mMaskPaint.setAntiAlias(true);           //抗锯齿
        //计算遮罩中心的圆形透明预览框的位置,预览框位于控件的中心
        int centerX = mWidth / 2;  //遮罩中心的圆形透明预览框圆心的x坐标
        int centerY = mHeight / 2; //遮罩中心的圆形透明预览框圆心的y左边
        Path path = new Path();
        path.addCircle(centerX, centerY, mPreviewRadius, Path.Direction.CW);  //预览框的圆形路径
        canvas.save();  //保存当前画布状态
        canvas.clipPath(path, Region.Op.DIFFERENCE);    //限制可以绘制的区域在预览框的路径之外
        canvas.drawColor(mMaskColor);   //绘制遮罩
        canvas.restore();               //还原画布状态
    }

    /**
     * 缩放.
     * 比率{@link #mImageScale}变化之后,{@link #mSampleSize}和{@link #mComplementaryScale}也会变化
     */
    private void onScale() {
        adjustmentScale();  //调整倍率,避免缩小到不能填充裁剪区域
        mSampleSize = (float) Math.pow(2, (int) Math.sqrt(1 / (mImageScale * mDisplayScale)));
        mComplementaryScale = (mImageScale * mDisplayScale) / (1 / mSampleSize);
        decodeCenter();
    }

    /**
     * 解码确定中心的合适大小的图片.
     * 中心:{@link #mDisplayCenterX},{@link #mDisplayCenterY}
     * 大小:控件可以放下的部分
     */
    private void decodeCenter() {
        if (mDisplayBitmapOption == null) {
            mDisplayBitmapOption = new BitmapFactory.Options();
        }
        if (mBitmapRegionDecoder != null) {
            mDisplayBitmapOption.inSampleSize = (int) mSampleSize;
            adjustmentCenter(); //调整中心,避免图片边界越过裁剪区域
            //mDisplayCenterX即为左边的像素,乘倍率,如果大于控件宽度的一半,说明图的左边会超出控件范围,切取控
            // 件范围的图像(控件宽度的一半除以图像的放大倍数,因为显示的时候会放大);如果小于,说明图的左边在控
            // 件范围内,切取左边的全部图像,即从0开始切.裁剪时多裁剪一个像素,避免放大很多倍导致累积误差
            int left = mDisplayCenterX * mImageScale > mWidth / 2 ?
                    (int) (mDisplayCenterX - mWidth / mImageScale / 2 - 1) : 0;
            int top = mDisplayCenterY * mImageScale > mHeight / 2 ?
                    (int) (mDisplayCenterY - mHeight / mImageScale / 2 - 1) : 0;
            //原图的像素减去mDisplayCenterX即为右边的图像,乘倍率,如果大于控件宽度的一半,说明右边的图像会超
            // 出控件范围,切取控件范围内的图像(控件宽度的一半除以图像的放大倍数,因为显示的时候会放大);如果
            // 小于,说明图的右边在控件范围内,切取右边全部图像,即切到原图的宽度为止
            int right = (int) ((mSrcBitmapWidth - mDisplayCenterX) * mImageScale > mWidth / 2 ?
                    mDisplayCenterX + mWidth / mImageScale / 2 + 1 : mSrcBitmapWidth);
            int bottom = (int) ((mSrcBitmapHeight - mDisplayCenterY) * mImageScale > mHeight / 2 ?
                    mDisplayCenterY + mHeight / mImageScale / 2 + 1 : mSrcBitmapHeight);
            //计算偏移量,切取的图像不一定应该居中放置,当左右或者上下切取的规则不同时,图片不居中,所以需要计算
            // 应该偏移多少,以中心点做参考,右边的距离减去左边的距离再除以2即为偏移量,但是计算出的是原图分辨率
            // 偏移量,图像有可能会缩小(解码时的缩小),所以还需要除以缩小的倍数.上下同理;这个便宜量最终会在
            // drawBitmap中参与矩阵的便偏移计算
            mDisplayOffsetX = ((mDisplayCenterX - right) - (left - mDisplayCenterX)) / 2 / mSampleSize;
            mDisplayOffsetY = ((mDisplayCenterY - top) - (bottom - mDisplayCenterY)) / 2 / mSampleSize;
            Rect bitmapDisplayRect = new Rect(left, top, right, bottom);
            if (mBitmapDisPlay != null) {
                mBitmapDisPlay.recycle();
            }
            mBitmapDisPlay = mBitmapRegionDecoder.decodeRegion(bitmapDisplayRect, mDisplayBitmapOption);
        }
    }

    /**
     * 调整倍缩放率.
     */
    private void adjustmentScale() {
        if (mSrcBitmapWidth > mSrcBitmapHeight) {
            mImageScale = mSrcBitmapHeight * mImageScale < mPreviewRadius * 2
                    ? mPreviewRadius * 2 / mSrcBitmapHeight : mImageScale;
        } else {
            mImageScale = mSrcBitmapWidth * mImageScale < mPreviewRadius * 2
                    ? mPreviewRadius * 2 / mSrcBitmapWidth : mImageScale;
        }
    }

    /**
     * 中心调整.
     * 左边界和右边界不能同时调节,所以调整边界前需要先调整缩放倍率,上下边界同理
     */
    private void adjustmentCenter() {
        //调整左边界
        mDisplayCenterX = mDisplayCenterX * mImageScale < mPreviewRadius
                ? mPreviewRadius / mImageScale : mDisplayCenterX;
        //调整右边界
        mDisplayCenterX = (mSrcBitmapWidth - mDisplayCenterX) * mImageScale < mPreviewRadius
                ? mSrcBitmapWidth - mPreviewRadius / mImageScale : mDisplayCenterX;
        //调整上边界
        mDisplayCenterY = mDisplayCenterY * mImageScale < mPreviewRadius
                ? mPreviewRadius / mImageScale : mDisplayCenterY;
        //调整下边界
        mDisplayCenterY = (mSrcBitmapHeight - mDisplayCenterY) * mImageScale < mPreviewRadius
                ? mSrcBitmapHeight - mPreviewRadius / mImageScale : mDisplayCenterY;
    }

    /**
     * 裁剪图片.
     *
     * @param file       存放的文件
     * @param resolution 裁剪的分辨率.(取一个最接近的有效分辨率,为负则取原始的分辨率)
     */
    public void crop(File file, int resolution) {
        if (mBitmapRegionDecoder == null) {
            return;
        }
        BitmapFactory.Options cropOption = null;
        if (resolution > 0) {
            cropOption = new BitmapFactory.Options();
            float inSampleSize = mPreviewRadius * 2 / mImageScale / resolution;
            cropOption.inSampleSize = (int) inSampleSize;   //压缩倍数
        }
        //计算裁剪范围
        int left = (int) (mDisplayCenterX - mPreviewRadius / mImageScale);
        int top = (int) (mDisplayCenterY - mPreviewRadius / mImageScale);
        int right = (int) (mDisplayCenterX + mPreviewRadius / mImageScale);
        int bottom = (int) (mDisplayCenterY + mPreviewRadius / mImageScale);
        Rect rect = new Rect(left, top, right, bottom);
        Bitmap result = mBitmapRegionDecoder.decodeRegion(rect, cropOption);
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
            result.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            result.recycle();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置遮罩颜色.
     *
     * @param maskColor 遮罩颜色 ARGB
     */
    @SuppressWarnings("unused")
    public void setMaskColor(int maskColor) {
        this.mMaskColor = maskColor;
        invalidate();
    }

    /**
     * 设置预览质量.
     *
     * @param quality 预览质量;0-1
     */
    @SuppressWarnings("unused")
    public void setPreviewQuality(float quality) {
        this.mDisplayScale = (float) (0.1 + (quality * 0.9));
    }

    /**
     * 计算直角坐标系中两个点的距离.
     *
     * @param x1 点1的x坐标
     * @param y1 点1的y坐标
     * @param x2 点2的x坐标
     * @param y2 点1的y坐标
     * @return 两点的距离
     */
    private float calculateDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:   //第一个点按下,保存坐标
                mLastTouchPoint.set((int) event.getX(0), (int) event.getY(0));
                break;
            case MotionEvent.ACTION_POINTER_DOWN:    //不是第一个点被按下
                if (event.getActionIndex() == 1) {   //第二个点被按下,记录位置
                    mLastDoubleTouchPointDistance = calculateDistance(
                            event.getX(0), event.getY(0),
                            event.getX(1), event.getY(1));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                switch (event.getPointerCount()) {
                    case 1:
                        mDisplayCenterX -= (event.getX(0) - mLastTouchPoint.x) / mImageScale;
                        mDisplayCenterY -= (event.getY(0) - mLastTouchPoint.y) / mImageScale;
                        mLastTouchPoint.set((int) event.getX(0), (int) event.getY(0));
                        decodeCenter();
                        invalidate();
                        break;
                    case 2:
                        //计算当前的两个触控点距离
                        float nowDoubleTouchPointDistance = calculateDistance(
                                event.getX(0), event.getY(0),
                                event.getX(1), event.getY(1)
                        );
                        mImageScale *= nowDoubleTouchPointDistance / mLastDoubleTouchPointDistance;
                        onScale();
                        //保存当前两个触控点的距离,作为下一次缩放的上一次的触控点距离
                        mLastDoubleTouchPointDistance = nowDoubleTouchPointDistance;
                        invalidate();
                        break;
                    default:
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:     //不是最后一个触控点释放
                //这里event.getPointerCount()是包含被释放的触控点的!
                switch (event.getPointerCount()) {   //判断触控点个数
                    case 2: //剩余两个触控点,释放后剩下一个,拖拽,记录剩余的触控点坐标
                        switch (event.getActionIndex()) {
                            case 0: //第0个触控点被释放,记录第1个触控点的坐标
                                mLastTouchPoint.set((int) event.getX(1),
                                        (int) event.getY(1));
                                break;
                            case 1: //第1个触控点被释放,记录第0个触控点的坐标
                                mLastTouchPoint.set((int) event.getX(0),
                                        (int) event.getY(0));
                                break;
                            default:
                        }
                        break;
                    case 3: //剩余三个触控点,释放后剩下两个,缩放,记录两个触控点的距离
                        switch (event.getActionIndex()) {
                            case 0: //第0个触控点被释放,记录触控点1,2的距离
                                mLastDoubleTouchPointDistance = calculateDistance(
                                        event.getX(1), event.getY(1),
                                        event.getX(2), event.getY(2));
                                break;
                            case 1: //第1个触控点被释放,记录触控点0,2的距离
                                mLastDoubleTouchPointDistance = calculateDistance(
                                        event.getX(0), event.getY(0),
                                        event.getX(2), event.getY(2));
                                break;
                            case 2: //第2个触控点被释放,记录触控点0,1的距离
                                mLastDoubleTouchPointDistance = calculateDistance(
                                        event.getX(0), event.getY(0),
                                        event.getX(1), event.getY(1));
                                break;
                        }
                        break;
                    default:
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
        //return super.onTouchEvent(event);
    }
}
