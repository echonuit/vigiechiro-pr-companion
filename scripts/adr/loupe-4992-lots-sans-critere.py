#!/usr/bin/env python3
"""Loupe de l'ADR 4992 : quels lots ouverts ne disent pas comment on saura qu'ils sont finis.

`AGENTS.md` exige depuis #4975 que chaque lot porte son critere de fin dans son CORPS. La loupe du
lot 2 (`rappelle_le_critere_de_fin.py`) rappelle la regle au moment ou un lot est ouvert ou edite.
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

Elles vivent dans `critere-de-fin.motif`, lu aussi par `rappelle_le_critere_de_fin.py`, et leur
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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import PLAFOND_ISSUES, forge, loupe, sort_si_contrat_demande
from _commun.epics import est_epic
from _commun.forge import interroge

PLAFOND = PLAFOND_ISSUES

# Le commit qui a ecrit la regle, en UTC. Fait historique, il ne se met pas a jour.
NAISSANCE = "2026-08-29T05:37:52Z"

# Le motif vit dans UN fichier, lu aussi par `.github/scripts/rappelle_le_critere_de_fin.py`. Chacun
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
    """L UNION du label et du titre, desormais IMPORTEE plutot que reecrite ici (#4967).

    Cette loupe portait la seule definition juste des quatre dispositifs qui lisent cette notion, et
    c est la sienne qui a ete retenue. Elle ne la garde plus en copie : une definition juste en double
    est une divergence qui attend, et c est exactement le defaut que #4967 corrige.

    Le nom reste, en camel, parce que ses appelants et ses cas l ecrivent ainsi.
    """
    return est_epic(issue)


def ditSonCritere(corps: str) -> bool:
    return bool(CRITERE.search(corps or ""))


def _corpus() -> tuple[list[dict], dict[int, list[dict]]]:
    """Les chantiers ouverts depuis la regle, et les sous-issues de chacun."""
    issues = json.loads(
        interroge(
            [
                "issue",
                "list",
                "--state",
                "all",
                "--limit",
                str(PLAFOND),
                "--json",
                "number,title,createdAt,labels",
            ],
            quoi="les chantiers ouverts",
        )
    )
    if len(issues) >= PLAFOND:
        print(
            f"REFUS : plafond de {PLAFOND} issues atteint ; la collecte peut etre tronquee.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    # ⟨la marge se RELIT⟩ Sa ligne de verdict dit `lus=<nombre de LOTS>`, pas le nombre d issues
    # collectees : la marge contre le plafond y etait donc invisible, et cette loupe a refuse
    # plusieurs semaines sans que personne ne le voie. Ce qui se publie ici est la collecte, avec son
    # plafond a cote, seule forme ou un lecteur peut juger qu on approche du mur (#5558).
    print(f"  collecte : {len(issues)} issue(s) lue(s), plafond {PLAFOND}")
    chantiers = [i for i in issues if estEpic(i) and i["createdAt"] > NAISSANCE]
    lots: dict[int, list[dict]] = {}
    for chantier in chantiers:
        rendu = json.loads(
            interroge(
                ["issue", "view", str(chantier["number"]), "--json", "subIssues"],
                quoi=f"les lots du chantier #{chantier['number']}",
            )
        )
        numeros = [
            n["number"]
            for n in rendu.get("subIssues", {}).get("nodes", [])
            if n.get("state") == "OPEN"
        ]
        lots[chantier["number"]] = [
            json.loads(
                interroge(
                    ["issue", "view", str(n), "--json", "number,title,body"],
                    quoi=f"le lot #{n}",
                )
            )
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


def _auto_test_plafond(joues: list[int]) -> None:
    from contextlib import redirect_stderr
    from io import StringIO
    from unittest.mock import patch

    for nombre in (PLAFOND - 1, PLAFOND, PLAFOND + 1):
        corps = json.dumps([{"title": "ordinaire", "labels": []}] * nombre)
        sortie = subprocess.CompletedProcess([], 0, stdout=corps)
        erreur = StringIO()
        with (
            patch.object(shutil, "which", return_value="gh"),
            patch.object(subprocess, "run", return_value=sortie),
            redirect_stderr(erreur),
        ):
            code = 0
            try:
                _corpus()
            except SystemExit as refus:
                code = refus.code
        joues[0] += 1
        assert code == (2 if nombre >= PLAFOND else 0), (
            f"collecte de {nombre} issues : code {code}, refus attendu au plafond {PLAFOND}"
        )
        if nombre >= PLAFOND:
            joues[0] += 1
            assert "plafond" in erreur.getvalue(), erreur.getvalue()


def _autoTest() -> int:
    """Les temoins : une par formulation du motif, un lot muet sort, la borne tient."""
    # ⟨le compte se DERIVE de chaque assertion⟩ Ce harnais emploie des `assert` nus : le
    # compteur s incremente devant chacun, et dans les boucles il compte par ITERATION.
    # Un `len(...)` ecrit a la main serait juste le jour ou on l ecrit (#5744).
    joues = [0]
    _auto_test_plafond(joues)
    joues[0] += 1
    assert ditSonCritere("blabla\n\n## Fini quand\n\nil rougit."), "« Fini quand » doit compter"
    joues[0] += 1
    assert ditSonCritere("**Fait quand** : les six y sont."), "« Fait quand » doit compter"
    joues[0] += 1
    assert ditSonCritere("## Comment on saura que chaque lot est fini\n\nil rougit."), (
        "la section doit compter"
    )
    joues[0] += 1
    assert ditSonCritere("Le critère de fin est le suivant."), "« critère de fin » doit compter"
    # Ce temoin est ce qui tient le « comment » du motif. Sans lui, restreindre `on saur...` a
    # `comment on saur...` ne serait prouve par rien : « on ne saura pas s il est fini » ne discrimine
    # pas, les deux mots n y etant pas adjacents. Celui-ci les colle, et il est mort si le « comment »
    # tombe.
    joues[0] += 1
    assert not ditSonCritere("Personne ne dit si on saura que c est fini."), (
        "« on saura ... fini » seul n est pas un critere"
    )
    joues[0] += 1
    assert not ditSonCritere("On ne saura pas s il est fini."), "une negation n est pas un critere"
    joues[0] += 1
    assert not ditSonCritere("Un lot sans rien."), "un corps muet ne doit pas compter"
    joues[0] += 1
    assert ditSonCritere("**Ce que je vérifierai** : le garde rougit."), (
        "« Ce que je vérifierai » est le mot que CLAUDE.md prescrit"
    )

    joues[0] += 1
    assert estEpic({"title": "[epic] X", "labels": []}), "le titre suffit"
    joues[0] += 1
    assert estEpic({"title": "[chantier] X", "labels": []}), "« [chantier] » aussi"
    joues[0] += 1
    assert estEpic({"title": "fix(x) : y", "labels": [{"name": "epic"}]}), "le label suffit"
    joues[0] += 1
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
    joues[0] += 1
    assert len(vus) == 2, vus
    joues[0] += 1
    assert "#21" in vus[0], "l ordre va du chantier le plus recent au plus ancien"
    joues[0] += 1
    assert "#11" in vus[1], vus
    joues[0] += 1
    assert all("#12" not in v for v in vus), "un lot qui dit son critere ne sort pas"

    # La borne historique : un chantier anterieur a la regle n entre pas dans le corpus. Elle est
    # appliquee dans `_corpus`, qui lit la forge ; on eprouve ici la COMPARAISON qui la porte.
    joues[0] += 1
    assert "2026-08-28T23:59:59Z" < NAISSANCE < "2026-08-29T06:00:00Z", "la borne a bouge"

    # ⟨les cas du module PARTAGE, joues ici⟩ `_commun/forge.py` porte l appel des quatre
    # dispositifs depuis #5544, et ses cas doivent etre joues par quelqu un sous peine d etre inertes
    # (ADR 5546, et #5594 pour l espece). UN seul joueur, et c est celui-ci : il portait deja le cas
    # qui exerce l APPEL et non le verdict, apres qu une mutation ait montre que retirer le refus
    # laissait cet auto-test vert.
    for libelle, tenu in forge.verifie_grammaire():
        joues[0] += 1
        assert tenu, f"appel a la forge : {libelle}"

    # L APPEL, et non le verdict (ADR 4331). Les cas ci-dessus n exercent jamais `_forge`, et une
    # mutation l a montre : retirer le refus laissait l auto-test VERT. On lance donc le vrai chemin
    # avec un PATH ou `gh` n existe pas, sans reseau et en une milliseconde.
    chemin = os.environ.get("PATH", "")
    os.environ["PATH"] = str(pathlib.Path(__file__).parent)
    try:
        interroge(["issue", "list"], quoi="un cas")
    except SystemExit as sortie:
        joues[0] += 1
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
    joues[0] += 1
    assert manquant.returncode == 2, (
        f"un motif introuvable doit REFUSER en 2, pas en {manquant.returncode}"
    )
    joues[0] += 1
    assert "introuvable" in manquant.stderr, manquant.stderr

    print(
        f"\n{joues[0]} cas joue(s) : les formulations du motif reconnues, la negation ecartee,"
        " un lot muet vu."
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
        # L'unite est le LOT ouvert, pas le fichier : ce garde lit la forge. `ouverts` porte deja
        # ce compte, et le titre du verdict l'affiche ; `lus` le rend lisible par le rapport.
        lus=ouverts,
    )
    print("\nPour chaque lot ci-dessus : ecrire dans SON corps comment on saura qu il est fini.")
    return code


CONTRAT = {
    "geste": "lot ouvert sans critere de fin dans son corps",
    "population": "les lots rattaches aux chantiers ouverts, sur la forge",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/loupe-4992-lots-sans-critere.py --auto-test",
    "decision": "ADR 4992",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    raise SystemExit(main())
