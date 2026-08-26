#!/usr/bin/env python3
"""Cliquet sur les pictogrammes d alerte qui tiennent lieu d avertissement.

Un pictogramme d alerte n avertit de rien : il signale qu on avertit. La ou il ouvre une ligne sur
quatre - c est le cas de l index des decisions - il a cesse de distinguer quoi que ce soit, et le
lecteur l enjambe comme il enjambe une puce. Ce qui doit alerter se dit dans la phrase, ou dans
l encart que le format prevoit pour cela.

**Pourquoi « probable » et non « certaine ».** Le motif se reconnait mecaniquement, mais son remede
ne se decide pas mecaniquement. Retirer le signe ne suffit pas : une phrase qui commencait par
« ⚠️ Ne pas redemarrer entre S7-12 et S7-13 » porte deja son avertissement dans ses mots, et le
signe s enleve seul ; une autre ne dit l alerte QUE par le signe, et demande alors d etre reecrite.
Le script rend des SUSPECTS, la relecture tranche.

**Ce que le releve ne lit pas, et pourquoi.**

- Les noeuds `<text>` et `<tspan>` des maquettes. Le pictogramme y est le CONTENU MONTRE : la
  maquette rend ce que l ecran affiche, et l effacer falsifierait la maquette au lieu de la corriger.
- Les blocs de code d un document markdown. Ils citent ce que le programme emet, ou ce qu un
  fichier contient ; ce n est pas la prose du document.
- Les chaines litterales des fichiers de code. Un `echo "⚠️ rien n a ete filme"` est un message
  d execution, gouverne par les articles sur le compte rendu, pas par celui-ci.
- Le signe CITE comme jeton. La difference est celle de la mention et de l usage : « les libelles
  commencaient par un ⚠ » PARLE du caractere, il ne s en sert pas pour alerter. L effacer ne
  corrigerait pas la phrase, il la rendrait fausse. Trois formes le disent : entre accents graves,
  entre guillemets francais, ou au voisinage d un AUTRE marqueur - `✓ / ✗ / ⚠`, « un ✗ interdit ;
  un ⚠ laisse deposer ». Le voisinage se mesure a 32 caracteres de part et d autre, et non sur la
  ligne entiere : une ligne qui porte une fleche quelque part ne doit pas devenir une zone franche.

**La cecite declaree.** L appartenance a une chaine se decide sur les GUILLEMETS DOUBLES seuls, en
comptant ceux qui precedent le signe : un compte impair vaut « dedans ». Compter aussi l apostrophe
serait une faute en francais, ou « l ecran ⚠️ » passerait pour du code. Une chaine a guillemets
SIMPLES echappe donc a ce compte - et c est pourquoi un second chemin la rattrape : l APPEL
d emission. `printf '⚠️ …'`, `format('⚠️ …')` ouvrent une chaine juste apres un verbe qui emet, ce
qui suffit a reconnaitre le message sans avoir a decider ce qu est une apostrophe. Le mot seul ne
suffit pas : « le format ⚠️ change » n est pas une emission, faute du guillemet qui suit.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4366"

ALERTE = "⚠"

NOEUD_MONTRE = re.compile(r"<text\b|<tspan\b")

# Les autres marqueurs du corpus. Leur voisinage dit que la ligne parle DES signes.
MARQUEURS = set("✓✗❌✅⛔❗→←▸☰≥≤−·✎💾ℹ🗑")

# Ce qui borne un jeton : ni lettre ni chiffre.
DELIMITEURS = set("`«»()*,;/|·\"'[]")

# Les paires qui ENCADRENT une mention. Un signe entoure du meme delimiteur est cite ; un signe
# precede d un delimiteur quelconque et suivi d un autre est employe, et le compte le veut.
PAIRES = {"`": "`", "«": "»", "(": ")", "[": "]", '"': '"', "'": "'", "*": "*", "|": "|"}

# La demi-fenetre du voisinage, en caracteres.
VOISINAGE = 32

# Un verbe qui emet, suivi de l ouverture d une chaine. C est cette SUITE qui fait l emission.
EMISSION = re.compile(r"""(?:echo|printf|print|format|System\.(?:out|err)\.\w+)\s*\(?\s*['"]""")

BINAIRES = {
    ".ttf", ".otf", ".woff", ".woff2", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
    ".jar", ".zip", ".gz", ".tar", ".pdf", ".webm", ".mp4", ".wav", ".class", ".db",
    ".webp", ".avif", ".bmp", ".tiff",
}

CODE = {".java", ".py", ".sh", ".bash", ".yml", ".yaml", ".fxml", ".css", ".bats"}

# Les prefixes d arbres REPRIS verbatim d un outil amont, exemptes parce que les reecrire les
# ferait diverger de leur source. La liste est VIDE depuis #4516 : les competences OpenSpec ont
# ete adoptees par #4515, et les six commandes `opsx` qui restaient amont ont disparu. Elle est
# gardee, et non supprimee, pour que la prochaine reprise sache ou se declarer.
REPRIS = ()

# Les trois textes qui doivent PORTER le signe pour l enoncer : la decision, le garde, ses cas.
# L exemption est nominative et tient a trois chemins, pour qu elle ne puisse pas s elargir en
# silence. C est l ADR 3645, « un detecteur textuel s exclut de son propre corpus ».
TEXTES_DU_MOTIF = (
    "dev-docs/decisions/4366-un-avertissement-se-dit-en-mots.md",
    "scripts/adr/4366-avertissement-en-pictogramme.py",
    "scripts/adr/verifie_scripts.py",
)


def fichiers(racine: pathlib.Path = pathlib.Path(".")) -> list[str]:
    """Les fichiers versionnes que le releve accepte de lire."""
    sortie = subprocess.run(
        ["git", "-C", str(racine), "ls-files", "-z"], capture_output=True, check=True
    ).stdout.decode()
    gardes = []
    for chemin in sortie.split("\0"):
        if not chemin or chemin.startswith(REPRIS) or chemin in TEXTES_DU_MOTIF:
            continue
        if pathlib.Path(chemin).suffix.lower() in BINAIRES:
            continue
        gardes.append(chemin)
    return sorted(gardes)


def dans_une_chaine(ligne: str, position: int) -> bool:
    """Le signe est-il dans une chaine a guillemets doubles ? Voir la cecite declaree en tete."""
    return ligne.count('"', 0, position) % 2 == 1


def citee(ligne: str, position: int) -> bool:
    """Le signe est-il MENTIONNE plutot qu employe ? Voir la troisieme cecite en tete."""
    if ligne.count("`", 0, position) % 2 == 1:
        return True
    if ligne.count("«", 0, position) > ligne.count("»", 0, position):
        return True
    fenetre = ligne[max(0, position - VOISINAGE) : position + VOISINAGE + 1]
    if any(c in MARQUEURS for c in fenetre):
        return True
    gauche = ligne[:position].rstrip()
    droite = ligne[position + 1 :].lstrip("️").lstrip()
    # Une mention est ENCADREE : le meme delimiteur ouvre et ferme. « Un delimiteur de chaque cote »
    # ne suffisait pas, et laissait passer la forme la PLUS courante d un avertissement reel :
    # `⚠️ **texte**` avait un gauche vide (donc accepte) et un `*` a droite, donc il ne comptait pas.
    # En javadoc c etait pire encore, `///` fournissant le `/` a gauche. 264 avertissements
    # echappaient ainsi au compte, sur 1 468 comptes (#4464).
    if not gauche or not droite:
        return False
    ouvrant = gauche[-1]
    return ouvrant in PAIRES and droite[0] == PAIRES[ouvrant]


def alertes(ligne: str, suffixe: str, dans_bloc: bool) -> list[int]:
    """Les positions des pictogrammes de PROSE d une ligne, contenu montre retire."""
    if ALERTE not in ligne:
        return []
    if NOEUD_MONTRE.search(ligne):
        return []
    if dans_bloc:
        return []
    trouves = []
    for m in re.finditer(ALERTE, ligne):
        if suffixe in CODE and dans_une_chaine(ligne, m.start()):
            continue
        if EMISSION.search(ligne) and ligne.count("'", 0, m.start()) % 2 == 1:
            continue
        if citee(ligne, m.start()):
            continue
        trouves.append(m.start())
    return trouves


def suspects(racine: pathlib.Path = pathlib.Path(".")) -> list[str]:
    """Un suspect par PICTOGRAMME, et non par fichier.

    Le grain compte : un cliquet pose sur le nombre de fichiers porteurs laisserait une page passer
    de un pictogramme a soixante-sept sans bouger d un cran.
    """
    trouves = []
    for chemin in fichiers(racine):
        try:
            texte = (racine / chemin).read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        suffixe = pathlib.Path(chemin).suffix.lower()
        dans_bloc = False
        for n, ligne in enumerate(texte.splitlines(), 1):
            # L etat de bloc se met a jour AVANT le releve : une cloture de bloc ne porte pas de
            # prose, et une ouverture non plus.
            if suffixe == ".md" and ligne.lstrip().startswith("```"):
                dans_bloc = not dans_bloc
                continue
            for _ in alertes(ligne, suffixe, dans_bloc):
                trouves.append(f"{chemin}:{n}  {ligne.strip()[:78]}")
    return trouves


def par_fichier(listes: list[str]) -> list[tuple[int, str]]:
    """Le releve regroupe, pour choisir par ou une tranche commence."""
    compte: dict[str, int] = {}
    for s in listes:
        chemin = s.split(":", 1)[0]
        compte[chemin] = compte.get(chemin, 0) + 1
    return sorted(((v, k) for k, v in compte.items()), key=lambda m: (-m[0], m[1]))


if __name__ == "__main__":
    listes = suspects()
    if "--releve" in sys.argv:
        groupes = par_fichier(listes)
        for compte, chemin in groupes[:25]:
            print(f"  {compte:5}  {chemin}")
        print(f"\n{len(groupes)} fichiers porteurs, {len(listes)} pictogrammes")
        sys.exit(0)
    sys.exit(rapporte(ADR, "avertissements portes par un pictogramme", listes, apercu=25))
