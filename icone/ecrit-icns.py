"""Assemble un fichier ICNS (icône macOS) à partir de PNG carrés.

Pourquoi ce script plutôt qu'un outil : `magick sortie.icns` écrit en réalité un PNG portant
l'extension `.icns` (vérifiable au `file`), que jpackage refuse ; `png2icns` (libicns) n'est pas
disponible partout et n'est plus maintenu. Le format, lui, est trivial et stable.

Structure : l'en-tête `icns` suivi de la taille totale, puis une suite de blocs
`<type sur 4 octets><taille du bloc, en-tête compris><données>`. Depuis OS X 10.7, les types
modernes acceptent directement une charge utile PNG, ce qu'on exploite ici.

Usage : ecrit-icns.py <sortie.icns> <taille>:<fichier.png> [<taille>:<fichier.png> …]
"""

import struct
import sys

# Type ICNS attendu pour chaque dimension en pixels. Les entrées « @2x » (ic11-ic14) portent la
# variante Retina d'une taille logique deux fois plus petite.
TYPES_PAR_TAILLE = {
    16: b"icp4",
    32: b"icp5",
    64: b"ic12",
    128: b"ic07",
    256: b"ic08",
    512: b"ic09",
    1024: b"ic10",
}


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2

    sortie, entrees = argv[1], argv[2:]
    blocs = []

    for entree in entrees:
        taille_texte, _, chemin = entree.partition(":")
        taille = int(taille_texte)
        if taille not in TYPES_PAR_TAILLE:
            print(f"taille non gérée par le format ICNS : {taille}", file=sys.stderr)
            return 1
        with open(chemin, "rb") as f:
            donnees = f.read()
        if not donnees.startswith(b"\x89PNG\r\n\x1a\n"):
            print(f"{chemin} n'est pas un PNG", file=sys.stderr)
            return 1
        blocs.append(TYPES_PAR_TAILLE[taille] + struct.pack(">I", len(donnees) + 8) + donnees)

    corps = b"".join(blocs)
    with open(sortie, "wb") as f:
        f.write(b"icns" + struct.pack(">I", len(corps) + 8) + corps)

    print(f"{sortie} : {len(blocs)} tailles, {len(corps) + 8} octets")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
