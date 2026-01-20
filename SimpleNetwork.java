import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class SimpleNetwork {
    private ArrayList<Node> hosts;
    private ArrayList<Node> routers;
    private ArrayList<Link> links;
    private Scheduler scheduler;

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
     * sets up the topology of hosts and routers
     * @param mode 0 for default, linear topology with 2 hosts and 1 router, 1 for custom topo from file
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

    private boolean setUpHelper(String[] features) {
        if(features[0].equals("host")) {
            hosts.add(new Node(features[1], Short.valueOf(features[2]), this, this.scheduler));
        }
        else if(features[0].equals("router")) {
            routers.add(new Node(features[1], Short.valueOf(features[2]), this, this.scheduler));
        }
        else if(features[0].equals("link")) {

            Node n1 = null;
            Node n2 = null;

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

            int queueSize;
            int bandwidth;
            int latency;

            try {
                queueSize = Integer.valueOf(features[3]);
                bandwidth = Integer.valueOf(features[4]);
                latency = Integer.valueOf(features[5]);
            } catch (NumberFormatException e) {
                System.out.println("File format error: not numbers");
                return false;
            }
            Link newLink1 = new Link(n1, queueSize, bandwidth, latency, this.scheduler);
            Link newLink2 = new Link(n2, queueSize, bandwidth, latency, this.scheduler);
            links.add(newLink1);
            links.add(newLink2);
            n1.addLink(n2, newLink2);
            n2.addLink(n1, newLink1);

        }
        else {
            System.out.println("File format error");
            return false;
        }
        
        return true;
    }
 
    public void printTopo() {

        System.out.println("Hosts: ");
        for (Node node : hosts) {
            System.out.println(node);
        }
        System.out.println("Routers: ");
        for (Node node : routers) {
            System.out.println(node);
        }
        System.out.println("Links: ");
        for (Link link : links) {
            System.out.println(link);
        }

    }

    /**
     * Use BFS to build routing tables for every node, starts at the first host
     */
    private void buildRoutingTables() {
        Node[] nodeList = new Node[hosts.size() + routers.size()];
        // System.out.println("hosts: ");
        for(int i = 0; i < hosts.size(); i++) {
            nodeList[i] = hosts.get(i);
            // System.out.println(nodeList[i]);
        }
        // System.out.println("routers: ");
        for(int i = 0; i < routers.size(); i++) {
            nodeList[hosts.size() + i] = routers.get(i);
            // System.out.println(nodeList[i]);
        }
        // System.out.println(" ------ end --------");
        for(Node host: hosts) {
            host.buildRoutingTable(nodeList);
            host.printTable();
        }
        for(Node router: routers) {
            router.buildRoutingTable(nodeList);
            router.printTable();
        }


    }
}
