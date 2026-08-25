#!/usr/bin/env python3
"""Quelle javadoc a ete relue, quelle javadoc reste a lire - et ce qui a bouge depuis.

Le chantier A30 se mesurait par un cliquet de LONGUEUR : les blocs de plus de huit lignes de prose.
Cette mesure ne dit rien des blocs courts, qui peuvent etre tout aussi caducs, ni de ceux qu on n a
jamais ouverts. « Zero commentaire non relu » ne se prouve pas avec elle.

Ce compteur lit un manifeste - `relus.txt` - et rend ce qui reste. Un fichier n y entre que lorsque
TOUS ses blocs ont ete ouverts et juges, pas seulement le plus long.

**Chaque entree porte l empreinte de la javadoc relue.** Sans elle, un fichier marque relu resterait
marque apres qu on a reecrit ses commentaires : le manifeste dirait « lu » d une prose que personne
n a jamais lue. Avec elle, toute javadoc qui change fait ressortir son fichier dans les restants,
et il faut le relire pour le remarquer. C est ce qui rend « ne rien oublier » mecanique.

    --reste N       : les N fichiers non relus les plus lourds en prose, pour choisir la tranche.
    --marque F...   : porte des fichiers au manifeste, avec leur empreinte du jour.
    --verifie       : sortir 1 s il reste des fichiers non relus (fin de chantier).
"""

import hashlib
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = RACINE / "src" / "main" / "java"
TESTS = RACINE / "src" / "test" / "java"
# Les deux racines de Java du depot. Les chemins du manifeste sont relatifs a la RACINE, et non a
# l une d elles : une cle « fr/…/Machin.java » ne dirait pas de quel arbre elle vient, et un
# homonyme entre production et test se recouvrirait en silence.
RACINES = (PRODUCTION, TESTS)
MANIFESTE = pathlib.Path(__file__).parent / "relus.txt"

ENTETE = (
    "# Fichiers Java dont TOUS les blocs de javadoc ont ete ouverts et juges.\n"
    "# Une ligne par fichier : <empreinte>  <chemin relatif a la racine du depot>.\n"
    "# L empreinte est celle de la javadoc RELUE : si elle change, le fichier redevient a relire.\n"
    "# Voir couverture-relecture.py et scripts/adr/cliquet-javadoc-non-relue.py.\n"
)


def lignes_javadoc(fichier: pathlib.Path) -> list[str]:
    """Le contenu des lignes `///` du fichier, sans leur indentation ni leur prefixe."""
    retenues = []
    for ligne in fichier.read_text(encoding="utf-8").split("\n"):
        nu = ligne.strip()
        if nu.startswith("///"):
            retenues.append(nu[3:].strip())
    return retenues


def empreinte(fichier: pathlib.Path) -> str:
    """L empreinte de la javadoc d un fichier : ce qui change quand un commentaire change.

    Elle porte les etiquettes de contrat autant que la prose : reecrire un `@param` est une
    modification de javadoc, et la relire est le geste que ce manifeste enregistre. Elle ignore en
    revanche l indentation, qui appartient au formateur et non a l auteur.
    """
    corps = "\n".join(lignes_javadoc(fichier))
    return hashlib.sha256(corps.encode("utf-8")).hexdigest()[:12]


def prose(fichier: pathlib.Path) -> int:
    """Les lignes de prose javadoc du fichier, etiquettes de contrat exclues."""
    compte, dans_etiquette = 0, False
    for corps in lignes_javadoc(fichier):
        if not corps:
            dans_etiquette = False
            continue
        if corps.startswith("@") and len(corps) > 1 and corps[1].isalpha():
            dans_etiquette = True
            continue
        if dans_etiquette:
            continue
        compte += 1
    return compte


def relus(chemin: pathlib.Path = None) -> dict[str, str]:
    """Le manifeste : chemin -> empreinte de la javadoc au moment de la relecture.

    Seule lecture du manifeste : le cliquet qui le verifie emprunte cette fonction plutot que d en
    tenir une seconde. Les deux s etaient deja separees sur le cas de la ligne mal formee, l une la
    refusant et l autre l ignorant, pour un meme fichier.
    """
    chemin = chemin or MANIFESTE
    if not chemin.exists():
        return {}
    lu = {}
    for ligne in chemin.read_text(encoding="utf-8").split("\n"):
        nu = ligne.strip()
        if not nu or nu.startswith("#"):
            continue
        parts = nu.split(None, 1)
        # Une entree sans empreinte est illisible plutot qu absente : le manifeste porte une
        # affirmation, et une affirmation mal formee ne doit pas passer pour un silence.
        if len(parts) != 2:
            raise SystemExit(f"Entree de manifeste mal formee : « {nu} » (attendu : <empreinte>  <chemin>)")
        lu[parts[1]] = parts[0]
    return lu


def ecrire(manifeste: dict[str, str]) -> None:
    corps = "".join(f"{manifeste[c]}  {c}\n" for c in sorted(manifeste))
    MANIFESTE.write_text(ENTETE + corps, encoding="utf-8")


def etat() -> tuple[list[tuple[int, str]], list[tuple[int, str, str]]]:
    """(relus, restants). Un restant dit POURQUOI il l est : jamais lu, ou javadoc modifiee."""
    deja = relus()
    lus, reste = [], []
    for f in sorted(f for racine in RACINES for f in racine.rglob("*.java")):
        rel = str(f.relative_to(RACINE))
        connue = deja.get(rel)
        if connue is None:
            reste.append((prose(f), rel, "jamais relu"))
        elif connue != empreinte(f):
            reste.append((prose(f), rel, "javadoc modifiée depuis la relecture"))
        else:
            lus.append((prose(f), rel))
    lus.sort(reverse=True)
    reste.sort(reverse=True)
    return lus, reste


def marque(chemins: list[str]) -> int:
    """Porte des fichiers au manifeste avec leur empreinte du jour."""
    manifeste = relus()
    for brut in chemins:
        chemin = pathlib.Path(brut)
        absolu = chemin if chemin.is_absolute() else RACINE / chemin
        if not absolu.is_file():
            raise SystemExit(f"Fichier introuvable : {brut}")
        manifeste[str(absolu.resolve().relative_to(RACINE))] = empreinte(absolu)
    ecrire(manifeste)
    return len(manifeste)


def main() -> int:
    if "--marque" in sys.argv:
        i = sys.argv.index("--marque")
        print(f"{marque(sys.argv[i + 1:])} fichiers au manifeste")
        return 0

    lus, reste = etat()
    total = len(lus) + len(reste)
    prose_lue = sum(p for p, _ in lus)
    prose_reste = sum(p for p, _, _ in reste)
    if "--reste" in sys.argv:
        i = sys.argv.index("--reste")
        n = int(sys.argv[i + 1]) if i + 1 < len(sys.argv) else 20
        for p, f, motif in reste[:n]:
            suffixe = "" if motif == "jamais relu" else f"  ({motif})"
            print(f"  {p:4}  {f}{suffixe}")
        print()
    part = 100 * len(lus) / total if total else 100
    print(
        f"relus : {len(lus)}/{total} fichiers ({part:.1f} %), "
        f"{prose_lue} lignes de prose lues, {prose_reste} restantes"
    )
    modifies = [f for _, f, m in reste if m != "jamais relu"]
    if modifies:
        print(f"dont {len(modifies)} fichier(s) dont la javadoc a changé depuis leur relecture :")
        for f in modifies[:10]:
            print(f"  {f}")
    if "--verifie" in sys.argv and reste:
        print(f"{len(reste)} fichier(s) dont la javadoc n'a pas été relue.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
