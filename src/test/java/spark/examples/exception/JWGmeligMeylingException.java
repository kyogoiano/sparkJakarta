package spark.examples.exception;

import java.io.Serial;

public class JWGmeligMeylingException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public String trustButVerify() {
        return "The fact that it doesn't break with more explicit types should be enough, but tipsy is a worrywart";
    }
}
