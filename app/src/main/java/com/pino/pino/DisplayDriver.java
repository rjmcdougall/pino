package com.pino.pino;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/*
 * Simple display head for multiple Pino displays
 * Allows drawing on standard Android Canvas with Android API and render canvas to display
 * Call DisplayDriver.render() to render on display.
 */
public class DisplayDriver {

    private static final String TAG = "Pino.Display";
    Context mContext;

    public int[] mOutputScreen;
    public int mScreenWidth = 1;
    public int mScreenHeight = 1;
    public String mScreenType;
    public CmdMessenger mListener = null;

    private SerialInputOutputManager mSerialIoManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    protected final Object mSerialConn = new Object();
    private static UsbSerialPort sPort = null;
    private static UsbSerialDriver mDriver = null;
    private UsbDevice mUsbDevice = null;
    protected static final String GET_USB_PERMISSION = "GetUsbPermission";

    Canvas mCanvas;
    Bitmap mBitmap;
    public IntBuffer mScreenBuffer = null;

    public DisplayDriver(Context context, int width, int height) {
        mContext = context;
        mScreenWidth = width;
        mScreenHeight = height;
        // Register to receive attach/detached messages that are proxied from MainActivity
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        mContext.registerReceiver(mUsbReceiver, filter);
        filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        mContext.registerReceiver(mUsbReceiver, filter);

        // Setup bitmap behind canvas
        mCanvas = new Canvas();
        mBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        mScreenBuffer = IntBuffer.allocate(width * height);

    }

    public Canvas getCanvas() {
        return mCanvas;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    public void render() {
    }

    private boolean checkUsbDevice(UsbDevice device) {

        int vid = device.getVendorId();
        int pid = device.getProductId();
        Log.d(TAG , "checking device " + device.describeContents() +
                ", pid:" + pid + ", vid: " + vid);
        // Check for our Teensy->LED Adapter
        if ((pid == 1155) && (vid == 5824)) {
            return true;
        } else {
            return false;
        }
    }

    public void initUsb() {
        Log.d(TAG, "BurnerBoard: initUsb()");

        if (mUsbDevice != null) {
            Log.d(TAG, "initUsb: already have a device");
            return;
        }

        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        // Find all available drivers from attached devices.
        List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "USB: No device/driver");
            return;
        }

        // Register to receive detached messages
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        mContext.registerReceiver(mUsbReceiver, filter);

        // Find the display device by pid/vid
        mUsbDevice = null;
        for (int i = 0; i < availableDrivers.size(); i++) {
            mDriver = availableDrivers.get(i);

            // See if we can find the adafruit M0 which is the Radio
            mUsbDevice = mDriver.getDevice();

            if (checkUsbDevice(mUsbDevice)) {
                Log.d(TAG, "found Display");
                break;
            } else {
                mUsbDevice = null;
            }
        }

        if (mUsbDevice == null) {
            Log.d(TAG,"No Display USB device found");
            return;
        }

        if (!manager.hasPermission(mUsbDevice)) {
            //ask for permission

            PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, new Intent(GET_USB_PERMISSION), 0);
            mContext.registerReceiver(new PermissionReceiver(), new IntentFilter(GET_USB_PERMISSION));
            //manager.requestPermission(mUsbDevice, pi);
            //return;

            Log.d(TAG, "USB: No Permission");
            return;
        }

        UsbDeviceConnection connection = manager.openDevice(mDriver.getDevice());
        if (connection == null) {
            Log.d(TAG, "USB connection == null");
            return;
        }

        try {
            sPort = (UsbSerialPort) mDriver.getPorts().get(0);//Most have just one port (port 0)
            sPort.open(connection);
            sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            sPort.setDTR(true);
        } catch (IOException e) {
            Log.d(TAG, "USB: Error setting up device: " + e.getMessage());
            try {
                sPort.close();
            } catch (IOException e2) {/*ignore*/}
            sPort = null;
            return;
        }
        Log.d(TAG, "USB: Connected");
        startIoManager();
    }

    private class PermissionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(this);
            if (intent.getAction().equals(GET_USB_PERMISSION)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.d(TAG, "USB we got permission");
                    if (device != null) {
                        initUsb();
                    } else {
                        Log.d(TAG, "USB perm receive device==null");
                    }

                } else {
                    Log.d(TAG, "USB no permission");
                }
            }
        }
    }

    public void stopIoManager() {
        synchronized (mSerialConn) {
            //status.setText("Disconnected");
            if (mSerialIoManager != null) {
                Log.d(TAG, "Stopping io manager ..");
                mSerialIoManager.stop();
                mSerialIoManager = null;
                mListener = null;
            }
            if (sPort != null) {
                try {
                    sPort.close();
                } catch (IOException e) {
                    // Ignore.
                }
                sPort = null;
            }
            Log.d(TAG, "USB Disconnected");

        }
    }

    public void startIoManager() {

        synchronized (mSerialConn) {
            if (sPort != null) {
                Log.d(TAG, "Starting io manager ..");
                //mListener = new BBListenerAdapter();
                mListener = new CmdMessenger(sPort, ',', ';', '\\');
                mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
                mExecutor.submit(mSerialIoManager);
                Log.d(TAG, "USB Connected");
            }
        }
    }

    // We use this to catch the USB accessory detached message
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            final String TAG = "mUsbReceiver";
            Log.d(TAG, "onReceive entered");
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "A USB Accessory was detached (" + device + ")");
                if (device != null) {
                    if (mUsbDevice == device) {
                        Log.d(TAG, "It's this device");
                        mUsbDevice = null;
                        stopIoManager();
                    }
                }
            }
            if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG, "USB Accessory attached (" + device + ")");
                if (mUsbDevice == null) {
                    Log.d(TAG, "Calling initUsb to check if we should add this device");
                    initUsb();
                } else {
                    Log.d(TAG, "this USB already attached");
                }
            }
            Log.d(TAG, "onReceive exited");
        }
    };

    public void flush2Board() {

        if (mListener != null) {
            mListener.flushWrites();
        } else {
            // Emulate board's 30ms refresh time
            try {
                Thread.sleep(2);
            } catch (Throwable e) {
            }
        }
    }

    public void aRGBtoBoardScreen(Buffer buf, int [] destScreen) {

        if (buf == null) {
            return;
        }

        int [] buffer = (int [])buf.array();

        for (int pixelNo = 0; pixelNo < (mScreenWidth * mScreenHeight); pixelNo++) {
            int pixel_offset = pixelNo * 3;
            int pixel = buffer[pixelNo];
            int a = pixel & 0xff;
            destScreen[pixel_offset] = (pixel >> 16) & 0xff;    //r
            destScreen[pixel_offset + 1] = (pixel >> 8) & 0xff; //g
            destScreen[pixel_offset + 2] = pixel & 0xff;        //b
        }
    }

    // Gamma correcttion for LEDs
    static int  gamma8[] = {
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,
            1,  1,  1,  1,  1,  1,  1,  1,  1,  2,  2,  2,  2,  2,  2,  2,
            2,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4,  4,  4,  5,  5,  5,
            5,  6,  6,  6,  6,  7,  7,  7,  7,  8,  8,  8,  9,  9,  9, 10,
            10, 10, 11, 11, 11, 12, 12, 13, 13, 13, 14, 14, 15, 15, 16, 16,
            17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 23, 24, 24, 25,
            25, 26, 27, 27, 28, 29, 29, 30, 31, 32, 32, 33, 34, 35, 35, 36,
            37, 38, 39, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 50,
            51, 52, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 66, 67, 68,
            69, 70, 72, 73, 74, 75, 77, 78, 79, 81, 82, 83, 85, 86, 87, 89,
            90, 92, 93, 95, 96, 98, 99,101,102,104,105,107,109,110,112,114,
            115,117,119,120,122,124,126,127,129,131,133,135,137,138,140,142,
            144,146,148,150,152,154,156,158,160,162,164,167,169,171,173,175,
            177,180,182,184,186,189,191,193,196,198,200,203,205,208,210,213,
            215,218,220,223,225,228,231,233,236,239,241,244,247,249,252,255 };


    static int gammaCorrect(int value) {
        //return ((value / 255) ^ (1 / gamma)) * 255;
        if (value>255) value = 255;
        if (value < 0) value = 0;
        return gamma8[value];
    }


}
