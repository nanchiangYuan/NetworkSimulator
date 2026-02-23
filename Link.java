import java.util.Arrays;
import java.util.Random;

/**
 * A class for links in a network
 */
public class Link {
    private double bandwidth;           // in Mbps
    private double latency;             // in ms
    private double bufferSize;          // in B, implemented here to have the logic along with bandwidth, latency, etc together
    private double lossRate;
    private double corruptionRate;

    private double nextAvailableTime;

    private int id;                     // for debugging
    private static int idPool = 0;      // keeping track of the IDs used

    private Scheduler scheduler;
    private Node fromNode;
    private Node toNode;

    private double fullBufferTime;      // using time to measure if the buffer is full or not (ie whether a packet can be sent)

    // saving the configurations from topology file
    private double bandwidthFromFile;   
    private double latencyFromFile;
    private double bufferSizeFromFile;
    private double lossRateFromFile;

    /**
     * The constructor
     * @param n1 from node
     * @param n2 to node
     * @param bufferSize size of the buffer
     * @param bandwidth bandwidth of this link
     * @param latency latency of this link
     * @param lossrate lossrate of this link
     * @param scheduler the scheduler for this network
     */
    Link(Node n1, Node n2, double bufferSize, double bandwidth, double latency, double lossrate, Scheduler scheduler) {
        this.bandwidth = bandwidth;     
        this.latency = latency;
        this.bufferSize = bufferSize;
        this.lossRate = lossrate;
        this.nextAvailableTime = 0.0;
        this.fromNode = n1;
        this.toNode = n2;
        this.scheduler = scheduler;
        this.fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0;  // in ms
        this.id = idPool;
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

    /**
     * Reset the values that's involved in a test
     */
    public void reset() {
        nextAvailableTime = 0.0;
        fullBufferTime = (this.bufferSize * 8.0) / (this.bandwidth * 1000000.0) * 1000.0;
    }

    /**
     * Reload the configurations from topology
     */
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
     * Main logic for link. Calculates the amount of time needed to send a packet through this link, then schedules
     * an arrival event (which has the packet arrive at the next node).
     * @param packet the packet to be sent
     */
    public void send(SimplePacket packet) {

        // just drop packet
        if(lossRate > 0.0) {
            Random rand = new Random();
            double prob = rand.nextDouble() * 100.0;

            if(prob <= lossRate)
                return;            
        }

        // randomly flip some bytes
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

        // the time when all buffered items are sent (if buffer is full)
        double currentBufferTime = this.fullBufferTime + currentTime;
        
        // the estimated time the packet can be sent
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
