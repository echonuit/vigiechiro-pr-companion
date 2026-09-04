#!/usr/bin/env python3
"""Loupe : un garde parcourt un chemin que sa population declaree ne nomme pas (ADR 5175).

`verifie_contrats_tiennent.py` annonce qu il confronte le champ `population`. Mesure du 2026-09-04 :
il le fait pour **26 contrats sur 67** et s abstient pour **41**. La cecite etait invisible, et elle
a coute : elle a laisse passer #5176, ou l invariant des contrats declarait lire `scripts/adr`
pendant qu il en lisait 66 sur deux dossiers. Il a fallu construire un artefact de cloture pour le
voir.

## Pourquoi une loupe, et pas un garde qui refuse

Parce que la confrontation ne conclut pas. Mesure du meme jour, sur les 41 abstentions :

| | |
|---|---:|
| chemins resolus par `chemins_lus()` | **13** |
| dont la population declaree les nomme tous | 8 |
| dont un chemin n est pas nomme | **5** |

Sur ces cinq, **quatre declarent une population PLUS PRECISE que le chemin**, pas une population
fausse : `verifie_scripts.py` parcourt `scripts/adr` et declare « les gardes que `_charge` nomme
dans ce fichier », qui est un sous-ensemble correctement decrit. Un garde qui refuserait la ferait
rougir pour une declaration meilleure que la regle.

Un dispositif qui crie sur du juste apprend a ignorer sa sortie, et le depot le sait : c est la
raison pour laquelle `SC2016` est exclu de shellcheck ici. Cette loupe **signale et rend 0** ; c est
la relecture qui trie.

## Ce qu elle voit, et ce qu elle ne verra jamais

Elle ne parle que des gardes dont `chemins_lus()` sait resoudre le parcours, treize sur quarante et
un. Les vingt-huit autres construisent leur racine d une facon que l evaluation symbolique ne suit
pas, et six n ont aucun corpus de fichiers : deux lisent la forge, deux passent par `git`, deux ne
parcourent rien. **Aucune loupe ne les couvrira**, et c est ecrit ici plutot que sous-entendu.

Elle ne prouve pas non plus que la phrase soit JUSTE. Elle prouve qu aucun chemin parcouru n est
passe sous silence, ce qui est moins, et ce qui aurait suffi a attraper #5176.

## Elle ne lance rien

Les contrats se lisent par `ast`, non en lancant chaque garde. C est le second recours de l ADR
5102, et il vaut mieux ici que le premier : `verifie_contrats_tiennent.py` lance deja les 68, et
doubler cette population de sous-processus pour un dispositif qui ne refuse pas serait payer cher un
signalement.
"""

from __future__ import annotations

import ast
import importlib
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "methode"))
from _commun import RACINE_DEPOT, loupe, sort_si_contrat_demande

# Le nom du releve porte des tirets : il ne s importe pas, il se charge.
releve = importlib.import_module("contrats-des-gardes")

# LA POPULATION DE CETTE LOUPE EST CE SUR QUOI LE CLIQUET S ABSTIENT, et c est pourquoi elle lui
# emprunte sa resolution plutot que d en ecrire une seconde. Sans cela elle empietait sur les 26
# contrats DEJA confrontes, et signalait `TESTS` comme ne nommant pas `src/test/java` - alors que
# c est exactement ce que l alias resout, et mieux qu une comparaison de chaines.
from verifie_contrats_tiennent import corpus_resolu

ADR = "5175"

DOSSIERS = ("adr", "methode")

# Ce que cette loupe ne regardera pas, et pourquoi. La cecite se nomme, sinon un lecteur croit la
# couverture plus large qu elle n est - c est l idiome de `HORS_PORTEE`, et la lecon de #5176.
HORS_PORTEE = {
    "populations sans corpus de fichiers": "les loupes de la forge et les gardes qui passent par "
    "`git` n ont aucun chemin a nommer ; il n y a rien a confronter, pas une confrontation manquee",
    "racines que l evaluation ne suit pas": "vingt-huit gardes sur quarante et un construisent leur "
    "parcours d une facon que `chemins_lus()` ne resout pas. Elle penche du bon cote : ne rien "
    "rendre est silencieux, rendre un chemin faux accuserait",
}


def contrat_declare(texte: str) -> dict[str, str]:
    """Le `CONTRAT` que ce module DECLARE, lu par `ast` et non par motif.

    Une MENTION n est pas une declaration, et une variable locale du meme nom n en est pas une non
    plus. C est le controle de #5032 et de #5149, et il vaut ici pour la meme raison.
    """
    try:
        arbre = ast.parse(texte)
    except SyntaxError:
        return {}
    for n in arbre.body:
        cibles = n.targets if isinstance(n, ast.Assign) else []
        if isinstance(n, ast.AnnAssign):
            cibles = [n.target]
        if not any(isinstance(c, ast.Name) and c.id == "CONTRAT" for c in cibles):
            continue
        valeur = n.value
        if not isinstance(valeur, ast.Dict):
            continue
        champs = {}
        for cle, val in zip(valeur.keys, valeur.values):
            if isinstance(cle, ast.Constant) and isinstance(val, ast.Constant):
                champs[str(cle.value)] = str(val.value)
        return champs
    return {}


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les points d entree Python des deux dossiers tenus, ceux qui declarent un contrat."""
    base = racine or RACINE_DEPOT
    trouves = []
    for d in DOSSIERS:
        dossier = base / "scripts" / d
        if not dossier.is_dir():
            continue
        for f in sorted(dossier.glob("*.py")):
            if f.name.startswith("_") or "__pycache__" in f.parts:
                continue
            trouves.append(f)
    return trouves


def candidats(racine: pathlib.Path | None = None) -> list[str]:
    """Les gardes dont un chemin parcouru manque a leur population declaree."""
    base = racine or RACINE_DEPOT
    trouves = []
    for f in fichiers(racine):
        texte = f.read_text(encoding="utf-8", errors="ignore")
        contrat = contrat_declare(texte)
        declaree = contrat.get("population")
        if not declaree:
            continue
        if corpus_resolu(declaree) is not None:
            continue  # deja confronte par le cliquet, et par un alias plutot qu une chaine
        lus = releve.chemins_lus(texte, f.relative_to(base).as_posix())
        manquants = sorted(c for c in lus if c not in declaree)
        if manquants:
            vu = f.relative_to(base).as_posix()
            trouves.append(f"{vu}  parcourt {manquants} sans le nommer : {declaree!r}")
    return trouves


def _auto_test() -> int:
    """Les DEUX sens, et le SILENCE : elle voit un chemin tu, elle se tait sur un chemin nomme."""
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test de la loupe des populations non nommees (#5175) :")

    verifie(
        "un CONTRAT declare est lu",
        contrat_declare('CONTRAT = {"population": "les x"}\n').get("population"),
        "les x",
    )
    verifie(
        "une MENTION en prose n en est pas un",
        contrat_declare('# CONTRAT = {"population": "les x"}\nx = 1\n'),
        {},
    )
    verifie(
        "ni une variable locale du meme nom",
        contrat_declare('def f():\n    CONTRAT = {"population": "les x"}\n    return CONTRAT\n'),
        {},
    )

    corps = (
        "import pathlib\n"
        "DOSSIER = pathlib.Path(__file__).resolve().parent\n"
        "def fichiers():\n"
        "    return sorted(DOSSIER.glob('*.py'))\n"
        'CONTRAT = {{"population": "{pop}"}}\n'
    )

    with tempfile.TemporaryDirectory(prefix="vc-5175-") as tmp:
        faux = pathlib.Path(tmp)
        cible = faux / "scripts" / "adr"
        cible.mkdir(parents=True)

        # LE SENS QUI COMPTE : le garde parcourt scripts/adr et ne le nomme pas.
        (cible / "0001-muet.py").write_text(
            corps.format(pop="les gardes du depot"), encoding="utf-8"
        )
        trouve = candidats(faux)
        verifie("un chemin parcouru et non nomme est vu", len(trouve), 1)
        verifie("et la loupe DIT lequel", "scripts/adr" in (trouve[0] if trouve else ""), True)

        # LE SILENCE, qui compte autant : nomme, elle ne dit rien.
        (cible / "0001-muet.py").write_text(
            corps.format(pop="les points d entree de scripts/adr"), encoding="utf-8"
        )
        verifie("un chemin NOMME ne fait rien dire", candidats(faux), [])

        # Et un module sans contrat n est pas un candidat : il n y a rien a confronter.
        (cible / "0002-sans-contrat.py").write_text("x = 1\n", encoding="utf-8")
        verifie("un module sans contrat est ignore", candidats(faux), [])

        # LA BARRIERE : une population que le CLIQUET sait resoudre ne regarde pas cette loupe.
        # Sans ce cas, la barriere est une branche que rien ne tient. Sans la BARRIERE, la loupe
        # rendait 10 candidats au lieu de 5, dont `TESTS` accuse de ne pas nommer `src/test/java` -
        # alors que c est precisement ce que l alias resout, et mieux qu une comparaison de chaines.
        (cible / "0003-alias.py").write_text(corps.format(pop="TESTS"), encoding="utf-8")
        verifie("une population resolue par alias n est pas de son ressort", candidats(faux), [])

    # La cecite est NOMMEE, non comptee : chaque entree porte une RAISON non vide, et c est ce que
    # ce cas tient. Une premiere version comparait `sorted(HORS_PORTEE)` a lui-meme : elle passait
    # toujours, y compris sur un dictionnaire vide, et c est precisement le temoin decoratif que
    # `verifie_temoins_non_decoratifs.py` existe pour interdire.
    verifie("chaque cecite porte une raison", all(HORS_PORTEE.values()), True)
    verifie("aucune n est vide", [k for k, v in HORS_PORTEE.items() if not v], [])
    verifie("et la portee manquante n est pas qu un compte", len(HORS_PORTEE) > 0, True)

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


# Ce que cette loupe DECLARE etre (issue #5009).
CONTRAT = {
    "geste": "chemin parcouru qu une population declaree ne nomme pas",
    "population": "les points d entree de scripts/adr et scripts/methode qui declarent un contrat",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/loupe-5175-population-non-nommee.py --auto-test",
    "decision": "ADR 5175",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(
        loupe(
            ADR,
            "chemin parcouru qu une population declaree ne nomme pas",
            candidats(),
            lus=len(fichiers()),
        )
    )
