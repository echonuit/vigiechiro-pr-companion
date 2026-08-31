#!/usr/bin/env python3
"""Garde sur les traces d outil : ce qu un collage non relu laisse derriere lui.

La grille de la competence `humaniser` porte six traces d outil, notees `T`. Cinq se comptent par
une expression, et c est ce que ce garde tient. La sixieme, le raisonnement laisse dans le texte,
demande une lecture et n est pas ici.

**Pourquoi un garde sur un zero.** Le releve du 2026-08-29 rend ZERO sur 372 370 lignes de prose.
Ce n est pas une raison de s abstenir, c est la raison d ecrire le garde : ces chaines n arrivent
pas par une derive lente qu une relecture rattraperait, elles arrivent d un seul collage, et une
seule occurrence conclut. C est le raisonnement du zero des connecteurs lourds, tenu par
`mesure-registre.py --verifie`, et non celui des onze occurrences que `registre-editorial.md`
ecarte : onze occurrences se corrigent une par une, un zero se perd d un seul coup.

**Ce qu il n examine pas, et pourquoi chaque exemption tient.**

- Les deux exemplaires de `humaniser/SKILL.md`. La grille ENUMERE les chaines qu elle cherche : sans
  cette exemption le garde refuse la page qui le definit. Mesure : 22 marques de citation, toutes
  aux lignes de `T1`. C est l ADR 3645, connue avant d ecrire plutot qu apres un rouge.
- Ce fichier, qui nomme les memes chaines pour la meme raison.
- Le signe CITE plutot qu employe, au grain de la ligne : entre accents graves, ou seul contenu d
une
  chaine litterale. `private static final char BOM = '﻿'` DECRIT la marque d ordre, il ne la
  pose pas. L effacer casserait un analyseur de CSV.
- La sequence d emoji. Le liant U+200D compose un pictogramme, comme dans le scientifique des
  personas. Un liant entre deux symboles n est pas un residu, c est un caractere qui fait son
  travail.

**La cecite declaree.** Le garde lit les fichiers SUIVIS et decodables en UTF-8. Un binaire n est
pas lu. Et il ne prononce rien sur `T6`, qui n a pas de forme.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte

ADR = "4783"
# Ancre sur le SCRIPT et non sur le repertoire courant : un chemin relatif ferait mesurer le depot
# du shell, ce qui rend vert sur un autre exemplaire du meme depot (#4781).
RACINE = pathlib.Path(__file__).resolve().parents[2]

BINAIRES = {
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".ico",
    ".jar",
    ".zip",
    ".gz",
    ".tar",
    ".pdf",
    ".mp4",
    ".webm",
    ".wav",
    ".ttf",
    ".otf",
    ".woff",
    ".woff2",
    ".class",
    ".db",
    ".webp",
    ".avif",
    ".bmp",
    ".tiff",
}

HORS_CHAMP = {
    ".agents/skills/humaniser/SKILL.md": "la grille enumere les chaines qu'elle cherche (ADR 3645)",
    ".claude/skills/humaniser/SKILL.md": "copie de la precedente, meme raison",
    "scripts/adr/4783-traces-d-outil.py": "le garde nomme les chaines qu'il cherche",
}

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


def fichiers(racine: pathlib.Path | None = None) -> list[str]:
    """Les fichiers que le releve accepte de lire, relatifs a `racine`.

    Sur le depot la liste vient de `git ls-files`, donc des fichiers SUIVIS. Sur une fixture, qui
    n est pas un depot, du parcours de l arbre : sans cette seconde branche, un appel avec `racine=`
    lirait quand meme le depot reel, et le temoin passerait au vert sur un arbre qu il ne lit pas.
    """
    racine = racine or RACINE
    if (racine / ".git").exists() or racine == RACINE:
        sortie = subprocess.run(
            ["git", "-C", str(racine), "ls-files", "-z"], capture_output=True, check=True
        ).stdout.decode()
        noms = [c for c in sortie.split("\0") if c]
    else:
        noms = [str(f.relative_to(racine)) for f in sorted(racine.rglob("*")) if f.is_file()]
    return sorted(
        c for c in noms if c not in HORS_CHAMP and pathlib.Path(c).suffix.lower() not in BINAIRES
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


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Une trace d outil par occurrence, avec sa famille."""
    base = racine or RACINE
    trouves = []
    for chemin in fichiers(base):
        try:
            texte = (base / chemin).read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for n, ligne in enumerate(texte.split("\n"), 1):
            extrait = ligne.strip()[:60]
            for marque in MARQUES:
                pos = ligne.find(marque)
                if pos >= 0 and not citee(ligne, pos):
                    trouves.append(f"{chemin}:{n}  T1 {marque}  {extrait}")
            for m in MARQUES_RE.finditer(ligne):
                if not citee(ligne, m.start()):
                    trouves.append(f"{chemin}:{n}  T1 {m.group(0)}  {extrait}")
            for m in UTM.finditer(ligne):
                if not citee(ligne, m.start()):
                    trouves.append(f"{chemin}:{n}  T2 {m.group(0)}  {extrait}")
            for i, car in enumerate(ligne):
                nom = INVISIBLES.get(car)
                if nom and not citee(ligne, i) and not liant_d_emoji(ligne, i):
                    trouves.append(f"{chemin}:{n}  T3 {nom}  {extrait}")
                elif (
                    car in SOSIES
                    and not citee(ligne, i)
                    and (
                        (i and LATINE.match(ligne[i - 1]))
                        or (i + 1 < len(ligne) and LATINE.match(ligne[i + 1]))
                    )
                ):
                    trouves.append(f"{chemin}:{n}  T4 sosie {car!r}  {extrait}")
            for m in GABARIT.finditer(ligne):
                if not citee(ligne, m.start()):
                    trouves.append(f"{chemin}:{n}  T5 {m.group(0)}  {extrait}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte(ADR, "traces d'outil laissees par un collage non relu", suspects()))
