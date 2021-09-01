package spark.utils;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class CollectionUtilsTest {

    @Test
    public void testIsEmpty_whenCollectionIsEmpty_thenReturnTrue() {

        Collection<Object> testCollection = new ArrayList<>();

        assertTrue(CollectionUtils.isEmpty(testCollection), "Should return true because collection is empty");

    }

    @Test
    public void testIsEmpty_whenCollectionIsNotEmpty_thenReturnFalse() {

        Collection<Integer> testCollection = new ArrayList<>();
        testCollection.add(1);
        testCollection.add(2);

        assertFalse(CollectionUtils.isEmpty(testCollection), "Should return false because collection is not empty");

    }

    @Test
    public void testIsEmpty_whenCollectionIsNull_thenReturnTrue() {

        Collection<Integer> testCollection = null;

        assertTrue(CollectionUtils.isEmpty(testCollection), "Should return true because collection is null");

    }

    @Test
    public void testIsNotEmpty_whenCollectionIsEmpty_thenReturnFalse() {

        Collection<Object> testCollection = new ArrayList<>();

        assertFalse(CollectionUtils.isNotEmpty(testCollection), "Should return false because collection is empty");

    }

    @Test
    public void testIsNotEmpty_whenCollectionIsNotEmpty_thenReturnTrue() {

        Collection<Integer> testCollection = new ArrayList<>();
        testCollection.add(1);
        testCollection.add(2);

        assertTrue(CollectionUtils.isNotEmpty(testCollection), "Should return true because collection is not empty");

    }

    @Test
    public void testIsNotEmpty_whenCollectionIsNull_thenReturnFalse() {

        Collection<Object> testCollection = null;

        assertFalse(CollectionUtils.isNotEmpty(testCollection), "Should return false because collection is null");

    }
}
