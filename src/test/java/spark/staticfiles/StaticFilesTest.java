/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.staticfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Spark;
import spark.examples.exception.NotFoundException;
import spark.util.SparkTestUtil;

import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.staticFiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test static files
 */
public class StaticFilesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticFilesTest.class);

    private static final String FO_SHIZZY = "Fo shizzy";
    private static final String NOT_FOUND_BRO = "Not found bro";

    private static final String EXTERNAL_FILE_NAME_HTML = "externalFile.html";

    private static final String CONTENT_OF_EXTERNAL_FILE = "Content of external file";

    private static SparkTestUtil testUtil;

    private static File tmpExternalFile;

    @AfterAll
    public static void tearDown() {
        Spark.stop();
        Spark.awaitStop();
        if (tmpExternalFile != null) {
            LOGGER.debug("tearDown().deleting: " + tmpExternalFile);
            tmpExternalFile.delete();
        }
    }

    @BeforeAll
    public static void setup() throws IOException {
        testUtil = new SparkTestUtil(4567);

        tmpExternalFile = new File(System.getProperty("java.io.tmpdir"), EXTERNAL_FILE_NAME_HTML);

        FileWriter writer = new FileWriter(tmpExternalFile);
        writer.write(CONTENT_OF_EXTERNAL_FILE);
        writer.flush();
        writer.close();

        staticFiles.location("/public");
        staticFiles.externalLocation(System.getProperty("java.io.tmpdir"));

        get("/hello", (q, a) -> FO_SHIZZY);

        get("/*", (q, a) -> {
            throw new NotFoundException();
        });

        exception(NotFoundException.class, (e, request, response) -> {
            response.status(404);
            response.body(NOT_FOUND_BRO);
        });
        //Spark.init();
        Spark.awaitInitialization();
    }

    @BeforeEach
    public void waitALittle(){
        SparkTestUtil.sleep(100L);
    }

    @Test
    public void testMimeTypes() throws Exception {
        assertEquals("text/html", doGet("/pages/index.html").headers.get("Content-Type"));
        assertEquals("application/javascript", doGet("/js/scripts.js").headers.get("Content-Type"));
        assertEquals("text/css", doGet("/css/style.css").headers.get("Content-Type"));
        assertEquals("image/png", doGet("/img/sparklogo.png").headers.get("Content-Type"));
        assertEquals("image/svg+xml", doGet("/img/sparklogo.svg").headers.get("Content-Type"));
        assertEquals("application/octet-stream", doGet("/img/sparklogoPng").headers.get("Content-Type"));
        assertEquals("application/octet-stream", doGet("/img/sparklogoSvg").headers.get("Content-Type"));
        assertEquals("text/html", doGet("/externalFile.html").headers.get("Content-Type"));
    }

    @Test
    public void testCustomMimeType() throws Exception {
        staticFiles.registerMimeType("cxt", "custom-extension-type");
        assertEquals("custom-extension-type", doGet("/img/file.cxt").headers.get("Content-Type"));
    }

    @Test
    public void testStaticFileCssStyleCss() throws Exception {
        SparkTestUtil.UrlResponse response = doGet("/css/style.css");
        assertEquals(200, response.status);
        assertEquals("text/css", response.headers.get("Content-Type"));
        assertEquals("Content of css file", response.body);

        testGet();
    }

    @Test
    public void testStaticFilePagesIndexHtml() throws Exception {
        SparkTestUtil.UrlResponse response = doGet("/pages/index.html");
        assertEquals(200, response.status);
        assertEquals("<html><body>Hello Static World!</body></html>", response.body);

        testGet();
    }

    @Test
    public void testStaticFilePageHtml() throws Exception {
        SparkTestUtil.UrlResponse response = doGet("/page.html");
        assertEquals(200, response.status);
        assertEquals("<html><body>Hello Static Files World!</body></html>", response.body);

        testGet();
    }

    @Test
    public void testDirectoryTraversalProtectionLocal() throws Exception {
        String path = "/" + URLEncoder.encode("..\\spark\\", StandardCharsets.UTF_8) + "Spark.class";
        SparkTestUtil.UrlResponse response = doGet(path);

        assertEquals(400, response.status);

        testGet();
    }

    @Test
    public void testExternalStaticFile() throws Exception {
        SparkTestUtil.UrlResponse response = doGet("/externalFile.html");
        assertEquals(200, response.status);
        assertEquals(CONTENT_OF_EXTERNAL_FILE, response.body);

        testGet();
    }

    /**
     * Used to verify that "normal" functionality works after static files mapping
     */
    private static void testGet() throws Exception {
        SparkTestUtil.UrlResponse response = testUtil.doMethod("GET", "/hello", "");

        assertEquals(200, response.status);
        assertTrue(response.body.contains(FO_SHIZZY));
    }

    @Test
    public void testExceptionMapping404() throws Exception {
        SparkTestUtil.UrlResponse response = doGet("/filethatdoesntexist.html");

        assertEquals(404, response.status);
        assertEquals(NOT_FOUND_BRO, response.body);
    }

    private SparkTestUtil.UrlResponse doGet(String fileName) throws Exception {
        return testUtil.doMethod("GET", fileName, null);
    }

}
