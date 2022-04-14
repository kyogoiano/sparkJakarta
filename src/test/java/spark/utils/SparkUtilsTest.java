package spark.utils;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.*;

public class SparkUtilsTest {

    @Test
    public void testConvertRouteToList() {

        List<String> expected = Arrays.asList("api", "person", ":id");

        List<String> actual = SparkUtils.convertRouteToList("/api/person/:id");

        assertThat("Should return route as a list of individual elements that path is made of",
                actual,
                is(expected));

    }

    @Test
    public void testIsParam_whenParameterFormattedAsParm() {

        Assertions.assertTrue(
                SparkUtils.isParam(":param"), "Should return true because parameter follows convention of a parameter (:paramname)");

    }

    @Test
    public void testIsParam_whenParameterNotFormattedAsParm() {

        Assertions.assertFalse(
                SparkUtils.isParam(".param"), "Should return false because parameter does not follows convention of a parameter (:paramname)");

    }


    @Test
    public void testIsSplat_whenParameterIsASplat() {

        Assertions.assertTrue(SparkUtils.isSplat("*"), "Should return true because parameter is a splat (*)");

    }

    @Test
    public void testIsSplat_whenParameterIsNotASplat() {

        Assertions.assertFalse(SparkUtils.isSplat("!"), "Should return true because parameter is not a splat (*)");

    }
}
