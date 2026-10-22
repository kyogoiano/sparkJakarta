package spark.globalstate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import spark.utils.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServletFlagTest
{

    @Before
    public void setup() {

        ReflectionTestUtils.setField(ServletFlag.class, "isRunningFromServlet", new AtomicBoolean(false));
    }

    @Test
    public void testRunFromServlet_whenDefault() throws Exception {

        AtomicBoolean isRunningFromServlet = ReflectionTestUtils.getField(ServletFlag.class, "isRunningFromServlet");
        assertFalse("Should be false because it is the default value", isRunningFromServlet.get());
    }

    @Test
    public void testRunFromServlet_whenExecuted() throws Exception {

        ServletFlag.runFromServlet();
        AtomicBoolean isRunningFromServlet = ReflectionTestUtils.getField(ServletFlag.class, "isRunningFromServlet");

        assertTrue("Should be true because it flag has been set after runFromServlet", isRunningFromServlet.get());
    }

    @Test
    public void testIsRunningFromServlet_whenDefault() throws Exception {

        assertFalse("Should be false because it is the default value", ServletFlag.isRunningFromServlet());

    }

    @Test
    public void testIsRunningFromServlet_whenRunningFromServlet() throws Exception {

        ServletFlag.runFromServlet();
        assertTrue("Should be true because call to runFromServlet has been made", ServletFlag.isRunningFromServlet());
    }
}
