import java.util.HashMap;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

/**
 * Class for routers and hosts.
 */
public class Node {
    private short ID;
    private String name;
    private HashMap<Node, Link> routingTable;   // Node: a node in the network, Link: the link that can lead to the node
    private HashMap<Node, Link> links;          // for building routing table
    private SimpleNetwork network;

    /**
     * Constructor
     * @param nodeName name of the node
     * @param nodeID ID of the node
     * @param network the network the node is in
     */
    Node(String nodeName, short nodeID, SimpleNetwork network) {
        this.ID = nodeID;
        this.name = nodeName;
        this.routingTable = new HashMap<>();
        this.links = new HashMap<>();
        this.network = network;
    }

    public short getID() {
        return this.ID;
    }
    public String getName() {
        return this.name;
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
     * Using BFS for creating the routing table
     * @param nodeList the full list of nodes in the network
     */
    public void buildRoutingTable(Node[] nodeList) {
        this.routingTable = new HashMap<>();

        HashMap<Node, Integer> distances = new HashMap<>();
        HashMap<Node, Node> parents = new HashMap<>();

        // Set up initial distances
        for(int i = 0; i < nodeList.length; i++) {
            distances.put(nodeList[i], Integer.MAX_VALUE);
            if(nodeList[i].equals(this))
                distances.put(nodeList[i], 0);
            parents.put(nodeList[i], null);
        }

        Queue<Node> unvisited = new LinkedList<>();
        unvisited.add(this);
        Queue<Node> visited = new LinkedList<>();

        // main logic for BFS
        while(!unvisited.isEmpty()) {

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

        // creating the routing table
        for(Node node: nodeList) {
            
            int dist = distances.get(node);
            // filter out the nodes that are either itself or not connected to this one
            if(dist == 0 || dist == Integer.MAX_VALUE)
                continue;

            // find the link that leads to the destination node and add to table
            Node parent = node;
            while(dist != 1) {
                parent = parents.get(parent);
                dist-=1;
            }
            this.routingTable.put(node, links.get(parent));
        }
    }

    /**
     * Nodes just send the packet to the link towards the destination of the packet.
     * @param packet the packet to be sent
     */
    public void send(SimplePacket packet) {
        short destID = packet.getDestinationID();

        Node node = this.network.getNodeFromID(destID);
        if(node == null) {
            System.out.println("node doesn't exist");
            return;
        }

        Link linkToSend = this.routingTable.get(node);
        if(linkToSend == null) {
            System.out.println("link doesn't exist");
            return;
        }
        linkToSend.send(packet);
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
    /**
     * Prints the routing table for this node
     */
    public void printTable() {
        System.out.println("=== Table for " + this.toString() + " ===");
        for(HashMap.Entry<Node, Link> entry: routingTable.entrySet()) {
            System.out.println("     " + entry.getKey() + " ---> " + entry.getValue());
        }
    }

}
