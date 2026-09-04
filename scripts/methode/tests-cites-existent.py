#!/usr/bin/env python3
"""Un test cite par une page de methode existe, sinon la commande rend vert sans rien faire (#4745).

`mvn test -Dtest=Classe#methode` rend `BUILD SUCCESS` et `Tests run: 0` quand la methode n existe
pas. Une page qui donne un tel exemple ne desinforme pas seulement : elle met en main une commande
VERTE SANS AVOIR JUGE. Le piege se referme au pire moment, puisqu on cible une methode precisement
quand on doute d elle.

Vecu le 2026-08-29 : `TESTING.md` donnait `-Dtest=SitesViewModelTest#chargeLesSites`, methode
absente depuis on ne sait quand, pendant que `dev-docs/tests-et-qualite.md` citait deux pages plus
loin la forme juste.

**Le motif a ete EPROUVE avant d etre cru.** `dev-docs/ci-cd-release.md` ecrit « a un meme
`-Dtest=A,B,C` » : un gabarit de prose, dont un premier motif tirait une classe `A` introuvable.
La regle qui l ecarte est DERIVEE et non enumeree - une citation ne compte que si elle nomme quelque
chose finissant par `Test`, et la convention la porte : 791 classes de test sur 791, les autres
fichiers de `src/test` etant des aides.

Usage :
    python3 scripts/methode/tests-cites-existent.py
    python3 scripts/methode/tests-cites-existent.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

TESTS = RACINE / "src" / "test"

CITATION = re.compile(r"-Dtest=([A-Za-z0-9_$.]+(?:#[A-Za-z0-9_$]+)?)")
METHODE = "void {}("


def pages() -> list[pathlib.Path]:
    """Tout ce qui peut prescrire une commande : la methode, la doc, les competences, les ateliers."""
    vues = []
    for nom in ("AGENTS.md", "CONTRIBUTING.md", "CONSTITUTION.md", "TESTING.md", "README.md"):
        f = RACINE / nom
        if f.exists():
            vues.append(f)
    vues += sorted((RACINE / "dev-docs").rglob("*.md"))
    vues += sorted((RACINE / ".agents" / "skills").glob("*/SKILL.md"))
    vues += sorted((RACINE / ".github").rglob("*.yml"))
    return vues


def est_une_citation(spec: str) -> bool:
    """Un gabarit de prose n est pas une prescription. La convention tranche : ca finit par `Test`."""
    return spec.split("#")[0].split(".")[-1].endswith("Test")


def fichier_de(classe: str) -> pathlib.Path | None:
    for f in TESTS.rglob(classe + ".java"):
        return f
    return None


def suspects() -> list[str]:
    """Les citations qui nomment une classe ou une methode absente de `src/test`."""
    fautes = []
    for page in pages():
        try:
            texte = page.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in CITATION.finditer(texte):
            spec = m.group(1)
            if not est_une_citation(spec):
                continue
            classe, _, methode = spec.partition("#")
            f = fichier_de(classe.split(".")[-1])
            ou = page.relative_to(RACINE)
            if f is None:
                fautes.append(f"{ou} : la classe de `-Dtest={spec}` est absente de src/test")
            elif methode and METHODE.format(methode) not in f.read_text(
                encoding="utf-8", errors="replace"
            ):
                fautes.append(f"{ou} : `-Dtest={spec}` nomme une methode absente de {f.name}")
    return sorted(set(fautes))


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    verifie("une classe de test se reconnait", est_une_citation("SitesViewModelTest"), True)
    verifie("avec sa methode aussi", est_une_citation("SitesViewModelTest#charger"), True)
    verifie("un gabarit de prose est ecarte", est_une_citation("A"), False)
    verifie("un gabarit a plusieurs lettres aussi", est_une_citation("B"), False)
    verifie("un nom pleinement qualifie se lit", est_une_citation("fr.univ_amu.AppTest"), True)
    # Le sens NEGATIF : sans ce cas, un motif qui n accepterait jamais rien passerait les trois
    # premiers, et le garde rendrait vert sur un depot casse.
    verifie("une classe reellement citee est trouvee", fichier_de("AppTest") is not None, True)
    verifie("une classe inventee ne l est pas", fichier_de("ClasseQuiNExistePasTest"), None)
    return echecs


CONTRAT = {
    "geste": "test cite par une page de methode et qui n existe pas",
    "population": "les competences de .agents/skills et leur copie de .claude/skills, et les pages de dev-docs",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/tests-cites-existent.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    fautes = suspects()
    for f in fautes:
        print(f"ÉCHEC : {f}", file=sys.stderr)
    if fautes:
        print(
            "\n`mvn test -Dtest=…` rend `BUILD SUCCESS` et `Tests run: 0` quand la cible n'existe\n"
            "pas. Un exemple faux ne désinforme pas seulement : il met en main une commande verte\n"
            "sans avoir jugé, au moment précis où l'on doute d'une méthode.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    print("Les tests cités par les pages de méthode existent tous.")
    raise SystemExit(0)
