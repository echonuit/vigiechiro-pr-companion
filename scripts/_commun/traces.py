"""Les cinq traces d outil qui se comptent, en UN seul endroit (#4749).

La grille de la competence `humaniser` porte six traces d outil, notees `T`. Cinq se reconnaissent a
une expression, et ce module en porte la definition : les chaines, les motifs, et les deux regles qui
separent un signe EMPLOYE d un signe cite ou d un liant d emoji.

## Pourquoi ce module

`scripts/adr/4783-traces-d-outil.py` portait cette definition, et il ne lit que les fichiers suivis.
Le corps d une demande de fusion n en est pas un : mesure du 2026-10-07, `verifie_corps_pr.py`
declarait conforme un corps qui portait une marque de citation d assistant, un lien marque du nom de
l outil et un gabarit non rempli. Les deux gardes lisent desormais la meme definition, ici.

Une seconde liste dans le garde du corps aurait diverge de la premiere. Le depot l a deja paye pour
le motif de l elision, ecrit deux fois, corrige d un seul cote a deux reprises (#4837).

## Ce qu il ne fait pas

Il ne lit aucun fichier et ne rend aucun verdict : il dit ce qu une LIGNE porte. Quels textes lire,
et quoi en faire, reste l affaire de chaque garde.

Il ne prononce rien sur `T6`, le raisonnement laisse dans le texte, qui n a pas de forme.

Ce fichier NOMME les chaines qu il cherche. Le garde des fichiers suivis l exempte pour cette raison,
comme il exempte la grille qui les enumere (ADR 3645).
"""

from __future__ import annotations

import re

# T1. Jetons de citation qu une interface d assistant rend invisibles et que le collage emporte.
MARQUES = (
    "citeturn",
    "contentReference[oaicite",
    "oai_citation",
    "grok_card",
    "grok_render_citation_card_json",
    "attributableIndex",
    "ppl-ai-file-upload",
)
MARQUES_RE = re.compile(r"\[cite:\s*\d|\[span_\d+\]\(start_span\)")

# T2. Parametres de suivi que plusieurs assistants accrochent aux liens qu ils rendent.
UTM = re.compile(r"utm_source=(chatgpt|openai|copilot|perplexity|claude)|referrer=grok")

# T3. Caracteres qui ne s affichent pas et se recopient sans qu on les voie.
INVISIBLES = {
    "\u200b": "U+200B",
    "‌": "U+200C",
    "‍": "U+200D",
    "﻿": "U+FEFF",
    "­": "U+00AD",
    "⁠": "U+2060",
}

# T4. Lettres cyrilliques et grecques employees a la place de leurs sosies latines.
SOSIES = "аеорсхуіАЕОСХоΑ"
LATINE = re.compile(r"[A-Za-z]")

# T5. Gabarits qu on a oublie de remplir. Les motifs sont etroits : `[texte](lien)` du Markdown ne
# doit pas les declencher.
GABARIT = re.compile(
    r"\[Votre nom\]|\[Your Name\]|\[INS[EÉ]RER\b|\[INSERT \b|\[[ÀA] COMPL[EÉ]TER\]"
    r"|\b\d{4}-XX-XX\b|\b20XX\b|\bXXXX-XX-XX\b",
    re.I,
)


def citee(ligne: str, position: int) -> bool:
    """Le signe est-il MENTIONNE plutot qu employe ? Voir la troisieme exemption en tete."""
    if ligne.count("`", 0, position) % 2 == 1:
        return True
    fenetre = ligne[max(0, position - 2) : position + 3]
    return bool(re.search(r"""(["'])(\\u[0-9a-fA-F]{4}|.)\1""", fenetre))


def liant_d_emoji(ligne: str, position: int) -> bool:
    """Un U+200D qui compose un pictogramme, et non un residu de collage.

    La sequence d emoji encadre le liant de deux symboles hors du plan latin. Le test porte sur le
    VOISIN de gauche : un liant en tete de ligne n a rien a composer.
    """
    if position == 0:
        return False
    return ord(ligne[position - 1]) > 0x2100


def traces(ligne: str) -> list[str]:
    """Les traces d outil d une ligne, une par occurrence, chacune avec sa famille.

    Chaque entree commence par sa famille, `T1` a `T5`, suivie de ce qui a ete trouve : la marque,
    le parametre de suivi, le nom du caractere invisible, la lettre sosie, le gabarit.
    """
    trouvees = []
    for marque in MARQUES:
        pos = ligne.find(marque)
        if pos >= 0 and not citee(ligne, pos):
            trouvees.append(f"T1 {marque}")
    for m in MARQUES_RE.finditer(ligne):
        if not citee(ligne, m.start()):
            trouvees.append(f"T1 {m.group(0)}")
    for m in UTM.finditer(ligne):
        if not citee(ligne, m.start()):
            trouvees.append(f"T2 {m.group(0)}")
    for i, car in enumerate(ligne):
        nom = INVISIBLES.get(car)
        if nom and not citee(ligne, i) and not liant_d_emoji(ligne, i):
            trouvees.append(f"T3 {nom}")
        elif (
            car in SOSIES
            and not citee(ligne, i)
            and (
                (i and LATINE.match(ligne[i - 1]))
                or (i + 1 < len(ligne) and LATINE.match(ligne[i + 1]))
            )
        ):
            trouvees.append(f"T4 sosie {car!r}")
    for m in GABARIT.finditer(ligne):
        if not citee(ligne, m.start()):
            trouvees.append(f"T5 {m.group(0)}")
    return trouvees
