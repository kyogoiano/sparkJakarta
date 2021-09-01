/*
 * Copyright 2016 - Per Wendel
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
package spark;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.route.HttpMethod;
import spark.routematch.RouteMatch;
import spark.util.SparkTestUtil;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.*;
import static spark.Service.ignite;
import static org.junit.jupiter.api.Assertions.*;
import static spark.Spark.awaitStop;

/**
 * Created by Per Wendel on 2016-02-18.
 */
public class MultipleServicesTest {

    private static Service first;
    private static Service second;

    private static SparkTestUtil firstClient;
    private static SparkTestUtil secondClient;

    @BeforeAll
    public static void setup() throws Exception {
        firstClient = new SparkTestUtil(4567);
        secondClient = new SparkTestUtil(1234);

        first = igniteFirstService();
        second = igniteSecondService();

        first.init();
        second.init();
        first.awaitInitialization();
        second.awaitInitialization();
    }

    @AfterAll
    public static void tearDown() {
        first.stop();
        first.awaitStop();
        second.stop();
        second.awaitStop();
    }

    @Test
    public void testGetHello() throws Exception {
        SparkTestUtil.UrlResponse response = firstClient.doMethod("GET", "/hello", null);
        assertEquals(200, response.status);
        assertEquals("Hello World!", response.body);
    }

    @Test
    public void testGetRedirectedHi() throws Exception {
        SparkTestUtil.UrlResponse response = secondClient.doMethod("GET", "/hi", null);
        assertEquals(200, response.status);
        assertEquals("Hello World!", response.body);
    }

    @Test
    public void testGetUniqueForSecondWithFirst() throws Exception {
        SparkTestUtil.UrlResponse response = firstClient.doMethod("GET", "/uniqueforsecond", null);
        assertEquals(404, response.status);
    }

    @Test
    public void testGetUniqueForSecondWithSecond() throws Exception {
        SparkTestUtil.UrlResponse response = secondClient.doMethod("GET", "/uniqueforsecond", null);
        assertEquals(200, response.status);
        assertEquals("Bompton", response.body);
    }

    @Test
    public void testStaticFileCssStyleCssWithFirst() throws Exception {
        SparkTestUtil.UrlResponse response = firstClient.doMethod("GET", "/css/style.css", null);
        assertEquals(404, response.status);
    }

    @Test
    public void testStaticFileCssStyleCssWithSecond() throws Exception {
        SparkTestUtil.UrlResponse response = secondClient.doMethod("GET", "/css/style.css", null);
        assertEquals(200, response.status);
        assertEquals("Content of css file", response.body);
    }

    @Test
    public void testGetAllRoutesFromBothServices(){
        for(RouteMatch routeMatch : first.routes()){
            assertEquals(routeMatch.getAcceptType(), "*/*");
            assertEquals(routeMatch.getHttpMethod(), HttpMethod.get);
            assertEquals(routeMatch.getMatchUri(), "/hello");
            assertEquals(routeMatch.getRequestURI(), "ALL_ROUTES");
            assertThat(routeMatch.getTarget(), instanceOf(RouteImpl.class));
        }

        for(RouteMatch routeMatch : second.routes()){
            assertEquals(routeMatch.getAcceptType(), "*/*");
            assertThat(routeMatch.getHttpMethod(), instanceOf(HttpMethod.class));
            boolean isUriOnList = ("/hello/hi/uniqueforsecond").contains(routeMatch.getMatchUri());
            assertTrue(isUriOnList);
            assertEquals(routeMatch.getRequestURI(), "ALL_ROUTES");
            assertThat(routeMatch.getTarget(), instanceOf(RouteImpl.class));
        }
    }

    private static Service igniteFirstService() {

        Service http = ignite(); // I give the variable the name 'http' for the code to make sense when adding routes.

        http.get("/hello", (q, a) -> "Hello World!");

        return http;
    }

    private static Service igniteSecondService() {

        Service http = ignite()
                .port(1234)
                .staticFileLocation("/public")
                .threadPool(40);

        http.get("/hello", (q, a) -> "Hello World!");
        http.get("/uniqueforsecond", (q, a) -> "Bompton");

        http.redirect.any("/hi", "/hello");

        return http;
    }


}
