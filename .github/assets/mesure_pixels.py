#!/usr/bin/env python3
"""La part de pixels qui different entre deux images. Destine a etre IMPORTE (porte du bash en #5239).

## Pourquoi ce fichier existe

`compare_apercus.py` et `compare_tournages.py` portaient la meme mesure, recopiee. Le 2026-08-23, le
MEME defaut a du etre corrige dans les deux : au-dela du million, ImageMagick ecrit ses comptes en
NOTATION SCIENTIFIQUE - « 1.2034e+06 » - et les deux scripts s arretaient au point.

Le second n a ete trouve que parce que le premier venait de l etre. Rien ne garantissait qu on regarde
le voisin ; une copie n est juste que le jour ou on l ecrit.

## Ce qu elle rend

Un pourcentage, ou « ? » si la mesure echoue. « ? » n est PAS zero : une mesure impossible et une
absence de difference se reparent a des endroits differents, donc elles ne doivent pas se lire pareil
(ADR 2748). C est a l appelant de compter les « ? » a part.

## Les deux pieges de la notation scientifique, aux deux bouts du calcul

**Le compte de pixels.** `compare -metric AE` rend « 1.2034e+06 (1) » au-dela du million, et le
decoupage d origine s arretait au point pour rendre « 1 », soit 0,00 % la ou TOUT avait change. Ce
defaut ment dans le sens rassurant. On prend donc le PREMIER MOT et on le lit comme un nombre a
virgule flottante, ce que la notation scientifique est.

**Le produit des dimensions.** `%w %h` et non `%[fx:w*h]`, meme piege a l autre bout : sur une toile
de 1280 × 900 le produit s ecrit « 1.152e+06 », que le test d entier refusait. La mesure rendait alors
« ? » sur TOUTES les grandes images - 33 des captures du depot depassent ce seuil.

## Pourquoi ImageMagick, et pas une bibliotheque Python

C est la decision de l ADR 5239 : `compare -metric AE` a une definition precise du « pixel
different », que `-fuzz` module, et la remplacer changerait ce que la mesure MESURE. Ajouter une
bibliotheque d images a la couche qui juge tout le reste couterait plus que le sous-processus qu on
economiserait.

Usage : from mesure_pixels import part_changee
        part_changee(avant, apres, tolerance=0, decimales=2)
"""

from __future__ import annotations

import subprocess

INCONNUE = "?"


def part_changee(
    avant: str, apres: str, tolerance: float | str = 0, decimales: int | str = 2
) -> str:
    """Le pourcentage de pixels differents, ou « ? » si la mesure echoue."""
    # `compare -metric AE` ecrit son compte sur la SORTIE D ERREUR et rend 1 des qu il y a une
    # difference : lire le code de sortie ferait passer une mesure reussie pour un echec.
    rendu = subprocess.run(
        ["compare", "-metric", "AE", "-fuzz", f"{tolerance}%", str(avant), str(apres), "null:"],
        capture_output=True,
        text=True,
        check=False,
    )
    brut = (rendu.stdout + rendu.stderr).strip()
    premier = brut.split(" ")[0] if brut else ""
    if not premier:
        return INCONNUE

    dimensions = subprocess.run(
        ["identify", "-format", "%w %h", str(apres)], capture_output=True, text=True, check=False
    )
    if dimensions.returncode != 0 or not dimensions.stdout.strip():
        return INCONNUE

    try:
        largeur, hauteur = (float(x) for x in dimensions.stdout.split())
        pixels = float(premier)
    except ValueError:
        return INCONNUE
    total = largeur * hauteur
    # Une valeur illisible se dit, elle ne se prend pas pour un zero. Le premier caractere doit etre
    # un chiffre : `compare` ecrit un message d erreur la ou il ecrirait un compte.
    if total <= 0 or not premier[0].isdigit():
        return INCONNUE
    return f"{100 * pixels / total:.{int(decimales)}f}"
