package com.pino.pino;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.pino.pino.DisplayDriver;

import java.nio.IntBuffer;
import android.graphics.Matrix;

public class DisplayPanel extends DisplayDriver {

    private static final String TAG = "Pino.DisplayPanel";
    private int mDimmerLevel = 255;
    private int mPanelWidth;
    private int mPanelHeight;

    public DisplayPanel(Context context, int width, int height) {
        super(context, width, height);
        mScreenWidth = width;
        mScreenHeight = height;
        mPanelWidth = height;
        mPanelHeight = width;
        mScreenType = "Panel Screen";
        Log.d(TAG, "DisplayDriver Panel initting...");
        mOutputScreen = new int[mScreenWidth * mScreenHeight * 3];
        mContext = context;
        mBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        //mCanvas.rotate(-90, mScreenHeight / 2, mScreenWidth / 2);
        mScreenBuffer = IntBuffer.allocate(width * height);
        initPixelOffset();
        initUsb();

    }

    public void render() {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        if (mScreenBuffer != null) {
            mScreenBuffer.rewind();
            Bitmap rotated = Bitmap.createBitmap(mBitmap, 0, 0, mScreenWidth, mScreenHeight, matrix, true);
            rotated.copyPixelsToBuffer(mScreenBuffer);
        }
        aRGBtoBoardScreen(mScreenBuffer, mOutputScreen);
        flush();
    }

    // Convert from xy to buffer memory
    int pixel2OffsetCalc(int x, int y, int rgb) {
        return (y * mPanelWidth + x) * 3 + rgb;
    }


    static int pixel2Offset(int x, int y, int rgb) {
        return pixel2OffsetTable[x][y][rgb];
    }

    static int PIXEL_RED = 0;
    static int PIXEL_GREEN = 1;
    static int PIXEL_BLUE = 2;

    static int[][][] pixel2OffsetTable = new int[255][255][3];

    private void initPixelOffset() {
        for (int x = 0; x < mPanelWidth; x++) {
            for (int y = 0; y < mPanelHeight; y++) {
                for (int rgb = 0; rgb < 3; rgb++) {
                    pixel2OffsetTable[x][y][rgb] = pixel2OffsetCalc(x, y, rgb);
                }
            }
        }
    }

    // No correction for HUB75 RGB Panel
    private int pixelColorCorrectionRed(int red) {
        return red;
    }
    private int pixelColorCorrectionGreen(int green) {
        return green;
    }
    private int pixelColorCorrectionBlue(int blue) {
        return blue;
    }

    private int flushCnt = 0;
    long lastFlushTime = java.lang.System.currentTimeMillis();

    public void flush() {

        flushCnt++;
        if (flushCnt > 100) {
            int elapsedTime = (int) (java.lang.System.currentTimeMillis() - lastFlushTime);
            lastFlushTime = java.lang.System.currentTimeMillis();

            Log.d(TAG, "Frame-rate: " + flushCnt + " frames in " + elapsedTime + ", " +
                    (flushCnt * 1000 / elapsedTime) + " frames/sec");
            flushCnt = 0;
        }



        // Here we calculate the total power percentage of the whole board
        // We want to limit the board to no more than 50% of pixel output total
        // This is because the board is setup to flip the breaker at 200 watts
        // Output is percentage multiplier for the LEDs
        // At full brightness we limit to 30% of their output
        // Power is on-linear to pixel brightness: 37% = 50% power.
        // powerPercent = 100: 15% multiplier
        // powerPercent <= 15: 100% multiplier
        int totalBrightnessSum = 0;
        int powerLimitMultiplierPercent = 100;
        for (int pixel = 0; pixel < mOutputScreen.length; pixel++) {

            if (pixel % 3 == 0) { // R
                totalBrightnessSum += mOutputScreen[pixel];

            } else if (pixel % 3 == 1) { // G
                totalBrightnessSum += mOutputScreen[pixel];
            } else { //B
                totalBrightnessSum += mOutputScreen[pixel] / 2;
            }
        }

        final int powerPercent = totalBrightnessSum / mOutputScreen.length * 100 / 255;
        powerLimitMultiplierPercent = 100 - java.lang.Math.max(powerPercent - 15, 0);

        int[] rowPixels = new int[mPanelWidth * 3];
        for (int y = 0; y < mPanelHeight; y++) {
            //for (int y = 30; y < 31; y++) {
            for (int x = 0; x < mPanelWidth; x++) {
                if (y < mPanelHeight) {
                    rowPixels[(mPanelWidth - 1 - x) * 3 + 0] =
                            mOutputScreen[pixel2Offset(x, y, PIXEL_RED)];
                    rowPixels[(mPanelWidth - 1 - x) * 3 + 1] =
                            mOutputScreen[pixel2Offset(x, y, PIXEL_GREEN)];
                    rowPixels[(mPanelWidth - 1 - x) * 3 + 2] =
                            mOutputScreen[pixel2Offset(x, y, PIXEL_BLUE)];
                }
            }
            //setRowVisual(y, rowPixels);
            setRow(y, rowPixels);
        }

        update();
        flush2Board();

    }

    private boolean setRow(int row, int[] pixels) {

        int [] dimPixels = new int [pixels.length];
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            dimPixels[pixel] =
                    (mDimmerLevel * pixels[pixel]) / 255;
        }

        // Do color correction on display pixels if needed
        byte [] newPixels = new byte[mPanelWidth * 3];
        for (int pixel = 0; pixel < mPanelWidth * 3; pixel = pixel + 3) {
            newPixels[pixel] = (byte)pixelColorCorrectionRed(dimPixels[pixel]);
            newPixels[pixel + 1] = (byte)pixelColorCorrectionGreen(dimPixels[pixel + 1]);
            newPixels[pixel + 2] = (byte)pixelColorCorrectionBlue(dimPixels[pixel + 2]);
        }

        //Log.d(TAG, "flush row:" + row + "," + Util.bytesToHex(newPixels));

        //l("sendCommand: 10,n,...");
        synchronized (mSerialConn) {
            if (mListener != null) {
                mListener.sendCmdStart(10);
                mListener.sendCmdArg(row);
                mListener.sendCmdEscArg(newPixels);
                mListener.sendCmdEnd();
                return true;
            }
        }
        return false;
    }

    public boolean update() {

        //l("sendCommand: 5");
        synchronized (mSerialConn) {
            if (mListener != null) {
                mListener.sendCmd(6);
                mListener.sendCmdEnd();
                return true;
            } else {
                // Emulate board's 5ms refresh time
                try {
                    Thread.sleep(5);
                } catch (Throwable e) {
                }
            }
        }

        return false;
    }


}
