import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.net.*;
import framework.controllers.Controller;
import framework.annotation.UrlHandler;
import framework.scanner.Scanner;

public class FrontServlet extends HttpServlet {
    
    private RequestDispatcher defaultDispatcher;
    private Map<String, Class<?>> urlMappings = new HashMap<>();

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
                    for (var method : cls.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(UrlHandler.class)) {
                            UrlHandler mapping = method.getAnnotation(UrlHandler.class);
                            urlMappings.put(mapping.url(), cls);
                        }
                    }
                }
            }
            System.out.println("✅ URLs détectées : " + urlMappings.keySet());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            defaultServe(request, response);
        } else {
            // === 🔍 Vérification via annotations ===
            if (urlMappings.containsKey(path)) {
                Class<?> controller = urlMappings.get(path);
                if (controller.isAnnotationPresent(Controller.class)) {
                    showResponse(response, path, "✅ L’URL existe (mappée) dans le contrôleur : " + controller.getSimpleName());
                } else {
                    showResponse(response, path, "❌ L’URL existe mais la classe " + controller.getSimpleName() + " n’est pas un contrôleur");
                }
            } else {
                customServe(request, response);
            }
        }
    }

    private void showResponse(HttpServletResponse response, String path, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><body>");
            out.println("URL saisie : " + path + "<br>");
            out.println(message);
            out.println("</body></html>");
        }
    }

    private void defaultServe(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        defaultDispatcher.forward(request, response);
    }

    private void customServe(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        showResponse(response, request.getRequestURI(), "❌ L’URL n’existe pas dans les contrôleurs");
    }
}