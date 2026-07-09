#!/usr/bin/env bash
# OUTIL FOURNI : conserve dans la version etudiante. Relance en CI a chaque push sur main
# (workflow .github/workflows/capture-vues.yml) pour tenir la galerie d'apercus a jour : cote
# enseignant elle montre les ecrans de depart (placeholders + sites/accueil), cote etudiant elle
# suit l'avancement de son IHM.
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
# Sorties (dans .github/assets/) : un ou plusieurs apercu-*.png par vue. La liste FAISANT FOI est le
# manifeste .github/assets/captures.manifest (verifie en CI par check-captures.sh : chaque vue y a au
# moins une capture, et chaque capture declaree existe). On ne duplique donc PAS ici l'enumeration des
# fichiers (elle derivait) : chaque outil ci-dessous ecrit les PNG que son `main` produit, le manifeste
# recense l'ensemble. Le tableau de correspondance vue <-> capture(s) vit dans le manifeste.
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
cd "$RACINE"

MAINS=(
  "fr.univ_amu.iut.commun.outils.CaptureAccueil"
  "fr.univ_amu.iut.recherche.outils.CaptureRecherche"
  "fr.univ_amu.iut.sites.outils.CaptureEcrans"
  "fr.univ_amu.iut.importation.outils.CaptureImport"
  "fr.univ_amu.iut.qualification.outils.CaptureQualification"
  "fr.univ_amu.iut.passage.outils.CapturePassage"
  "fr.univ_amu.iut.lot.outils.CaptureLot"
  "fr.univ_amu.iut.multisite.outils.CaptureMultisite"
  "fr.univ_amu.iut.analyse.outils.CaptureAnalyse"
  "fr.univ_amu.iut.diagnostic.outils.CaptureDiagnostic"
  "fr.univ_amu.iut.audio.outils.CaptureSonsValidation"
  "fr.univ_amu.iut.audio.outils.CaptureSonsValidationFiltres"
  "fr.univ_amu.iut.audio.outils.CaptureSonsValidationColonnes"
  "fr.univ_amu.iut.audio.outils.CaptureSonsValidationLot"
  "fr.univ_amu.iut.audio.outils.CaptureCommentaireAudio"
  "fr.univ_amu.iut.audio.outils.CaptureValidationTadarida"
  "fr.univ_amu.iut.commun.outils.CaptureDialogues"
  "fr.univ_amu.iut.connexion.outils.CaptureConnexion"
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
