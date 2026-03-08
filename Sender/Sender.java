// Need to program out the side
//Using UDP to send the message to the receiver

//Data wrapped in a packet class that can be converted to and from bytes for sending over UDP
import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.net.*;
import DSPacket;
import ChaosEngine;

public class Sender{
    //instance variables
    private String filePath;
    private String receiverIP;
    private int receiverPort;
    private int senderPort;
    private int windowSize;
    private int timeout;
    private DatagramSocket socket;
    //private List<DSPacket> packets; This can't be a shared one. 

    //constructor
    public Sender(String revieverIP, int receiverPort, int senderPort, String filePath, int timeout, int windowSize) throws SocketException {
        this.receiverIP = revieverIP;
        this.receiverPort = receiverPort;
        this.senderPort = senderPort;
        this.filePath = filePath;
        this.windowSize = windowSize;
        this.timeout = timeout;
        this.socket = new DatagramSocket(senderPort);
        this.packets = new ArrayList<>();
        socket.setSoTimeout(timeout);
    };

    //overloading the contructor to support stop and wait
    public Sender(String revieverIP, int receiverPort, int senderPort, String filePath, int timeout) throws SocketException {
        this(revieverIP, receiverPort, senderPort, filePath, timeout, 1);
    }

    //method to read the file and create packets
    private List<DSPacket> create_packet() throws IOException {
        byte[] buffer = new byte[124];
        int byteRead;
        int seqNum = 0;
        FileInputStream fis = new FileInputStream(filePath);
        while ((byteRead = fis.read(buffer)) != -1) {
            byte[] data = arrays.copyOf(buffer, byteRead);
            DSPacket packet = new DSPacket(DSPacket.TYPE_DATA,seqNum, data);
            packets.add(packet);
            seqNum = (seqNum + 1) % 124;

        }
    }

    public stop_and_wait(List<DSPacket> packets) throws IOException {

        for (DSPacket packet : packets) {
            time_out_ctr= 0;
            boolean ack_received = false;
            while (!ack_received) {
                DatagramPacket udpPacket = new DatagramPacket(packet.toBytes(), packet.toBytes().length, InetAddress.getByName(receiverIP), receiverPort);
                socket.send(udpPacket);
                System.out.println("Sent packet with sequence number: " + packet.getSeqNum());

                try {
                    byte[] ackBuffer = new byte[124];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);
                    DSPacket ack = DSPacket.fromBytes(ackPacket.getData());
                    if (ack.getType() == DSPacket.TYPE_ACK && ack.getSeqNum() == packet.getSeqNum()) {
                        System.out.println("Received ACK for packet with sequence number: " + packet.getSeqNum());
                        ack_received = true;
                        time_out_ctr = 0; 
                 
                } catch (SocketTimeoutException e) {
                    time_out_ctr++;
                    System.out.println("Timeout occurred for packet with sequence number: " + packet.getSeqNum() + ". Retrying... (Attempt " + time_out_ctr + ")");

                    if (time_out_ctr >= 3) {
                        System.out.println("unable to transfer file");
                        socket.close();
                        System.exit(1);
                    }
                }



        }
    }
}
    }
    

    public void go_back_n(List<DSPacket> packets) throws IOException {
}

