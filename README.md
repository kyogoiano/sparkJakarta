# Spark Framework - Community Maintained (Jakarta EE 11 / Jetty 12.1)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![CI](https://github.com/sparkjava-community/spark/actions/workflows/ci.yml/badge.svg)](https://github.com/sparkjava-community/spark/actions/workflows/ci.yml)
[![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-11-orange)](https://jakarta.ee/specifications/platform/11/)
[![Jetty](https://img.shields.io/badge/Jetty-12.1.14-blue)](https://eclipse.dev/jetty/)
[![Java](https://img.shields.io/badge/Java-21%2B-red)](https://adoptium.net/)

> A tiny web framework for Java — community-maintained fork modernized for Jakarta EE 11, Jetty 12.1, and Java 21+

## 🚨 About This Fork

**Spark Framework** was created by [Per Wendel](https://github.com/perwendel) and last updated in 2021. This is a **community-maintained fork** that modernizes Spark for the **Jakarta EE 11** ecosystem and **Eclipse Jetty 12.1**.

### What's New in This Version?

- ✅ **Jakarta EE 11 Migration** - Migrated from `javax.servlet` to `jakarta.servlet` (Jakarta Servlet 6.1 & Jakarta WebSocket 2.2)
- ✅ **Eclipse Jetty 12.1 (`ee11`)** - Upgraded embedded server to Jetty `12.1.14` (`jetty-ee11-webapp`, `jetty-ee11-websocket`) and SLF4J `2.0.20`
- ✅ **Java 21+ Baseline & Virtual Threads** - Requires JDK 21 or newer and supports Virtual Threads out of the box via `useVirtualThreads()`
- ✅ **Jakarta Cookie Attributes (`SameSite` & Custom Attributes)** - First-class support for `SameSite` and arbitrary cookie attributes (`Partitioned`, etc.) via `res.cookie(...)`
- ✅ **Servlet 6.1 Redirects & `103 Early Hints`** - Native `HttpServletResponse.sendRedirect(location, statusCode)` and `res.sendEarlyHints()` support
- ✅ **JPMS Module Support** - Includes `module-info.java` (`module sparkJakarta`)
- ✅ **Gradle Build & JUnit 5** - Migrated build system to Gradle and test suite to JUnit Jupiter 5

> **Migration Note (`javax` -> `jakarta`):** Because this release targets Jakarta EE 11 and Jetty 12.1, servlet and websocket types exposed via `req.raw()`, `res.raw()`, `SparkFilter`, and WebSocket handlers now use the `jakarta.servlet.*`, `jakarta.websocket.*`, and Jetty 12 `org.eclipse.jetty.websocket.api.*` packages.

### Original Project

- **Original repository:** [perwendel/spark](https://github.com/perwendel/spark)
- **Original documentation:** [sparkjava.com](http://sparkjava.com/documentation)
- **Creator:** [Per Wendel](https://github.com/perwendel)

---

## 📦 Installation

### Gradle

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sparkjava-community:spark:3.0.0-SNAPSHOT'
}
```

### Maven

Add JitPack repository:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add dependency:

```xml
<dependency>
    <groupId>com.github.sparkjava-community</groupId>
    <artifactId>spark</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

---

## 🚀 Getting Started

```java
import static spark.Spark.*;

public class HelloWorld {
    public static void main(String[] args) {
        get("/hello", (req, res) -> "Hello World!");
    }
}
```

View at: http://localhost:4567/hello

---

## 📚 Documentation

- **Documentation:** [sparkjava.com/documentation](http://sparkjava.com/documentation) *(original docs still apply)*
- **Javadoc:** [javadoc.io/doc/com.sparkjava/spark-core](http://javadoc.io/doc/com.sparkjava/spark-core)
- **Questions:** [Stack Overflow - spark-java tag](http://stackoverflow.com/questions/tagged/spark-java)
- **Issues:** [GitHub Issues](https://github.com/sparkjava-community/spark/issues)

---

## 📖 Examples

### Simple Example

```java
import static spark.Spark.*;

public class SimpleExample {
    public static void main(String[] args) {
        get("/hello", (req, res) -> "Hello World!");

        post("/hello", (req, res) -> "Hello World: " + req.body());

        get("/users/:name", (req, res) -> 
            "Selected user: " + req.params(":name")
        );

        get("/news/:section", (req, res) -> {
            res.type("text/xml");
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><news>" 
                + req.params("section") + "</news>";
        });
    }
}
```

### CRUD Example

```java
import static spark.Spark.*;
import java.util.*;

public class Books {
    private static Map<String, Book> books = new HashMap<>();

    public static void main(String[] args) {
        // Create
        post("/books", (req, res) -> {
            String author = req.queryParams("author");
            String title = req.queryParams("title");
            Book book = new Book(author, title);
            
            String id = UUID.randomUUID().toString();
            books.put(id, book);
            
            res.status(201);
            return id;
        });

        // Read
        get("/books/:id", (req, res) -> {
            Book book = books.get(req.params(":id"));
            if (book != null) {
                return "Title: " + book.getTitle() 
                    + ", Author: " + book.getAuthor();
            } else {
                res.status(404);
                return "Book not found";
            }
        });

        // Update
        put("/books/:id", (req, res) -> {
            String id = req.params(":id");
            Book book = books.get(id);
            if (book != null) {
                String newAuthor = req.queryParams("author");
                String newTitle = req.queryParams("title");
                if (newAuthor != null) book.setAuthor(newAuthor);
                if (newTitle != null) book.setTitle(newTitle);
                return "Book updated";
            } else {
                res.status(404);
                return "Book not found";
            }
        });

        // Delete
        delete("/books/:id", (req, res) -> {
            Book book = books.remove(req.params(":id"));
            if (book != null) {
                return "Book deleted";
            } else {
                res.status(404);
                return "Book not found";
            }
        });
    }

    static class Book {
        private String author, title;
        
        public Book(String author, String title) {
            this.author = author;
            this.title = title;
        }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
```

### Filter Example (Authentication)

```java
import static spark.Spark.*;
import java.util.*;

public class FilterExample {
    private static Map<String, String> users = new HashMap<>();

    public static void main(String[] args) {
        users.put("foo", "bar");
        users.put("admin", "admin");

        // Authentication filter
        before((req, res) -> {
            String user = req.queryParams("user");
            String password = req.queryParams("password");
            
            String dbPassword = users.get(user);
            if (!(password != null && password.equals(dbPassword))) {
                halt(401, "Unauthorized");
            }
        });

        get("/hello", (req, res) -> "Hello World!");
        
        after("/hello", (req, res) -> 
            res.header("X-Custom-Header", "Added by filter")
        );
    }
}
```

### Static Files

```java
import static spark.Spark.*;

public class StaticResources {
    public static void main(String[] args) {
        // Serve static files from /public in classpath
        staticFileLocation("/public");
        
        get("/hello", (req, res) -> "Hello World!");
    }
}
```

### JSON Example

```java
import static spark.Spark.*;

public class JsonExample {
    public static void main(String[] args) {
        get("/hello", "application/json", (req, res) -> 
            "{\"message\": \"Hello World\"}"
        );
    }
}
```

For more examples, see the [source code](https://github.com/sparkjava-community/spark/tree/main/src/test/java/sparkTest/examples).

---

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Building from Source

```bash
git clone https://github.com/sparkjava-community/spark.git
cd spark
gradle clean build
```

### Running Tests

```bash
gradle test
```

**Note:** Requires JDK 21 or newer.

---

## 👥 Maintainers

- [@KainoVaii](https://github.com/KainoVaii) - Maintainer, uses Spark in [Obsidian Framework](https://github.com/KainoVaii/obsidian)

**Looking for co-maintainers!** If you're interested in helping maintain Spark, please open an [issue](https://github.com/sparkjava-community/spark/issues).

*** For jakarta modern fork Leandro Andrade (https://github.com/kyogoiano) is the current mantainer and feature releaser  

---

## 📜 License

Apache License 2.0 - Same as original Spark Framework

---

## 🙏 Credits

- **Original Creator:** [Per Wendel](https://github.com/perwendel)
- **Original Contributors:** [All contributors](https://github.com/perwendel/spark/graphs/contributors)
- **Original Project:** [perwendel/spark](https://github.com/perwendel/spark)

---
