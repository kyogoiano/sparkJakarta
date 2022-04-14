package spark.utils;


import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;


public class MimeParseTest {

    @Test
    public void testBestMatch() {

        final String header = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Collection<String> supported = Arrays.asList("application/xml", "text/html");

        assertEquals(
                "text/html", MimeParse.bestMatch(supported, header), "bestMatch should return the supported mime type with the highest quality factor" +
                "because it is preferred mime type as indicated in the HTTP header");

    }

    @Test
    public void testBestMatch_whenSupportedIsLowQualityFactor() {

        final String header = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        Collection<String> supported = Collections.singletonList("application/json");

        assertEquals(
                "application/json", MimeParse.bestMatch(supported, header),
            "bestMatch should return the mime type even if it is not included in the supported" +
                "mime types because it is considered by the */* all media type specified in the Accept" +
                "Header");

    }

}
