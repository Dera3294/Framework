package framework.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import jakarta.servlet.http.HttpServletRequest;

public class Scanner {

    public static List<Class<?>> scanAllClasses(File baseDir, String packageName) {
        List<Class<?>> classes = new ArrayList<>();

        for (File file : baseDir.listFiles()) {
            if (file.isDirectory()) {
                String newPackage = packageName.isEmpty()
                        ? file.getName()
                        : packageName + "." + file.getName();
                classes.addAll(scanAllClasses(file, newPackage));
            } else if (file.getName().endsWith(".class")) {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    String fullName = packageName.isEmpty() ? className : packageName + "." + className;
                    Class<?> cls = Class.forName(fullName);
                    classes.add(cls);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        return classes;
    }

    public static Object[] mapFormParametersToMethodArgs(Method method, HttpServletRequest request){
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for(int i=0; i< params.length ; i++){
            Parameter p=params[i];
            String value= null;
            if(p.isNamePresent()){
                value= request.getParameter(p.getName());

            }
            args[i]= convert(value,p.getType());
        }
        return args;
    }

    private static Object convert(String v, Class<?> t) {
        if (v == null || v.isBlank()) return null;
        try {
            if (t == String.class) return v.trim();
            if (t == int.class || t == Integer.class) return Integer.parseInt(v.trim());
            if (t == long.class || t == Long.class) return Long.parseLong(v.trim());
            if (t == double.class || t == Double.class) return Double.parseDouble(v.trim());
            if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(v.trim());
        } catch (Exception e) { }
        return v;
    }
}
