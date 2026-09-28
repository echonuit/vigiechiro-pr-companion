#!/usr/bin/env python3
"""Qui appelle cette methode, quand le depot en declare 188 du meme nom.

Le lecteur `scripts/_commun/index.py` repond deja « qui appelle X », et il est livre depuis #5473.
Rien ne l importe. La raison est dans sa signature : il exige la cle EXACTE que l extracteur ecrit,
parametres compris, `fr.univ_amu.iut.audio.outils.GraineSonsValidation#preparer()`. Personne ne l a
sous la main.

La question qu on pose vraiment est « qui appelle `preparer` », et c est la question a laquelle aucun
lecteur du depot ne repondait. Mesure du 2026-09-28, sur 14 816 methodes indexees :

    grep -rl preparer --include=*.java src/   ->  243 fichiers a lire
    cet outil                                 ->  188 declarations, dont 9 appelees d ailleurs

Le depot porte **1 235 noms declares dans plusieurs classes**, sur 9 835 noms distincts. Les pires
sont massifs : `preparer` dans 188 classes, `start` dans 150, `nettoyer` dans 77. Devant eux, un
`grep` par nom ne rend pas une reponse, il rend une liste de fichiers a ouvrir.

## Ce qu il ne dit PAS, et le contresens que ce fichier existe pour ne pas commettre

« Aucun appelant hors de son fichier » n est PAS du code mort. C est l etat normal d un helper privé,
et `index.py` l ecrit deja dans sa propre docstring. Mesure : 10 376 methodes sur 14 816 sont dans ce
cas, soit **70 % du corpus**, dont 5 014 cas de test que JUnit appelle par reflexion, et le reste
domine par des helpers appeles dans leur propre fichier et des `configure` / `fournir*` de Guice.

Cet outil ne juge donc rien, ne compte aucun suspect et n a pas de cliquet. Il REPOND. Le seul cas ou
il sort non nul est l absence de l index, parce qu un index manquant rendrait « aucun appelant » pour
TOUTE methode, ce qui se lit exactement comme du code mort.

## Aucun banc ne mute ce fichier, et ce n est pas son dossier qui l en protege

Mesure faite a sa livraison : il **entre** dans la derivation brute du banc de
`scripts/methode/temoins-de-methode-non-decoratifs.py`, qui lit `lint.yml`, et c est le filtre
`declare_un_contrat` qui l en retire - avec `graphify/pont_ressources.py`, `graphify/rebuild.py` et
`mkdocs/bandeau_adr.py`.

**Lui donner un `CONTRAT` le ferait donc entrer au banc sans un mot**, et ses cas seraient alors
eprouves par mutation automatique. En l etat ils ne le sont pas : les six mutations de ce fichier ont
ete jouees A LA MAIN, et deux d entre elles ont demasque un cas qui ne pouvait pas rougir. Qui touche
a ce fichier sans rejouer ces six-la ne saura pas si ses cas jugent encore.

Usage :
    python3 scripts/qualite/appelants.py <nom|signature> [--index CHEMIN]
    python3 scripts/qualite/appelants.py --auto-test
"""

import io
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import cas_d_auto_test
from _commun.index import IndexAbsent, charge

# Au-dela, la liste cesse d etre une reponse et devient un mur. C est le meme plafond, et pour la
# meme raison, que les quinze classes de `rapport_mutation.py`.
PLAFOND = 15


def declarations(cible: str, index: dict[str, list[str]]) -> list[str]:
    """Les cles de l index que `cible` designe, qu elle soit un NOM ou une signature entiere.

    Deux formes, parce que l utilisateur a l une et le lecteur exige l autre. Une signature passe
    telle quelle ; un nom nu se resout en toutes les declarations qui le portent, ce qui est
    precisement le service rendu.

    Le depart se fait sur `#` et non sur la presence de parentheses : `preparer()` est une signature
    partielle sans porteur, et la traiter comme une cle rendrait zero sans le dire.
    """
    if "#" in cible:
        return [cible] if cible in index else []
    return sorted(k for k in index if k.split("#", 1)[1].split("(", 1)[0] == cible)


def reponse(cible: str, index: dict[str, list[str]]) -> tuple[list[tuple[str, list[str]]], int]:
    """Les declarations APPELEES d ailleurs avec leurs appelants, et le compte des autres.

    Le couple, et non une liste unique, parce que les deux moities ne se lisent pas pareil. Celles
    qui ont un appelant externe sont la reponse a la question posee. Les autres ne sont pas un
    defaut : les nommer une par une noierait la reponse sous 179 lignes sans information, et les
    taire ferait croire que le nom n est declare que neuf fois.
    """
    trouvees = declarations(cible, index)
    appelees = [(sig, index[sig]) for sig in trouvees if index[sig]]
    appelees.sort(key=lambda couple: (-len(couple[1]), couple[0]))
    return appelees, len(trouvees) - len(appelees)


def rendu(cible: str, appelees: list[tuple[str, list[str]]], muettes: int) -> str:
    lignes = [f"APPELANTS {cible}"]
    if not appelees and not muettes:
        lignes.append("  aucune declaration de ce nom dans l index")
        lignes.append(f"\nAPPELANTS {cible} | declarations=0 | appelees=0")
        return "\n".join(lignes)

    for sig, qui in appelees[:PLAFOND]:
        lignes.append(f"  {sig}")
        for appelant in qui[:PLAFOND]:
            lignes.append(f"      <- {appelant}")
        if len(qui) > PLAFOND:
            lignes.append(f"      <- ... {len(qui) - PLAFOND} autre(s)")
    if len(appelees) > PLAFOND:
        # Dire ce qu on tronque : une liste coupee en silence se lit comme une liste complete.
        lignes.append(f"  ... {len(appelees) - PLAFOND} autre(s) declaration(s) appelee(s)")
    if muettes:
        lignes.append(
            f"  {muettes} declaration(s) sans appelant hors de leur fichier, ce qui est l etat"
            " NORMAL d un helper prive et non du code mort"
        )
    lignes.append(
        f"\nAPPELANTS {cible} | declarations={len(appelees) + muettes} | appelees={len(appelees)}"
    )
    return "\n".join(lignes)


def auto_test() -> int:
    """Les cas de cet outil, sur un index FABRIQUE plutot que sur celui du depot.

    La raison est celle que `index.py` donne pour la sienne : un auto-test qui lirait
    `target/index-appels.json` mesurerait le corpus du jour et non l outil. Il rougirait au premier
    refactoring, et resterait vert si l outil cessait de repondre.

    Le cas qui compte est le DERNIER, et c est le seul qui ne porte pas sur le calcul : un index
    absent doit REFUSER. Sans lui, l outil rendrait « aucun appelant » pour toute methode, et ce
    silence se lirait comme un dépôt sain.
    """
    verifie, echecs = cas_d_auto_test()

    # L ordre ALPHABETIQUE de cet index contredit son ordre par nombre d appelants, et c est la
    # seule raison de ces chiffres-la. La premiere version donnait deux appelants a `fr.A` et un a
    # `fr.C` : les deux ordres coincidaient, et le cas du tri restait VERT quand on retirait le tri.
    # Une fixture ou le resultat attendu se lit aussi bien sans le code qu avec ne prouve rien.
    faux = {
        "fr.A#preparer()": ["fr.X"],
        "fr.B#preparer()": [],
        "fr.C#preparer(int)": ["fr.U", "fr.V", "fr.W"],
        "fr.D#autre()": ["fr.T"],
    }

    verifie(
        "un nom nu rend TOUTES ses declarations, porteurs et surcharges confondus",
        lambda: declarations("preparer", faux),
        ["fr.A#preparer()", "fr.B#preparer()", "fr.C#preparer(int)"],
    )
    verifie(
        "un nom ne capture pas un prefixe : `autre` ne rend pas `autrement`",
        lambda: declarations("autr", faux),
        [],
    )
    verifie(
        "une signature entiere passe telle quelle",
        lambda: declarations("fr.C#preparer(int)", faux),
        ["fr.C#preparer(int)"],
    )
    verifie(
        "une signature SANS porteur rend zero plutot que d etre prise pour un nom",
        lambda: declarations("preparer()", faux),
        [],
    )
    verifie(
        "les declarations appelees sont rendues, les muettes seulement COMPTEES",
        lambda: reponse("preparer", faux),
        ([("fr.C#preparer(int)", ["fr.U", "fr.V", "fr.W"]), ("fr.A#preparer()", ["fr.X"])], 1),
    )
    verifie(
        "elles sont triees par nombre d appelants decroissant",
        lambda: [sig for sig, _ in reponse("preparer", faux)[0]],
        ["fr.C#preparer(int)", "fr.A#preparer()"],
    )
    verifie(
        "un nom inconnu rend une reponse vide, sans lever",
        lambda: reponse("inexistant", faux),
        ([], 0),
    )
    verifie(
        "le rendu DIT que les muettes ne sont pas du code mort",
        lambda: "NORMAL d un helper prive" in rendu("preparer", *reponse("preparer", faux)),
        True,
    )

    # Le chemin de REFUS, et il s eprouve par `main` ENTIER plutot que par `charge`.
    #
    # La premiere ecriture de ces deux cas appelait `charge` directement. Ils passaient, et ils ne
    # prouvaient rien de CET outil : ils eprouvaient le lever de `index.py`, qui a ses propres cas.
    # Retirer le `except IndexAbsent` de `main` les laissait VERTS, l outil se contentant alors de
    # remonter une trace de pile - non nulle, donc la CI l attrapait, mais sans dire lequel de ses
    # controles avait rougi, ce que l ADR 4918 refuse. Le temoin etait decoratif au sens exact du
    # terme, et c est `--index` qui le rend vivant.
    absent = pathlib.Path("/index-qui-n-existe-pas/index-appels.json")
    verifie(
        "un index ABSENT fait REFUSER l outil entier, au lieu de rendre « aucun appelant »",
        lambda: main(["appelants.py", "--index", str(absent), "preparer"]),
        1,
    )
    verifie(
        "et le refus DIT comment produire l index",
        lambda: "ExtracteurIndex" in _message_du_refus(absent),
        True,
    )
    verifie(
        "sur un index FABRIQUE, le meme chemin rend la reponse et sort en 0",
        lambda: _sans_bruit(["appelants.py", "--index", str(_index_temoin(faux)), "preparer"]),
        0,
    )

    return echecs()


def _sans_bruit(argv: list[str]) -> int:
    """`main` sans sa sortie, pour que la reponse du cas positif ne se lise pas comme des cas.

    Un auto-test dont la trace porte les lignes de l outil lui-meme se compte mal : un releve qui
    cherche les cas joues y voit la reponse, et le nombre annonce cesse d etre le nombre de cas.
    """
    vraie = sys.stdout
    try:
        sys.stdout = io.StringIO()
        return main(argv)
    finally:
        sys.stdout = vraie


def _message_du_refus(chemin: pathlib.Path) -> str:
    try:
        charge(chemin)
    except IndexAbsent as refus:
        return str(refus)
    return ""


def _index_temoin(faux: dict[str, list[str]]) -> pathlib.Path:
    """Un index ecrit sur disque, pour que le cas positif emprunte le MEME chemin que le refus.

    Sans lui, le cas de refus serait le seul a passer par `main`, et rien ne dirait si l outil refuse
    parce que l index manque ou parce qu il refuse toujours. C est le controle de contraste.
    """
    ou = pathlib.Path(tempfile.gettempdir()) / "appelants-auto-test-index.json"
    ou.write_text(json.dumps(faux), encoding="utf-8")
    return ou


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()

    ou = None
    if "--index" in argv:
        ou = pathlib.Path(argv[argv.index("--index") + 1])

    passes = {"--markdown", "--index", "" if ou is None else str(ou)}
    cibles = [a for a in argv[1:] if not a.startswith("--") and a not in passes]
    if not cibles:
        print("Usage : appelants.py <nom|signature> [--index CHEMIN]", file=sys.stderr)
        print("        appelants.py --auto-test", file=sys.stderr)
        return 2

    try:
        index = charge(ou)
    except IndexAbsent as refus:
        print(refus, file=sys.stderr)
        return 1

    for cible in cibles:
        print(rendu(cible, *reponse(cible, index)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
