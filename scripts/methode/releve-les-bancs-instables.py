#!/usr/bin/env python3
"""Combien de fois chaque banc a-t-il rougi, sur combien de tirages.

Six bancs du depot rougissent par intermittence (#4804), et aucun n'avait de taux : chaque issue
consignait une ou deux observations en disant elle-meme que ce n'etait pas une frequence.

Rejouer la suite N fois ne convient pas : elle prend 16 minutes, donc trente tirages font huit
heures. Et rejouer une classe seule ne reproduit rien, le rouge de #4694 n'apparaissant QUE dans la
suite complete, ou le Stage de TestFX est partage entre les classes d'un meme fork.

Les runs passes SONT les tirages. Ce releve les lit.

Usage : releve-les-bancs-instables.py [--jours N] [--auto-test]
"""

from __future__ import annotations

import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

# Surefire nomme un test echoue sous deux formes, et il faut les deux : la premiere seule rate les
# erreurs, la seconde seule rate les echecs d'assertion quand la classe entiere tombe.
#
#   [ERROR]   AppTest.le_stage_partage_reste_ajustable:145 [une scene...]
#   [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest.ouvrir_tout(FxRobot) -- ... <<< ERROR!
#
# Le paquet est facultatif et se jette : deux journaux du meme test ne s'agregeraient pas si l'un le
# portait et l'autre non.
CLASSE = r"(?:[a-z][A-Za-z0-9_]*\.)*([A-Z][A-Za-z0-9_]*Test)"
RESUME = re.compile(rf"\[ERROR\]\s+{CLASSE}\.([a-z][A-Za-z0-9_]*):\d+")
DETAIL = re.compile(rf"\[ERROR\]\s+{CLASSE}\.([a-z][A-Za-z0-9_]*)\([^)]*\)\s+--\s+Time elapsed")


def testsEchoues(journal: str) -> set[str]:
    """Les `Classe.methode` que ce journal declare en echec."""
    vus = set()
    for motif in (RESUME, DETAIL):
        for classe, methode in motif.findall(journal):
            vus.add(f"{classe}.{methode}")
    return vus


def rapport(parRun: dict[str, set[str]], tirages: int) -> list[tuple[str, int, int]]:
    """`(test, occurrences, tirages)`, du plus frequent au moins frequent."""
    comptes: dict[str, int] = {}
    for echoues in parRun.values():
        for test in echoues:
            comptes[test] = comptes.get(test, 0) + 1
    return [(test, n, tirages) for test, n in sorted(comptes.items(), key=lambda c: (-c[1], c[0]))]


# **Un verdict courant ment.** `gh run list` rend l'etat COURANT d'un run, et une relance ECRASE
# l'echec precedent : sur 21 jours, aucun commit n'apparaissait vu vert ET rouge, alors que 52 runs
# avaient ete relances. La signature d'une instabilite est donc invisible dans cette source.
#
# Les TENTATIVES la disent. Un run dont `run_attempt` vaut 2 et qui finit vert a echoue puis reussi
# sur le meme commit : c'est exactement ce que les six issues du chantier #4804 decrivent.
#
# Le groupe de concurrence tue par ailleurs les runs quand une poussee arrive. Un run annule n'est pas
# un tirage, et le compter au denominateur ferait baisser tous les taux sans qu'aucun chiffre ne
# paraisse faux.
CONCLUS = ("success", "failure")
DEPOT = "echonuit/vigiechiro-pr-companion"
FLUX = 286171791  # « Java CI with Maven »


def _forge(*args: str) -> str:
    """Sortie de `gh`, ou une chaine vide s'il n'est pas installe."""
    if shutil.which("gh") is None:
        return ""
    fait = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
    return fait.stdout


def _borne(jours: int) -> str:
    return subprocess.run(
        ["date", "-u", "-d", f"-{jours} days", "+%Y-%m-%dT%H:%M:%SZ"],
        capture_output=True, text=True, check=False,
    ).stdout.strip()


def relances(jours: int) -> tuple[list[dict], int]:
    """Les runs RELANCES de la fenetre, et le nombre de tirages (conclus, annules exclus).

    `gh run list` ne suffit pas : il plafonne, et surtout il ne porte pas `run_attempt`.
    """
    brut = _forge(
        "api", "--paginate", f"repos/{DEPOT}/actions/workflows/{FLUX}/runs?per_page=100",
        "-q", ".workflow_runs[] | [.id, .run_attempt, .conclusion, .created_at, .head_sha] | @tsv",
    )
    borne = _borne(jours)
    dans = []
    for ligne in brut.splitlines():
        champs = ligne.split("\t")
        if len(champs) < 5 or champs[3] < borne or champs[2] not in CONCLUS:
            continue
        dans.append({"id": int(champs[0]), "tentatives": int(champs[1]),
                     "verdict": champs[2], "sha": champs[4]})
    return [r for r in dans if r["tentatives"] > 1], len(dans)


def journalDeTentative(idRun: int, tentative: int, atelier: str = "build") -> str:
    """Le journal d'UNE tentative, decompresse. `--log-failed` ne rend que la DERNIERE."""
    if shutil.which("gh") is None or shutil.which("unzip") is None:
        return ""
    with tempfile.TemporaryDirectory() as dossier:
        zipDeRun = pathlib.Path(dossier) / "l.zip"
        fait = subprocess.run(
            ["gh", "api", f"repos/{DEPOT}/actions/runs/{idRun}/attempts/{tentative}/logs"],
            capture_output=True, check=False,
        )
        if not fait.stdout:
            return ""
        zipDeRun.write_bytes(fait.stdout)
        subprocess.run(["unzip", "-qq", "-o", str(zipDeRun), "-d", dossier],
                       capture_output=True, check=False)
        morceaux = []
        for fichier in pathlib.Path(dossier).glob("*.txt"):
            morceaux.append(fichier.read_text(encoding="utf-8", errors="replace"))
        return "\n".join(morceaux)


def _autoTest() -> int:
    """Les temoins, sur des extraits de journaux REELS de la forge."""
    # Forme « resume » : une ligne par test, a la fin du rapport surefire.
    resume = (
        "build\tBuild + tests\t2026-08-29T14:31:22Z [ERROR] Tests run: 5306, Failures: 1\n"
        "build\tBuild + tests\t2026-08-29T14:31:22Z [ERROR]   AppTest.le_stage_partage_reste_ajustable:145 [une scene]\n"
    )
    assert testsEchoues(resume) == {"AppTest.le_stage_partage_reste_ajustable"}, testsEchoues(resume)

    # Forme « detail » : le paquet est present, et les arguments du cas aussi.
    detail = (
        "build\tBuild\t2026-08-29T14:31:27Z [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest"
        ".ouvrir_tout_charge_les_passages(FxRobot) -- Time elapsed: 0.002 s <<< ERROR!\n"
    )
    assert testsEchoues(detail) == {"ActiviteViewTest.ouvrir_tout_charge_les_passages"}, testsEchoues(detail)

    # Le sens NEGATIF : la ligne de COMPTE ne nomme aucun test, et ne doit rien produire.
    compte = "build\tB\t2026-08-29T14:31:27Z [ERROR] Tests run: 40, Failures: 1, Errors: 0, Skipped: 0\n"
    assert testsEchoues(compte) == set(), testsEchoues(compte)

    # Un meme test vu sous les DEUX formes dans le meme journal ne compte qu'une fois.
    assert len(testsEchoues(resume + detail + resume)) == 2

    # Le rapport agrege par test, et porte le denominateur.
    #
    # Le test le PLUS frequent porte ici un nom alphabetiquement PLUS GRAND que le rare : sans cela,
    # le tri par frequence et le tri alphabetique rendraient le meme ordre, et le temoin ne dirait
    # rien de celui qu'il pretend tenir. Mesure : la mutation qui retire `-c[1]` y a d'abord survecu.
    parRun = {
        "r1": {"ZStageTest.le_stage_partage_reste_ajustable"},
        "r2": {"ZStageTest.le_stage_partage_reste_ajustable", "AbandonTest.bandeau_suit"},
        "r3": set(),
    }
    lignes = rapport(parRun, tirages=200)
    assert lignes[0] == ("ZStageTest.le_stage_partage_reste_ajustable", 2, 200), lignes
    assert lignes[1] == ("AbandonTest.bandeau_suit", 1, 200), lignes
    # Le sens NEGATIF : un rapport qui rendrait toujours vide passerait tout le reste.
    assert lignes, "trois runs dont deux rouges doivent produire des lignes"

    print("auto-test : 6 temoins verts")
    return 0


def _jours() -> int:
    if "--jours" in sys.argv:
        return int(sys.argv[sys.argv.index("--jours") + 1])
    return 21


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    jours = _jours()
    rejoues, tirages = relances(jours)
    if not tirages:
        print("Aucun tirage lu : `gh` est-il installe et authentifie ?")
        return 1
    parRun, muets = {}, []
    for r in rejoues:
        echoues = set()
        for tentative in range(1, r["tentatives"]):
            echoues |= testsEchoues(journalDeTentative(r["id"], tentative))
        if echoues:
            parRun[str(r["id"])] = echoues
        else:
            muets.append(r["id"])
    lignes = rapport(parRun, tirages)
    print(f"RELEVE bancs | fenetre={jours}j | tirages={tirages} | relances={len(rejoues)}"
          f" | instables={len(lignes)}")
    if not lignes:
        print("\nAucun test nomme dans les tentatives echouees.")
    for test, n, total in lignes:
        print(f"  {n:3d}/{total}  {100 * n / total:5.3f} %  {test}")
    if muets:
        # Une relance dont la tentative echouee ne nomme aucun test n'a pas echoue sur un banc :
        # compilation, garde, quota, ou runner tombe. Les taire ferait passer ces relances pour des
        # instabilites de test.
        print(
            "\n%d relance(s) dont la tentative echouee ne nomme aucun test, donc echouees pour"
            " autre chose : %s."
            % (len(muets), ", ".join(str(i) for i in muets))
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
