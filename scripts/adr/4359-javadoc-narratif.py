#!/usr/bin/env python3
"""Cliquet sur les blocs de javadoc qui racontent au lieu de contracter.

Le code de production porte 26 814 lignes de PROSE en javadoc pour 77 891 lignes de code. Les
etiquettes de contrat - `@param`, `@return`, `@throws` - n en font que 7 % : le reste est du recit.

Beaucoup de ce recit est l histoire d un depot qui n existe plus. Un bloc de 89 lignes racontait
l implementation qu il avait remplacee ; un autre, 49 lignes, recopiait mot pour mot une ADR du
corpus. Le lecteur qui cherche ce que fait une classe traverse d abord ce qu elle a ete.

**Le seuil : 8 lignes de prose par bloc, et le compte est en LIGNES au-dela.** Un bloc de huit
lignes ou moins ne coute rien - une classe difficile merite un paragraphe. Au-dela, chaque ligne
compte une.

Le grain de la ligne a ete choisi apres coup. Au grain du BLOC, reecrire un bloc de 50 lignes en 22
ne bougeait pas le compte d un cran : le travail ne se voyait pas, et le cliquet poussait a couper
du contrat pour passer sous le seuil plutot qu a retirer du recit.

**Pourquoi le code de production seulement.** Un garde DOIT dire ce qu il verifie - c est l article
A2, et sa javadoc est sa declaration, pas du bavardage. Compter les tests reviendrait a demander au
depot de renoncer a l une de ses regles pour en tenir une autre.

**Pourquoi « probable » et non « certaine ».** La longueur se compte, ce qu il faut couper ne se
decide pas mecaniquement. Trois natures se melangent dans un bloc trop long, et seule la lecture les
separe : ce qui est caduc s enleve, ce qui est une decision se CITE au lieu d etre recopie, ce que
le code dit deja disparait. Le script rend des SUSPECTS.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero, et `_commun.py` la
# retrouve par `dev-docs/decisions/{numero}-*.md`.
ADR = "4359"

RACINE = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = RACINE / "src" / "main" / "java"

# Au-dela, chaque ligne de prose compte une. Mesure du 2026-08-23 : 3 668 lignes de dette.
SEUIL = 8

# Une etiquette de contrat : elle documente une entree ou une sortie, elle ne raconte rien.
ETIQUETTE = re.compile(r"@\w")


def prose(lignes: list[str]) -> int:
    """Les lignes d un bloc qui ne sont ni vides ni du contrat.

    Une etiquette `@param` tient souvent sur plusieurs lignes. Les SUITES appartiennent a
    l etiquette : les compter comme de la prose penaliserait un record bien documente, ce que cette
    decision refuse explicitement. Le banc ne le voyait pas - ses trente `@param` tenaient chacun
    sur une ligne.
    """
    compte = 0
    dans_etiquette = False
    for l in lignes:
        corps = l.strip()[3:].strip()
        if not corps:
            dans_etiquette = False
            continue
        if ETIQUETTE.match(corps):
            dans_etiquette = True
            continue
        if dans_etiquette:
            continue
        compte += 1
    return compte


def blocs(fichier: pathlib.Path) -> list[tuple[int, int]]:
    """Les blocs `///` du fichier : (ligne de depart, lignes de prose)."""
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    trouves, i = [], 0
    while i < len(lignes):
        if lignes[i].strip().startswith("///"):
            j = i
            while j < len(lignes) and lignes[j].strip().startswith("///"):
                j += 1
            trouves.append((i + 1, prose(lignes[i:j])))
            i = j
        else:
            i += 1
    return trouves


def suspects(racine: pathlib.Path = None) -> list[str]:
    """Un suspect par LIGNE de prose au-dela du seuil, et non par bloc.

    Le grain a ete choisi apres coup, et la raison merite d etre ecrite. Compter les BLOCS trop
    longs ne bougeait pas d un cran quand on reecrivait un bloc de 50 lignes en 22 : le travail ne
    se voyait pas, et le cliquet poussait a couper du CONTRAT pour passer sous le seuil plutot qu a
    retirer du recit. Au grain de la ligne, chaque ligne retiree compte, et un bloc qui garde
    vingt lignes de contrat coute simplement douze - ce qu il vaut.

    Un bloc de huit lignes ou moins ne coute rien : une classe difficile merite un paragraphe.
    """
    racine = racine or PRODUCTION
    trouves = []
    for f in sorted(racine.rglob("*.java")):
        nom = f.relative_to(racine.parents[2]) if racine == PRODUCTION else f.name
        for depart, n in blocs(f):
            for i in range(n - SEUIL):
                trouves.append(f"{nom}:{depart}  ligne {SEUIL + i + 1} d un bloc de {n}")
    return trouves


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        def pose(nom: str, contenu: str) -> None:
            (r / nom).write_text(contenu, encoding="utf-8")

        court = "\n".join(f"/// Ligne {i}." for i in range(SEUIL)) + "\nclass A {}\n"
        pose("A.java", court)
        cas.append(("un bloc au seuil passe", suspects(r) == []))

        long_ = "\n".join(f"/// Ligne {i}." for i in range(SEUIL + 3)) + "\nclass B {}\n"
        pose("A.java", long_)
        f = suspects(r)
        # Trois lignes au-dela du seuil : trois suspects. C est ce grain qui fait qu une reecriture
        # partielle se voit, au lieu d attendre que le bloc passe sous le seuil pour compter.
        cas.append((f"un bloc de {SEUIL + 3} lignes coute trois", len(f) == 3))
        cas.append(("le suspect dit la taille du bloc", any(f"bloc de {SEUIL + 3}" in x for x in f)))

        # LE cas qui rend le seuil juste : les etiquettes de contrat ne racontent rien. Sans cette
        # exception, un record de trente champs serait le pire suspect du depot alors qu il est
        # exemplaire.
        tags = "/// Resume.\n" + "\n".join(f"/// @param p{i} un champ" for i in range(30)) + "\nclass C {}\n"
        pose("A.java", tags)
        cas.append(("trente @param ne racontent rien", suspects(r) == []))

        # Une etiquette tient souvent sur plusieurs lignes. Sans ce cas, les SUITES comptaient
        # comme de la prose et un record bien documente devenait le pire suspect du depot - ce que
        # le cas precedent ne voyait pas, ses trente etiquettes tenant chacune sur une ligne.
        longs = "/// Resume.\n" + "\n".join(
            f"/// @param p{i} un champ\n///     dont l explication continue\n///     sur trois lignes"
            for i in range(30)) + "\nclass C {}\n"
        pose("A.java", longs)
        cas.append(("les suites d une etiquette non plus", suspects(r) == []))

        # Une ligne `///` vide ne compte pas non plus : elle aere, elle ne dit rien.
        aere = "\n".join(f"/// Ligne {i}.\n///" for i in range(SEUIL)) + "\nclass D {}\n"
        pose("A.java", aere)
        cas.append(("les lignes vides n allongent pas le bloc", suspects(r) == []))

        pose("A.java", "class E {}\n")
        cas.append(("un fichier sans javadoc ne rend rien", suspects(r) == []))

        # Deux blocs dans un meme fichier cumulent leur dette : le grain est la ligne.
        pose("A.java", long_ + "\n" + long_)
        cas.append(("deux blocs longs cumulent leur dette", len(suspects(r)) == 6))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le cliquet ne compte pas ce qu'il annonce.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le cliquet voit un bloc narratif et laisse passer le contrat.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes[:30]:
            print(f"  {s}")
        print(f"\n{len(listes)} lignes de prose au-delà de {SEUIL} par bloc")
        sys.exit(0)
    sys.exit(rapporte(ADR, "javadoc qui raconte au lieu de contracter", listes, apercu=20))
