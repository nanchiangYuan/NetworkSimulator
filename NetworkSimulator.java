import java.util.Scanner;

public class NetworkSimulator {

    private static SimpleNetwork network;

    private static final int DROP_RATE_BASE_DEFAULT = 0;
    private static final int BANDWIDTH_BASE_DEFAULT = 0;
    private static final int LATENCY_BASE_DEFAULT = 0;

    private static String[] COMMANDS = {"run", "config", "setup", "exit"};
    private static String[] RUN_CONFIG = {"latency", "bandwidth", "queuesize"};

    private static Scheduler scheduler;

    // params for run:
    //           -l (latency)
    //           -b (bandwidth)
    //           -q (queue size)
    //           [none] all


    public static void main(String args[]) {

        Scanner in = new Scanner(System.in);
        

        while(true) {
            System.out.print("NetworkEmulator> ");
            String input = in.nextLine();
            String[] inputSplit = input.split("\\s+");
            
            // run
            if(inputSplit[0].equals(COMMANDS[0])) {
                if(network == null)
                    System.out.println("Network not set.");
                else if(inputSplit.length == 1) {
                    run();
                }
                else if(inputSplit.length > 2) {
                    System.out.println("invalid run command");
                }
                else {
                    if(inputSplit[1].equals("-l")) {
                        // run changing latency
                    }
                }
            }

            // config
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

            // exit
            else if(inputSplit[0].equals(COMMANDS[3]))
                break;

            else {
                System.out.println("Unknown command. ");
                System.out.println("This is a list of commands: ");
                System.out.println("1. run: starts the tests");
                System.out.println("2. setup [filename]: sets up the network based on topology in file");
                System.out.println("3. config: shows the configuration of the network and topology");
                System.out.println("4. exit: exits the emulator");
            }

        }
        
        in.close();
    }

    private static void run() {
        scheduler = new Scheduler();
        
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
