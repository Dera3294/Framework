import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import framework.controllers.Controller;
import framework.annotation.UrlHandler;
import framework.scanner.Scanner;
import framework.scanner.ModelView;

public class FrontServlet extends HttpServlet {
    
    private RequestDispatcher defaultDispatcher;
    private Map<String, RouteInfo> urlMappings = new HashMap<>();

    private static class RouteInfo {
        Class<?> controllerClass;
        Method method;
    }

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        try {
            // === 🔍 Nouvelle logique : détecter automatiquement les classes à scanner ===
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            if (classesPath == null) {
                System.err.println("❌ Impossible de trouver le dossier WEB-INF/classes");
                return;
            }

            File classesDir = new File(classesPath);
            if (!classesDir.exists()) {
                System.err.println("❌ Dossier WEB-INF/classes introuvable : " + classesPath);
                return;
            }

            System.out.println("🔍 Scan du dossier : " + classesPath);

            List<Class<?>> allClasses = Scanner.scanAllClasses(classesDir, "");
            for (Class<?> cls : allClasses) {
                if (cls.isAnnotationPresent(Controller.class)) {
                    for (Method method : cls.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(UrlHandler.class)) {
                            UrlHandler handler = method.getAnnotation(UrlHandler.class);
                            RouteInfo info = new RouteInfo();
                            info.controllerClass = cls;
                            info.method = method;
                            urlMappings.put(handler.url(), info);
                        }
                    }
                }
            }

            ///AJOUT : stocker les routes dans le ServletContext pour y accéder plus tard
            ServletContext contexte = getServletContext();
            contexte.setAttribute("routesInfo", urlMappings);
            System.out.println("✅ URLs détectées : " + urlMappings.keySet());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultDispatcher.forward(request, response);
            return;
        }

        if (urlMappings.containsKey(path)) {
            RouteInfo info = urlMappings.get(path);
            try {
                Object controller = info.controllerClass.getDeclaredConstructor().newInstance();
                Object result = info.method.invoke(controller);

                // 🔹 Vérifie si le résultat est un ModelView
                if (result instanceof ModelView mv) {
                    
                    // On place les données dans la requête
                    for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                        request.setAttribute(entry.getKey(), entry.getValue());
                    }
                    String jspPath = "/WEB-INF/views/" + mv.getView();
                    RequestDispatcher dispatcher = request.getRequestDispatcher(jspPath);
                    dispatcher.forward(request, response);
                } else {
                    showDetailedResponse(response, path, info, result);
                }

            } catch (Exception e) {
                e.printStackTrace();
                showError(response, path, e.getMessage());
            }
        } else {
            showResponse(response, path, "❌ L’URL n’existe pas dans les contrôleurs");
        }
    }

    private void showDetailedResponse(HttpServletResponse response, String path, RouteInfo info, Object result)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><body>");
            out.println("<h3>URL : " + path + "</h3>");
            out.println("<p>Classe : " + info.controllerClass.getSimpleName() + "</p>");
            out.println("<p>Méthode : " + info.method.getName() + "()</p>");
            if (result != null) out.println("<hr>" + result);
            out.println("</body></html>");
        }
    }

    private void showResponse(HttpServletResponse response, String path, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><body>" + message + "</body></html>");
        }
    }

    private void showError(HttpServletResponse response, String path, String msg) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><body><h3>Erreur : " + msg + "</h3></body></html>");
        }
    }
}