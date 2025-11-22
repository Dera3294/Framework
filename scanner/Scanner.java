package framework.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import framework.annotation.Param;

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

    // NOUVELLE FONCTION MAGIQUE – EXTRAIT {id} DE L'URL
    private static Map<String, String> extractPathVariables(String pattern, String actualPath) {
        Map<String, String> variables = new HashMap<>();
        
        if (!pattern.contains("{") || !pattern.contains("}")) {
            return variables; // pas de variable
        }

        String[] patternParts = pattern.split("/");
        String[] pathParts = actualPath.split("/");

        if (patternParts.length != pathParts.length) {
            return variables;
        }

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                variables.put(paramName, pathParts[i]);
            }
        }
        return variables;
    }

    /* Mappe automatiquement les valeurs du formulaire aux arguments de la méthode.
    * Si le nom du paramètre de la méthode correspond au nom d'un input du formulaire,
    * la valeur est injectée. Sinon, l'argument reste null.*/
    public static Object[] mapFormParametersToMethodArgs(Method method, HttpServletRequest request,String urlPattern, String actualPath){
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for(int i=0; i< params.length ; i++){
            Parameter p=params[i];
            String value= null;

             // Extraire les variables d'URL : /etudiant/{id} → id=12
            Map<String, String> pathVars = extractPathVariables(urlPattern, actualPath);

            // 1. @Param("xxx")
            if (p.isAnnotationPresent(Param.class)) {
                String name = p.getAnnotation(Param.class).value();
                value = request.getParameter(name);
            }
            // 2. Sinon : chercher d'abord dans les variables d'URL {id}
            else if (p.isNamePresent()) {
                String paramName = p.getName(); // ex: "id"
                if (pathVars.containsKey(paramName)) {
                    value = pathVars.get(paramName);
                } else {
                    value = request.getParameter(paramName);
                }
            }
            // Conversion automatique
            if (value != null && !value.trim().isEmpty()) {
                Class<?> type = p.getType();
                try {
                    if (type == String.class) {
                        args[i] = value;
                    } else if (type == int.class || type == Integer.class) {
                        args[i] = Integer.parseInt(value);
                    } else if (type == long.class || type == Long.class) {
                        args[i] = Long.parseLong(value);
                    } else if (type == double.class || type == Double.class) {
                        args[i] = Double.parseDouble(value);
                    } else {
                        args[i] = value;
                    }
                } catch (Exception e) {
                    args[i] = null;   // ← CORRIGÉ ICI
                }
            } else {
                args[i] = null;
            }
                
            }
            return args;
        }
}
