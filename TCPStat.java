import java.util.ArrayList;

public class TCPStat {

    private int sentDataSize = 0;           // in bytes
    private int sentPacketCount = 0;    
    private int receivedDataSize = 0;       // in bytes
    private int receivedPacketCount = 0;    
    private int retransmissionCount = 0;
    private int dupAckCount = 0;

    private int invalidChecksumCount = 0;
    private int droppedPacketCount = 0;

    private ArrayList<Double> rtt;          // all rtt times
    private ArrayList<Double> cwnd;         // all cwnd size
    private int timeoutCount = 0;
    private int fastRetransmitCount = 0;

    private double finalTime;

    private int mode;   // 0: sender, 1: receiver

    TCPStat(String mode) {
        if(mode.equals("sender"))
            this.mode = 0;
        else
            this.mode = 1;

        this.rtt = new ArrayList<>();
        this.cwnd = new ArrayList<>();
    }

    public void addSentData(int packets, int size) {
        sentPacketCount += packets;
        sentDataSize += size;
    }
    public void addReceivedData(int packets, int size) {
        receivedPacketCount += packets;
        receivedDataSize += size;
    }
    public void addRetransmissionCount(int count) {
        retransmissionCount += count;
    }
    public void addDupAck(int count) {
        dupAckCount += count;
    }
    public void addInvalidChecksum(int count) {
        invalidChecksumCount += count;
    }
    public void addDroppedPacket(int count) {
        droppedPacketCount += count;
    }
    public void addRTT(double time) {
        rtt.add(time);
    }
    public void addCwnd(double c) {
        cwnd.add(c);
    }
    public void addTimeout(int count) {
        timeoutCount += count;
    }
    public void addFastRetransmit(int count) {
        fastRetransmitCount += count;
    }
    public void setFinalTime(double time) {
        finalTime = time;
    }

    public int getSentDataSize() {
        return sentDataSize;
    }
    public int getReceivedDataSize() {
        return receivedDataSize;
    }
    public int getSentPacketCount() {
        return sentPacketCount;
    }
    public int getReceivedPacketCount() {
        return receivedPacketCount;
    }
    public int getRetransmissionCount() {
        return retransmissionCount;
    }
    public int getDupAck() {
        return dupAckCount;
    }
    public int getInvalidChecksum() {
        return invalidChecksumCount;
    }
    public int getDroppedPacket() {
        return droppedPacketCount;
    }
    public ArrayList<Double> getRTT() {
        return rtt;
    }
    public ArrayList<Double> getCwnd() {
        return cwnd;
    }
    public int getTimeout() {
        return timeoutCount;
    }
    public int getFastRetransmit() {
        return fastRetransmitCount;
    }
    public double getFinalTime() {
        return finalTime;
    }

    public void printPackets(TCPmessage message, String sndRcv, double time, boolean verbose) {

        if(!verbose)
            return; 

        String profile;

        if(mode == 0)
            profile = "sender";
        else
            profile = "                                            recver";

        String timeFormat = String.format("%.6f", time);

        StringBuilder output = new StringBuilder();
        output.append(profile);
        output.append(" ");
        output.append(sndRcv);
        output.append(" ");
        output.append(timeFormat);
        output.append(" ");
        if(message.isSYN())
            output.append("S");
        else
            output.append("-");
        output.append(" ");
        if(message.isACK())
            output.append("A");
        else
            output.append("-");
        output.append(" ");
        if(message.isFIN())
            output.append("F");
        else
            output.append("-");
        output.append(" ");
        if(message.hasData())
            output.append("D");
        else
            output.append("-");

        output.append(" ");
        output.append(message.getSequenceNo());
        output.append(" ");
        output.append(message.getLength());
        output.append(" ");
        output.append(message.getAcknowledgment());

        System.out.println(output.toString());
    }

    public void printStat() {
        switch(mode) {
            case 0:
                System.out.println("Sender Stats: ");
                System.out.println("    Amount of data transferred:                        " + convert(sentDataSize));
                System.out.println("    Number of packets sent:                            " + sentPacketCount);
                System.out.println("    Number of packets received:                        " + receivedPacketCount);
                System.out.println("    Number of retransmissions:                         " + retransmissionCount);
                System.out.println("    Number of duplicate acknowledgements:              " + dupAckCount);
                System.out.println("    Number of packets discarded (incorrect checksum):  " + invalidChecksumCount);
                break;
            case 1:
                System.out.println("Receiver Stats: ");
                System.out.println("    Amount of data received:                           " + convert(receivedDataSize));
                System.out.println("    Number of packets sent:                            " + sentPacketCount);
                System.out.println("    Number of packets received:                        " + receivedPacketCount);
                System.out.println("    Number of out-of-sequence packets discarded:       " + droppedPacketCount);
                System.out.println("    Number of packets discarded (incorrect checksum):  " + invalidChecksumCount);
        }
    }

    private String convert(double num) {
        if(num / 1000.0 > 1.0) {
            num /= 1000.0;
            if(num / 1000.0 > 1.0)
                return String.format("%.3f MB", num / 1000.0);
            return String.format("%.3f KB", num);
        }
        else
            return String.format("%.3f B", num);

    }
    
}
