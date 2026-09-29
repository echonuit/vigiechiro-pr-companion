"""Le lecteur de `target/index-appels.json` : qui appelle quoi, a travers tout le corpus.

Les deux autres lecteurs du fonds lisent UN FICHIER a la fois. `arbre.py` rend la structure d une
source ; PMD, par `UnusedPrivateMethod`, ne juge que les methodes privees dans leur propre classe.
Aucun ne sait dire qui appelle une methode publique ailleurs.

**Ce qui rend cet index necessaire est le taux d homonymes**, mesure le 2026-09-07 : 1 155 noms de
methode sur 9 803 sont declares dans plusieurs fichiers, et les pires massivement - `preparer` dans
185 fichiers, `executer` dans 61. Chercher un appelant par le nom seul rend un melange de methodes
sans rapport, et c est precisement ce que #4656 demande d eviter en cherchant six copies d un helper
qui s appelle `executer`.

**Ce lecteur NE PRODUIT PAS l index**, il le lit. L extracteur est un point d entree Java qui coute
20 s et 2 Go, lance a la compilation par le job qui compile deja ; un garde qui le lancerait a chaque
verdict rendrait la batterie inutilisable.
"""

from __future__ import annotations

import json
import pathlib

RACINE_DEPOT = pathlib.Path(__file__).resolve().parents[2]
INDEX = RACINE_DEPOT / "target" / "index-appels.json"


class IndexAbsent(RuntimeError):
    """L index n a pas ete produit, ou il est illisible.

    Une classe a part, et non un `FileNotFoundError` nu, pour la meme raison que `LecteurAbsent` de
    `arbre.py` : une erreur nue ressemble a un defaut du changement en cours, et se classe
    « environnemental » sans etre lue (ADR 5407).
    """


# La RECETTE, une fois, pour tous les index. Elle disait `$(cat cp.txt)`, un fichier que rien ne
# produit : la recette etait donc injouable telle quelle. Celle-ci est l invocation que la CI joue
# depuis #5531, et elle marche sans rien preparer.
_RECETTE = (
    "Produisez-le : ./mvnw -B test-compile puis\n"
    "  ./mvnw -B -q org.codehaus.mojo:exec-maven-plugin:exec \\\n"
    "    -Dexec.executable=java -Dexec.classpathScope=test \\\n"
    "    -Dexec.args='-cp %classpath fr.univ_amu.iut.commun.outils.ExtracteurIndex'"
)


def refus_de(fichier: str, quoi: str, faux_verdict: str) -> str:
    """Le message d un index absent, construit ICI pour les trois lecteurs.

    UN SEUL endroit, et ce n est pas une economie de lignes. Deux loupes du depot ont partage un
    plafond en s y conduisant differemment - l une refusant, l autre avertissant - et le partage a
    REVELE la divergence plutot que de la causer (#5567). Trois lecteurs qui recopieraient chacun
    leur refus y sont exposes de la meme facon, et rien ne le dirait tant que chacun garderait le
    sien.
    """
    return (
        f"L index {quoi} est absent : `target/{fichier}` n a pas ete produit.\n"
        f"Ce lecteur REFUSE plutot que de conclure sur ce qu il n a pas lu : un index manquant\n"
        f"rendrait « {faux_verdict} », ce qui se lit comme un depot sain.\n" + _RECETTE
    )


def charge_index(chemin: pathlib.Path, refus: str) -> dict[str, list[str]]:
    """Un index, ou un REFUS. Le chargement des trois lecteurs passe par ici.

    Le refus est le point de cette fonction. Un index absent rendrait un dictionnaire vide, donc le
    verdict le plus rassurant sur chaque question posee. C est le faux negatif le plus couteux qu un
    lecteur d index puisse produire, et le seul que rien d autre ne rattraperait.
    """
    try:
        return json.loads(chemin.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as absent:
        raise IndexAbsent(refus) from absent


_REFUS = refus_de(
    "index-appels.json", "des appels", "zero appelant pour TOUTE methode, donc du code mort partout"
)


def charge(chemin: pathlib.Path | None = None) -> dict[str, list[str]]:
    """L index des appels entier, ou un REFUS."""
    return charge_index(INDEX if chemin is None else chemin, _REFUS)


def appelants(signature: str, index: dict[str, list[str]] | None = None) -> list[str]:
    """Les types qui appellent cette methode depuis un AUTRE fichier.

    La signature est celle que l extracteur ecrit : `fr.univ_amu.iut.X.Y#methode(int,String)`. Les
    parametres en font partie, et ce n est pas decoratif : une cle sans eux fusionnait **436**
    surcharges du corpus, et #4656 cherche justement « une signature morte a cote d une signature
    vivante » - question impossible a poser si les deux partagent une entree.
    """
    return (charge() if index is None else index).get(signature, [])


def sans_appelant_externe(index: dict[str, list[str]] | None = None) -> list[str]:
    """Les methodes que personne n appelle hors de leur fichier.

    **Ce n est pas une liste de code mort**, et les confondre serait le contresens de ce lecteur.
    Une methode appelee dans sa propre classe est vivante et figure ici ; c est PMD qui juge ce
    cas-la, par `UnusedPrivateMethod`. Ce que cette liste designe est plus etroit : ce qui ne sert
    qu a son fichier, donc ce qui pourrait etre prive, local, ou duplique ailleurs.
    """
    lu = charge() if index is None else index
    return sorted(k for k, v in lu.items() if not v)


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de ce lecteur, sur un index FABRIQUE plutot que sur celui du depot.

    Un auto-test qui lirait `target/index-appels.json` mesurerait le corpus du jour et non le
    lecteur : il rougirait au premier refactoring, et resterait vert si le lecteur cessait de lire.
    """
    faux = {
        "fr.X#seul()": [],
        "fr.X#appelee(int)": ["fr.Y", "fr.Z"],
        "fr.X#appelee(String)": ["fr.W"],
    }
    return [
        (
            "une methode sans appelant externe rend une liste vide",
            appelants("fr.X#seul()", faux) == [],
        ),
        ("ses appelants sont rendus", appelants("fr.X#appelee(int)", faux) == ["fr.Y", "fr.Z"]),
        (
            "une SURCHARGE ne partage pas les appelants de sa voisine",
            appelants("fr.X#appelee(String)", faux) == ["fr.W"],
        ),
        (
            "une signature inconnue rend une liste vide, sans lever",
            appelants("fr.absent#rien()", faux) == [],
        ),
        (
            "les methodes sans appelant externe se listent",
            sans_appelant_externe(faux) == ["fr.X#seul()"],
        ),
    ]
