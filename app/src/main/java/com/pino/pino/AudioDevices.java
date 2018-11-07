package com.pino.pino;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.util.Log;

public class AudioDevices {

    private static String TAG = "Pino.AudioDevices";

    AudioDeviceInfo mAudioOutputDevice;
    AudioDeviceInfo mAudioInputDevice;
    private Context mContext;

    public AudioDevices(Context context) {

        mContext = context;
        mAudioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS,
                AudioDeviceInfo.TYPE_BUS);
        if (mAudioOutputDevice == null) {
            Log.e(TAG, "failed to found I2S audio output device, using default");
        }
        mAudioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS,
                AudioDeviceInfo.TYPE_BUS);
        if (mAudioInputDevice == null) {
            Log.e(TAG, "failed to found I2S audio input device, using default");
        }
    }

    AudioDeviceInfo getOutputDevice() {
        return mAudioOutputDevice;
    }

    AudioDeviceInfo getInputDevice() {
        return mAudioInputDevice;
    }

    // Audio constants.
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final AudioFormat AUDIO_FORMAT_STEREO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();

    // For Pino's voice
    public static final AudioFormat AUDIO_FORMAT_OUT_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();

    // Possibly use for recognizer
    public static final AudioFormat AUDIO_FORMAT_IN_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    private static final int SAMPLE_BLOCK_SIZE = 1024;


    private AudioDeviceInfo findAudioDevice(int deviceFlag, int deviceType) {
        AudioManager manager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] adis = manager.getDevices(deviceFlag);
        for (AudioDeviceInfo adi : adis) {
            Log.d(TAG, "found audio device: " + adi.getChannelMasks() + ", type: " + adi.getType());
            if (adi.getType() == deviceType) {
                return adi;
            }
        }
        return null;
    }

}
