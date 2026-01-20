public class Event implements Comparable<Event>{

    private SimplePacket packet;
    private EventType type;
    private double time;
    private Node destination;

    public static enum EventType {
        ARRIVE,
        TIMEOUT_CHECK
    }

    Event(SimplePacket packet, EventType type, double time, Node dest) {
        this.packet = packet;
        this.type = type;
        this.time = time;
        this.destination = dest;
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

    @Override
    public int compareTo(Event other) {
        return Double.compare(this.time, other.getTime());
    }

    @Override
    public String toString() {
        return this.packet + ", " + this.type + ", " + time;
    }

}


