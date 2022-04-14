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

import static java.lang.ClassLoader.getPlatformClassLoader;
import static java.lang.ClassLoader.getSystemClassLoader;
import static java.lang.System.arraycopy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.jetty.util.TypeUtil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spark.util.SparkTestUtil;
import spark.util.SparkTestUtil.UrlResponse;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;


public class StaticFilesFromArchiveTest {

    private static SparkTestUtil testUtil;
    private static ClassLoader classLoader;
    private static ClassLoader initialClassLoader;

    @BeforeAll
    public static void setup() throws Exception {
        setupClassLoader();
        testUtil = new SparkTestUtil(4567);

        Class<?> sparkClass = classLoader.loadClass("spark.Spark");

        Method staticFileLocationMethod = sparkClass.getMethod("staticFileLocation", String.class);
        staticFileLocationMethod.invoke(null, "/public-jar");

        Method initMethod = sparkClass.getMethod("init");
        initMethod.invoke(null);

        Method awaitInitializationMethod = sparkClass.getMethod("awaitInitialization");
        awaitInitializationMethod.invoke(null);
    }

    @AfterAll
    public static void resetClassLoader() {
        Thread.currentThread().setContextClassLoader(initialClassLoader);
        Spark.stop();
        Spark.awaitStop();
    }

    private static void setupClassLoader() throws Exception {
        ClassLoader extendedClassLoader = createExtendedClassLoader();
        initialClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(extendedClassLoader);
        classLoader = extendedClassLoader;
    }

    private static URLClassLoader createExtendedClassLoader() throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        //URL[] parentURLs = ((URLClassLoader) getSystemClassLoader()).getURLs();

        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);

        Class<?> builtinClazzLoader = Class.forName("jdk.internal.loader.BuiltinClassLoader");

        Field ucpField = builtinClazzLoader.getDeclaredField("ucp");
        long ucpFieldOffset = unsafe.objectFieldOffset(ucpField);
        Object ucpObject = unsafe.getObject(builtinClazzLoader, ucpFieldOffset);
        //Object ucpObject = ucpField.get();
        Class<?> clazz = Class.forName("jdk.internal.loader.URLClassPath");
        Method getURLs = clazz.getMethod("getURLs");

        // jdk.internal.loader.URLClassPath.path
        Field pathField = ucpField.getType().getDeclaredField("path");
        long pathFieldOffset = unsafe.objectFieldOffset(pathField);
        ArrayList<URL> path = (ArrayList<URL>) unsafe.getObject(builtinClazzLoader, pathFieldOffset);

        //URL[] path = (URL[]) getURLs.invoke(ucpField);

        URL[] urls = new URL[path.size() + 1];
        arraycopy(path, 0, urls, 0, path.size());

        URL publicJar = StaticFilesFromArchiveTest.class.getResource("/public-jar.zip");
        urls[urls.length - 1] = publicJar;

        // no parent classLoader because Spark and the static resources need to be loaded from the same classloader
        return URLClassLoader.newInstance(urls, null);
    }

    @Test
    @Disabled
    public void testCss() throws Exception {
        UrlResponse response = testUtil.doMethod("GET", "/css/style.css", null);

        String expectedContentType = response.headers.get("Content-Type");
        assertEquals(expectedContentType, "text/css");

        String body = response.body;
        assertEquals("Content of css file", body);
    }
}
