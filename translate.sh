#!/bin/bash

# Afficher le répertoire courant pour débogage
echo "Répertoire courant : $(pwd)"

# Lister les fichiers dans lib/ pour débogage
echo "Fichiers dans Framework/lib/ :"
ls -l lib/

# Vérifier si lib/jakarta.servlet-api-6.0.0.jar existe et le renommer
if [ -f "lib/jakarta.servlet-api-6.0.0.jar" ]; then
    echo "Renommage de lib/jakarta.servlet-api-6.0.0.jar en lib/jakarta.servlet-api.jar"
    mv lib/jakarta.servlet-api-6.0.0.jar lib/jakarta.servlet-api.jar
    if [ $? -ne 0 ]; then
        echo "Erreur : Échec du renommage de lib/jakarta.servlet-api-6.0.0.jar"
        exit 1
    fi
elif [ -f "lib/jakarta.servlet-api.jar" ]; then
    echo "lib/jakarta.servlet-api.jar déjà présent, pas de renommage nécessaire"
else
    echo "Erreur : Aucun fichier jakarta.servlet-api*.jar trouvé dans Framework/lib/"
    echo "Vérifiez que lib/jakarta.servlet-api-6.0.0.jar est présent ou copiez-le depuis /home/zed/apache-tomcat-10.1.28/lib/jakarta.servlet-api.jar"
    exit 1
fi

# Vérifier les permissions de lib/jakarta.servlet-api.jar
ls -l lib/jakarta.servlet-api.jar

# Vérifier si src/FrontServlet.java existe
if [ ! -f "src/FrontServlet.java" ]; then
    echo "Erreur : src/FrontServlet.java introuvable"
    exit 1
fi

# Compiler toutes les classes nécessaires dans un dossier de sortie dédié
OUT_DIR="classes"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# Compiler FrontServlet (sans package) + annotations et controller (packagés)
javac -cp lib/jakarta.servlet-api.jar -d "$OUT_DIR" \
    src/FrontServlet.java \
    Annotation/UrlHandler.java \
    controllers/Controller.java \
    scanner/Scanner.java \
    scanner/ModelView.java 

# Vérifier si la compilation a réussi
if [ $? -ne 0 ]; then
    echo "Erreur lors de la compilation"
    exit 1
fi

# Créer le JAR contenant toutes les classes compilées (y compris les packages framework/*)
rm -f framework.jar
jar cvf framework.jar -C "$OUT_DIR" .

# Déploiement du JAR vers Tomcat et vers l'app de test si disponibles
TOMCAT_LIB_DEFAULT="/home/zed/apache-tomcat-10.1.28/lib"
TOMCAT_LIB_PATH="${TOMCAT_LIB:-$TOMCAT_LIB_DEFAULT}"

if [ -d "$TOMCAT_LIB_PATH" ]; then
    echo "Copie de framework.jar vers $TOMCAT_LIB_PATH"
    cp -f framework.jar "$TOMCAT_LIB_PATH/"
else
    echo "Info : Dossier Tomcat lib introuvable ($TOMCAT_LIB_PATH). Définissez TOMCAT_LIB pour activer la copie."
fi

# Copier aussi dans l'app de test si présente
if [ -d "../Test-Framework/WEB-INF/lib" ]; then
    echo "Copie de framework.jar vers ../Test-Framework/WEB-INF/lib"
    cp -f framework.jar ../Test-Framework/WEB-INF/lib/
fi

echo "Compilation et génération de framework.jar terminées, copies effectuées si possible."

# La compilation des classes de test est réalisée dans Test/deploy.sh