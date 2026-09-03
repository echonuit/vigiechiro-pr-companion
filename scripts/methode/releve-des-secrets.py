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
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from _commun import sort_si_contrat_demande

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


def releve(motif: re.Pattern, ateliers: pathlib.Path | None = None) -> dict[str, list[str]]:
    """Chaque nom exige par les ateliers, et ceux qui le demandent.

    `ateliers` est injectable pour que l auto-test pose son propre corpus (issue #5157). Sans cela,
    le seul temoin possible lirait les ateliers reels, donc changerait de reponse a chaque atelier
    ajoute : un temoin dont le resultat depend du depot ne prouve pas le releveur, il le suit.
    """
    exiges: dict[str, list[str]] = {}
    for atelier in sorted((ateliers or ATELIERS).glob("*.yml")):
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


def auto_test() -> int:
    """Le releveur se prouve sur un corpus POSE, et dans les deux sens.

    Un temoin qui ne verifierait que « il rend quelque chose » passerait sur un motif casse : il
    faut lui montrer ce qu il doit voir ET ce qu il doit ignorer.
    """
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du releve des secrets (#5157) :")
    _, _, motif_secret = FAMILLES[0]
    _, _, motif_variable = FAMILLES[1]

    with tempfile.TemporaryDirectory(prefix="vc-secrets-") as tmp:
        faux = pathlib.Path(tmp)
        (faux / "a.yml").write_text(
            "jobs:\n  x:\n    env:\n      A: ${{ secrets.JETON_A }}\n"
            "      B: ${{ vars.REGLAGE_B }}\n"
            "      C: ${{ secrets.GITHUB_TOKEN }}\n",
            encoding="utf-8",
        )
        (faux / "b.yml").write_text(
            "jobs:\n  y:\n    env:\n      A: ${{ secrets.JETON_A }}\n", encoding="utf-8"
        )
        # Le sens POSITIF : un secret exige par deux ateliers est rendu, avec ses deux ateliers.
        verifie(
            "un secret exige est rendu, avec qui le demande",
            releve(motif_secret, faux).get("JETON_A"),
            ["a.yml", "b.yml"],
        )
        # Ce que le releveur doit IGNORER : ce que les actions fournissent d elles-memes.
        verifie(
            "GITHUB_TOKEN n est pas exige du depot",
            "GITHUB_TOKEN" in releve(motif_secret, faux),
            False,
        )
        # Les deux familles ne se melangent pas : un motif ne voit pas ce que l autre cherche.
        verifie(
            "une variable n est pas un secret", "REGLAGE_B" in releve(motif_secret, faux), False
        )
        verifie(
            "et le motif des variables la voit", "REGLAGE_B" in releve(motif_variable, faux), True
        )
        # Le sens NEGATIF : sur un corpus vide, le releveur ne rend rien plutot que de deviner.
        vide = faux / "vide"
        vide.mkdir()
        verifie("un corpus sans atelier ne rend rien", releve(motif_secret, vide), {})

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


def main() -> int:
    p = argparse.ArgumentParser(
        description="Relevé des secrets et variables exigés par les ateliers"
    )
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
            print(
                f"\n`gh {commande} list` n'a pas répondu : le relevé seul est rendu.\n",
                file=sys.stderr,
            )
            continue
        manquants = sorted(set(exiges) - presents)
        inutiles = sorted(presents - set(exiges))
        print(f"\n{len(presents)} {libelle}(s) posé(s) sur le dépôt.")
        if manquants:
            ecart = 1
            print(
                f"\n{len(manquants)} MANQUANT(S) : les étapes qui en dépendent seront sautées, "
                "en gris et non en rouge."
            )
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


# Pourquoi `rapport` : il releve ce que les ateliers EXIGENT, et `--compare` dit l ecart avec ce que
# le depot porte. Ni l un ni l autre ne refuse : un secret manquant se voit a l usage, et faire
# rougir la CI sur l inventaire d un depot dont on ne connait pas les secrets serait un faux verdict.
CONTRAT = {
    "geste": "releve des secrets et variables que les ateliers exigent",
    "population": "les workflows de .github/workflows",
    "dispositif": "rapport",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/releve-des-secrets.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())
    raise SystemExit(main())
