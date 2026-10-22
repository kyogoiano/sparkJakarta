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
package sparkTest.staticfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Spark;
import sparkTest.examples.exception.NotFoundException;
import sparkTest.util.SparkTestUtil;

import static spark.Spark.get;
import static spark.Spark.staticFiles;

/**
 * Test static files
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DisableMimeGuessingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisableMimeGuessingTest.class);

    private static final String FO_SHIZZY = "Fo shizzy";
    private static final String EXTERNAL_FILE_NAME_HTML = "externalFile.html";
    private static final String CONTENT_OF_EXTERNAL_FILE = "Content of external file";

    private SparkTestUtil testUtil;

    private File tmpExternalFile;

    @AfterAll
    public void tearDown() {
        Spark.stop();
        Spark.awaitStop();
        if (tmpExternalFile != null) {
            LOGGER.debug("tearDown().deleting: {}", tmpExternalFile);
            tmpExternalFile.delete();
        }
    }

    @BeforeAll
    public void setup() throws IOException {
        testUtil = new SparkTestUtil(4567);

        tmpExternalFile = new File(System.getProperty("java.io.tmpdir"), EXTERNAL_FILE_NAME_HTML);

        FileWriter writer = new FileWriter(tmpExternalFile);
        writer.write(CONTENT_OF_EXTERNAL_FILE);
        writer.flush();
        writer.close();

        staticFiles.location("/public");
        staticFiles.externalLocation(System.getProperty("java.io.tmpdir"));
        staticFiles.disableMimeTypeGuessing();

        get("/hello", (q, a) -> FO_SHIZZY);

        get("/*", (q, a) -> {
            throw new NotFoundException();
        });

        Spark.awaitInitialization();
    }

    @Test
    public void testMimeTypes() throws Exception {
        Assertions.assertNull(doGet("/pages/index.html").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/js/scripts.js").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/css/style.css").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/img/sparklogo.png").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/img/sparklogo.svg").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/img/sparklogoPng").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/img/sparklogoSvg").headers.get("Content-Type"));
        Assertions.assertNull(doGet("/externalFile.html").headers.get("Content-Type"));
    }

    @Test
    public void testCustomMimeType() throws Exception {
        staticFiles.registerMimeType("cxt", "custom-extension-type");
        Assertions.assertNull(doGet("/img/file.cxt").headers.get("Content-Type"));
    }

    private SparkTestUtil.UrlResponse doGet(String fileName) throws Exception {
        return testUtil.doMethod("GET", fileName, null);
    }

}
