import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.spec.IvParameterSpec;

/**
 * Class for receiver
 */
public class TCPrecver{
    private String filename;
    private int mtu;
    private int sws;
    private ConcurrentHashMap<Integer, TCPpacketR> buffer;
    private int sequenceNo;

    private short senderID;

    private int expectedSeq; // the next expected byte

    // final stats
    private int invalidChecksumCount = 0;
    private int droppedPacketCount = 0;
    private int receivedPacketCount = 0;
    private int receivedDataSize = 0;
    private int sentPacketCount = 0;
    private int sentDataSize = 0;

    boolean connected = false;

    private static final long START_TIME = System.nanoTime();

    /**
     * constructor
     * @param port
     * @param filename
     * @param mtu
     * @param sws
     */
    TCPrecver(short sourceID, String filename, int mtu, int sws) {

        this.senderID = sourceID;
        this.filename = filename;
        this.mtu = mtu;
        this.sws = sws;
        
        this.buffer = new ConcurrentHashMap<>();
        this.sequenceNo = 0;
        this.expectedSeq = 0;
    }

    /**
     * runs the main receiving while loop and write to file
     */
    public void run() {

        byte[] received = new byte[this.mtu + TCPmessage.HEADER_LENGTH];
        SimplePacket receivedPacket = null;
        FileOutputStream output = null;

        try{
            output = new FileOutputStream(this.filename, false);
        }
        catch (FileNotFoundException e) {
            System.out.println("error opening fileoutputstream");
            return;
        }

        // store received packets in buffer
        // if an out of order packet arrives, store in buffer, send ack of previous
        // take note of number of dup acks, if finally got the one in order, send an ack that includes all the one after
        while(true) {

            // receive a packet
            receivedPacket = new SimplePacket(this.senderID, (short) -1, received);
            receivePacket(receivedPacket);

            TCPmessage receivedSegment = new TCPmessage(0, 0, 0);
            receivedSegment = receivedSegment.deserialize(receivedPacket.getPayload());

            receivedPacketCount++;
            receivedDataSize += receivedSegment.getLength();
            
            printStat(receivedSegment, "rcv");

            // check if initializing
            if(receivedSegment.isSYN()){
                this.senderIP = receivedPacket.getAddress();
                this.senderPort = receivedPacket.getPort();
                initConnectionResponse(receivedSegment);
            }
            // check if terminating
            else if(receivedSegment.isFIN()) {
                terminateConnectionAck(receivedSegment.getSequenceNo());
                // wait for last ack
                waitForAck();
                try {
                    output.close();
                    break;
                }
                catch (IOException e) {
                    System.out.println("file not properly closed");
                }
            }
            // as long as connection is good, process other packets here
            else if (connected) {
                TCPpacketR newPacket = new TCPpacketR();
                newPacket.message = receivedSegment;
                newPacket.packet = receivedPacket;
                // check if checksum is valid
                boolean valid = checksumCheck(newPacket.message);
                if(!valid) {
                    System.out.println("checksum wrong");
                    invalidChecksumCount++;
                    continue;
                }
                int receivedSeqNo = receivedSegment.getSequenceNo();
                // if the received packet has a seq number smaller than what the receiver is expecting
                // drop it and send ack again
                if(receivedSeqNo < this.expectedSeq) {
                    sendAck("ack sent error", receivedSegment.getTimestamp());
                    printStat(receivedSegment, "snd");
                    droppedPacketCount ++;
                }
                // if received seq number is bigger, put in buffer
                // but if buffer is full, drop it
                else if(receivedSeqNo > this.expectedSeq) {
                    if(buffer.size() < this.sws && !buffer.containsKey(receivedSeqNo))
                        buffer.put(receivedSeqNo, newPacket);
                    else
                        droppedPacketCount ++;
                // if packet is exactly what the receiver wants, just write to file
                } else if (receivedSegment.getPayload() != null){
                    try{
                        output.write(receivedSegment.getPayload());
                        output.flush();
                    }
                    catch (IOException e) {
                        System.out.println("file write error");
                        try {
                            output.close();
                        }
                        catch (IOException e2) {
                            System.out.println("file not properly closed");
                        }
                        return;
                    }
                    
                    this.expectedSeq += receivedSegment.getLength();
                    // if buffer has segments immediately afterwards, write to file also
                    while(buffer.containsKey(this.expectedSeq)) {
                        TCPpacketR toBeWritten = buffer.remove(this.expectedSeq);
                        try {
                            output.write(toBeWritten.message.getPayload());
                            output.flush();
                        }
                        catch (IOException e) {
                            System.out.println("file write error");
                            try {
                                output.close();
                            }
                            catch (IOException e2) {
                                System.out.println("file not properly closed");
                            }
                            return;
                        }
                        
                        this.expectedSeq += toBeWritten.message.getLength();
                    }
                    // send ack for the segment that is written
                    sendAck("ack sent error", receivedSegment.getTimestamp());
                    printStat(receivedSegment, "snd");
                }
            }
            
        }
    }
    /**
     * initialization ack response
     * @param received
     */
    public void initConnectionResponse(TCPmessage received) {

        this.expectedSeq = received.getSequenceNo() + 1;

        TCPmessage initR = new TCPmessage(this.sequenceNo, this.expectedSeq, 0);
        initR.setFlag('S');
        initR.setFlag('A');
        initR.setTimestamp(received.getTimestamp());
        byte[] initBytes = initR.serialize();
        DatagramPacket initPacket = new DatagramPacket(initBytes, initBytes.length, this.senderIP, this.senderPort);
        try{
            this.socket.send(initPacket);
        }
        catch(IOException e) {
            System.out.println("receiver: init ack not sent");
            return;
        }
        sentPacketCount ++;
        sentDataSize += initR.getLength();

        this.sequenceNo += 1;
        // connection is up
        this.connected = true;

    }
    
    /**
     * simple function that send acks
     * @param printMessage error message
     * @param time timestamp from first message
     * @return
     */
    public boolean sendAck(String printMessage, long time) {

        TCPmessage ack = new TCPmessage(this.sequenceNo, this.expectedSeq, 0);
        ack.setFlag('A');
        ack.setTimestamp(time);
        byte[] data = ack.serialize();
        DatagramPacket ackPacket = new DatagramPacket(data, data.length, this.senderIP, this.senderPort);

        try {
            this.socket.send(ackPacket);
        }
        catch (IOException e) {
            System.out.println(printMessage);
            return false;
        }

        sentPacketCount++;
        sentDataSize += ack.getLength();
        return true;
    }
    
    /**
     * simple function for receiving packets
     * @param packet
     * @param printMessage error message
     * @return
     */
    public boolean receivePacket(DatagramPacket packet, String printMessage) {
        try {
            this.socket.receive(packet);  
        }
        catch (IOException e) {
            System.out.println(printMessage);
            return false;
        }
        return true;
    }

    public void printStat(TCPmessage packet, String sndRcv) {
        StringBuilder output = new StringBuilder();
        output.append(sndRcv);
        output.append(" ");
        output.append(String.format("%.3f", (packet.getTimestamp() - START_TIME) / 1_000_000.0));
        output.append(" ");
        if(packet.isSYN())
            output.append("S");
        else
            output.append("-");
        output.append(" ");
        if(packet.isACK())
            output.append("A");
        else
            output.append("-");
        output.append(" ");
        if(packet.isFIN())
            output.append("F");
        else
            output.append("-");
        output.append(" ");
        if(packet.hasData())
            output.append("D");
        else
            output.append("-");

        output.append(" ");
        output.append(packet.getSequenceNo());
        output.append(" ");
        output.append(packet.getLength());
        output.append(" ");
        output.append(packet.getAcknowledgment());

        System.out.println(output.toString());
    }

    /**
     * send ack response to sender fin segment
     * @param senderAckNo
     */
    public void terminateConnectionAck(int senderAckNo) {

        TCPmessage finAck = new TCPmessage(this.sequenceNo, senderAckNo + 1, 0);
        finAck.setFlag('A');
        finAck.setFlag('F');
        byte[] finData = finAck.serialize();
        DatagramPacket finPacket = new DatagramPacket(finData, finData.length, this.senderIP, this.senderPort);
        try {
            this.socket.send(finPacket);
        }
        catch (IOException e) {
            System.out.println("fin packet send error");
        }

        printStat(finAck, "snd");
        sentPacketCount++;
        sentDataSize += finAck.getLength();
        this.connected = false;
    }

    /**
     * wait for last fin ack from sender for a couple of seconds
     */
    public void waitForAck() {
        byte[] lastAck = new byte[this.mtu + TCPmessage.HEADER_LENGTH];
        DatagramPacket lastAckPacket = new DatagramPacket(lastAck, lastAck.length);
        try {
            this.socket.setSoTimeout(5000);
        }
        catch(SocketException e) {
            System.out.println("timeout error");
        }
        if(receivePacket(lastAckPacket, "last ack receive error"))
            receivedPacketCount ++;

    }

    /**
     * verify checksum
     * @param segment
     * @return
     */
    public boolean checksumCheck(TCPmessage segment) {

        int oldChecksum = segment.getChecksum();
        byte[] stream = segment.serialize();

        ByteBuffer bb = ByteBuffer.wrap(stream);

        int value = 0;
        bb.putShort(22, (short)0);
        bb.rewind();
        while(bb.remaining() >= 2) {
            int current = Short.toUnsignedInt(bb.getShort());
            value += current;
            value = (value & 0xFFFF) + (value >>> 16);
        }
        if(bb.remaining() == 1) {
            int last = (bb.get() & 0xFF) << 8;
            value += last;
            value = (value & 0xFFFF) + (value >>> 16);
        }
        value = (value & 0xFFFF) + (value >>> 16);

        int newChecksum = (short) (~value & 0xFFFF);

        return newChecksum == oldChecksum;

    }

    public int[] returnStats() {
        int[] result = {receivedDataSize, sentDataSize, 
                        receivedPacketCount, sentPacketCount, 
                        invalidChecksumCount, droppedPacketCount};
        return result;
    }
}

/**
 * class for putting inside the buffer, more convenient
 */
class TCPpacketR {
    SimplePacket packet;
    TCPmessage message;
}