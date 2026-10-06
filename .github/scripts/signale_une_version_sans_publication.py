#!/usr/bin/env python3
"""Un tag de version sans publication se SIGNALE, au lieu de rester invisible (#5987, ADR 5987).

## Ce qu il empeche

Quand une publication s arrete en chemin, `main` porte un tag et une entree de journal d apparence
normale, et il n y a pas de publication. Qui regarde la branche voit une version livree. Le job est
rouge, mais un job rouge se lit « le train a echoue », pas « une version existe a moitie et voici ce
qui lui manque » - et il faut interroger la forge pour le decouvrir.

Mesure du 2026-10-06 : ce depot portait **17** tags de version sans publication, sur 512 tags et 495
publications. Aucun garde ne le disait.

## Ce qu il signale, et ce qu il laisse passer

Il ne signale pas « un tag sans publication », qui serait vrai 17 fois et donc inutile. Il signale un
tag dont la VERSION depasse la plus haute version PUBLIEE - ce qui est la definition d une version a
moitie faite, par opposition a une sequelle d une periode close.

Le calcul n est pas ecrit ici : il vient de `resout_le_tag_de_version.py`, parce que la RESOLUTION de
l atelier pose exactement la meme question. Elle y voit le tag qu une reprise doit reprendre ; ce
garde y voit une version a signaler. Une borne recopiee aurait derive, et celle-ci a demande trois
essais dont deux faux.

## Il se TAIT de lui-meme, et c est voulu

Apres un train reussi, la plus haute version publiee EST la derniere, donc aucun tag ne la depasse et
ce garde est vert. Il ne reste donc pas rouge indefiniment : il s eteint des que la version qu il
signalait est publiee, par la chaine et sans geste particulier. C est ce qui le rend tenable en fin
d atelier plutot qu en liste d exceptions a maintenir.

## Son silence n est pas un verdict

Il interroge la forge. Une forge muette ne vaut pas « aucune publication » : ici le vide signifierait
que TOUTE version est au-dessus de la plus haute publiee, donc une alerte sur tout. Le refus est donc
distinct de l alerte, et son code de sortie aussi (2 contre 1), pour qu un atelier puisse les
distinguer sans lire le message.

Usage : python3 .github/scripts/signale_une_version_sans_publication.py [--auto-test]
        0 = rien a signaler · 1 = une version est a moitie faite · 2 = je n ai pas pu lire
"""

from __future__ import annotations

import os
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from _versions import (
    Refus,
    candidats,
    tags_de_version,
    versions_publiees,
)

RIEN_A_SIGNALER = 0
VERSION_A_MOITIE_FAITE = 1
LECTURE_IMPOSSIBLE = 2


def message(au_dessus: list[tuple[tuple[int, int, int], str]]) -> str:
    """Ce que le signalement DIT, et c est la moitie de son service.

    Il nomme les tags, dit ce qui leur manque, et renvoie a la conduite a tenir plutot que de laisser
    chercher. La page qu il cite porte les trois etats d une publication interrompue et leur reprise.
    """
    noms = ", ".join(t for _, t in au_dessus)
    pluriel = "s" if len(au_dessus) > 1 else ""
    return (
        f"{len(au_dessus)} tag{pluriel} de version depasse{'nt' if pluriel else ''} la plus haute"
        f" version publiee : {noms}. Une version existe donc a moitie - le tag est pose, la"
        " publication non - et personne ne le verra en regardant la branche. La conduite a tenir est"
        " dans dev-docs/ci-cd-release.md, section « Une publication peut s arreter en chemin »."
    )


def verdict(
    au_dessus: list[tuple[tuple[int, int, int], str]],
) -> tuple[int, str]:
    """La DECISION du garde : un code de sortie et la phrase qui l accompagne.

    Elle est a part pour etre eprouvable. `juger` ne fait plus que lire le monde et l imprimer : une
    decision melangee a ses entrees ne se mute pas, et un garde dont la decision n est pas eprouvee
    peut s inverser sans qu aucun cas ne tombe.
    """
    if au_dessus:
        return VERSION_A_MOITIE_FAITE, message(au_dessus)
    return (
        RIEN_A_SIGNALER,
        "Aucune version a moitie faite : chaque tag de version a sa publication.",
    )


def juger() -> int:
    """Lit le monde, prend le verdict, l imprime. Trois sorties distinctes."""
    try:
        au_dessus = candidats(tags_de_version(), versions_publiees())
    except Refus as refus:
        print(f"REFUS : {refus}", file=sys.stderr)
        return LECTURE_IMPOSSIBLE
    code, dit = verdict(au_dessus)
    if code == RIEN_A_SIGNALER:
        print(dit)
    else:
        print(f"⚠️ {dit}", file=sys.stderr)
    return code


CONTRAT = {
    "garde": ".github/scripts/signale_une_version_sans_publication.py",
    "geste": "une version dont le tag existe sans publication, qui ne se voit pas depuis la branche",
    "population": "les tags vX.Y.Z du depot et les publications que la forge declare non brouillonnes",
    "dispositif": "alerte",
    "seuil": "(sans objet)",
    "temoin": ".github/scripts/signale_une_version_sans_publication.py --auto-test",
    "decision": "ADR 5987",
    "chemins": """
.github/workflows/release.yml
.github/scripts/signale_une_version_sans_publication.py
.github/scripts/resout_le_tag_de_version.py
""",
}


def _juge_sur_un_chemin_vide() -> bool:
    """Vrai si `juger` rend LECTURE_IMPOSSIBLE quand la forge est inatteignable.

    Le cas remplace une comparaison de trois constantes, qui ne prouvait rien : elle verifiait que
    trois nombres different, pas que le garde les emploie. Joue sur un `PATH` fabrique VIDE, donc sans
    `gh`, ce qui fait lever le fonds - et c est le chemin que l en-tete PROMET et qu aucun cas ne
    tenait. Mesure : muter la sonde du fonds ne faisait tomber aucun cas de ce garde.

    Il faut aussi un `git` : le `PATH` vide le retire, donc `tags_de_version` refuse d abord. Les deux
    refus conduisent au meme code, et c est bien ce code que ce cas exige.
    """
    avant = os.environ.get("PATH", "")
    os.environ["PATH"] = str(pathlib.Path(tempfile.mkdtemp(prefix="sans-outils-")))
    try:
        return juger() == LECTURE_IMPOSSIBLE
    finally:
        os.environ["PATH"] = avant


def _signale_en_nommant(tags: list[str], publiees: list[str], attendus: list[str]) -> bool:
    """Vrai si ces entrees declenchent un signalement dont le message NOMME chaque tag attendu.

    Exiger le message et pas seulement le declenchement : une alerte qui ne nomme pas la version a
    moitie faite laisse chercher parmi cinq cents tags, et c est la moitie du service qu elle rend.
    """
    au_dessus = candidats(tags, publiees)
    if not au_dessus:
        return False
    dit = message(au_dessus)
    return all(attendu in dit for attendu in attendus)


def _auto_test() -> int:
    """La lecture INVERSE des memes entrees que la resolution, et le refus qui n est pas une alerte.

    La resolution est deja eprouvee par ses sept cas ; ce qui reste a eprouver ici est ce que ce
    garde FAIT du meme calcul, et que son refus ne se confond pas avec son alerte.
    """
    sequelles = [f"v2.{n}.0" for n in range(26, 36)] + ["v2.186.0"]
    cas = [
        (
            "une version a moitie faite : elle est SIGNALEE, et le message la nomme",
            lambda: _signale_en_nommant(["v2.196.0", "v2.197.0"], ["v2.196.0"], ["v2.197.0"]),
        ),
        (
            "apres un train reussi : RIEN a signaler, le garde s eteint tout seul",
            lambda: candidats(["v2.196.0", "v2.197.0"], ["v2.196.0", "v2.197.0"]) == [],
        ),
        (
            "les dix-sept sequelles ne declenchent RIEN : elles sont sous la plus haute publiee",
            lambda: candidats([*sequelles, "v2.197.0"], ["v2.197.0"]) == [],
        ),
        (
            "deux versions a moitie faites : les DEUX sont nommees, aucune n est choisie",
            lambda: _signale_en_nommant(
                ["v2.196.0", "v2.197.0", "v2.198.0"], ["v2.196.0"], ["v2.197.0", "v2.198.0"]
            ),
        ),
        (
            "le message renvoie a la conduite a tenir, et pas seulement au constat",
            lambda: "ci-cd-release.md" in message(candidats(["v2.197.0"], [])),
        ),
        (
            "forge muette : le code « je n ai pas pu lire », et non celui de l alerte",
            _juge_sur_un_chemin_vide,
        ),
    ]

    cas += [
        (
            "la DECISION : des candidats -> le code « version a moitie faite »",
            lambda: (
                verdict(candidats(["v2.196.0", "v2.197.0"], ["v2.196.0"]))[0]
                == VERSION_A_MOITIE_FAITE
            ),
        ),
        (
            "la DECISION : aucun candidat -> le code « rien a signaler »",
            lambda: verdict([])[0] == RIEN_A_SIGNALER,
        ),
    ]

    echecs = []
    for libelle, joue in cas:
        try:
            tenu = bool(joue())
        except Exception as souci:  # noqa: BLE001 - un cas qui leve est un cas qui echoue
            tenu, libelle = False, f"{libelle} (a leve : {souci})"
        print(f"  {'✔' if tenu else '✘'} {libelle}")
        if not tenu:
            echecs.append(libelle)

    if echecs:
        print(f"\n❌ {len(echecs)} cas sur {len(cas)} ne tiennent pas.")
        return 1
    print(f"\n✅ {len(cas)} cas tenus : le signalement fait ce que son en-tete annonce.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
