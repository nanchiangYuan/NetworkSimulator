import java.util.PriorityQueue;

/**
 * Schedule events in the network.
 */
public class Scheduler {

    private double currentTime;                     // in ms
    private PriorityQueue<Event> global_queue;
    private Event timer;                            // checks for timeouts

    /**
     * Constructor
     */
    Scheduler (){   
        this.currentTime = 0;
        this.global_queue = new PriorityQueue<>();
        this.timer = null;
    }

    public double getCurrentTime() {
        return this.currentTime;
    }

    public PriorityQueue<Event> getQueue() {
        return this.global_queue;
    }

    public void setCurrentTime(double time) {
        this.currentTime = time;
    }

    public void schedule(Event e) {
        this.global_queue.add(e);
    }

    /**
     * Schedule an event to happen
     * @return the event to be run
     */
    public Event run() {
        Event tobeRun = this.global_queue.poll();
        this.currentTime = tobeRun.getTime();
        return tobeRun;
    }

    public Event getTimer() {
        return timer;
    }

    /**
     * Updates the timeout time and the oldest packet that has not been acked when this is called
     * @param oldestSeqNo the oldest packet that hasn't been acked
     * @param timeout the timeout time
     */
    public void setTimer(int oldestSeqNo, double timeout) {
        // first remove the old timer
        if(timer != null)
            global_queue.remove(timer);
        
        timer = new Event(oldestSeqNo, Event.EventType.TIMEOUT_CHECK, currentTime + timeout);
        global_queue.add(timer);
    }

    public void print() {
        System.out.println("     ======= current schedule: ");
        for(Event e: global_queue) {
            if(e.getType() == Event.EventType.TIMEOUT_CHECK) 
                System.out.println("       " + e.toTimeoutString());
            else
                System.out.println("       " + e);
        }
        System.out.println("     ==========================");
    }
}
