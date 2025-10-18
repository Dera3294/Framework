package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Annotation personnalisée pour associer une méthode à une URL
@Retention(RetentionPolicy.RUNTIME) // Accessible à l’exécution
@Target(ElementType.METHOD)         // Utilisable uniquement sur les méthodes
public @interface UrlHandler {
    String url(); // Un attribut obligatoire appelé "url"
}
