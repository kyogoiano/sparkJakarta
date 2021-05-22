package spark;

import org.junit.Assert;
import org.junit.Test;

public class Base64Test {

    //CS304 manually Issue link:https://github.com/perwendel/spark/issues/1061

    @Test
    public final void test_encode() {
        String in = "hello";
        String encode = Base64.encode(in);
        Assert.assertNotEquals(in, encode);
    }

    //CS304 manually Issue link:https://github.com/perwendel/spark/issues/1061

    @Test
    public final void test_decode() {
        String in = "hello";
        String encode = Base64.encode(in);
        String decode = Base64.decode(encode);

        Assert.assertEquals(in, decode);
    }

}
