#!/usr/bin/env python3
"""Un lien markdown de javadoc ne se fait pas casser en deux par le formateur.

**Le defaut, reproduit.** Palantir replie une ligne trop longue a la derniere espace qui tient. Sur
une ligne `///` ordinaire il repose le prefixe `///` sur la continuation, et tout va bien. Mais si
l espace choisie tombe DANS le texte d un lien markdown, il coupe le lien et **ne repose pas** le
prefixe : la continuation devient du code, et le fichier cesse de compiler.

    /// ([ADR 4134](../../../../../../dev-docs/decisions/4134-un-banc-....md)).

devient

    /// ([ADR
    4134](../../../../../../dev-docs/decisions/4134-un-banc-....md)).

Mesure du 2026-08-26, `mvn spotless:apply` sur un temoin de trois lignes : la seconde ligne perd son
`///`, et `spotless` sort ensuite en erreur sur le fichier qu il vient d ecrire lui-meme.

**Ce que le garde refuse.** Une ligne `///` de plus de 120 caracteres qui porte un lien markdown
dont le TEXTE contient une espace. Les deux conditions ensemble, parce que ni l une ni l autre ne
suffit : une ligne longue sans lien se replie correctement (mesure : une telle ligne de 141
caracteres est revenue coupee et prefixee), et un lien court ne se replie pas du tout.

**Le remede est d ecrire, pas de rallonger la ligne.** Poser le lien pre-replie a la main, texte sur
une ligne et cible sur la suivante, ce que le depot fait deja ailleurs :

    /// Voir l [ADR
    /// 4134](../../../../../../dev-docs/decisions/4134-un-banc-....md).

**Tolerance zero, et elle est gratuite.** Mesure d ouverture : zero ligne du depot est dans ce cas,
et une seule ligne `///` depasse 120 caracteres, sans lien. Le garde ne demande donc rien a
personne aujourd hui ; il empeche la premiere.

**La cecite declaree.** Le garde ne rejoue pas le formateur : il refuse la FORME a risque, et non
le resultat du repliage. Une ligne sous 120 caracteres qu une reecriture du formateur allongerait
lui echappe, et un lien dont le texte n a pas d espace aussi - le formateur ne peut alors couper
qu avant le crochet ouvrant, ce qui est sans dommage.

Exit 0 si aucune ligne a risque, 1 sinon.
"""

import argparse
import pathlib
import re
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINES, RACINE_DEPOT  # noqa: E402

RACINE_DEPOT = pathlib.Path(__file__).resolve().parents[2]
ARBRES = RACINES

# La borne du formateur, telle que `pom.xml` la configure pour palantir-java-format.
BORNE = 120

# Un lien markdown dont le TEXTE porte au moins une espace : c est la seule espace ou le formateur
# peut couper a l interieur du lien.
LIEN_COUPABLE = re.compile(r"\[[^\]\n]* [^\]\n]*\]\(")


def a_risque(racine: pathlib.Path = None) -> list[str]:
    """Les lignes `///` que le formateur casserait, une par entree."""
    base = racine or RACINE_DEPOT
    trouves = []
    for arbre in ARBRES:
        dossier = base / arbre
        if not dossier.is_dir():
            continue
        for f in sorted(dossier.rglob("*.java")):
            try:
                lignes = f.read_text(encoding="utf-8").split("\n")
            except (UnicodeDecodeError, OSError):
                continue
            for n, ligne in enumerate(lignes, 1):
                if not ligne.strip().startswith("///"):
                    continue
                if len(ligne) > BORNE and LIEN_COUPABLE.search(ligne):
                    trouves.append(
                        f"{f.relative_to(base)}:{n}  {len(ligne)} caracteres, lien a texte espace"
                    )
    return trouves


def _auto_test() -> int:
    """Quatre cas, et ce sont les EPARGNES qui portent la regle.

    Un temoin qui ne prouverait que la detection laisserait passer un garde devenu bavard, et un
    garde bavard sur de la javadoc finit desactive - ce qui coute plus cher que son absence.
    """
    print("Auto-test du garde des liens de javadoc formatables :")
    cas = []
    lien = "(../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage.md)"
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)
        src = r / "src/main/java/fr"
        src.mkdir(parents=True)

        def ecrire(nom: str, contenu: str) -> None:
            (src / nom).write_text(contenu, encoding="utf-8")

        # 1. Le cas refuse : longue ET lien a texte espace.
        ecrire("A.java", f"/// Le defaut est revenu quatre fois ([ADR 4134]{lien}).\nclass A {{}}\n")
        cas.append(("une ligne longue avec un lien a texte espace est refusee", len(a_risque(r)) == 1))

        # 2. Longue, mais SANS lien : le formateur la replie correctement, mesure a l appui.
        ecrire("A.java", "/// " + "mot " * 40 + "\nclass A {}\n")
        cas.append(("une ligne longue sans lien passe", a_risque(r) == []))

        # 3. Un lien a texte espace, mais ligne COURTE : rien a replier.
        ecrire("A.java", "/// Voir l [ADR 4134](a.md).\nclass A {}\n")
        cas.append(("un lien a texte espace sur une ligne courte passe", a_risque(r) == []))

        # 4. Longue avec un lien dont le texte n a PAS d espace : le formateur ne peut couper
        #    qu avant le crochet, ce qui est sans dommage.
        ecrire("A.java", f"/// Le defaut est revenu quatre fois, voir ici ([ADR4134]{lien}).\nclass A {{}}\n")
        cas.append(("une ligne longue dont le lien n a pas d espace passe", a_risque(r) == []))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le garde ne dit pas ce qu'il vérifie.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le garde voit la forme à risque et laisse passer les trois voisines.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Aucun lien de javadoc que le formateur casserait")
    p.add_argument("--auto-test", action="store_true", help="éprouve le refus sur des fixtures")
    args = p.parse_args()
    if args.auto_test:
        return _auto_test()
    trouves = a_risque()
    for t in trouves:
        print(f"  {t}", file=sys.stderr)
    if trouves:
        print(f"\n{len(trouves)} ligne(s) que le formateur casserait.", file=sys.stderr)
        print("Pré-repliez le lien : le texte sur une ligne `///`, la cible sur la suivante.", file=sys.stderr)
        return 1
    print("Javadoc : aucun lien que le formateur casserait.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
