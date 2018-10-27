package com.pino.pino;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;


import java.io.InputStream;

/**
 * Skeleton of Pino app
 */
public class MainActivity extends Activity {

    private Context mContext = this.getApplicationContext();
    private DisplayDriver mDisplay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mDisplay == null) {
            mDisplay = new DisplayPanel(mContext, 16, 8);
        }

        drawHello();
        drawTestImage();
    }

    // A simple demo function to draw an image
    private void drawTestImage() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Bitmap image =  BitmapFactory.decodeResource(getResources(), R.drawable.pino_frown);
        Paint paint = new Paint();
        canvas.drawBitmap(image, 0, 0, paint);
        mDisplay.render();
    }

    // A simple demo function to draw using graphics primitives
    private void drawTestGraphic() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Paint arcPaint = new Paint();
        arcPaint.setColor(Color.BLUE); //  Color
        arcPaint.setStrokeWidth(1);
        arcPaint.setStyle(Paint.Style.STROKE);
        int left = 0;
        int top = mDisplay.mScreenWidth;
        int right = mDisplay.mScreenHeight;
        int bottom = 0;
        int startAngle = 90;
        int sweepAngle = 270;
        boolean useCenter = true;
        canvas.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, arcPaint);
    }

    // A simple demo function to write text
    private static final int kTextSize = 12;
    private void drawHello() {
        if (mDisplay == null) {
            return;
        }
        Canvas canvas = mDisplay.getCanvas();
        Paint textPaint = new TextPaint();
        textPaint.setDither(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE); // Text Color
        textPaint.setTextSize(kTextSize); // Text Size
        canvas.drawText("Pino",
                (mDisplay.getScreenWidth() / 2),
                mDisplay.getScreenHeight() / 2 + (kTextSize / 3),
                textPaint);
        mDisplay.render();
    }

}
