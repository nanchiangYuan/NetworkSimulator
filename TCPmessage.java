import java.nio.ByteBuffer;

/**
 * Class for a TCP segment
 */
public class TCPmessage{
    private int sequenceNo;
    private int acknowledgment;
    private double timestamp;
    private int length;
    private int flags;
    private short checksum;
    private byte[] payload;

    public final static int HEADER_LENGTH = 24;

    private boolean Sflag;
    private boolean Fflag;
    private boolean Aflag;

    TCPmessage(int sequenceNo, int acknowledgment, int length, double currentTime) {
        this.sequenceNo = sequenceNo;
        this.acknowledgment = acknowledgment;
        this.timestamp = currentTime;
        this.length = length;
        this.flags = 0;
        this.checksum = 0;
        this.Sflag = false;
        this.Fflag = false;
        this.Aflag = false;

        this.payload = null;
    }

    public int getSequenceNo() {
        return this.sequenceNo;
    }

    public int getAcknowledgment() {
        return this.acknowledgment;
    }

    public double getTimestamp() {
        return this.timestamp;
    }

    public int getLength() {
        return this.length;
    }

    public int getChecksum() {
        return this.checksum;
    }
    public byte[] getPayload() {
        return this.payload;
    }

    public boolean isSYN() {
        return this.Sflag;
    }
    public boolean isFIN() {
        return this.Fflag;
    }
    public boolean isACK() {
        return this.Aflag;
    }

    public void setFlag(char flag) {

        if(flag == 'S' && !this.Sflag) {
            this.flags |= 4;
            this.Sflag = true;
        }
        if(flag == 'F' && !this.Fflag) {
            this.flags |= 2;
            this.Fflag = true;
        }
        if(flag == 'A' && !this.Aflag) {
            this.flags |= 1;
            this.Aflag = true;
        }
    }
    public void removeFlag(char flag) {
        if(flag == 'S' && this.Sflag) {
            this.flags &= ~4;
            this.Sflag = false;
        }
        if(flag == 'F' && this.Fflag) {
            this.flags &= ~2;
            this.Fflag = false;
        }
        if(flag == 'A' && this.Aflag) {
            this.flags &= ~1;
            this.Aflag = false;
        }
    }

    public void setAcknowledgment(int ack) {
        this.acknowledgment = ack;
    }
    public void setTimestamp(double time) {
        this.timestamp = time;
    }
    public void setPayload(byte[] data) {
        this.payload = data;
        this.length = data.length;
    }

    public boolean hasData() {
        return this.getLength() > 0;
    }
    public void resetChecksum() {
        this.checksum = 0;
    }

    public byte[] serialize() {
        this.checksum = 0;

        int totalLength = HEADER_LENGTH + this.length;

        byte[] data = new byte[totalLength];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.putInt(this.sequenceNo);
        bb.putInt(this.acknowledgment);
        bb.putDouble(this.timestamp);

        int lengthWithFlags = (this.length << 3) | this.flags;

        bb.putInt(lengthWithFlags);
        bb.putShort((short)0);
        bb.putShort(this.checksum);
        if (this.payload != null && this.length != 0) {
            bb.put(this.payload);
        }

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int value = 0;

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

            this.checksum = (short) (~value & 0xFFFF);
            bb.putShort(22, this.checksum);
        }

        return data;
    }

    public TCPmessage deserialize(byte[] data) {

        ByteBuffer bb = ByteBuffer.wrap(data);
        this.sequenceNo = bb.getInt();
        this.acknowledgment = bb.getInt();
        this.timestamp = bb.getDouble();
        int lengthWithFlags = bb.getInt();
        this.length = lengthWithFlags >> 3;
        this.flags = lengthWithFlags & 7;
        bb.getShort();
        this.checksum = bb.getShort();
        byte[] payloadData = new byte[this.length];
        if(this.length > 0) {
            bb.get(payloadData);
            this.payload = payloadData;
        }
            
        // check flags
        this.Sflag = ((this.flags & 4) != 0);
        this.Fflag = ((this.flags & 2) != 0);
        this.Aflag = ((this.flags & 1) != 0);

        return this;
    }

}
