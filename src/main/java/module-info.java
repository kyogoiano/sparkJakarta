module sparkJakarta {
    requires org.jetbrains.annotations;
    requires org.eclipse.jetty.ee10.servlet;
    requires org.eclipse.jetty.ee10.websocket.jetty.server;
    requires org.eclipse.jetty.ee10.websocket.jakarta.server;
    requires org.eclipse.jetty.websocket.server;
    exports spark;
    opens spark;
    exports spark.utils;
    exports spark.embeddedserver;
    exports spark.embeddedserver.jetty;
    exports spark.route;
    exports spark.staticfiles;
    exports spark.ssl;
    exports spark.embeddedserver.jetty.websocket;
    exports spark.globalstate;
    opens spark.globalstate;
    exports spark.routematch;
    exports spark.resource;
    exports spark.serialization;
    exports spark.servlet;
}
