package spark.utils;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ObjectUtilsTest {

    @Test
    public void testIsEmpty_whenArrayIsEmpty() {

        assertTrue(ObjectUtils.isEmpty(new Object[]{}), "Should return false because array is empty");

    }

    @Test
    public void testIsEmpty_whenArrayIsNotEmpty() {

        assertFalse(ObjectUtils.isEmpty(new Integer[]{1,2}), "Should return false because array is not empty");

    }
}
