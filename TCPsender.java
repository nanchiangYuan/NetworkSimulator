import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

/**
 * TCP node that sends data over the network.
 */
public class TCPsender {

    private short sourceID;             // sender ID
    private short destinationID;        // receiver ID
    private String filename;            // input file name
    private int mtu;                    // maximum transmission unit in bytes
    private int mss;                    // in bytes
    private double cwnd;                // congestion window in packets
    private int sequenceNo;             // the sequence number sender puts on its packets
    private int expRcvNo;               // the expected sequence to get from receiver
    private Scheduler scheduler;

    private HashMap<Integer, TCPmessage> buffer;    // the buffer where the sender puts its data to be sent
    private int lastAck;                // the index of expected seq from receiver
    private int lastSent;               // the index of the last sent seq
    private int dupAcks;                // number of duplicate acks
    private int lastLength;             // length of the previous packet

    private State state;                // current state the sender is in
    private RenoState reno;             // state for congestion control
    private double ssthresh;            // slow start threshold

    private Node node = null;           // the node this sender is on

    private FileInputStream fileIn;     // stream to read the file from

    private boolean fileDone = false;   // keep track of whether the file is done buffering
    
    private double timeout;             // current timeout time
    private double ertt;                // estimated rtt
    private double srtt;                // smoothed round trip time
    private double edev = 0.0;          // estimated deviation
    private double timeoutCoA = 0.125;  // coefficient a
    private double timeoutCoB = 0.25;   // coefficient b
    private double rttvar;              // 
    private double GRANULARITY = 1.0;   //
    private double MIN_RTO = 1000.0;      // minimum retransmission timeout

    private double segmentLifetime = 60000.0;   // in ms, 60 sec
    
    private boolean verbose;            // whether to print every packet sent or received 
    private TCPStat stat;               // holds all the stats

    private int bufferSize;             // size of the data buffer

    // state for TCP reno
    public static enum RenoState {
        SLOW_START,
        CONGESTION_AVOIDANCE,
        FAST_RECOVERY
    }

    /**
     * Constructor
     * @param sID source ID
     * @param dID destination ID
     * @param node the node the sender is on
     * @param fn file name of the data to be sent over
     * @param m mtu
     * @param sched scheduler
     * @param v verbose
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
        this.buffer = new HashMap<>();
        this.lastAck = 0;
        this.lastSent = 0;
        this.lastLength = 0;
        this.expRcvNo = 0;
        this.dupAcks = 0;
        this.scheduler = sched;
        this.state = State.CLOSED;
        this.reno = RenoState.SLOW_START;
        this.ssthresh = 64; // random large number that is 2^n
        this.bufferSize = 65535 / mss;

        this.timeout = 1000.0; // 1 second
        this.verbose = v;
        this.stat = new TCPStat("sender");
    }

    public TCPStat getStat() {
        return stat;
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
        buffer.put(sequenceNo, init);
        scheduler.setTimer(sequenceNo, timeout);

        state = State.SYN_SENT;
        sequenceNo += 1;
    }

    public void initWaitForAck(TCPmessage message) {

        if(!message.isSYN() || !message.isACK() || message.getAcknowledgment() != sequenceNo) {
            System.out.println("sender: init received wrong info");
            return;
        }

        buffer.remove(sequenceNo-1);

        // calculate first value for timeout
        srtt = scheduler.getCurrentTime() - message.getTimestamp();
        rttvar = srtt / 2.0;
        timeout = srtt + Math.max(GRANULARITY, 4 * rttvar);
        
        int inSeqNO = message.getSequenceNo();
        
        expRcvNo = inSeqNO + 1;
        
        // third packet 
        TCPmessage init2 = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        init2.setFlag('A');
        buffer.put(sequenceNo, init2);
        sendPacket(init2);

        scheduler.setTimer(sequenceNo, timeout);

        state = State.ESTABLISHED;
        lastSent = init2.getSequenceNo();

        // prepare the fiel to be sent
        try{
            File file = new File(filename);
            fileIn = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } 

        // start sending data: sends until cwnd is reached, then only send when space is free
        sendData();
    }

    /**
     * Runs receive logic depending on which state the node is currently in. 
     * @param packet the packet received by node
     * @return
     */
    public void receive(SimplePacket packet) {

        if(!checksumCheck(packet.getPayload())) {
            stat.addInvalidChecksum(1);
            return;
        }
            

        TCPmessage message = new TCPmessage(0, 0, 0, 0);
        message = message.deserialize(packet.getPayload());

        stat.addReceivedData(1, message.getLength());
        stat.printPackets(message, "rcv", scheduler.getCurrentTime(), verbose);
        
        switch(state) {
            case State.SYN_SENT:
                initWaitForAck(message);
                lastAck = message.getSequenceNo();
                state = State.ESTABLISHED;
                break;
            case State.ESTABLISHED:
                processPacket(message);
                break;
            case State.FIN_WAIT_1:
                // if received fin from receiver, enter termination function
                if(message.isFIN()) 
                    terminateConnectionResponse();
                break;
            default:
                break;
        }

    }

    public void processPacket(TCPmessage message) {
        // just ack messages
        if(message.isACK()) {
            recalculateTimeout(message.getTimestamp());
            int recvdAckNo = message.getAcknowledgment();
            int recvdSeq = message.getSequenceNo();
            expRcvNo = recvdSeq + 1;

            // check if the receiver expected the correct ack, which is ones after lastAck
            if(recvdAckNo > lastAck) {
                buffer.entrySet().removeIf(entry -> entry.getKey() < recvdAckNo);
                reno = RenoState.CONGESTION_AVOIDANCE;
                lastAck = recvdAckNo;
                dupAcks = 0;
                calculateCongestionWindow();
                sendData();
                scheduler.setTimer(recvdAckNo, timeout);

                // this means the last ack has arrived, file transfer completed
                if(buffer.size() == 0) {
                    state = State.FIN_WAIT_1;
                    terminateConnection();
                }
            }
            // check if the receiver expected the same packet, which indicates this is a duplicate ack
            else if(recvdAckNo == lastAck) {
                dupAcks++;
                stat.addDupAck(1);

                // fast retransmission
                if(dupAcks == 2) {
                    sendPacket(buffer.get(recvdAckNo));

                    // cwnd drops
                    ssthresh = cwnd / 2.0;
                    cwnd /= 2.0;
                    stat.addCwnd(cwnd);

                    reno = RenoState.FAST_RECOVERY;
                    int count = sendData();
                    stat.addRetransmissionCount(count);
                    stat.addFastRetransmit(1);
                }
                if(dupAcks >= 3) {  
                    calculateCongestionWindow();
                    int count = sendData();
                    stat.addRetransmissionCount(count);
                }
            } else {
                // ignore
            }
            // 3. if not, mark in ackList, if ack is > 3, fast retransmit of previous (need to somehow restart timeout)
            //    ssthresh = cwnd, cwnd = ssthresh / 2, if next ack is new, congestion avoidance, if not(more dup ack), fast recovery

            
        }
    }
    


    /**
     * recalculate congestion window upon receiving an ack
     */
    private void calculateCongestionWindow() {
        // System.out.println("reno state: " + reno);
        switch(reno) {
            case RenoState.SLOW_START:
                cwnd++;
                if(cwnd == ssthresh)
                    reno = RenoState.CONGESTION_AVOIDANCE;
                break;
            case RenoState.CONGESTION_AVOIDANCE:
                cwnd = cwnd + 1/cwnd;
                break;
            case RenoState.FAST_RECOVERY:
                cwnd++;
                break;
        }
        stat.addCwnd(cwnd);

    }

    /** 
     * The data in the file is all put into the buffer first 
    */
    private void dataIntoBuffer() {

        if(fileDone)
            return;

        try{
            byte[] segment = new byte[mss];

            // put all segments into buffer
            while(buffer.size() < bufferSize) {
                int segLength = fileIn.read(segment);
                if(segLength < 0) {
                    fileDone = true;
                    break;
                }

                // build the TCP segment to be put in buffer
                byte[] payload = Arrays.copyOf(segment, segLength);
                TCPmessage TCPsegment = new TCPmessage(sequenceNo, 0, segLength, scheduler.getCurrentTime());
                TCPsegment.setPayload(payload);
                TCPsegment.setFlag('A');

                // add to buffer
                buffer.put(sequenceNo, TCPsegment);

                sequenceNo += segLength;

            }
        } catch (IOException e) {
            System.out.println("Error when reading file");
        }

        // System.out.println("######### buffer #########");
        // for(Map.Entry<Integer, TCPmessage> entry: buffer.entrySet()) {
        //     System.out.println(entry.getKey() + ", " + entry.getValue());
        // }
        // System.out.println("##########################");

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
        dataIntoBuffer();
        calculateCongestionWindow();
        // System.out.println("cwnd: " + cwnd);
        int count = 0;
        while(!buffer.isEmpty() && lastSent - lastAck <= (cwnd * mss)) {
            int lastSentCheck = lastSent + lastLength;
            if(lastSentCheck >= sequenceNo)
                break;
            // System.out.println("lastLength: " + lastLength);
            // System.out.println("lastSent: " + lastSent);
            lastSent+=lastLength;
            TCPmessage toBeSent = buffer.get(lastSent);
            lastLength = toBeSent.getLength();
            sendPacket(toBeSent);
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

    }

    /**
     * recalculate timeout for every ack
     * @param dataTime
     */
    public void recalculateTimeout(double dataTime) {
        double current = scheduler.getCurrentTime();
        double tempR = current - dataTime;

        rttvar = (1 - timeoutCoB) * rttvar + timeoutCoB * Math.abs(srtt - tempR);
        srtt = (1 - timeoutCoA) * srtt + timeoutCoA * tempR;

        timeout = srtt + Math.max(GRANULARITY, 4 * rttvar);

        if(timeout < MIN_RTO)
            timeout = MIN_RTO;

        stat.addRTT(tempR);
    }

    /** 
     * terminate connection three way hand shake
    */
    public void terminateConnection() {
        TCPmessage finMessage = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        finMessage.setFlag('F');
        buffer.put(sequenceNo, finMessage);
        sendPacket(finMessage);
        scheduler.setTimer(sequenceNo, timeout);
        sequenceNo++;
    }   
    /** 
     * response for terminate connection, send back an ack
    */
    public void terminateConnectionResponse() {

        TCPmessage finMessage2 = new TCPmessage(sequenceNo, expRcvNo, 0, scheduler.getCurrentTime());
        finMessage2.setFlag('A');
        buffer.remove(sequenceNo - 1);
        buffer.put(sequenceNo, finMessage2);
        sendPacket(finMessage2);
    
        state = State.TIME_WAIT;
        Event waitandclose = new Event(Event.EventType.TIME_WAIT, scheduler.getCurrentTime() + segmentLifetime);
        scheduler.schedule(waitandclose);
    }
    
    /**
     * make sure no segment in the buffer stay beyond timeout
     */
    public void checkTimeout(int oldestSeqNo) {

        TCPmessage message = buffer.get(oldestSeqNo);
        if(message == null || oldestSeqNo < lastAck)
            return;
        sendPacket(message);
        scheduler.setTimer(oldestSeqNo, timeout * 2);
        ssthresh = cwnd / 2.0;
        cwnd = 1.0;
        stat.addTimeout(1);
        stat.addCwnd(cwnd);
        stat.addRetransmissionCount(1);
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
