import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import framework.controllers.Controller;
import framework.annotation.UrlHandler;
import framework.scanner.Scanner;
import framework.scanner.ModelView;
import framework.scanner.MappedMethod;

public class FrontServlet extends HttpServlet {
    
    private RequestDispatcher defaultDispatcher;
    private Map<String, List<MappedMethod>> urlMappings = new HashMap<>();

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        try {
            // 🔹 Scanner toutes les classes du projet
            String classesPath = getServletContext().getRealPath("/WEB-INF/classes");
            File baseDir = new File(classesPath);

            // 🔹 Charger toutes les routes automatiquement
            urlMappings = Scanner.loadAllRoutes(baseDir);

            ///AJOUT : stocker les routes dans le ServletContext pour y accéder plus tard
            ServletContext contexte = getServletContext();
            contexte.setAttribute("routesInfo", urlMappings);
            System.out.println("✅ Routes détectées :");
            Scanner.printRoutes(urlMappings);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.isEmpty() || path.equals("/")) path = "/index.html";

        // 🔹 Gestion automatique des ressources statiques
        if (Scanner.isStaticResource(path, getServletContext())) {
            defaultDispatcher.forward(request, response);
            return;
        }

        String httpMethod = request.getMethod().toUpperCase();
        MappedMethod mapped = Scanner.findMappedMethod(path, httpMethod, urlMappings);

        if (mapped != null) {
            executeRoute(path, request, response, mapped);
        } else {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            showResponse(response, path,
                "❌ Aucune méthode " + httpMethod + " trouvée pour l’URL : " + path);
        }
    }

    private void executeRoute(String path, HttpServletRequest request, HttpServletResponse response,
        MappedMethod mapped) throws IOException {
        try {
            Object result = mapped.invoke(request, path);

            if (result instanceof ModelView mv) {
                for (var entry : mv.getData().entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            String jspPath = "/WEB-INF/views/" + mv.getView();
            RequestDispatcher dispatcher = request.getRequestDispatcher(jspPath);
            dispatcher.forward(request, response);
            } else {
                showDetailedResponse(response, path, mapped, result);
            }

        } catch (Exception e) {
                e.printStackTrace();
                showError(response, path, e.getMessage());
        }
}

    private void showDetailedResponse(HttpServletResponse response, String path, MappedMethod mapped, Object result)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html><body>");
            out.println("<h3>[" + mapped.getHttpMethod() + "] " + path + "</h3>");
            out.println("<p>Classe : " + mapped.getControllerClass().getSimpleName() + "</p>");
            out.println("<p>Méthode : " + mapped.getMethod().getName() + "()</p>");
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