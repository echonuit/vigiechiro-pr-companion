#!/usr/bin/env python3
"""Rend les ecrans des features en PNG, pour les comparer aux maquettes du brief (porte du bash).

OUTIL FOURNI : conserve dans la version etudiante. Relance en CI a chaque push sur main par
`capture-vues.yml` pour tenir la galerie d apercus a jour - cote enseignant elle montre les ecrans de
depart, cote etudiant elle suit l avancement de son IHM.

S appuie sur les `<feature>.outils.Capture*`, qui rendent hors ecran via `SwingFXUtils`. Tourne via la
Headless Platform de JavaFX 26 (`glass.platform=Headless`, rendu logiciel `sw`) : aucune fenetre,
aucun serveur d affichage requis - CI, conteneur, session Wayland -, plus besoin de xvfb. Le
`snapshot()` de la Scene reste deterministe.

Sorties (dans `.github/assets/`) : un ou plusieurs `apercu-*.png` par vue. La liste FAISANT FOI est
le manifeste `captures.manifest`, verifie en CI par `check_captures.py`. On ne duplique donc PAS ici
l enumeration des fichiers - elle derivait : chaque outil ci-dessous ecrit les PNG que son `main`
produit, le manifeste recense l ensemble.

## Le tableau MAINS est lu par un garde

`check_capture_mains.py` cherche dans CE fichier les litteraux `"fr.univ_amu.iut…"` pour verifier
qu aucun outil de capture porteur d un `main` n y manque, et il y cherche aussi les trois proprietes
d epinglage ci-dessous. Les ecrire autrement - une liste construite, un fichier a cote - rendrait ce
garde MUET sans qu il rougisse.

## Langue et fuseau EPINGLES (#3389)

Sans cela, un apercu ne montre pas le produit mais la machine qui l a rendu : JavaFX resout les
libelles par defaut de `ButtonType` via `Locale.getDefault()` - « Cancel » sur un runner anglais,
« Annuler » sur un poste francais, donc un bouton plus large, donc une fenetre auto-dimensionnee qui
n a plus les memes dimensions - et formate les horodatages dans le fuseau du systeme.

Ici et non dans `capture-vues.yml` : c est ce script que la CI ET un poste lancent. Un reglage pose
dans le workflow n aurait discipline que la CI, et deplace l ecart au lieu de le supprimer.

Par les proprietes JVM et non par `LC_ALL` : `fr_FR.UTF-8` n est pas forcement genere sur un runner,
auquel dire le contraire ne ferait que retomber en silence sur la locale par defaut. `user.language`
et `user.country` ne dependent, eux, d aucune locale systeme. `TZ` est double par `user.timezone`,
que la JVM lit en priorite.

La valeur est le francais, pas une locale neutre : la galerie documente un produit francophone, et
doit montrer ce que son utilisateur voit.

Usage : python3 .github/assets/capture_screenshots.py
"""

from __future__ import annotations

import os
import pathlib
import subprocess
import sys

ICI = pathlib.Path(__file__).resolve().parent
RACINE = ICI.parent.parent

MAINS = (
    "fr.univ_amu.iut.commun.outils.CaptureAccueil",
    "fr.univ_amu.iut.commun.outils.CaptureMenuOutils",
    "fr.univ_amu.iut.commun.outils.CaptureBandeauAnnonce",
    "fr.univ_amu.iut.maj.outils.CaptureAnnonceMiseAJour",
    "fr.univ_amu.iut.commun.outils.CaptureBandeauRetour",
    "fr.univ_amu.iut.commun.outils.CaptureEcranReglages",
    "fr.univ_amu.iut.recherche.outils.CaptureRecherche",
    "fr.univ_amu.iut.sites.outils.CaptureEcrans",
    "fr.univ_amu.iut.importation.outils.CaptureImport",
    "fr.univ_amu.iut.importation.outils.CaptureConfirmationsImport",
    "fr.univ_amu.iut.importation.outils.CaptureImportTransformes",
    "fr.univ_amu.iut.importation.outils.CaptureCompteRenduParticipation",
    "fr.univ_amu.iut.qualification.outils.CaptureQualification",
    "fr.univ_amu.iut.passage.outils.CapturePassage",
    "fr.univ_amu.iut.passage.outils.CaptureRefusRattachement",
    "fr.univ_amu.iut.lot.outils.CaptureLot",
    "fr.univ_amu.iut.lot.outils.CaptureCompteRenduDepot",
    "fr.univ_amu.iut.multisite.outils.CaptureCompteRenduReleve",
    "fr.univ_amu.iut.multisite.outils.CaptureMultisite",
    "fr.univ_amu.iut.multisite.outils.CaptureValeurHorsJeu",
    "fr.univ_amu.iut.saison.outils.CaptureSaison",
    "fr.univ_amu.iut.analyse.outils.CaptureAnalyse",
    "fr.univ_amu.iut.analyse.outils.CaptureActivite",
    "fr.univ_amu.iut.analyse.outils.CaptureSynthese",
    # État « référentiel absent » (#2351) : le seul chemin où l'écran ne s'ouvrait pas, et le
    # seul que rien n'exerçait. L'image le fige.
    "fr.univ_amu.iut.analyse.outils.CaptureSyntheseSansReferentiel",
    "fr.univ_amu.iut.diagnostic.outils.CaptureDiagnostic",
    "fr.univ_amu.iut.audit.outils.CaptureAudit",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidation",
    "fr.univ_amu.iut.audio.outils.CaptureAvisValidateur",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidationFiltres",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidationLieu",
    "fr.univ_amu.iut.audio.outils.CaptureListeLieu",
    "fr.univ_amu.iut.audio.outils.CaptureMenuReferences",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidationColonnes",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidationDivergence",
    "fr.univ_amu.iut.audio.outils.CaptureSonsValidationLot",
    "fr.univ_amu.iut.audio.outils.CaptureCommentaireAudio",
    "fr.univ_amu.iut.audio.outils.CaptureValidationTadarida",
    "fr.univ_amu.iut.audio.outils.CaptureMenuLigne",
    "fr.univ_amu.iut.audio.outils.CapturePublicationCorrections",
    "fr.univ_amu.iut.audio.outils.CaptureExportSons",
    "fr.univ_amu.iut.audio.outils.CaptureImportVigieChiro",
    "fr.univ_amu.iut.commun.outils.CaptureFicheEspece",
    "fr.univ_amu.iut.commun.outils.CaptureDialogues",
    "fr.univ_amu.iut.commun.outils.CaptureCompteRendu",
    "fr.univ_amu.iut.connexion.outils.CaptureConnexion",
)

LOCALISATION = "-Duser.language=fr -Duser.country=FR -Duser.timezone=Europe/Paris"


def rendre() -> int:
    """La compilation, puis un rendu par outil, et le code de sortie qui va avec."""
    environnement = dict(os.environ)
    environnement["TZ"] = "Europe/Paris"
    java = os.environ.get("JAVA_HOME")
    java_bin = f"{java}/bin/java" if java else "java"

    # `flush` : sans lui, les lignes de ce script sortent APRES celles de Maven quand la sortie
    # est capturee, et le journal de CI ne dit plus quelle etape a produit quoi.
    print("[capture] Compilation des classes et ressources...", flush=True)
    compile = subprocess.run(
        ["./mvnw", "-q", "-DskipTests", "compile"], cwd=RACINE, env=environnement, check=False
    )
    if compile.returncode != 0:
        return compile.returncode

    for main in MAINS:
        print(
            f"[capture] Rendu hors-ecran via la Headless Platform JavaFX 26 : {main} (cible : {ICI})...",
            flush=True,
        )
        rendu = subprocess.run(
            [
                "./mvnw",
                "-q",
                "org.codehaus.mojo:exec-maven-plugin:exec",
                f"-Dexec.executable={java_bin}",
                "-Dexec.classpathScope=runtime",
                (
                    "-Dexec.args=--enable-native-access=ALL-UNNAMED,javafx.graphics "
                    "-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true "
                    f"{LOCALISATION} -cp %classpath {main}"
                ),
                f"-Dcapture.outDir={ICI}",
            ],
            cwd=RACINE,
            env=environnement,
            check=False,
        )
        if rendu.returncode != 0:
            return rendu.returncode

    print("[capture] PNG generes :", flush=True)
    # Le motif LITTERAL quand rien ne correspond, comme un glob de shell non developpe : `ls` dit
    # alors qu il ne trouve pas, plutot que de lister le dossier entier.
    trouves = sorted(str(p) for p in ICI.glob("apercu-*.png")) or [str(ICI / "apercu-*.png")]
    subprocess.run(["ls", "-l"] + trouves, check=False)
    return 0


if __name__ == "__main__":
    sys.exit(rendre())
