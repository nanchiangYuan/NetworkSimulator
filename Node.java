import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Class for routers and hosts.
 */
public class Node {
    private short ID;
    private String name;
    private HashMap<Node, Link> routingTable;
    private HashMap<Node, Link> links;          // for building routing table
    private SimpleNetwork network;
    private ArrayBlockingQueue<SimplePacket> received;
    private long timeout;
    private Scheduler scheduler;

    Node(String nodeName, short nodeID, SimpleNetwork network, Scheduler scheduler) {
        this.ID = nodeID;
        this.name = nodeName;
        this.routingTable = new HashMap<>();
        this.links = new HashMap<>();
        this.network = network;
        this.received = new ArrayBlockingQueue<>(1);
        this.timeout = 0;
        this.scheduler = scheduler;
    }

    public short getID() {
        return this.ID;
    }
    public String getName() {
        return this.name;
    }

    public void setTimeout(long t) {
        this.timeout = t;
    }

    public void addLink(Node node, Link link) {
        links.put(node, link);
    }

    public void removeLink(Node node, Link link) {
        links.remove(node, link);
    }

    private HashMap<Node, Link> getLinks() {
        return this.links;
    }

    public HashMap<Node, Link> getRoutingTable() {
        return this.routingTable;
    }

    /**
     * BFS for building routing table
     */
    public void buildRoutingTable(Node[] nodeList) {
        this.routingTable = new HashMap<>();

        HashMap<Node, Integer> distances = new HashMap<>();
        HashMap<Node, Node> parents = new HashMap<>();

        for(int i = 0; i < nodeList.length; i++) {
            distances.put(nodeList[i], Integer.MAX_VALUE);
            if(nodeList[i].equals(this))
                distances.put(nodeList[i], 0);
            parents.put(nodeList[i], null);
        }

        Queue<Node> unvisited = new LinkedList<>();
        unvisited.add(this);
        Queue<Node> visited = new LinkedList<>();

        // System.out.println("table for " + this.toString());
        while(!unvisited.isEmpty()) {
            // for(Node n: unvisited)
            //     System.out.println("visited: " + n);
            Node curr = unvisited.poll();
            visited.add(curr);
            for(Node node : curr.getLinks().keySet()) {
                int dist = distances.get(curr);
                if(distances.get(node) > dist) {
                    distances.put(node, dist+1);
                    parents.put(node, curr);
                }
                if(!visited.contains(node))
                    unvisited.add(node);
            }
        }

        // for(Node node: nodeList) {
        //     System.out.println("   " + node + ", dist: " + distances.get(node) + ", parent: " + parents.get(node));
        // }

        for(Node node: nodeList) {
            
            int dist = distances.get(node);
            if(dist == 0 || dist == Integer.MAX_VALUE)
                continue;
            Node parent = node;
            while(dist != 1) {
                parent = parents.get(parent);
                dist-=1;
            }
            this.routingTable.put(node, links.get(parent));
        }
    }

    /**
     * nodes just send packet along by scheduling an event
     * @param packet
     * @return
     */
    public boolean send(SimplePacket packet) {
        short destID = packet.getDestinationID();

        Node node = this.network.getNodeFromID(destID);
        if(node == null)
            return false;

        Link linkToSend = this.routingTable.get(node);
        if(linkToSend == null)
            return false;

        linkToSend.send(packet);
        return false;
    }

    public boolean receive(SimplePacket packet) {
        
        return false;
    }

    public String toString() {
        return "Node: ID: " + this.ID + ", Name: " + this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;

        if(obj instanceof Node) {
            Node o = (Node) obj;
            if(o.getName().equals(this.name) && o.getID() == this.ID)
                return true;
        }
        return false;
    }
    @Override
    public int hashCode() {
        return Objects.hash(this.ID, this.name);
    }

    public void printTable() {
        System.out.println("=== Table for " + this.toString() + " ===");
        for(HashMap.Entry<Node, Link> entry: routingTable.entrySet()) {
            System.out.println("     " + entry.getKey() + " ---> " + entry.getValue());
        }
    }

}
