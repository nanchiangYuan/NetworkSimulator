public class Link {
    private int queueSize;
    private int bandwidth; // in bits
    private int latency; // in ms
    private int bufferSize; // in KB
    private double nextAvailableTime;

    private Scheduler scheduler;
    private Node connection;

    private double fullBufferTime;

    Link(Node n, int queueSize, int bandwidth, int latency, Scheduler scheduler) {
        this.queueSize = queueSize;
        this.bandwidth = bandwidth;     // in Mbps
        this.latency = latency;
        this.bufferSize = queueSize;
        this.nextAvailableTime = 0.0;
        this.connection = n;
        this.scheduler = scheduler;
        this.fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0; 
    }

    public void setConnections(Node n) {
        this.connection = n;
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

    public Node getConnection() {
        return this.connection;
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
        return "link: to " + this.connection.getName();
    }

    /**
     * 
     * @param packet
     * @param source
     * @return false if sending caused errors, true if no error (dropping a packet is considered normal behavior)
     */
    public boolean send(SimplePacket packet) {

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
            return true;    // drop packet

        this.nextAvailableTime = checkAvailableTime;
        double arriveTime = this.nextAvailableTime + this.latency;

        this.scheduler.schedule(new Event(packet, Event.EventType.ARRIVE, arriveTime, this.connection));
        return false;
    }

    public boolean receive(SimplePacket packet) {
        return false;
    }


}
