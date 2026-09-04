#!/usr/bin/env python3
"""Une entree `type: boolean` ne se compare pas a une chaine (#5219, porte du bash).

Une entree declaree `type: boolean` arrive comme un VRAI booleen. La comparer a une chaine casse les
deux en nombres : `== 'true'` vaut TOUJOURS faux, `!= 'true'` TOUJOURS vrai. La forme juste est
`if: ${{ inputs.drapeau }}` ou `if: ${{ !inputs.drapeau }}`.

## Pourquoi TOUTE ligne, et pas seulement les `if:`

La premiere version ne regardait que les conditions, et la comparaison fautive suivante est passee
dessous sans etre vue :

    name: recette-filmee-${{ inputs.publier_les_clips == 'true' && 'planche' || … }}

L artefact s est donc appele `recette-filmee-EditeurCommentaireTest`, le job de publication ne l a
pas trouve, et le tournage complet a echoue une seconde fois - sur le defaut meme que ce garde venait
d etre ecrit pour empecher.

Une expression GitHub peut vivre dans n importe quelle valeur : `name:`, `env:`, un argument
d action. Restreindre aux `if:` etait une supposition sur l endroit du defaut.

## Ce que le garde laisse passer, et qui compte autant

**L interpolation shell.** Dans `[ "${{ inputs.x }}" = "true" ]`, `inputs.x` est suivi de `}}`,
jamais de `==` : le motif la laisse passer de lui-meme, et un cas de non-regression le garde.

**Une entree de type CHAINE comparee a une chaine**, qui est la forme juste. Sans ce cas, le garde
interdirait du correct et se ferait contourner plutot que corriger.

Le compte des lignes ne retient que celles qui parlent d une entree : « 700 lignes examinees » ne
renseigne personne, et gonfle un chiffre qu on lira comme une couverture.
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
FLUX = "FLUX"

# `inputs.<nom>` compare a une chaine entre apostrophes.
COMPARAISON = re.compile(r"inputs\.([A-Za-z0-9_-]+)\s*[!=]=\s*'([^']*)'")


def ateliers(flux: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les fichiers d atelier que ce garde LIT."""
    base = flux or pathlib.Path(os.environ.get(FLUX, RACINE / ".github" / "workflows"))
    if not base.is_dir():
        return []
    return sorted(list(base.glob("*.yml")) + list(base.glob("*.yaml")))


def _booleennes(contenu: dict) -> set[str]:
    """Les entrees que ce workflow declare booleennes.

    `contenu.get(True)` d abord : YAML interprete la cle `on:` comme le booleen vrai, et la chercher
    sous « on » seul rendrait un dictionnaire vide pour la plupart des ateliers.
    """
    declenchements = contenu.get(True) or contenu.get("on") or {}
    trouves = set()
    if isinstance(declenchements, dict):
        for declencheur in ("workflow_dispatch", "workflow_call"):
            bloc = declenchements.get(declencheur) or {}
            for nom, spec in (bloc.get("inputs") or {}).items():
                if isinstance(spec, dict) and spec.get("type") == "boolean":
                    trouves.add(nom)
    return trouves


def fautives(flux: pathlib.Path | None = None) -> tuple[list[str], int]:
    """Les comparaisons fautives, et le nombre de lignes PARLANT d une entree."""
    import yaml

    trouves, total = [], 0
    for chemin in ateliers(flux):
        contenu = yaml.safe_load(chemin.read_text(encoding="utf-8"))
        if not isinstance(contenu, dict):
            continue
        booleennes = _booleennes(contenu)
        if not booleennes:
            continue
        for numero, ligne in enumerate(chemin.read_text(encoding="utf-8").splitlines(), start=1):
            nue = ligne.strip()
            if "inputs." in nue:
                total += 1
            for nom, valeur in COMPARAISON.findall(nue):
                if nom in booleennes:
                    trouves.append(f"{chemin.name}:{numero} · inputs.{nom} comparé à « {valeur} »")
    return trouves, total


def juge(flux: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    try:
        import yaml  # noqa: F401
    except ImportError:
        print("✗ PyYAML est absent : la garde ne peut rien analyser.")
        return 1
    trouves, total = fautives(flux)
    if trouves:
        print(f"✗ {len(trouves)} comparaison(s) d'une entrée booléenne à une chaîne :")
        for f in trouves:
            print(f"   · {f}")
        print()
        print("  Une entrée `type: boolean` arrive comme un VRAI booléen. La comparer à une chaîne")
        print("  casse les deux en nombres : `== 'true'` vaut TOUJOURS faux, `!= 'true'` TOUJOURS")
        print("  vrai. Écrire `if: ${{ inputs.drapeau }}` ou `if: ${{ !inputs.drapeau }}`.")
        return 1
    print(
        f"✓ Aucune entrée booléenne comparée à une chaîne ({total} ligne(s) parlant d'une entrée)."
    )
    return 0


_EN_TETE_BOOL = """on:
  workflow_dispatch:
    inputs:
      drapeau:
        type: boolean
        default: false
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
"""
EGAL = _EN_TETE_BOOL + "      - if: inputs.drapeau == 'true'\n        run: echo\n"
DIFFERENT = _EN_TETE_BOOL + "      - if: inputs.drapeau != 'true'\n        run: echo\n"
NU = _EN_TETE_BOOL + "      - if: ${{ inputs.drapeau }}\n        run: echo\n"
NIE = _EN_TETE_BOOL + "      - if: ${{ !inputs.drapeau }}\n        run: echo\n"
DANS_UN_NOM = _EN_TETE_BOOL + (
    "      - uses: actions/upload-artifact@0000000000000000000000000000000000000000\n"
    "        with:\n"
    "          name: film-${{ inputs.drapeau == 'true' && 'tout' || 'un' }}\n"
)
INTERPOLATION = _EN_TETE_BOOL + (
    '      - run: if [ "${{ inputs.drapeau }}" = "true" ]; then echo; fi\n'
)
CHAINE = """on:
  workflow_dispatch:
    inputs:
      mode:
        type: string
        default: rapide
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - if: inputs.mode == 'rapide'
        run: echo
"""

CAS = (
    ("rouge", "un booléen comparé à la chaîne « true » est refusé", EGAL),
    ("rouge", "la forme niée est refusée aussi", DIFFERENT),
    ("vert", "le booléen nu passe", NU),
    ("vert", "sa négation passe", NIE),
    # Le cas qui a manque au premier jet : la comparaison peut vivre AILLEURS que dans un `if:`.
    ("rouge", "une comparaison dans un « name: » est refusée", DANS_UN_NOM),
    # Le cas qui empeche la garde de tout refuser : une entree CHAINE se compare a une chaine.
    ("vert", "une entrée de type chaîne se compare à une chaîne", CHAINE),
    # Et celui qui garde l INTERPOLATION, forme juste dans un `run:`.
    ("vert", "l interpolation shell reste permise", INTERPOLATION),
)


def _auto_test() -> int:
    """Les sept cas de la version bash, dont TROIS qui doivent rougir."""
    import tempfile

    echecs = total = 0
    print("AUTO-TEST")
    for attendu, libelle, contenu in CAS:
        total += 1
        with tempfile.TemporaryDirectory(prefix="vc-bool-") as tmp:
            flux = pathlib.Path(tmp) / ".github" / "workflows"
            flux.mkdir(parents=True)
            (flux / "essai.yml").write_text(contenu, encoding="utf-8")
            code = juge(flux)
        obtenu = "vert" if code == 0 else "rouge"
        if obtenu == attendu:
            print(f"  [OK   ] {libelle:<52} -> {obtenu}")
        else:
            print(f"  [ÉCHEC] {libelle:<52} -> {obtenu} (attendu {attendu})")
            echecs += 1

    print()
    print(f"{total} cas, dont 3 qui DOIVENT rougir.")
    if echecs:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce garde.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juge())
