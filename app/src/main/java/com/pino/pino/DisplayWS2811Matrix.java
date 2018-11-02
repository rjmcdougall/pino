package com.pino.pino;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.nio.IntBuffer;

public class DisplayWS2811Matrix extends DisplayDriver {

    private static final String TAG = "Pino.DisplayPanel";
    private int mDimmerLevel = 255;

    public DisplayWS2811Matrix(Context context, int width, int height) {
        super(context, width, height);
        mScreenWidth = width;
        mScreenHeight = height;
        mScreenType = "Panel Screen";
        Log.d(TAG, "DisplayDriver Panel initting...");
        mBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        mScreenBuffer = IntBuffer.allocate(width * height);
        mOutputScreen = new int[mScreenWidth * mScreenHeight * 3];
        mContext = context;
        initPixelOffset();
        initpixelMap2Board();
        initUsb();
    }

    public void render() {
        if (mScreenBuffer != null) {
            mScreenBuffer.rewind();
            mBitmap.copyPixelsToBuffer(mScreenBuffer);
        }
        aRGBtoBoardScreen(mScreenBuffer, mOutputScreen);
        //Log.d(TAG, "render mScreenBuffer:" + mScreenBuffer + "," + Util.intToHex(mScreenBuffer.array()));
        //Log.d(TAG, "render mScreenBuffer:" + mOutputScreen + "," + Util.intToHex(mOutputScreen));
        flush();
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
    long lastFlushTime = System.currentTimeMillis();

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
            // R
            if (pixel % 3 == 0) {
                totalBrightnessSum += mOutputScreen[pixel];
            } else if (pixel % 3 == 1) {
                totalBrightnessSum += mOutputScreen[pixel];
            } else {
                totalBrightnessSum += mOutputScreen[pixel] / 2;
            }
        }

        final int powerPercent = totalBrightnessSum / mOutputScreen.length * 100 / 255;
        //powerLimitMultiplierPercent = 100 - java.lang.Math.max(powerPercent - 12, 0);
        powerLimitMultiplierPercent = 10;

        // Walk through each strip and fill from the graphics buffer
        for (int s = 0; s < kStrips; s++) {
            int[] stripPixels = new int[mScreenHeight * kColumsPerStrip * 3];
            // Walk through all the pixels in the strip
            //Log.d(TAG, "pixelmap2boatdtable :" + s + "," + Util.intToHex(pixelMap2BoardTable[s]));

            for (int offset = 0; offset < mScreenHeight * kColumsPerStrip * 3; ) {
                stripPixels[offset] = mOutputScreen[pixelMap2BoardTable[s][offset++]];
                stripPixels[offset] = mOutputScreen[pixelMap2BoardTable[s][offset++]];
                stripPixels[offset] = mOutputScreen[pixelMap2BoardTable[s][offset++]];
            }
            setStrip(s, stripPixels, powerLimitMultiplierPercent);
            // Send to board
            flush2Board();
        }


        // Render on board
        update();
        flush2Board();

    }

    // Send a strip of pixels to the board
    private void setStrip(int strip, int[] pixels, int powerLimitMultiplierPercent) {

        //Log.d(TAG, "flushPixels raw row:" + strip + "," + Util.intToHex(pixels));

        int[] dimPixels = new int[pixels.length];
        //Log.d(TAG, "powerlimit set to " + powerLimitMultiplierPercent + "%");
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            dimPixels[pixel] =
                    (mDimmerLevel * pixels[pixel]) / 255 * powerLimitMultiplierPercent / 100;
        }

        // Do color correction on burner board display pixels
        byte [] newPixels = new byte[pixels.length];
        for (int pixel = 0; pixel < pixels.length; pixel = pixel + 3) {
            newPixels[pixel] = (byte)pixelColorCorrectionRed(dimPixels[pixel]);
            newPixels[pixel + 1] = (byte)pixelColorCorrectionGreen(dimPixels[pixel + 1]);
            newPixels[pixel + 2] = (byte)pixelColorCorrectionBlue(dimPixels[pixel + 2]);
        }

        //newPixels[30]=(byte)128;
        //newPixels[31]=(byte)128;
        //newPixels[32]=(byte)128;
        //newPixels[3]=2;
        //newPixels[4]=40;
        //newPixels[5]=2;
        //newPixels[6]=2;
        //newPixels[7]=2;
        //newPixels[8]=40;
        //newPixels[9]=2;
        //newPixels[10]=2;
        //newPixels[11]=40;

        //Log.d(TAG, "flushPixels row:" + strip + "," + Util.bytesToHex(newPixels));

        //l("sendCommand: 14,n,...");
        synchronized (mSerialConn) {
            if (mListener != null) {
                mListener.sendCmdStart(10);
                mListener.sendCmdArg(strip);
                mListener.sendCmdEscArg(newPixels);
                mListener.sendCmdEnd();
            }
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
                // Emulate board's 5ms refresh time
                try {
                    Thread.sleep(5);
                } catch (Throwable e) {
                }
            }
        }

        return false;
    }



    private void pixelRemap(int x, int y, int stripNo, int stripOffset) {
        //Log.d(TAG, "PixelRemap: " + x + ", " + y + " " + stripNo + ":" + stripOffset);
        pixelMap2BoardTable[stripNo][stripOffset] =
                pixel2Offset(x, y, PIXEL_RED);
        pixelMap2BoardTable[stripNo][stripOffset + 1] =
                pixel2Offset(x, y, PIXEL_GREEN);
        pixelMap2BoardTable[stripNo][stripOffset + 2] =
                pixel2Offset(x, y, PIXEL_BLUE);
    }


    // Two primary mapping functions
    static int kStrips = 8;
    int [][] pixelMap2BoardTable = new int[8][2048];
    static int kColumsPerStrip = 32;

    private void initpixelMap2Board() {

        for (int x = 0; x < mScreenWidth; x++) {
            for (int y = 0; y < mScreenHeight; y++) {

                final int subStrip = x % kColumsPerStrip;
                final int stripNo = x / kColumsPerStrip;
                final boolean stripUp = subStrip % 2 == 0;
                int stripOffset;

                if (stripUp) {
                    stripOffset = subStrip * mScreenHeight + y;
                } else {
                    stripOffset = subStrip * mScreenHeight + (mScreenHeight - 1 - y);
                }
                pixelRemap(x, y, stripNo, stripOffset * 3);
            }

        }

    }

}
