package sparkTest.embeddedserver.jetty;

import jakarta.servlet.Filter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import spark.embeddedserver.jetty.HttpRequestWrapper;
import spark.embeddedserver.jetty.JettyHandler;
import spark.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

public class JettyHandlerAndRequestWrapperTest {

    @Test
    public void testJettyHandler_whenRequestNotConsumed_setsStatus404() throws Exception {
        Filter filter = mock(Filter.class);
        doAnswer(invocation -> {
            HttpRequestWrapper wrapper = invocation.getArgument(0);
            wrapper.notConsumed(true);
            return null;
        }).when(filter).doFilter(any(HttpRequestWrapper.class), any(HttpServletResponse.class), isNull());

        JettyHandler jettyHandler = new JettyHandler(filter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        jettyHandler.doHandle(request, response);

        verify(filter).doFilter(any(HttpRequestWrapper.class), eq(response), isNull());
        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testJettyHandler_whenRequestConsumed_doesNotOverwriteStatusWith404() throws Exception {
        Filter filter = mock(Filter.class);
        doAnswer(invocation -> {
            HttpRequestWrapper wrapper = invocation.getArgument(0);
            wrapper.notConsumed(false);
            return null;
        }).when(filter).doFilter(any(HttpRequestWrapper.class), any(HttpServletResponse.class), isNull());

        JettyHandler jettyHandler = new JettyHandler(filter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        jettyHandler.doHandle(request, response);

        verify(filter).doFilter(any(HttpRequestWrapper.class), eq(response), isNull());
        verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testHttpRequestWrapper_cachesInputStreamForMultipleReads() throws Exception {
        byte[] payload = "Jakarta-Jetty-12-Body".getBytes(StandardCharsets.UTF_8);
        ServletInputStream underlyingStream = createServletInputStream(payload);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getInputStream()).thenReturn(underlyingStream);

        HttpRequestWrapper wrapper = new HttpRequestWrapper(request);
        assertFalse(wrapper.notConsumed());
        wrapper.notConsumed(true);
        assertTrue(wrapper.notConsumed());

        ServletInputStream firstStream = wrapper.getInputStream();
        assertTrue(firstStream.isReady());
        assertFalse(firstStream.isFinished());
        assertEquals(payload.length, firstStream.available());
        firstStream.setReadListener(mock(ReadListener.class));

        String firstRead = IOUtils.toString(firstStream);
        assertEquals("Jakarta-Jetty-12-Body", firstRead);
        assertTrue(firstStream.isFinished());
        assertEquals(0, firstStream.available());

        // Second read must return the full cached payload without calling request.getInputStream() again
        ServletInputStream secondStream = wrapper.getInputStream();
        String secondRead = IOUtils.toString(secondStream);
        assertEquals("Jakarta-Jetty-12-Body", secondRead);

        verify(request, times(1)).getInputStream();
    }

    @Test
    public void testHttpRequestWrapper_whenChunkedTransferEncoding_bypassesCache() throws Exception {
        ServletInputStream rawStream1 = mock(ServletInputStream.class);
        ServletInputStream rawStream2 = mock(ServletInputStream.class);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Transfer-Encoding")).thenReturn("chunked");
        when(request.getInputStream()).thenReturn(rawStream1, rawStream2);

        HttpRequestWrapper wrapper = new HttpRequestWrapper(request);

        assertSame(rawStream1, wrapper.getInputStream());
        assertSame(rawStream2, wrapper.getInputStream());
        verify(request, times(2)).getInputStream();
    }

    @Test
    public void testHttpRequestWrapper_whenMultipartFormData_bypassesCache() throws Exception {
        ServletInputStream rawStream1 = mock(ServletInputStream.class);
        ServletInputStream rawStream2 = mock(ServletInputStream.class);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Content-Type")).thenReturn("multipart/form-data; boundary=----SparkBoundary");
        when(request.getInputStream()).thenReturn(rawStream1, rawStream2);

        HttpRequestWrapper wrapper = new HttpRequestWrapper(request);

        assertSame(rawStream1, wrapper.getInputStream());
        assertSame(rawStream2, wrapper.getInputStream());
        verify(request, times(2)).getInputStream();
    }

    @Test
    public void testStaticFilesConfiguration_directoryTraversalDetectionSets400BadRequest() throws Exception {
        spark.staticfiles.StaticFilesConfiguration config = spark.staticfiles.StaticFilesConfiguration.create();
        config.configure("/public");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        java.io.StringWriter stringWriter = new java.io.StringWriter();
        java.io.PrintWriter printWriter = new java.io.PrintWriter(stringWriter);

        when(request.getPathInfo()).thenReturn("/..\\spark\\Spark.class");
        when(response.getWriter()).thenReturn(printWriter);

        boolean consumed = config.consume(request, response);
        assertFalse(consumed);
        verify(response).setStatus(400);
        assertEquals("Bad request", stringWriter.toString());
    }

    private static ServletInputStream createServletInputStream(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
    }
}
