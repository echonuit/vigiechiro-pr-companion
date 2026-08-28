#!/usr/bin/env python3
"""Garde sur l apostrophe courbe : ce depot n ecrit que l apostrophe ASCII.

La regle est dans `CONTRIBUTING.md`, section « Le registre ». Une seule apostrophe, la droite.

**Ce que la premiere version gardait, et pourquoi elle a grandi.** #4368 tenait les seules chaines
litterales de `src/main/java`, au motif que douze apostrophes seulement atteignaient un ecran et
qu un cliquet a 188 aurait coute plus qu il ne rapporte. La regle a ete tranchee depuis : le depot
n ecrit que l ASCII, partout. Le garde suit la decision, pas l inverse.

**Ce qu il n examine pas, et pourquoi chaque exemption tient.**

- `CHANGELOG.md`. Engendre par semantic-release depuis les sujets de commits deja fusionnes. Le
  corriger falsifierait le compte rendu de ce qui a ete livre, et la ligne reecrite reviendrait a la
  generation suivante. Sa SOURCE est le titre de PR, que `verifie-titre-pr.sh` garde (ADR 2843
  emploie le meme raisonnement pour le cadratin).
- Les SVG engendres par Mocodo. Mesure : leurs sources `.mcd` portent l apostrophe DROITE, et l outil
  substitue la courbe au rendu. `date d'import` dans la source devient `date d’import` dans le SVG.
  Corriger le rendu serait defait a la regeneration ; on ne reecrit pas ce qu on n a pas ecrit.
- Le signe CITE plutot qu employe. `COURBE = "’"`, « choisir la droite ou la courbe », une classe de
  caracteres d expression reguliere : ces lignes PARLENT du caractere. L effacer ne corrigerait pas
  la phrase, il la rendrait fausse. La regle est celle de l ADR 3645 appliquee au grain de la ligne :
  entre accents graves, entre guillemets francais, ou dans une chaine dont c est le seul contenu.

**La cecite declaree.** Le garde lit les fichiers SUIVIS et decodables en UTF-8. Un binaire qui
porterait la sequence n est pas lu, et son nombre n est pas annonce : il n y en a aucun aujourd hui,
et un binaire ne se relit pas de toute facon.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

ADR = "4368"
RACINE = pathlib.Path(__file__).resolve().parents[2]

COURBE = "’"

BINAIRES = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".jar", ".zip", ".gz", ".tar", ".pdf",
    ".mp4", ".webm", ".wav", ".ttf", ".otf", ".woff", ".woff2", ".class", ".db",
    ".webp", ".avif", ".bmp", ".tiff",
}

# Les fichiers dont la forme de l apostrophe ne nous appartient pas.
HORS_CHAMP = {
    "CHANGELOG.md": "engendré par semantic-release ; sa source est le titre de PR",
    "brief/docs/assets/diagrammes/modele-conceptuel.svg": "rendu par Mocodo, qui substitue",
    "brief/docs/assets/diagrammes/modele-integration-plateforme.svg": "rendu par Mocodo, qui substitue",
    "dev-docs/assets/nuit-de-capture.svg": "rendu par Mocodo, qui substitue",
    "scripts/adr/4368-apostrophe-droite.py": "le garde nomme le caractère qu'il cherche",
}

VOISINAGE = 24


def fichiers(racine: pathlib.Path = None) -> list[str]:
    """Les fichiers que le releve accepte de lire, relatifs a `racine`.

    Sur le depot, la liste vient de `git ls-files` : les fichiers SUIVIS, donc ni les artefacts de
    build ni le brouillon local de qui lance le script. Sur une fixture, qui n est pas un depot, elle
    vient du parcours de l arbre. Sans cette seconde branche, `suspects(racine=...)` accepterait un
    chemin et lirait quand meme le depot reel : une signature qui ment est pire qu une absente, et
    c est ce qui a fait passer son cas temoin au vert sur une fixture qu il ne lisait pas.
    """
    racine = racine or RACINE
    if (racine / ".git").exists() or racine == RACINE:
        sortie = subprocess.run(
            ["git", "-C", str(racine), "ls-files", "-z"], capture_output=True, check=True
        ).stdout.decode()
        noms = [c for c in sortie.split("\0") if c]
    else:
        noms = [str(f.relative_to(racine)) for f in sorted(racine.rglob("*")) if f.is_file()]
    gardes = []
    for chemin in noms:
        if chemin in HORS_CHAMP:
            continue
        if pathlib.Path(chemin).suffix.lower() in BINAIRES:
            continue
        gardes.append(chemin)
    return sorted(gardes)


def citee(ligne: str, position: int) -> bool:
    """Le signe est-il MENTIONNE plutot qu employe ? Voir la troisieme exemption en tete."""
    if ligne.count("`", 0, position) % 2 == 1:
        return True
    if ligne.count("«", 0, position) > ligne.count("»", 0, position):
        return True
    # Une chaine dont le signe est le seul contenu : `"’"`, `'’'`.
    fenetre = ligne[max(0, position - 2): position + 3]
    if re.search(r"""(["'])’\1""", fenetre):
        return True
    # Une classe de caracteres d expression reguliere qui l enumere avec la droite.
    voisin = ligne[max(0, position - VOISINAGE): position + VOISINAGE]
    return "[" in voisin and "'" in voisin and "]" in voisin


def suspects(racine: pathlib.Path = None) -> list[str]:
    """Une apostrophe courbe employee, par occurrence."""
    base = racine or RACINE
    trouves = []
    for chemin in fichiers(base):
        p = base / chemin
        try:
            texte = p.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for n, ligne in enumerate(texte.split("\n"), 1):
            if COURBE not in ligne:
                continue
            for m in re.finditer(COURBE, ligne):
                if citee(ligne, m.start()):
                    continue
                trouves.append(f"{chemin}:{n}  {ligne.strip()[:70]}")
    return trouves


if __name__ == "__main__":
    sys.exit(rapporte(ADR, "apostrophe courbe employée au lieu de l'ASCII", suspects(), apercu=15))
