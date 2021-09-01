package spark;


import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class RouteImplTest {

    private final static String PATH_TEST = "/opt/test";
    private final static String ACCEPT_TYPE_TEST  = "*/test";

    private RouteImpl route;

    @Test
    public void testConstructor(){
        route = new RouteImpl(PATH_TEST) {
            @Override
            public Object handle(Request request, Response response) {
                return null;
            }
        };
        assertEquals(PATH_TEST, route.getPath(), "Should return path specified");
    }

    @Test
    public void testGets_thenReturnGetPathAndGetAcceptTypeSuccessfully() {
        route = RouteImpl.create(PATH_TEST, ACCEPT_TYPE_TEST, null);
        assertEquals(PATH_TEST, route.getPath(), "Should return path specified");
        assertEquals(ACCEPT_TYPE_TEST, route.getAcceptType(), "Should return accept type specified");
    }

    @Test
    public void testCreate_whenOutAssignAcceptTypeInTheParameters_thenReturnPathAndAcceptTypeSuccessfully(){
        route = RouteImpl.create(PATH_TEST, null);
        assertEquals( PATH_TEST, route.getPath(), "Should return path specified");
        assertEquals( RouteImpl.DEFAULT_ACCEPT_TYPE, route.getAcceptType(), "Should return the default accept type");
    }

    @Test
    public void testCreate_whenAcceptTypeNullValueInTheParameters_thenReturnPathAndAcceptTypeSuccessfully(){
        route = RouteImpl.create(PATH_TEST, null, null);
        assertEquals(PATH_TEST, route.getPath(), "Should return path specified");
        assertEquals(RouteImpl.DEFAULT_ACCEPT_TYPE, route.getAcceptType(), "Should return the default accept type");
    }

    @Test
    public void testRender_whenElementParameterValid_thenReturnValidObject() throws Exception {
        String finalObjValue = "object_value";
        route = RouteImpl.create(PATH_TEST, null);
        Object value = route.render(finalObjValue);
        assertNotNull(value, "Should return an Object because we configured it to have one");
        assertEquals(finalObjValue, value.toString(),"Should return a string object specified");
    }

    @Test
    public void testRender_whenElementParameterIsNull_thenReturnNull() throws Exception {
        route = RouteImpl.create(PATH_TEST, null);
        Object value = route.render(null);
        assertNull(value, "Should return null because the element from render is null");
    }
}
