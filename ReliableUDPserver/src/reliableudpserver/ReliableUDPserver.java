/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package reliableudpserver;

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

    DatagramSocket serverSocket;

    public ReceivingListener() {
        this.serverSocket = ReliableUDPserver.serverSocket;
    }

    public void run() {
        while (true) {
            try {
                byte[] tempData = new byte[1024];
                DatagramPacket packet = new DatagramPacket(tempData, tempData.length);
                serverSocket.receive(packet);
                byte[] data = new byte[packet.getLength()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = tempData[i];
                }
                InetAddress address = packet.getAddress();
                ArrayList<EndPoint> points = ReliableUDPserver.points;
                boolean found = false;
                for (int i = 0; i < points.size(); i++) {
                    if (address.getHostName().equals(points.get(i).host)
                            && packet.getPort() == points.get(i).port) {
                        points.get(i).handleReceive(data);
                        found = true;
                        break;
                    }
                }
                if (!found && data[0] == 0) {
                    EndPoint point = new EndPoint(address.getHostName(), packet.getPort());
                    //System.out.println(packet.getPort());
                    points.add(point);
                    point.handleReceive(data);
                }

            } catch (Exception e) {

            }
        }
    }
}

class EndPoint {

    public String host;
    public int port;
    private int senderState = 0;
    private int receiverState = 0;
    private DatagramSocket severSocket;
    private TimeoutHandler handler;
    private InetAddress IP;

    public EndPoint(String host, int port) {
        this.host = host;
        this.port = port;
        this.severSocket = ReliableUDPserver.serverSocket;
        try {
            this.IP = InetAddress.getByName(this.host);
        } catch (Exception e) {

        }
    }

    public void changeSenderState() {
        this.senderState = (this.senderState + 1) % 4;
    }

    public void changeReceiverState() {
        this.receiverState = (this.receiverState + 1) % 2;
    }

    public static short calcChecksum(byte[] segment) {
        short result = 0;
        for (int i = 0; i < segment.length; i++) {
            int b1 = segment[i], b2 = (i != segment.length - 1 ? segment[i + 1] : 0);
            short added = (short) ((b1 << 8) + b2);
            int tempAdd = (b1 << 8) + b2;
            result += added;
            if (tempAdd != added) {
                result++;
            }
        }
        return (short) Math.abs(~result);
    }

    public String extract(byte[] data) {
        byte[] actualData = new byte[data.length - 4];
        for (int i = 2; i < data.length - 2; i++) {
            actualData[i - 2] = data[i];
        }
        String stringData = new String(actualData);
        System.out.println("server received: " + stringData + " with seq " + data[1]+" from client with IP " + this.IP.getHostAddress()+", and port " + this.port);
        return stringData;
    }

    public void sendAck(byte seq) {
        try {
            byte[] typeSeq = {1, seq};
            short newChecksum = calcChecksum(typeSeq);
            byte[] checksumBytes = {(byte) (newChecksum >> 8), (byte) (newChecksum & 255)};
            byte[] sent = new byte[4];
            ByteBuffer buffer = ByteBuffer.wrap(sent);
            buffer.put(typeSeq);
            buffer.put(checksumBytes);
            severSocket.send(new DatagramPacket(sent, sent.length, IP, port));
        } catch (Exception e) {

        }
    }

    void handleReceive(byte[] data) {
        try {
            byte recData[] = new byte[data.length - 2];
            for (int i = 0; i < recData.length; i++) {
                recData[i] = data[i];
            }
            short checksum = calcChecksum(recData);
            short b1 = data[data.length - 2], b2 = data[data.length - 1];
            if (data[0] == 1) {
                if ((senderState + 1) % 2 == 0) {
                    if (data[1] != (senderState - 1) / 2 || checksum != (short) ((b1 << 8) + b2)) {
                        System.out.println("server received ack for seq " + data[1]+" from client with IP " + this.IP.getHostAddress()+", and port " + this.port);
                        System.out.println();
                        return;
                    }
                    handler.stop = true;
                    //for(int i=0; i<100000; i++);
                    changeSenderState();
                }
                System.out.println("server received ack for seq " + data[1]+" from client with IP " + this.IP.getHostAddress()+", and port " + this.port);
                System.out.println();
            } else {
                InetAddress IP = InetAddress.getByName(this.host);
                if (receiverState == 0) {
                    if (data[1] == 1 || checksum != (short) ((b1 << 8) + b2)) {
                        /*for(int i=0; i<data.length; i++)
                            System.out.print(data[i]);
                        System.out.println("");*/

                        // System.out.println((short)((b1<<8)+b2));
                        sendAck((byte) 1);
                    } else {
                        String rec = extract(data);
                        sendAck((byte) 0);
                        System.out.println("server sent ack for seq 0 to client with IP " + this.IP.getHostAddress()+", and port " + this.port);
                        changeReceiverState();
                        send(rec);
                    }
                } else {
                    if (data[1] == 0 || checksum != (short) ((b1 << 8) + b2)) {
                        sendAck((byte) 0);
                    } else {
                        String rec = extract(data);
                        sendAck((byte) 1);
                        System.out.println("server sent ack for seq 1 to client with IP " + this.IP.getHostAddress()+", and port " + this.port);
                        changeReceiverState();
                        send(rec);
                    }
                }
            }
        } catch (Exception e) {

        }
        /*
     
         */
    }

    public void send(String rec) {
        try {
            if (senderState % 2 == 0) {
                //BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                //String message = input.readLine();
                String message = rec.toUpperCase();
                byte[] typeSeq = {0, (byte) (senderState / 2)};
                byte[] messageBytes = message.getBytes("UTF-8");
                byte[] typeSeqMessage = new byte[1 + typeSeq.length + messageBytes.length];
                ByteBuffer buffer = ByteBuffer.wrap(typeSeqMessage);
                buffer.put(typeSeq);
                buffer.put(messageBytes);
                short shortChecksum = calcChecksum(typeSeqMessage);
                byte[] checksum = {(byte) (shortChecksum >> 8), (byte) (shortChecksum & 255)};
                byte[] typeSeqMessageChecksum = new byte[typeSeqMessage.length + 2];
                buffer = ByteBuffer.wrap(typeSeqMessageChecksum);
                buffer.put(typeSeqMessage);
                buffer.put(checksum);
                DatagramPacket packet = new DatagramPacket(typeSeqMessageChecksum, typeSeqMessageChecksum.length, IP, port);
                severSocket.send(packet);
                System.out.println("server sent: " + message + " with seq " + (senderState / 2)+" to client with IP " + this.IP.getHostAddress()+", and port " + this.port);
                handler = new TimeoutHandler(severSocket, packet);
                handler.start();
                changeSenderState();
            }

        } catch (Exception e) {

        }
    }
}

class TimeoutHandler extends Thread {

    private DatagramSocket socket;
    private DatagramPacket packet;
    private long delay = (long) 1e10;
    public boolean stop = false;

    public TimeoutHandler(DatagramSocket socket, DatagramPacket packet) {
        this.socket = socket;
        this.packet = packet;
        this.stop = false;
    }

    public void run() {
        while (true) {
            try {
                for (int i = 0; i < delay; i++) {
                    if (stop) {
                        break;
                    }
                }
                System.out.print("");
                System.out.print("");
                if(stop)
                    break;
                System.out.println("timeout in server waiting for client with IP " +packet.getAddress().getHostAddress()+", and port " + packet.getPort());
                socket.send(packet);
                System.out.println("server resent message to client with IP " +packet.getAddress().getHostAddress()+", and port " + packet.getPort());
            } catch (Exception e) {

            }
        }
    }
}

public class ReliableUDPserver {

    public static ArrayList<EndPoint> points = new ArrayList();
    public static DatagramSocket serverSocket;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            serverSocket = new DatagramSocket(8080);
            ReceivingListener listener = new ReceivingListener();
            listener.start();

        } catch (Exception e) {

        }
    }

}
