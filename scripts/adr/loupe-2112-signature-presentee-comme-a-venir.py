#!/usr/bin/env python3
"""Loupe de l'ADR 2112 : ou le depot presente-t-il encore la signature comme une suite a venir.

L'ADR 2112 tranche que les installeurs ne seront pas signes, et que le produit s'arrete a la
notification de mise a jour. C'est une decision de NE PAS FAIRE : elle ne laisse aucun code
derriere elle, donc aucune demande ne peut la faire rougir. Le seul endroit ou elle peut etre
trahie est la PROSE, et c'est la prose qu'on releve.

## Ce qu'elle est, et ce qu'elle n'est pas

Une LOUPE. Elle rend 0 en signalant, elle ne bloque rien, comme l'ADR 2465 le veut d'une
`verification: humaine`. Nommer ici un `enforced_by:` pretendrait qu'un garde refuse, quand il ne
fait que relever.

Elle ne juge pas si une phrase est BIEN ecrite. Elle rapproche deux choses, parler de signer les
installeurs et parler d'une suite, puis laisse un lecteur trancher. Mentionner la signature reste
legitime : la FAQ et la prise en main disent la limite, et c'est ce que l'ADR demande. Ce qui ne
l'est plus, c'est de l'annoncer comme un travail qui vient.

## Pourquoi deux motifs et non un

Le premier attrape la promesse ecrite en toutes lettres (« viendra », « reste a trancher »). Le
second attrape le renvoi a l'issue #2112, qui est close : un lecteur qui la suit tombe sur une
question qui n'en est plus une. Les deux se sont produits, et le second ne contenait aucun mot
d'attente : `dev-docs/ci-cd-release.md` renvoyait a #2112 sans rien promettre.

## Les bornes de mot ne sont pas un detail

Sans elles, le premier motif attrapait « designer », « consignees » et « designant », et le second
« deviendra », qui contient « viendra » : quatre faux positifs sur six constats aux deux premiers
essais. Les DEUX motifs en avaient besoin. Un releve qui crie sur un mot cache dans un autre
apprend a etre ignore, ce que l'ADR 4961 dit deja des rouges mal cibles.

## Le releve se fait au PARAGRAPHE, jamais a la ligne

C'est le defaut qui a failli rendre cette loupe verte sur le passage meme qui l'a fait naitre.
`docs/prise-en-main.md` ecrivait « Elle ne remplace pas la signature des installeurs » a une ligne
et « celle-la viendra separement » a la suivante. Aucune des deux, prise seule, ne porte les deux
motifs ; le paragraphe les porte. Une prose enroulee a 100 colonnes coupe les phrases n'importe ou,
donc un motif qui exige deux mots sur UNE ligne ne mesure que la largeur des colonnes.

Une ligne de tableau, un titre et un item de liste sont en revanche des unites a eux seuls : joindre
un tableau entier faisait voisiner des lignes sans rapport, et le releve accusait leur rencontre.

## Un renvoi qui LIE la decision n'est pas un renvoi en suspens

Le second motif s'eteint des que le paragraphe cite le fichier de l'ADR : citer l'issue A COTE de la
decision qu'elle a produite rend le chemin lisible dans les deux sens, ce que l'ADR 1881 attend d'un
numero d'ADR. Le premier motif reste strict : « la signature viendra plus tard, voir ADR 2112 »
serait une contradiction, pas un renvoi.

## Ce qu'elle ne lit pas, et le dit

Les archives des decisions et l'ADR 2112 elle-meme sont hors corpus : la premiere garde la trace de
ce qui a ete pense avant, la seconde a le droit et le devoir de parler de la decision qu'elle porte.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import DECISIONS, RACINE_DEPOT, loupe, sort_si_contrat_demande

# Les deux arbres de prose. Ce ne sont pas les racines de code de `_commun`, et l'ADR 4586 ne les
# regit pas : ce qui s'importe ici est le dossier des decisions, qu'elle nomme.
PROSE = (pathlib.Path("docs"), pathlib.Path("dev-docs"))
ARCHIVE = DECISIONS / "archive"
ADR_2112 = "2112-on-ne-signe-pas-les-installeurs.md"

SIGNATURE = re.compile(
    r"\b(?:signature|signatures|signer|sign[ée]|sign[ée]s|sign[ée]e|sign[ée]es|notaris\w*)\b",
    re.IGNORECASE,
)
ATTENTE = re.compile(
    r"\b(?:viendra|viendront|à venir|a venir|plus tard|pas encore|suspendue?s?|"
    r"prévue?s?|prevue?s?|ultérieure?s?|ulterieure?s?|séparément|separement)\b"
    r"|\breste (?:une|à|a)\b",
    re.IGNORECASE,
)
RENVOI = re.compile(r"#2112")
PORTE_LA_REPONSE = re.compile(re.escape(ADR_2112))

DEBUT_D_UNITE = re.compile(r"^(?:[-*+]|\d+\.)\s")


def pages(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les pages de prose du depot, archives et ADR 2112 exclues."""
    socle = racine or RACINE_DEPOT
    trouvees: list[pathlib.Path] = []
    for arbre in PROSE:
        trouvees.extend(sorted((socle / arbre).rglob("*.md")))
    if racine is not None:  # un arbre de test n'a pas la forme du depot
        trouvees = sorted(set(trouvees) | set(socle.rglob("*.md")))
    return [p for p in trouvees if ARCHIVE not in p.parents and p.name != ADR_2112]


def unites(page: pathlib.Path) -> list[tuple[int, str]]:
    """Les unites de prose d'une page, avec le numero de leur premiere ligne.

    Une unite est ce qu'un lecteur lit d'un tenant : un paragraphe enroule sur plusieurs lignes, UNE
    ligne de tableau, UN titre, UN item de liste avec ses lignes de continuation.
    """
    blocs: list[tuple[int, str]] = []
    courant: list[str] = []
    depart = 1

    def fermer() -> None:
        nonlocal courant
        if courant:
            blocs.append((depart, " ".join(courant)))
            courant = []

    for numero, brute in enumerate(page.read_text(encoding="utf-8").splitlines(), start=1):
        ligne = brute.strip()
        if not ligne:
            fermer()
            continue
        seule = ligne.startswith("|")
        if seule or ligne.startswith("#") or DEBUT_D_UNITE.match(ligne):
            fermer()
            depart = numero
        elif not courant:
            depart = numero
        courant.append(ligne)
        if seule:
            fermer()
    fermer()
    return blocs


def candidats(racine: pathlib.Path | None = None) -> list[str]:
    """Les passages a relire, prets pour le rapport de la loupe."""
    socle = racine or RACINE_DEPOT
    trouves: list[str] = []
    for page in pages(racine):
        chemin = page.relative_to(socle).as_posix()
        for depart, bloc in unites(page):
            if SIGNATURE.search(bloc) and ATTENTE.search(bloc):
                motif = "promesse"
            elif RENVOI.search(bloc) and not PORTE_LA_REPONSE.search(bloc):
                motif = "renvoi a #2112"
            else:
                continue
            extrait = bloc if len(bloc) <= 110 else bloc[:107] + "..."
            trouves.append(f"{chemin}:{depart} [{motif}] {extrait}")
    return trouves


CONTRAT = {
    "geste": "prose qui presente la signature des installeurs comme une suite a venir",
    "population": "docs/ + dev-docs/ (archives et ADR 2112 exclues)",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_scripts.py#test_loupe_2112",
    "decision": "ADR 2112",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(
        loupe(
            "2112",
            "prose presentant la signature comme une suite a venir",
            candidats(),
            lus=len(pages()),
        )
    )
