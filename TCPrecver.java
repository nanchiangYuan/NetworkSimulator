import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;


/**
 * Class for receiver
 */
public class TCPrecver{
    private String filename;            // output file
    private int mtu;
    private int sws;                    // buffer size
    private HashMap<Integer, TCPmessage> buffer;
    private int sequenceNo;

    private short sourceID;             // receiver ID
    private short destinationID;        // sender ID
    private Node node;                  // receiver node

    private int expectedSeq;            // the next expected sequence from sender

    private FileOutputStream output;
    private boolean verbose;
    private Scheduler scheduler;

    private State state;

    // final stats
    private int invalidChecksumCount = 0;
    private int droppedPacketCount = 0;
    private int receivedPacketCount = 0;
    private int receivedDataSize = 0;
    private int sentPacketCount = 0;
    private int sentDataSize = 0;

    /**
     * constructor
     * @param port
     * @param filename
     * @param mtu
     * @param sws
     */
    TCPrecver(short sourceID, short destID, Node node, String filename, int mtu, int sws, Scheduler sched, boolean v) {

        this.sourceID = sourceID;
        this.destinationID = destID;
        this.filename = filename;
        this.node = node;
        this.mtu = mtu;
        this.sws = sws;
        this.scheduler = sched;
        this.state = State.CLOSED;
        
        this.buffer = new HashMap<>();
        this.sequenceNo = 0;
        this.expectedSeq = 0;
        this.verbose = v;
    }

    public void listen() {
        state = State.LISTEN;

        try{
            output = new FileOutputStream(filename, false);
        }
        catch (FileNotFoundException e) {
            System.out.println("error opening fileoutputstream");
            return;
        }
    }

    public void receive(SimplePacket packet) {

        // retrieve payload
        TCPmessage message = new TCPmessage(0, 0, 0, 0);
        message = message.deserialize(packet.getPayload());
        receivedPacketCount++;
        receivedDataSize += message.getLength();

        printStat(message, "rcv");

        switch(state) {

            case State.LISTEN:
                if(message.isSYN()) {
                    initConnectionResponse(message);
                    state = State.SYN_RCVD;
                }
                break;

            case State.SYN_RCVD:
                if(message.isACK() && message.getSequenceNo() == expectedSeq)
                    state = State.ESTABLISHED;
                break;
            
            case State.ESTABLISHED:
                receiveData(message);
                break;

            case State.CLOSED:
                break;
            
            case State.LAST_ACK:
                waitForAck(message);
                break;
            default:
                break;

        }

    }

    private void receiveData(TCPmessage message) {
        boolean valid = checksumCheck(message);
        if(!valid) {
            System.out.println("checksum wrong");
            invalidChecksumCount++;
            return;
        }

        int receivedSeqNo = message.getSequenceNo();
        // if the received packet has a seq number smaller than what the receiver is expecting
        // drop it and send ack again
        if(receivedSeqNo < expectedSeq) {
            TCPmessage ack = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
            sendAck(ack);
            droppedPacketCount ++;
        }

        // if received seq number is bigger, put in buffer
        // but if buffer is full, drop it
        else if(receivedSeqNo > expectedSeq) {
            if(buffer.size() < sws && !buffer.containsKey(receivedSeqNo))
                buffer.put(receivedSeqNo, message);
            else
                droppedPacketCount ++;
        // if packet is exactly what the receiver wants, just write to file
        } else if (message.getPayload() != null){

            try{
                output.write(message.getPayload());
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
            
            expectedSeq += message.getLength();
            // if buffer has segments immediately afterwards, write to file also
            while(buffer.containsKey(expectedSeq)) {
                TCPmessage toBeWritten = buffer.remove(expectedSeq);
                if(toBeWritten.isFIN()) {
                    terminateConnectionAck(message.getSequenceNo());
                    return;
                }
                    
                try {
                    output.write(toBeWritten.getPayload());
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
                
                expectedSeq += toBeWritten.getLength();
            }
            // send ack for the segment that is written
            TCPmessage ack = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
            sendAck(ack);
        }
    }

    private void sendPacket(TCPmessage message) {

        message.setAcknowledgment(expectedSeq); // expected seqNo from receiver will be different, build packet only when sending
        message.setTimestamp(scheduler.getCurrentTime());
        byte[] stream = message.serialize();
        SimplePacket TCPpacket = new SimplePacket(sourceID, destinationID, stream);

        node.send(TCPpacket);
        sentDataSize += message.getLength();
        sentPacketCount++;  
        sequenceNo += Math.max(message.getLength(), 1);

        printStat(message, "snd");
    }

    /**
     * initialization ack response
     * @param received
     */
    public void initConnectionResponse(TCPmessage received) {

        expectedSeq = received.getSequenceNo() + 1;

        TCPmessage initR = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
        initR.setFlag('S');
        initR.setFlag('A');

        sendPacket(initR);

        sequenceNo += 1;
    }
    
    /**
     * simple function that send acks
     * @param printMessage error message
     * @param time timestamp from first message
     * @return
     */
    public void sendAck(TCPmessage ack) {

        ack.setFlag('A');
        sendPacket(ack);

        sentPacketCount++;
        sentDataSize += ack.getLength();

    }

    public void printStat(TCPmessage packet, String sndRcv) {
        if(!verbose)
            return; 

        StringBuilder output = new StringBuilder();
        output.append(sndRcv);
        output.append(" ");
        output.append(scheduler.getCurrentTime());
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

        expectedSeq = senderAckNo + 1;
        TCPmessage finAck = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
        finAck.setFlag('A');
        
        sendPacket(finAck);
        state = State.CLOSE_WAIT;
        sequenceNo += Math.max(finAck.getLength(), 1);

        TCPmessage rcvFin = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
        rcvFin.setFlag('F');
        sendPacket(rcvFin);
        state = State.LAST_ACK;
        sequenceNo += Math.max(finAck.getLength(), 1);
    }

    /**
     * wait for last fin ack from sender for a couple of seconds
     */
    public void waitForAck(TCPmessage message) {

        if(message.isACK()) {
            receivedPacketCount ++;
            state = State.CLOSED;
            try {
                output.close();
            }
            catch (IOException e) {
                System.out.println("file not properly closed");
            }
        }
            

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

