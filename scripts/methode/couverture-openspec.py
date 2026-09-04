#!/usr/bin/env python3
"""Loupe : ce que la spécification vivante couvre du produit, et ce qu'elle ne couvre pas encore.

**Elle ne juge pas.** Elle sort toujours 0, comme ses quatre sœurs de ce dossier. Ce qui refuse est
le cliquet d'[ADR 4922], `.github/scripts/verifie_specification_consignee.py`, et il compte un
déficit et non une couverture.

## Pourquoi des repères, et jamais un total

Afficher « N capacités sur M » exigerait un dénominateur, et le dépôt en offre cinq incompatibles :
écrans, commandes, cas de recette, EPIC, services de domaine. Choisir entre eux revient à décider ce
qu'est une capacité du produit, ce qui est un chantier à soi seul. Les grandeurs affichées ici sont
donc nommées **repères** : elles donnent l'ordre de grandeur du produit, elles ne prétendent pas
mesurer ce qui reste à faire.

## Ce qu'elle ne peut pas lire, et le dit

Elle ne sait pas quels paquets de fonctionnalité portent une capacité métier au sens d'[ADR 0014].
`commun`, `cli` ou `diagnostic` n'en portent probablement aucune, et elle n'a aucun moyen de le
trancher. Elle les liste donc tous en le déclarant, plutôt que d'appliquer une exclusion que
personne n'a écrite. C'est l'article A3.

    --auto-test : éprouver le comptage sur un arbre jetable, et sortir 1 s'il compte faux.
"""

import pathlib
import re
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(
    subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=False
    ).stdout.strip()
    or "."
)
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

EXIGENCE = re.compile(r"^### Requirement:", re.MULTILINE)
COMMANDE = re.compile(r'name\s*=\s*"([a-z][a-z0-9-]*)"')


def capacites(racine: pathlib.Path) -> list[tuple[str, int]]:
    """Les capacités spécifiées, nommées `<paquet>/<geste>`, avec leur compte d'exigences.

    Les delta specs sous `changes/` sont écartées : une capacité archivée est déjà fusionnée dans
    `specs/`, et la compter deux fois doublerait le numérateur sans que rien ne le montre.
    """
    base = racine / "openspec" / "specs"
    trouvees = []
    for spec in sorted(base.rglob("spec.md")):
        nom = str(spec.parent.relative_to(base))
        trouvees.append((nom, len(EXIGENCE.findall(spec.read_text(encoding="utf-8")))))
    return trouvees


def racine_des_paquets(racine: pathlib.Path) -> pathlib.Path:
    """La racine des paquets de fonctionnalite, declaree ICI et nulle part ailleurs (ADR 4586).

    Elle etait ecrite trois fois. Deux ecritures qui divergent feraient compter les paquets dans un
    arbre et les services dans un autre, et la loupe rendrait un rapport coherent sur deux realites
    differentes. Trouve a la passe 1 de la cloture de #4511.
    """
    return racine / "src" / "main" / "java" / "fr" / "univ_amu" / "iut"


def paquets(racine: pathlib.Path) -> list[str]:
    base = racine_des_paquets(racine)
    return sorted(d.name for d in base.iterdir() if d.is_dir()) if base.is_dir() else []


def reperes(racine: pathlib.Path) -> list[tuple[str, int]]:
    ecrans = racine / "docs" / "ecrans"
    modele = racine_des_paquets(racine)
    cli = modele / "cli" / "commande"

    nb_ecrans = len([f for f in ecrans.glob("*.md") if f.stem != "index"]) if ecrans.is_dir() else 0
    noms = set()
    if cli.is_dir():
        for f in cli.rglob("*.java"):
            noms.update(COMMANDE.findall(f.read_text(encoding="utf-8")))
    nb_services = len(list(modele.glob("*/model/Service*.java"))) if modele.is_dir() else 0
    return [
        ("écrans documentés", nb_ecrans),
        ("commandes", len(noms)),
        ("services de domaine", nb_services),
    ]


def rapporte(racine: pathlib.Path) -> None:
    trouvees = capacites(racine)
    total = sum(n for _, n in trouvees)
    print(f"LOUPE OPENSPEC | {len(trouvees)} capacité(s) spécifiée(s), {total} exigence(s)")
    for nom, n in trouvees:
        print(f"  · {nom} ({n} exigence(s))")

    print("  repères du produit, qui ne sont PAS un dénominateur :")
    print("      " + ", ".join(f"{n} {libelle}" for libelle, n in reperes(racine)))

    couverts = {nom.split("/", 1)[0] for nom, _ in trouvees}
    tous = paquets(racine)
    manquants = [p for p in tous if p not in couverts]
    if tous:
        print(f"  paquets sans capacité spécifiée : {len(manquants)} sur {len(tous)}")
        print("      " + ", ".join(manquants))
        print("  cette loupe ne sait pas lesquels portent une capacité métier au sens d'ADR 0014")
        print("  et n'en exclut donc aucun : la liste est un point de départ, pas une dette.")


def auto_test() -> int:
    echecs = 0

    def joue(libelle: str, obtenu, attendu) -> None:
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac)
        spec = r / "openspec" / "specs" / "passage" / "emport-d-une-nuit"
        spec.mkdir(parents=True)
        (spec / "spec.md").write_text(
            "# x\n## Requirements\n### Requirement: A\n### Requirement: B\n", encoding="utf-8"
        )
        joue(
            "une capacité et ses deux exigences se comptent",
            capacites(r),
            [("passage/emport-d-une-nuit", 2)],
        )

        # Le cas qui compte : une delta spec archivée ne doit pas doubler le numérateur.
        delta = (
            r
            / "openspec"
            / "changes"
            / "archive"
            / "2026-01-01-x"
            / "specs"
            / "passage"
            / "emport-d-une-nuit"
        )
        delta.mkdir(parents=True)
        (delta / "spec.md").write_text("### Requirement: A\n", encoding="utf-8")
        joue(
            "une delta ARCHIVÉE ne se compte pas deux fois",
            capacites(r),
            [("passage/emport-d-une-nuit", 2)],
        )

        src = racine_des_paquets(r)
        (src / "passage").mkdir(parents=True)
        (src / "commun").mkdir(parents=True)
        joue("les paquets se lisent depuis l'arbre", paquets(r), ["commun", "passage"])

        (src / "cli" / "commande").mkdir(parents=True)
        (src / "cli" / "commande" / "C.java").write_text(
            'name = "emporter"\nname = "emporter"\n', encoding="utf-8"
        )
        joue("une commande citée deux fois ne compte qu'une", dict(reperes(r))["commandes"], 1)

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


# Pourquoi `loupe` : elle met sous les yeux ce que la specification couvre et ce qu elle ne couvre
# pas, sans jamais bloquer. C est la definition meme de la loupe (ADR 2465) : elle rend une surface
# de revue, et classer une lacune en infraction pretendrait a une certitude qu aucun compte ne donne.
CONTRAT = {
    "geste": "ce que la specification vivante couvre du produit, et ce qu elle ne couvre pas",
    "population": "les specs de .github/openspec, confrontees aux ecrans et services du produit",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/couverture-openspec.py --auto-test",
    "decision": "ADR 0014",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())
    rapporte(RACINE)
