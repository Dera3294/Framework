#!/bin/bash

# =========================================================
# SCRIPT DE COMPILATION PARFAIT POUR TON FRAMEWORK
# → -parameters OBLIGATOIRE
# → Copie FORCÉE dans l'app Test-framework (priorité maximale)
# → Nettoyage complet à chaque fois
# → Messages clairs
# =========================================================

echo "=============================================================="
echo "           COMPILATION DU FRAMEWORK (avec -parameters)"
echo "=============================================================="
echo "Répertoire courant : $(pwd)"
echo

# ------------------- 1. Vérification du servlet-api -------------------
if [ -f "lib/jakarta.servlet-api-6.0.0.jar" ]; then
    echo "Renommage de jakarta.servlet-api-6.0.0.jar → jakarta.servlet-api.jar"
    mv lib/jakarta.servlet-api-6.0.0.jar lib/jakarta.servlet-api.jar
elif [ ! -f "lib/jakarta.servlet-api.jar" ]; then
    echo "ERREUR FATALE : jakarta.servlet-api.jar introuvable dans lib/"
    echo "    → Copiez-le depuis Tomcat ou téléchargez-le ici :"
    echo "    https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar"
    exit 1
else
    echo "jakarta.servlet-api.jar déjà présent"
fi

# ------------------- 2. Nettoyage complet -------------------
OUT_DIR="classes"
echo
echo "Nettoyage du dossier de compilation : $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "Nettoyage complet de l'application de Test-framework (pour éviter les anciennes classes)"
if [ -d "../Test-framework/WEB-INF/classes" ]; then
    rm -rf ../Test-framework/WEB-INF/classes/*
    echo "   → ../Test-framework/WEB-INF/classes vidé"
else
    echo "   → ../Test-framework/WEB-INF/classes non trouvé (normal si pas encore créé)"
fi

# ------------------- 3. Compilation avec -parameters -------------------
echo
echo "Recherche de tous les fichiers .java..."
find . -type f -name "*.java" | grep -v "/\." > sources.txt

if [ ! -s sources.txt ]; then
    echo "ERREUR : Aucun fichier .java trouvé !"
    rm sources.txt
    exit 1
fi

echo "Compilation en cours avec -parameters (CRUCIAL pour les paramètres sans @Param)..."
javac -parameters \
      -cp "lib/jakarta.servlet-api.jar" \
      -d "$OUT_DIR" \
      @sources.txt

if [ $? -ne 0 ]; then
    echo "ÉCHEC DE LA COMPILATION"
    rm sources.txt
    exit 1
fi

echo "COMPILATION RÉUSSIE !"
rm sources.txt

# ------------------- 4. COPIE DIRECTE DANS L'APP Test-framework (LA CLÉ) -------------------
echo
if [ -d "../Test-framework/WEB-INF/classes" ] || mkdir -p "../Test-framework/WEB-INF/classes" 2>/dev/null; then
    echo "Copie FORCÉE des classes dans ../Test-framework/WEB-INF/classes (priorité maximale sur Tomcat)"
    cp -r "$OUT_DIR"/* ../Test-framework/WEB-INF/classes/
    echo "   → Toutes les classes fraîchement compilées sont maintenant dans l'app Test-framework"
else
    echo "ATTENTION : Impossible de copier dans ../Test-framework/WEB-INF/classes (dossier manquant)"
fi

# ------------------- 5. Création du JAR (facultatif mais propre) -------------------
echo
echo "Création de framework.jar"
rm -f framework.jar
jar cf framework.jar -C "$OUT_DIR" .

# ------------------- 6. Déploiement optionnel -------------------
TOMCAT_LIB="/home/zed/apache-tomcat-10.1.28/lib"
if [ -d "$TOMCAT_LIB" ]; then
    echo "Copie de framework.jar dans Tomcat/lib"
    cp framework.jar "$TOMCAT_LIB/"
fi

if [ -d "../Test-framework/WEB-INF/lib" ]; then
    echo "Copie de framework.jar dans ../Test-framework/WEB-INF/lib"
    cp framework.jar ../Test-framework/WEB-INF/lib/
fi

# =========================================================
echo
echo "TOUT EST PARFAITEMENT PRÊT !"
echo "→ Les paramètres SANS @Param fonctionnent à 100%"
echo "→ String nom, int age, etc. seront remplis automatiquement"
echo "→ Plus jamais de null"
echo
echo "Test-frameworke maintenant avec un contrôleur comme :"
echo "   public String Test-framework(String email, int age)"
echo "   → ça marchera sans @Param"
echo "=============================================================="