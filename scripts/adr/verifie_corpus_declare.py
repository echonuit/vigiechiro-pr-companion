#!/usr/bin/env python3
"""Le corpus qu un garde lit se declare dans `_commun.py`, il ne se recopie pas (issue #4586).

#4488 a decide que les gardes de code lisent les DEUX arbres, production et tests, et
`verifie_scripts.py` tient cette decision par un cas nomme. Mais sa liste est ENUMEREE, et elle a
derive : dix entrees pour quinze gardes qui portent le chemin de l arbre de test. Six lisent donc
les deux arbres sans que rien ne le verifie, et l un d eux peut cesser d en lire un - son compte
baissera, aucun cliquet ne se plaint qu on lui retire du corpus.

Le depot connait ce piege et l a ecrit ailleurs : `verifie_temoins_non_decoratifs.py` derive sa
liste plutot que de l enumerer, parce qu un garde neuf passerait au travers. La lecon valait pour
les temoins, pas encore pour le corpus.

**Ce que ce garde refuse.** Un fichier de `scripts/adr/` qui ecrit lui-meme le chemin d un arbre de
sources. Le corpus s importe depuis `_commun` : `RACINES` pour les deux arbres, `PRODUCTION` seule
pour une exception assumee. C est ce refus qui rend la DERIVATION fiable : sans lui, un garde neuf
reecrit le chemin et redevient invisible a la liste derivee.

**Les deux formes, et pourquoi la normalisation.** Le chemin s ecrit d un morceau chez les uns,
`Path("src/main/java")`, et en segments chez les autres, `RACINE / "src" / "main" / "java"`. Un
refus qui ne verrait que la premiere se contournerait en decoupant une chaine, et serait decoratif
au sens de #4490. La ligne est donc normalisee - guillemets, barres, virgules et blancs retires -
avant qu on y cherche la sequence.

**Ce qu il ne refuse PAS**, et c est delibere : le chemin d un fichier de donnees, d un dossier de
documentation, et la MENTION d un arbre dans une prose. Sans cette derniere exemption, ce garde
interdirait d ecrire l ADR qui le justifie.

**Ni les FIXTURES, et la marge les distingue.** Un corpus se declare au niveau du module ; une
fixture se construit la ou elle sert, donc indentee. `verifie_scripts.py` batit des arbres temoins
par dizaines - `"src/main/java/Exemple.java"` et ses voisins - et aucun n est le corpus du depot.
Refuser sur la marge zero separe les deux sans enumerer d exception, et une declaration qu on
indenterait pour y echapper ne serait plus une declaration de module.

**Pourquoi la sequence se construit au lieu de s ecrire.** Un detecteur textuel qui porte sa propre
cible se compte lui-meme, et l article A2 demande qu il s exclue de son corpus. L idiome vient de
`verifie-corps-pr.sh`, qui construit ses glyphes pour que le garde des cadratins ne compte pas sa
prose.

Aucune dependance hors stdlib : lance comme ses voisins, `python3 scripts/adr/verifie_corpus_declare.py`.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

ADR = "4586"

DOSSIER = pathlib.Path("scripts/adr")

# Le fonds commun EST l endroit ou le corpus se declare, et ce fichier-ci porte la sequence : tous
# deux se compteraient eux-memes.
RESERVES = {"_commun.py", pathlib.Path(__file__).name}

# Construite, jamais ecrite : voir la note en tete.
_RACINE = "sr" + "c"
_ARBRES = ("ma" + "in", "te" + "st")
SEQUENCES = tuple(_RACINE + arbre + "java" for arbre in _ARBRES)

# Ce qui separe les segments d un chemin, quelle que soit la forme employee.
BRUIT = re.compile(r"[\"'/,\s]+")

# Une ligne de prose n affecte rien et n appelle rien : elle CITE. Les trois ouvertures qui comptent
# ici sont le croisillon, le triple guillemet d une docstring, et la continuation d un bloc.
PROSE = re.compile(r"^\s*(?:#|\"\"\"|''')")


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par ligne qui ECRIT un chemin d arbre au lieu de l importer.

    `racine` n existe que pour le temoin, qui a besoin d un corpus qu il maitrise : mesurer sur le
    depot ne separerait pas ce que le garde compte de ce qu il epargne.
    """
    dossier = (racine / DOSSIER) if racine else DOSSIER
    trouves = []
    for source in sorted(dossier.glob("*.py")):
        if source.name in RESERVES:
            continue
        dans_docstring = False
        for numero, ligne in enumerate(source.read_text(encoding="utf-8").split("\n"), 1):
            # Une docstring de plusieurs lignes cite sans que chaque ligne porte une ouverture.
            if ligne.count('"""') == 1 or ligne.count("'''") == 1:
                dans_docstring = not dans_docstring
                continue
            if dans_docstring or PROSE.match(ligne):
                continue
            # Un corpus se declare au niveau du module ; ce qui est indente construit une fixture.
            if ligne[:1].isspace():
                continue
            nu = BRUIT.sub("", ligne)
            for sequence in SEQUENCES:
                if sequence in nu:
                    arbre = sequence[len(_RACINE) : -len("java")]
                    trouves.append(
                        f"{source.as_posix()}:{numero}  l arbre {arbre} ecrit au lieu d etre importe"
                    )
                    break
    return trouves


def auto_test() -> int:
    """Le garde se prouve dans les DEUX sens, sinon il ne prouve rien."""
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    def pose(racine, texte, nom="faux-cliquet.py"):
        (racine / DOSSIER / nom).write_text(texte, encoding="utf-8")

    plein = '"' + _RACINE + "/" + _ARBRES[0] + '/java"'
    segments = '"' + _RACINE + '" / "' + _ARBRES[0] + '" / "java"'

    print("Auto-test du garde du corpus declare (#4586) :")
    with tempfile.TemporaryDirectory() as brut:
        racine = pathlib.Path(brut)
        (racine / DOSSIER).mkdir(parents=True)

        # 1. Le sens POSITIF, forme d un seul morceau.
        pose(racine, f"SOURCES = pathlib.Path({plein})\n")
        verifie("un chemin ecrit d un morceau est vu", len(suspects(racine)), 1)

        # 2. Le sens POSITIF, forme en SEGMENTS. Sans ce cas, le refus se contournerait en
        #    decoupant la chaine, et quatre gardes du depot y echappaient deja.
        pose(racine, f"SOURCES = RACINE / {segments}\n")
        verifie("un chemin ecrit en segments est vu", len(suspects(racine)), 1)

        # 3. Le sens NEGATIF : l import ne l est pas. Sans lui, un garde qui rendrait TOUJOURS un
        #    suspect passerait les deux premiers et n aurait rien prouve.
        pose(racine, "from _commun import RACINES\n")
        verifie("un corpus importe ne l est pas", len(suspects(racine)), 0)

        # 4. La MENTION n est pas l usage, en commentaire comme en docstring.
        pose(racine, f"# Les gardes lisent {plein} et son voisin, cf #4488.\n")
        verifie("un chemin cite en commentaire n est pas un usage", len(suspects(racine)), 0)
        pose(racine, f'"""Ce garde lit\n{plein}\net son voisin.\n"""\n')
        verifie("un chemin cite dans une docstring n est pas un usage", len(suspects(racine)), 0)

        # 5. Une FIXTURE indentee n est pas une declaration de corpus. Sans ce cas, le garde
        #    refuserait les dizaines d arbres temoins que le harnais batit pour eprouver ses voisins.
        pose(racine, "def temoin():\n    ecrit(" + plein + ")\n")
        verifie("un chemin indente construit une fixture, pas un corpus", len(suspects(racine)), 0)

        # 6. Le fonds commun s exclut : c est la ou le corpus DOIT etre ecrit.
        pose(racine, "")
        pose(racine, f"PRODUCTION = pathlib.Path({plein})\n", nom="_commun.py")
        verifie("`_commun.py` peut ecrire le chemin", len(suspects(racine)), 0)

    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    sys.exit(
        rapporte(ADR, "corpus recopie : un chemin d arbre ecrit au lieu d etre importe", suspects())
    )
