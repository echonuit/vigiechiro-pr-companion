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
- `scripts/_commun/traces.py`, qui porte la definition des cinq familles et nomme donc les memes
  chaines. C etait ce fichier-ci jusqu a #4749 : la definition en est sortie pour que le garde du
  corps des demandes, `.github/scripts/verifie_corps_pr.py`, lise la meme au lieu d en ecrire une
  seconde.
- Le signe CITE plutot qu employe, au grain de la ligne : entre accents graves, ou seul contenu d
une
  chaine litterale. `private static final char BOM = '﻿'` DECRIT la marque d ordre, il ne la
  pose pas. L effacer casserait un analyseur de CSV.
- La sequence d emoji. Le liant U+200D compose un pictogramme, comme dans le scientifique des
  personas. Un liant entre deux symboles n est pas un residu, c est un caractere qui fait son
  travail.

**La cecite declaree.** Le garde lit les fichiers SUIVIS et decodables en UTF-8. Un binaire n est
pas lu. Le corps d une demande de fusion n est pas un fichier : c est le garde du corps qui le
tient, sur la meme definition. Et il ne prononce rien sur `T6`, qui n a pas de forme.
"""

import pathlib
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import rapporte, sort_si_contrat_demande
from _commun.traces import traces

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
    "scripts/_commun/traces.py": "la definition nomme les chaines que le garde cherche",
}


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
            trouves.extend(f"{chemin}:{n}  {trace}  {extrait}" for trace in traces(ligne))
    return trouves


CONTRAT = {
    "geste": "trace d outil laissee par un collage non relu",
    "population": "les fichiers versionnes du depot, par git ls-files",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_4783_traces_d_outil",
    "decision": "ADR 4783",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(
        rapporte(
            ADR, "traces d'outil laissees par un collage non relu", suspects(), lus=len(fichiers())
        )
    )
