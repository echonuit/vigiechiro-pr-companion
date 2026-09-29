#!/usr/bin/env python3
"""Qui tient ce contrat, quand la reponse ne se lit dans aucun fichier.

`arbre.py` voit qu une classe declare `implements Contrat`. Il ne voit pas **qui d autre** le declare,
parce qu il lit un fichier a la fois. La question « quel type implemente ce contrat » est la deuxieme
des trois que #5464 ecrivait dans sa frontiere, et la premiere qu aucun lecteur ne savait poser.

Mesure du 2026-09-29, sur les 123 contrats du depot :

    grep -rl 'implements DaoGenerique' --include=*.java src/   ->  30 fichiers a ouvrir
    cet outil                                                 ->  30 porteurs, qualifies, en une ligne

Le gain n est pas le compte, c est la RESOLUTION. Un `grep` sur `implements Contrat` rate la classe qui
l obtient par sa mere, et rate les 22 interfaces imbriquees du depot - `EcritureAtomique.Attente`,
`TransportVigieChiro.CorpsAEnvoyer` - dont la declaration ne porte pas le nom du fichier.

## Ce qu il ne dit PAS

**Un contrat sans porteur n est pas un contrat mort.** 27 des 123 en sont la : une interface posee pour
un point d extension, une classe abstraite dont la seule fille est anonyme, un contrat qu un seul test
implemente en ligne. Cet outil REPOND, il ne refuse pas, et c est l ADR 5532 appliquee a cet index-ci.

## Pourquoi un second fichier plutot qu un mode de `appelants.py`

Parce que les deux outils repondent a deux questions et lisent deux index : un mode unique ferait
dependre chaque reponse de la production des DEUX, ce que #5564 a justement ecarte en posant deux
fichiers. Le squelette se ressemble, et c est assume tant qu ils sont deux ; au troisieme, il se
factorise, et ce sera la lecon de #5216 appliquee ici.

Usage :
    python3 scripts/qualite/implemente.py <contrat> [--index CHEMIN]
    python3 scripts/qualite/implemente.py --auto-test
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import cas_d_auto_test
from _commun import implementations as lecteur
from _commun.implementations import charge
from _commun.outil_d_index import PLAFOND, index_temoin, joue, message_du_refus, sans_bruit


def contrats(cible: str, index: dict[str, list[str]]) -> list[str]:
    """Les cles que `cible` designe : un nom QUALIFIE tel quel, ou un nom SIMPLE resolu.

    Deux formes, parce que l utilisateur a rarement le nom qualifie sous la main. Un nom simple peut
    en designer plusieurs, et alors les deux sont rendus plutot qu un choisi : `Attente` existe dans
    `EcritureAtomique` et pourrait exister ailleurs, et trancher a sa place serait rendre une reponse
    fausse avec l air d etre sure.
    """
    if cible in index:
        return [cible]
    return sorted(k for k in index if k.rsplit(".", 1)[-1].rsplit("$", 1)[-1] == cible)


def rendu(cible: str, trouves: list[str], index: dict[str, list[str]]) -> str:
    lignes = [f"IMPLEMENTE {cible}"]
    if not trouves:
        lignes.append("  aucun contrat de ce nom dans l index")
        lignes.append(f"\nIMPLEMENTE {cible} | contrats=0 | porteurs=0")
        return "\n".join(lignes)

    total = 0
    for cle in trouves:
        porteurs = index[cle]
        total += len(porteurs)
        lignes.append(f"  {cle}")
        if not porteurs:
            lignes.append(
                "      aucun porteur, ce qui n est PAS un contrat mort : un point d extension,"
                " une fille anonyme ou un test qui l implemente en ligne comptent"
            )
        for p in porteurs[:PLAFOND]:
            lignes.append(f"      <- {p}")
        if len(porteurs) > PLAFOND:
            # Dire ce qu on tronque : une liste coupee en silence se lit comme une liste complete.
            lignes.append(f"      <- ... {len(porteurs) - PLAFOND} autre(s)")
    lignes.append(f"\nIMPLEMENTE {cible} | contrats={len(trouves)} | porteurs={total}")
    return "\n".join(lignes)


def auto_test() -> int:
    """Les cas de cet outil, sur un index FABRIQUE plutot que sur celui du depot.

    Le cas du REFUS passe par `main` ENTIER et non par `charge`. La premiere ecriture du temoin de
    `appelants.py` appelait la fonction de chargement, et elle restait VERTE quand on retirait le
    `except` du point d entree : elle eprouvait la bibliotheque, pas l outil. Un cas positif emprunte
    le meme chemin, sans quoi rien ne dirait si l outil refuse a cause de l index ou refuse toujours.
    """
    verifie, echecs = cas_d_auto_test()

    faux = {
        "fr.p.Contrat": ["fr.p.Une", "fr.p.Deux"],
        "fr.q.Contrat": ["fr.q.Seule"],
        "fr.p.Vide": [],
        "fr.p.Englobe$Dedans": ["fr.p.Englobe$Tient"],
    }

    verifie(
        "un nom QUALIFIE passe tel quel", lambda: contrats("fr.p.Contrat", faux), ["fr.p.Contrat"]
    )
    verifie(
        "un nom SIMPLE homonyme rend TOUS ses porteurs de nom, jamais un choisi",
        lambda: contrats("Contrat", faux),
        ["fr.p.Contrat", "fr.q.Contrat"],
    )
    verifie(
        "un contrat IMBRIQUE se resout par son nom simple, apres le dollar",
        lambda: contrats("Dedans", faux),
        ["fr.p.Englobe$Dedans"],
    )
    verifie("un nom inconnu rend une liste vide", lambda: contrats("Absent", faux), [])
    verifie(
        "le rendu DIT qu un contrat sans porteur n est pas mort",
        lambda: "n est PAS un contrat mort" in rendu("fr.p.Vide", ["fr.p.Vide"], faux),
        True,
    )
    verifie(
        "le rendu compte les porteurs de TOUS les contrats homonymes",
        lambda: "porteurs=3" in rendu("Contrat", contrats("Contrat", faux), faux),
        True,
    )

    absent = pathlib.Path("/index-qui-n-existe-pas/index-implementations.json")
    verifie(
        "un index ABSENT fait REFUSER l outil entier",
        lambda: main(["implemente.py", "--index", str(absent), "Contrat"]),
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
            main, ["implemente.py", "--index", str(index_temoin(faux, "implemente")), "Contrat"]
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
        verifie(f"lecteur des implementations : {libelle}", lambda tenu=tenu: tenu, True)

    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    return joue(
        argv,
        usage=(
            "Usage : implemente.py <contrat> [--index CHEMIN]",
            "        implemente.py --auto-test",
        ),
        charge=charge,
        repond=lambda cible, index: rendu(cible, contrats(cible, index), index),
    )


if __name__ == "__main__":
    sys.exit(main(sys.argv))
