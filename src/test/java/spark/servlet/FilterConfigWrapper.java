package spark.servlet;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

import java.util.Enumeration;


public record FilterConfigWrapper(FilterConfig delegate) implements FilterConfig {

    /**
     * @return filter name
     * @see FilterConfig#getFilterName()
     */
    public String getFilterName() {
        return delegate.getFilterName();
    }

    /**
     * @param name
     * @return
     * @see FilterConfig#getInitParameter(String)
     */
    public String getInitParameter(String name) {
        if (name.equals("applicationClass")) {
            return "spark.servlet.MyApp";
        }
        return delegate.getInitParameter(name);
    }

    /**
     * @return
     * @see FilterConfig#getInitParameterNames()
     */
    public Enumeration<String> getInitParameterNames() {
        return delegate.getInitParameterNames();
    }

    /**
     * @return
     * @see FilterConfig#getServletContext()
     */
    public ServletContext getServletContext() {
        return delegate.getServletContext();
    }

}
