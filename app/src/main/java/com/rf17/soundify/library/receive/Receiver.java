package com.rf17.soundify.library.receive;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.rf17.soundify.library.Soundify;
import com.rf17.soundify.library.Config;
import com.rf17.soundify.library.exception.SoundifyException;
import com.rf17.soundify.library.utils.DebugUtils;
import com.rf17.soundify.library.utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class Receiver {

    private AudioRecord audioRecord;
    private Thread thread;
    private boolean threadRunning = true;
    private static Receiver sReceiver;

    private List<Byte> list = new ArrayList<>();
    private short[] recordedData = new short[Config.TIME_BAND];

    public static Receiver getReceiver() {
        if (sReceiver == null) {
            sReceiver = new Receiver();
        }
        return sReceiver;
    }

    public Receiver() {
        initThread();
    }

    private void initThread() {
        thread = new Thread() {
            @Override
            public void run() {
                while (threadRunning) {
                    audioRecord.read(recordedData, 0, Config.TIME_BAND);
                    short parsedData = parseRecData(recordedData);
                    if (parsedData != Config.NONSENSE_DATA) {
                        list.add((byte) parsedData);
                    }
                }
            }
        };
    }

    private short parseRecData(short[] recordedData) {
        float[] floatData = ListUtils.convertArrayShortToArrayFloat(recordedData);
        short freq = ReceiverUtils.calcFreq(floatData);
        short data = ReceiverUtils.calcData(freq);

        DebugUtils.log("Freq: " + freq + "  |  data: " + data);

        switch (data) {
            case Config.START_COMMAND:
                list = new ArrayList<>();
                return Config.NONSENSE_DATA;
            case Config.STOP_COMMAND:
                byte[] retByte = ListUtils.convertListBytesToArrayBytes(list);
                Soundify.soundifyListener.OnReceiveData(retByte);
                return Config.NONSENSE_DATA;
            default:
                if(data >= Config.STOP_COMMAND){
                    return Config.NONSENSE_DATA;
                }else{
                    return data;
                }
        }
    }

    public void inicializeReceiver() throws SoundifyException {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, Config.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, Config.AUDIO_FORMAT, AudioTrack.getMinBufferSize(Config.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4);
        }
        if (audioRecord.getState() != AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.startRecording();
        }
        if(!threadRunning){
            threadRunning = true;
        }
        if (thread.getState() == Thread.State.NEW) {
            thread.start();
        }
        if (thread.getState() == Thread.State.TERMINATED) {
            initThread();
            thread.start();
        }
    }

    public void stopReceiver() throws SoundifyException {
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.stop();
            audioRecord.release();
        }
        if(threadRunning){
            threadRunning = false;
        }
        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

}
