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

ICI = pathlib.Path(__file__).parent
LIGNE_CLIQUET = re.compile(r"^ADR (\d+) \| suspects=(\d+) \| cliquet=(\d+) \| verdict=(\S+)$", re.M)
LIGNE_LOUPE = re.compile(r"^LOUPE (\d+) \| candidats=(\d+)$", re.M)


def executer(script: pathlib.Path) -> str:
    """Lance un script de vérification et rend sa sortie. Le code de sortie est ignoré : le rapport
    observe, il ne juge pas - un cliquet en régression fait déjà rougir la CI ailleurs."""
    fini = subprocess.run(
        [sys.executable, str(script)], capture_output=True, text=True, check=False
    )
    return fini.stdout + fini.stderr


def collecter():
    cliquets, loupes = [], []
    for script in sorted(ICI.glob("[0-9]*.py")):
        sortie = executer(script)
        for m in LIGNE_CLIQUET.finditer(sortie):
            num, suspects, cliquet, verdict = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
            cliquets.append((num, suspects, cliquet, verdict))
    for script in sorted(ICI.glob("loupe-*.py")):
        sortie = executer(script)
        for m in LIGNE_LOUPE.finditer(sortie):
            loupes.append((m.group(1), int(m.group(2))))
    return cliquets, loupes


def rendre(cliquets, loupes, markdown: bool) -> str:
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

    out.append(f"{h2}Loupes (vérifications « humaine », indicatif)")
    if loupes:
        for num, n in loupes:
            out.append(f"{li}ADR {num} : {n} candidat(s) à revoir.")
    else:
        out.append(f"{li}Aucune loupe active.")
    return "\n".join(out) + "\n"


def resserrements(cliquets):
    """La liste (num, nouvelle_valeur) des cliquets à abaisser : c'est ce qu'un geste d'auto-calibration
    appliquerait dans les ADR."""
    return [(num, s) for num, s, c, v in cliquets if v == "a-resserrer"]


if __name__ == "__main__":
    markdown = "--markdown" in sys.argv
    cliquets, loupes = collecter()
    sys.stdout.write(rendre(cliquets, loupes, markdown))
