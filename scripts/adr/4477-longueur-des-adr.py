#!/usr/bin/env python3
"""Cliquet sur les ADR qui racontent plus que leur decision et l incident qui l a produite.

Pourquoi « probable » et non « certaine » : le nombre de mots est un INDICE de lisibilite, pas la
regle elle-meme. Une decision peut legitimement demander mille mots quand elle tranche une question
epineuse, et une ADR de trois cents mots peut rester illisible. Le script rend donc des SUSPECTS,
que la relecture trie ; il ne prononce aucun verdict sur une ADR en particulier.

**Ce que la mesure lit, et ce qu elle ne lit pas.** Elle compte les mots du CORPS, en-tete OKF exclu :
un en-tete est de la donnee machine, et le faire peser contre la prose punirait une ADR bien
renseignee. Elle compte en revanche les blocs de code et les tableaux, et c est delibere : les
exclure aurait l air soigneux sans rien mesurer, puisque les 27 ADR qui portent du code ne totalisent
que 495 mots de bloc sur 124 385. Une exclusion qui ne change rien est une precaution decorative, et
elle offrirait en prime un moyen de deplacer de la prose dans un tableau pour passer sous le seuil.

L ENCART DE REVISION est la seule exclusion de corps, pour la meme raison que l en-tete : « Ce qui
fait foi aujourd hui » ne raconte pas la decision, il renvoie a celle qui la depasse. Le compter
punirait l ADR qui dit honnetement ce qui l amende, et pousserait a le taire. La faille que cette
exclusion ouvrirait - deplacer de la prose dans l encart pour passer sous le seuil - est fermee
ailleurs : `verifie_encart_de_revision.py` exige que l encart porte UNE entree par relation declaree,
et rien d autre.

**Pourquoi 800 mots.** Ce n est pas la longueur souhaitable, c est le rang ou la dette devient
resorbable : 53 ADR sur 173, soit trois sur dix. A 600 mots elles seraient 126, un objectif que
personne ne tient et qui finit ignore ; a 1000 elles ne seraient que 18, trop peu pour porter le
travail. Le seuil descendra quand le cliquet, lui, sera descendu.
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import DECISIONS, rapporte, sort_si_contrat_demande

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4477"
SEUIL = 800
RESERVES = {"index.md", "log.md"}

# L encart qui annonce ce qui depasse la decision. Sa forme est fixe, et un autre garde la verifie.
ENCART = '!!! warning "Ce qui fait foi aujourd\u2019hui"'


def corps(chemin: pathlib.Path) -> str:
    """Le corps d une ADR, en-tete OKF et encart de revision retires."""
    texte = chemin.read_text(encoding="utf-8")
    if texte.startswith("---\n"):
        fin = texte.find("\n---\n", 4)
        if fin != -1:
            texte = texte[fin + 5 :]
    return sans_encart(texte)


def sans_encart(texte: str) -> str:
    """Le corps prive de son encart de revision, s il en porte un.

    L encart s arrete a la premiere ligne non vide qui n est PAS indentee : c est la regle des
    admonitions, et s en remettre a une ligne vide couperait au premier paragraphe.
    """
    debut = texte.find(ENCART)
    if debut < 0:
        return texte
    lignes = texte[debut:].split("\n")
    fin = 1
    for i, ligne in enumerate(lignes[1:], 1):
        if ligne.strip() and not ligne.startswith("    "):
            break
        fin = i + 1
    return texte[:debut] + "\n".join(lignes[fin:])


def fichiers(racine=None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT, extraites pour que `lus` les compte (issue #5015).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu il RETENAIT : un
    ciblage manque donnait zero suspect sur zero fichier, et ce zero passait pour un succes.
    """
    return sorted((racine or DECISIONS).glob("*.md"))


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les ADR dont le corps depasse le seuil, de la plus longue a la plus courte.

    `racine` n existe que pour le temoin, qui a besoin d un corpus qu il maitrise : mesurer sur le
    depot ne separerait pas ce que le garde compte de ce qu il epargne.
    """
    mesures = []
    for f in fichiers(racine):
        if f.name in RESERVES:
            continue
        mots = len(corps(f).split())
        if mots > SEUIL:
            mesures.append((mots, f.name))
    mesures.sort(reverse=True)
    return [f"{nom}  {mots} mots ({mots - SEUIL} au-dela du seuil)" for mots, nom in mesures]


CONTRAT = {
    "geste": "corps d ADR au-dela du plafond de mots",
    "population": "les ADR de dev-docs/decisions",
    "dispositif": "cliquet",
    "seuil": "58, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_4477_longueur_des_adr",
    "decision": "ADR 4477",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(rapporte(ADR, f"corps d'ADR au-dela de {SEUIL} mots", suspects(), lus=len(fichiers())))
