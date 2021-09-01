package spark;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import static org.junit.jupiter.api.Assertions.*;

public class ExceptionMapperTest {


    @Test
    public void testGetInstance_whenDefaultInstanceIsNull() {
        //given
        Whitebox.setInternalState(ExceptionMapper.class, "servletInstance", (Object) null);

        //then
        final ExceptionMapper exceptionMapper = ExceptionMapper.getServletInstance();
        assertEquals(Whitebox.getInternalState(ExceptionMapper.class, "servletInstance"), exceptionMapper, "Should be equals because ExceptionMapper is a singleton");
    }

    @Test
    public void testGetInstance_whenDefaultInstanceIsNotNull() {
        //given
        ExceptionMapper.getServletInstance(); //initialize Singleton

        //then
        ExceptionMapper exceptionMapper = ExceptionMapper.getServletInstance();
        assertEquals(Whitebox.getInternalState(ExceptionMapper.class, "servletInstance"), exceptionMapper, "Should be equals because ExceptionMapper is a singleton");
    }
}
