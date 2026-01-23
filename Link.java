public class Link {
    private int queueSize;
    private int bandwidth; // in bits
    private int latency; // in ms
    private int bufferSize; // in KB
    private double nextAvailableTime;

    private int id;     // for debugging
    private static int idPool = 0;

    private Scheduler scheduler;
    private Node fromNode;
    private Node toNode;

    private double fullBufferTime;

    Link(Node n1, Node n2, int queueSize, int bandwidth, int latency, Scheduler scheduler) {
        this.queueSize = queueSize;
        this.bandwidth = bandwidth;     // in Mbps
        this.latency = latency;
        this.bufferSize = queueSize;
        this.nextAvailableTime = 0.0;
        this.fromNode = n1;
        this.toNode = n2;
        this.scheduler = scheduler;
        this.fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0;
        this.id = idPool;
        idPool++; 
    }

    public void setConnections(Node n1, Node n2) {
        this.fromNode = n1;
        this.toNode = n2;
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

    public Node getStartNode() {
        return this.fromNode;
    }

    public Node getEndNode() {
        return this.toNode;
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
        return "link " + this.id + " : from " + this.fromNode.getName() + ", to " + this.toNode.getName();
    }

    /**
     * 
     * @param packet
     * @param source
     * @return false if dropping a packet, true if no error 
     */
    public void send(SimplePacket packet) {

        // calculate delay caused by bandwidth, in ms
        // ms = (Bytes * 8) / (Mb / s * 1,000,000) * 1,000
        double currentTime = this.scheduler.getCurrentTime();
        double transmitTime = (packet.getSize() * 8.0) / (this.bandwidth * 1000000.0) * 1000.0; // s to ms

        double currentBufferTime = this.fullBufferTime + currentTime;

        double departTime = Math.max(this.nextAvailableTime, currentTime);
        double checkAvailableTime = departTime + transmitTime;

        // check whether the buffer is full by seeing if the estimated end time to go through the link
        // is further away from the end time of processing everything in the buffer
        if(checkAvailableTime > currentBufferTime)
            return;    // drop packet

        this.nextAvailableTime = checkAvailableTime;
        double arriveTime = this.nextAvailableTime + this.latency;

        this.scheduler.schedule(new Event(packet, Event.EventType.ARRIVE, arriveTime, this.toNode));

    }

    public boolean receive(SimplePacket packet) {
        return false;
    }


}
