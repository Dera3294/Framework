package framework.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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

    // 🔹 Parcours de tous les paramètres de la méthode du contrôleur
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Object argValue = null;

            // ----------------------------------------------------------
            // 🆕 1️⃣ NOUVELLE FONCTIONNALITÉ : support Map<String, Object>
            // ----------------------------------------------------------
            if (Map.class.isAssignableFrom(p.getType())) {
                Map<String, Object> formMap = new HashMap<>();

                // 🔸 1.1 Récupérer tous les champs simples du formulaire
                Map<String, String[]> allParams = request.getParameterMap();
                for (Map.Entry<String, String[]> entry : allParams.entrySet()) {
                    String key = entry.getKey();
                    String[] values = entry.getValue();

                    if (values != null && values.length > 0) {
                        if (values.length == 1) {
                            // Champ unique (ex: text, email, hidden...)
                            formMap.put(key, values[0]);
                        } else {
                            // Champs multiples (checkbox, select multiple…)
                            formMap.put(key, Arrays.asList(values));
                        }
                    }
                }

                // 🔸 1.2 Récupérer les fichiers envoyés (multipart/form-data)
                try {
                    for (jakarta.servlet.http.Part part : request.getParts()) {
                        // Vérifie si c’est bien un fichier uploadé
                        if (part.getSubmittedFileName() != null && part.getSize() > 0) {
                            formMap.put(part.getName(), part); // Stocke directement l’objet Part
                        }
                    }
                } catch (Exception e) {
                    // Pas de fichier ou requête non multipart : on ignore simplement
                }

                // Stocker la Map complète comme argument
                argValue = formMap;
            }

            // ----------------------------------------------------------
            // 🔹 2️⃣ PARAMÈTRES CLASSIQUES (avec @Param, sans @Param, ou {id})
            // ----------------------------------------------------------
            else {
                String value = null;

                // 2.1 Si le paramètre a l’annotation @Param
                if (p.isAnnotationPresent(Param.class)) {
                    String name = p.getAnnotation(Param.class).value();
                    value = request.getParameter(name);
                }

                // 2.2 Sinon, on essaie le nom du paramètre (si compilé avec -parameters)
                if ((value == null || value.isEmpty()) && p.isNamePresent()) {
                    value = request.getParameter(p.getName());
                }

                // 2.3 Sinon, on regarde dans les variables dynamiques de l’URL {id}
                if ((value == null || value.isEmpty()) && pathVars.containsKey(p.getName())) {
                    value = pathVars.get(p.getName());
                }

                // 2.4 Conversion automatique vers le bon type (int, long, double, String, etc.)
                argValue = convertValue(value, p.getType());
            }

            // Enregistre la valeur trouvée dans le tableau des arguments
            args[i] = argValue;
        }

        // 🔹 Retourne tous les arguments préparés pour l’invocation
        return args;
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
