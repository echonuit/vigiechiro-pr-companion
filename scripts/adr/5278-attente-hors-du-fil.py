#!/usr/bin/env python3
"""Une attente qui lit le graphe de scene le lit SUR le fil JavaFX (issue #5278).

`Attente.queSurLeFil` existe depuis #4408, et sa javadoc dit pourquoi :

    Un predicat qui touche le graphe de scene doit etre lu sur le fil FX, qui n est pas partageable :
    sinon il lit un graphe qu un autre fil est en train d ecrire.

Le patron n a pas essaime. Mesure du 2026-09-05 : 138 appels a `Attente.que` ou `queSurLeFil`, dont
HUIT sur le fil, et 63 qui lisent le graphe depuis le fil du test.

## Ce que cela produit, et ce n est pas une hypothese

`RetourApresVerificationE2ETest.depuis_multisite_la_verification_se_propage` a leve en CI :

    java.lang.IndexOutOfBoundsException: Index 6 out of bounds for length 6

Son predicat lisait `getItems()` d une `TableView` que le chargement asynchrone remplacait. Un index
egal a la longueur est la signature d une lecture concurrente, pas d un decalage applicatif. Le banc
est au releve des bancs instables, 2 chutes en tete sur 884 tirages et 3 de plus comme victime.

## La regle se DERIVE des lectures de noeuds

Un predicat touche le graphe s il appelle `lookup(`, `queryAs`, `getItems()`, `getScene()`,
`getChildren()` ou `getText()`. Ce sont des lectures de noeuds, et elles n ont de sens que sur le fil
FX.

Deriver plutot qu enumerer une liste de classes est delibere : une liste a tenir a la main derive de
ce qu elle decrit sans que rien ne rougisse, ce que l ADR 5258 vient de mesurer sur une autre
population.

## Ce que ce garde NE fait pas, et c est declare

**Il ne dit pas qu une conversion est due.** `queSurLeFil` fait un aller-retour sur le fil FX a chaque
tour de boucle : la ou le predicat ne touche le graphe que par un chemin sur, la conversion couterait
sans rien tenir. Le garde COMPTE ; le jugement reste au site, et un site laisse en `que` ecrit sa
raison plutot que de sortir du compte en silence.

**Il ne suit pas un predicat qui delegue.** Un predicat qui appelle une methode privee touchant, elle,
le graphe lui echappe. Limite declaree et non oubli : la suivre demanderait un graphe d appels, la ou
le motif textuel attrape deja 63 sites.

Usage :
    python3 scripts/adr/5278-attente-hors-du-fil.py
    python3 scripts/adr/5278-attente-hors-du-fil.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import TESTS_ANCRES, rapporte, sort_si_contrat_demande


APPEL = re.compile(r"Attente\.(que|queSurLeFil)\s*\(")
# Les lectures de noeuds. `getText()` y est parce qu un libelle se lit sur le noeud qui le porte, et
# que c est la forme la plus frequente de l attente « le texte a change ».
LECTURE_DE_NOEUD = re.compile(
    r"lookup\(|queryAs|\.getItems\(\)|\.getScene\(\)|getChildren\(\)|\.getText\(\)"
)

# Au-dela, ce n est plus un appel mais un fichier mal ferme : la borne evite de balayer la source
# entiere si une parenthese manque, et le dit par un suspect plutot qu en bouclant.
BORNE = 4000


def argument(source: str, depuis: int) -> str:
    """Le texte de l appel qui commence a `depuis`, par equilibrage de parentheses."""
    profondeur = 0
    for i in range(depuis, min(depuis + BORNE, len(source))):
        if source[i] == "(":
            profondeur += 1
        elif source[i] == ")":
            profondeur -= 1
            if profondeur == 0:
                return source[depuis : i + 1]
    return source[depuis : depuis + BORNE]


def sites(source: str) -> list[int]:
    """Les lignes des `Attente.que` dont l argument lit le graphe de scene."""
    trouves = []
    for appel in APPEL.finditer(source):
        if appel.group(1) != "que":
            continue
        if LECTURE_DE_NOEUD.search(argument(source, appel.end() - 1)):
            trouves.append(source[: appel.start()].count("\n") + 1)
    return trouves


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les sites fautifs sous `racine`, nommes par leur chemin et leur ligne.

    La racine s INJECTE parce que `verifie_scripts.py` exerce ce detecteur sur un arbre temporaire :
    un detecteur qui ne lit qu un chemin fixe n est tenu que par son cliquet, c est-a-dire par un
    compte qui ne monte pas.
    """
    racine = TESTS_ANCRES if racine is None else racine
    fautifs = []
    for fichier in sorted(racine.rglob("*.java")):
        source = fichier.read_text(encoding="utf-8", errors="replace")
        ou = fichier.relative_to(racine)
        fautifs += [f"{ou}:{ligne}" for ligne in sites(source)]
    return fautifs


def lus(racine: pathlib.Path | None = None) -> int:
    """Le nombre d appels a `Attente` lus : ce que le garde a REGARDE, pas ce qu il a retenu."""
    racine = TESTS_ANCRES if racine is None else racine
    return sum(
        len(APPEL.findall(f.read_text(encoding="utf-8", errors="replace")))
        for f in racine.rglob("*.java")
    )


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    fautif = 'Attente.que(\n () -> !robot.lookup("#t").queryAll().isEmpty(),\n "que ca paraisse");\n'
    verifie("un que qui lit le graphe est vu", sites(fautif), [1])

    surlefil = fautif.replace("Attente.que(", "Attente.queSurLeFil(")
    verifie("le MEME site en queSurLeFil ne l est plus", sites(surlefil), [])

    # Le sens NEGATIF, sans quoi un motif qui accepterait tout passerait le premier cas et rendrait le
    # garde vert sur un depot entierement fautif.
    sobre = 'Attente.que(() -> vm.chargement().not().get(), "que le chargement finisse");\n'
    verifie("un predicat qui ne touche pas le graphe est ignore", sites(sobre), [])

    verifie("une source sans Attente ne rend rien", sites("int x = lookup(3);\n"), [])

    multiple = fautif + "\n" + sobre + "\n" + fautif
    verifie("plusieurs sites sont tous vus", len(sites(multiple)), 2)
    # Les deux sauts de jointure decalent le second site : la ligne comptee est la 7, pas la 6.
    verifie("et leurs lignes sont justes", sites(multiple), [1, 7])

    # Les cinq lectures de noeud, une par une : sans cela un motif qui n en verrait qu une passerait
    # tout ce qui precede, le premier cas employant `lookup(` ET `queryAll`.
    for lecture in (".getItems()", ".getScene()", "getChildren()", ".getText()", "queryAs"):
        un = f'Attente.que(() -> n{lecture}.isEmpty(), "que ca vienne");\n'
        verifie(f"la lecture {lecture} est vue", sites(un), [1])

    # Un appel non ferme ne doit ni boucler ni faire planter le garde.
    verifie("un appel non ferme ne fait pas planter", sites('Attente.que(() -> lookup("#a")'), [1])

    verifie("le garde a lu des appels reels", lus() > 0, True)
    return echecs


CONTRAT = {
    "geste": "attente dont le predicat lit le graphe de scene depuis le fil du test",
    "population": "les appels a `Attente.que` de src/test/java, l argument etant delimite par "
    "equilibrage de parentheses. `queSurLeFil` en est exclu : c est la forme JUSTE. Un predicat qui "
    "delegue a une methode privee touchant le graphe echappe au motif, limite declaree dans l en-tete",
    "dispositif": "cliquet",
    "seuil": "63, polarite=descend",
    "temoin": "scripts/adr/5278-attente-hors-du-fil.py --auto-test",
    "decision": "ADR 5278",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    raise SystemExit(
        rapporte(
            "5278",
            "attentes qui lisent le graphe de scene hors du fil JavaFX",
            suspects(),
            apercu=12,
            lus=lus(),
        )
    )
