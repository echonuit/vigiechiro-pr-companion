#!/usr/bin/env python3
"""Loupe de l'ADR 4992 : quels lots ouverts ne disent pas comment on saura qu'ils sont finis.

`AGENTS.md` exige depuis #4975 que chaque lot porte son critere de fin dans son CORPS. La loupe du
lot 2 (`rappelle-le-critere-de-fin.sh`) rappelle la regle au moment ou un lot est ouvert ou edite.
Elle ne voit rien du stock deja la, ni des lots rattaches apres coup et jamais reedites : la forge
n'emet aucun evenement au rattachement d'une sous-issue, et aucun workflow ne peut s'y abonner.

Cette loupe couvre ce trou-la, une fois par semaine, dans le rapport du lundi.

## Ce qu'elle est, et ce qu'elle n'est pas

Une LOUPE. Elle rend 0 en signalant, elle ne bloque rien, et l'arbitrage de #4961 l'a voulu ainsi :
un rouge qui tombe sur qui n'a pas la main, des jours apres l'ouverture qu'il juge, apprend a
ignorer les rouges. Le cout est assume et il s'ecrit : les lots muets ne descendront que si
quelqu'un lit le rapport.

Elle ne juge pas la QUALITE d'un critere. « Fini quand c'est fait » lui convient. Deux dessins de
garde mecanique sur de la prose d'EPIC ont deja ete mesures puis ecartes dans ce depot.

## Le corpus s'arrete a la naissance de la regle

La regle est entree dans `ouvrir-un-chantier` par le commit `d4c3651` du 2026-08-29 07:37:52+02:00.
Un chantier ouvert avant ne pouvait pas y repondre, et le compter serait lui reprocher une regle qui
n'existait pas. Cette borne est un fait historique : elle ne se met pas a jour.

C'est aussi ce qui a fausse le comptage d'origine (#4951) : 3 sur 70, dont 67 anterieurs a la regle.

## Cinq formulations, et une sixieme viendra

Elles vivent dans `critere-de-fin.motif`, lu aussi par `rappelle-le-critere-de-fin.sh`, et leur
provenance est dans `critere-de-fin.motif.md`. La cinquieme, « Ce que je verifierai », a manque aux
deux dispositifs pendant une demi-journee alors que c'est celle que `CLAUDE.md` prescrit : neuf
rappels a tort sur les douze lots du chantier #4980 (#4995).

Une sixieme apparaitra. Signaler a tort coute une ligne de rapport qu'un lecteur ecarte ; se taire a
tort laisse un lot muet, que le rappel du lot 2 aura deja signale s'il est neuf. Le motif peut donc
rester genereux la ou un cliquet aurait du etre exact, et il s'elargit a un seul endroit.

## Ce qu'un EPIC est ici, et pourquoi ce n'est pas le label

Le label `epic` en designe 92, le titre `[epic]` ou `[chantier]` en designe 130, et aucune des deux
populations ne contient l'autre. Cette loupe prend l'UNION : rater un chantier reviendrait a ne pas
poser la question, ce qui est exactement ce qu'elle existe pour eviter. La divergence des deux
definitions est consignee en #4948, et elle n'est pas de son ressort.

## Hors ligne, elle le dit

Sans `gh`, elle ne rend pas un rapport vide qui se lirait comme « aucun lot muet ». Elle sort en 2 et
le dit, conformement a l'ADR 2748 : un dispositif qui peut ne rien verifier le declare.

Usage : loupe-4992-lots-sans-critere.py [--auto-test]
"""

from __future__ import annotations

import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import loupe  # noqa: E402

# Le commit qui a ecrit la regle, en UTC. Fait historique, il ne se met pas a jour.
NAISSANCE = "2026-08-29T05:37:52Z"

# Le motif vit dans UN fichier, lu aussi par `.github/scripts/rappelle-le-critere-de-fin.sh`. Chacun
# portait sa copie, et elles avaient deja diverge sur deux caracteres : la cinquieme formulation
# manquait aux deux, et rien ne pouvait le dire (#4995, #4837). Les contraintes de dialecte sont dans
# `critere-de-fin.motif.md`.
# Injectable pour l auto-test, comme le corpus des deux cliquets de forge : sans cela le chemin de
# refus n est exerce par rien, et une mutation l a montre en le laissant vert.
MOTIF = pathlib.Path(
    os.environ.get("CRITERE_MOTIF_FICHIER")
    or pathlib.Path(__file__).parent / "critere-de-fin.motif"
)
if not MOTIF.is_file():
    print(
        f"REFUS : « {MOTIF} » est introuvable. Cette loupe ne conclut pas sans son motif.",
        file=sys.stderr,
    )
    raise SystemExit(2)
CRITERE = re.compile(MOTIF.read_text(encoding="utf-8").splitlines()[0], re.I)


def estEpic(issue: dict) -> bool:
    """L UNION du label et du titre : rater un chantier, c est ne pas poser la question."""
    parLabel = any(e.get("name") == "epic" for e in issue.get("labels") or [])
    titre = (issue.get("title") or "").lower()
    return parLabel or titre.startswith("[epic]") or titre.startswith("[chantier]")


def ditSonCritere(corps: str) -> bool:
    return bool(CRITERE.search(corps or ""))


def _forge(arguments: list[str]) -> str:
    if not shutil.which("gh"):
        print(
            "REFUS : « gh » est absent. Cette loupe ne conclut pas sur ce qu'elle n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    sortie = subprocess.run(["gh", *arguments], capture_output=True, text=True, check=False)
    if sortie.returncode != 0:
        print("REFUS : la forge n'a pas repondu.", file=sys.stderr)
        raise SystemExit(2)
    return sortie.stdout


def _corpus() -> tuple[list[dict], dict[int, list[dict]]]:
    """Les chantiers ouverts depuis la regle, et les sous-issues de chacun."""
    issues = json.loads(
        _forge(
            [
                "issue",
                "list",
                "--state",
                "all",
                "--limit",
                "1600",
                "--json",
                "number,title,createdAt,labels",
            ]
        )
    )
    chantiers = [i for i in issues if estEpic(i) and i["createdAt"] > NAISSANCE]
    lots: dict[int, list[dict]] = {}
    for chantier in chantiers:
        rendu = json.loads(
            _forge(["issue", "view", str(chantier["number"]), "--json", "subIssues"])
        )
        numeros = [
            n["number"]
            for n in rendu.get("subIssues", {}).get("nodes", [])
            if n.get("state") == "OPEN"
        ]
        lots[chantier["number"]] = [
            json.loads(_forge(["issue", "view", str(n), "--json", "number,title,body"]))
            for n in numeros
        ]
    return chantiers, lots


def candidats(chantiers: list[dict], lots: dict[int, list[dict]]) -> list[str]:
    """Un candidat par LOT, chacun se lisant seul (#4758)."""
    lignes: list[str] = []
    for chantier in sorted(chantiers, key=lambda c: -c["number"]):
        for lot in sorted(lots.get(chantier["number"], []), key=lambda l: l["number"]):
            if ditSonCritere(lot.get("body") or ""):
                continue
            lignes.append(
                f"lot #{lot['number']} du chantier #{chantier['number']} · {(lot.get('title') or '')[:90]}"
            )
    return lignes


def _autoTest() -> int:
    """Les temoins : une par formulation du motif, un lot muet sort, la borne tient."""
    assert ditSonCritere("blabla\n\n## Fini quand\n\nil rougit."), "« Fini quand » doit compter"
    assert ditSonCritere("**Fait quand** : les six y sont."), "« Fait quand » doit compter"
    assert ditSonCritere("## Comment on saura que chaque lot est fini\n\nil rougit."), (
        "la section doit compter"
    )
    assert ditSonCritere("Le critère de fin est le suivant."), "« critère de fin » doit compter"
    # Ce temoin est ce qui tient le « comment » du motif. Sans lui, restreindre `on saur...` a
    # `comment on saur...` ne serait prouve par rien : « on ne saura pas s il est fini » ne discrimine
    # pas, les deux mots n y etant pas adjacents. Celui-ci les colle, et il est mort si le « comment »
    # tombe.
    assert not ditSonCritere("Personne ne dit si on saura que c est fini."), (
        "« on saura ... fini » seul n est pas un critere"
    )
    assert not ditSonCritere("On ne saura pas s il est fini."), "une negation n est pas un critere"
    assert not ditSonCritere("Un lot sans rien."), "un corps muet ne doit pas compter"
    assert ditSonCritere("**Ce que je vérifierai** : le garde rougit."), (
        "« Ce que je vérifierai » est le mot que CLAUDE.md prescrit"
    )

    assert estEpic({"title": "[epic] X", "labels": []}), "le titre suffit"
    assert estEpic({"title": "[chantier] X", "labels": []}), "« [chantier] » aussi"
    assert estEpic({"title": "fix(x) : y", "labels": [{"name": "epic"}]}), "le label suffit"
    assert not estEpic({"title": "fix(x) : y", "labels": []}), "ni l un ni l autre"

    chantiers = [
        {"number": 10, "title": "[epic] recent", "createdAt": "2026-08-30T00:00:00Z", "labels": []},
        {
            "number": 20,
            "title": "[epic] recent aussi",
            "createdAt": "2026-08-31T00:00:00Z",
            "labels": [],
        },
    ]
    lots = {
        10: [
            {"number": 11, "title": "muet", "body": "rien"},
            {"number": 12, "title": "parlant", "body": "**Fini quand** il rougit."},
        ],
        20: [{"number": 21, "title": "muet aussi", "body": "rien non plus"}],
    }
    vus = candidats(chantiers, lots)
    assert len(vus) == 2, vus
    assert "#21" in vus[0], "l ordre va du chantier le plus recent au plus ancien"
    assert "#11" in vus[1], vus
    assert all("#12" not in v for v in vus), "un lot qui dit son critere ne sort pas"

    # La borne historique : un chantier anterieur a la regle n entre pas dans le corpus. Elle est
    # appliquee dans `_corpus`, qui lit la forge ; on eprouve ici la COMPARAISON qui la porte.
    assert "2026-08-28T23:59:59Z" < NAISSANCE < "2026-08-29T06:00:00Z", "la borne a bouge"

    # L APPEL, et non le verdict (ADR 4331). Les cas ci-dessus n exercent jamais `_forge`, et une
    # mutation l a montre : retirer le refus laissait l auto-test VERT. On lance donc le vrai chemin
    # avec un PATH ou `gh` n existe pas, sans reseau et en une milliseconde.
    chemin = os.environ.get("PATH", "")
    os.environ["PATH"] = str(pathlib.Path(__file__).parent)
    try:
        _forge(["issue", "list"])
    except SystemExit as sortie:
        assert sortie.code == 2, f"le refus doit sortir en 2, pas en {sortie.code}"
    else:
        raise AssertionError("sans « gh », l appel doit REFUSER au lieu de conclure")
    finally:
        os.environ["PATH"] = chemin

    # Le motif manquant fait REFUSER, pas conclure. Le script se relance par son chemin reel, avec un
    # motif introuvable : c est le seul moyen d exercer un refus pose au chargement du module.
    manquant = subprocess.run(
        [sys.executable, __file__, "--auto-test"],
        capture_output=True,
        text=True,
        env={**os.environ, "CRITERE_MOTIF_FICHIER": "/nulle/part/critere.motif"},
        check=False,
    )
    assert manquant.returncode == 2, (
        f"un motif introuvable doit REFUSER en 2, pas en {manquant.returncode}"
    )
    assert "introuvable" in manquant.stderr, manquant.stderr

    print(
        "Auto-test concluant : les formulations du motif reconnues, la negation ecartee, un lot muet vu."
    )
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    chantiers, lots = _corpus()
    ouverts = sum(len(v) for v in lots.values())
    muets = candidats(chantiers, lots)
    code = loupe(
        "4992",
        f"lots ouverts sans critere de fin ({len(muets)} sur {ouverts}, {len(chantiers)} chantiers depuis la regle)",
        muets,
    )
    print("\nPour chaque lot ci-dessus : ecrire dans SON corps comment on saura qu il est fini.")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
