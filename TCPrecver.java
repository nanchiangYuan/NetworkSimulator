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
    private int bufferSize;                    // buffer size
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

    private TCPStat stat;

    /**
     * constructor
     * @param port
     * @param filename
     * @param mtu
     * @param bufferSize
     */
    TCPrecver(short sourceID, short destID, Node node, String filename, int mtu, int bufferSize, Scheduler sched, boolean v) {

        this.sourceID = sourceID;
        this.destinationID = destID;
        this.filename = filename;
        this.node = node;
        this.mtu = mtu;
        this.bufferSize = bufferSize;
        this.scheduler = sched;
        this.state = State.CLOSED;
        
        this.buffer = new HashMap<>();
        this.sequenceNo = 0;
        this.expectedSeq = 0;
        this.verbose = v;

        this.stat = new TCPStat("receiver");
    }

    public TCPStat getStat() {
        return stat;
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

        if(!checksumCheck(packet.getPayload())) {
            stat.addInvalidChecksum(1);
            return;
        }

        // retrieve payload
        TCPmessage message = new TCPmessage(0, 0, 0, 0);
        message = message.deserialize(packet.getPayload());

        stat.addReceivedData(1, message.getLength());
        stat.printPackets(message, "rcv", scheduler.getCurrentTime(), verbose);

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
                else if(message.isSYN()) {
                    sequenceNo -= 1;
                    initConnectionResponse(message);
                }
                    
                break;
            
            case State.ESTABLISHED:
                receiveData(message);
                break;

            case State.CLOSED:
                break;
            
            case State.LAST_ACK:
                if(message.isFIN()) {
                    sequenceNo -= 2;
                    terminateConnectionAck(message);
                    break;
                }
                waitForAck(message);
                break;
            default:
                break;

        }

    }

    private void receiveData(TCPmessage message) {

        int receivedSeqNo = message.getSequenceNo();
        // if the received packet has a seq number smaller than what the receiver is expecting
        // drop it and send ack again
        if(receivedSeqNo < expectedSeq) {
            TCPmessage ack = new TCPmessage(sequenceNo, expectedSeq, 0, message.getTimestamp());
            sendAck(ack);
            stat.addDroppedPacket(1);
        }

        // if received seq number is bigger, put in buffer
        // but if buffer is full, drop it
        else if(receivedSeqNo > expectedSeq) {
            if(buffer.size() < bufferSize && !buffer.containsKey(receivedSeqNo))
                buffer.put(receivedSeqNo, message);
            else
                stat.addDroppedPacket(1);
            TCPmessage ack = new TCPmessage(sequenceNo, expectedSeq, 0, message.getTimestamp());
            sendAck(ack);
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
            
            expectedSeq = Math.max(expectedSeq + message.getLength(), expectedSeq + 1);
            // if buffer has segments immediately afterwards, write to file also
            while(buffer.containsKey(expectedSeq)) {
                TCPmessage toBeWritten = buffer.remove(expectedSeq);
                if(toBeWritten.isFIN()) {
                    terminateConnectionAck(toBeWritten);
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
                
                expectedSeq = Math.max(expectedSeq + toBeWritten.getLength(), expectedSeq + 1);
            }
            // send ack for the segment that is written
            TCPmessage ack = new TCPmessage(sequenceNo, expectedSeq, 0, message.getTimestamp());
            sendAck(ack);
        } else if(message.isFIN()) {
            terminateConnectionAck(message);
        }
    }

    private void sendPacket(TCPmessage message) {

        message.setAcknowledgment(expectedSeq); // expected seqNo from receiver will be different, build packet only when sending
        byte[] stream = message.serialize();
        SimplePacket TCPpacket = new SimplePacket(sourceID, destinationID, stream);

        node.send(TCPpacket);
        sequenceNo += Math.max(message.getLength(), 1);

        stat.addSentData(1, message.getLength());
        stat.printPackets(message, "snd", scheduler.getCurrentTime(), verbose);
    }

    /**
     * initialization ack response
     * @param received
     */
    public void initConnectionResponse(TCPmessage received) {

        expectedSeq = received.getSequenceNo() + 1;

        TCPmessage initR = new TCPmessage(sequenceNo, expectedSeq, 0, received.getTimestamp());;
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
    }

    /**
     * send ack response to sender fin segment
     * @param senderAckNo
     */
    public void terminateConnectionAck(TCPmessage message) {

        expectedSeq = message.getSequenceNo() + 1;
        TCPmessage finAck = new TCPmessage(sequenceNo, expectedSeq, 0, message.getTimestamp());
        finAck.setFlag('A');
        
        sendPacket(finAck);
        state = State.CLOSE_WAIT;
        sequenceNo += 1;

        TCPmessage rcvFin = new TCPmessage(sequenceNo, expectedSeq, 0, scheduler.getCurrentTime());
        rcvFin.setFlag('F');
        sendPacket(rcvFin);
        state = State.LAST_ACK;
        sequenceNo += 1;
    }

    /**
     * wait for last fin ack from sender for a couple of seconds
     */
    public void waitForAck(TCPmessage message) {

        if(message.isACK()) {
            state = State.CLOSED;
            stat.setFinalTime(scheduler.getCurrentTime());
            try {
                output.close();
            }
            catch (IOException e) {
                System.out.println("file not properly closed");
            }
        }
    }

    public boolean checksumCheck(byte[] payload) {

        ByteBuffer forChecksum = ByteBuffer.wrap(payload);
        short oldChecksum = forChecksum.getShort(22);

        ByteBuffer bb = ByteBuffer.wrap(payload);

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

}

