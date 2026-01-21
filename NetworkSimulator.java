import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class NetworkSimulator {

    private static SimpleNetwork network;

    private static String[] COMMANDS = {"run", "showconfig", "setup", "exit", "help", "setuptcp"};
    private static String[] RUN_CONFIG = {"latency", "bandwidth", "queuesize"};

    private static final int DEFAULT_FILE_SIZE = 200; // in KB
    private static final int DEFAULT_MTU = 1500; // in bytes
    private static final int DEFAULT_SWS = 10; // number of segments

    private static final String INITIAL_FILE_NAME = "test_original.txt";

    private static Scheduler scheduler;

    // params for run:
    //           -s start host
    //           -d destination host
    //           -f file size to be sent
    //           -l (latency)
    //           -b (bandwidth)
    //           -q (queue size)
    //           [none] all

    record TestConfig(short sourceID, short destID, int filesize, String filename, boolean latencyTest, boolean bandwidthTest, boolean bufferTest, int mtu, int sws) {}


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
                    boolean latencyTest = false;
                    boolean bandwidthTest = false;
                    boolean bufferTest = false;
                    boolean specifiedTest = false;
                    for(int i = 1; i < inputSplit.length; i+=2) {
                        if(i+1 > inputSplit.length) {
                            System.out.println("Invalid setuptcp command, type \"help\" for list of commands.");
                            break;
                        }
                        if(inputSplit[i].equals("-s")) {
                            try{
                                startID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                            }
                        }
                        if(inputSplit[i].equals("-d")) {
                            try{
                                destID = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                            }
                        }
                        if(inputSplit[i].equals("-f")) {
                            try{
                                fileSize = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                            }
                        }
                        if(inputSplit[i].equals("-n")) {
                            try{
                                //fileSize = Short.parseShort(inputSplit[i+1]);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid run command, type \"help\" for list of commands.");
                            }
                        }
                        if(inputSplit[i].equals("-l")) {
                            latencyTest = true;
                            specifiedTest = true;
                        }
                        if(inputSplit[i].equals("-b")) {
                            bandwidthTest = true;
                            specifiedTest = true;
                        }
                        if(inputSplit[i].equals("-q")) {
                            bufferTest = true;
                            specifiedTest = true;
                        }
                    }
                    if(!specifiedTest) {
                        latencyTest = true;
                        bandwidthTest = true;
                        bufferTest = true;
                    }
                    if(startID == -1 || destID == -1)
                        System.out.println("Invalid run command, type \"help\" for list of commands.");

                    TestConfig testConfig = new TestConfig(startID, destID, fileSize, filename, latencyTest, bandwidthTest, bufferTest, mtu, sws);
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
                    System.out.println("            -f file size to be sent (in KB) (if none provided, default is 200KB)");
                    System.out.println("            -n name of file to be sent (if none provided, will create one)");
                    System.out.println("            -l run tests with different latency values");
                    System.out.println("            -b run tests with different bandwidths");
                    System.out.println("            -q run tests with different buffer sizes");
                    System.out.println("            if no -l ,-b, -q flags set, runs all three");
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
        scheduler = new Scheduler();
        String newFile;


        if(testConfig.filename == null) {
            createFile(testConfig.filesize);
            newFile = INITIAL_FILE_NAME;
        }
        else {
            newFile = testConfig.filename;
        }

        TCPsender sender = new TCPsender(testConfig.sourceID, testConfig.destID, newFile, testConfig.mtu, testConfig.sws);
        TCPsender receiver = new TCPsender(testConfig.sourceID, testConfig.destID, newFile, testConfig.mtu, testConfig.sws);

        sender.run();
        
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
