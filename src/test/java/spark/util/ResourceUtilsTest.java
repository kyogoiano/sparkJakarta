package spark.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import spark.utils.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;


public class ResourceUtilsTest {


//    @Test
//    public void testGetFile_whenURLProtocolIsNotFile_thenThrowFileNotFoundException() throws
//                                                                                      MalformedURLException,
//                                                                                      FileNotFoundException {
//
//        URL url = new URL("http://example.com/");
//        Assert.assertThrows("My File Path cannot be resolved to absolute file path " +
//            "because it does not reside in the file system: http://example.com/", FileNotFoundException.class, (ThrowingRunnable) ResourceUtils.getFile(url, "My File Path"));
//    }

    @Test
    public void testGetFile_whenURLProtocolIsFile_thenReturnFileObject() throws
                                                                         MalformedURLException,
                                                                         FileNotFoundException,
                                                                         URISyntaxException {
        //given
        URL url = new URL("file://public/file.txt");
        File file = ResourceUtils.getFile(url, "Some description");

        //then
        Assertions.assertEquals(file, new File(ResourceUtils.toURI(url).getSchemeSpecificPart()), "Should be equals because URL protocol is file");
    }

}
