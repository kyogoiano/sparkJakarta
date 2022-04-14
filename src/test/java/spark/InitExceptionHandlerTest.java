package spark;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import static spark.Service.ignite;

public class InitExceptionHandlerTest {

    private static final int NON_VALID_PORT = Integer.MAX_VALUE;
    private static Service service;
    private static String errorMessage = "";

    @BeforeAll
    public static void setUpClass() {
        service = ignite();
        service.port(NON_VALID_PORT);
        service.initExceptionHandler((e) -> errorMessage = "Custom init error");
        service.init();
        service.awaitInitialization();
    }

    @Test
    public void testInitExceptionHandler() {
        assertEquals("Custom init error", errorMessage);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        service.stop();
    }

}
