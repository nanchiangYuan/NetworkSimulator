import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class NetworkSimulator {

    private static SimpleNetwork network;

    private static String[] COMMANDS = {"run", "showconfig", "setup", "exit", "help", "setuptcp"};

    private static final int DEFAULT_FILE_SIZE = 1000;           // in KB
    private static final int DEFAULT_MTU = 1500;                // in bytes
    private static final int DEFAULT_RECV_BUFFER_SIZE = 20;     // in number of segments

    private static final String INITIAL_FILE_NAME = "test_original.txt";
    private static final String LATENCY_FILE_NAME = "test_latency_";
    private static final String BANDWIDTH_FILE_NAME = "test_bandwidth_";
    private static final String BUFFERSIZE_FILE_NAME = "test_buffersize_";
    private static final String LOSSRATE_FILE_NAME = "test_lossrate_";
    private static final String FILE_NAME_EXTENSION = ".txt";

    private static final int LATENCY_TEST_NO = 0;
    private static final int BANDWIDTH_TEST_NO = 1;
    private static final int BUFFERSIZE_TEST_NO = 2;
    private static final int LOSSRATE_TEST_NO = 3;

    private static final double[] LATENCY_STEP = {1, 5, 10, 25, 50, 100};             // by ms
    private static final double[] BANDWIDTH_STEP = {1, 10, 30, 50, 75, 100};          // by 
    private static final double[] BUFFERSIZE_STEP = {3, 5, 10, 30, 50, 80};               // by packets
    private static final double[] LOSSRATE_STEP = {0.0, 0.01, 0.1, 0.5, 1, 2, 5};     // percentage

    private static Scheduler scheduler;

    private record TestConfig(
        short sourceID, 
        short destID, 
        Node sourceNode, 
        Node destNode, 
        Link[] links, 
        int filesize, 
        String filename, 
        int testName, 
        double[] steps,
        int mtu, 
        int rcvBufSize, 
        boolean verbose
    ) {}

    public static void main(String args[]) {

        int fileSize = DEFAULT_FILE_SIZE;
        int mtu = DEFAULT_MTU;
        int rcvBufSize = DEFAULT_RECV_BUFFER_SIZE;

        Scanner in = new Scanner(System.in);

        while(true) {
            System.out.print("NetworkEmulator> ");
            String input = in.nextLine();
            String[] inputSplit = input.split("\\s+");
            
            /*
             * run
             */
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
                    Link[] links = null;
                    boolean verbose = false;
                    double[] steps = null;
                    boolean originalLink = false;

                    for(int i = 1; i < inputSplit.length; i++) {
                        if(inputSplit[i].equals("-s") && i+1 <= inputSplit.length) {
                            try{
                                startID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("-s Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }
                        }
                        if(inputSplit[i].equals("-d") && i+1 <= inputSplit.length) {
                            try{
                                destID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("-d Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }
                        }
                        if(inputSplit[i].equals("-f") && i+1 <= inputSplit.length) {
                            try{
                                fileSize = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("-f Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }
                        }
                        if(inputSplit[i].equals("-n") && i+1 <= inputSplit.length) {
                            filename = inputSplit[i+1];
                        }
                        if(inputSplit[i].equals("-t") && i+1 <= inputSplit.length) {
                            if(inputSplit[i+1].equals("latency"))
                                testname = LATENCY_TEST_NO;
                            else if(inputSplit[i+1].equals("bandwidth"))
                                testname = BANDWIDTH_TEST_NO;
                            else if(inputSplit[i+1].equals("buffersize"))
                                testname = BUFFERSIZE_TEST_NO;
                            else if(inputSplit[i+1].equals("lossrate"))
                                testname = LOSSRATE_TEST_NO;
                            else {
                                System.out.println("-t Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }
                        }
                        if(inputSplit[i].equals("-r") && i+1 <= inputSplit.length) {
                            String[] numbers = inputSplit[i+1].split(":");
                            if(numbers.length != 3) {
                                System.out.println("-r Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }

                            double[] range = new double[3];

                            for(int j = 0; j < 3; j++) {
                                try{
                                    range[j] = Double.parseDouble(numbers[j]);
                                } catch (NumberFormatException e) {
                                    System.out.println("-r Invalid run command, type \"help\" for list of commands.");
                                    continue;
                                }
                            }
                            steps = getRange(range[0], range[1], range[2]);

                        }
                        if(inputSplit[i].equals("-l") && i+1 <= inputSplit.length) {
                            String[] nodes = inputSplit[i+1].split(":");
                            links = findLink(nodes[0], nodes[1]);
                            if(links == null) {
                                System.out.println("-l Invalid run command, type \"help\" for list of commands.");
                                continue;
                            }
                        }
                        if(inputSplit[i].equals("-v")) {
                            verbose = true;
                        }
                        if(inputSplit[i].equals("-c")) {
                            originalLink = true;
                        }
                    }

                    if(startID == -1 || destID == -1 || testname == -1) {
                        System.out.println("aa Invalid run command, type \"help\" for list of commands.");
                        continue;
                    }

                    if(steps == null) {
                        switch(testname) {
                            case LATENCY_TEST_NO:
                                steps = LATENCY_STEP;
                                break;
                            case BANDWIDTH_TEST_NO:
                                steps = BANDWIDTH_STEP;
                                break;
                            case BUFFERSIZE_TEST_NO:
                                steps = BUFFERSIZE_STEP;
                                break;
                            case LOSSRATE_TEST_NO:
                                steps = LOSSRATE_STEP;
                                break;
                        }
                    }

                    if(links == null) {
                        System.out.println("bb Invalid run command, type \"help\" for list of commands.");
                        continue;
                    }

                    if(!originalLink)
                        setDefaultLinkConfig(links, mtu);
                    else
                        resetLinks();

                    TestConfig testConfig = new TestConfig(startID, destID, network.getNodeFromID(startID), network.getNodeFromID(destID), links, fileSize, filename, testname, steps, mtu, rcvBufSize, verbose);
                    run(testConfig);
                }
            }

            /*
             * showconfig
             */
            else if(inputSplit[0].equals(COMMANDS[1])) {
                // print out network config
                network.printTopo();
            }
            
            /*
             * setup
             */
            else if(inputSplit[0].equals(COMMANDS[2])) {
                if(inputSplit.length == 2) {
                    network = new SimpleNetwork(inputSplit[1], scheduler);
                }
                else {
                    System.out.println("invalid setup command");
                }

            }
            /*
             * help
             */
            else if(inputSplit[0].equals(COMMANDS[4])) {
                if(inputSplit.length == 1) {
                    System.out.println("Commands: ");
                    System.out.println("  1. setup [filename]: sets up the network based on topology in file");
                    System.out.println("  2. run: starts the tests, if no range set, the tests will be run in default configurations");
                    System.out.println("     flags: <REQUIRED>");
                    System.out.println("              -s start host ID");
                    System.out.println("              -d destination host ID");
                    System.out.println("              -l link to do sweep test on, use start and end node of link to specify the link, example: \"r1:r2\"");
                    System.out.println("              -t test to run, \"latency\", \"bandwidth\", \"buffersize\", \"lossrate\"");
                    System.out.println("            <OPTIONAL>");
                    System.out.println("              -r range and step size (example: \"10:100:20\" starts at 10, ends at 100 with step size of 20)");
                    System.out.println("              -f file size to be sent (in KB) (if none provided, default is 1MB)");
                    System.out.println("              -n name of file to be sent (if none provided, will create one)");
                    System.out.println("              -c run tests with original link configs");
                    System.out.println("              -v verbose mode");
                    System.out.println("  3. settcp: sets some parameters for TCP");
                    System.out.println("              -m sets maximum transmission unit in bytes");
                    System.out.println("              -b sets receiver buffer size in segments");
                    System.out.println("  4. showconfig: shows the configuration of the network and topology");
                    System.out.println("  5. exit: exits the emulator");
                }
                else {
                    System.out.println("Invalid help command, type \"help\" for list of commands.");
                }

            }
            
            /*
             * setuptcp
             */
            else if(inputSplit[0].equals(COMMANDS[5])){
                if(inputSplit.length > 2) {
                    for(int i = 1; i < inputSplit.length; i+=2) {
                        if(i+1 > inputSplit.length) {
                            System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                            break;
                        }
                        if(inputSplit[i].equals("-m")) {
                            try{
                                mtu = Integer.parseInt(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                                break;
                            }
                        }
                        if(inputSplit[i].equals("-b")) {
                            try{
                                rcvBufSize = Integer.parseInt(inputSplit[i+1]);
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

            /*
             * exit
             */
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

        runTests(testConfig, newFile, testConfig.testName);

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
            case LOSSRATE_TEST_NO:
                filePrefix = LOSSRATE_FILE_NAME;
                break;
        }

        for(int i = 0; i < testConfig.steps.length; i++) {

            configureLinks(testConfig.links, testConfig.steps, testConfig.mtu, i, testNo);

            printHeader(testConfig, i);
            System.out.println();

            if(testConfig.verbose)
                System.out.println("Trace: ");

            String outputFilename = filePrefix + i + FILE_NAME_EXTENSION;

            scheduler = new Scheduler();
            TCPsender sender = new TCPsender(testConfig.sourceID, testConfig.destID, testConfig.sourceNode, filename, testConfig.mtu, scheduler, testConfig.verbose);
            TCPrecver receiver = new TCPrecver(testConfig.destID, testConfig.sourceID, testConfig.destNode, outputFilename, testConfig.mtu, testConfig.rcvBufSize, scheduler, testConfig.verbose); 

            setUpLink();
    
            receiver.listen();
            sender.initConnection();    // only sends first handshake

            while(!scheduler.getQueue().isEmpty()) {

                Event currEvent = scheduler.run();     
                
                if(currEvent.getType() == Event.EventType.ARRIVE) {

                    // depending on whether the packet arrives at the source or destination, TCP sender or receiver is called
                    if(currEvent.getDestination().getID() == testConfig.destID) {
                        receiver.receive(currEvent.getPacket());
                    }
                    else if(currEvent.getDestination().getID() == testConfig.sourceID) {
                        sender.receive(currEvent.getPacket());
                        // if ack for threeway handshake is received, start sending packets, done in TCPsender
                    }
                    // otherwise, just send packet down to the next node
                    else {
                        currEvent.getDestination().send(currEvent.getPacket());
                    }
                }

                if(currEvent.getType() == Event.EventType.TIMEOUT_CHECK) {
                    sender.checkTimeout(currEvent.getSequenceNo());
                    // get a data structure from sender that records if a packet received an ack
                    // if received, just continue, if not, send packet again
                }
            }

            
            if(testConfig.verbose)
                System.out.println("End of Trace");
            System.out.println();
            sender.getStat().printStat();
            System.out.println();
            receiver.getStat().printStat();
            System.out.println();
            TCPFinalStat finalStat = new TCPFinalStat(sender.getStat(), receiver.getStat());
            finalStat.printFinalStat();
            System.out.println();
            if(!checkFile(filename, outputFilename))
                System.out.println("!!! file transfer failed: files are not the same !!!");
        }
        
    }

    private static void setUpLink() {
        for(Link link: network.getLinks()) {
            link.setScheduler(scheduler);
            link.reset();
        }
    }

    private static void resetLinks() {
        for(Link link: network.getLinks()) {
            link.resetConfig();
        }
    }

    private static void configureLinks(Link[] links, double[] steps, int mtu, int index, int testNo) {
        switch(testNo) {
            case LATENCY_TEST_NO:
                links[0].setLatency(steps[index]);
                links[1].setLatency(steps[index]);
                break;
            case BANDWIDTH_TEST_NO:
                links[0].setBandwidth(steps[index]);
                links[1].setBandwidth(steps[index]);
                break;
            case BUFFERSIZE_TEST_NO:
                links[0].setBufferSize(steps[index] * mtu);
                links[1].setBufferSize(steps[index] * mtu);
                break;
            case LOSSRATE_TEST_NO:
                links[0].setLossRate(steps[index]);
                links[1].setLossRate(steps[index]);
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

    public static double[] getRange(double start, double end, double step) {

        int testCount = (int) Math.ceil((end - start) / step);
        double[] steps = new double[testCount+1];
        for(int k = 0; k < steps.length - 1; k++) {
            steps[k] = start + k * step;                                    
        }
        steps[steps.length - 1] = end;

        return steps;
    }

    public static void setDefaultLinkConfig(Link[] bottleneckLinks, int mtu) {

        for(Link link: network.getLinks()) {
            // non-bottleneck links
            if(link != bottleneckLinks[0] && link != bottleneckLinks[1]) {
                link.setBandwidth(1000.0);
                link.setLatency(1);
                link.setBufferSize(10000000);   // 10 MB
                link.setLossRate(0.0);
            }
            else {
                link.setBandwidth(10.0);
                link.setLatency(20);
                link.setBufferSize(50 * mtu);
                link.setLossRate(0.0);
            }
        }
    }

    private static String convert(double num) {
        if(num / 1000.0 > 1.0) {
            num /= 1000.0;
            if(num / 1000.0 > 1.0)
                return "" + num / 1000.0 + " MB";
            return "" + num + " KB";
        }
        else
            return "" + num + " B";

    }

    private static boolean checkFile(String fileName1, String fileName2) {

        try {
            File file1 = new File(fileName1);
            File file2 = new File(fileName2);

            if(file1.length() != file2.length())
                return false;
            InputStream in1 = new FileInputStream(file1);
            InputStream in2 = new FileInputStream(file2);
            
            byte[] buffer1 = new byte[1024];
            byte[] buffer2 = new byte[1024];
            int len1;

            while((len1 = in1.read(buffer1)) != -1) {
                int len2 = in2.read(buffer2);
                if(len2 == -1 || !Arrays.equals(buffer1, buffer2)) {
                    in1.close();
                    in2.close();
                    return false;
                }
            }
            in1.close();
            in2.close();

        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        } catch (IOException e) {
            System.out.println("File read error");
        }

        return true;
    }

    private static void printHeader(TestConfig testConfig, int index) {

        int headerLength = 64;

        String line = "=";
        String separator = line.repeat(headerLength);

        String spaces = " ";

        System.out.println(separator);

        String title = "";

        switch(testConfig.testName) {
            case 0:
                title = "Latency Test " + index + " (" + testConfig.links[0].getLatency() + " ms)";
                int len = title.length();
                int spacesLen = (headerLength - len) / 2;
                title = spaces.repeat(spacesLen) + title + spaces.repeat(spacesLen);
                break;
            case 1:
                title = "Bandwidth Test " + index + " (" + testConfig.links[0].getBandwidth() + " Mbps)";
                len = title.length();
                spacesLen = (headerLength - len) / 2;
                title = spaces.repeat(spacesLen) + title + spaces.repeat(spacesLen);
                break;
            case 2:
                title = "Buffer Size Test " + index + " (" + convert(testConfig.links[0].getBufferSize()) + ")";
                len = title.length();
                spacesLen = (headerLength - len) / 2;
                title = spaces.repeat(spacesLen) + title + spaces.repeat(spacesLen);
                break;
            case 3:
                title = "Loss Rate Test " + index + " (" + testConfig.links[0].getLossRate() + "%)";
                len = title.length();
                spacesLen = (headerLength - len) / 2;
                title = spaces.repeat(spacesLen) + title + spaces.repeat(spacesLen);
                break;
        }
        System.out.println(title);
        System.out.println(separator);

        System.out.println("Bottleneck Link Configuration: ");    
        System.out.println("    Latency:                                           " + testConfig.links[0].getLatency() + " ms");
        System.out.println("    Bandwidth:                                         " + testConfig.links[0].getBandwidth() + " Mbps");
        System.out.println("    Buffer Size:                                       " + convert(testConfig.links[0].getBufferSize()));
        System.out.println("    Loss Rate:                                         " + testConfig.links[0].getLossRate() + " %");
    }
    
}

