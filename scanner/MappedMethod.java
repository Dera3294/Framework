package framework.scanner;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import framework.annotation.Json;

public class MappedMethod {
    private final Class<?> controllerClass;
    private final Method method;
    private final String urlPattern;
    private final String httpMethod; // "GET", "POST", ou "ALL" si @UrlHandler

    public MappedMethod(Class<?> controllerClass, Method method, String urlPattern, String httpMethod) {
        this.controllerClass = controllerClass;
        this.method = method;
        this.urlPattern = urlPattern;
        this.httpMethod = httpMethod;
    }

    public Class<?> getControllerClass() { return controllerClass; }
    public Method getMethod() { return method; }
    public String getUrlPattern() { return urlPattern; }
    public String getHttpMethod() { return httpMethod; }

    public Object invoke(HttpServletRequest request, String actualPath) throws Exception {
        Object controller = controllerClass.getDeclaredConstructor().newInstance();

        Object[] args = Scanner.mapFormParametersToMethodArgs(
                method,
                request,
                urlPattern,
                actualPath
        );

        return method.invoke(controller, args);
    }

    public boolean isJson() {
        return method.isAnnotationPresent(Json.class);
    }

    @Override
    public String toString() {
        return "[" + httpMethod + "] " + urlPattern + " → "
                + controllerClass.getSimpleName() + "." + method.getName() + "()";
    }
}
