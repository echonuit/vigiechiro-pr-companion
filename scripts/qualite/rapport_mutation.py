#!/usr/bin/env python3
"""Synthèse d'un rapport PIT : ce qui a survécu, et où.

Le pourcentage seul ne dit rien - c'est la leçon de la passe 6 de #2554. Ce qui se lit, ce sont les
**classes qui portent des survivants**, parce qu'un survivant est une question posée à un humain :
vrai trou, mutant équivalent, ou artefact de ciblage.

Le script se lit donc de bas en haut : le score situe, la liste travaille.

Usage :
    python3 scripts/qualite/rapport_mutation.py [--markdown] [chemin/mutations.xml]
"""

import collections
import pathlib
import sys
import xml.etree.ElementTree as ET

RAPPORT = pathlib.Path("target/pit-reports/mutations.xml")

# Au-delà, la liste cesse d'être une liste et devient un mur. Les classes sont triées par nombre de
# survivants : les premières sont celles où une garantie se raconte.
PLAFOND_CLASSES = 15

# Ce que PIT appelle « tué » : le mutant a été DÉTECTÉ, peu importe comment. Un mutant qui fait boucler
# le code est détecté par le butoir (`TIMED_OUT`) aussi sûrement que par une assertion. Ne compter que
# `KILLED` donnait un score inférieur de deux points à celui que PIT affiche lui-même - un rapport qui
# contredit son propre outil ne se fait pas croire longtemps.
DETECTES = ("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR")


def lire(chemin: pathlib.Path):
    # PIT écrit ce fichier AU FIL DE L'EAU : lu pendant un run, il est tronqué. Le laisser remonter une
    # ParseError brute donnerait une trace Python dans le résumé du job ; pire, un fichier qui se
    # trouverait coupé sur une frontière d'élément se lirait comme un rapport complet, donc comme un
    # décompte honnête. On préfère refuser de compter.
    racine = ET.parse(chemin).getroot()
    etats = collections.Counter()
    survivants = collections.Counter()
    for mutation in racine.iter("mutation"):
        etat = mutation.get("status")
        etats[etat] += 1
        if etat in ("SURVIVED", "NO_COVERAGE"):
            classe = (mutation.findtext("mutatedClass") or "?").rsplit(".", 1)[-1]
            survivants[classe] += 1
    return etats, survivants


def ventilation_sans_assertion(etats: collections.Counter) -> str:
    """Ce que le score doit à autre chose qu'une assertion, énoncé en clair quand il y en a.

    Le décompte, lui, ne change pas : un mutant qui fait boucler le code est bien détecté, et un rapport
    qui contredit son propre outil ne se fait pas croire (cf. `DETECTES`). Ce qui manquait n'était pas le
    chiffre mais sa **composition** : cinq expirations fondues dans 685 détections ne se voyaient pas, et
    deux cents ne se verraient pas davantage.
    """
    libelles = {
        "TIMED_OUT": ("expiration", "expirations"),
        "MEMORY_ERROR": ("mémoire épuisée", "mémoires épuisées"),
        "RUN_ERROR": ("erreur d'exécution", "erreurs d'exécution"),
    }
    parts = []
    for etat, (singulier, pluriel) in libelles.items():
        nombre = etats.get(etat, 0)
        if nombre:
            parts.append(f"{nombre} {singulier if nombre == 1 else pluriel}")
    return ", ".join(parts)


def markdown(etats: collections.Counter, survivants: collections.Counter) -> str:
    total = sum(etats.values())
    tues = sum(etats.get(etat, 0) for etat in DETECTES)
    score = (100 * tues / total) if total else 0.0
    lignes = [
        "## Mutation (PIT)",
        "",
        f"**{tues} / {total} mutants détectés** ({score:.1f} %), "
        f"{etats.get('SURVIVED', 0)} survivants, {etats.get('NO_COVERAGE', 0)} sans couverture.",
        "",
    ]
    detections_sans_assertion = ventilation_sans_assertion(etats)
    if detections_sans_assertion:
        lignes += [
            f"⚠️ Dont **{detections_sans_assertion}** détectés, mais par épuisement plutôt que par une"
            " assertion. Un butoir mal calibré gonfle donc ce score : mesuré sur une classe de vue, un"
            " butoir absurde a fait passer 43 % à **100 %** (#2768). Si cette part grossit, c'est le"
            " butoir ou le périmètre qu'il faut regarder, pas les tests.",
            "",
        ]
    if not survivants:
        lignes.append("Aucun survivant : rien à arbitrer cette semaine.")
        return "\n".join(lignes)

    lignes += [
        "Un survivant n'est pas un défaut : c'est une **question**. Vrai trou → un test. Mutant",
        "équivalent → assumé, sans test creux. Artefact de ciblage → élargir et remesurer.",
        "",
        "| Classe | Survivants + non couverts |",
        "|---|---|",
    ]
    for classe, nombre in survivants.most_common(PLAFOND_CLASSES):
        lignes.append(f"| `{classe}` | {nombre} |")
    reste = len(survivants) - PLAFOND_CLASSES
    if reste > 0:
        # Dire ce qu'on tronque : une liste coupée en silence se lit comme une liste complète.
        lignes.append("")
        lignes.append(f"_{reste} autres classes portent des survivants, non listées ici._")
    return "\n".join(lignes)


def main(argv: list[str]) -> int:
    arguments = [a for a in argv[1:] if a != "--markdown"]
    chemin = pathlib.Path(arguments[0]) if arguments else RAPPORT
    if not chemin.exists():
        print(f"Rapport introuvable : {chemin}", file=sys.stderr)
        print(
            "PIT a-t-il tourné ? Rappel : le but seul n'exécute aucune phase, il faut",
            file=sys.stderr,
        )
        print(
            "  ./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage",
            file=sys.stderr,
        )
        return 1
    try:
        etats, survivants = lire(chemin)
    except ET.ParseError as tronque:
        print(f"Rapport illisible ({chemin}) : {tronque}", file=sys.stderr)
        print(
            "Il est écrit au fil de l'eau : une lecture pendant le run tombe sur un fichier",
            file=sys.stderr,
        )
        print("incomplet. Attendre la fin de la mesure.", file=sys.stderr)
        return 2
    print(markdown(etats, survivants))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
