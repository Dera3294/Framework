package framework.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import framework.annotation.*;
import framework.controllers.Controller;
import java.net.MalformedURLException;

public class Scanner {

      // ------------------------------
    // 🔹 1. SCAN DE CLASSES CONTROLEUR
    // ------------------------------
    public static Map<String, List<MappedMethod>> loadAllRoutes(File baseDir) {
        Map<String, List<MappedMethod>> urlMappings = new HashMap<>();
        List<Class<?>> allClasses = scanAllClasses(baseDir, "");

        for (Class<?> cls : allClasses) {
            if (cls.isAnnotationPresent(Controller.class)) {
                for (Method method : cls.getDeclaredMethods()) {
                    String url = null;
                    String httpMethod = "GET"; // par défaut

                    // ✅ Support des 3 types d’annotations
                    if (method.isAnnotationPresent(UrlHandler.class)) {
                        url = method.getAnnotation(UrlHandler.class).url();
                        httpMethod = "ALL"; // accepte GET & POST
                    } else if (method.isAnnotationPresent(UrlGet.class)) {
                        url = method.getAnnotation(UrlGet.class).value();
                        httpMethod = "GET";
                    } else if (method.isAnnotationPresent(UrlPost.class)) {
                        url = method.getAnnotation(UrlPost.class).value();
                        httpMethod = "POST";
                    }

                    if (url != null) {
                        MappedMethod mapped = new MappedMethod(cls, method, url, httpMethod);
                        urlMappings.computeIfAbsent(url, k -> new ArrayList<>()).add(mapped);
                    }
                }
            }
        }
        return urlMappings;
    }

    // ------------------------------
    // 🔹 2. AFFICHAGE DES ROUTES (DEBUG)
    // ------------------------------
    public static void printRoutes(Map<String, List<MappedMethod>> routes) {
        routes.forEach((url, methods) ->
            methods.forEach(m -> System.out.println(" → " + m))
        );
    }

    // ------------------------------
    // 🔹 3. TROUVER LA ROUTE MATCHÉE
    // ------------------------------
        // ------------------------------
    // 2. TROUVER UNE ROUTE MATCHÉE
    // ------------------------------
    public static MappedMethod findMappedMethod(String path, String httpMethod, Map<String, List<MappedMethod>> urlMappings) {
        for (Map.Entry<String, List<MappedMethod>> entry : urlMappings.entrySet()) {
            String pattern = entry.getKey();

            for (MappedMethod mapped : entry.getValue()) {
                String routeMethod = mapped.getHttpMethod();

                // Cas 1 : route exacte (GET/POST/ALL)
                if (pattern.equals(path)
                        && (routeMethod.equalsIgnoreCase(httpMethod) || routeMethod.equals("ALL"))) {
                    return mapped;
                }

                // Cas 2 : route avec {param}
                if (pattern.contains("{") && pattern.contains("}")) {
                    String regex = pattern.replaceAll("\\{[^/]+\\}", "[^/]+");
                    if (path.matches(regex)
                            && (routeMethod.equalsIgnoreCase(httpMethod) || routeMethod.equals("ALL"))) {
                        return mapped;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------
    // 🔹 4. DETECTION DES FICHIERS STATIQUES
    // ------------------------------
    public static boolean isStaticResource(String path, ServletContext context) {
        String[] staticExt = {".html", ".htm", ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico"};
        for (String ext : staticExt) if (path.endsWith(ext)) return true;
        try {
            return context.getResource(path) != null;
        } catch (MalformedURLException e) {
            return false;
        }
    }

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

// ------------------------------
    // 🔹 6. MAPPING DES PARAMÈTRES DE FORMULAIRE
    // ------------------------------
    public static Object[] mapFormParametersToMethodArgs(Method method,
                                                     HttpServletRequest request,
                                                     String urlPattern,
                                                     String actualPath) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];

    // 🔹 Extraction des variables dynamiques dans l'URL (ex: /etudiant/{id})
    Map<String, String> pathVars = extractPathVariables(urlPattern, actualPath);
    // Récupère tous les paramètres du formulaire
    Map<String, String[]> formParams = request.getParameterMap();

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Class<?> paramType = p.getType();
            Object value = null;

            // --- 1️⃣ Cas : @Param explicite ---
            if (p.isAnnotationPresent(Param.class)) {
                String name = p.getAnnotation(Param.class).value();
                String v = request.getParameter(name);
                value = convertValue(v, paramType);
            }

            // --- 2️⃣ Cas : Map<String, Object> ---
            else if (Map.class.isAssignableFrom(paramType)) {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                    if (entry.getValue().length > 1) {
                        map.put(entry.getKey(), Arrays.asList(entry.getValue()));
                    } else {
                        map.put(entry.getKey(), entry.getValue()[0]);
                    }
                }
                value = map;
            }

            // --- 3️⃣ Cas : variable dans l’URL (path variable) ---
            else if (pathVars.containsKey(p.getName())) {
                value = convertValue(pathVars.get(p.getName()), paramType);
            }

            // --- 4️⃣ Cas : types simples (String, int, double, etc.) ---
            else if (paramType.isPrimitive() ||
                    paramType == String.class ||
                    Number.class.isAssignableFrom(paramType) ||
                    paramType == Boolean.class) {
                String v = request.getParameter(p.getName());
                value = convertValue(v, paramType);
            }

            // --- 5️⃣ Cas : objet complexe (classe Java personnalisée) ---
            else {
                try {
                    Object instance = paramType.getDeclaredConstructor().newInstance();

                    // 🔹 Correction : injecte TOUS les champs du formulaire
                    // sans exiger de préfixe "parametre." (comme employe.)
                    for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                        String paramName = entry.getKey();
                        String[] values = entry.getValue();

                        // Si le champ commence par le nom de la variable (ex: "employe.")
                        // on enlève ce préfixe avant injection
                        if (paramName.startsWith(p.getName() + ".")) {
                            paramName = paramName.substring((p.getName() + ".").length());
                        }

                        try {
                            String fieldName = paramName.split("\\.")[0];
                            boolean hasField = Arrays.stream(paramType.getDeclaredFields())
                                                    .anyMatch(f -> f.getName().equals(fieldName));

                            if (hasField || paramName.contains(".")) {
                                setObjectFieldValue(instance, paramName, values);
                            }
                        } catch (Exception ignored) {}
                    }

                    value = instance;
                } catch (Exception e) {
                    e.printStackTrace();
                    value = null;
                }
            }

            args[i] = value;
        }
        return args;
    }

    private static void setObjectFieldValue(Object obj, String paramName, String[] values) {
        try {
            if (obj == null || paramName == null) return;
    
            // Exemple : "departements[0].nom" ou "adresse.ville"
            String[] parts = paramName.split("\\.");
            Object currentObj = obj;
    
            for (int i = 0; i < parts.length; i++) {
                String fieldName = parts[i];
    
                // --- 🔹 Cas spécial : champ de liste (ex: departements[0]) ---
                int listIndex = -1;
                if (fieldName.contains("[") && fieldName.contains("]")) {
                    String baseName = fieldName.substring(0, fieldName.indexOf("["));
                    listIndex = Integer.parseInt(fieldName.substring(fieldName.indexOf("[") + 1, fieldName.indexOf("]")));
                    fieldName = baseName;
                }
    
                Field field = currentObj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
    
                // --- 🔹 Dernier champ (injection finale de valeur simple) ---
                if (i == parts.length - 1) {
                    Object converted = null;
                    if (values != null && values.length > 0) {
                        if (values.length > 1)
                            converted = Arrays.asList(values);
                        else
                            converted = convertValue(values[0], fieldType);
                    }
                    field.set(currentObj, converted);
                }
                else {
                    // --- 🔹 Si c’est une liste d’objets ---
                    if (List.class.isAssignableFrom(fieldType)) {
                        List<Object> list = (List<Object>) field.get(currentObj);
                        if (list == null) {
                            list = new ArrayList<>();
                            field.set(currentObj, list);
                        }
    
                        // Déterminer le type générique de la liste
                        ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                        Class<?> elementType = (Class<?>) genericType.getActualTypeArguments()[0];
    
                        // Créer ou récupérer l’objet à l’index demandé
                        while (list.size() <= listIndex) {
                            list.add(elementType.getDeclaredConstructor().newInstance());
                        }
    
                        Object element = list.get(listIndex);
                        String remainingPath = String.join(".", Arrays.copyOfRange(parts, i + 1, parts.length));
                        setObjectFieldValue(element, remainingPath, values);
                        return; // stop ici car tout le reste est géré récursivement
                    }
    
                    // --- 🔹 Cas d’un sous-objet normal (ex: adresse.ville) ---
                    Object nested = field.get(currentObj);
                    if (nested == null) {
                        nested = fieldType.getDeclaredConstructor().newInstance();
                        field.set(currentObj, nested);
                    }
                    currentObj = nested;
                }
            }
        } catch (NoSuchFieldException e) {
            // Champ inexistant, on ignore pour éviter le crash
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> extractPathVariables(String pattern, String actualPath) {
        Map<String, String> vars = new HashMap<>();
        String[] pat = pattern.split("/");
        String[] act = actualPath.split("/");
        if (pat.length != act.length) return vars;

        for (int i = 0; i < pat.length; i++) {
            if (pat[i].startsWith("{") && pat[i].endsWith("}")) {
                String name = pat[i].substring(1, pat[i].length() - 1);
                vars.put(name, act[i]);
            }
        }
        return vars;
    }

    private static Object convertValue(String value, Class<?> type) {
        if (value == null) return null;
        try {
            if (type == String.class) return value;
            if (type == int.class || type == Integer.class) return Integer.parseInt(value);
            if (type == long.class || type == Long.class) return Long.parseLong(value);
            if (type == double.class || type == Double.class) return Double.parseDouble(value);
        } catch (Exception ignored) {}
        return null;
    }
}
