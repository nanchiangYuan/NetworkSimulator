import java.util.PriorityQueue;

public class Scheduler {

    private double currentTime; // in ms
    private PriorityQueue<Event> global_queue;

    Scheduler (){   
        this.currentTime = 0;
        this.global_queue = new PriorityQueue<>();

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

    public Event runSchedule() {
        Event tobeRun = this.global_queue.poll();
        this.currentTime = tobeRun.getTime();
        return tobeRun;
    }
}
