package com.pino.pino;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.google.android.things.contrib.driver.voicehat.Max98357A;
import com.google.android.things.contrib.driver.voicehat.VoiceHat;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

public class PinoVoice implements TextToSpeech.OnInitListener {

    String TAG = "PinoVoice";
    private TextToSpeech mVoice;
    String mFilesDir;
    String mFilename;
    int mBufferSize;
    public AudioTrack mAudioTrack;
    private AudioFormat mAudioFormat;
    private AudioDeviceInfo mDevice;
    Max98357A mDac;

    public PinoVoice(Context context, AudioFormat format, AudioDeviceInfo device) {

        mAudioFormat = format;
        mDevice = device;
        mVoice = new TextToSpeech(context, this);
        mVoice.setOnUtteranceProgressListener(mProgressListener);
        mFilesDir = context.getFilesDir().getAbsolutePath();
        mFilename = mFilesDir + "/" + "tts.wav";
        mBufferSize = AudioTrack.getMinBufferSize(format.getSampleRate()
                , format.getChannelMask(), format.getEncoding());
        try {
            mDac = VoiceHat.openDac();
            //dac.setGainSlot(Max98357A.GAIN_SLOT_ENABLE);
            mDac.setSdMode(Max98357A.SD_MODE_SHUTDOWN);
            Log.d(TAG, "Voicehat is setup");
        } catch (Exception e) {
            Log.d(TAG, "Cannot open Voicehat: " + e.toString());
        }
    }

    @Override
    public void onInit(int status) {
        Log.d(TAG, "Text To Speech onInit...");
        // check for successful instantiation
        if (status == TextToSpeech.SUCCESS) {
            if (mVoice.isLanguageAvailable(Locale.UK) == TextToSpeech.LANG_AVAILABLE)
                mVoice.setLanguage(Locale.UK);
            mVoice.setSpeechRate((float) 0.9);
            speak("I'm Pino", "impino");
            Log.d(TAG, "Text To Speech ready...");

        } else if (status == TextToSpeech.ERROR) {
            Log.d(TAG, "Sorry! Text To Speech failed...");
        }
    }

    private UtteranceProgressListener mProgressListener =
            new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "synthesizeToFile onStart " + utteranceId);

                }

                @Override
                public void onError(String utteranceId) {
                    Log.d(TAG, "synthesizeToFile onError " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "synthesizeToFile onDone " + utteranceId);
                    int bytesRead = 0;
                    byte[] buffer = new byte[mBufferSize];
                    try {
                        FileInputStream fileInputStream = new FileInputStream(mFilename + utteranceId);
                        DataInputStream dataInputStream = new DataInputStream(fileInputStream);

                        mAudioTrack = new AudioTrack.Builder()
                                .setAudioFormat(mAudioFormat)
                                .setBufferSizeInBytes(mBufferSize)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build();
                        if (mDevice != null) {
                            mAudioTrack.setPreferredDevice(mDevice);
                        }
                        // Enable Speaker
                        mAudioTrack.play();
                        mDac.setSdMode(Max98357A.SD_MODE_LEFT);
                        while ((bytesRead = dataInputStream.read(buffer, 0, mBufferSize)) > -1) {
                            mAudioTrack.write(buffer, 0, bytesRead);
                        }
                        mAudioTrack.flush();
                        // Mute speaker
                        mDac.setSdMode(Max98357A.SD_MODE_SHUTDOWN);
                        mAudioTrack.stop();
                        mAudioTrack.release();
                        dataInputStream.close();
                        fileInputStream.close();
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "file not found");
                    } catch (IOException e) {
                        Log.e(TAG, "voice play error: " + e.getMessage());
                    }

                }
            };

    public void speak(String text, String utteranceId) {
        Log.d(TAG, "speak: " + text);
        File file = new File(mFilename + utteranceId);
        if (!file.exists()) {
            Log.d(TAG, "file: " + mFilename + utteranceId);
            try {
                file.createNewFile();
            } catch (Exception e) {
                Log.d(TAG, "cannot create file: " + mFilename + utteranceId);

            }
        }
        mVoice.synthesizeToFile(text, null, file, utteranceId);
        Log.d(TAG, "synthesizeToFile queued");
    }
}
