package sparkTest.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.globalstate.ServletFlag;
import spark.servlet.SparkApplication;
import spark.servlet.SparkFilter;
import sparkTest.util.FieldUtil;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SparkFilterTest {

    public static class FirstDummyApp implements SparkApplication {
        static final AtomicBoolean initialized = new AtomicBoolean(false);
        static final AtomicBoolean destroyed = new AtomicBoolean(false);
        static String capturedPathInfo;
        static String capturedUri;
        static String capturedParam;

        @Override
        public void init() {
            initialized.set(true);
            spark.Spark.get("/users/:id", (req, res) -> {
                capturedPathInfo = req.pathInfo();
                capturedUri = req.uri();
                capturedParam = req.params("id");
                return "user-" + capturedParam;
            });
        }

        @Override
        public void destroy() {
            destroyed.set(true);
            spark.route.ServletRoutes.get().clear();
        }
    }

    public static class SecondDummyApp implements SparkApplication {
        static final AtomicBoolean initialized = new AtomicBoolean(false);
        static final AtomicBoolean destroyed = new AtomicBoolean(false);

        @Override
        public void init() {
            initialized.set(true);
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }
    }

    @BeforeEach
    public void resetFlags() {
        FirstDummyApp.initialized.set(false);
        FirstDummyApp.destroyed.set(false);
        FirstDummyApp.capturedPathInfo = null;
        FirstDummyApp.capturedUri = null;
        FirstDummyApp.capturedParam = null;
        SecondDummyApp.initialized.set(false);
        SecondDummyApp.destroyed.set(false);
        spark.route.ServletRoutes.get().clear();
    }

    @AfterEach
    public void resetServletFlag() throws Exception {
        spark.route.ServletRoutes.get().clear();
        final Field field = FieldUtils.getField(ServletFlag.class, "isRunningFromServlet", true);
        FieldUtil.modifyField(field, new AtomicBoolean(false));
    }

    @Test
    public void testInitAndDestroy_withMultipleApplicationsAndCustomFilterMapping() throws Exception {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(filterConfig.getInitParameter(SparkFilter.APPLICATION_CLASS_PARAM))
            .thenReturn(FirstDummyApp.class.getName() + ", " + SecondDummyApp.class.getName());
        when(filterConfig.getInitParameter("filterMappingUrlPattern")).thenReturn("/api/*");

        SparkFilter sparkFilter = new SparkFilter();
        sparkFilter.init(filterConfig);

        assertTrue(ServletFlag.isRunningFromServlet());
        verify(servletContext).setRequestCharacterEncoding(MimeTypes.UTF8);
        verify(servletContext).setResponseCharacterEncoding(MimeTypes.UTF8);
        assertTrue(FirstDummyApp.initialized.get());
        assertTrue(SecondDummyApp.initialized.get());

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContextPath()).thenReturn("/ctx");
        when(request.getRequestURI()).thenReturn("/ctx/api/users/42");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeaders(anyString())).thenReturn(java.util.Collections.emptyEnumeration());
        when(response.getOutputStream()).thenReturn(mock(jakarta.servlet.ServletOutputStream.class));

        sparkFilter.doFilter(request, response, chain);
        assertEquals("/users/42", FirstDummyApp.capturedPathInfo);
        assertEquals("/users/42", FirstDummyApp.capturedUri);
        assertEquals("42", FirstDummyApp.capturedParam);
        verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);

        sparkFilter.destroy();
        assertTrue(FirstDummyApp.destroyed.get());
        assertTrue(SecondDummyApp.destroyed.get());
    }

    @Test
    public void testInit_whenApplicationClassDoesNotExist_throwsServletException() {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(filterConfig.getInitParameter(SparkFilter.APPLICATION_CLASS_PARAM))
            .thenReturn("sparkTest.servlet.NonExistentApp");

        SparkFilter sparkFilter = new SparkFilter();
        assertThrows(ServletException.class, () -> sparkFilter.init(filterConfig));
    }

    @Test
    public void testInit_whenFilterMappingPatternIsInvalid_throwsRuntimeException() {
        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(filterConfig.getInitParameter(SparkFilter.APPLICATION_CLASS_PARAM))
            .thenReturn(FirstDummyApp.class.getName());
        when(filterConfig.getInitParameter("filterMappingUrlPattern")).thenReturn("invalid-pattern");

        SparkFilter sparkFilter = new SparkFilter();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> sparkFilter.init(filterConfig));
        assertTrue(ex.getMessage().contains("filterMappingUrlPattern"));
    }
}
