#!/usr/bin/env python3
"""Rapport hebdomadaire de conformité aux ADR.

Il fait tourner tous les scripts de vérification et agrège leur sortie normalisée en un tableau
Markdown, pour mesurer l'écart et la dette d'une semaine sur l'autre. Deux sections :

- **Cliquets** (`probable`) : chaque script rend `suspects=N | cliquet=M | verdict=…`. Le rapport
  rappelle la marge, et surtout signale les cliquets À RESSERRER - ceux dont la réalité (`suspects`)
  est passée sous la marge. C'est le carburant de la calibration : un cliquet qui ne descend pas quand
  le dépôt s'améliore laisse une marge morte où une régression pourrait se glisser sans rougir.
- **Loupes** (`humaine`) : indicatif seul. On compte les candidats à revoir, sans verdict.

Le rapport N'ÉCHOUE PAS sur une régression de cliquet : ce n'est pas son rôle (c'est celui du script,
en CI, sur la PR fautive). Son rôle est de donner l'image d'ensemble et de proposer les resserrements.

Usage : python3 scripts/adr/rapport.py [--markdown]
Sans --markdown, sortie texte lisible en console.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT  # noqa: E402

ICI = pathlib.Path(__file__).parent
LIGNE_CLIQUET = re.compile(r"^ADR (\d+) \| suspects=(\d+) \| cliquet=(\d+) \| verdict=(\S+)$", re.M)
LIGNE_LOUPE = re.compile(r"^LOUPE (\d+) \| candidats=(\d+)$", re.M)
# Le PLANCHER est la polarite inverse du cliquet, et il a sa propre ligne. Ce rapport ne la lisait
# pas : le garde des renvois annoncait « a-relever » a chaque passage, sans que rien ne le montre.
LIGNE_PLANCHER = re.compile(
    r"^PLANCHER (\d+) \| mesure=(\d+) \| plancher=(\d+) \| verdict=(\S+)$", re.M)


def executer(script: pathlib.Path) -> str:
    """Lance un script de vérification et rend sa sortie. Le code de sortie est ignoré : le rapport
    observe, il ne juge pas - un cliquet en régression fait déjà rougir la CI ailleurs.

    Le script tourne DANS le dépôt, pas dans le répertoire de l'appelant (issue #4781). Sans `cwd`,
    chaque garde héritait du répertoire courant : lancé d'ailleurs, cinq cliquets sur dix-huit
    rendaient une autre mesure, dont quatre tombaient à zéro faute de trouver quoi que ce soit.
    `resserre_cliquets.py` agit sur ces verdicts et ÉCRIT : il ramenait alors quatre `ratchet:` à
    zéro dans les vraies ADR, en annonçant un succès. Une ligne ici couvre les cinq, et le garde
    qu'on écrira demain."""
    fini = subprocess.run(
        [sys.executable, str(script)],
        capture_output=True,
        text=True,
        check=False,
        cwd=RACINE_DEPOT,
    )
    return fini.stdout + fini.stderr


def collecter():
    """Les verdicts, et la liste de ceux que ce rapport n a PAS su lire (article A3).

    Un script lance dont aucune ligne ne correspond rendait un rapport silencieux : il manquait dans
    le tableau, et rien ne disait qu il manquait. Mesure du 2026-08-28 : sur vingt scripts, TROIS
    etaient dans ce cas, dont un plancher qui annoncait `verdict=a-relever` depuis on ne sait quand.
    Un dispositif dit ce qu il couvre, et ce qu il n a pas pu lire.
    """
    cliquets, planchers, loupes, muets = [], [], [], []
    for script in sorted(ICI.glob("[0-9]*.py")):
        sortie = executer(script)
        lus = 0
        for m in LIGNE_CLIQUET.finditer(sortie):
            num, suspects, cliquet, verdict = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
            cliquets.append((num, suspects, cliquet, verdict))
            lus += 1
        for m in LIGNE_PLANCHER.finditer(sortie):
            num, mesure, plancher, verdict = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
            planchers.append((num, mesure, plancher, verdict))
            lus += 1
        if not lus:
            muets.append((script.name, premiere_ligne_de_verdict(sortie)))
    for script in sorted(ICI.glob("loupe-*.py")):
        sortie = executer(script)
        lus = 0
        for m in LIGNE_LOUPE.finditer(sortie):
            loupes.append((m.group(1), int(m.group(2))))
            lus += 1
        if not lus:
            muets.append((script.name, premiere_ligne_de_verdict(sortie)))
    return cliquets, planchers, loupes, muets


def premiere_ligne_de_verdict(sortie: str) -> str:
    """Ce que le script a rendu qui RESSEMBLE a un verdict, pour que le rapport montre l ecart.

    Sans cet extrait, le lecteur sait qu un script est muet et doit le relancer a la main pour savoir
    pourquoi. Avec lui, l ecart entre ce qui est rendu et ce qui est attendu se lit sur place.
    """
    for ligne in sortie.split("\n"):
        if ligne.startswith(("ADR ", "LOUPE ", "PLANCHER ")) and "|" in ligne:
            return ligne.strip()
    return "aucune ligne de verdict"


def rendre(cliquets, planchers, loupes, muets, markdown: bool) -> str:
    h1, h2, li = ("## ", "### ", "- ") if markdown else ("== ", "-- ", "  ")
    out = [f"{h1}Rapport de conformité aux ADR", ""]

    out.append(f"{h2}Cliquets (vérifications « probable »)")
    if markdown:
        out += ["", "| ADR | suspects | cliquet | verdict |", "|---|---|---|---|"]
        for num, s, c, v in cliquets:
            out.append(f"| {num} | {s} | {c} | {v} |")
    else:
        for num, s, c, v in cliquets:
            out.append(f"{li}ADR {num} : suspects={s} cliquet={c} → {v}")
    out.append("")

    a_resserrer = [(num, s, c) for num, s, c, v in cliquets if v == "a-resserrer"]
    regressions = [(num, s, c) for num, s, c, v in cliquets if v == "regression"]

    if regressions:
        out.append(f"{h2}⚠ Régressions (un cas a été ajouté)")
        for num, s, c in regressions:
            out.append(f"{li}ADR {num} : {s} suspects pour un cliquet de {c}. À corriger sur la PR fautive.")
        out.append("")

    if a_resserrer:
        out.append(f"{h2}Cliquets à resserrer (la réalité fait mieux que la marge)")
        for num, s, c in a_resserrer:
            out.append(f"{li}ADR {num} : ramener le cliquet de {c} à {s}.")
        out.append("")
    else:
        out.append(f"{li}Aucun cliquet à resserrer : chaque marge colle à la réalité.")
        out.append("")

    if planchers:
        out += ["", f"{h2}Planchers (ce qu'on possède et qui ne redescend pas)"]
        for num, mesure, plancher, verdict in planchers:
            fleche = "→ ok" if verdict == "ok" else f"→ {verdict}"
            out.append(f"{li}ADR {num} : mesure={mesure} plancher={plancher} {fleche}")
        out.append("")

    out.append(f"{h2}Loupes (vérifications « humaine », indicatif)")
    if loupes:
        for num, n in loupes:
            out.append(f"{li}ADR {num} : {n} candidat(s) à revoir.")
    else:
        out.append(f"{li}Aucune loupe active.")
    if muets:
        out += ["", f"{h2}\u26a0 Verdicts que ce rapport n'a pas su lire"]
        for nom, rendu in muets:
            out.append(f"{li}{nom} : {rendu}")
        out.append(f"{li}Ces scripts ont été lancés ; leur sortie ne porte aucun verdict que ce"
                   f" rapport sache lire. Un registre et un garde qui refuse de conclure sont"
                   f" légitimement dans ce cas ; une ligne de verdict mal formée ne l'est pas.")

    return "\n".join(out) + "\n"


def resserrements(cliquets):
    """La liste (num, nouvelle_valeur) des cliquets à abaisser : c'est ce qu'un geste d'auto-calibration
    appliquerait dans les ADR."""
    return [(num, s) for num, s, c, v in cliquets if v == "a-resserrer"]


def auto_test() -> int:
    """Le lancement s ancre, et se prouve par une sonde plutôt qu en relançant les dix-huit gardes."""
    import os
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du lancement des gardes (#4781) :")
    with tempfile.TemporaryDirectory() as brut:
        sonde = pathlib.Path(brut) / "sonde.py"
        sonde.write_text("import os\nprint(os.getcwd())\n", encoding="utf-8")

        # 1. Depuis le dépôt : la sonde doit déjà voir la racine, et non `scripts/adr`.
        verifie("lancé du dépôt, le garde tourne à la racine", executer(sonde).strip(), str(RACINE_DEPOT))

        # 2. Depuis AILLEURS : c est le cas qui a corrompu quatre ADR. Sans le premier, ce cas
        #    passerait au vert pour la mauvaise raison, le répertoire d essai étant peut-être le bon.
        ancien = os.getcwd()
        try:
            os.chdir(brut)
            verifie("lancé d ailleurs, le garde tourne QUAND MÊME à la racine",
                    executer(sonde).strip(), str(RACINE_DEPOT))
            verifie("et le répertoire d essai n était pas déjà la racine",
                    os.getcwd() == str(RACINE_DEPOT), False)
        finally:
            os.chdir(ancien)

    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    markdown = "--markdown" in sys.argv
    cliquets, planchers, loupes, muets = collecter()
    sys.stdout.write(rendre(cliquets, planchers, loupes, muets, markdown))
