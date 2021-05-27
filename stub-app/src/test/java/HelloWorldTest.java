import com.cisco.dhruva.HelloWorld;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;


public class HelloWorldTest {
    @Test
    public void testApp(){
        HelloWorld helloWorld=new HelloWorld();
        assertEquals(helloWorld.returnZero(),0);

    }
}
