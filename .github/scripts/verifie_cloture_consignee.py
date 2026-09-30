#!/usr/bin/env python3
"""Un EPIC clos sans trace de cloture ne se distingue pas d un EPIC clos sans cloture (#4659).

Porte du bash en #5233.

Le depot ecrit a trois endroits que tout chantier se clot par quatorze passes - `CONTRIBUTING.md` §5,
`dev-docs/cycle-de-chantier.md`, la competence `clore-un-chantier`. Rien ne le verifiait, et **43 des
64 EPIC clos** n en portaient aucune trace au 2026-08-28.

## Ce qu il cherche, et pourquoi cette chaine-la

L en-tete `## Cloture de chantier` du modele que `cycle-de-chantier.md` demande de coller dans
l EPIC. C est une convention, pas une preuve : un EPIC peut la porter sans que les passes aient eu
lieu. Le garde ne mesure donc pas la QUALITE d une cloture, il mesure son absence de trace la ou la
documentation la demande - ce qui suffit a rendre la regle verifiable, et c est tout ce qu il pretend.

## Un cliquet, pas un butoir

65 clotures manquent deja. Refuser tout net rendrait le depot rouge sans qu aucune PR soit fautive,
et le garde se ferait desactiver la premiere semaine. Le cliquet ne peut que DESCENDRE : fermer un
EPIC sans trace le fait monter d un, et c est ce mouvement-la qui rougit. Rejouer quatorze passes sur
un chantier clos depuis un an n aurait pas de sens ; elles sont assumees une fois par ce chiffre.

## La POPULATION, corrigee le 2026-09-30 (#4967)

Ce garde demandait `--label epic`, donc la FORGE filtrait pour lui, alors que le depot designe aussi
un EPIC par le prefixe de son titre, `[epic]` ou `[chantier]`. Mesure sur les 1 737 issues closes :
96 par le label, 154 par l union, donc 58 jamais lues, dont 23 sans aucune trace. D ou le cliquet a
65 et non 42.

Le compte monte parce que la mesure commence a dire vrai, non parce qu une cloture a regresse. Le
controle qui l etablit : compter les manques parmi les 96 que ce garde lisait rend exactement 42, le
chiffre que l ADR portait. La divergence etait dans la population, pas dans la detection.

La definition vit desormais dans `scripts/_commun/epics.py`, partagee par les quatre dispositifs qui
lisaient cette notion, et dont trois en lisaient la fausse.

## La PREMISSE, verifiee a chaque passage (#4948)

Ce garde cherche une chaine dans de la prose d issue. Si le modele est renomme, plus aucune trace ne
la porte : le compte saute a tout le corpus et le garde accuse les cloturers alors que c est sa
propre chaine qui a bouge. On ne peut pas le voir dans le corpus - « aucun EPIC ne porte la marque »
est precisement l etat que ce garde EXISTE pour signaler. Le signal est donc dans le MODELE lui-meme,
qui est sous controle de version.

## Pourquoi il vit ici et non dans `scripts/adr/`

Il interroge la forge. Les cliquets de `scripts/adr/` sont hors ligne et tournent dans la batterie
locale : y mettre celui-ci ferait rougir quiconque travaille sans reseau.

Usage : python3 .github/scripts/verifie_cloture_consignee.py [--auto-test]
"""

from __future__ import annotations

import json
import os
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _forge import (
    candidats_clos,
    cas_d_auto_test_de_forge,
    cliquet_declare,
    racine,
    vue_issue,
)

# La chaine cherchee : l en-tete du modele de `dev-docs/cycle-de-chantier.md`.
MARQUE = "## Clôture de chantier"

# L ADR qui porte le cliquet. Il y vit et non ici : c est la seule facon qu un lecteur de la decision
# voie le chiffre qu elle tient (doctrine du fonds commun).
ADR = "dev-docs/decisions/4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md"
MODELE = "dev-docs/cycle-de-chantier.md"


def cliquet() -> int:
    """Le cliquet declare par l ADR, ou un refus si l en-tete ne le porte pas."""
    return cliquet_declare(pathlib.Path(os.environ.get("CLOTURE_ADR_FICHIER") or racine() / ADR))


def epics() -> list[dict]:
    """Les EPIC clos, corps et commentaires. Injectable pour l auto-test.

    Sans cette couture, les cas exigeraient le reseau, et un garde dont les cas ne tournent pas hors
    ligne ne se relance jamais.
    """
    injectee = os.environ.get("CLOTURE_EPICS_FICHIER")
    if injectee:
        # Le leurre porte le corpus COMPLET, corps et commentaires : il court-circuite les deux
        # appels a la forge, la liste ET les vues. Le faire passer par `liste_issues` seul rendrait
        # une liste que la boucle irait ensuite detailler EN LIGNE, ce qui remettrait le reseau au
        # milieu des cas. Constate en rebranchant ce garde.
        return json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))

    # `candidats_clos` remplace un `--label epic` qui faisait FILTRER la forge (#4967). Le compte
    # d appels monte de 96 a 154 sur la mesure du 2026-09-30, et c est le prix de lire le corpus que
    # ce garde pretend juger : 58 EPIC clos lui etaient invisibles, dont 23 sans aucune trace.
    corpus = []
    for entree in candidats_clos():
        corpus.append(vue_issue(entree["number"], "number,title,body,comments"))
    return corpus


def sans_trace(corpus: list[dict]) -> list[int]:
    """Les numeros des EPIC clos SANS trace."""
    manquants = []
    for epic in corpus:
        textes = [epic.get("body") or ""] + [
            (c.get("body") or "") for c in (epic.get("comments") or [])
        ]
        if not any(MARQUE in t for t in textes):
            manquants.append(epic["number"])
    return manquants


def juger() -> int:
    """La premisse, puis le cliquet, et le code de sortie qui va avec."""
    modele = pathlib.Path(os.environ.get("CLOTURE_MODELE_FICHIER") or racine() / MODELE)
    if modele.is_file() and MARQUE not in modele.read_text(encoding="utf-8"):
        print(f"REFUS : « {MARQUE} » n apparait plus dans {MODELE}.", file=sys.stderr)
        print(
            "La marque que ce garde cherche a ete renommee dans le modele. Alignez-la ici, sinon il",
            file=sys.stderr,
        )
        print(
            "comptera toutes les clotures comme absentes et accusera les cloturers.",
            file=sys.stderr,
        )
        return 2

    seuil = cliquet()
    liste = sans_trace(epics())
    compte = len(liste)

    print(f"CLIQUET 4659 | sans trace={compte} | cliquet={seuil}")
    if compte > seuil:
        print()
        print("Un EPIC a été clos sans que sa clôture soit consignée.")
        print(
            "Collez le modèle de « dev-docs/cycle-de-chantier.md » en commentaire sur l'EPIC, cases"
        )
        print("cochées, ou baissez le cliquet dans l'ADR si vous venez d'en rattraper une.")
        print()
        print(f"EPIC sans trace : {' '.join(str(n) for n in liste)}")
        return 1
    if compte < seuil:
        print(f"Le dépôt en porte MOINS que son cliquet : descendez-le à {compte} dans l'ADR.")
    return 0


TRACE = "## Clôture de chantier\n- [x] 0."

# (attendu, libelle, corpus, adr, modele, motif). Le motif n est pas decoratif : sans lui, deux refus
# differents sortent tous deux en 2 et un cas peut passer pour la mauvaise raison (ADR 4918).
CAS = (
    # Le cas qui compte : le garde doit VOIR une cloture qui manque. Sans lui, tous ses verts ne
    # valent rien.
    (
        "rouge",
        "un EPIC de plus sans trace fait monter le compte, et il refuse",
        [
            {"number": 1, "body": "a", "comments": []},
            {"number": 2, "body": "b", "comments": []},
            {"number": 3, "body": "c", "comments": []},
        ],
        "adr",
        "sain",
        "",
    ),
    (
        "ok",
        "le compte égal au cliquet passe",
        [{"number": 1, "body": "a", "comments": []}, {"number": 2, "body": "b", "comments": []}],
        "adr",
        "sain",
        "",
    ),
    (
        "ok",
        "le compte SOUS le cliquet passe : un cliquet descend",
        [{"number": 1, "body": "a", "comments": []}],
        "adr",
        "sain",
        "",
    ),
    (
        "ok",
        "la trace dans le CORPS compte",
        [{"number": 1, "body": TRACE, "comments": []}, {"number": 2, "body": "b", "comments": []}],
        "adr",
        "sain",
        "",
    ),
    (
        "ok",
        "la trace dans un COMMENTAIRE compte, c'est là qu'elle se colle",
        [
            {"number": 1, "body": "b", "comments": [{"body": TRACE}]},
            {"number": 2, "body": "b", "comments": []},
        ],
        "adr",
        "sain",
        "",
    ),
    ("ok", "AUCUN EPIC clos : rien a juger", [], "adr", "sain", ""),
    # La premisse : la marque vit-elle encore dans le modele ? Sans ce cas, le refus serait du code
    # qu aucune epreuve ne traverse (#4948).
    (
        "refus",
        "la marque absente du MODELE fait REFUSER : elle a ete renommee",
        [{"number": 1, "body": MARQUE, "comments": []}],
        "adr",
        "renomme",
        "n apparait plus dans",
    ),
    (
        "refus",
        "une ADR sans cliquet lisible fait REFUSER, pas conclure",
        [{"number": 1, "body": "a", "comments": []}],
        "muette",
        "sain",
        "ne déclare aucun cliquet lisible",
    ),
    (
        "refus",
        "une ADR introuvable fait REFUSER aussi",
        [{"number": 1, "body": "a", "comments": []}],
        "absente",
        "sain",
        "ne déclare aucun cliquet lisible",
    ),
)


def _auto_test() -> int:
    """Neuf cas hors ligne, dont quatre qui DOIVENT refuser."""
    import tempfile

    verifie, echecs = cas_d_auto_test_de_forge()
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-cloture-") as tmp:
        bac = pathlib.Path(tmp)
        (bac / "adr.md").write_text("ratchet: 2\n", encoding="utf-8")
        (bac / "adr-muette.md").write_text("title: une ADR sans cliquet\n", encoding="utf-8")
        (bac / "modele-sain.md").write_text(MARQUE + "\n", encoding="utf-8")
        (bac / "modele-renomme.md").write_text(
            "un modele qui a perdu sa marque\n", encoding="utf-8"
        )
        adrs = {
            "adr": bac / "adr.md",
            "muette": bac / "adr-muette.md",
            "absente": bac / "nulle-part.md",
        }
        modeles = {
            "sain": bac / "modele-sain.md",
            "renomme": bac / "modele-renomme.md",
            "": bac / "modele-sain.md",
        }

        for attendu, libelle, corpus, adr, modele, motif in CAS:
            cas += 1
            if attendu != "ok":
                rouges += 1
            (bac / "epics.json").write_text(json.dumps(corpus), encoding="utf-8")
            os.environ["CLOTURE_EPICS_FICHIER"] = str(bac / "epics.json")
            os.environ["CLOTURE_ADR_FICHIER"] = str(adrs[adr])
            os.environ["CLOTURE_MODELE_FICHIER"] = str(modeles[modele])
            verifie(attendu, libelle, motif, juger)

    for cle in ("CLOTURE_EPICS_FICHIER", "CLOTURE_ADR_FICHIER", "CLOTURE_MODELE_FICHIER"):
        os.environ.pop(cle, None)

    # #4967, et ces deux familles ne se remplacent pas.
    #
    # La DEFINITION se joue ici parce que ce garde est de l autre cote de la barriere : les memes cas
    # tournent dans l auto-test de `scripts/adr/loupe-4712-lots-multi-pr.py`, et ce qu il faut
    # prouver est que la MEME definition traverse les deux paquets. Un seul joueur ne le montrerait
    # pas, et c est precisement la divergence entre les deux paquets qui a produit ce defaut.
    #
    # La COLLECTE se joue ici parce que le leurre ci-dessus la court-circuite : il injecte le corpus
    # deja constitue, donc l elargissement de la requete et son refus au plafond ne sont traverses
    # par aucun des neuf cas precedents.
    from _commun.epics import verifie_grammaire
    from _forge import PLAFOND_CLOSES, refus_au_plafond

    for libelle, tenu in verifie_grammaire():
        cas += 1
        # `tenu` passe par un defaut d argument : sans lui, la fermeture lirait la DERNIERE valeur
        # de la boucle et les cas se jugeraient tous sur le meme booleen.
        verifie("ok", f"#4967 : {libelle}", "", lambda tenu=tenu: 0 if tenu else 1)

    # Le CHEMIN DE REFUS, a part : il sort en 2 et se juge donc comme les trois refus ci-dessus.
    # Une fonction pure ne peut pas porter un refus, et un refus non traverse se casse sans bruit.
    for combien, attendu in ((PLAFOND_CLOSES, "refus"), (PLAFOND_CLOSES - 1, "ok")):
        cas += 1
        if attendu != "ok":
            rouges += 1
        verifie(
            attendu,
            f"#4967 : une collecte de {combien} contre le plafond {PLAFOND_CLOSES}",
            "",
            lambda combien=combien: refus_au_plafond(combien) or 0,
        )

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT refuser.")
    if echecs() == 0:
        print("Auto-test concluant : le garde voit une clôture qui manque.")
    else:
        print("Auto-test EN ÉCHEC.")
    return echecs()


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
