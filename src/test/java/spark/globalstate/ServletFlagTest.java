package spark.globalstate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
public class ServletFlagTest {

    @BeforeEach
    public void setup() {

        Whitebox.setInternalState(ServletFlag.class, "isRunningFromServlet", new AtomicBoolean(false));
    }

    @Test
    public void testRunFromServlet_whenDefault() {

        AtomicBoolean isRunningFromServlet = Whitebox.getInternalState(ServletFlag.class, "isRunningFromServlet");
        assertFalse(isRunningFromServlet.get(), "Should be false because it is the default value");
    }

    @Test
    public void testRunFromServlet_whenExecuted() {

        ServletFlag.runFromServlet();
        AtomicBoolean isRunningFromServlet = Whitebox.getInternalState(ServletFlag.class, "isRunningFromServlet");

        assertTrue( isRunningFromServlet.get(), "Should be true because it flag has been set after runFromServlet");
    }

    @Test
    public void testIsRunningFromServlet_whenDefault() {

        assertFalse(ServletFlag.isRunningFromServlet(), "Should be false because it is the default value");

    }

    @Test
    public void testIsRunningFromServlet_whenRunningFromServlet() {

        ServletFlag.runFromServlet();
        assertTrue(ServletFlag.isRunningFromServlet(), "Should be true because call to runFromServlet has been made");
    }
}
