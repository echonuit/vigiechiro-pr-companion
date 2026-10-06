#!/usr/bin/env python3
"""Le fonds commun des deux dispositifs qui lisent les versions de ce depot (#5987, ADR 5987).

## Pourquoi un fonds, et pas une copie

Deux dispositifs posent la MEME question - « quels tags de version depassent la plus haute version
publiee ? » - et en tirent deux lectures opposees :

- `resout_le_tag_de_version.py` y voit le tag qu une reprise doit reprendre ;
- `signale_une_version_sans_publication.py` y voit une version a moitie faite, donc une alerte.

Meme entree, meme borne, deux conclusions. La borne a demande trois essais dont deux faux pour etre
trouvee, et une borne recopiee derive : elle vit donc ici une seule fois.

## Pourquoi ici, avec un souligne

Parce que `verifie-dependances-declarees.py` refuse qu un garde importe un module que rien ne
declare, et il a raison : un import local non souligne ressemble a une distribution absente du
`pyproject.toml`. Le souligne dit « fonds commun », et c est la forme que `_forge.py` a posee - un
fonds borne a ce que deux scripts partagent, ni une bibliotheque de domaine ni une copie.

Le nom souligne a un second effet voulu : ni l inventaire des gardes ni le banc de mutation ne
prennent ce fichier pour un garde, ce qu il n est pas - il ne juge rien, il calcule.

## La borne, et pourquoi la version plutot que le graphe

Mesure du 2026-10-06 : les 17 tags de version sans publication de ce depot ne sont PAS des ancetres
de sa branche principale - zero sur 17 - une reecriture d historique les ayant orphelines. Une regle
d ancestralite les designerait donc tous. La comparaison de versions, elle, ne lit que des numeros et
survit a une reecriture.
"""

from __future__ import annotations

import re
import shutil
import subprocess

MOTIF_DE_VERSION = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")


class Refus(Exception):
    """Ce qu un dispositif ne peut pas trancher seul, et qui doit arreter son appelant."""


def version(tag: str) -> tuple[int, int, int] | None:
    """Le triplet d un tag de version, ou `None` si ce tag n en est pas un.

    Les tags qui ne suivent pas `vX.Y.Z` sont ecartes sans bruit : ce depot en porte d autres, et ils
    ne concernent pas la publication.
    """
    trouve = MOTIF_DE_VERSION.match(tag)
    return (int(trouve[1]), int(trouve[2]), int(trouve[3])) if trouve else None


def candidats(
    tags_de_version: list[str], versions_publiees: list[str]
) -> list[tuple[tuple[int, int, int], str]]:
    """Les tags de version qui DEPASSENT la plus haute version publiee, tries par version."""
    publiees = sorted(v for t in versions_publiees if (v := version(t)))
    plus_haute = publiees[-1] if publiees else (0, 0, 0)
    return sorted((v, t) for t in tags_de_version if (v := version(t)) and v > plus_haute)


def tag_de_la_tete() -> str:
    """Le tag porte par `HEAD`, chaine vide s il n y en a pas. Un echec de git EST une absence ici."""
    rendu = subprocess.run(
        ["git", "describe", "--tags", "--exact-match", "HEAD"],
        capture_output=True,
        text=True,
        check=False,
    )
    return rendu.stdout.strip() if rendu.returncode == 0 else ""


def tags_de_version() -> list[str]:
    """Tous les tags `vX.Y.Z` du depot, par `git tag`.

    La sonde sur `git` n est pas une precaution de style : sans elle, un `PATH` ampute fait LEVER un
    `FileNotFoundError` au lieu de refuser, et le message parle d une ligne de Python plutot que d une
    panne d installation. Trouve par le cas « forge muette » du garde de l ADR 5987, ecrit pour
    eprouver un chemin que l en-tete PROMETTAIT - et qui n etait pas atteignable. Le garde de l ADR
    5692 ne l avait pas vu : il surveille les outils de forge, et `git` n est pas dans sa population.
    """
    if shutil.which("git") is None:
        raise Refus(
            "« git » est absent : impossible de lister les tags de version. C est une panne"
            " d installation, et non un verdict sur les versions du depot."
        )
    rendu = subprocess.run(["git", "tag", "-l", "v*"], capture_output=True, text=True, check=False)
    if rendu.returncode != 0:
        raise Refus("`git tag` a refuse : impossible de lister les tags de version.")
    return [ligne.strip() for ligne in rendu.stdout.splitlines() if ligne.strip()]


def versions_publiees() -> list[str] | None:
    """Les tags que la forge declare publies, ou `None` si elle n a pas repondu.

    Les brouillons sont ECARTES : un brouillon est precisement l etat d une version a moitie faite, et
    le compter comme publie ferait taire les deux dispositifs dans le cas ou ils servent.

    Deux absences, deux conduites, et c est le garde de l ADR 5692 qui l a exigee : un `gh`
    introuvable fait REFUSER en nommant la panne d installation, parce que `subprocess.run` leverait
    un `FileNotFoundError` avant qu il y ait un code de sortie. Un `gh` present qui rend non zero,
    lui, rend `None` : la forge n a pas repondu, ce qui est une panne de lecture et non
    d installation.
    """
    if shutil.which("gh") is None:
        raise Refus(
            "« gh » est absent : ce dispositif ne peut pas savoir quelles versions sont publiees,"
            " donc il ne peut pas distinguer une version a moitie faite d une sequelle. C est une"
            " panne d installation, et non un verdict sur la publication."
        )
    rendu = subprocess.run(
        ["gh", "release", "list", "--limit", "1000", "--json", "tagName,isDraft"],
        capture_output=True,
        text=True,
        check=False,
    )
    if rendu.returncode != 0:
        return None
    import json

    try:
        lu = json.loads(rendu.stdout or "[]")
    except json.JSONDecodeError:
        return None
    return [p["tagName"] for p in lu if not p.get("isDraft")]
