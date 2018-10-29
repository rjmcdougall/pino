package com.pino.pino;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;


/**
 * Skeleton of Pino app
 */
public class MainActivity extends Activity {

    private Context mContext;
    private DisplayDriver mDisplay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
         mContext = this.getApplicationContext();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mDisplay == null) {
            // WS2811 matrix on Teensy Driver
            //mDisplay = new DisplayWS2811Matrix(mContext, 32, 8);
            // HUB75 Matrix on Teensy Driver
            mDisplay = new DisplayPanel(mContext, 64, 32);

        }

        //drawTestFill();
        //drawHello();
        drawTestImage();
        //drawTestGraphic();
    }

    // A simple demo function to draw an image
    private void drawTestFill() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        canvas.drawColor(Color.argb(255, 0, 50, 50));
        mDisplay.render();
    }

    // A simple demo function to draw an image
    private void drawTestImage() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Bitmap image =  BitmapFactory.decodeResource(getResources(), R.drawable.pino_frown);

        /*
        canvas.drawBitmap(image, null,
                new RectF(mDisplay.getScreenWidth() / 4, 0,
                        mDisplay.getScreenWidth() * 3 / 4,
                        mDisplay.getScreenHeight()), null);
                        */
        canvas.drawBitmap(image, null,
                new RectF(0, 0,
                        mDisplay.getScreenWidth(),
                        mDisplay.getScreenHeight()), null);
        mDisplay.render();
    }

    // A simple demo function to draw using graphics primitives
    private void drawTestGraphic() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Paint arcPaint = new Paint();
        arcPaint.setColor(Color.argb(255, 0, 50, 50)); //  Color
        arcPaint.setStrokeWidth(1);
        arcPaint.setStyle(Paint.Style.STROKE);
        int left = 0;
        int top = mDisplay.mScreenHeight;
        int right = mDisplay.mScreenWidth;
        int bottom = 0;
        int startAngle = 90;
        int sweepAngle = 270;
        boolean useCenter = false;
        canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, arcPaint);
        mDisplay.render();
    }

    // A simple demo function to write text
    private static final int kTextSize = 18;
    private void drawHello() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Paint textPaint = new TextPaint();
        textPaint.setDither(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.argb(255, 50, 50, 50)); // Text Color
        textPaint.setTextSize(kTextSize); // Text Size
        canvas.drawText("Pino",
                (mDisplay.getScreenWidth() / 2),
                mDisplay.getScreenHeight() / 2 + (kTextSize / 3),
                textPaint);
        mDisplay.render();
    }

}
