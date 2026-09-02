#!/usr/bin/env python3
"""Un contrat declare ne contredit pas ce que le garde fait (ADR 4636, issue #5108).

`imprime_contrat` refuse un contrat INCOMPLET, c est-a-dire a qui il manque un champ. Rien ne
comparait ce qu un contrat DECLARE a ce que le garde FAIT. Un contrat que personne ne confronte est
un commentaire, et il derive : la docstring de `2843-tiret-cadratin.py` annoncait deux arbres pour
un corpus qui n en portait qu un, pendant des mois (#5048).

## La regle n est PAS l egalite

Mesure du 2026-09-02 sur les trois porteurs, dont aucune ligne n est un defaut :

    0008                 decision   declare « ADR 0008 »          infere « 0008 »
    0008                 population declare « PRODUCTION + TESTS » infere « RACINES »
    matrice-constitution population declare « DECISIONS + ... »    infere « (non declaree) »

**Le vocabulaire differe** : `RACINES = (PRODUCTION, TESTS)`, les deux formes disent le meme corpus.
**Et le contrat sait PLUS que l inference**, dont le corpus ne connait que les arbres Java.

La regle est donc « les deux ne se CONTREDISENT pas ». Un silence de l inference n est pas une
contradiction : c est le cas normal, et c est meme la raison d etre du contrat.

## Comment les porteurs se trouvent

Par un grep, puis par un LANCEMENT. Le grep ne fait que reduire les candidats ; c est la reponse a
`--contrat` qui fait foi. Les trois porteurs emploient trois idiomes de dispatch - `sys.argv`,
`argparse`, et le shell - et un motif qui chercherait l un d eux en manquerait deux. J ai commis
cette erreur en mesurant ce chantier, et c est la meme que #5032 et #5103.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, rapporte

ADR = "4636"

# Les alias du corpus, resolus vers les deux arbres elementaires. Sans cela, « RACINES » et
# « PRODUCTION + TESTS » se liraient comme un desaccord alors qu ils nomment le meme corpus.
ALIAS = {
    "RACINES": ("PRODUCTION", "TESTS"),
    "RACINES_ANCREES": ("PRODUCTION", "TESTS"),
    "PRODUCTION_ANCREE": ("PRODUCTION",),
    "TESTS_ANCRES": ("TESTS",),
    "PRODUCTION": ("PRODUCTION",),
    "TESTS": ("TESTS",),
}

# Ce que ce garde ne confronte PAS, et pourquoi. La cecite se declare, sinon un lecteur croit que
# les six champs sont tenus.
HORS_CONFRONTATION = {
    "geste": "une phrase libre, qu aucun motif ne derive du code",
    "dispositif": "cliquet, plancher, loupe ou invariant : l inference le devine du nom de l aide "
    "appelee, ce qui rendrait un desaccord la ou il n y a qu une convention de nommage",
}

CHIFFRE = re.compile(r"(\d+)")


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les points d entree qui MENTIONNENT un contrat, candidats a en porter un.

    Le grep ne conclut pas : il reduit. C est la reponse a `--contrat` qui fait foi, parce que les
    idiomes de dispatch different. Lancer `--contrat` sur les 117 points d entree couterait des
    minutes, un script sans cette branche ignorant l argument et faisant son travail.
    """
    base = racine or RACINE_DEPOT
    vus = []
    for motif in ("scripts/**/*.py", ".github/scripts/*.sh", ".github/assets/*.sh"):
        for f in sorted(base.glob(motif)):
            if "__pycache__" in f.parts or f.name.startswith("_"):
                continue
            try:
                if "--contrat" in f.read_text(encoding="utf-8", errors="ignore"):
                    vus.append(f)
            except OSError:
                continue
    return vus


def contrat_de(chemin: pathlib.Path) -> dict[str, str] | None:
    """Ce que le garde REPOND a `--contrat`, ou rien s il ne repond pas."""
    lance = ["bash", str(chemin)] if chemin.suffix == ".sh" else [sys.executable, str(chemin)]
    try:
        # `check=False` est VOULU : un garde qui refuse sort 1, et seule sa sortie nous interesse.
        # Lever sur le code rendrait ce releve dependant du verdict des gardes qu il inventorie.
        rendu = subprocess.run(
            [*lance, "--contrat"],
            capture_output=True,
            text=True,
            timeout=20,
            cwd=RACINE_DEPOT,
            check=False,
        ).stdout
    except (subprocess.TimeoutExpired, OSError):
        return None
    if not rendu.startswith("CONTRAT | garde="):
        return None
    champs = {}
    for ligne in rendu.split("\n")[1:]:
        if ": " in ligne:
            cle, _, valeur = ligne.partition(": ")
            champs[cle.strip()] = valeur.strip()
    return champs


def corpus_resolu(expression: str) -> frozenset[str] | None:
    """Les arbres elementaires qu une expression de population designe, ou rien si on ne sait pas."""
    morceaux = [m.strip() for m in expression.split("+")]
    if not morceaux or not all(m in ALIAS for m in morceaux):
        return None
    return frozenset(a for m in morceaux for a in ALIAS[m])


def temoin_existe(temoin: str, base: pathlib.Path) -> bool:
    """Ce que le champ `temoin` NOMME existe-t-il ?

    Deux formes vivent dans le depot : `fichier#fonction`, et une commande dont le premier mot est
    un fichier. Le garde ne LANCE pas le temoin : le prouver est le travail des meta-gardes, pas
    celui-ci, et le lancer couterait des minutes.
    """
    if "#" in temoin:
        fichier, _, fonction = temoin.partition("#")
        cible = base / fichier
        return cible.is_file() and f"def {fonction}" in cible.read_text(encoding="utf-8")
    premier = temoin.split()[0] if temoin.split() else ""
    return bool(premier) and (base / premier).is_file()


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par CONTRADICTION entre ce qu un contrat declare et ce que le garde fait."""
    base = racine or RACINE_DEPOT
    sys.path.insert(0, str(base / "scripts" / "methode"))
    import importlib.util

    spec = importlib.util.spec_from_file_location(
        "releve", base / "scripts" / "methode" / "contrats-des-gardes.py"
    )
    releve = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(releve)

    trouves = []
    for chemin in fichiers(racine):
        contrat = contrat_de(chemin)
        if contrat is None:
            continue
        vu = chemin.relative_to(base).as_posix()
        texte = chemin.read_text(encoding="utf-8", errors="ignore")

        if not temoin_existe(contrat.get("temoin", ""), base):
            trouves.append(f"{vu}  temoin declare introuvable : {contrat.get('temoin')!r}")

        if chemin.suffix != ".py":
            continue  # l inference ne lit que le Python ; pour le shell, seul le temoin se confronte

        declare = CHIFFRE.findall(contrat.get("decision", ""))
        infere = releve.numeros_adr(texte)
        if declare and infere and not set(declare) & set(infere):
            trouves.append(f"{vu}  decision {declare} contredit l ADR rendue {infere}")

        d = corpus_resolu(contrat.get("population", ""))
        i = corpus_resolu(releve.population(texte))
        if d is not None and i is not None and d != i:
            trouves.append(f"{vu}  population declaree {sorted(d)} contredit {sorted(i)}")

        seuil_declare = CHIFFRE.search(contrat.get("seuil", ""))
        seuil_infere = CHIFFRE.search(releve.seuil(texte, base / "dev-docs" / "decisions"))
        if seuil_declare and seuil_infere and seuil_declare.group(1) != seuil_infere.group(1):
            trouves.append(
                f"{vu}  seuil declare {seuil_declare.group(1)} contredit {seuil_infere.group(1)}"
            )
    return trouves


def _auto_test() -> int:
    """Les DEUX moities : une contradiction est vue, et un desaccord de VOCABULAIRE ne l est pas."""
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    verifie(
        "RACINES et PRODUCTION + TESTS disent le meme corpus",
        corpus_resolu("RACINES") == corpus_resolu("PRODUCTION + TESTS"),
        True,
    )
    verifie(
        "TESTS seul n est pas les deux arbres",
        corpus_resolu("TESTS") == corpus_resolu("RACINES"),
        False,
    )
    verifie(
        "une population inconnue ne se resout pas, donc ne contredit rien",
        corpus_resolu("(non declaree)"),
        None,
    )
    verifie(
        "un temoin nomme une fonction qui existe",
        temoin_existe("scripts/adr/verifie_scripts.py#test_0008_echec_silencieux", RACINE_DEPOT),
        True,
    )
    verifie(
        "un temoin qui nomme une fonction absente est vu",
        temoin_existe("scripts/adr/verifie_scripts.py#test_fantome", RACINE_DEPOT),
        False,
    )
    verifie(
        "un temoin qui nomme un fichier absent est vu",
        temoin_existe("scripts/adr/fantome.py --auto-test", RACINE_DEPOT),
        False,
    )

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(
        rapporte(
            ADR,
            "contrats qui contredisent ce que le garde fait",
            suspects(),
            lus=len(fichiers()),
        )
    )
