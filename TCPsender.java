import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * TCP sender class
 */
public class TCPsender {

    private short sourceID;             // sender ID
    private short destinationID;        // receiver ID
    private String filename;            // input file name
    private int mtu;                    // maximum transmission unit in bytes
    private int mss;                    // in bytes
    private int cwnd;                   // congestion window in packets
    private int sequenceNo;             // the sequence number sender puts on its packets
    private int expRcvNo;               // the expected sequence to get from receiver
    private Scheduler scheduler;

    private ArrayList<TCPmessage> buffer;
    private int lastAck;    // the index of expected seq from receiver (need to multiply by mss to get sequence number)
    private int lastSent;   // the index of the last sent seq (need to multiply by mss to get sequence number)
    private int dupAcks;

    private State state;
    private RenoState reno;
    private int ssthresh;

    private Node node = null;
    
    // for timeout
    private double timeout;
    private double ertt;        // estimated rtt
    private double edev = 0.0;      // estimated deviation
    private double timeoutCoA = 0.875;  // coefficient a
    private double timeoutCoB = 0.75;   // coefficient b

    // for final stats
    private int sentDataSize = 0;
    private int sentPacketCount = 0;
    private int receivedDataSize = 0;
    private int receivedPacketCount = 0;
    private int retransmissionCount = 0;
    private int dupAckCount = 0;

    private double segmentLifetime = 60000.0; // in ms, 60 sec
    
    private boolean verbose;
    private TCPStat stat;

    public static enum RenoState {
        SLOW_START,
        CONGESTION_AVOIDANCE,
        FAST_RECOVERY
    }

    /**
     * Constructor 
     * @param sID source ID
     * @param dID destination ID
     * @param fn input file name
     * @param m mtu in bytes
     * @param s sliding window size in segments
     */
    TCPsender(short sID, short dID, Node node, String fn, int m, Scheduler sched, boolean v) {

        this.sourceID = sID;
        this.destinationID = dID;
        this.node = node;
        this.filename = fn;
        this.mtu = m;
        this.mss = this.mtu - TCPmessage.HEADER_LENGTH - SimplePacket.HEADER_LENGTH;
        this.cwnd = 0;
        this.sequenceNo = 0;
        this.buffer = new ArrayList<>();
        this.lastAck = -1;
        this.lastSent = -1;
        this.expRcvNo = 0;
        this.dupAcks = 0;
        this.scheduler = sched;
        this.state = State.CLOSED;
        this.reno = RenoState.SLOW_START;
        this.ssthresh = 64; // random large number that is 2^n

        this.timeout = 5000.0; // 5 seconds
        this.verbose = v;
        this.stat = new TCPStat("sender");
    }

    /**
     * Sends the first packet to initialize threeway handshake
     * @return succeed or not
     */
    public void initConnection() {

        if(state != State.CLOSED) {
            System.out.println("initializing connection state error");
            return;
        }

        // build segment for sending
        TCPmessage init = new TCPmessage(sequenceNo, 0, 0, scheduler.getCurrentTime());
        init.setFlag('S');

        // send segment
        sendPacket(init);

        state = State.SYN_SENT;
        sequenceNo += 1;
    }

    public void initWaitForAck(TCPmessage message) {

        if(!message.isSYN() || !message.isACK() || message.getAcknowledgment() != sequenceNo + 1) {
            System.out.println("sender: init received wrong info");
            return;
        }

        stat.addReceivedData(1, message.getLength());
        stat.printPackets(message, "rcv", scheduler.getCurrentTime(), verbose);

        // calculate first value for timeout
        ertt = scheduler.getCurrentTime() - message.getTimestamp();
        timeout = ertt * 2.0;

        int inSeqNO = message.getSequenceNo();
        
        expRcvNo = inSeqNO + 1;
        
        // third packet 
        TCPmessage init2 = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        init2.setFlag('A');

        sendPacket(init2);
        
        node.send(initPacket2);

        printStat(init2, "snd");

        sentDataSize += init2.getLength();
        sentPacketCount ++;

        dataIntoBuffer();

        // start sending data: sends until cwnd is reached, then only send when space is free
        sendData();
    }

    public void processPacket(TCPmessage message) {
        // just ack messages
        if(message.isACK()) {
            recalculateTimeout(message.getTimestamp());
            int recvdAckNo = message.getAcknowledgment();
            int recvdSeq = message.getSequenceNo();

            // 1. check if ack is before or after lastAck
            // 2. if after, move lastAck forward,
            // call cwnd and send data

            // check if the receiver expected the correct ack, which is ones after lastAck
            if(recvdAckNo/mss >= lastAck + 1) {
                reno = RenoState.CONGESTION_AVOIDANCE;
                lastAck = recvdAckNo/mss;
                dupAcks = 0;
                calculateCongestionWindow();
                sendData();

                // this means the last ack has arrived, file transfer completed
                if(lastSent == buffer.size() - 1 && lastAck == buffer.size() - 1) {
                    state = State.FIN_WAIT_1;
                    terminateConnection();
                }
            }
            // check if the receiver expected the same packet, which indicates this is a duplicate ack
            else if(recvdAckNo/mss == lastAck) {
                dupAcks++;
                dupAckCount ++;

                // fast retransmission
                if(dupAcks == 3) {
                    sendPacket(buffer.get(recvdAckNo/mss));

                    // cwnd drops
                    ssthresh = cwnd;
                    cwnd /= 2;

                    reno = RenoState.FAST_RECOVERY;
                    int count = sendData();
                    retransmissionCount += count;
                }
                if(dupAcks >= 4) {  
                    calculateCongestionWindow();
                    int count = sendData();
                    retransmissionCount += count;
                }
            } else {
                // ignore
            }
            // 3. if not, mark in ackList, if ack is > 3, fast retransmit of previous (need to somehow restart timeout)
            //    ssthresh = cwnd, cwnd = ssthresh / 2, if next ack is new, congestion avoidance, if not(more dup ack), fast recovery

            expRcvNo = recvdSeq + 1;
        }
    }
    
    /**
     * Runs receive logic depending on which state the node is currently in. 
     * @param packet the packet received by node
     * @return
     */
    public void receive(SimplePacket packet) {

        TCPmessage message = new TCPmessage(0, 0, 0, 0);
        message = message.deserialize(packet.getPayload());

        receivedPacketCount ++;
        receivedDataSize += message.getLength();

        printStat(message, "rcv");

        switch(state) {
            case State.SYN_SENT:
                initWaitForAck(message);
                state = State.ESTABLISHED;
                break;
            case State.ESTABLISHED:
                processPacket(message);
                break;
            case State.FIN_WAIT_1:
                // if received ack of fin from receiver, enter termination function
                if(message.isACK() && message.isFIN()) 
                    terminateConnectionResponse();
                break;
            default:
                break;
        }

    }

    /**
     * recalculate congestion window upon receiving an ack
     */
    private void calculateCongestionWindow() {
        switch(reno) {
            case RenoState.SLOW_START:
                cwnd++;
                if(cwnd == ssthresh)
                    reno = RenoState.CONGESTION_AVOIDANCE;
                break;
            case RenoState.CONGESTION_AVOIDANCE:
                cwnd = (int) (cwnd + 1/cwnd);
                break;
            case RenoState.FAST_RECOVERY:
                cwnd++;
                break;
        }

    }

    /** 
     * The data in the file is all put into the buffer first 
    */
    private void dataIntoBuffer() {

        FileInputStream fileIn = null;
        try{
            File file = new File(filename);
            fileIn = new FileInputStream(file);
            byte[] segment = new byte[mss];
            int segLength = fileIn.read(segment);

            // put all segments into buffer
            while(segLength != -1) {

                // build the TCP segment to be put in buffer
                byte[] payload = Arrays.copyOf(segment, segLength);
                TCPmessage TCPsegment = new TCPmessage(sequenceNo * mss, expRcvNo, segLength, scheduler.getCurrentTime());
                TCPsegment.setPayload(payload);
                TCPsegment.setFlag('A');

                // add to buffer
                buffer.add(sequenceNo, TCPsegment);

                sequenceNo ++;
                segLength = fileIn.read(segment);

            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (IOException e) {
            System.out.println("Error when reading file");
        }
    }

    /**
     * 
     * @return the number of packets sent
     */
    private int sendData() {

        if(state != State.ESTABLISHED) {
            System.out.println("state error when sending data");
            return 0;
        }

        calculateCongestionWindow();
        int count = 0;
        while(lastSent - lastAck <= cwnd) {
            lastSent++;
            sendPacket(buffer.get(lastSent));    
            count++;
        }
        return count;

    }

    private void sendPacket(TCPmessage message) {

        message.setAcknowledgment(expRcvNo); // expected seqNo from receiver will be different, build packet only when sending
        message.setTimestamp(scheduler.getCurrentTime());
        byte[] stream = message.serialize();
        SimplePacket TCPpacket = new SimplePacket(sourceID, destinationID, stream);

        node.send(TCPpacket);
        stat.addSentData(1, message.getLength());
        stat.printPackets(message, "snd", scheduler.getCurrentTime(), verbose);

        Event timeoutE = new Event(TCPpacket, message.getSequenceNo(), message.getLength(), Event.EventType.TIMEOUT_CHECK, scheduler.getCurrentTime() + timeout);
        scheduler.schedule(timeoutE);

    }

    /**
     * printing host output
     * @param packet
     * @param sndRcv snd or rcv
     */
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
     * recalculate timeout for every ack
     * @param dataTime
     */
    public void recalculateTimeout(double dataTime) {
        double current = scheduler.getCurrentTime();
        double srtt = current - dataTime;
        double sdev = Math.abs(srtt - ertt);
        ertt = timeoutCoA * ertt + (1 - timeoutCoA) * srtt;
        edev = timeoutCoB * edev + (1 - timeoutCoB) * sdev;
        timeout = ertt + 4 * edev;
    }

    /** 
     * terminate connection three way hand shake
    */
    public void terminateConnection() {
        TCPmessage finMessage = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        finMessage.setFlag('F');
        sendPacket(finMessage);
        sequenceNo++;
        sentDataSize += finMessage.getLength();
        sentPacketCount ++;
    }   
    /** 
     * response for terminate connection, send back an ack
    */
    public void terminateConnectionResponse() {

        TCPmessage finMessage2 = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        finMessage2.setFlag('A');
        sendPacket(finMessage2);
        sentDataSize += finMessage2.getLength();
        sentPacketCount ++;
        state = State.TIME_WAIT;
        Event waitandclose = new Event(Event.EventType.TIME_WAIT, scheduler.getCurrentTime() + segmentLifetime);
        scheduler.schedule(waitandclose);
    }
    
    /**
     * make sure no segment in the buffer stay beyond timeout
     */
    public void checkTimeout(SimplePacket packet, int seqNo, int length) {

        if(seqNo / mss == lastAck + 1) {
            node.send(packet);
            sentDataSize += length;
            sentPacketCount++;

            Event timeoutE = new Event(packet, seqNo, length, Event.EventType.TIMEOUT_CHECK, scheduler.getCurrentTime() + timeout);
            scheduler.schedule(timeoutE);
        }
    }

    /**
     * return stats to TCPend
     * @return
     */
    public int[] returnStats() {
        int[] result = {receivedDataSize, sentDataSize,
                        receivedPacketCount, sentPacketCount,
                        retransmissionCount, dupAckCount};
        return result;
    }

}

/**
 * a class for added to the buffer, more convenient, so don't have tcp serialize and deserialize
 */
class TCPpacket {
    SimplePacket packet;
    TCPmessage message;
    int ackCount = 0; // counts if matches the acknowledgment in header, if >= 3 retransmit
}
