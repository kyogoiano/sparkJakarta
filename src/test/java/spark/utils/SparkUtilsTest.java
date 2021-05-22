package spark.utils;

import org.junit.Assert;
import org.junit.Test;

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

        Assert.assertTrue("Should return true because parameter follows convention of a parameter (:paramname)",
                SparkUtils.isParam(":param"));

    }

    @Test
    public void testIsParam_whenParameterNotFormattedAsParm() {

        Assert.assertFalse("Should return false because parameter does not follows convention of a parameter (:paramname)",
                SparkUtils.isParam(".param"));

    }


    @Test
    public void testIsSplat_whenParameterIsASplat() {

        Assert.assertTrue("Should return true because parameter is a splat (*)", SparkUtils.isSplat("*"));

    }

    @Test
    public void testIsSplat_whenParameterIsNotASplat() {

        Assert.assertFalse("Should return true because parameter is not a splat (*)", SparkUtils.isSplat("!"));

    }
}
