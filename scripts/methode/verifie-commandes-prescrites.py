#!/usr/bin/env python3
"""Une commande prescrite nomme quelque part ce qui l INSTALLE (issue #4849).

`AGENTS.md`, `CONTRIBUTING.md` et six competences prescrivent `graphify`. Aucune ne nomme
`graphifyy`, la distribution qui l installe. Ce n est pas un detail de forme :

    pip install graphify   -> n existe pas
    npm i -g graphify      -> installe un AUTRE paquet, un generateur de graphes aleatoires
                              publie par un tiers

Le lecteur qui suit la methode n obtient donc pas une erreur, il obtient le mauvais outil. C est le
piege que `pyproject.toml` documente deja pour `PyYAML` / `yaml`, en plus couteux.

**Ce que ce garde refuse.** Une distribution declaree dans `[tool.vigiechiro.commandes]` dont le nom
ne parait dans AUCUNE surface de methode. Le depot prescrit alors un outil sans jamais dire ce qui
le pose.

**Ce qu il ne refuse PAS, et pourquoi.** Que chaque mention de la commande nomme la distribution a
cote. Vingt fichiers citent `graphify` en passant - « voir graphify », « le graphe » - et exiger la
distribution partout rendrait la methode illisible pour un gain nul. Il suffit qu elle soit nommee
**quelque part** ou l on cherche comment installer.

**Et il refuse l inverse** : une distribution declaree que rien ne prescrit. La declaration serait
morte, ce que `pyproject.toml` refuse deja pour les dependances - « ne porte que ce qui est UTILISE ».

Usage :
    python3 scripts/methode/verifie-commandes-prescrites.py
    python3 scripts/methode/verifie-commandes-prescrites.py --auto-test
"""

import pathlib
import re
import sys
import tomllib

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

DECLARATION = "pyproject.toml"

# Les surfaces ou un lecteur cherche comment installer. `CHANGELOG.md` en est exclu : il raconte ce
# qui a ete fait, il ne prescrit rien.
SURFACES = (
    "AGENTS.md",
    "CONTRIBUTING.md",
    "CLAUDE.md",
    "README.md",
    "dev-docs",
    ".agents/skills",
)

CONTRAT = {
    "geste": "commande prescrite dont la distribution n est nommee nulle part",
    "population": "les distributions de [tool.vigiechiro.commandes]",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-commandes-prescrites.py --auto-test",
    "decision": "hygiene, sans decision",
    "chemins": """
pyproject.toml
AGENTS.md
CONTRIBUTING.md
CLAUDE.md
README.md
dev-docs/**
.agents/skills/**
""",
}


def declarees(racine: pathlib.Path | None = None) -> dict[str, list[str]]:
    """Les distributions declarees et les commandes qu elles posent."""
    fichier = (racine or RACINE) / DECLARATION
    if not fichier.exists():
        return {}
    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return {}
    table = ((donnees.get("tool") or {}).get("vigiechiro") or {}).get("commandes") or {}
    return {d: [c for c in cs if isinstance(c, str)] for d, cs in table.items()}


def prose(racine: pathlib.Path | None = None) -> str:
    """Le texte de toutes les surfaces de methode, concatene une fois."""
    base = racine or RACINE
    morceaux = []
    for surface in SURFACES:
        chemin = base / surface
        if chemin.is_dir():
            morceaux.extend(
                f.read_text(encoding="utf-8", errors="replace") for f in chemin.rglob("*.md")
            )
        elif chemin.is_file():
            morceaux.append(chemin.read_text(encoding="utf-8", errors="replace"))
    return "\n".join(morceaux)


def nomme(texte: str, mot: str) -> bool:
    """Le mot ENTIER parait-il ? Une sous-chaine ne suffit pas, et c est tout le sujet.

    `graphify` est une sous-chaine de `graphifyy` : un garde qui cherche l un trouverait l autre, et
    declarerait nommee une distribution que personne n a ecrite. Le defaut s est produit sur ce garde
    meme, sur son cas d auto-test - `outil` dans `outily` - avant d etre corrige.
    """
    return re.search(rf"\b{re.escape(mot)}\b", texte) is not None


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les distributions muettes, et les declarations mortes."""
    texte = prose(racine)
    trouves = []
    for distribution, commandes in sorted(declarees(racine).items()):
        if not nomme(texte, distribution):
            trouves.append(
                f"{distribution} : declaree, mais son nom ne parait dans aucune surface de methode"
            )
            continue
        if not any(nomme(texte, c) for c in commandes):
            trouves.append(
                f"{distribution} : declaree et nommee, mais aucune de ses commandes n est prescrite"
            )
    return trouves


def lus(racine: pathlib.Path | None = None) -> int:
    """Ce que le garde a REGARDE : les distributions declarees."""
    return len(declarees(racine))


def _auto_test() -> int:
    import tempfile

    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    def arbre(toml: str, agents: str) -> pathlib.Path:
        bac = pathlib.Path(tempfile.mkdtemp())
        (bac / "pyproject.toml").write_text(toml, encoding="utf-8")
        (bac / "AGENTS.md").write_text(agents, encoding="utf-8")
        return bac

    declare = '[tool.vigiechiro.commandes]\noutily = ["outil"]\n'

    # Le cas NOMINAL : la distribution est nommee, sa commande est prescrite.
    bon = arbre(declare, "Installer par `pip install outily`, puis lancer `outil query`.\n")
    verifie("une distribution nommee et prescrite ne compte pas", suspects(bon), [])

    # Le DEFAUT que ce garde existe pour trouver : la commande est prescrite, la distribution muette.
    muet = arbre(declare, "Lancer `outil query` au commencement de chaque issue.\n")
    verifie("une distribution MUETTE est vue", len(suspects(muet)), 1)
    verifie("et le message dit laquelle", "outily" in suspects(muet)[0], True)

    # L INVERSE : declaree et nommee, mais aucune commande prescrite. La declaration est morte.
    morte = arbre(declare, "Le paquet outily existe.\n")
    verifie("une declaration morte est vue", len(suspects(morte)), 1)

    # Le sens NEGATIF, sans quoi un garde qui accuse tout passerait les cas ci-dessus.
    verifie("sans declaration, rien n est suspect", suspects(arbre("", "outil query\n")), [])
    verifie("le garde a lu des declarations reelles", lus() > 0, True)

    print()
    return echecs


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    trouves = suspects()
    print("Une commande prescrite nomme ce qui l installe")
    for ligne in trouves:
        print(f"  {ligne}")
    print()
    print(
        f"COMMANDES | lus={lus()} | suspects={len(trouves)} | verdict={'ok' if not trouves else 'refus'}"
    )
    raise SystemExit(1 if trouves else 0)
