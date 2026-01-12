public class Link {
    private int queueSize;
    private int bandwidth;
    private int latency;

    private Node[] connections;

    Link(Node n1, Node n2, int queueSize, int bandwidth, int latency) {
        this.queueSize = queueSize;
        this.bandwidth = bandwidth;
        this.latency = latency;
        this.connections = new Node[]{n1, n2};
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

    public void sendPacket(TCPmessage packet) {

    }


}
