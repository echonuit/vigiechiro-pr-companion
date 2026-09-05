#!/usr/bin/env python3
"""Le fonds commun des gardes de CI qui interrogent la forge.

Deux cliquets - `verifie_cloture_consignee.py` (ADR 4659) et
`verifie_specification_consignee.py` (ADR 4922) - portaient **142 lignes appariees**, mesurees par
`difflib` sur leurs lignes non commentees. #4954 le constatait depuis le 2026-08-30 sans qu un
chantier en traite la cause ; la conversion en Python les a recopiees puis etoffees, de 59 lignes a
142.

## Ce que la mesure a corrige dans le diagnostic

Comparees FONCTION PAR FONCTION, une seule est identique : `racine`. Les quatre autres portent le
meme nom et des corps differents. Le partage n est donc pas a la granularite de la fonction, il est
DANS les fonctions : trente-trois blocs, dont quinze de plus de trois lignes, et les quatre plus gros
sont exactement ceux que #4954 nommait.

Ce module extrait ces blocs, et rien d autre. Les bords restent chez chaque garde, parce qu ils
different vraiment : les champs demandes a la forge, le filtre applique, le nom des variables
d environnement de leurs leurres.

## Pourquoi ici, et pas dans `scripts/_commun/`

`scripts/_commun/` sert `scripts/`, et y verser de la logique de forge en ferait une bibliotheque de
domaine - ce que #5216 avait MESURE comme non justifie : « le partage reel est plus etroit qu il n y
parait ». `.github/scripts` n avait aucun fonds ; il en a un, borne a ce que deux gardes partagent.

Le nom commence par un souligne : ni l inventaire des gardes ni le banc de mutation ne le prennent
pour un garde.
"""

from __future__ import annotations

import contextlib
import io
import json
import pathlib
import re
import subprocess
import sys
from collections.abc import Callable

CLIQUET_DANS_L_ENTETE = re.compile(r"^ratchet:[ \t]*([0-9]+)[ \t]*$", re.M)


def racine() -> pathlib.Path:
    """La racine du depot, ou le dossier courant si git ne repond pas."""
    rendu = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=False
    )
    return pathlib.Path(rendu.stdout.strip() or ".")


def cliquet_declare(fichier: pathlib.Path) -> int:
    """Le cliquet que l ADR declare dans son en-tete, ou un REFUS.

    Le chiffre vit dans l ADR et non dans le garde : c est la seule facon qu un lecteur de la
    decision voie le seuil qu elle tient. Un en-tete illisible n est pas un zero, c est un refus -
    conclure sur ce qu on n a pas lu est ce que l article A3 interdit.
    """
    texte = fichier.read_text(encoding="utf-8") if fichier.is_file() else ""
    trouve = CLIQUET_DANS_L_ENTETE.search(texte)
    if not trouve:
        print(
            f"REFUS : {fichier} ne déclare aucun cliquet lisible (attendu « ratchet: N »).",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return int(trouve.group(1))


def liste_issues(arguments: list[str], injectee: str | None = None) -> list[dict]:
    """`gh issue list` avec les arguments donnes, ou le contenu du leurre injecte.

    Le leurre n est pas un confort : sans lui, les cas de ces gardes exigeraient le reseau, et un
    garde dont les cas ne tournent pas hors ligne ne se relance jamais.

    `gh` absent est un REFUS et non une liste vide. Les deux gardes ecrivaient ce refus mot pour mot,
    a deux endroits : la prochaine reformulation n en aurait corrige qu un.
    """
    if injectee:
        return json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))
    rendu = subprocess.run(
        ["gh", "issue", "list", *arguments], capture_output=True, text=True, check=False
    )
    if rendu.returncode != 0 and not rendu.stdout.strip():
        print(
            "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return json.loads(rendu.stdout or "[]")


def vue_issue(numero: int, champs: str) -> dict:
    """`gh issue view` sur une issue, ou un REFUS nommant le numero qui manque.

    Nommer le numero est ce qui distingue « la forge est muette » de « cette issue-la a disparu ».
    """
    vue = subprocess.run(
        ["gh", "issue", "view", str(numero), "--json", champs],
        capture_output=True,
        text=True,
        check=False,
    )
    if vue.returncode != 0 or not vue.stdout.strip():
        print(f"REFUS : la forge n'a pas répondu pour #{numero}.", file=sys.stderr)
        raise SystemExit(2)
    return json.loads(vue.stdout)


def joue_pour_auto_test(juger: Callable[[], int]) -> tuple[object, str]:
    """Lance `juger` en capturant tout ce qu il ecrit, et rend (code, sortie).

    Le `SystemExit` est rattrape : un garde qui refuse sort en 2 par une exception, et un banc qui la
    laisserait passer s arreterait au premier cas de refus.

    SON NOM PORTE « auto » ET « test », ET CE N EST PAS UN ORNEMENT. Les bancs de mutation epargnent
    toute fonction dont le nom porte ces deux mots : neutraliser la machinerie d un auto-test le fait
    echouer trivialement, au lieu de prouver qu il a cesse de detecter. La lecon vient du lot 2 de
    #5257, ou une assertion extraite sous un nom neutre a rendu seize gardes non concluants sous
    mutation sans qu aucun banc ne s en apercoive.
    """
    tampon = io.StringIO()
    with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
        try:
            code = juger()
        except SystemExit as fin:
            code = fin.code
    return code, tampon.getvalue()


def cas_d_auto_test_de_forge() -> tuple:
    """Rend (verifie, echecs) : un cas d auto-test de cliquet de forge, et le lecteur de sa marque.

    Ces gardes ont TROIS verdicts et non deux - `ok`, `rouge`, `refus` - et un cas doit pouvoir
    exiger le MOTIF du refus : sortir en 2 pour la mauvaise raison ne prouve rien (ADR 4918). D ou
    un cas qui prend `motif` et le cherche dans ce que le garde a ecrit.

    Comme `cas_d_auto_test` du fonds de `scripts/`, son nom porte « auto » et « test » pour que les
    bancs de mutation l epargnent : neutraliser la machinerie d un auto-test le fait echouer
    trivialement au lieu de prouver qu il a cesse de detecter.
    """
    marque = [0]

    def verifie(attendu: str, libelle: str, motif: str, juger: Callable[[], int]) -> None:
        code, ecrit = joue_pour_auto_test(juger)
        obtenu = {1: "rouge", 2: "refus"}.get(code, "ok")
        if obtenu == attendu and (not motif or motif in ecrit):
            print(f"  ✔ {libelle}")
        elif obtenu != attendu:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            marque[0] = 1
        else:
            print(
                f"  ✘ {libelle} : {obtenu} pour la MAUVAISE raison, « {motif} » absent de la sortie"
            )
            marque[0] = 1

    return verifie, (lambda: marque[0])
