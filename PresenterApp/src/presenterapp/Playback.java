/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package presenterapp;

/**
 *
 * @author User
 */


import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;
import java.util.Random;

/**
 * Created by USER on 11/27/2016.
 */
public class Playback{

    private boolean stopCapture;
    private AudioFormat audioFormat;
    private SourceDataLine sourceDataLine;
    private byte[] tempBuffer;
    private DatagramSocket socket;
    private DatagramSocket socket1;
    private Thread worker;
    private static InetAddress ip;
    private static InetAddress [] ipClientConnected;
    private static int [] ports;
    public String passcode = "";
    private static int position;

    public Playback(){
        Random rand = new Random();
        passcode = Integer.toString(rand.nextInt(10000));
        tempBuffer = new byte[1280];
        position = 0;
        ipClientConnected = new InetAddress[10];
        ports = new int[10];

        try {
            socket = new DatagramSocket(2000);
            socket1 = new DatagramSocket(5000);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Create a audio format for source data line
    private AudioFormat getAudioFormat() {
        float sampleRate = 16000;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
    //Check & Create a I/O data lines
    public void createOutputline(){
        try {
            Mixer.Info[] mixerInfo = AudioSystem.getMixerInfo();    //get available mixers
            System.out.println("Available mixers:");
            Mixer mixer = null;
            for (int cnt = 0; cnt < mixerInfo.length; cnt++) {
                System.out.println(cnt + " " + mixerInfo[cnt].getName());
                mixer = AudioSystem.getMixer(mixerInfo[cnt]);

                Line.Info[] lineInfos = mixer.getTargetLineInfo();
                if (lineInfos.length >= 1 && lineInfos[0].getLineClass().equals(TargetDataLine.class)) {
                    System.out.println(cnt + " Mic is supported!");
                    break;
                }
            }
            audioFormat = getAudioFormat();     //get the audio format

            //Create a output audio data line
            DataLine.Info dataLineInfo1 = new DataLine.Info(SourceDataLine.class, audioFormat);
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo1);
            sourceDataLine.open(audioFormat);
            sourceDataLine.start();

            // Setting the maximum volume
            FloatControl control = (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
            control.setValue(control.getMaximum());

        } catch (LineUnavailableException e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    //Create new sockets and threads for play audio & connect to the client
    public void runServer() {

        try {
            stopCapture = false;
            socket1 = new DatagramSocket(5000);
        } catch (SocketException e) {

        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopCapture) {
                    getClients();   
                }
            }
        });
        thread.start();
        System.out.println("Thread works");

        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                //create new datagram packet for store the received data packet from client
                DatagramPacket receivedPacket = new DatagramPacket(tempBuffer, tempBuffer.length);
                sourceDataLine.flush();
                while (!stopCapture) {
                    try {
                        //store the data coming from client
                        socket.receive(receivedPacket);
                        System.out.println("Data reciving");
                        if(ip.equals(receivedPacket.getAddress())) {
                            //playing audio available in tempBuffer
                            sourceDataLine.write(receivedPacket.getData(), 0, receivedPacket.getLength());

                        }
                    } catch (Exception e) {
                        sourceDataLine.flush();
                        System.out.println("No clients");
                    }
                }
                sourceDataLine.flush();
            }
        });
        worker.start();
    }

    //Stop the server by distroy thread created
    public void stopServer(){
        stopCapture = true;
        System.out.println("stop server:" + stopCapture);
    }

    //connect to new client disconnecting all other clients
    public void nextClient(){
        try {
            activeClient();
            socket = new DatagramSocket(2000);

        }catch (Exception e){
            System.out.println("not works");
        }
        stopCapture = false;
        System.out.println("I am working");
    }

    //Get the new client connected
    public void getClients(){
        byte [] passCo = new byte[32];
        DatagramPacket clientPasscode = new DatagramPacket(passCo,passCo.length);
        try {
            socket1.receive(clientPasscode);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("recived");
        String str = new String(clientPasscode.getData(), 0, clientPasscode.getLength());
        
        //get the recived passcode by client
        if(!isConnected(clientPasscode.getAddress()) && str.equals(passcode) && position < 10){
            ipClientConnected[position] = clientPasscode.getAddress() ;
            ports[position] = clientPasscode.getPort();
            str = "Wait for connecting";
            DatagramPacket message = new DatagramPacket(str.getBytes(),str.length(),clientPasscode.getAddress(),clientPasscode.getPort());
            try {
                socket1.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
            position++;
        }else if(!str.equals(passcode)){
            str = "Wrong passcode";
            DatagramPacket message = new DatagramPacket(str.getBytes(),str.length(),clientPasscode.getAddress(),clientPasscode.getPort());
            try {
                socket1.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if(position>=10){
            str = "Wait for moment";
            DatagramPacket message = new DatagramPacket(str.getBytes(),str.length(),clientPasscode.getAddress(),clientPasscode.getPort());
            try {
                socket1.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if(isConnected(clientPasscode.getAddress())){
            str = "Allready connected";
            DatagramPacket message = new DatagramPacket(str.getBytes(),str.length(),clientPasscode.getAddress(),clientPasscode.getPort());
            try {
                socket1.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //give the chance client to speak
    public void activeClient(){
        ip = ipClientConnected[0];
        System.out.println(ip);
        String str = "Connection successful";
        DatagramPacket message = new DatagramPacket(str.getBytes(),str.length(),ip,ports[0]);
        try {
            socket.send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(int i = 0;i<9;i++){
            ipClientConnected[i] = ipClientConnected[i+1];
            ports[i] = ports[i+1];
        }
        position--;
    }
    
    //get the number of clients connected
    public int noOfClientConnected(){
    return position;
    }
    public boolean isConnected(InetAddress ip){
    for(int i = 0;i<position;i++){
    if(ipClientConnected[i].equals(ip)){
    return true;}
    
        }
    return false;
    }
 
}
