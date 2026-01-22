import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

import Event.EventType;

public class NetworkSimulator {

    private static SimpleNetwork network;

    private static String[] COMMANDS = {"run", "showconfig", "setup", "exit", "help", "setuptcp"};
    private static String[] RUN_CONFIG = {"latency", "bandwidth", "queuesize"};

    private static final int DEFAULT_FILE_SIZE = 200; // in KB
    private static final int DEFAULT_MTU = 1500; // in bytes
    private static final int DEFAULT_SWS = 10; // number of segments

    private static final String INITIAL_FILE_NAME = "test_original.txt";
    private static final String LATENCY_FILE_NAME = "test_latency_";
    private static final String BANDWIDTH_FILE_NAME = "test_bandwidth_";
    private static final String BUFFERSIZE_FILE_NAME = "test_buffersize_";
    private static final String FILE_NAME_EXTENSION = ".txt";

    private static final int LATENCY_TEST_NO = 0;
    private static final int BANDWIDTH_TEST_NO = 1;
    private static final int BUFFERSIZE_TEST_NO = 2;

    private static Scheduler scheduler;

    record TestConfig(short sourceID, short destID, Link[] links, int filesize, String filename, int test, int stepSize, int testCount, int mtu, int sws) {}

    public static void main(String args[]) {

        int fileSize = DEFAULT_FILE_SIZE;
        int mtu = DEFAULT_MTU;
        int sws = DEFAULT_SWS;

        Scanner in = new Scanner(System.in);
        

        while(true) {
            System.out.print("NetworkEmulator> ");
            String input = in.nextLine();
            String[] inputSplit = input.split("\\s+");
            
            // run
            if(inputSplit[0].equals(COMMANDS[0])) {
                if(network == null)
                    System.out.println("Network not set, type \"help\" for list of commands.");
                else if(inputSplit.length < 4) {
                    System.out.println("Invalid run command, type \"help\" for list of commands.");
                }
                else {
                    short startID = -1;
                    short destID = -1;
                    String filename = null;
                    int testname = -1;
                    int stepsize = -1;
                    int numberOfTests = -1;
                    Link[] links = null;
                    for(int i = 1; i < inputSplit.length; i+=2) {
                        if(i+1 >= inputSplit.length) {
                            System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                            break;
                        }
                        if(inputSplit[i].equals("-s")) {
                            try{
                                startID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-d")) {
                            try{
                                destID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-f")) {
                            try{
                                fileSize = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-n")) {
                            filename = inputSplit[i+1];
                        }
                        if(inputSplit[i].equals("-t")) {
                            if(inputSplit[i+1].equals("latency"))
                                testname = LATENCY_TEST_NO;
                            else if(inputSplit[i+1].equals("bandwidth"))
                                testname = BANDWIDTH_TEST_NO;
                            else if(inputSplit[i+1].equals("buffersize"))
                                testname = BUFFERSIZE_TEST_NO;
                            else {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                                
                        }
                        if(inputSplit[i].equals("-p")) {
                            try{
                                stepsize = Integer.parseInt(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-c")) {
                            try{
                                numberOfTests = Integer.parseInt(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-l")) {
                            String[] nodes = inputSplit[i+1].split(":");
                            links = findLink(nodes[0], nodes[1]);
                            if(links == null) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                    }

                    if(startID == -1 || destID == -1 || testname == -1 || stepsize == -1) {
                        System.out.println("Invalid run command, type \"help\" for list of commands.");
                        break;
                    }
                        
                    if(numberOfTests == -1)
                        numberOfTests = 3;

                    if(links == null) {
                        System.out.println("Invalid run command, type \"help\" for list of commands.");
                        break;
                    }
                    
                    TestConfig testConfig = new TestConfig(startID, destID, links, fileSize, filename, testname, stepsize, numberOfTests, mtu, sws);
                    run(testConfig);
                }
            }

            // showconfig
            else if(inputSplit[0].equals(COMMANDS[1])) {
                // print out network config
                network.printTopo();
            }
            
            // setup
            else if(inputSplit[0].equals(COMMANDS[2])) {
                if(inputSplit.length == 2) {
                    network = new SimpleNetwork(inputSplit[1], scheduler);
                }
                else {
                    System.out.println("invalid setup command");
                }

            }
            // help
            else if(inputSplit[0].equals(COMMANDS[4])) {
                if(inputSplit.length == 1) {
                    System.out.println("Commands: ");
                    System.out.println("  1. run: starts the tests");
                    System.out.println("     flags: -s start host ID");
                    System.out.println("            -d destination host ID");
                    System.out.println("            -l link to test on, use start and end node of link to specify the link, example: \"r1:r2\"");
                    System.out.println("            -t test to run, \"latency\", \"bandwidth\", \"buffersize\"");
                    System.out.println("            -p step size (unit same as the units of each parameter)");
                    System.out.println("            -c number of tests to run (default is 3 if not provided)");
                    System.out.println("            -f file size to be sent (in KB) (if none provided, default is 200KB)");
                    System.out.println("            -n name of file to be sent (if none provided, will create one)");
                    System.out.println("  2. setup [filename]: sets up the network based on topology in file");
                    System.out.println("  3. settcp: sets some parameters for TCP");
                    System.out.println("            -m sets maximum transmission unit in bytes");
                    System.out.println("  4. showconfig: shows the configuration of the network and topology");
                    System.out.println("  5. exit: exits the emulator");
                }
                else {
                    System.out.println("Invalid help command, type \"help\" for list of commands.");
                }

            }
            // setuptcp
            else if(inputSplit[0].equals(COMMANDS[5])){
                if(inputSplit.length > 2) {
                    for(int i = 1; i < inputSplit.length; i+=2) {
                        if(i+1 > inputSplit.length) {
                            System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                            break;
                        }
                        if(inputSplit[i].equals("-m")) {
                            try{
                                mtu = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                    }
                }
                else {
                    System.out.println("invalid setup command");
                }

            }

            // exit
            else if(inputSplit[0].equals(COMMANDS[3]))
                break;

            else {
                System.out.println("Unknown command, type \"help\" for list of commands.");
            }

        }
        
        in.close();
    }

    private static void run(TestConfig testConfig) {
        String newFile;

        if(testConfig.filename == null) {
            createFile(testConfig.filesize);
            newFile = INITIAL_FILE_NAME;
        }
        else {
            newFile = testConfig.filename;
        }

        runTests(testConfig, newFile, testConfig.test);

    }
    

    private static void runTests(TestConfig testConfig, String filename, int testNo) {

        // set up file name for specific tests
        String filePrefix = "";
        switch(testNo) {
            case LATENCY_TEST_NO:
                filePrefix = LATENCY_FILE_NAME;
                break;
            case BANDWIDTH_TEST_NO:
                filePrefix = BANDWIDTH_FILE_NAME;
                break;
            case BUFFERSIZE_TEST_NO:
                filePrefix = BUFFERSIZE_FILE_NAME;
                break;
        }

        for(int i = 1; i <= testConfig.testCount; i++) {
            String outputFilename = filePrefix + i + FILE_NAME_EXTENSION;

            scheduler = new Scheduler();
            TCPsender sender = new TCPsender(testConfig.sourceID, testConfig.destID, filename, testConfig.mtu, testConfig.sws, scheduler);
            TCPrecver receiver = new TCPrecver(testConfig.sourceID, outputFilename, testConfig.mtu, testConfig.sws); 

            
            /**
             * flow:
             * 1. sender init connection
             * 2. go into while loop, get event from queue, get global time
             * 3. do what the event is: 
             *      arrive: check which end node, if not, send onto link
             *              if so, do the end node logic
             *              might need to restructure tcp sender and recver, tcp sender may need a counter to send packets individually and not in a loop
             *              also when sender sends, add a timeout event onto queue
             *      timeout: check if the ack was received, if not, add a new event onto queue
             * 4. go back to 2.
             * 
             * So sender init connection by sending a packet ie calling node.send, 
             * then node calls link.send which puts the arrival event on the queue. 
             * then when queue is polled, the event is executed by calling node.send of the arrival node, 
             * then link.send again until the arrival node is the end node. 
             * Then i call tcp receiver's receive function to process the packet. 
             */

            sender.initConnection();    // only sends first handshake
            while(!scheduler.getQueue().isEmpty()) {
                Event currEvent = scheduler.runSchedule();
                if(currEvent.getType() == Event.EventType.ARRIVE) {
                    if(currEvent.getDestination().getID() == currEvent.getPacket().getDestinationID()) {
                        receiver.receivePacket(null, outputFilename);
                    }
                    else {
                        // send packet down to the next node
                    }
                }
                if(currEvent.getType() == Event.EventType.TIMEOUT_CHECK) {
                    // get a data structure from sender that records if a packet received an ack
                    // if received, just continue, if not, send packet again
                }

            }


            configureLinks(testConfig.links, testConfig.stepSize, testNo);

        }
        
    }

    private static void configureLinks(Link[] links, int stepSize, int testNo) {
        switch(testNo) {
            case LATENCY_TEST_NO:
                int initialLatency = links[0].getLatency();
                links[0].setLatency(initialLatency + stepSize);
                links[1].setLatency(initialLatency + stepSize);
                break;
            case BANDWIDTH_TEST_NO:
                int initialBandwidth = links[0].getBandwidth();
                links[0].setLatency(initialBandwidth + stepSize);
                links[1].setLatency(initialBandwidth + stepSize);
                break;
            case BUFFERSIZE_TEST_NO:
                int initialBuffer = links[0].getQueueSize();
                links[0].setLatency(initialBuffer + stepSize);
                links[1].setLatency(initialBuffer + stepSize);
                break;
            default:
                return;
            
        }
    }

    private static void createFile(int filesize) {
        try{
            FileOutputStream f = new FileOutputStream(INITIAL_FILE_NAME);
            byte[] b = new byte[filesize * 1024];
            Random randomgen = new Random();
            randomgen.nextBytes(b);
            f.write(b);
            f.close();
        } catch(IOException e) {
            System.out.println("Error when creating initial file.");
        }

    }

    private static Link[] findLink(String n1, String n2) {
        Node node1 = null;
        Node node2 = null;
        for(Node node: network.getHosts()) {
            if(node.getName().equals(n1))
                node1 = node;
            else if(node.getName().equals(n2))
                node2 = node;
        }
        for(Node node: network.getRouters()) {
            if(node.getName().equals(n1))
                node1 = node;
            else if(node.getName().equals(n2))
                node2 = node;
        }
        if(node1 == null || node2 == null)
            return null;

        // for both sides
        Link[] targetLink = new Link[2];

        for(Link link: network.getLinks()) {
            if(link.getStartNode().equals(node1) && link.getEndNode().equals(node2))
                targetLink[0] = link;
            if(link.getStartNode().equals(node2) && link.getEndNode().equals(node1)) 
                targetLink[1] = link;
        }

        if(targetLink[0] == null || targetLink[1] == null)
            return null;

        return targetLink;
    }
    
}

/**
 * To do:
 * O 1. change run flags to:
 *      user specify which test to run, specify step size, specify number of tests
 * 2. figure out how to run the tests, who call who and grab event from queue, 4.
 * 3. add congestion control
 * 4. go through tcpsender and recver to make sure it's adapted to the current simulator
 * 5. make print statements to show the test results
 */
