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
#   apercu-sites-mes-sites-vide.png M-Sites etat initial (aucun site : onboarding « premier site »)
#   apercu-sites-detail.png         M-Site-detail (fiche + points + tableau des passages)
#   apercu-sites-detail-sans-passage.png  M-Site-detail d'un site sans passage (tableau vide)
#   apercu-sites-modale-point.png   modale point : edition (champs pre-remplis)
#   apercu-sites-modale-point-creation.png  modale point : creation (formulaire vierge)
#   apercu-import-assistant.png     M-Import (assistant « Importer une nuit », cas standard)
#   apercu-import-en-cours.png      M-Import (import en cours : barre de progression deterministe, form gele)
#   apercu-import-melange.png       M-Import (cas « melange » : 2 enregistreurs detectes, avertissement non bloquant)
#   apercu-import-incoherence.png   M-Import (cas « incoherence » : journal/releve en desaccord avec les WAV, serie + date)
#   apercu-qualification-initial.png  M-Qualification (etat initial : selection generee, rien d'ecoute, sans verdict)
#   apercu-qualification.png        M-Qualification (avance : sequences ecoutees, verdict OK pose)
#   apercu-passage.png              M-Passage (pivot, statut Verifie : preparer le depot actif, validation verrouillee)
#   apercu-passage-depose.png       M-Passage (pivot, statut Depose : depot fait, validation deverrouillee)
#   apercu-passage-rattachement.png M-Passage (modale Modifier le rattachement : annee + n de passage)
#   apercu-validation-import.png    M-Vision-Tadarida (etat d'entree : avant import, bouton import)
#   apercu-validation-revue.png     M-Vision-Tadarida (revue : statuts varies + detail selectionne)
#   apercu-lot-preparer.png         M-Lot (Verifie coherent : recap + dossier, preparer actif)
#   apercu-lot-deposer.png          M-Lot (Pret a deposer : marquer depose actif)
#   apercu-lot-alertes.png          M-Lot (Verifie incoherent : alertes de coherence R14)
#   apercu-bibliotheque-vide.png    M-Bibliotheque (etat vide : aucun son de reference, export inactif)
#   apercu-bibliotheque-sons.png    M-Bibliotheque (peuplee : sons de reference, detail + ecoute, export actif)
#   apercu-multisite.png            M-Multisite (vue agregee : tableau complet, filtres, tri, export)
#   apercu-multisite-filtre.png     M-Multisite (tableau filtre par verdict OK : resume recalcule)
#   apercu-multisite-vues.png       M-Multisite (modale des vues sauvegardees, une vue selectionnee)
#   apercu-diagnostic.png           M-Diagnostic (releve present : courbe climat + anomalies + GPS)
#   apercu-diagnostic-sans-releve.png  M-Diagnostic (releve absent : absence signalee, anomalies seules)
set -euo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
cd "$RACINE"

MAINS=(
  "fr.univ_amu.iut.commun.outils.CaptureAccueil"
  "fr.univ_amu.iut.sites.outils.CaptureEcrans"
  "fr.univ_amu.iut.importation.outils.CaptureImport"
  "fr.univ_amu.iut.qualification.outils.CaptureQualification"
  "fr.univ_amu.iut.passage.outils.CapturePassage"
  "fr.univ_amu.iut.validation.outils.CaptureValidation"
  "fr.univ_amu.iut.lot.outils.CaptureLot"
  "fr.univ_amu.iut.bibliotheque.outils.CaptureBibliotheque"
  "fr.univ_amu.iut.multisite.outils.CaptureMultisite"
  "fr.univ_amu.iut.diagnostic.outils.CaptureDiagnostic"
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
