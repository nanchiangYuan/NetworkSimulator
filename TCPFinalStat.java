import java.util.ArrayList;

public class TCPFinalStat {

    private TCPStat senderStat;
    private TCPStat receiverStat;
    private double finalTime;       // time is in ms

    private double throughput;
    private double retransmissionRatio;
    private double avgRTT;
    private double avgCWND;

    TCPFinalStat(TCPStat senderStat, TCPStat receiverStat) {
        this.senderStat = senderStat;
        this.receiverStat = receiverStat;
        this.finalTime = receiverStat.getFinalTime();

        this.throughput = receiverStat.getReceivedDataSize() * 8.0 / (finalTime * 0.001) * 0.000001;
        this.retransmissionRatio = (double) senderStat.getRetransmissionCount() / (double) senderStat.getSentPacketCount();

        this.avgRTT = calculateAvg(senderStat.getRTT());
        this.avgCWND = calculateAvg(senderStat.getCwnd());
    }



    public void printFinalStat() {
        System.out.println("Final Stat: ");
        System.out.printf("    Total Time:                                        %.3f sec\n", finalTime * 0.001);
        System.out.printf("    Throughput:                                        %.3f Mbps\n", throughput);
        System.out.printf("    Average RTT:                                       %.3f ms\n", avgRTT);
        System.out.printf("    Average congestion window size:                    %.3f segments\n", avgCWND);
        System.out.printf("    Retransmission Ratio:                              %.3f\n", retransmissionRatio);
        System.out.printf("    Total Timeouts:                                    %d\n", senderStat.getTimeout());
        System.out.printf("    Total Fast Retransmissions:                        %d\n", senderStat.getFastRetransmit());
    }

    private double calculateAvg(ArrayList<Double> array) {

        double sum = 0.0;
        for(double item: array) {
            sum += item;
        }
        return sum / array.size();

    }

}