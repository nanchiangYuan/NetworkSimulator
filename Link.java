import java.util.Arrays;
import java.util.Random;

public class Link {
    private double bandwidth; // in bits
    private double latency; // in ms
    private double bufferSize; // in B
    private double lossRate;
    private double corruptionRate;

    private double nextAvailableTime;

    private int id;     // for debugging
    private static int idPool = 0;

    private Scheduler scheduler;
    private Node fromNode;
    private Node toNode;

    private double fullBufferTime;

    private double bandwidthFromFile;
    private double latencyFromFile;
    private double bufferSizeFromFile;
    private double lossRateFromFile;

    Link(Node n1, Node n2, double bufferSize, double bandwidth, double latency, double lossrate, Scheduler scheduler) {
        this.bandwidth = bandwidth;     // in Mbps
        this.latency = latency;
        this.bufferSize = bufferSize;
        this.lossRate = lossrate;
        this.nextAvailableTime = 0.0;
        this.fromNode = n1;
        this.toNode = n2;
        this.scheduler = scheduler;
        this.fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0;
        this.id = idPool;
        this.lossRate = 0.0;
        this.corruptionRate = 0.01;
        idPool++; 

        this.bandwidthFromFile = bandwidth;
        this.latencyFromFile = latency;
        this.bufferSizeFromFile = bufferSize;
        this.lossRateFromFile = lossrate;
    }

    public void setConnections(Node n1, Node n2) {
        this.fromNode = n1;
        this.toNode = n2;
    }

    public void setBufferSize(double size) {
        this.bufferSize = size;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setLatency(double latency) {
        this.latency = latency;
    }

    public Node getStartNode() {
        return this.fromNode;
    }

    public Node getEndNode() {
        return this.toNode;
    }

    public double getBufferSize() {
        return this.bufferSize;
    }

    public double getBandwidth() {
        return this.bandwidth;
    }

    public double getLatency() {
        return this.latency;
    }

    public int getID() {
        return this.id;
    }

    public double getLossRate() {
        return lossRate;
    }

    public void setScheduler(Scheduler sched) {
        this.scheduler = sched;
    }

    public void setLossRate(double rate) {
        lossRate = rate;
    }

    public void reset() {
        nextAvailableTime = 0.0;
        fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0;
    }

    public void resetConfig() {
        bandwidth = bandwidthFromFile;
        latency = latencyFromFile;
        bufferSize = bufferSizeFromFile;
        lossRate = lossRateFromFile;
    }

    public String toString() {
        StringBuilder linkInfo = new StringBuilder();
        linkInfo.append("link " + this.id + ": ");
        linkInfo.append(this.fromNode.getName() + " to " + this.toNode.getName() + "\n");
        linkInfo.append("    bandwidth: " + bandwidth + ", latency: " + latency + ", buffer size: " + bufferSize + ", loss rate: " + lossRate + ", corruption rate: " + corruptionRate);
        return linkInfo.toString();
    }

    /**
     * 
     * @param packet
     * @param source
     * @return false if dropping a packet, true if no error 
     */
    public void send(SimplePacket packet) {

        if(lossRate > 0.0) {
            Random rand = new Random();
            double prob = rand.nextDouble() * 100.0;

            if(prob <= lossRate)
                return;            
        }

        if(corruptionRate > 0.0) {
            Random rand = new Random();
            double prob = rand.nextDouble() * 100.0;

            if(prob <= corruptionRate) {
                byte[] data = Arrays.copyOf(packet.getPayload(), packet.getPayload().length);
                int index = rand.nextInt(0, data.length);
                data[index] = (byte) (data[index] ^ 0xFF);
                packet = packet.clone();
                packet.setPayload(data);
            }
        }

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

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;

        if(obj instanceof Link) {
            Link o = (Link) obj;
            if(o.getID() == this.id)
                return true;
        }
        return false;
    }


}
