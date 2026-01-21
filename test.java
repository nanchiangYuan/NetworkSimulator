import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Random;

public class test {
    public static void main(String[] args) {

        try{
            FileOutputStream f = new FileOutputStream("hi.txt");
            byte[] b = new byte[1024];
            Random randomgen = new Random();
            randomgen.nextBytes(b);
            f.write(b);
            f.close();
        } catch(FileNotFoundException e) {
            System.out.println("uh oh");
        } catch(IOException e) {
            System.out.println("huh");
        }
        


    }


}
