#!/usr/bin/env python3
"""Un emballage de distribution demarre-t-il ? (#3617, porte du bash en #5231.)

## Ce qu il verifie, et ce que la CI verifiait deja

`maven.yml` construit l app-image a chaque PR (#2256) **et la lance** (#2299), parce que la v2.32.3
etait installable et incapable de demarrer. Ce controle porte sur la **charge utile**.

Il ne portait sur **aucune enveloppe**. Or chaque emballage a ete choisi POUR CE QU IL PRESERVE, ce
qui est une autre facon de dire que chacun a une facon connue de casser : `tar.gz` garde le **bit
executable** du lanceur ; `ditto` est le seul outil qui preserve un bundle `.app` intact, un `zip -r`
cassant ses liens symboliques ; et `appimagetool` a deja fait echouer la release v2.21.0.

Trois emballages, trois modes de rupture documentes, et rien qui les ouvrait pour regarder.

## Deux conditions, pas une

Le processus doit **tenir debout**, ET sa sortie doit etre **exempte d erreur de chargement**. Une
exception sur un fil de fond ne tue pas le processus : s en tenir a la survie fabriquerait un vert
creux. C est la regle heritee de #2299.

## Le controle de la ligne de commande, et le cas qui le justifie

Un emballage peut ouvrir sa fenetre et n exposer AUCUNE commande : c est l etat dans lequel le
produit a vecu jusqu a #4071. On demande donc sa version au lanceur CLI - seule invocation qui ne
touche ni la base, ni le reseau, ni le dossier de travail - et on exige d avoir LU quelque chose : un
lanceur bati en sous-systeme graphique n ecrit NULLE PART et rend 0, si bien que sa panne est
indiscernable d un succes pour qui ne regarde que le code de sortie.

`--cli` reste FACULTATIF, et son absence se dit : sans elle, ce script ne prouve rien de la ligne de
commande, et un appelant qui l oublie doit le voir passer (ADR 2748).

## Windows n est pas couvert, et la raison a change de nature

La version bash l excluait parce qu elle etait du bash. Ce script-ci tournerait sous Windows, mais le
SUIVI d un lanceur en sous-systeme graphique depuis Git Bash n a jamais ete eprouve, et un faux echec
y bloquerait une publication. L exclusion reste donc, sur cette raison-la et non sur l ancienne. Le
`.msi`, lui, est installe ET lance par `winget.yml`.

## Pourquoi un script, et pas des lignes dans le workflow

Parce que la verification des enveloppes vit dans `release.yml`, **qu aucune PR ne traverse**. Une
etape ecrite la peut etre fusionnee cassee et ne se decouvrir qu au train suivant, en bloquant la
publication.

Usage : python3 .github/scripts/verifie_demarrage_emballage.py <lanceur> [--secondes N]
                [--libelle TEXTE] [--cli <lanceur CLI>]
        python3 .github/scripts/verifie_demarrage_emballage.py --auto-test
"""

from __future__ import annotations

import os
import pathlib
import re
import subprocess
import sys
import tempfile
import time

MOTIFS_DE_CHARGEMENT = re.compile(
    r"NoClassDefFoundError|ClassNotFoundException|Exception in Application start method"
)


def _ls_l(chemin: pathlib.Path) -> str:
    rendu = subprocess.run(["ls", "-l", str(chemin)], capture_output=True, text=True, check=False)
    return rendu.stdout.rstrip("\n")


def verifier_le_demarrage(lanceur: str | pathlib.Path, secondes: int, libelle: str) -> int:
    """Le lanceur tient-il debout, sans erreur de chargement ?"""
    chemin = pathlib.Path(lanceur)

    if not chemin.exists():
        print(f"::error::{libelle} : le lanceur est absent de l'emballage ({lanceur}).")
        print("   L'archive s'est décompressée, mais elle ne contient pas ce qu'elle promet.")
        return 1

    # Le bit executable est la raison d etre du choix de tar.gz : son absence est un defaut
    # d emballage, pas une curiosite de permissions. On le NOMME au lieu de le reparer par un chmod
    # complaisant.
    if not os.access(chemin, os.X_OK):
        print(f"::error::{libelle} : le lanceur a perdu son bit exécutable dans l'emballage.")
        print(f"   {_ls_l(chemin)}")
        print("   C'est exactement ce que le format tar.gz est censé préserver.")
        return 1

    environnement = dict(os.environ)
    environnement.pop("DISPLAY", None)
    environnement["JAVA_TOOL_OPTIONS"] = "-Dglass.platform=Headless"

    with tempfile.NamedTemporaryFile("w+", encoding="utf-8", delete=False) as fichier:
        journal = pathlib.Path(fichier.name)
    try:
        with journal.open("w", encoding="utf-8") as sortie:
            processus = subprocess.Popen(
                [str(chemin)], stdout=sortie, stderr=subprocess.STDOUT, env=environnement
            )

        tenu = 0
        while tenu < secondes and processus.poll() is None:
            time.sleep(1)
            tenu += 1

        if processus.poll() is None:
            processus.kill()
            processus.wait()
        else:
            code = processus.returncode
            print(
                f"::error::{libelle} : arrêté seul après {tenu} s (code {code}) - "
                "l'emballage ne démarre pas."
            )
            for ligne in journal.read_text(encoding="utf-8", errors="replace").splitlines()[:120]:
                print(ligne)
            return 1

        texte = journal.read_text(encoding="utf-8", errors="replace")
        if MOTIFS_DE_CHARGEMENT.search(texte):
            print(
                f"::error::{libelle} : reste en vie, mais son démarrage a levé une erreur de chargement."
            )
            trouvees = [
                f"{numero}:{ligne}"
                for numero, ligne in enumerate(texte.splitlines(), 1)
                if MOTIFS_DE_CHARGEMENT.search(ligne)
            ]
            for ligne in trouvees[:20]:
                print(ligne)
            return 1
    finally:
        journal.unlink(missing_ok=True)

    print(f"{libelle} : démarre depuis l'emballage et tient {tenu} s, sans erreur de chargement.")
    return 0


def verifier_la_ligne_de_commande(cli: str | pathlib.Path, libelle: str) -> int:
    """Le lanceur de ligne de commande rend-il sa version, et ECRIT-il quelque chose ?"""
    chemin = pathlib.Path(cli)

    if not chemin.exists():
        print(
            f"::error::{libelle} : l'emballage n'expose aucun lanceur de ligne de commande ({cli})."
        )
        print(
            "   La fenêtre s'ouvre, mais aucune des commandes n'est atteignable : c'est exactement ce que"
        )
        print("   #4071 a corrigé, et ce contrôle est là pour que ça ne revienne pas en silence.")
        return 1

    if not os.access(chemin, os.X_OK):
        print(f"::error::{libelle} : le lanceur de ligne de commande a perdu son bit exécutable.")
        print(f"   {_ls_l(chemin)}")
        return 1

    rendu = subprocess.run([str(chemin), "--version"], capture_output=True, text=True, check=False)
    sortie = rendu.stdout + rendu.stderr
    if rendu.returncode != 0:
        print(
            f"::error::{libelle} : le lanceur de ligne de commande rend {rendu.returncode} "
            "au lieu de sa version."
        )
        for ligne in sortie.splitlines()[:20]:
            print(ligne)
        return 1

    # Le cas qui justifie ce controle plutot qu un simple code de sortie. Un lanceur bati en
    # sous-systeme graphique n ecrit NULLE PART et rend 0 : sa panne est indiscernable d un succes.
    if not sortie.strip():
        print(f"::error::{libelle} : le lanceur de ligne de commande rend 0 mais n'écrit RIEN.")
        print("   Un lanceur sans console se comporte exactement ainsi ; le vert serait creux.")
        return 1

    print(f"{libelle} : la ligne de commande répond ({sortie.split(chr(10))[0]}).")
    return 0


def principal(arguments: list[str]) -> int:
    """Le dispatch des options, puis les deux controles."""
    if not arguments:
        print(
            "usage : verifie_demarrage_emballage.py <lanceur> [--secondes N] [--libelle TEXTE] "
            "[--cli <lanceur CLI>]",
            file=sys.stderr,
        )
        return 2
    lanceur = arguments[0]
    secondes, libelle, cli = 20, "", ""
    reste = arguments[1:]
    while reste:
        if reste[0] == "--secondes":
            secondes = int(reste[1])
            reste = reste[2:]
        elif reste[0] == "--libelle":
            libelle = reste[1]
            reste = reste[2:]
        elif reste[0] == "--cli":
            cli = reste[1]
            reste = reste[2:]
        else:
            print(f"option inconnue : {reste[0]}", file=sys.stderr)
            return 2

    if not libelle:
        libelle = pathlib.Path(lanceur).name

    if verifier_le_demarrage(lanceur, secondes, libelle) != 0:
        return 1

    if not cli:
        print(f"{libelle} : ligne de commande NON vérifiée (aucun --cli donné).")
        return 0

    return verifier_la_ligne_de_commande(cli, libelle)


FAUX = {
    "tient": "sleep 30",
    "meurt": 'echo "boum"; exit 3',
    "bavard": 'echo "Exception in Application start method"; sleep 30',
    "sans_droit": "sleep 30",
    "cli_repond": 'echo "VigieChiro - compagnon PR (CLI) 9.9.9"',
    "cli_muet": "exit 0",
    "cli_casse": 'echo "boum" >&2; exit 1',
}


def _auto_test() -> int:
    """Dix cas hors ligne, dont sept rouges verifies sur leur MESSAGE.

    Le message, et non le seul code : un `exit 1` peut venir du script lui-meme.
    """
    import contextlib
    import io

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-emballage-") as tmp:
        bac = pathlib.Path(tmp)
        for nom, corps in FAUX.items():
            faux = bac / nom
            faux.write_text(f"#!/usr/bin/env bash\n{corps}\n", encoding="utf-8")
            faux.chmod(0o755)
        (bac / "sans_droit").chmod(0o644)

        def joue(arguments: list[str]) -> tuple[str, int]:
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                code = principal(arguments)
            return tampon.getvalue(), code

        def verifie(attendu: int, fragment: str, libelle: str, lanceur: str) -> None:
            nonlocal echecs
            sortie, code = joue([str(bac / lanceur), "--secondes", "2", "--libelle", "essai"])
            if code != attendu:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                for l in sortie.splitlines():
                    print(f"      {l}")
                echecs = 1
                return
            if fragment not in sortie:
                print(f"  ✘ {libelle} : code correct, mais le message ne dit pas « {fragment} »")
                for l in sortie.splitlines():
                    print(f"      {l}")
                echecs = 1
                return
            print(f"  ✔ {libelle}")

        verifie(0, "tient 2 s", "un emballage sain passe", "tient")
        verifie(1, "arrêté seul", "un lanceur qui meurt est refusé", "meurt")
        # Le cas qui distingue ce controle d un simple « le processus vit-il ? ».
        verifie(1, "erreur de chargement", "un lanceur VIVANT mais en erreur est refusé", "bavard")
        # Le defaut propre a l emballage, invisible sur l app-image d origine.
        verifie(1, "bit exécutable", "un lanceur qui a perdu son bit x est refusé", "sans_droit")

        sortie, _ = joue(
            [str(bac / "absent-de-l-archive"), "--secondes", "2", "--libelle", "essai"]
        )
        if "absent de l'emballage" in sortie:
            print("  ✔ un lanceur absent de l archive est refusé")
        else:
            print("  ✘ un lanceur absent de l archive est refusé : message inattendu")
            for l in sortie.splitlines():
                print(f"      {l}")
            echecs = 1

        def verifie_cli(attendu: int, fragment: str, libelle: str, faux_cli: str) -> None:
            nonlocal echecs
            sortie, code = joue(
                [
                    str(bac / "tient"),
                    "--secondes",
                    "2",
                    "--libelle",
                    "essai",
                    "--cli",
                    str(bac / faux_cli),
                ]
            )
            if code != attendu:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                for l in sortie.splitlines():
                    print(f"      {l}")
                echecs = 1
                return
            if fragment not in sortie:
                print(f"  ✘ {libelle} : code correct, mais le message ne dit pas « {fragment} »")
                for l in sortie.splitlines():
                    print(f"      {l}")
                echecs = 1
                return
            print(f"  ✔ {libelle}")

        verifie_cli(
            0, "la ligne de commande répond", "une CLI qui rend sa version passe", "cli_repond"
        )
        verifie_cli(1, "n'écrit RIEN", "une CLI muette en code 0 est refusée", "cli_muet")
        verifie_cli(1, "au lieu de sa version", "une CLI qui échoue est refusée", "cli_casse")
        verifie_cli(
            1,
            "n'expose aucun lanceur",
            "une CLI absente de l emballage est refusée",
            "cli_jamais_cree",
        )

        # Sans `--cli`, le script le DIT au lieu de laisser croire qu il a tout verifie.
        sortie, _ = joue([str(bac / "tient"), "--secondes", "2", "--libelle", "essai"])
        if "NON vérifiée" in sortie:
            print("  ✔ sans --cli, l absence de vérification est annoncée")
        else:
            print("  ✘ sans --cli, l absence de vérification devrait être annoncée")
            for l in sortie.splitlines():
                print(f"      {l}")
            echecs = 1

    if echecs == 0:
        print(
            "Auto-test du démarrage des emballages : OK (10 cas, dont 7 rouges vérifiés sur leur message)."
        )
    else:
        print(
            "Auto-test du démarrage des emballages : ÉCHEC - la règle ne fait plus ce qu'elle promet."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(principal(sys.argv[1:]))
