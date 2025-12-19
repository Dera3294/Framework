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
import jakarta.servlet.http.HttpServletResponse;
import framework.annotation.*;
import framework.controllers.Controller;
import java.net.MalformedURLException;
import framework.utils.ApiResponse;
import framework.utils.JsonUtils;
import java.io.IOException;
import jakarta.servlet.http.Part;
import framework.utils.UploadedFile;
import java.io.InputStream;

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
        public static Object[] mapFormParametersToMethodArgs(
            Method method,
            HttpServletRequest request,
            String urlPattern,
            String actualPath) {

        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        // Variables dynamiques dans l’URL (ex: /user/{id})
        Map<String, String> pathVars = extractPathVariables(urlPattern, actualPath);

        // Paramètres simples (form data)
        Map<String, String[]> formParams = request.getParameterMap();

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Class<?> paramType = p.getType();
            Object value = null;

            try {
                // 1️ Objets de contexte
                if (paramType == HttpServletRequest.class) {
                    value = request;
                } else if (paramType == jakarta.servlet.http.HttpSession.class) {
                    value = request.getSession();
                }

                //2️ Paramètre avec @Param
                else if (p.isAnnotationPresent(framework.annotation.Param.class)) {
                    String name = p.getAnnotation(framework.annotation.Param.class).value();
                    String v = request.getParameter(name);
                    value = convertValue(v, paramType);
                }

                // 3️ Map<String,Object>
                else if (Map.class.isAssignableFrom(paramType)) {
                    Map<String, Object> map = new HashMap<>();
                    for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                        if (entry.getValue().length > 1)
                            map.put(entry.getKey(), Arrays.asList(entry.getValue()));
                        else
                            map.put(entry.getKey(), entry.getValue()[0]);
                    }
                    value = map;
                }

                // 4️ Variables dynamiques {id} dans l’URL
                else if (pathVars.containsKey(p.getName())) {
                    value = convertValue(pathVars.get(p.getName()), paramType);
                }

                // 5️ Types simples
                else if (paramType.isPrimitive()
                        || paramType == String.class
                        || Number.class.isAssignableFrom(paramType)
                        || paramType == Boolean.class) {
                    String v = request.getParameter(p.getName());
                    value = convertValue(v, paramType);
                }

                //6️ Tableaux ou listes (ex: Employe[])
                else if (paramType.isArray() || List.class.isAssignableFrom(paramType)) {
                    value = createArrayOrListFromForm(p, paramType, formParams);
                }

                // 7 Upload d’un seul fichier
                else if (paramType == framework.utils.UploadedFile.class) {
                    Part part = request.getPart(p.getName());
                    if (part != null && part.getSubmittedFileName() != null && part.getSize() > 0) {
                        byte[] bytes = part.getInputStream().readAllBytes();
                        value = new framework.utils.UploadedFile(
                                part.getSubmittedFileName(),
                                part.getContentType(),
                                part.getSize(),
                                bytes
                        );
                    }
                }

                // Upload : plusieurs fichiers
                else if (paramType.isArray()
                    && paramType.getComponentType() == framework.utils.UploadedFile.class) {

                    List<UploadedFile> files = new ArrayList<>();

                    // Détermine le nom du champ de formulaire
                    String paramName = p.getName();
                    if (p.isAnnotationPresent(framework.annotation.Param.class)) {
                        paramName = p.getAnnotation(framework.annotation.Param.class).value();
                    }

                    for (Part part : request.getParts()) {
                        if (part.getName().equals(paramName) &&
                            part.getSubmittedFileName() != null &&
                            part.getSize() > 0) {

                            byte[] bytes = part.getInputStream().readAllBytes();
                            files.add(new UploadedFile(
                                    part.getSubmittedFileName(),
                                    part.getContentType(),
                                    part.getSize(),
                                    bytes
                            ));
                        }
                    }

                    value = files.toArray(new UploadedFile[0]);
                }
            
            // ✅ 9️⃣ Objets complexes (ex: Departement, Employe)
            else {
                    Object instance = paramType.getDeclaredConstructor().newInstance();

                    for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                        String paramName = entry.getKey();
                        String[] values = entry.getValue();

                        if (paramName.startsWith(p.getName() + ".")) {
                            paramName = paramName.substring((p.getName() + ".").length());
                        }

                        setObjectFieldValue(instance, paramName, values);
                    }
                    value = instance;
                }

            } catch (Exception e) {
                e.printStackTrace();
                value = null;
            }

            args[i] = value;
        }

        return args;
    }



    public static void extractMultipartData(HttpServletRequest request,
                                            Map<String, String[]> formParams,
                                            Map<String, List<UploadedFile>> fileParams)
            throws Exception {

        request.setCharacterEncoding("UTF-8");

        for (Part part : request.getParts()) {
            String name = part.getName();

            if (part.getSubmittedFileName() != null && !part.getSubmittedFileName().isEmpty()) {
                try (InputStream input = part.getInputStream()) {
                    byte[] bytes = input.readAllBytes();
                    UploadedFile file = new UploadedFile(
                            part.getSubmittedFileName(),
                            part.getContentType(),
                            part.getSize(),
                            bytes
                    );
                    fileParams.computeIfAbsent(name, k -> new ArrayList<>()).add(file);
                }
            } else {
                String value = new String(part.getInputStream().readAllBytes(), "UTF-8");
                formParams.put(name, new String[]{value});
            }
        }
    }
    
    
    @SuppressWarnings("unchecked")
    private static Object createArrayOrListFromForm(Parameter parameter, Class<?> paramType, Map<String, String[]> formParams) throws Exception {
        String paramName = parameter.getName();
    
        if (paramType.isArray()) {
            Class<?> elementType = paramType.getComponentType();
            List<Object> list = new ArrayList<>();
    
            for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(paramName + "[")) {
                    int idxStart = key.indexOf('[') + 1;
                    int idxEnd = key.indexOf(']');
                    int idx = Integer.parseInt(key.substring(idxStart, idxEnd));
    
                    while (list.size() <= idx) {
                        list.add(elementType.getDeclaredConstructor().newInstance());
                    }
    
                    Object element = list.get(idx);
                    String remaining = key.substring(idxEnd + 2); // skip ]. 
                    setObjectFieldValue(element, remaining, entry.getValue());
                }
            }
    
            Object array = java.lang.reflect.Array.newInstance(elementType, list.size());
            for (int i = 0; i < list.size(); i++) {
                java.lang.reflect.Array.set(array, i, list.get(i));
            }
            return array;
    
        } else if (List.class.isAssignableFrom(paramType)) {
            ParameterizedType genericType = (ParameterizedType) parameter.getParameterizedType();
            Class<?> elementType = (Class<?>) genericType.getActualTypeArguments()[0];
            List<Object> list = new ArrayList<>();
    
            for (Map.Entry<String, String[]> entry : formParams.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(paramName + "[")) {
                    int idxStart = key.indexOf('[') + 1;
                    int idxEnd = key.indexOf(']');
                    int idx = Integer.parseInt(key.substring(idxStart, idxEnd));
    
                    while (list.size() <= idx) {
                        list.add(elementType.getDeclaredConstructor().newInstance());
                    }
    
                    Object element = list.get(idx);
                    String remaining = key.substring(idxEnd + 2);
                    setObjectFieldValue(element, remaining, entry.getValue());
                }
            }
            return list;
        }
    
        return null;
    }
    
    
    
    
    private static void setObjectFieldValue(Object obj, String paramName, String[] values) {
        try {
            if (obj == null || paramName == null) return;
    
            String[] parts = paramName.split("\\.");
            Object currentObj = obj;
    
            for (int i = 0; i < parts.length; i++) {
                String fieldName = parts[i];
                int listIndex = -1;
    
                // Gestion des listes : "es[0]"
                if (fieldName.contains("[") && fieldName.contains("]")) {
                    String baseName = fieldName.substring(0, fieldName.indexOf("["));
                    listIndex = Integer.parseInt(fieldName.substring(fieldName.indexOf("[") + 1, fieldName.indexOf("]")));
                    fieldName = baseName;
                }
    
                Field field = currentObj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
    
                if (i == parts.length - 1) {
                    Object converted = null;
                    if (values != null && values.length > 0) {
                        if (values.length > 1)
                            converted = Arrays.asList(values);
                        else
                            converted = convertValue(values[0], fieldType);
                    }
                    field.set(currentObj, converted);
                } else {
                    if (List.class.isAssignableFrom(fieldType)) {
                        List<Object> list = (List<Object>) field.get(currentObj);
                        if (list == null) {
                            list = new ArrayList<>();
                            field.set(currentObj, list);
                        }
    
                        ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                        Class<?> elementType = (Class<?>) genericType.getActualTypeArguments()[0];
    
                        while (list.size() <= listIndex) {
                            list.add(elementType.getDeclaredConstructor().newInstance());
                        }
    
                        Object element = list.get(listIndex);
                        String remainingPath = String.join(".", Arrays.copyOfRange(parts, i + 1, parts.length));
                        setObjectFieldValue(element, remainingPath, values);
                        return;
                    }
    
                    Object nested = field.get(currentObj);
                    if (nested == null) {
                        nested = fieldType.getDeclaredConstructor().newInstance();
                        field.set(currentObj, nested);
                    }
                    currentObj = nested;
                }
            }
    
        } catch (NoSuchFieldException ignored) {
            // Champ inexistant, ignore
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

    public static void sendJson(HttpServletResponse response, Object result, Exception ex) throws IOException {
        ApiResponse apiResponse;

        if (ex != null) {
            // Si une exception est passée, retour d'erreur JSON
            apiResponse = new ApiResponse("error", 400, null);
        } else {
            apiResponse = new ApiResponse("success", 200, result);
        }

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(apiResponse));
    }
}
