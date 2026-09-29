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

Le depot porte **1 422 noms declares dans plusieurs classes** - 1 235 avant que #5564 fasse entrer
les types imbriques - sur 9 835 noms distincts. Les pires
sont massifs : `preparer` dans 188 classes, `start` dans 150, `nettoyer` dans 77. Devant eux, un
`grep` par nom ne rend pas une reponse, il rend une liste de fichiers a ouvrir.

## Ce qu il ne dit PAS, et le contresens que ce fichier existe pour ne pas commettre

« Aucun appelant hors de son fichier » n est PAS du code mort. C est l etat normal d un helper privé,
et `index.py` l ecrit deja dans sa propre docstring. Mesure : 11 517 methodes sur 16 680 sont dans ce
cas, soit **69 % du corpus**, dont 5 014 cas de test que JUnit appelle par reflexion, et le reste
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

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import cas_d_auto_test
from _commun import index as lecteur
from _commun.index import charge
from _commun.outil_d_index import PLAFOND, index_temoin, joue, message_du_refus, sans_bruit


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
        lambda: "ExtracteurIndex" in message_du_refus(charge, absent),
        True,
    )
    verifie(
        "sur un index FABRIQUE, le meme chemin rend la reponse et sort en 0",
        lambda: sans_bruit(
            main, ["appelants.py", "--index", str(index_temoin(faux, "appelants")), "preparer"]
        ),
        0,
    )

    # ⟨les cas du LECTEUR, joues ici parce que rien d autre ne les jouait⟩ Les trois modules de
    # `_commun` portent un `verifie_grammaire()` depuis #5473 et #5564, et la mesure du 2026-09-29
    # dit qu AUCUN harnais ne les appelait : seul celui d `arbre.py` est joue, par
    # `scripts/adr/4472-commentaire-en-corps.py`. Trois gages inertes, ce que l ADR 5546 nomme un
    # defaut. L outil est le bon endroit : il est le seul lecteur de son module, et son `--auto-test`
    # est joue par la porte comme par `lint.yml`.
    for libelle, tenu in lecteur.verifie_grammaire():
        verifie(f"lecteur des appels : {libelle}", lambda tenu=tenu: tenu, True)

    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    return joue(
        argv,
        usage=(
            "Usage : appelants.py <nom|signature> [--index CHEMIN]",
            "        appelants.py --auto-test",
        ),
        charge=charge,
        repond=lambda cible, index: rendu(cible, *reponse(cible, index)),
    )


if __name__ == "__main__":
    sys.exit(main(sys.argv))
