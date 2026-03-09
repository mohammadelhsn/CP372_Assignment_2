import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.*;
import java.net.*;

public class Sender {

    // Instance variables
    private String filePath;
    private String receiverIP;
    private int receiverPort;
    private int senderPort;
    private int windowSize;
    private int timeout;
    private DatagramSocket socket;

    // Constructor for Go-Back-N
    public Sender(String receiverIP, int receiverPort, int senderPort, String filePath, int timeout, int windowSize) throws SocketException {
        this.receiverIP= receiverIP;
        this.receiverPort= receiverPort;
        this.senderPort= senderPort;
        this.filePath= filePath;
        this.windowSize= windowSize;
        this.timeout= timeout;
        this.socket= new DatagramSocket(senderPort);
        socket.setSoTimeout(timeout);
    }

    // Overloaded constructor for Stop-and-Wait
    public Sender(String receiverIP, int receiverPort, int senderPort, String filePath, int timeout) throws SocketException {
        this(receiverIP, receiverPort, senderPort, filePath, timeout, 0);
    }

    // Tokenizer
    private List<DSPacket> createPackets() throws IOException {
        List<DSPacket> packets = new ArrayList<>();
        byte[] buffer = new byte[124];
        int bytesRead;
        int seqNum = 1; // DATA packets start at seq 1

        FileInputStream fis = new FileInputStream(filePath);
        while ((bytesRead = fis.read(buffer)) != -1) {
            byte[] data = Arrays.copyOf(buffer, bytesRead);
            DSPacket packet = new DSPacket(DSPacket.TYPE_DATA, seqNum, data);
            packets.add(packet);
            seqNum = (seqNum + 1) % 128; // wrap around at 128
        }
        fis.close();
        return packets;
    }

    //Stop and wait function
    public void stopAndWait(List<DSPacket> packets) throws IOException {
        for (DSPacket packet : packets) {
            int timeoutCtr = 0;
            boolean ackReceived = false;

            while (!ackReceived) {
                DatagramPacket udpPacket = new DatagramPacket(
                    packet.toBytes(),
                    packet.toBytes().length,
                    InetAddress.getByName(receiverIP),
                    receiverPort
                );
                socket.send(udpPacket);
                System.out.println("Sent DATA seq=" + packet.getSeqNum());

                try {
                    byte[] ackBuffer = new byte[128];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);

                    DSPacket ack = new DSPacket(ackBuffer);

                    if (ack.getType() == DSPacket.TYPE_ACK && ack.getSeqNum() == packet.getSeqNum()) {
                        System.out.println("ACK received seq=" + ack.getSeqNum());
                        ackReceived = true;
                        timeoutCtr = 0;
                    } else {
                        System.out.println("Wrong ACK received, retransmitting...");
                    }
                } catch (SocketTimeoutException e) {
                    timeoutCtr++;
                    System.out.println("Timeout #" + timeoutCtr + " for seq=" + packet.getSeqNum());
                    if (timeoutCtr >= 3) {
                        System.out.println("Unable to transfer file.");
                        socket.close();
                        System.exit(1);
                    }
                }
            }
        }
    }

   //Go Back N
    public void goBackN(List<DSPacket> packets) throws IOException {
        int base    = 0;
        int nextSeq = 0; 
        int timeoutCtr = 0;

        while (base < packets.size()) {
            while (nextSeq < base + windowSize && nextSeq < packets.size()) {

                List<DSPacket> group = new ArrayList<>();
                for (int i = nextSeq; i < Math.min(nextSeq + 4, packets.size()); i++) {
                    group.add(packets.get(i));
                }
                List<DSPacket> toSend = ChaosEngine.permutePackets(group);
                for (DSPacket pkt : toSend) {
                    byte[] rawBytes = pkt.toBytes();
                    DatagramPacket udpPacket = new DatagramPacket(
                        rawBytes,
                        rawBytes.length,
                        InetAddress.getByName(receiverIP),
                        receiverPort
                    );
                    socket.send(udpPacket);
                    System.out.println("Sent DATA seq=" + pkt.getSeqNum());
                }

                nextSeq += toSend.size();
            }

            try {
                byte[] ackBuffer = new byte[128];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ackPacket);

                DSPacket ack = new DSPacket(ackBuffer);

                if (ack.getType() == DSPacket.TYPE_ACK) {
                    int ackedSeq = ack.getSeqNum();
                    System.out.println("ACK received seq=" + ackedSeq);

                for (int i = base; i < packets.size(); i++) {
                    if (packets.get(i).getSeqNum() == ackedSeq) {
                        base = i + 1;
                        timeoutCtr = 0; 
                        break;
                    }
                }
            }

            } catch (SocketTimeoutException e) {
                timeoutCtr++;
                System.out.println("Timeout #" + timeoutCtr + " at base seq=" + packets.get(base).getSeqNum());

                if (timeoutCtr >= 3) {
                    System.out.println("Unable to transfer file.");
                    socket.close();
                    System.exit(1);
                }

                // Retransmit entire window from base
                System.out.println("Retransmitting from base...");
                nextSeq = base;
            }
        }
    }

   
    // Main
  
    public static void main(String[] args) throws Exception {

        // Parse command-line arguments
        String rcvIP= args[0];
        int rcvDataPort= Integer.parseInt(args[1]);
        int senderAckPort= Integer.parseInt(args[2]);
        String inputFile= args[3];
        int timeoutMs= Integer.parseInt(args[4]);
        int windowSize= (args.length == 6) ? Integer.parseInt(args[5]) : 0;

        // Create sender object
        Sender sender;
        if (windowSize > 0) {
            sender = new Sender(rcvIP, rcvDataPort, senderAckPort, inputFile, timeoutMs, windowSize);
        } else {
            sender = new Sender(rcvIP, rcvDataPort, senderAckPort, inputFile, timeoutMs);
        }

        //Handshake
        DSPacket sot     = new DSPacket(DSPacket.TYPE_SOT, 0, null);
        byte[] sotBytes  = sot.toBytes();
        DatagramPacket sotPacket = new DatagramPacket(
            sotBytes,
            sotBytes.length,
            InetAddress.getByName(rcvIP),
            rcvDataPort
        );

        boolean handshakeDone = false;
        int handshakeTimeouts = 0;

        while (!handshakeDone) {
            sender.socket.send(sotPacket);
            System.out.println("Sent SOT");

            try {
                byte[] ackBuf = new byte[128];
                DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                sender.socket.receive(ackPacket);

                DSPacket ack = new DSPacket(ackBuf);
                if (ack.getType() == DSPacket.TYPE_ACK && ack.getSeqNum() == 0) {
                    System.out.println("Handshake complete");
                    handshakeDone = true;
                }

            } catch (SocketTimeoutException e) {
                handshakeTimeouts++;
                System.out.println("Handshake timeout #" + handshakeTimeouts);
                if (handshakeTimeouts >= 3) {
                    System.out.println("Unable to transfer file.");
                    sender.socket.close();
                    System.exit(1);
                }
            }
        }

        // Tokenize file
        List<DSPacket> packets = sender.createPackets();

       
        // Start timer
        long startTime = System.currentTimeMillis();

        // Data transfer
        if (windowSize > 0) {
            System.out.println("Starting Go-Back-N, window=" + windowSize);
            sender.goBackN(packets);
        } else {
            System.out.println("Starting Stop-and-Wait");
            sender.stopAndWait(packets);
        }

        // Teardown — send EOT, wait for ACK
        int lastSeq = packets.isEmpty() ? 1 : (packets.get(packets.size() - 1).getSeqNum() + 1) % 128;
        DSPacket eot    = new DSPacket(DSPacket.TYPE_EOT, lastSeq, null);
        byte[] eotBytes = eot.toBytes();
        DatagramPacket eotPacket = new DatagramPacket(
            eotBytes,
            eotBytes.length,
            InetAddress.getByName(rcvIP),
            rcvDataPort
        );

        boolean eotAcked  = false;
        int eotTimeouts   = 0;

        while (!eotAcked) {
            sender.socket.send(eotPacket);
            System.out.println("Sent EOT seq=" + lastSeq);

            try {
                byte[] ackBuf = new byte[128];
                DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
                sender.socket.receive(ackPacket);

                DSPacket ack = new DSPacket(ackBuf);
                if (ack.getType() == DSPacket.TYPE_ACK && ack.getSeqNum() == lastSeq) {
                    System.out.println("EOT ACK received");
                    eotAcked = true;
                }

            } catch (SocketTimeoutException e) {
                eotTimeouts++;
                System.out.println("EOT timeout #" + eotTimeouts);
                if (eotTimeouts >= 3) {
                    System.out.println("Unable to transfer file.");
                    sender.socket.close();
                    System.exit(1);
                }
            }
        }

        // Print total transmission time
        long endTime    = System.currentTimeMillis();
        double totalTime = (endTime - startTime) / 1000.0;
        System.out.printf("Total Transmission Time: %.2f seconds%n", totalTime);

        sender.socket.close();
    }
}