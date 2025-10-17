import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;

public class FrontServlet extends HttpServlet {
    
    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() throws ServletException {
        // Initialisation du dispatcher par défaut pour servir les ressources
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Extraction du chemin relatif de la ressource demandée
        String path = request.getRequestURI().substring(request.getContextPath().length());
        
        // Gérer l'URL racine ("/")
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html"; 
        }

        // Vérifier si la ressource existe (jpg, pdf, html, etc.)
        boolean resourceExists = getServletContext().getResource(path) != null;

        if (resourceExists) {
            // Si la ressource existe, déléguer au dispatcher par défaut
            defaultServe(request, response);
        } else {
            // Si la ressource n'existe pas, générer une réponse personnalisée
            customServe(request, response);
        }
    }

    private void defaultServe(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Transférer la requête au dispatcher par défaut pour servir la ressource
        defaultDispatcher.forward(request, response);
    }

    private void customServe(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Générer une réponse HTML simple pour les ressources non trouvées
        try (PrintWriter out = response.getWriter()) {
            response.setContentType("text/html;charset=UTF-8");
            out.println("<html><body>");
            out.println("URL saisie : " + request.getRequestURL().toString());
            out.println("</body></html>");
        }
    }
}