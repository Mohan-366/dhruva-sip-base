import static org.testng.AssertJUnit.assertEquals;

import com.cisco.dhruva.HelloWorld;
import org.testng.annotations.Test;

public class HelloWorldTest {
  @Test
  public void testApp() {
    HelloWorld helloWorld = new HelloWorld();
    assertEquals(helloWorld.returnZero(), 0);
  }
}
