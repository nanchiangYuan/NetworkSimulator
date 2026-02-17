import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * The network that includes all the nodes and links.
 */
public class SimpleNetwork {
    private ArrayList<Node> hosts;      // all the hosts
    private ArrayList<Node> routers;    // all the routers
    private ArrayList<Link> links;      // all the links
    private Scheduler scheduler;

    /**
     * Constructor
     * @param filename the topology file to be imported
     * @param scheduler the scheduler that runs on this network
     */
    SimpleNetwork(String filename, Scheduler scheduler) {
        setUpTopology(filename);
        this.scheduler = scheduler;
    }

    public ArrayList<Node> getHosts() {
        return this.hosts;
    }
    public ArrayList<Node> getRouters() {
        return this.routers;
    }
    public ArrayList<Link> getLinks() {
        return this.links;
    } 

    /**
     * Find node from a given ID
     * @param ID the ID of the node to be found
     * @return the node
     */
    public Node getNodeFromID(short ID) {
        Node node = null;
        for(Node curr: hosts) {
            if(curr.getID() == ID) {
                node = curr;
                break;
            }

        }
        if(node == null) {
            for(Node curr: routers) {
                if(curr.getID() == ID) {
                    node = curr;
                    break;
                }
            }
        }
        return node;
    }

    /**
     * Sets up the network with the topology specified in the give file
     * @param filename the name of the topology file
     */
    private void setUpTopology(String filename) {

        // clear out old info first
        hosts = new ArrayList<>();
        routers = new ArrayList<>();
        links = new ArrayList<>();

        File topoFile = new File(filename);

        try (Scanner readFile = new Scanner(topoFile)){
            while(readFile.hasNextLine()) {
                String line = readFile.nextLine();
                String[] split = line.split("\\s+");
                if(!setUpHelper(split)) 
                    break;
            }
        } catch (FileNotFoundException E) {
            System.out.println("File does not exit");
            return;
        } 

        buildRoutingTables();
    }

    /**
     * Get a single line from the file, process it and add it to the network.
     * @param features the line of input from a topology file
     * @return true if success, false otherwise
     */
    private boolean setUpHelper(String[] features) {

        // adding hosts
        if(features[0].equals("host")) {
            hosts.add(new Node(features[1], Short.valueOf(features[2]), this));
        }
        // adding routers
        else if(features[0].equals("router")) {
            routers.add(new Node(features[1], Short.valueOf(features[2]), this));
        }
        // adding links
        else if(features[0].equals("link")) {

            Node n1 = null;
            Node n2 = null;

            // find the nodes that are connected to this link
            // the nodes should all be before the links in the original file
            for(Node n: hosts) {
                if(n.getName().equals(features[1])) 
                    n1 = n;
                if(n.getName().equals(features[2])) 
                    n2 = n;
            }
            for(Node n: routers) {
                if(n.getName().equals(features[1])) 
                    n1 = n;
                if(n.getName().equals(features[2])) 
                    n2 = n;
            }

            if(n1 == null || n2 == null || n1.equals(n2)) {
                System.out.println("File format error");
                return false;
            }

            double queueSize;
            double bandwidth;
            double latency;
            double lossrate;

            // grab the configuration values of this link
            try {
                queueSize = Double.valueOf(features[3]);
                bandwidth = Double.valueOf(features[4]);
                latency = Double.valueOf(features[5]);
                lossrate = Double.valueOf(features[6]);
            } catch (NumberFormatException e) {
                System.out.println("File format error: not numbers");
                return false;
            }
            Link newLink1 = new Link(n1, n2, queueSize, bandwidth, latency, lossrate, this.scheduler);
            Link newLink2 = new Link(n2, n1, queueSize, bandwidth, latency, lossrate, this.scheduler);
            links.add(newLink1);
            links.add(newLink2);
            n1.addLink(n2, newLink1);
            n2.addLink(n1, newLink2);
        }
        else {
            System.out.println("File format error");
            return false;
        }
        
        return true;
    }
    
    /**
     * Prints the current topology
     */
    public void printTopo() {

        System.out.println("Hosts: ");
        for (Node node : hosts) {
            System.out.println(" " + node);
        }
        System.out.println("Routers: ");
        for (Node node : routers) {
            System.out.println(" " + node);
        }
        System.out.println("Links: ");
        for (Link link : links) {
            System.out.println(" " + link);
        }

    }

    /**
     * Helper to build routing tables of every node
     */
    private void buildRoutingTables() {

        // first construct a full list of all nodes
        Node[] nodeList = new Node[hosts.size() + routers.size()];
        for(int i = 0; i < hosts.size(); i++) {
            nodeList[i] = hosts.get(i);
        }
        for(int i = 0; i < routers.size(); i++) {
            nodeList[hosts.size() + i] = routers.get(i);
        }

        // call the build methods for every node
        for(Node host: hosts) {
            host.buildRoutingTable(nodeList);
        }
        for(Node router: routers) {
            router.buildRoutingTable(nodeList);
        }

    }
}
