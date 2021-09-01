package spark;


import org.junit.jupiter.api.*;

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
        Assertions.assertEquals("Custom init error", errorMessage);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Spark.stop();
        Spark.awaitStop();
    }

}
