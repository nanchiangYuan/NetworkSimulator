import java.util.PriorityQueue;

public class Scheduler {

    private double currentTime; // in ms
    private PriorityQueue<Event> global_queue;
    private Event timer;

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

    public Event run() {
        Event tobeRun = this.global_queue.poll();
        this.currentTime = tobeRun.getTime();
        return tobeRun;
    }

    public Event getTimer() {
        return timer;
    }

    public void setTimer(int oldestSeqNo, double timeout) {
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
