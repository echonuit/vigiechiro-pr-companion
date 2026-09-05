#!/usr/bin/env python3
"""Un garde que la CI lance est nomme par la page qu on lit avant de pousser (issue #5258).

La competence `ouvrir-une-pr` porte une section « La batterie locale, et pourquoi elle ne se devine
pas ». C est la page qu on ouvre quand on croit avoir fini, et sa phrase d ouverture annoncait
« QUATRE gardes rougissent en CI alors que la compilation, le format et `scripts/adr/rapport.py`
passent tous en local ». Le compte etait faux, et rien ne le disait.

Un inventaire tenu a la main derive de la CI en silence. Ce garde le fait rougir.

## Ce que le 2026-09-05 a coute, deux fois dans la meme demande

La cloture du chantier #5065 (PR #5255) descendait le cliquet de l ADR 4359 de 742 a 741.

1. J ai corrige les quatre sites que je connaissais, lance la boucle `[0-9]*.py`, les loupes et la
   matrice de la constitution : tout vert. `verifie_contrats_tiennent.py` a rougi en CI sur un
   CINQUIEME site, le contrat que `scripts/adr/4359-javadoc-narratif.py` declare sur lui-meme.
2. La meme cloture ecrivait une ADR portant `nielsen-1`. La ligne du tableau qui dit quoi lancer
   quand une ADR bouge ne nomme que `matrice-constitution.py`, et il y a DEUX matrices :
   `matrice-ergonomie.py --verifie` a rougi en CI a son tour.

Aucun des deux ne demandait de connaitre le depot. Le second demandait seulement d avoir ecrit une
ADR.

## La population se DERIVE, elle ne s enumere pas

Trois regles derivees plutot qu une liste d exemptions a tenir, parce qu une liste a tenir est
exactement le defaut que ce garde combat.

**Une invocation `--auto-test` ne compte pas.** Elle eprouve le garde, elle ne juge pas le depot :
elle ne peut donc pas rougir sur ce qu on vient d ecrire. `scripts/mkdocs/bandeau_adr.py` n est
lance qu ainsi, et la page ne lui doit rien.

**Un script qui ne declare aucun `CONTRAT` n est pas un garde.** `scripts/qualite/rapport_mutation.py`
synthetise un rapport PIT, `scripts/graphify/rebuild.py` reconstruit un graphe : ils produisent, ils
ne jugent pas. Le champ `CONTRAT` est le meme que celui qu `imprime_contrat` exige, donc la regle
suit le corpus au lieu de le doubler.

**Ce que `rapport.py` lance deja est couvert.** Il balaie `[0-9]*.py` et `loupe-*.py` de son propre
repertoire, et la page dit de le lancer. Ces deux globs sont VERIFIES dans son source par l auto-test :
s il elargissait sa couverture sans que ce garde le sache, la population retrecirait en silence.

## La population s arrete a `scripts/`, et ce n est pas un oubli

`.github/scripts/` porte trente et un scripts lances par les ateliers, dont `verifie_permissions.py`,
`verifie_epinglage.py` et `verifie_inventaires_ci.py` : ce sont des gardes, et de vrais. Un seul y
declare un `CONTRAT`, mesure du 2026-09-05.

Y etendre la population appliquerait donc la regle du contrat a un arbre qui ne la suit pas encore,
et TRENTE gardes en seraient exclus sans un mot. Ce serait le faux vert que ce garde existe pour
empecher. La frontiere tombera quand cet arbre declarera, ce qui est le chantier #5006 lui-meme.

## Nommer par GLOB est legitime, et voulu

Un tableau qui listerait vingt-sept lignes ne serait plus lu. La page peut donc ecrire
`scripts/adr/verifie_*.py`, et le garde tient le glob pour un nom : ce qui compte est qu un lecteur
trouve la commande a lancer, pas qu il lise un inventaire.

Usage :
    python3 scripts/methode/verifie-batterie-locale.py
    python3 scripts/methode/verifie-batterie-locale.py --auto-test
"""

import fnmatch
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import rapporte, sort_si_contrat_demande

WORKFLOWS = RACINE / ".github" / "workflows"
BATTERIE = RACINE / ".agents" / "skills" / "ouvrir-une-pr" / "SKILL.md"
RAPPORT = RACINE / "scripts" / "adr" / "rapport.py"

# Les deux globs que `rapport.py` balaie. Ils sont ECRITS ici et CONFRONTES a son source par
# l auto-test, plutot que devines : c est la seule facon qu une modification de sa couverture ne
# retrecisse pas cette population sans qu on le voie.
GLOBS_DE_RAPPORT = ("[0-9]*.py", "loupe-*.py")

INVOCATION = re.compile(r"python3 (scripts/[a-z_]+/[A-Za-z0-9_.-]+\.py)(.*)")
CHEMIN_CITE = re.compile(r"scripts/[a-z_]+/[A-Za-z0-9_.*-]+\.py")


def lancees_par_la_ci() -> set[str]:
    """Les scripts que les ateliers lancent pour JUGER, donc hors `--auto-test`."""
    trouves = set()
    for atelier in sorted(WORKFLOWS.glob("*.yml")):
        for ligne in atelier.read_text(encoding="utf-8", errors="replace").splitlines():
            appel = INVOCATION.search(ligne)
            if appel and "--auto-test" not in appel.group(2):
                trouves.add(appel.group(1))
    return trouves


def couvertes_par_rapport() -> set[str]:
    """Ce que `scripts/adr/rapport.py` lance deja, plus lui-meme."""
    couverts = {"scripts/adr/rapport.py"}
    for glob in GLOBS_DE_RAPPORT:
        couverts |= {f"scripts/adr/{p.name}" for p in (RACINE / "scripts" / "adr").glob(glob)}
    return couverts


def est_un_garde(relatif: str) -> bool:
    """Un garde DECLARE ce qu il est. Ce qui ne declare rien produit, mais ne juge pas."""
    fichier = RACINE / relatif
    if not fichier.exists():
        return False
    return "CONTRAT = {" in fichier.read_text(encoding="utf-8", errors="replace")


def noms_de_la_page(texte: str) -> list[str]:
    """Les chemins que la page cite, globs compris."""
    return sorted(set(CHEMIN_CITE.findall(texte)))


def est_nomme(relatif: str, noms: list[str]) -> bool:
    """Un chemin est nomme s il est cite tel quel, ou couvert par un glob que la page ecrit."""
    return any(nom == relatif or ("*" in nom and fnmatch.fnmatch(relatif, nom)) for nom in noms)


def population() -> list[str]:
    """Les gardes qui jugent en CI, que `rapport.py` ne lance pas."""
    return sorted(g for g in (lancees_par_la_ci() - couvertes_par_rapport()) if est_un_garde(g))


def suspects() -> tuple[list[str], int]:
    """Ceux de la population que la page de la batterie ne nomme pas, et ce qui a ete lu."""
    lisibles = population()
    noms = noms_de_la_page(BATTERIE.read_text(encoding="utf-8", errors="replace"))
    manquants = [g for g in lisibles if not est_nomme(g, noms)]
    return manquants, len(lisibles)


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    ligne = "        run: python3 scripts/methode/exemple.py"
    verifie("une invocation nue est lue", bool(INVOCATION.search(ligne)), True)
    juge = INVOCATION.search(ligne)
    verifie("et son chemin est retenu", juge.group(1), "scripts/methode/exemple.py")
    eprouve = INVOCATION.search(ligne + " --auto-test")
    verifie("un auto-test se distingue", "--auto-test" in eprouve.group(2), True)

    noms = ["scripts/adr/verifie_*.py", "scripts/methode/matrice-ergonomie.py"]
    verifie("un chemin cite tel quel est nomme", est_nomme("scripts/methode/matrice-ergonomie.py", noms), True)
    verifie("un glob de la page couvre son dossier", est_nomme("scripts/adr/verifie_okf.py", noms), True)
    # Le sens NEGATIF, sans quoi un `est_nomme` qui rendrait toujours vrai passerait tout ce qui
    # precede, et le garde serait vert sur une page qui ne nomme rien.
    verifie("un glob ne deborde pas de son dossier", est_nomme("scripts/methode/verifie_okf.py", noms), False)
    verifie("un chemin absent n est pas nomme", est_nomme("scripts/adr/rapport.py", noms), False)
    verifie("une page sans aucun chemin ne nomme rien", est_nomme("scripts/adr/verifie_okf.py", []), False)

    verifie("un garde declare un contrat", est_un_garde("scripts/methode/verifie-batterie-locale.py"), True)
    verifie("un script inexistant n en est pas un", est_un_garde("scripts/methode/absent.py"), False)

    # `rapport.py` balaie-t-il TOUJOURS ce que cette population lui soustrait ? Sans ce cas, une
    # couverture elargie chez lui retrecirait la population ici, et le cliquet baisserait tout seul.
    source = RAPPORT.read_text(encoding="utf-8", errors="replace")
    for glob in GLOBS_DE_RAPPORT:
        verifie(f"rapport.py balaie toujours {glob}", f'glob("{glob}")' in source, True)

    lisibles = population()
    verifie("la population n est pas vide", len(lisibles) > 0, True)
    return echecs


CONTRAT = {
    "geste": "garde que la CI lance et que la page de la batterie locale ne nomme pas",
    "population": "les invocations `python3 scripts/**.py` des ateliers, hors `--auto-test`, moins "
    "ce que `scripts/adr/rapport.py` balaie, moins ce qui ne declare pas de `CONTRAT`. Les trois "
    "regles sont DERIVEES et non enumerees : une liste d exemptions a tenir serait le defaut meme "
    "que ce garde combat",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/methode/verifie-batterie-locale.py --auto-test",
    "decision": "ADR 5258",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    manquants, lus = suspects()
    raise SystemExit(
        rapporte(
            "5258",
            "gardes que la page de la batterie locale ne nomme pas",
            manquants,
            lus=lus,
        )
    )
