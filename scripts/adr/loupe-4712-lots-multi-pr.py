#!/usr/bin/env python3
"""Loupe de l'ADR 4712 : quels lots d'EPIC auraient du s'ouvrir en sous-chantier.

Un lot est de la PROSE dans le corps d'un EPIC. Rien ne dit, de facon lisible par une machine,
combien de PR il portera : c'est un jugement, et c'est pourquoi l'ADR 4712 se verifie en `humaine`.

Ce que cette loupe fait, et c'est tout ce qu'elle pretend : elle POSE la question au bon moment, en
mettant sous les yeux, pour chaque EPIC ouvert, ses lots et les issues qui lui pendent. Le lecteur
tranche. Elle ne compte pas de suspects et ne porte pas de cliquet.

## Deux mesures qui ont ecarte un garde mecanique

Un premier dessin comptait les lots citant plus d'une issue. Mesure du 2026-08-29 : ZERO sur les dix
EPIC ouverts, parce que la forme courante est un lot en prose SANS reference, les issues se
rattachant a l'EPIC par ailleurs. Le signal n'existe pas.

Un second dessin comptait les issues rattachees par `gh issue list --search`. La recherche plein
texte de la forge n'honore pas les phrases exactes : un EPIC sans aucune issue rattachee revenait
avec un resultat, lui-meme. Un cliquet bati la-dessus aurait rougi au hasard.

D'ou la lecture EXACTE ici : on rapatrie les corps une fois, et on cherche la chaine en local.

## Hors ligne, elle le dit

Sans `gh`, elle ne rend pas un rapport vide - qui se lirait comme « aucun lot suspect ». Elle sort en
2 et le dit, conformement a l'ADR 2748 : un dispositif qui peut ne rien verifier le declare.

Usage : loupe-4712-lots-multi-pr.py [--auto-test]
"""

from __future__ import annotations

import json
import pathlib
import re
import shutil
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import loupe  # noqa: E402

LOT = re.compile(r"^- \[[ x]\] \*\*Lot[^\n]*(?:\n(?:    |\t)[^\n]*)*", re.M)
RATTACHEMENT = "Fait partie de #"


def lots(corps: str) -> list[str]:
    """Les lignes de lot d'un corps d'EPIC, lignes de continuation recollees."""
    return [" ".join(bloc.split()) for bloc in LOT.findall(corps or "")]


def rattachees(numero: int, issues: list[dict]) -> list[dict]:
    """Les issues dont le corps porte EXACTEMENT « Fait partie de #<numero> »."""
    marque = f"{RATTACHEMENT}{numero}"
    return [
        i
        for i in issues
        if i["number"] != numero and re.search(rf"{re.escape(marque)}(?!\d)", i.get("body") or "")
    ]


def estEpic(issue: dict) -> bool:
    return any(e.get("name") == "epic" for e in issue.get("labels") or [])


def _issues() -> list[dict]:
    if not shutil.which("gh"):
        print("REFUS : « gh » est absent. Cette loupe ne conclut pas sur ce qu'elle n'a pas lu.", file=sys.stderr)
        raise SystemExit(2)
    sortie = subprocess.run(
        ["gh", "issue", "list", "--state", "open", "--limit", "800", "--json", "number,title,body,labels"],
        capture_output=True,
        text=True,
        check=False,
    )
    if sortie.returncode != 0:
        print("REFUS : la forge n'a pas repondu.", file=sys.stderr)
        raise SystemExit(2)
    return json.loads(sortie.stdout)


def rapport(issues: list[dict]) -> list[str]:
    """Un candidat par LOT, chacun se lisant seul (#4758).

    La surface de revue est le lot, pas l EPIC : la loupe demande « pour chaque lot, combien de
    PR ? », et plusieurs EPIC n en portent aucun. Chaque ligne nomme donc son EPIC, comme les
    quatre autres loupes du depot dont chaque candidat se lit sans son voisin.
    """
    lignes: list[str] = []
    for epic in sorted((i for i in issues if estEpic(i)), key=lambda i: -i["number"]):
        enfants = rattachees(epic["number"], issues)
        sousChantiers = [e for e in enfants if estEpic(e)]
        for lot in lots(epic.get("body") or ""):
            lignes.append(
                f"EPIC #{epic['number']} ({len(enfants)} issue(s), "
                f"{len(sousChantiers)} sous-chantier(s)) · {lot[:130]}"
            )
    return lignes


def sansLot(issues: list[dict]) -> list[int]:
    """Les EPIC ouverts qui ne declarent aucun lot. Ils ne sont pas des candidats a la revue.

    Ils restent affiches parce qu un EPIC sans lot est un signal en soi, mais les compter parmi
    les candidats gonflerait le nombre d une revue qui n a rien a lire.
    """
    return sorted(
        (e["number"] for e in issues if estEpic(e) and not lots(e.get("body") or "")),
        reverse=True,
    )


def _autoTest() -> int:
    """Les temoins : la loupe voit un lot, recolle ses continuations, et ne confond pas 46 avec 4."""
    corpsEpic = (
        "- [x] **Lot 0 - Instruction.** Fait.\n"
        "- [ ] **Lot 1 - Porter.** Sous-chantier #99, parce qu'il porte deux issues\n"
        "      et au moins deux PR.\n"
        "\n## Autre section\n"
    )
    vus = lots(corpsEpic)
    assert len(vus) == 2, vus
    assert "au moins deux PR" in vus[1], "les lignes de continuation doivent etre recollees"

    issues = [
        {"number": 4, "title": "parent", "body": corpsEpic, "labels": [{"name": "epic"}]},
        {"number": 99, "title": "enfant", "body": "Fait partie de #4", "labels": [{"name": "epic"}]},
        {"number": 46, "title": "voisine", "body": "Fait partie de #46", "labels": []},
    ]
    liees = rattachees(4, issues)
    assert [i["number"] for i in liees] == [99], liees
    assert estEpic(liees[0])

    vide = [{"number": 7, "title": "vide", "body": "aucun lot ici", "labels": [{"name": "epic"}]}]
    assert lots(vide[0]["body"]) == []
    # Un EPIC sans lot ne donne AUCUN candidat : la surface de revue est le lot, et il n en a pas.
    assert rapport(vide) == [], rapport(vide)
    # Mais il reste signale, sinon un EPIC sous-structure disparaitrait du releve (#4758).
    assert sansLot(vide) == [7], sansLot(vide)

    # Un candidat se lit SEUL : il nomme son EPIC, comme les quatre autres loupes du depot.
    unLot = rapport(issues)
    assert len(unLot) == 2, unLot
    assert all(l.startswith("EPIC #4 ") for l in unLot), unLot
    assert "Lot 1 - Porter" in unLot[1], unLot[1]
    # Le sens NEGATIF : sans ce cas, un `rapport` qui rendrait toujours vide passerait les autres.
    assert rapport(issues) != [], "un EPIC portant deux lots doit rendre deux candidats"

    print("auto-test : 10 temoins verts")
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    issues = _issues()
    code = loupe("4712", "un lot multi-PR s'ouvre en sous-chantier (jugement humain)", rapport(issues))
    print("\nPour chaque lot ci-dessus : combien de PR ? Plus de deux, il lui fallait un sous-chantier.")
    muets = sansLot(issues)
    if muets:
        print(
            "%d EPIC ouvert(s) ne declarent aucun lot, donc rien a relire ici : %s."
            % (len(muets), ", ".join("#%d" % n for n in muets))
        )
    return code


if __name__ == "__main__":
    raise SystemExit(main())
