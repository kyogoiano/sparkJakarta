package sparkTest.globalstate;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.globalstate.ServletFlag;
import sparkTest.util.FieldUtil;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ServletFlagTest {

    @BeforeEach
    public void setup() throws Exception {

        //Whitebox.setInternalState(ServletFlag.class, "isRunningFromServlet", new AtomicBoolean(false));

        final Field field = FieldUtils.getField(ServletFlag.class, "isRunningFromServlet", true);
        FieldUtil.modifyField(field, new AtomicBoolean(false));
    }

    @Test
    public void testRunFromServlet_whenDefault() throws IllegalAccessException {

//        AtomicBoolean isRunningFromServlet = Whitebox.getInternalState(ServletFlag.class, "isRunningFromServlet");
//        assertFalse(isRunningFromServlet.get(), "Should be false because it is the default value");

        final Field field = FieldUtils.getField(ServletFlag.class, "isRunningFromServlet", true);
        assertFalse(((AtomicBoolean)field.get(null)).get(), "Should be false because it is the default value");

    }

    @Test
    public void testRunFromServlet_whenExecuted() throws IllegalAccessException {

        ServletFlag.runFromServlet();
//        AtomicBoolean isRunningFromServlet = Whitebox.getInternalState(ServletFlag.class, "isRunningFromServlet");
//        assertTrue( isRunningFromServlet.get(), "Should be true because it flag has been set after runFromServlet");

        final Field field = FieldUtils.getField(ServletFlag.class, "isRunningFromServlet", true);
        assertTrue(((AtomicBoolean)field.get(null)).get(), "Should be false because it is the default value");

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

    @AfterAll()
    public static void tearDown() throws Exception {
        final Field field = FieldUtils.getField(ServletFlag.class, "isRunningFromServlet", true);
        FieldUtil.modifyField(field, new AtomicBoolean(false));
    }
}
