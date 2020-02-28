package com.example.user.micanywheremobile;

import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity {

    private EditText userInput;
    public static String passCode = "";
    private final Context context = this;
    private static AudioRecord recorder;
    private byte[] recorded;
    public boolean stopRecoeding;
    private int minBufferSize;
    private static DatagramSocket socket;
    private DatagramPacket datapacket;
    public static InetAddress ip;
    private Thread worker;
    public static boolean connected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.imageButton1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Prompt the passcode from user
                // get prompts.xml view
                LayoutInflater li = LayoutInflater.from(context);
                View promptsView = li.inflate(R.layout.prompts, null);

                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
//                             set prompts.xml to alertdialog builder
                alertDialogBuilder.setView(promptsView);

                userInput = (EditText) promptsView.findViewById(R.id.editText);

//                             set dialog message
                alertDialogBuilder.setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        passCode = userInput.getText().toString();
                        varifyPasscode();

                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

                // create alert dialog
                AlertDialog alertDialog = alertDialogBuilder.create();
                //show it
                alertDialog.show();
            }
        });

        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecoeding = true;
                if(connected) { //close all connections created when stop the recording
                    recorder.stop();
                    recorder.release();
                }
                connected = false;
                Toast.makeText(getApplicationContext(),"Stoped the connection",Toast.LENGTH_LONG).show();
            }
        });

    }

    //Varify the passcode & if ok the passcode then sent the recorded packets
    public void varifyPasscode() {
        if (!connected) {
            try {
                recordPlayback();
                stopRecoeding = false;
                worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (connect()) {
                            connected = true;
                            recorder.startRecording();
                            byte[] finalMessage = new byte[1024];
                            DatagramPacket finalServerMessage = new DatagramPacket(finalMessage, finalMessage.length);

                            try {
                                socket.receive(finalServerMessage); //Wait for connction successful message
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            final String string = new String(finalMessage, 0, finalMessage.length);

                            runOnUiThread(new Runnable() { //Show the connction successful message in the UI
                                @Override
                                public void run() {
                                    Toast.makeText(getApplication(), string, Toast.LENGTH_LONG).show();
                                }
                            });

                            datapacket = new DatagramPacket(recorded, recorded.length, ip, 2000);

                            while (!stopRecoeding) {
                                Log.i("my tag", "recording");
                                recorder.read(recorded, 0, recorded.length);    //Recording using MIC

                                try {
                                    socket.send(datapacket);    //sent the recorded packet
                                    Log.i("sent", "packet sent");
                                } catch (IOException e) {
                                    try {
                                        socket = new DatagramSocket();
                                        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
                                        recorder.startRecording();
                                        connected = true;
                                    } catch (SocketException e1) {
                                        e1.printStackTrace();
                                    }
                                }

                            }
                            socket.close();

                        } else {
                            //close all connection if passcode wrong
                            recorder.stop();
                            recorder.release();
                            socket.close();
                            Log.i("recorder", "released");
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
            worker.start();
        }else{
            Toast.makeText(getApplicationContext(),"Allready connected",Toast.LENGTH_LONG).show();
        }
    }
    //Check the passcode & Ininializing the connction
    public boolean connect(){
        boolean value;
        DatagramPacket message = new DatagramPacket(passCode.getBytes(),passCode.length(),ip,5000);

        try {
            socket.send(message);   //sent the passcode to the server
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("sent message");

        byte [] recieved = new byte[32];
        DatagramPacket returnMessage = new DatagramPacket(recieved,recieved.length);

        try {
            socket.receive(returnMessage);  //ReturnMessage of the server checked the passcode
        } catch (IOException e) {
            e.printStackTrace();
        }

        ip = returnMessage.getAddress();
        final String str = new String(returnMessage.getData(), 0, returnMessage.getLength());
        System.out.println(str);

        runOnUiThread(new Runnable() {  //Show the return message of the server in the UI
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),str,Toast.LENGTH_LONG).show();
            }
        });

        if(str.equals("Wait for connecting")){
            value = true;
        }else {
            value = false;
        }
        return value;
    }

    //Cofigure network & output lines for speaker
    public void recordPlayback() throws Exception {
        minBufferSize = 1280;
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        Log.i("mic", "mic opened");
        recorded = new byte[minBufferSize];
        Log.i("array", "Array created");
        socket = new DatagramSocket();
        Log.d("socket", "socket opened" + minBufferSize);
        ip = InetAddress.getByName("172.16.99.255");
    }
}
