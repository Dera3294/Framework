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

# Compiler avec lib/jakarta.servlet-api.jar dans le classpath
javac -cp lib/jakarta.servlet-api.jar src/FrontServlet.java

# Vérifier si la compilation a réussi
if [ $? -ne 0 ]; then
    echo "Erreur lors de la compilation"
    exit 1
fi

# Déplacer FrontServlet.class à la racine pour le JAR
mv src/FrontServlet.class .

# Créer le JAR avec FrontServlet.class à la racine
jar cvf framework.jar FrontServlet.class

# Nettoyage optionnel
# rm FrontServlet.class

echo "Compilation et génération de framework.jar terminées."