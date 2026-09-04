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

43 clotures manquent deja. Refuser tout net rendrait le depot rouge sans qu aucune PR soit fautive,
et le garde se ferait desactiver la premiere semaine. Le cliquet ne peut que DESCENDRE : fermer un
EPIC sans trace le fait monter a 44, et c est ce mouvement-la qui rougit. Rejouer quatorze passes sur
un chantier clos depuis un an n aurait pas de sens ; les 43 sont assumees une fois par ce chiffre.

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
import subprocess
import sys

# La chaine cherchee : l en-tete du modele de `dev-docs/cycle-de-chantier.md`.
MARQUE = "## Clôture de chantier"

# L ADR qui porte le cliquet. Il y vit et non ici : c est la seule facon qu un lecteur de la decision
# voie le chiffre qu elle tient (doctrine du fonds commun).
ADR = "dev-docs/decisions/4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md"
MODELE = "dev-docs/cycle-de-chantier.md"


def racine() -> pathlib.Path:
    rendu = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=False
    )
    return pathlib.Path(rendu.stdout.strip() or ".")


def cliquet() -> int:
    """Le cliquet declare par l ADR, ou un refus si l en-tete ne le porte pas."""
    import re

    fichier = pathlib.Path(os.environ.get("CLOTURE_ADR_FICHIER") or racine() / ADR)
    texte = fichier.read_text(encoding="utf-8") if fichier.is_file() else ""
    trouve = re.search(r"^ratchet:[ \t]*([0-9]+)[ \t]*$", texte, re.M)
    if not trouve:
        print(
            f"REFUS : {fichier} ne déclare aucun cliquet lisible (attendu « ratchet: N »).",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return int(trouve.group(1))


def epics() -> list[dict]:
    """Les EPIC clos, corps et commentaires. Injectable pour l auto-test.

    Sans cette couture, les cas exigeraient le reseau, et un garde dont les cas ne tournent pas hors
    ligne ne se relance jamais.
    """
    injectee = os.environ.get("CLOTURE_EPICS_FICHIER")
    if injectee:
        return json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))

    rendu = subprocess.run(
        [
            "gh",
            "issue",
            "list",
            "--label",
            "epic",
            "--state",
            "closed",
            "--limit",
            "300",
            "--json",
            "number,title",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0 and not rendu.stdout.strip():
        print(
            "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    corpus = []
    for entree in json.loads(rendu.stdout or "[]"):
        numero = entree["number"]
        vue = subprocess.run(
            ["gh", "issue", "view", str(numero), "--json", "number,title,body,comments"],
            capture_output=True,
            text=True,
            check=False,
        )
        if vue.returncode != 0 or not vue.stdout.strip():
            print(f"REFUS : la forge n'a pas répondu pour #{numero}.", file=sys.stderr)
            raise SystemExit(2)
        corpus.append(json.loads(vue.stdout))
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
    import contextlib
    import io
    import tempfile

    echecs = cas = rouges = 0
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
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                try:
                    code = juger()
                except SystemExit as fin:
                    code = fin.code
            ecrit = tampon.getvalue()
            obtenu = {1: "rouge", 2: "refus"}.get(code, "ok")
            if obtenu == attendu and (not motif or motif in ecrit):
                print(f"  ✔ {libelle}")
            elif obtenu != attendu:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs = 1
            else:
                print(
                    f"  ✘ {libelle} : {obtenu} pour la MAUVAISE raison, « {motif} » absent de la sortie"
                )
                echecs = 1

    for cle in ("CLOTURE_EPICS_FICHIER", "CLOTURE_ADR_FICHIER", "CLOTURE_MODELE_FICHIER"):
        os.environ.pop(cle, None)

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT refuser.")
    if echecs == 0:
        print("Auto-test concluant : le garde voit une clôture qui manque.")
    else:
        print("Auto-test EN ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
