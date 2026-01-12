import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class NetworkEmulator {

    private static ArrayList<Node> hosts;
    private static ArrayList<Node> routers;
    private static ArrayList<Link> links;

    private static boolean topoSet = false;

    private static final int DROP_RATE_BASE_DEFAULT = 0;
    private static final int BANDWIDTH_BASE_DEFAULT = 0;
    private static final int LATENCY_BASE_DEFAULT = 0;

    public static void main(String args[]) {

        Scanner in = new Scanner(System.in);

        while(true) {
            System.out.print("NetworkEmulator> ");
            String input = in.nextLine();
            String[] inputSplit = input.split("\\s+");

            if(inputSplit[0].equals("run")) {
                if(!topoSet)
                    System.out.println("Network not set.");
                else if(inputSplit.length == 1) {
                    run();
                }
                else {
                    // parse run command
                }
            }

            else if(inputSplit[0].equals("config")) {
                // print out network config
            }

            else if(inputSplit[0].equals("setup")) {
                if(inputSplit.length == 2) {
                    setUpTopology(inputSplit[1]);
                }
                else {
                    System.out.println("invalid setup command");
                }

            }

            else if(inputSplit[0].equals("exit"))
                break;

            else {
                System.out.println("Unknown command. ");
                System.out.println("This is a list of commands: ");
                System.out.println("run: ...");
            }

        }
        
        in.close();
    }

    /**
     * sets up the topology of hosts and routers
     * @param mode 0 for default, linear topology with 2 hosts and 1 router, 1 for custom topo from file
     */
    private static void setUpTopology(String filename) {

        // clear out old info first
        hosts = new ArrayList<>();
        routers = new ArrayList<>();
        links = new ArrayList<>();

        File topoFile = new File(filename);
        boolean error = false;

        try (Scanner readFile = new Scanner(topoFile)){
            while(readFile.hasNextLine()) {
                String line = readFile.nextLine();
                String[] split = line.split("\\s+");
                if(!setUpHelper(split)) {
                    error = true;
                    break;
                }
            }
        } catch (FileNotFoundException E) {
            System.out.println("File does not exit");
            return;
        } 

        if(!error)
            topoSet = true;

        buildRoutingTables();
        // printTopo();

    }

    private static boolean setUpHelper(String[] features) {
        if(features[0].equals("host")) {
            hosts.add(new Node(features[1], Short.valueOf(features[2])));
        }
        else if(features[0].equals("router")) {
            routers.add(new Node(features[1], Short.valueOf(features[2])));
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
            Link newLink = new Link(n1, n2, queueSize, bandwidth, latency);
            links.add(newLink);
            n1.addLink(n2, newLink);
            n2.addLink(n1, newLink);

        }
        else {
            System.out.println("File format error");
            return false;
        }
        
        return true;
    }
 
    private static void printTopo() {

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
    private static void buildRoutingTables() {
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

    private static void run() {

    }
    
}

/**
 * commands:
 * run bandwidth
 * run queueSize
 * run latency
 * setup [topoFile]: 
 *      topoFile: file for topology (for now make one for dumbbell topo) and the parameters for the links
 * config: print configurations for network (topo and links)
 * exit: exits program
 */
