#!/usr/bin/env python3
"""Engendre et garde la matrice des heuristiques d'ergonomie (article A29).

Même idée que la matrice de la constitution : un vocabulaire seul reste une liste bien intentionnée ;
ce qui le rend utile, c'est une matrice qui relie chaque heuristique aux décisions qui la servent. La
ligne qui vaut le déplacement est celle qui rend **zéro** : elle nomme ce dont personne n'a décidé.

Elle annonce **deux nombres**, et pas un : les *rattachements* et les *ADR*. Une décision peut servir
plusieurs heuristiques, ce qui découple les deux ; les confondre ferait croire à une couverture qui
n'existe pas - trente rattachements portés par douze décisions ne valent pas trente décisions.

Elle est **engendrée**, jamais saisie. `--verifie` refuse une matrice périmée : c'est ce refus qui
empêche l'annexe de mentir sur elle-même.

Aucune dépendance hors stdlib : `lint.yml` n'installe rien.
"""

import argparse
import pathlib
import re
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from verifie_okf import RESERVES, lit_entete  # noqa: E402

ANNEXE = RACINE / "dev-docs" / "ergonomie" / "heuristiques.md"
DECISIONS = RACINE / "dev-docs" / "decisions"

DEBUT = "<!-- matrice engendree : ne pas editer a la main -->"
FIN = "<!-- fin de la matrice engendree -->"

# Une entrée du vocabulaire : sa clé, puis son nom. Même expression que celle du garde, sur la même
# annexe - le vocabulaire vit à un seul endroit.
ENTREE = re.compile(r"^\| `([a-z0-9-]+)` \| ([^|]+?) \|", re.M)


def vocabulaire(annexe: pathlib.Path = None) -> list[tuple[str, str]]:
    """Le vocabulaire clos, dans l'ordre de l'annexe : (clé, nom)."""
    texte = (annexe or ANNEXE).read_text(encoding="utf-8")
    # La matrice engendrée porte elle aussi des clés entre accents graves : on lit AVANT elle,
    # sans quoi chaque exécution compterait ses propres lignes comme du vocabulaire.
    if DEBUT in texte:
        texte = texte[: texte.index(DEBUT)]
    return ENTREE.findall(texte)


def recense(decisions: pathlib.Path = None, annexe: pathlib.Path = None) -> dict:
    """Ce que chaque heuristique sert, et les deux totaux."""
    decisions = decisions or DECISIONS
    par_cle: dict[str, list[str]] = {cle: [] for cle, _ in vocabulaire(annexe)}
    rattachements = 0
    porteuses = set()
    for f in sorted(decisions.glob("*.md")):
        if f.name in RESERVES:
            continue
        try:
            entete = lit_entete(f.read_text(encoding="utf-8"))
        except ValueError:
            continue
        cles = entete.get("heuristiques") or []
        if not isinstance(cles, list):
            continue
        for cle in cles:
            rattachements += 1
            porteuses.add(f.name)
            if cle in par_cle:
                par_cle[cle].append(f.name[:-3])
    return {"par_cle": par_cle, "rattachements": rattachements, "adr": len(porteuses)}


def rend(releve: dict, annexe: pathlib.Path = None) -> str:
    """La section de matrice, telle qu'elle doit figurer dans l'annexe."""
    noms = dict(vocabulaire(annexe))
    par_cle = releve["par_cle"]
    sortie = [DEBUT, "", "## Matrice : ce que chaque heuristique sert", "",
              "Engendrée depuis les en-têtes des ADR par `scripts/methode/matrice-ergonomie.py`, "
              "et gardée par lui.", "",
              f"**{releve['rattachements']} rattachement(s), portés par {releve['adr']} décision(s).** "
              "Les deux nombres diffèrent dès qu'une décision sert plusieurs heuristiques : c'est le "
              "cas ordinaire, et les confondre ferait croire à une couverture qui n'existe pas.", "",
              "| Clé | Heuristique | ADR | Lesquelles |", "|---|---|---:|---|"]
    vides = []
    for cle, servantes in par_cle.items():
        if servantes:
            montrees = ", ".join(f"[{s}](../decisions/{s}.md)" for s in servantes[:3])
            if len(servantes) > 3:
                reste = len(servantes) - 3
                montrees += f", et {reste} autre" + ("s" if reste > 1 else "")
        else:
            montrees = "**aucune**"
            vides.append(cle)
        sortie.append(f"| `{cle}` | {noms.get(cle, '?')} | {len(servantes)} | {montrees} |")
    sortie += ["", f"**{len(vides)} heuristique(s) sur {len(par_cle)} que rien ne sert.** Ce n'est "
               "pas une faute : c'est ce dont personne n'a eu à décider, et il faut le voir pour "
               "savoir si c'est un choix ou un angle mort.", ""]
    if vides:
        sortie += [f"- `{c}` · {noms.get(c, '?')}" for c in vides] + [""]
    sortie.append(FIN)
    return "\n".join(sortie)


def remplace(texte: str, matrice: str) -> str:
    """Le document, sa matrice remplacée ou ajoutée en fin."""
    if DEBUT in texte and FIN in texte:
        return texte[: texte.index(DEBUT)] + matrice + texte[texte.index(FIN) + len(FIN):]
    return texte.rstrip("\n") + "\n\n---\n\n" + matrice + "\n"


def _auto_test() -> int:
    cas = []
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        decisions = racine / "decisions"
        decisions.mkdir()
        annexe = racine / "annexe.md"
        annexe.write_text("| `nielsen-1` | Un |\n| `nielsen-10` | Deux |\n| `affordance` | Trois |\n",
                          encoding="utf-8")
        tete = '---\ntype: adr\nheuristiques: {}\n---\n\n# Un\n'
        (decisions / "a.md").write_text(tete.format('["nielsen-1", "affordance"]'), encoding="utf-8")
        (decisions / "b.md").write_text(tete.format('["nielsen-1"]'), encoding="utf-8")

        r = recense(decisions, annexe)
        cas.append(("trois rattachements", r["rattachements"] == 3))
        # LE cas qui justifie les deux nombres : trois rattachements pour deux decisions.
        cas.append(("portés par deux décisions", r["adr"] == 2))
        cas.append(("nielsen-1 est servie deux fois", r["par_cle"]["nielsen-1"] == ["a", "b"]))
        # `nielsen-10` contient `nielsen-1` : un comptage par sous-chaine la dirait servie.
        cas.append(("nielsen-10 n'est servie par rien", r["par_cle"]["nielsen-10"] == []))

        texte = annexe.read_text(encoding="utf-8")
        une = remplace(texte, rend(r, annexe))
        annexe.write_text(une, encoding="utf-8")
        # Deuxieme passe : la matrice engendree porte des cles entre accents graves. Si elle se
        # relisait, le vocabulaire doublerait a chaque execution.
        deux = remplace(une, rend(recense(decisions, annexe), annexe))
        cas.append(("engendrer deux fois rend le même texte", une == deux))
        cas.append(("le vocabulaire ne se relit pas lui-même", len(vocabulaire(annexe)) == 3))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : la matrice ne dit pas ce qu'elle annonce.", file=sys.stderr)
        return 1
    print("\nAuto-test concluant : la matrice compte ses deux nombres, et ne se relit pas.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Matrice des heuristiques d'ergonomie")
    p.add_argument("--verifie", action="store_true", help="refuse une matrice périmée")
    p.add_argument("--auto-test", action="store_true", help="éprouve le garde sur des fixtures")
    args = p.parse_args()

    if args.auto_test:
        return _auto_test()

    texte = ANNEXE.read_text(encoding="utf-8")
    attendu = remplace(texte, rend(recense()))
    if args.verifie:
        if texte != attendu:
            print("La matrice des heuristiques est périmée.\n"
                  "Relancez : python3 scripts/methode/matrice-ergonomie.py", file=sys.stderr)
            return 1
        print("Matrice des heuristiques à jour.")
        return 0
    ANNEXE.write_text(attendu, encoding="utf-8")
    print("Matrice des heuristiques engendrée.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
