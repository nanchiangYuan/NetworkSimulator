public class PacketWrapper {
    SimplePacket packet;
    TCPmessage message;
    int ackCount = 0; // counts if matches the acknowledgment in header, if >= 3 retransmit
    double timeSent;

    PacketWrapper(SimplePacket packet, TCPmessage message, double timeSent) {
        this.packet = packet;
        this.message = message;
        this.timeSent = timeSent;
    }
}