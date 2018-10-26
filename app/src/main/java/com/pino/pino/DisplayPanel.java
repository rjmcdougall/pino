package com.pino.pino;

import android.content.Context;

import com.pino.pino.DisplayDriver;

public class DisplayPanel extends DisplayDriver {

    public DisplayPanel(Context context) {
        super(context);
        mScreenWidth = 32;
        mScreenHeight = 64;
        mScreenType = "Panel Screen";
        l("DisplayDriver Panel initting...");
        mOutputScreen = new int[mScreenWidth * mScreenHeight * 3];
        mContext = context;
        initPixelOffset();
        initUsb();
    }



}
