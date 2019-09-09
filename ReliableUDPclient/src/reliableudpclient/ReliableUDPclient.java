/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package reliableudpclient;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;

/**
 *
 * @author elteir
 */
class ReceivingListener extends Thread {
    DatagramSocket clientSocket;

    public ReceivingListener() {
        this.clientSocket = ReliableUDPclient.clientSocket;
    }
    
    public void run() {
        while(true) {
            try {
                byte[] tempData = new byte[1024];
                DatagramPacket packet = new DatagramPacket(tempData, tempData.length);
                clientSocket.receive(packet);
                byte[] data = new byte[packet.getLength()];
                for(int i=0; i<data.length; i++) {
                    data[i] = tempData[i];
                }
                InetAddress address = packet.getAddress();
                ArrayList<EndPoint> points = ReliableUDPclient.points;
                boolean found = false;
                for(int i=0; i<points.size(); i++) {
                    if(address.getHostAddress().equals(points.get(i).host) &&
                            packet.getPort()==points.get(i).port) {
                        points.get(i).handleReceive(data);
                        found = true;
                        break;
                    }
                }
                if(!found && data[0]==0) {
                    EndPoint point = new EndPoint(address.getHostAddress(), packet.getPort());
                    points.add(point);
                    point.start();
                    point.handleReceive(data);
                }
                
            }
            catch(Exception e) {

            }
        }
    }
}
class EndPoint extends Thread {
    public String host;
    public int port;
    private int senderState = 0;
    private int receiverState = 0;
    private DatagramSocket clientSocket;
    private TimeoutHandler handler;
    private InetAddress IP;
    
    public EndPoint(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientSocket = ReliableUDPclient.clientSocket;
        try {
            this.IP = InetAddress.getByName(this.host);
        }
        catch(Exception e) {
            
        }
    }
    
    public void changeSenderState() {
        this.senderState = (this.senderState + 1)%4;
    }
    
    public void changeReceiverState() {
        this.receiverState = (this.receiverState + 1)%2;
    }
    
    public static short calcChecksum(byte[] segment) {
        short result = 0;
        for(int i=0; i<segment.length; i++) {
            int b1= segment[i], b2 = (i!=segment.length-1?segment[i+1]:0);
            short added = (short)((b1<<8)+b2);
            int tempAdd = (b1<<8)+b2;
            result += added;
            if(tempAdd != added)
                result++;
        }
        return (short)Math.abs(~result);
    }
    
    public void extract(byte[] data) {
        byte[] actualData = new byte[data.length-4];
        for(int i=2; i<data.length-2; i++)
            actualData[i-2] = data[i];
        System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort() + "  received: " + new String(actualData)+" with seq "+data[1]);
    }
    
    public void sendAck(byte seq) {
        try {
            byte[] typeSeq = {1,seq};
            short newChecksum = calcChecksum(typeSeq);
            byte[] checksumBytes = {(byte)(newChecksum>>8), (byte)(newChecksum&255)};
            byte[] sent = new byte[4];
            ByteBuffer buffer = ByteBuffer.wrap(sent);
            buffer.put(typeSeq);
            buffer.put(checksumBytes);
            clientSocket.send(new DatagramPacket(sent, sent.length, IP, port));
        }
        catch(Exception e) {
            
        }
    }
    
    void handleReceive(byte[] data) {
        try{
            byte recData[] = new byte[data.length-2];
            for(int i=0; i<recData.length; i++)
                recData[i] = data[i];
            short checksum = calcChecksum(recData);
            short b1 = data[data.length-2], b2 = data[data.length-1];
            if(data[0]==1) {
                if((senderState + 1)%2==0) {
                    if(data[1]!=(senderState - 1)/2 || checksum != (short)((b1<<8)+b2)) {
                        System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort() + " received ack for seq "+data[1]); 
                        return;
                    }
                    handler.stop=true;
                    handler.join();
                    //for(int i=0; i<100000; i++);
                    changeSenderState();
                }      
                System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort() + " received ack for seq "+data[1]);
            }
            else {
                InetAddress IP = InetAddress.getByName(this.host);
                if(receiverState == 0) {
                    if(data[1]==1 || checksum != (short)((b1<<8)+b2)) {
                        sendAck((byte)1);
                    }
                    else {
                        extract(data);
                        sendAck((byte)0);
                        System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort()+ " sent ack for seq 0");
                        changeReceiverState();
                    }
                }
                else {
                    if(data[1]== 0 || checksum != (short)((b1<<8)+b2)) {
                        sendAck((byte)0);
                    }
                    else {
                        extract(data);
                        sendAck((byte)1);
                        System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort()+" sent ack for seq 1");
                        changeReceiverState();
                    }
                }
            }
        }
        catch(Exception e) {
            
        }
        /*
     
*/
    }
    
    public void run() {
        try {
            while(true) {
                System.out.print("");
                if(senderState % 2==0) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                    String message = input.readLine();
                    byte[] typeSeq = {0,(byte)(senderState/2)};
                    byte[] messageBytes = message.getBytes("UTF-8");
                    byte[] typeSeqMessage = new byte[1+typeSeq.length+messageBytes.length];
                    ByteBuffer buffer = ByteBuffer.wrap(typeSeqMessage);
                    buffer.put(typeSeq);
                    buffer.put(messageBytes);
                    short shortChecksum = calcChecksum(typeSeqMessage);
                    byte[] checksum = {(byte)(shortChecksum>>8), (byte)(shortChecksum&255)};
                    byte[] typeSeqMessageChecksum = new byte[typeSeqMessage.length+2];
                    buffer = ByteBuffer.wrap(typeSeqMessageChecksum);
                    buffer.put(typeSeqMessage);
                    buffer.put(checksum);
                    DatagramPacket packet = new DatagramPacket(typeSeqMessageChecksum, typeSeqMessageChecksum.length, IP, port);
                    clientSocket.send(packet);
                    System.out.println("client with IP 127.0.0.1 and port " + clientSocket.getLocalPort()+" sent: "+message+" with seq "+(senderState/2));
                    /*System.out.println(typeSeqMessageChecksum.length);
                    byte[] data=typeSeqMessageChecksum;
                        for(int i=0; i<data.length; i++)
                            System.out.print(data[i]);
                        System.out.println("");*/
                    //short b1 = checksum[0], b2 = checksum[1];
                    //System.out.println((short)((b1<<8)+b2));
                    handler = new TimeoutHandler(clientSocket, packet);
                    handler.start();
                    changeSenderState();
                }
            }
            
        } catch (Exception e) {
            
        }
    }
}
class TimeoutHandler extends Thread {
    private DatagramSocket socket;
    private DatagramPacket packet;
    private long delay = (long) 1e9;
    public boolean stop = false;
    public TimeoutHandler(DatagramSocket socket, DatagramPacket packet) {
        this.socket = socket;
        this.packet=packet;
        this.stop = false;
    }
    public void run() {
        while(true) {
            try {
                for(int i=0; i<delay; i++) {
                    if(stop)
                        break;
                }
                if(stop)
                    break;
                System.out.println("timeout in client with IP 127.0.0.1 and port " + socket.getLocalPort());
                socket.send(packet);
                System.out.println("client with IP 127.0.0.1 and port " + socket.getLocalPort()+" resent message to server");
                System.out.println();
            }
            catch(Exception e) {
                System.out.println(e);
            }
        }
    }
}
public class ReliableUDPclient extends Thread {
    public static ArrayList<EndPoint> points = new ArrayList();
    public static DatagramSocket clientSocket;
    public static void main(String[] args)  {
          try{              
              clientSocket = new DatagramSocket();
              String host = "127.0.0.1";
              int port = 8080;
              EndPoint point = new EndPoint(host, port);
              points.add(point);
              ReceivingListener listener = new ReceivingListener();
              listener.start();
              point.start();
          }
          catch(Exception e) {
              System.out.println(e);
          }
        //String IP = "localhost";
    
    }
}
