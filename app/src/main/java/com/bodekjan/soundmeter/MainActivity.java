package com.bodekjan.soundmeter;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.FillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    ArrayList<Entry> yVals;
    boolean refreshed=false;
    Speedometer speedometer;
    public static Typeface tf;
    ImageButton infoButton;
    ImageButton refreshButton;
    LineChart mChart;
    TextView minVal;
    TextView maxVal;
    TextView mmVal;
    TextView curVal;
    long currentTime=0;
    long savedTime=0;
    boolean isChart=false;
    boolean isMoney=false;
    /* Decibel */
    private boolean bListener = true;
    private boolean isThreadRun = true;
    private Thread thread;
    float volume = 10000;
    int refresh=0;
    private MyMediaRecorder mRecorder;
    private TextToSpeech mTextToSpeech;

    final Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            DecimalFormat df1 = new DecimalFormat("####.0");
            if(msg.what == 1){
                if(!isChart){
                    initChart();
                    return;
                }
                speedometer.refresh();
                minVal.setText(df1.format(World.minDB));
                mmVal.setText(df1.format((World.minDB+World.maxDB)/2));
                maxVal.setText(df1.format(World.maxDB));
                curVal.setText(df1.format(World.dbCount));
                updateData(World.dbCount,0);
                if(refresh==1){
                    long now=new Date().getTime();
                    now=now-currentTime;
                    now=now/1000;
                    refresh=0;
                }else {
                    refresh++;
                }
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // prevent screen from dimming
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        setContentView(R.layout.activity_main);
        tf= Typeface.createFromAsset(this.getAssets(), "fonts/Let_s go Digital Regular.ttf");
        minVal=(TextView)findViewById(R.id.minval);minVal.setTypeface(tf);
        mmVal=(TextView)findViewById(R.id.mmval);mmVal.setTypeface(tf);
        maxVal=(TextView)findViewById(R.id.maxval);maxVal.setTypeface(tf);
        curVal=(TextView)findViewById(R.id.curval);curVal.setTypeface(tf);
        infoButton=(ImageButton)findViewById(R.id.infobutton);
        infoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InfoDialog.Builder builder = new InfoDialog.Builder(MainActivity.this);
                builder.setMessage(getString(R.string.activity_infobull));
                // builder.setTitle(getString(R.string.activity_infotitle));
                builder.setTitle("Config. threshold");
                // builder.setNegativeButton(getString(R.string.activity_infobutton),
                builder.setNegativeButton("Save",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (dialog instanceof InfoDialog) {
                                    float newThreshold = ((InfoDialog) dialog).getThreshold();
                                    World.setThreshold(newThreshold);
                                }
                                dialog.dismiss();
                            }
                        });
                builder.create().show();
            }
        });
        refreshButton=(ImageButton)findViewById(R.id.refreshbutton);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshRecord();
            }
        });

        speedometer=(Speedometer)findViewById(R.id.speed);
        mRecorder = new MyMediaRecorder();
        mTextToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                mTextToSpeech.setLanguage(Locale.getDefault());
            }
        });
        World.setOnTooMuchNoiseListener(new World.OnTooMuchNoiseListener() {
            @Override
            public void onTooMuchNoise() {
                if (mTextToSpeech != null && !mTextToSpeech.isSpeaking()) {
                    new AsyncTask<Void, Void, Void>() {

                        @Override
                        protected Void doInBackground(Void... voids) {
                            Log.w("android-sound-meter", "stop recording");
                            // pause recording
                            bListener = false;
                            mRecorder.delete(); //Stop recording and delete the recording file
                            isChart=false;

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            mTextToSpeech.speak("Pessoal, vocês estão falando muito alto.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null);

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.w("android-sound-meter", "restart recording");
                                    File file = FileUtil.createFile("temp.amr");
                                    if (file != null) {
                                        startRecord(file);
                                    } else {
                                        Log.e("android-sound-meter", getString(R.string.activity_recFileErr));
                                    }
                                    bListener = true;
                                }
                            }, 3500);
                        }
                    }.execute();
                }
            }
        });
        World.setOnUnknownCrashListener(new World.OnUnknownCrashListener() {
            @Override
            public void onUnknownCrash() {
                /*if (infoButton != null) {
                    infoButton.performClick();
                }*/

                /*Intent intent = getIntent();
                finish();
                startActivity(intent);*/
            }
        });
    }

    private void refreshRecord() {
        refreshed = true;
        World.minDB=100;
        World.dbCount=0;
        World.lastDbCount=0;
        World.maxDB=0;
        initChart();
    }

    private void updateData(float val, long time) {
        if(mChart==null){
            return;
        }
        if (mChart.getData() != null &&
                mChart.getData().getDataSetCount() > 0) {
            LineDataSet set1 = (LineDataSet)mChart.getData().getDataSetByIndex(0);
            set1.setValues(yVals);
            Entry entry=new Entry(savedTime,val);
            set1.addEntry(entry);
            if(set1.getEntryCount()>200){
                set1.removeFirst();
                set1.setDrawFilled(false);
            }
            mChart.getData().notifyDataChanged();
            mChart.notifyDataSetChanged();
            mChart.invalidate();
            savedTime++;
        }
    }

    private void initChart() {
        if(mChart!=null){
            if (mChart.getData() != null &&
                    mChart.getData().getDataSetCount() > 0) {
                savedTime++;
                isChart=true;
            }
        }else{
            currentTime=new Date().getTime();
            mChart = (LineChart) findViewById(R.id.chart1);
            mChart.setViewPortOffsets(50, 20, 5, 60);
            // no description text
            mChart.setDescription("");
            // enable touch gestures
            mChart.setTouchEnabled(true);
            // enable scaling and dragging
            mChart.setDragEnabled(false);
            mChart.setScaleEnabled(true);
            // if disabled, scaling can be done on x- and y-axis separately
            mChart.setPinchZoom(false);
            mChart.setDrawGridBackground(false);
            //mChart.setMaxHighlightDistance(400);
            XAxis x = mChart.getXAxis();
            x.setLabelCount(8, false);
            x.setEnabled(true);
            x.setTypeface(tf);
            x.setTextColor(Color.GREEN);
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setDrawGridLines(true);
            x.setAxisLineColor(Color.GREEN);
            YAxis y = mChart.getAxisLeft();
            y.setLabelCount(6, false);
            y.setTextColor(Color.GREEN);
            y.setTypeface(tf);
            y.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
            y.setDrawGridLines(false);
            y.setAxisLineColor(Color.GREEN);
            y.setAxisMinValue(0);
            y.setAxisMaxValue(120);
            mChart.getAxisRight().setEnabled(true);
            yVals = new ArrayList<Entry>();
            yVals.add(new Entry(0,0));
            LineDataSet set1 = new LineDataSet(yVals, "DataSet 1");
            set1.setValueTypeface(tf);
            set1.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set1.setCubicIntensity(0.02f);
            set1.setDrawFilled(true);
            set1.setDrawCircles(false);
            set1.setCircleColor(Color.GREEN);
            set1.setHighLightColor(Color.rgb(244, 117, 117));
            set1.setColor(Color.GREEN);
            set1.setFillColor(Color.GREEN);
            set1.setFillAlpha(100);
            set1.setDrawHorizontalHighlightIndicator(false);
            set1.setFillFormatter(new FillFormatter() {
                @Override
                public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
                    return -10;
                }
            });
            LineData data;
            if (mChart.getData() != null &&
                    mChart.getData().getDataSetCount() > 0) {
                data =  mChart.getLineData();
                data.clearValues();
                data.removeDataSet(0);
                data.addDataSet(set1);
            }else {
                data = new LineData(set1);
            }

            data.setValueTextSize(9f);
            data.setDrawValues(false);
            mChart.setData(data);
            mChart.getLegend().setEnabled(false);
            mChart.animateXY(2000, 2000);
            // dont forget to refreshRecord the drawing
            mChart.invalidate();
            isChart=true;
        }

    }

    /* Sub-chant analysis */
    private void startListenAudio() {
        if (thread != null) {
            return;
        }
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (BuildConfig.DEBUG) {
                    Log.d("android-sound-meter", "initialize thread #" + thread.getId());
                }
                while (isThreadRun) {
                    try {
                        if(bListener) {
                            volume = mRecorder.getMaxAmplitude();  //Get the sound pressure value
                            if(volume > 0 && volume < 1000000) {
                                World.setDbCount(20 * (float)(Math.log10(volume)));  //Change the sound pressure value to the decibel value
                                // Update with thread
                                Message message = new Message();
                                message.what = 1;
                                handler.sendMessage(message);
                            }
                        }
                        if(refreshed){
                            Thread.sleep(1200);
                            refreshed=false;
                        }else{
                            Thread.sleep(200);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        bListener = false;
                    }
                }
            }
        });
        thread.start();
    }

    /**
     * Start recording
     * @param fFile
     */
    public void startRecord(File fFile){
        try{
            mRecorder.setMyRecAudioFile(fFile);
            if (mRecorder.startRecorder()) {
                startListenAudio();
            }else{
                Toast.makeText(this, getString(R.string.activity_recStartErr), Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){
            Toast.makeText(this, getString(R.string.activity_recBusyErr), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        File file = FileUtil.createFile("temp.amr");
        if (file != null) {
            startRecord(file);
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.activity_recFileErr), Toast.LENGTH_LONG).show();
        }
        bListener = true;
    }

    /**
     * Stop recording
     */
    @Override
    protected void onPause() {
        super.onPause();
        bListener = false;
        mRecorder.delete(); //Stop recording and delete the recording file
        isChart=false;
    }

    @Override
    protected void onDestroy() {
        if (thread != null) {
            isThreadRun = false;
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            thread = null;
        }
        mRecorder.delete();
        if (mTextToSpeech != null) {
            mTextToSpeech.shutdown();
            mTextToSpeech = null;
        }
        super.onDestroy();
    }
}
