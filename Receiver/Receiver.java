// java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>");
            return;
        }

        String senderIp = args[0];
        int sender_port = Integer.parseInt(args[1]);
        int dataPort = Integer.parseInt(args[2]);
        String outputFile = args[3];
        int rn = Integer.parseInt(args[4]);

        int ackCounter = 0;
        byte[] receiverBuffer = new byte[DSPacket.MAX_PACKET_SIZE];

        try {
            DatagramSocket socket = new DatagramSocket(dataPort);
            FileOutputStream outputFileStream = new FileOutputStream(outputFile);
            InetAddress senderAddress = InetAddress.getByName(senderIp);
            boolean running = true;
            while (running) {
                DatagramPacket rawPacket = new DatagramPacket(receiverBuffer, receiverBuffer.length);
                socket.receive(rawPacket);

                DSPacket dsPacket = new DSPacket(rawPacket.getData());
                byte type = dsPacket.getType();
                int seqNum = dsPacket.getSeqNum();

                if (type == DSPacket.TYPE_DATA) {
                    outputFileStream.write(dsPacket.getPayload());
                    System.out.println("Received DATA Seq: " + seqNum);
                } else if (type == DSPacket.TYPE_SOT) {
                    System.out.println("Received SOT. Starting transfer...");
                } else if (type == DSPacket.TYPE_EOT) {
                    System.out.println("Received EOT. Wrapping up...");
                    running = false;
                    outputFileStream.close();
                    socket.close();
                }

                ackCounter++;

                boolean shouldDrop = (rn > 0 && ackCounter % rn == 0);
                if (shouldDrop) {
                    System.out.println("!!! RN Triggered: Dropping ACK for Seq " + seqNum);
                } else {
                    DSPacket ack = new DSPacket(DSPacket.TYPE_ACK, seqNum, null);
                    byte[] ackBytes = ack.toBytes();

                    DatagramPacket ackDatagram = new DatagramPacket(ackBytes, ackBytes.length, senderAddress,
                            sender_port);
                    socket.send(ackDatagram);
                    System.out.println("Sent ACK for Seq: " + seqNum);
                }
                System.out.println("Transfer complete. File saved to " + outputFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}