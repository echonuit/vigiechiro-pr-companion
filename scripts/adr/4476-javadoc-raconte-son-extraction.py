#!/usr/bin/env python3
"""Cliquet sur la javadoc qui raconte le refactoring dont sa classe est nee.

Le motif 30 de la grille `humaniser` - « la version precedente racontee » - dit qu une javadoc
decrit le comportement d aujourd hui, et que le passe a ses lieux : l historique, le journal des
changements, la section « alternatives ecartees » d une ADR. Une classe extraite d un controleur
trop gros en porte pourtant souvent le recit, et rien d autre :

    /// Extrait de [QualificationController] pour le garder sous le plafond de taille (PMD `NcssCount`).

Le lecteur apprend d ou vient la classe. Il n apprend pas ce qu elle fait.

**Ce que ce cliquet tient.** Les blocs de javadoc de production ou un verbe d extraction et un nom
d outil de mesure tombent DANS LA MEME PHRASE. C est cette proximite qui distingue le recit d une
mention legitime : un bloc qui cite `NcssCount` dix lignes plus bas, pour dire ou en est la classe
aujourd hui, n est pas compte.

**Ce qu il ne tient pas.** La difference entre le recit et la contrainte vivante. Deux phrases se
ressemblent :

- « Extrait de X pour le garder sous le plafond » raconte un refactoring passe ;
- « PMD refuse le litteral repete, et le depot refuse qu on le taise » enonce une regle qui vaut
  encore, et qu un futur auteur doit connaitre.

Le motif ne les separe pas. Mesure sur dix cas tires au hasard : neuf recits, une contrainte. C est
donc un cliquet `probable`, dont les suspects se trient a la main.

**Le geste attendu n est pas de supprimer la phrase**, c est de dire ce que la classe EST. Le
plafond qui a motive l extraction se retrouve dans l historique du fichier ; ce que la classe fait
ne se retrouve nulle part ailleurs.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, RACINES_ANCREES, rapporte

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4476"

# Les DEUX arbres (#4462). Une javadoc de test raconte son extraction exactement comme une javadoc
# de production, et la mesure d ouverture a rendu ZERO suspect cote test : l extension ne coute
# rien, elle empeche seulement l angle mort de se remplir.
RACINES = RACINES_ANCREES

# Les noms sous lesquels le portail qualite mesure une classe. `WMC` et `God Class` s ecrivent de
# plusieurs facons dans le depot ; les trois orthographes rencontrees sont couvertes.
OUTIL = re.compile(r"PMD|God[ ‑-]?[Cc]lass|NcssCount|ExcessiveParameterList|WMC")

# Le verbe qui raconte la naissance de la classe. « sorti de » et « deplace » ont ete essayes et
# retires : ils decrivent aussi des gestes du domaine (« sortie de la modale », « deplace le point »)
# et rendaient le motif bruyant sans rien ajouter.
EXTRAIT = re.compile(r"\b(extrait|extraite|extraits|extraites|scind[ée]e?s?)\b", re.I)

# Une phrase, au sens ou ce cliquet en a besoin : ce qui se termine par un point, un point-virgule ou
# un deux-points. Suffisant pour tenir le voisinage, et sans dependance hors stdlib.
FIN_DE_PHRASE = re.compile(r"(?<=[.;:])\s+")


def blocs(fichier: pathlib.Path) -> list[tuple[int, str]]:
    """Les blocs `///` du fichier : (ligne de debut, prose du bloc sur une seule ligne)."""
    trouves, bloc, debut = [], [], None
    for numero, ligne in enumerate(fichier.read_text(encoding="utf-8").split("\n") + [""], 1):
        nu = ligne.strip()
        if nu.startswith("///"):
            if debut is None:
                debut = numero
            bloc.append(nu[3:].strip())
        elif bloc:
            trouves.append((debut, " ".join(bloc)))
            bloc, debut = [], None
    return trouves


def raconte(corps: str) -> str | None:
    """La phrase qui raconte l extraction, ou `None` si le bloc n en porte aucune."""
    for phrase in FIN_DE_PHRASE.split(corps):
        if OUTIL.search(phrase) and EXTRAIT.search(phrase):
            return phrase.strip()
    return None


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par bloc de javadoc qui raconte l extraction dont sa classe est nee."""
    arbres = [racine] if racine else list(RACINES)
    trouves = []
    for f in sorted(x for a in arbres if a.is_dir() for x in a.rglob("*.java")):
        for debut, corps in blocs(f):
            phrase = raconte(corps)
            if phrase:
                extrait = phrase if len(phrase) <= 90 else phrase[:87] + "…"
                trouves.append(f"{f.relative_to(racine or RACINE_DEPOT)}:{debut}  {extrait}")
    return trouves


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        (r / "A.java").write_text(
            "/// Extrait de [X] pour le garder sous le plafond de taille (PMD `NcssCount`).\n"
            "class A {}\n",
            encoding="utf-8",
        )
        vus = suspects(r)
        cas.append(("le recit d une extraction est vu", len(vus) == 1))
        cas.append(("et le suspect cite la phrase", "plafond de taille" in vus[0]))

        # Le geste attendu : dire ce que la classe fait. Le cliquet doit alors se taire.
        (r / "A.java").write_text(
            "/// Libelles du bandeau de qualification.\nclass A {}\n", encoding="utf-8"
        )
        cas.append(("une javadoc qui contracte ne l est pas", suspects(r) == []))

        # LA borne qui justifie la proximite : un bloc peut nommer l outil pour dire ou en est la
        # classe AUJOURD HUI. Sans la contrainte de phrase, ces blocs-la seraient comptes a tort, et
        # le cliquet punirait la mention utile en meme temps que le recit.
        (r / "B.java").write_text(
            "/// Colonnes de la table.\n"
            "///\n"
            "/// Ce controleur est au plafond de `NcssCount` : vingt colonnes de plus le feraient\n"
            "/// depasser, et le sélecteur vit donc ici.\n"
            "class B {}\n",
            encoding="utf-8",
        )
        cas.append(("une contrainte du jour n est pas un recit", suspects(r) == []))

        # Et la borne inverse : le verbe seul, sans outil, decrit souvent un geste du domaine.
        (r / "C.java").write_text(
            "/// Le taxon extrait de la ligne Tadarida.\nclass C {}\n", encoding="utf-8"
        )
        cas.append(("un verbe sans outil ne suffit pas", suspects(r) == []))

        # Un outil seul non plus : le depot cite PMD dans des blocs qui ne racontent rien.
        (r / "D.java").write_text(
            "/// Exempte de `ExcessiveParameterList` par son annotation.\nclass D {}\n",
            encoding="utf-8",
        )
        cas.append(("un outil sans verbe ne suffit pas", suspects(r) == []))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : le cliquet ne tient pas ce qu'il annonce.",
            file=sys.stderr,
        )
        return 1
    print(
        f"\n{len(cas)} cas : le cliquet voit le récit d'une extraction, et pas la contrainte du jour."
    )
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes:
            print(f"  {s}")
        print(f"\n{len(listes)} blocs de javadoc racontent l'extraction dont leur classe est née")
        sys.exit(0)
    sys.exit(
        rapporte(ADR, "javadoc qui raconte le refactoring dont elle est née", listes, apercu=12)
    )
