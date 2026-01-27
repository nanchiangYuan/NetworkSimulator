public class TCPStat {

    private int sentDataSize = 0;
    private int sentPacketCount = 0;
    private int receivedDataSize = 0;
    private int receivedPacketCount = 0;
    private int retransmissionCount = 0;
    private int dupAckCount = 0;

    private int invalidChecksumCount = 0;
    private int droppedPacketCount = 0;

    private int mode;   // 0: sender, 1: receiver

    TCPStat(String mode) {
        if(mode.equals("sender"))
            this.mode = 0;
        else
            this.mode = 1;
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

    public void printPackets(TCPmessage message, String sndRcv, double time, boolean verbose) {

        if(!verbose)
            return; 

        String profile;

        if(mode == 0)
            profile = "sender";
        else
            profile = "receiver";

        StringBuilder output = new StringBuilder();
        output.append(profile);
        output.append(" ");
        output.append(sndRcv);
        output.append(" ");
        output.append(time);
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
                System.out.println("Amount of data transferred: " + sentDataSize);
                System.out.println("Amount of data received: " + receivedDataSize);
                System.out.println("Number of packets sent: " + sentPacketCount);
                System.out.println("Number of packets received: " + receivedPacketCount);
                System.out.println("Number of retransmissions: " + retransmissionCount);
                System.out.println("Number of duplicate acknowledgements: " + dupAckCount);
                break;
            case 1:
                System.out.println("Receiver Stats: ");
                System.out.println("Amount of data transferred: " + sentDataSize);
                System.out.println("Amount of data received: " + receivedDataSize);
                System.out.println("Number of packets sent: " + sentPacketCount);
                System.out.println("Number of packets received: " + receivedPacketCount);
                System.out.println("Number of out-of-sequence packets discarded: " + droppedPacketCount);
                System.out.println("Number of packets discarded due to incorrect checksum: " + invalidChecksumCount);
        }
    }
}
