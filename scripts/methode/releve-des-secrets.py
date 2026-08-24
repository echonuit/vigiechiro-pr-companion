#!/usr/bin/env python3
"""Releve les secrets ET les variables que les ateliers exigent, et les confronte au depot.

La verification est : « `gh secret list` du depot egale le releve des workflows ».
Ce script fait les deux moities. Sans reseau il rend le RELEVE, lu dans `.github/workflows/` ; avec
`gh` disponible et les droits qu il faut, il rend en plus l ECART.

Pourquoi un script et non une liste ecrite : une liste de secrets recopiee vieillit au premier
atelier ajoute, et son vieillissement est SILENCIEUX. Un secret manquant ne fait pas rougir un
workflow, il fait SAUTER l etape qui en depend. C est ainsi que la publication de la documentation
est passee inapercue : l etape etait grise, pas rouge, et une pastille verte la couvrait.

`GITHUB_TOKEN` est ecarte du releve : Actions le fournit a chaque execution, il n a pas a etre pose.
"""

import argparse
import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
ATELIERS = RACINE / ".github" / "workflows"
FOURNI_PAR_ACTIONS = {"GITHUB_TOKEN"}

# Deux familles, le meme defaut. Une VARIABLE absente ne fait pas plus rougir qu un secret absent :
# elle rend une condition fausse, et le job entier est saute en GRIS. Ce depot en pose trois qui
# sont des interrupteurs, `ENABLE_RELEASE`, `ENABLE_PAGES` et `ENABLE_FLATPAK_REPO` : les omettre du
# releve laisserait hors du champ ce qui eteint le plus de choses d un seul coup.
FAMILLES = (
    ("secret", "secret", re.compile(r"secrets\.([A-Z_][A-Z0-9_]*)")),
    ("variable", "variable", re.compile(r"vars\.([A-Z_][A-Z0-9_]*)")),
)


def releve(motif: re.Pattern) -> dict[str, list[str]]:
    """Chaque nom exige par les ateliers, et ceux qui le demandent."""
    exiges: dict[str, list[str]] = {}
    for atelier in sorted(ATELIERS.glob("*.yml")):
        for nom in sorted(set(motif.findall(atelier.read_text(encoding="utf-8")))):
            if nom in FOURNI_PAR_ACTIONS:
                continue
            exiges.setdefault(nom, []).append(atelier.name)
    return exiges


def poses(commande: str) -> set[str] | None:
    """Ce qui est pose sur le depot, ou None si `gh` ne peut pas repondre."""
    try:
        fini = subprocess.run(["gh", commande, "list"], capture_output=True, text=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    return {l.split("\t")[0].strip() for l in fini.stdout.splitlines() if l.strip()}


def main() -> int:
    p = argparse.ArgumentParser(description="Relevé des secrets et variables exigés par les ateliers")
    p.add_argument("--compare", action="store_true", help="confronte le relevé au dépôt via `gh`")
    args = p.parse_args()

    ecart = 0
    for libelle, commande, motif in FAMILLES:
        exiges = releve(motif)
        print(f"{len(exiges)} {libelle}(s) exigé(s) par les ateliers :\n")
        for nom, ateliers in sorted(exiges.items()):
            print(f"  {nom:26} {', '.join(ateliers)}")

        if not args.compare:
            print()
            continue

        presents = poses(commande)
        if presents is None:
            print(f"\n`gh {commande} list` n'a pas répondu : le relevé seul est rendu.\n",
                  file=sys.stderr)
            continue
        manquants = sorted(set(exiges) - presents)
        inutiles = sorted(presents - set(exiges))
        print(f"\n{len(presents)} {libelle}(s) posé(s) sur le dépôt.")
        if manquants:
            ecart = 1
            print(f"\n{len(manquants)} MANQUANT(S) : les étapes qui en dépendent seront sautées, "
                  "en gris et non en rouge.")
            for nom in manquants:
                print(f"  {nom:26} {', '.join(exiges[nom])}")
        if inutiles:
            print(f"\n{len(inutiles)} posé(s) qu'aucun atelier ne demande :")
            for nom in inutiles:
                print(f"  {nom}")
        if not manquants and not inutiles:
            print("Le relevé et le dépôt concordent.")
        print()

    return ecart


if __name__ == "__main__":
    raise SystemExit(main())
