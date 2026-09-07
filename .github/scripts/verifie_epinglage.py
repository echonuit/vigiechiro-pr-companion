#!/usr/bin/env python3
"""Garde d epinglage (#2737, porte du bash en #5231).

Refuse toute action ou tout conteneur designe par un NOM deplacable. Un tag - fut-il aussi precis
que `v5.6.0` - peut etre repointe sur un autre commit sans que rien ne bouge chez nous : ce qui
s execute dans nos workflows changerait alors sans qu aucun commit ne le dise.

Sans cette garde, la prochaine action ajoutee le sera par tag et l epinglage se deferait en silence -
la forme meme du defaut que #2737 corrige.

## Le second controle, et pourquoi il n est pas theorique

Une meme action ne doit pas etre epinglee sur DEUX SHA differents. Pendant le lot 3, une PR
Dependabot a monte `setup-java` de v5.6.0 a v5.7.0 en fusionnant ONZE MINUTES apres un workflow qui
venait d etre ajoute - sa liste de fichiers, calculee avant, ne pouvait pas le connaitre. Le depot
s est retrouve avec neuf occurrences en v5.7.0 et une en v5.6.0, et rien ne l a signale.

## Formes acceptees

    uses: <depot>/<action>@<sha 40 hex>          # <tag>
    uses: docker://<image>@sha256:<64 hex>
    uses: ./.github/actions/<locale>

Le commentaire de version n est pas decoratif : sans lui, plus personne ne sait quelle version
tourne, et Dependabot n a rien a mettre a jour de lisible.

## Elle porte sa propre preuve

`--auto-test` fait passer huit lignes connues - quatre a refuser, quatre a accepter - par les MEMES
regles que le balayage. Les eprouver separement laisserait l auto-test certifier une regle que le
balayage n applique pas.

Usage : python3 .github/scripts/verifie_epinglage.py [--auto-test]
"""

from __future__ import annotations

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
ATELIERS = "*.yml"

SHA40 = re.compile(r"@[0-9a-f]{40}([ \t]|$)")
SHA40_COMMENTE = re.compile(r"@[0-9a-f]{40}[ \t]+#[ \t]*\S")
AUTRES_FORMES = re.compile(r"@sha256:[0-9a-f]{64}|uses:[ \t]*\./")
REFERENCE = re.compile(r"uses: ([^@ ]+)@([0-9a-f]{40})")
ACTION = re.compile(r"uses: [^@ ]+@")

# LE TROISIEME CONTROLE : la version que l action INSTALLE (issue #5401).
#
# Les deux premiers tiennent ce qui S EXECUTE. Celui-ci tient ce qui est INSTALLE, et c est le meme
# defaut vu un cran plus bas : `python-version: "3.x"` fait prendre la derniere version stable, si
# bien que ce que la CI execute change sans qu aucun commit ne le dise - la phrase meme de #2737.
#
# **Pourquoi le niveau MINEUR et pas le correctif.** Un correctif de Python ne casse pas une roue
# binaire ; une version mineure, si. Les roues sont publiees PAR version mineure, et le delai varie :
# mesure le 2026-09-07, apres la sortie de Python 3.14, PyYAML avait publie 12 jours AVANT et
# `tree-sitter` deux mois avant, quand `jpype1` a mis 184 jours. `java-version: 25` et
# `node-version: 24` ne flottent donc qu au correctif et restent hors de ce controle.
VERSION_INSTALLEE = re.compile(r"^\s*(python-version):\s*[\"']?([^\"'\n#]+)")
FLOTTANTE = re.compile(r"^\d+\.x$|^\d+$|latest|^\*$")


def ligne_acceptable(contenu: str) -> bool:
    """Vraie si la ligne est acceptable. Partagee par le balayage et l auto-test."""
    if contenu.lstrip().startswith("#"):
        return True
    if SHA40.search(contenu):
        return bool(SHA40_COMMENTE.search(contenu))
    return bool(AUTRES_FORMES.search(contenu))


def ateliers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    base = (racine or RACINE) / ".github" / "workflows"
    return sorted(base.glob(ATELIERS)) if base.is_dir() else []


def juger(racine: pathlib.Path | None = None) -> int:
    """Les deux controles, dans l ordre, et le code de sortie qui va avec."""
    base = racine or RACINE
    problemes = 0
    for atelier in ateliers(base):
        rel = atelier.relative_to(base).as_posix()
        for numero, contenu in enumerate(atelier.read_text(encoding="utf-8").splitlines(), start=1):
            if "uses:" not in contenu or ligne_acceptable(contenu):
                continue
            if SHA40.search(contenu):
                print(
                    f"❌ {rel}:{numero} : SHA épinglé sans commentaire de version : {contenu.lstrip(' ')}"
                )
            else:
                print(f"❌ {rel}:{numero} : référence non figée : {contenu.lstrip(' ')}")
            problemes += 1

    for atelier in ateliers(base):
        rel = atelier.relative_to(base).as_posix()
        for numero, contenu in enumerate(atelier.read_text(encoding="utf-8").splitlines(), start=1):
            trouve = VERSION_INSTALLEE.match(contenu)
            if version_flottante(contenu):
                print(
                    f"❌ {rel}:{numero} : version d outil flottante : "
                    f"{trouve.group(1)}: {trouve.group(2).strip()}"
                )
                problemes += 1

    if problemes > 0:
        print()
        print(f"Garde épinglage : {problemes} problème(s).")
        print('Une version d outil se fige au MINEUR : `python-version: "3.14"`, jamais `"3.x"`.')
        print("Résoudre le tag en SHA :")
        print("  gh api repos/<proprietaire>/<action>/git/ref/tags/<tag> --jq .object.sha")
        print(
            "(si l'objet est de type « tag », déréférencer : gh api repos/…/git/tags/<sha> --jq .object.sha)"
        )
        print("Puis écrire : uses: <proprietaire>/<action>@<sha>  # <tag>")
        return 1

    # Deux SHA pour une meme action : presque toujours une montee de version incomplete.
    epinglages: dict[str, set[str]] = {}
    for atelier in ateliers(base):
        for action, sha in REFERENCE.findall(atelier.read_text(encoding="utf-8")):
            epinglages.setdefault(action, set()).add(sha)
    divergences = sorted(a for a, shas in epinglages.items() if len(shas) > 1)

    if divergences:
        print("❌ Une même action est épinglée sur DEUX SHA différents :")
        for action in divergences:
            print(f"   {action}")
            for atelier in ateliers(base):
                rel = atelier.relative_to(base).as_posix()
                for numero, ligne in enumerate(
                    atelier.read_text(encoding="utf-8").splitlines(), start=1
                ):
                    if f"uses: {action}@" in ligne:
                        print(f"      {rel}:{numero}:{ligne}")
        print()
        print(
            "Presque toujours une montée de version incomplète : un fichier ajouté pendant qu'une PR de"
        )
        print(
            "mise à jour était ouverte n'a pas pu être repris par elle. Aligner sur la version la plus"
        )
        print("récente, ou dire en commentaire pourquoi la divergence est voulue.")
        return 1

    total = sum(
        1
        for atelier in ateliers(base)
        for ligne in atelier.read_text(encoding="utf-8").splitlines()
        if "uses:" in ligne
    )
    distinctes = len(
        {
            m
            for atelier in ateliers(base)
            for m in ACTION.findall(atelier.read_text(encoding="utf-8"))
        }
    )
    print(
        f"Garde épinglage : OK ({total} référence(s) figées, {distinctes} action(s) distincte(s), "
        "aucune divergence de version)."
    )
    return 0


SHA_ESSAI = "0123456789abcdef0123456789abcdef01234567"
A_REFUSER = (
    "      - uses: actions/checkout@v7",
    "      - uses: actions/setup-java@v5.7.0",
    f"      - uses: actions/checkout@{SHA_ESSAI}",
    "        uses: docker://ghcr.io/flathub/flatpak-external-data-checker:latest",
)
A_ACCEPTER = (
    f"      - uses: actions/checkout@{SHA_ESSAI} # v7",
    "        uses: docker://ghcr.io/flathub/exemple@sha256:" + "0" * 64,
    "      - uses: ./.github/actions/locale",
    "      # uses: actions/checkout@v7 (mention en commentaire)",
)


# Les versions d outil, jugees par la MEME paire de motifs que le balayage. Elles sont a part de
# `A_REFUSER` et `A_ACCEPTER`, qui portent des lignes `uses:` : melanger les deux ferait passer une
# ligne de version dans `ligne_acceptable`, qui ne la juge pas et la dirait donc correcte.
VERSIONS_A_REFUSER = (
    '          python-version: "3.x"',
    "          python-version: 3",
    '          python-version: "latest"',
)
VERSIONS_A_ACCEPTER = (
    '          python-version: "3.14"',
    '          python-version: "3.12.7"',
    # Une ligne qui ne porte pas de version d outil ne concerne pas ce controle.
    "      - uses: actions/checkout@0123456789abcdef0123456789abcdef01234567  # v7",
)


def version_flottante(ligne: str) -> bool:
    """Cette ligne declare-t-elle une version d outil qui bouge ? La regle du troisieme controle."""
    trouve = VERSION_INSTALLEE.match(ligne)
    return bool(trouve and FLOTTANTE.search(trouve.group(2).strip()))


def _auto_test() -> int:
    """Les lignes connues, par les MEMES regles que le balayage."""
    echecs = 0
    for ligne in VERSIONS_A_REFUSER:
        if not version_flottante(ligne):
            print(f"❌ autotest : version flottante ACCEPTÉE -> {ligne.strip()}")
            echecs += 1
    for ligne in VERSIONS_A_ACCEPTER:
        if version_flottante(ligne):
            print(f"❌ autotest : version figée REFUSÉE -> {ligne.strip()}")
            echecs += 1
    for ligne in A_REFUSER:
        if ligne_acceptable(ligne):
            print(f"❌ autotest : référence non figée ACCEPTÉE -> {ligne}")
            echecs += 1
    for ligne in A_ACCEPTER:
        if not ligne_acceptable(ligne):
            print(f"❌ autotest : référence correcte REFUSÉE -> {ligne}")
            echecs += 1
    if echecs > 0:
        print(
            f"Autotest de la garde épinglage : {echecs} échec(s). Les règles ne font plus ce qu'elles promettent."
        )
        return 1
    print(
        f"Autotest de la garde épinglage : OK ({len(A_REFUSER) + len(VERSIONS_A_REFUSER)} refusées, "
        f"{len(A_ACCEPTER) + len(VERSIONS_A_ACCEPTER)} acceptées)."
    )
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(juger())
