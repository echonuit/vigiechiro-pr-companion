#!/usr/bin/env bash
# OUTIL ENSEIGNANT (hors version etudiante, retire en passe A2).
#
# Rend les ecrans des features (sites, importation, qualification) en PNG pour les comparer aux maquettes du brief.
# S'appuie sur les <feature>.outils.CaptureEcrans / CaptureImport (rendu hors-ecran via SwingFXUtils).
# Tourne via la Headless Platform de JavaFX 26 (glass.platform=Headless, rendu logiciel sw) :
# aucune fenetre, aucun serveur d'affichage requis (CI, conteneur, session Wayland), plus besoin
# de xvfb. Le snapshot() de la Scene reste deterministe.
#
# Usage :
#   .github/assets/capture-screenshots.sh
#
# Sorties (dans .github/assets/) :
#   apercu-accueil.png              ecran d'accueil (chrome + cartes d'activites des features)
#   apercu-sites-mes-sites.png      ecran d'accueil M-Sites (cartes des sites)
#   apercu-sites-detail.png         M-Site-detail (fiche + points + tableau des passages)
#   apercu-sites-modale-point.png   modale d'edition d'un point d'ecoute
#   apercu-import-assistant.png     M-Import (assistant « Importer une nuit », cas standard)
#   apercu-qualification.png        M-Qualification (verification par echantillonnage, cas standard)
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
cd "$RACINE"

MAINS=(
  "fr.univ_amu.iut.commun.outils.CaptureAccueil"
  "fr.univ_amu.iut.sites.outils.CaptureEcrans"
  "fr.univ_amu.iut.importation.outils.CaptureImport"
  "fr.univ_amu.iut.qualification.outils.CaptureQualification"
)
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "[capture] Compilation des classes et ressources..."
./mvnw -q -DskipTests compile

for MAIN in "${MAINS[@]}"; do
  echo "[capture] Rendu hors-ecran via la Headless Platform JavaFX 26 : $MAIN (cible : $ICI)..."
  ./mvnw -q org.codehaus.mojo:exec-maven-plugin:exec \
    -Dexec.executable="$JAVA_BIN" \
    -Dexec.classpathScope=runtime \
    -Dexec.args="--enable-native-access=ALL-UNNAMED,javafx.graphics -Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true -cp %classpath $MAIN" \
    -Dcapture.outDir="$ICI"
done

echo "[capture] PNG generes :"
ls -l "$ICI"/apercu-*.png
