import java.util.concurrent.LinkedBlockingQueue;

public class Link {
    private int queueSize;
    private int bandwidth; // in bits
    private int latency;
    private LinkedBlockingQueue<SimplePacket> buffer;

    private Scheduler scheduler;
    private Node[] connections;

    Link(Node n1, Node n2, int queueSize, int bandwidth, int latency, Scheduler scheduler) {
        this.queueSize = queueSize;
        this.bandwidth = bandwidth;
        this.latency = latency;
        this.buffer = new LinkedBlockingQueue<>(queueSize);
        this.connections = new Node[]{n1, n2};
        this.scheduler = scheduler;
    }

    public void setConnections(Node n1, Node n2) {
        this.connections[0] = n1;
        this.connections[1] = n2;
    }

    public void setQueueSize(int size) {
        this.queueSize = size;
    }

    public void setBandwidth(int bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setLatency(int latency) {
        this.latency = latency;
    }

    public Node[] getConnections() {
        return connections;
    }

    public int getQueueSize() {
        return this.queueSize;
    }

    public int getBandwidth() {
        return this.bandwidth;
    }

    public int getLatency() {
        return this.latency;
    }

    public String toString() {
        return "link: " + this.connections[0].getName() + ", " + this.connections[1].getName();
    }

    public boolean send(SimplePacket packet, Node source) {

        
        Node dest;
        if(source.equals(this.connections[0]))
            dest = this.connections[1];
        else
            dest = this.connections[0];
        if(dest == null)
            return false;

        double currentTime = this.scheduler.getCurrentTime();
        double arrivalTime = currentTime ++;
        

        this.scheduler.getQueue().add(new Event(packet, Event.EventType.ARRIVE, arrivalTime));
        return false;
    }

    public boolean receive(SimplePacket packet) {
        return false;
    }


}
