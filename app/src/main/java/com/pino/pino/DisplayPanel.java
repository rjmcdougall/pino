package com.pino.pino;

import android.content.Context;
import android.util.Log;

import com.pino.pino.DisplayDriver;

public class DisplayPanel extends DisplayDriver {

    private static final String TAG = "Pino.DisplayPanel";
    private int mDimmerLevel = 255;

    public DisplayPanel(Context context, int width, int height) {
        super(context, width, height);
        mScreenWidth = width;
        mScreenHeight = height;
        mScreenType = "Panel Screen";
        Log.d(TAG, "DisplayDriver Panel initting...");
        mOutputScreen = new int[mScreenWidth * mScreenHeight * 3];
        mContext = context;
        initPixelOffset();
        initUsb();
    }

    // Convert from xy to buffer memory
    int pixel2OffsetCalc(int x, int y, int rgb) {
        return (y * mScreenWidth + x) * 3 + rgb;
    }


    static int pixel2Offset(int x, int y, int rgb) {
        return pixel2OffsetTable[x][y][rgb];
    }

    static int PIXEL_RED = 0;
    static int PIXEL_GREEN = 1;
    static int PIXEL_BLUE = 2;

    static int[][][] pixel2OffsetTable = new int[255][255][3];

    private void initPixelOffset() {
        for (int x = 0; x < mScreenWidth; x++) {
            for (int y = 0; y < mScreenHeight; y++) {
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

            Log.d(TAG, "Framerate: " + flushCnt + " frames in " + elapsedTime + ", " +
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

        int[] rowPixels = new int[mScreenWidth * 3];
        for (int y = 0; y < mScreenHeight; y++) {
            //for (int y = 30; y < 31; y++) {
            for (int x = 0; x < mScreenWidth; x++) {
                if (y < mScreenHeight) {
                    rowPixels[(mScreenWidth - 1 - x) * 3 + 0] =
                            mOutputScreen[pixel2Offset(x, y, PIXEL_RED)];
                    rowPixels[(mScreenWidth - 1 - x) * 3 + 1] =
                            mOutputScreen[pixel2Offset(x, y, PIXEL_GREEN)];
                    rowPixels[(mScreenWidth - 1 - x) * 3 + 2] =
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

        // Do color correction on burner board display pixels
        byte [] newPixels = new byte[mScreenWidth * 3];
        for (int pixel = 0; pixel < mScreenWidth * 3; pixel = pixel + 3) {
            newPixels[pixel] = (byte)pixelColorCorrectionRed(dimPixels[pixel]);
            newPixels[pixel + 1] = (byte)pixelColorCorrectionGreen(dimPixels[pixel + 1]);
            newPixels[pixel + 2] = (byte)pixelColorCorrectionBlue(dimPixels[pixel + 2]);
        }

        //System.out.println("flush row:" + y + "," + bytesToHex(newPixels));

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

    boolean haveUpdated = false;
    public void setMsg(String msg) {
        //l("sendCommand: 10,n,...");
        synchronized (mSerialConn) {
            if (mListener != null) {
                mListener.sendCmdStart(13);
                mListener.sendCmdArg(msg);
                mListener.sendCmdEnd();
            }
            if (!haveUpdated) {
                haveUpdated = true;
                update();
            }
            flush2Board();
        }
    }


    //    cmdMessenger.attach(BBUpdate, OnUpdate);              // 6
    public boolean update() {

        //l("sendCommand: 5");
        synchronized (mSerialConn) {
            if (mListener != null) {
                mListener.sendCmd(6);
                mListener.sendCmdEnd();
                return true;
            } else {
                // Emulate board's 30ms refresh time
                try {
                    Thread.sleep(5);
                } catch (Throwable e) {
                }
            }
        }

        return false;
    }


}
