public class TCPend {

    /**
     * This main function parses command line argument, calls sender and receiver code, 
     * and prints out the final stats.
     * @param args
     */
    public static void main(String args[]) {

        int port = -1;
        String remoteIP = null;
        int remotePort = -1;
        String filename = null;
        int mtu = -1;
        int sws = -1;

        if(args.length != 12 && args.length != 8) {
            System.out.println("wrong args");
            System.out.println(args.length);
            return;
        }

        boolean isSender = false;

        try {
            for(int i = 0; i < args.length; i+=2) {
                if(args[i].equals("-p"))
                    port = Integer.parseInt(args[i+1]);
                else if(args[i].equals("-s")) {
                    remoteIP = args[i+1];
                    isSender = true;
                }
                else if(args[i].equals("-a"))
                    remotePort = Integer.parseInt(args[i+1]);
                else if(args[i].equals("-f"))
                    filename = args[i+1];
                else if(args[i].equals("-m"))
                    mtu = Integer.parseInt(args[i+1]);
                else if(args[i].equals("-c"))
                    sws = Integer.parseInt(args[i+1]);
            }
        }
        catch (NumberFormatException e) {
            System.out.println("args format error");
            return;
        }
        
        TCPsender sender;
        TCPrecver receiver;
        int[] result = null;

        if(isSender) {
            if(port == -1 || remoteIP == null || remotePort == -1 || filename == null || mtu == -1 || sws == -1) {
                System.out.println("missing args for sender");
                return;                
            }
            else {
                sender = new TCPsender(port, remoteIP, remotePort, filename, mtu, sws);
                sender.run();
                result = sender.returnStats();
                System.out.println("Sender Stats: ");
                System.out.println("Amount of data transferred: " + result[1]);
                System.out.println("Amount of data received: " + result[0]);
                System.out.println("Number of packets sent: " + result[3]);
                System.out.println("Number of packets received: " + result[2]);
                System.out.println("Number of retransmissions: " + result[4]);
                System.out.println("Number of duplicate acknowledgements: " + result[5]);
            }
        }
        else {
            if(port == -1 || filename == null || mtu == -1 || sws == -1) {
                System.out.println("missing args for receiver");  
                return;
            }
            else {
                receiver = new TCPrecver(port, filename, mtu, sws);
                receiver.run();
                result = receiver.returnStats();
                System.out.println("Receiver Stats: ");
                System.out.println("Amount of data transferred: " + result[1]);
                System.out.println("Amount of data received: " + result[0]);
                System.out.println("Number of packets sent: " + result[3]);
                System.out.println("Number of packets received: " + result[2]);
                System.out.println("Number of out-of-sequence packets discarded: " + result[5]);
                System.out.println("Number of packets discarded due to incorrect checksum: " + result[4]);
            }
        }

    }
}


/**
 * The idea:
 *  - TCPsender: the host that sends messages
 *  - TCPrecver: the host that receives messages
 *  - TCPmessage: the message to be sent back and forth
 * 
 * 
 */