package spark;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SessionTest {

    Request request;
    HttpSession httpSession;
    Session session;

    @BeforeEach
    public void setup() {

        httpSession = mock(HttpSession.class);
        request = mock(Request.class);
        session = new Session(httpSession, request);
    }

    @Test
    public void testSession_whenHttpSessionIsNull_thenThrowException() {

        try {

            new Session(null, request);
            fail("Session instantiation with a null HttpSession should throw an IllegalArgumentException");

        } catch (IllegalArgumentException ex) {

            assertEquals("session cannot be null", ex.getMessage());
        }
    }

    @Test
    public void testSession_whenRequestIsNull_thenThrowException() {

        try {

            new Session(httpSession, null);
            fail("Session instantiation with a null Request should throw an IllegalArgumentException");

        } catch (IllegalArgumentException ex) {

            assertEquals("request cannot be null", ex.getMessage());
        }
    }

    @Test
    public void testSession() {

        HttpSession internalSession = Whitebox.getInternalState(session, "session");
        assertEquals(
                httpSession, internalSession, "Internal session should be set to the http session provided during instantiation");
    }

    @Test
    public void testRaw() {

        assertEquals(
                httpSession, session.raw(), "Should return the HttpSession provided during instantiation");
    }

    @Test
    public void testAttribute_whenAttributeIsRetrieved() {

        when(httpSession.getAttribute("name")).thenReturn("Jett");

        assertEquals( "Jett", session.attribute("name"), "Should return attribute from HttpSession");

    }

    @Test
    public void testAttribute_whenAttributeIsSet() {

        session.attribute("name", "Jett");

        verify(httpSession).setAttribute("name", "Jett");
    }

    @Test
    public void testAttributes() {

        Set<String> attributes = new HashSet<>(Arrays.asList("name", "location"));

        when(httpSession.getAttributeNames()).thenReturn(Collections.enumeration(attributes));

        assertEquals(attributes, session.attributes(), "Should return attributes from the HttpSession");
    }

    @Test
    public void testCreationTime() {

        when(httpSession.getCreationTime()).thenReturn(10000000L);

        assertEquals( 10000000L, session.creationTime(), "Should return creationTime from HttpSession");
    }

    @Test
    public void testId() {

        when(httpSession.getId()).thenReturn("id");

        assertEquals("id", session.id(), "Should return session id from HttpSession");
    }

    @Test
    public void testLastAccessedTime() {

        when(httpSession.getLastAccessedTime()).thenReturn(20000000L);

        assertEquals( 20000000L, session.lastAccessedTime(), "Should return lastAccessedTime from HttpSession");
    }

    @Test
    public void testMaxInactiveInterval_whenRetrieved() {

        when(httpSession.getMaxInactiveInterval()).thenReturn(100);

        assertEquals(100, session.maxInactiveInterval(), "Should return maxInactiveInterval from HttpSession");
    }

    @Test
    public void testMaxInactiveInterval_whenSet() {

        session.maxInactiveInterval(200);

        verify(httpSession).setMaxInactiveInterval(200);
    }

    @Test
    public void testInvalidate() {

        session.invalidate();

        verify(httpSession).invalidate();
    }

    @Test
    public void testIsNew() {

        when(httpSession.isNew()).thenReturn(true);

        assertTrue( session.isNew(), "Should return isNew status from HttpSession");
    }

    @Test
    public void testRemoveAttribute() {

        session.removeAttribute("name");

        verify(httpSession).removeAttribute("name");
    }
}
