package sparkTest.examples.hello;

import java.net.URI;
import java.net.URISyntaxException;

import static spark.Spark.get;
import static spark.Spark.secure;

/**
 * You'll need to provide a JKS keystore as arg 0 and its password as arg 1.
 */
public class HelloSecureWorld {
    public static void main(String[] args) throws URISyntaxException {

        secure(new URI(args[0]), args[1], null, null);
        get("/hello", (request, response) -> "Hello Secure World!");

    }
}
