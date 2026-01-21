/**
 * A simple packet class that acts like datagram packets without checksum.
 * Not implementing checksum to overcomplicate the emulator, but added the fields just in case.
 * 
*/
public class SimplePacket {
    private short sourceID;
    private short destID;
    private short length;           // recorded but not used, in bytes
    private short checksum = 0;     // not used
    private byte[] payload;

    private final short HEADER_LENGTH = 8; // in bytes

    SimplePacket(short source, short dest, byte[] payload) {
        this.sourceID = source;
        this.destID = dest;
        this.payload = payload;

        this.length = (short) (HEADER_LENGTH + this.payload.length);
    }

    public short getSourceID() {
        return this.sourceID;
    }
    public short getDestinationID() {
        return this.destID;
    }
    public short getLength() {
        return this.length;
    }
    public byte[] getPayload() {
        return this.payload;
    }

    public void setSourceID(short source) {
        this.sourceID = source;
    }
    public void setDestinationID(short dest) {
        this.destID = dest;
    }
    public void setPayload(byte[] payload) {
        this.payload = payload;
        this.length = (short) (HEADER_LENGTH + this.payload.length);
    }

    // in bytes
    public int getSize() {
        return HEADER_LENGTH + this.payload.length;
    }

}
