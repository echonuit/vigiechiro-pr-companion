#!/usr/bin/env python3
"""Garde « jeton VigieChiro en clair » (#2741, porte du bash en #5231).

## Pourquoi une garde maison, alors que GitHub scanne les secrets

Le scan de secrets et la protection au push sont actives sur le depot, mais ils ne reconnaissent que
les **motifs de fournisseurs** : cles AWS, jetons GitHub, cles Stripe. Or le secret que ce depot
risque de laisser fuir est un **jeton VigieChiro**, lu dans `localStorage['auth-session-token']` du
site : une chaine opaque, sans prefixe distinctif, qu aucun catalogue de fournisseur ne connait. Les
motifs personnalises, qui l attraperaient, demandent GitHub Advanced Security - indisponible sur le
plan de l organisation.

## Ce qu elle cherche, et pourquoi ce n est pas la forme du jeton

La forme est indistinguable : une chaine alphanumerique quelconque. Un detecteur par entropie
hurlerait sur les empreintes SHA-256 que ce depot contient en clair partout (manifestes de
sauvegarde, fixtures de recette).

C est donc le **contexte** qui est cherche, et il est stable : le nom de la propriete ou de la cle,
suivi d une **affectation** et d une **valeur litterale assez longue** pour ne pas etre un
marque-place.

## Elle porte sa propre preuve

`--auto-test` fait passer neuf lignes connues - quatre fuites, cinq usages legitimes - par le MEME
motif que le balayage. Une garde qu on n a jamais vue rougir n est pas une garde.

Usage : python3 .github/scripts/verifie_jeton.py [--auto-test]
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
MOI = ".github/scripts/verifie_jeton.py"

# Nom de la cle, une affectation (`=`, `:` ou `>` pour le XML), puis 12 caracteres ou plus de valeur
# litterale. 12 : plus long que tous les marque-places du depot, plus court que n importe quel jeton.
MOTIF = re.compile(
    r"""(vigiechiro\.token|auth-session-token)["']?[ \t]*[=:>][ \t]*["']?[A-Za-z0-9_-]{12,}"""
)
# Ce qui reste admis malgre la forme : un marque-place explicite, ou une reference a une variable.
TOLERE = re.compile(r"XXXX|VIGIECHIRO_TOKEN|EXEMPLE|PLACEHOLDER|votre-jeton|secrets\.")

# Le motif tel que `git grep -E` le comprend : les classes POSIX y sont attendues telles quelles.
MOTIF_GIT = """(vigiechiro\\.token|auth-session-token)["']?[[:space:]]*[=:>][[:space:]]*["']?[A-Za-z0-9_-]{12,}"""


def suspecte(ligne: str) -> bool:
    """Vrai si la ligne ressemble a un jeton en clair."""
    return bool(MOTIF.search(ligne)) and not TOLERE.search(ligne)


FUITES = (
    "./mvnw -Papi-live test -Dvigiechiro.token=a1b2c3d4e5f6a7b8c9d0",
    "vigiechiro.token: 5f2b8c1e9a3d7f4b2e6c",
    '"auth-session-token": "9d8c7b6a5e4f3d2c1b0a"',
    "<vigiechiro.token>abcdef1234567890</vigiechiro.token>",
)
LEGITIMES = (
    "./mvnw -Papi-live test -Dvigiechiro.token=XXXX",
    './mvnw -B -Papi-live test -Dvigiechiro.token="$VIGIECHIRO_TOKEN"',
    "VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}",
    "<vigiechiro.token></vigiechiro.token>",
    'System.setProperty("vigiechiro.token", token);',
)


def _auto_test() -> int:
    """Les neuf lignes connues, par le MEME motif que le balayage."""
    echecs = 0
    for ligne in FUITES:
        if not suspecte(ligne):
            print(f"❌ autotest : fuite NON détectée -> {ligne}")
            echecs += 1
    for ligne in LEGITIMES:
        if suspecte(ligne):
            print(f"❌ autotest : faux positif -> {ligne}")
            echecs += 1
    if echecs > 0:
        print(
            f"Autotest de la garde jeton : {echecs} échec(s). Le motif ne fait plus ce qu'il promet."
        )
        return 1
    print(
        f"Autotest de la garde jeton : OK ({len(FUITES)} fuites détectées, "
        f"{len(LEGITIMES)} usages légitimes tolérés)."
    )
    return 0


def juger() -> int:
    """Le balayage du contenu VERSIONNE, celui que la CI recoit et qui part chez tout le monde."""
    rendu = subprocess.run(
        ["git", "grep", "-nIE", MOTIF_GIT, "--", f":!{MOI}"],
        capture_output=True,
        text=True,
        cwd=RACINE,
        check=False,
    )
    trouvailles = [l for l in rendu.stdout.splitlines() if l and not TOLERE.search(l)]

    if trouvailles:
        print("❌ Jeton VigieChiro possiblement en clair dans un fichier versionné :")
        for t in trouvailles:
            print(f"   {t}")
        print()
        print("Un jeton VigieChiro vit 14 jours et donne accès aux données de son porteur.")
        print(
            "Si c'en est un : le RÉVOQUER d'abord (il est déjà dans l'historique git), puis le retirer."
        )
        print("Si c'est un marque-place, l'écrire « XXXX » comme le reste de la documentation.")
        return 1

    print(
        "Garde jeton : OK (aucune valeur littérale affectée à vigiechiro.token ni auth-session-token)."
    )
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
