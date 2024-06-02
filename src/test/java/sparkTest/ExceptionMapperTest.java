package sparkTest;

import org.junit.jupiter.api.Test;
import spark.ExceptionMapper;

import static org.junit.jupiter.api.Assertions.*;


public class ExceptionMapperTest {


    @Test
    public void testGetInstance_whenDefaultInstanceIsNotNull() {
        //given
        ExceptionMapper.getServletInstance(); //initialize Singleton

        //then
        assertDoesNotThrow( () -> ExceptionMapper.getServletInstance());
    }
}
