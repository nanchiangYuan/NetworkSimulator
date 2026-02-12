public class Event implements Comparable<Event>{

    private SimplePacket packet;
    private int seqNo;
    private int length;
    private EventType type;
    private double time;        // the time this event is happening at
    private Node destination;   // the arrival node (not necessarily the end destination, just the next node on the link), null for timeout check

    public static enum EventType {
        ARRIVE,
        TIMEOUT_CHECK,
        TIME_WAIT
    }

    // for arrive
    Event(SimplePacket packet, EventType type, double time, Node dest) {
        this.packet = packet;
        this.type = type;
        this.time = time;
        this.destination = dest;
    }

    // for timeout
    Event(int oldestSequenceNo, EventType type, double timeout) {
        this.seqNo = oldestSequenceNo;
        this.type = type;
        this.time = timeout;
    }

    // for time wait
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

    /**
     * 
     * @return event to add onto queue (timeout mostly)
     */
    public Event execute() {
        if(this.type == EventType.ARRIVE) {
            // if arrival node is dest node, do TCP receiver logic (remove infinite loop)
            // and add timeout check event onto queue
            // else pass down packet by doing node.send
        }
        else if(this.type == EventType.TIMEOUT_CHECK) {

        }
        return null;
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


