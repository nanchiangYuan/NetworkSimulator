/**
 * A class for defining what an event is.
 */
public class Event implements Comparable<Event>{

    private SimplePacket packet;    // the packet related to this event
    private int seqNo;              // the sequence number of the packet
    private int length;             // the length of the packet
    private EventType type;
    private double time;            // the time this event is happening at
    private Node destination;       // the arrival node (not necessarily the end destination, just the next node on the link)

    public static enum EventType {
        ARRIVE,             // for when a packet arrives at a node
        TIMEOUT_CHECK,      // for checking timeouts
        TIME_WAIT           // for termination
    }

    /**
     * Constructor for arrival events
     * @param packet the packet arriving
     * @param type the Event type
     * @param time the scheduled time
     * @param dest the destination node (the immediate node it is arriving at)
     */
    Event(SimplePacket packet, EventType type, double time, Node dest) {
        this.packet = packet;
        this.type = type;
        this.time = time;
        this.destination = dest;
    }

    /**
     * Constructor for timeout events
     * @param oldestSequenceNo the oldest packet sequence number that hasn't been acked
     * @param type the Event type
     * @param timeout the time where the timeout is reached
     */
    Event(int oldestSequenceNo, EventType type, double timeout) {
        this.seqNo = oldestSequenceNo;
        this.type = type;
        this.time = timeout;
    }

    /**
     * Contructor for time wait
     * @param type the Event type
     * @param time the time to wait in this state
     */
    Event(EventType type, double time) {
        this.type = type;
        this.time = time;
    }

    public SimplePacket getPacket() {
        return this.packet;
    }

    public EventType getType() {
        return this.type;
    }

    public double getTime() {
        return this.time;
    }

    public Node getDestination() {
        return this.destination;
    }

    public int getSequenceNo() {
        return this.seqNo;
    }

    public int getLength() {
        return this.length;
    }

    @Override
    public int compareTo(Event other) {
        return Double.compare(this.time, other.getTime());
    }

    @Override
    public String toString() {
        return "[E] seq: " + seqNo + ", " + type + ", " + time + ", dest: " + destination.getID();
    }

    // for timeout
    public String toTimeoutString() {
        return "[E] oldest seq: " + seqNo + ", " + type + ", " + time;
    }

}


