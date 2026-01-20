import java.util.concurrent.LinkedBlockingQueue;

public class Link {
    private int queueSize;
    private int bandwidth; // in bits
    private int latency; // in ms
    private int bufferSize; // in KB
    private double nextAvailableTime;

    private Scheduler scheduler;
    private Node[] connections;

    private double fullBufferTime;

    Link(Node n1, Node n2, int queueSize, int bandwidth, int latency, Scheduler scheduler) {
        this.queueSize = queueSize;
        this.bandwidth = bandwidth;     // in Mbps
        this.latency = latency;
        this.bufferSize = queueSize;
        this.nextAvailableTime = 0.0;
        this.connections = new Node[]{n1, n2};
        this.scheduler = scheduler;
        this.fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0; 
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

    /**
     * 
     * @param packet
     * @param source
     * @return false if sending caused errors, true if no error (dropping a packet is considered normal behavior)
     */
    public boolean send(SimplePacket packet, Node source) {
        Node dest;
        if(source.equals(this.connections[0]))
            dest = this.connections[1];
        else
            dest = this.connections[0];
        if(dest == null)
            return false;

        // calculate delay caused by bandwidth, in ms
        // ms = (Bytes * 8) / (Mb / s * 1,000,000) * 1,000
        double currentTime = this.scheduler.getCurrentTime();
        double transmitTime = (packet.getSize() * 8.0) / (this.bandwidth * 1000000.0) * 1000.0; // s to ms

        // check whether the buffer is full 
        double currentBufferTime = this.fullBufferTime + currentTime;

        double departTime = Math.max(this.nextAvailableTime, currentTime);
        double checkAvailableTime = departTime + transmitTime;

        if(checkAvailableTime > currentBufferTime)
            return true;
        
        this.nextAvailableTime = checkAvailableTime;
        double arriveTime = this.nextAvailableTime + this.latency;

        this.scheduler.schedule(new Event(packet, Event.EventType.ARRIVE, arriveTime));
        return false;
    }

    public boolean receive(SimplePacket packet) {
        return false;
    }


}
