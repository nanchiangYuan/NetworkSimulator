import java.util.Objects;

public class test {
    public static void main(String[] args) {
        
        Event a = new Event(null, null, 0);
        Event b = new Event(null, null, 1);
        Scheduler sched = new Scheduler();
        sched.addToQueue(b);
        sched.addToQueue(a);

        System.out.println(sched.popFromQueue());
        System.out.println(sched.popFromQueue());

    }


}
