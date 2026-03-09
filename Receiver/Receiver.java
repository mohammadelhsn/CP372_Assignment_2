// java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

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
        int expect_seq = 1;
        Map<Integer, byte[]> buffer = new HashMap<>();
        
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

                if (type == DSPacket.TYPE_SOT){
                    System.out.println("Received SOT seq=" + seqNum);

                }else if (type == DSPacket.TYPE_DATA) {
                    if (!buffer.containsKey(seqNum)) {
                      buffer.put(seqNum, dsPacket.getPayload());
                      System.out.println("Buffered DATA seq=" + seqNum);
                   }

                   while (buffer.containsKey(expect_seq)) {
                       outputFileStream.write(buffer.get(expect_seq));
                       System.out.println("Delivered DATA seq=" + expect_seq);
                       buffer.remove(expect_seq);
                        expect_seq = (expect_seq + 1) % 128;
                 }

                 seqNum = (expect_seq - 1 + 128) % 128;
              
                } else if (type == DSPacket.TYPE_EOT) {
                    System.out.println("Received EOT. Wrapping up...");
                    running = false;
                }

                ackCounter++;

                boolean shouldDrop = (ChaosEngine.shouldDrop(ackCounter, rn));
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

                if(!running){ 
                    outputFileStream.close();
                    socket.close();
                    System.out.println("Transfer complete. File saved to " + outputFile);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}