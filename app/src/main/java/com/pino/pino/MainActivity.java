package com.pino.pino;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.media.AudioRouting;
import android.media.AudioFormat;
import android.speech.tts.Voice;
import android.text.TextPaint;
// import the VoiceHat driver
import com.google.android.things.contrib.driver.voicehat.Max98357A;
import com.google.android.things.contrib.driver.voicehat.VoiceHat;

/**
 * Skeleton of Pino app
 */
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

public class MainActivity extends Activity implements
        RecognitionListener {

    private static String TAG = "Pino.MainActivity";

    Thread mSetupThread;

    Handler mHandler = new Handler(Looper.getMainLooper());;

    // For Pino LED Display
    private Context mContext;
    private DisplayDriver mDisplay = null;

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String WORD_SEARCH = "word";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "wakeup pino";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    // Speech Recognizer
    private SpeechRecognizer recognizer;
    private HashMap<String, Integer> captions;

    // Pino's voice
    PinoVoice mVoice;

    // Audio
    AudioDeviceInfo mAudioOutputDevice;
    AudioDeviceInfo mAudioInputDevice;

    Thread mPinoThread = null;

    boolean mSetupFinished = false;

    // UI Elements
    TextView mCaption;
    CaptionUpdater mCaptionUpdater;
    TextView mResult;
    ResultUpdater mResultUpdater;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        Log.d(TAG, "OnCreate");


        mContext = this.getApplicationContext();

        setContentView(R.layout.activity_main);

        mCaptionUpdater = new CaptionUpdater();
        mCaption = ((TextView) findViewById(R.id.caption_text));

        mResult = ((TextView) findViewById(R.id.result_text));
        mResultUpdater = new ResultUpdater();

                // Configure android audio
        Log.d(TAG, "Setup Audio");
        setupAudioDevices();

        // Setup LED Display and draw sample
        Log.d(TAG, "Setup Display");
        if (mDisplay == null) {
            // WS2811 matrix on Teensy Driver
            //mDisplay = new DisplayWS2811Matrix(mContext, 32, 8);
            // HUB75 Matrix on Teensy Driver
            mDisplay = new DisplayPanel(mContext, 64, 32);
        }

        mSetupThread = new Thread((new Runnable() {
            @Override
            public void run() {
                try {
                    // Setup voice
                    Log.d(TAG, "Setup Voice");
                    mVoice = new PinoVoice(mContext, AUDIO_FORMAT_OUT_MONO, mAudioOutputDevice);
                    Log.d(TAG, "Setup Voice Recognizer");
                    Assets assets = new Assets(mContext);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                    mSetupFinished = true;
                    switchSearch(KWS_SEARCH);
                } catch (Exception e) {
                    Log.d(TAG, "Setup Voice Recognizer failed: " + e.toString());
                }
            }
        }));

        // Prepare the data for UI
        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        captions.put(WORD_SEARCH, R.string.word_caption);
        mCaption.setText("Preparing the recognizer");

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            //return;
        } else {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
            //new SetupTask(this).execute();
            mSetupThread.start();
        }

        //mSetupThread.start();


        if (mPinoThread == null) {
            Log.d(TAG, "starting main pino thread");
            mPinoThread = new Thread(new Runnable() {
                public void run() {
                    Thread.currentThread().setName("Pino Thread");
                    while (true) {
                        pinoLoop();
                    }
                }
            });
            mPinoThread.start();
        } else {
            Log.d(TAG, "pino thread thread already running");
        }


    }

    private class CaptionUpdater implements Runnable{
        private String txt;
        @Override
        public void run() {
            mCaption.setText(txt);
        }
        public void setText(String txt){
            this.txt = txt;
        }
    }

    private class ResultUpdater implements Runnable{
        private String txt;
        @Override
        public void run() {
            mResult.setText(txt);
        }
        public void setText(String txt){
            this.txt = txt;
        }
    }


    private void pinoLoop() {
        // TODO: wait until voice tts ready
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "got perms");

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                //new SetupTask(this).execute();
                mSetupThread.start();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE))
            switchSearch(MENU_SEARCH);
        else if (text.equals(WORD_SEARCH))
            switchSearch(WORD_SEARCH);
        else {
            mCaptionUpdater.setText(text);
            mHandler.post(mCaptionUpdater);
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        ((TextView) findViewById(R.id.result_text)).setText("");
        mResultUpdater.setText("");
        mHandler.post(mResultUpdater);
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH)) {
            mVoice.speak("Speak to me", "speak2me");
        recognizer.startListening(searchName);
        } else {
            mVoice.speak("ok, what next?", "oknext");
            recognizer.startListening(searchName, 10000);
        }
        String caption = getResources().getString(captions.get(searchName));
        mCaptionUpdater.setText(caption);
        mHandler.post(mCaptionUpdater);
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup(mContext)
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .getRecognizer();
        recognizer.addListener(this);
        //.setSampleRate(48000)  // Experiment

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create grammar-based search for selection between demos
        File menuGrammar = new File(assetsDir, "menu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);

        // Create language model search
        File languageModel = new File(assetsDir, "en-70k-0.1-pruned.lm");
        recognizer.addNgramSearch(WORD_SEARCH, languageModel);
    }

    @Override
    public void onError(Exception error) {
        mCaptionUpdater.setText(error.getMessage());
        mHandler.post(mCaptionUpdater);
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
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


    // Audio constants.
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final AudioFormat AUDIO_FORMAT_STEREO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();

    // For Pino's voice
    private static final AudioFormat AUDIO_FORMAT_OUT_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();

    // Possibly use for recognizer
    private static final AudioFormat AUDIO_FORMAT_IN_MONO =
            new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .build();
    private static final int SAMPLE_BLOCK_SIZE = 1024;

    private void setupAudioDevices() {

        try {
            Max98357A dac = VoiceHat.openDac();
            dac.setSdMode(Max98357A.SD_MODE_LEFT);
            //dac.setGainSlot(Max98357A.GAIN_SLOT_ENABLE);
            //dac.setSdMode(Max98357A.SD_MODE_SHUTDOWN);
            Log.d(TAG, "Voicehat is setup");
        } catch (Exception e) {
            Log.d(TAG, "Cannot open Voicehat: " + e.toString());
        }

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

    private AudioDeviceInfo findAudioDevice(int deviceFlag, int deviceType) {
        AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
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
