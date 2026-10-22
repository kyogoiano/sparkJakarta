package sparkTest;

import org.junit.jupiter.api.Test;
import spark.ExceptionMapper;
import spark.utils.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;


public class ExceptionMapperTest {

    @Test
    public void testGetInstance_whenDefaultInstanceIsNull() {
        //given
        ExceptionMapper<?> exceptionMapper = null;
        ReflectionTestUtils.setField(ExceptionMapper.class, "servletInstance", exceptionMapper);

        //then
        exceptionMapper = ExceptionMapper.getServletInstance();
        assertEquals(ReflectionTestUtils.getField(ExceptionMapper.class, "servletInstance"), exceptionMapper, "Should be equals because ExceptionMapper is a singleton");
    }

    @Test
    public void testGetInstance_whenDefaultInstanceIsNotNull() {
        //given
        ExceptionMapper.getServletInstance(); //initialize Singleton

        //then
        ExceptionMapper<?> exceptionMapper = ExceptionMapper.getServletInstance();
        assertEquals(ReflectionTestUtils.getField(ExceptionMapper.class, "servletInstance"), exceptionMapper, "Should be equals because ExceptionMapper is a singleton");
    }
}
