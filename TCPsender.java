import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOError;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * TCP sender class
 */
public class TCPsender {
    private int port;
    private InetAddress remoteIP;
    private int remotePort;
    private String filename;
    private int mtu; // bytes
    private int sws; // number of segments
    private int sequenceNo; // the sequence number sender puts on its packets
    private int ackNo; // the acknowledgement (sequence number) of the other host
    private ConcurrentHashMap<Integer, TCPpacket> buffer;

    private DatagramSocket socket = null;
    
    // for timeout
    private long timeout;
    private long ertt;
    private long edev = 0;

    // for final stats
    private int sentDataSize = 0;
    private int sentPacketCount = 0;
    private int receivedDataSize = 0;
    private int receivedPacketCount = 0;
    private int retransmissionCount = 0;
    private int dupAckCount = 0;

    private boolean connected;

    private static final long START_TIME = System.nanoTime();

    /**
     * Constructor 
     * @param po port
     * @param rIP remote IP
     * @param rPo remote port
     * @param fn input file name
     * @param m mtu in bytes
     * @param s sliding window size in segments
     */
    TCPsender(int po, String rIP, int rPo, String fn, int m, int s) {

        this.port = po;

        try{
            this.remoteIP = InetAddress.getByName(rIP);
        }
        catch(UnknownHostException e){
            System.out.println("sender: no such ip");
        }
        
        this.remotePort = rPo;
        this.filename = fn;
        this.mtu = m;
        this.sws = s;
        this.sequenceNo = 0;
        this.buffer = new ConcurrentHashMap<>();
        try{
            this.socket = new DatagramSocket(po);
        }
        catch(SocketException e) {
            System.out.println("sender: socket error");
        }
        this.timeout = 5_000_000_000L; // 5 seconds
        this.connected = false;
    }

    /**
     * Send packets
     */
    public void run() {

        // initialize connection with three way hand shake
        this.connected = initConnection();
        
        if(!this.connected) {
            System.out.println("sender: three way handshake failed");
            return;
        }

        // can start sending after connection
        // open a thread for receiving messages and checking timeouts
        Thread recvThread = new Thread(() -> receiveThread());
        recvThread.start();
        Thread timeoutThread = new Thread(() -> checkTimeout());
        timeoutThread.start();

        FileInputStream in = null;

        try{
            // read from file
            File file = new File(this.filename);
            in = new FileInputStream(file);
            byte[] segment = new byte[this.mtu - TCPmessage.HEADER_LENGTH];
            int segLength = in.read(segment);

            while(segLength != -1 || buffer.size() > 0) {

                // only enter this loop if there is space on buffer
                // otherwise wait in the outer loop for buffer to have an empty spot
                while(segLength != -1 && buffer.size() < this.sws) {

                    // build the TCP segment to be put in buffer
                    byte[] payload = Arrays.copyOf(segment, segLength);
                    TCPmessage TCPsegment = new TCPmessage(this.sequenceNo, 0, segLength);
                    TCPsegment.setPayload(payload);
                    TCPsegment.setFlag('A');
                    TCPsegment.setAcknowledgment(this.ackNo);
                    byte[] stream = TCPsegment.serialize();

                    DatagramPacket TCPpacket = new DatagramPacket(stream, stream.length, this.remoteIP, this.remotePort);
                    TCPpacket toBeSent = new TCPpacket(); // a class for storing in the buffer (convenience)
                    toBeSent.message = TCPsegment;
                    toBeSent.packet = TCPpacket;

                    // add to buffer and send it
                    buffer.put(this.sequenceNo, toBeSent);
                    sendPacket(TCPpacket, "packet send error");
                    printStat(toBeSent.message, "snd");
                    this.sequenceNo += segLength;
                    segLength = in.read(segment);
                    sentDataSize += TCPsegment.getLength();
                    sentPacketCount ++;
                }
                try {
                    Thread.sleep(1);
                }
                catch(InterruptedException e) {
                }
            }
            // outside of loop means we're done with the file, start terminating
            terminateConnection();
        }
        catch(FileNotFoundException e) {
            System.out.println("Sender: file not found");
            return;
        }        
        catch(IOException e) {
            System.out.println("Sender: file read error");
            return;
        }
        finally{
            try {
                recvThread.join(this.timeout / 1000);
                timeoutThread.join(this.timeout / 1000);          
                in.close();      
            }
            catch (InterruptedException e) {
                System.out.println("thread not closed");
                return;
            }
            catch (IOException e) {
                System.out.println("file read not closed");
                return;
            }
            
        }

    }

    /**
     * Three way hand shake for initializing connection
     * @return succeed or not
     */
    public boolean initConnection() {
        // build segment for sending
        TCPmessage init = new TCPmessage(this.sequenceNo, 0, 0);
        init.setFlag('S');
        byte[] initBytes = init.serialize();
        
        DatagramPacket initPacket = new DatagramPacket(initBytes, initBytes.length, this.remoteIP, this.remotePort);

        // send segment
        if(!sendPacket(initPacket, "sender: init 1st packet not sent"))
            return false;
        printStat(init, "snd");

        sentDataSize += init.getLength();
        sentPacketCount ++;

        byte[] inBuffer = new byte[this.mtu + TCPmessage.HEADER_LENGTH];
        DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

        if(!receivePacket(inPacket, "sender: init no ack received"))
            return false;

        // build segment for receiving
        TCPmessage ackRecv = new TCPmessage(0, 0, 0);
        ackRecv = ackRecv.deserialize(inPacket.getData());

        if(!ackRecv.isSYN() || !ackRecv.isACK() || ackRecv.getAcknowledgment() != this.sequenceNo + 1) {
            System.out.println("sender: init received wrong info");
            return false;
        }
        this.ackNo = ackRecv.getAcknowledgment();
        printStat(ackRecv, "rcv");
        receivedPacketCount ++;
        receivedDataSize += ackRecv.getLength();

        // calculate first value for timeout
        this.ertt = System.nanoTime() - ackRecv.getTimestamp();
        this.timeout = this.ertt * 2;

        // set initila timeout
        try {
            this.socket.setSoTimeout((int)(this.timeout / 1000000)); // milliseconds
        }
        catch(SocketException e) {
            System.out.println("sender: timeout set error");
            return false;
        }

        int inSeqNO = ackRecv.getSequenceNo();
        this.sequenceNo += 1;

        // third packet 
        TCPmessage init2 = new TCPmessage(this.sequenceNo, inSeqNO + 1, 0);
        init2.setFlag('A');
        byte[] initBytes2 = init2.serialize();
        DatagramPacket initPacket2 = new DatagramPacket(initBytes2, initBytes2.length, this.remoteIP, this.remotePort);
        
        if(!sendPacket(initPacket2, "sender: init 2nd packet not sent"))
            return false;
        printStat(init2, "snd");

        sentDataSize += init.getLength();
        sentPacketCount ++;

        return true;
    }

    /**
     * simple function taht sends packets
     * @param packet datagrampacket to be sent
     * @param printMessage error message
     * @return
     */
    public boolean sendPacket(DatagramPacket packet, String printMessage) {
        try {
            this.socket.send(packet);
        }
        catch (IOException e) {
            System.out.println(printMessage);
            return false;
        }
        return true;
    }
    
    /**
     * simple function for receiving packets
     * @param packet datagrampacket to be received
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

    /**
     * printing host output
     * @param packet
     * @param sndRcv snd or rcv
     */
    public void printStat(TCPmessage packet, String sndRcv) {
        StringBuilder output = new StringBuilder();
        output.append(sndRcv);
        output.append(" ");
        output.append(String.format("%.3f", (packet.getTimestamp() - START_TIME) / 100_000_000.0));
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
     * Run by the receiving thread, continue to listen for acks
     */
    public void receiveThread() {
        // check ack, if match, remove from buffer
        // keep track of ack count, if 3 acks of same then resend everything after
        while(this.connected) {
            byte[] inBuffer = new byte[this.mtu + TCPmessage.HEADER_LENGTH];
            DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

            if(!receivePacket(inPacket, "error receiving"))
                continue;

            TCPmessage message = new TCPmessage(0, 0, 0);
            message = message.deserialize(inPacket.getData());
            printStat(message, "rcv");

            receivedPacketCount ++;
            receivedDataSize += message.getLength();

            // if received ack of fin from receiver, enter termination function
            if(message.isACK() && message.isFIN()) {
                terminateConnectionResponse();
                break;
            }
            // just ack messages
            else if(message.isACK()) {
                recalculateTimeout(message.getTimestamp());
                int recvdAckNo = message.getAcknowledgment();
                int recvdSeq = message.getSequenceNo();

                // make sure other threads don't access the buffer 
                synchronized(buffer) {
                    for (Map.Entry<Integer, TCPpacket> segment : buffer.entrySet()) {
                        Integer seq = segment.getKey();
                        TCPpacket data = segment.getValue();

                        // remove from buffer if received later acks
                        if(seq + data.message.getLength() <= recvdAckNo) {
                            buffer.remove(seq);
                        }
                        // update count for items that received an ack
                        if(seq == recvdAckNo) {
                            data.ackCount ++;
                            if(data.ackCount > 1)
                                dupAckCount ++;
                            // fast retransmission
                            if(data.ackCount >= 3) {
                                data.message.setTimestamp(System.nanoTime());
                                byte[] newStream = data.message.serialize();
                                data.packet = new DatagramPacket(newStream, newStream.length, this.remoteIP, this.remotePort);
                                sendPacket(data.packet, "resent packet ack > 3");
                                printStat(data.message, "snd");
                                data.ackCount = 0;
                                sentDataSize += data.message.getLength();
                                sentPacketCount ++;
                                retransmissionCount ++;
                            }
                        }
                    }                      
                }
                this.ackNo = recvdSeq;   
            }
        }
    }

    /**
     * recalculate timeout for every ack
     * @param dataTime
     */
    public void recalculateTimeout(long dataTime) {
        long current = System.nanoTime();
        long srtt = current - dataTime;
        long sdev = Math.abs(srtt - this.ertt);
        this.ertt = (long) (0.875 * this.ertt + (1 - 0.875) * srtt);
        this.edev = (long) (0.75 * this.edev + (1 - 0.75) * sdev);
        this.timeout = this.ertt + 4 * this.edev;
        try {
            this.socket.setSoTimeout((int)(this.timeout / 1_000_000));
        }
        catch(SocketException e) {
            System.out.println("sender: timeout set error");
        }
        
    }

    /** 
     * terminate connection three way hand shake
    */
    public void terminateConnection() {
        TCPmessage finMessage = new TCPmessage(this.sequenceNo, this.ackNo, 0);
        finMessage.setFlag('F');
        byte[] finData = finMessage.serialize();
        DatagramPacket finPacket = new DatagramPacket(finData, finData.length, this.remoteIP, this.remotePort);
        sendPacket(finPacket, "fin message send error");
        printStat(finMessage, "snd");
        this.sequenceNo++;
        sentDataSize += finMessage.getLength();
        sentPacketCount ++;
    }   
    /** 
     * response for terminate connection, send back an ack
    */
    public void terminateConnectionResponse() {

        TCPmessage finMessage2 = new TCPmessage(this.sequenceNo, this.ackNo + 1, 0);
        finMessage2.setFlag('A');
        byte[] finData = finMessage2.serialize();
        DatagramPacket finPacket = new DatagramPacket(finData, finData.length, this.remoteIP, this.remotePort);
        sendPacket(finPacket, "fin message send error");
        printStat(finMessage2, "snd");
        this.connected = false;
        sentDataSize += finMessage2.getLength();
        sentPacketCount ++;
    }   
    
    /**
     * make sure no segment in the buffer stay beyond timeout
     */
    public void checkTimeout() {
        // loop through buffer to see timeout
        while(this.connected) {
            if(buffer.size() > 0) {
                for (Map.Entry<Integer, TCPpacket> segment : buffer.entrySet()) {
                    TCPpacket current = segment.getValue();
                    long timestamp = current.message.getTimestamp();
                    if(System.nanoTime() - timestamp > timeout) {
                        // update timestamp and resend packet
                        current.message.setTimestamp(System.nanoTime());
                        byte[] newStream = current.message.serialize();
                        current.packet = new DatagramPacket(newStream, newStream.length, this.remoteIP, this.remotePort);
                        sendPacket(current.packet, "resent packet because timeout");
                        printStat(current.message, "snd");
                        sentDataSize += current.message.getLength();
                        sentPacketCount ++;
                        retransmissionCount ++;
                    }
                }
            }
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
 * a class for added to the buffer, more convenient, so don't have tp serialize and deserialize
 */
class TCPpacket {
    DatagramPacket packet;
    TCPmessage message;
    int ackCount = 0; // counts if matches the acknowledgment in header, if >= 3 retransmit
}
