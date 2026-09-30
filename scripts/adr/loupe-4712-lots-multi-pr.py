#!/usr/bin/env python3
"""Loupe de l'ADR 4712 : quels lots d'EPIC auraient du s'ouvrir en sous-chantier.

Un lot est de la prose OU une sous-issue native. Rien ne dit, de facon lisible par une machine,
combien de PR il portera : c'est un jugement, et c'est pourquoi l'ADR 4712 se verifie en `humaine`.

Ce que cette loupe fait, et c'est tout ce qu'elle pretend : elle POSE la question au bon moment, en
mettant sous les yeux, pour chaque EPIC ouvert, ses lots et les issues qui lui pendent. Le lecteur
tranche. Elle ne compte pas de suspects et ne porte pas de cliquet.

## Ce que #4967 a corrige, et la premisse qui avait cesse d'etre vraie

Cette loupe cherchait UNE forme de lot, `- [ ] **Lot` en prose, et reconnaissait un EPIC au seul
LABEL `epic`. Les deux etaient faux le 2026-09-30, et les deux le rendaient aveugle :

- un lot est une SOUS-ISSUE native depuis #4829, livre par #4854. Elle declarait « aucun lot, rien a
  relire ici » sur #5504, #5496 et #2104, qui en portaient 1, 3 et 10 ;
- un EPIC se designe aussi par le prefixe de son titre. Dix EPIC ouverts lui etaient donc
  ENTIEREMENT invisibles, dont le sas des suites #4562 et ses 43 sous-issues.

La definition vit maintenant dans `scripts/_commun/epics.py`, partagee avec `loupe-4992` et les deux
cliquets de cloture de `.github/scripts/`. La divergence ETAIT le defaut, donc c'est le cas ou le
commun se justifie.

## Deux mesures qui ont ecarte un garde mecanique

Un premier dessin comptait les lots citant plus d'une issue. Mesure du 2026-08-29 : ZERO sur les dix
EPIC ouverts, parce que la forme courante etait alors un lot en prose SANS reference, les issues se
rattachant a l'EPIC par ailleurs. Le signal n'existait pas. **Cette phrase a cesse d'etre vraie sans
qu'une ligne de ce fichier ne bouge**, et c'est ce que #4967 a trouve : la prose d'un garde vieillit
contre un corpus qui, lui, n'est pas sous controle de version.

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

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import loupe, sort_si_contrat_demande
from _commun.epics import est_epic

# Cette loupe ne lit que les issues OUVERTES, population neuf fois plus petite que le corpus
# entier : son plafond n a donc pas a suivre celui de `_commun.PLAFOND_ISSUES`, qui couvre
# `--state all`. Mesure du 2026-09-28 : 163 issues ouvertes contre 800, soit un facteur 4,9.
# Le chiffre est ecrit ici parce que c est le seul endroit ou quelqu un le relira avant de le
# croire perime (#5558).
PLAFOND = 800

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


def enfants(numero: int, issues: list[dict]) -> list[dict]:
    """Les enfants d un EPIC, par la relation NATIVE de la forge ET par la marque de corps.

    Mesure du 2026-09-30 sur les 40 EPIC ouverts : les deux sources desaccordent sur 17, et TOUJOURS
    dans le meme sens. La marque ne trouve jamais rien que la relation ignore, l inverse est massif :
    #4562 portait 17 enfants par la marque et 43 par la relation, #5006 en portait 3 et 24.

    Les DEUX sont donc lues, et non la seule relation. Rattacher une issue demande deux gestes, la
    marque « Fait partie de #N » et `gh issue edit --parent`, et c est le second qu on oublie : une
    issue peut porter la marque sans que la relation ait ete posee. L union ne peut rien perdre.
    """
    par_relation = {
        i["number"]
        for i in issues
        if (i.get("parent") or {}).get("number") == numero and i["number"] != numero
    }
    par_marque = {i["number"] for i in rattachees(numero, issues)}
    retenus = par_relation | par_marque
    return [i for i in issues if i["number"] in retenus]


def lotsDeLEpic(epic: dict, sesEnfants: list[dict]) -> list[str]:
    """Les lots d un EPIC, quelle que soit la forme qu il leur a donnee.

    Un lot etait de la prose cochee. Depuis #4829 c est une SOUS-ISSUE native, et #4854 l a livre :
    la loupe qui ne lisait que la prose annoncait « aucun lot, rien a relire ici » sur des EPIC qui
    en portaient dix. Mesure du 2026-09-30 : elle declarait sans lot #5504, #5496 et #2104, qui en
    portaient respectivement 1, 3 et 10.

    Une sous-issue que la prose NOMME DEJA ne compte pas deux fois : c est la forme mixte, ou un lot
    en prose cite son numero, et la compter deux fois gonflerait une revue qui n a qu une chose a
    lire.
    """
    enProse = lots(epic.get("body") or "")
    cites = {int(n) for ligne in enProse for n in re.findall(r"#(\d+)", ligne)}
    lignes = list(enProse)
    for enfant in sorted(sesEnfants, key=lambda i: i["number"]):
        if enfant["number"] in cites:
            continue
        lignes.append(f"sous-issue #{enfant['number']} · {(enfant.get('title') or '')[:110]}")
    return lignes


def _issues() -> list[dict]:
    if not shutil.which("gh"):
        print(
            "REFUS : « gh » est absent. Cette loupe ne conclut pas sur ce qu'elle n'a pas lu.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    sortie = subprocess.run(
        [
            "gh",
            "issue",
            "list",
            "--state",
            "open",
            "--limit",
            str(PLAFOND),
            "--json",
            # `parent` porte la relation NATIVE, et il vient dans le MEME appel : la lire ne coute
            # donc aucune requete de plus. Mesure du 2026-09-30 : ce champ rend exactement les
            # memes enfants que 40 appels a `gh issue view --json subIssues`, un par EPIC.
            "number,title,body,labels,parent",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if sortie.returncode != 0:
        print("REFUS : la forge n'a pas repondu.", file=sys.stderr)
        raise SystemExit(2)
    issues = json.loads(sortie.stdout)
    if len(issues) >= PLAFOND:
        print(
            f"REFUS : plafond de {PLAFOND} issues atteint ; la collecte peut etre tronquee.",
            file=sys.stderr,
        )
        raise SystemExit(2)
    return issues


def rapport(issues: list[dict]) -> list[str]:
    """Un candidat par LOT, chacun se lisant seul (#4758).

    La surface de revue est le lot, pas l EPIC : la loupe demande « pour chaque lot, combien de
    PR ? », et plusieurs EPIC n en portent aucun. Chaque ligne nomme donc son EPIC, comme les
    quatre autres loupes du depot dont chaque candidat se lit sans son voisin.
    """
    lignes: list[str] = []
    for epic in sorted((i for i in issues if est_epic(i)), key=lambda i: -i["number"]):
        sesEnfants = enfants(epic["number"], issues)
        sousChantiers = [e for e in sesEnfants if est_epic(e)]
        for lot in lotsDeLEpic(epic, sesEnfants):
            lignes.append(
                f"EPIC #{epic['number']} ({len(sesEnfants)} issue(s), "
                f"{len(sousChantiers)} sous-chantier(s)) · {lot[:130]}"
            )
    return lignes


def sansLot(issues: list[dict]) -> list[int]:
    """Les EPIC ouverts qui ne declarent aucun lot. Ils ne sont pas des candidats a la revue.

    Ils restent affiches parce qu un EPIC sans lot est un signal en soi, mais les compter parmi
    les candidats gonflerait le nombre d une revue qui n a rien a lire.
    """
    return sorted(
        (
            e["number"]
            for e in issues
            if est_epic(e) and not lotsDeLEpic(e, enfants(e["number"], issues))
        ),
        reverse=True,
    )


def _auto_test_plafond() -> None:
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
                _issues()
            except SystemExit as refus:
                code = refus.code
        assert code == (2 if nombre >= PLAFOND else 0), (
            f"collecte de {nombre} issues : code {code}, refus attendu au plafond {PLAFOND}"
        )
        if nombre >= PLAFOND:
            assert "plafond" in erreur.getvalue(), erreur.getvalue()


def _autoTest() -> int:
    """Les temoins : la loupe voit un lot, recolle ses continuations, et ne confond pas 46 avec 4."""
    _auto_test_plafond()
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
        {
            "number": 99,
            "title": "enfant",
            "body": "Fait partie de #4",
            "labels": [{"name": "epic"}],
        },
        {"number": 46, "title": "voisine", "body": "Fait partie de #46", "labels": []},
    ]
    liees = rattachees(4, issues)
    assert [i["number"] for i in liees] == [99], liees
    assert est_epic(liees[0])

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

    # --- Le defaut de #4967 : un lot est devenu une SOUS-ISSUE, et un EPIC un titre ---

    # Un EPIC dont les lots ne sont QUE des sous-issues natives : le cas qui faisait dire « aucun
    # lot, rien a relire ici » sur #2104, qui en portait dix.
    natif = [
        {"number": 20, "title": "[epic] par le titre seul", "body": "pas de prose", "labels": []},
        {"number": 21, "title": "lot A", "body": "", "labels": [], "parent": {"number": 20}},
        {"number": 22, "title": "lot B", "body": "", "labels": [], "parent": {"number": 20}},
    ]
    vusNatifs = rapport(natif)
    # Les DEUX sources d enfants, et leur union. La marque seule ratait 17 EPIC sur 40.
    mixte = [
        {"number": 30, "title": "[epic] deux sources", "body": "rien en prose", "labels": []},
        {
            "number": 31,
            "title": "par la relation",
            "body": "",
            "labels": [],
            "parent": {"number": 30},
        },
        {"number": 32, "title": "par la marque", "body": "Fait partie de #30", "labels": []},
    ]
    # Une sous-issue que la prose NOMME DEJA ne compte pas deux fois : la forme mixte.
    cite = [
        {
            "number": 40,
            "title": "[epic] forme mixte",
            "labels": [],
            "body": "- [ ] **Lot 1.** #41\n",
        },
        {"number": 41, "title": "le lot", "body": "", "labels": [], "parent": {"number": 40}},
    ]
    vusCites = rapport(cite)

    # Une TABLE et non des `assert` epars : ce qui est compte se compte, et le nombre affiche plus
    # bas se DERIVE d elle. L ancienne ligne annoncait « 10 temoins verts » pour onze assertions,
    # c est-a-dire un compte ecrit a la main qui avait cesse d etre vrai sans que rien ne le dise.
    cas = [
        ("la prose de #20 ne porte aucun lot, et c est le point", lots(natif[0]["body"]) == []),
        ("ses deux sous-issues natives sont pourtant des lots", len(vusNatifs) == 2),
        ("chaque candidat nomme son EPIC", all(l.startswith("EPIC #20 ") for l in vusNatifs)),
        ("et dit laquelle", "sous-issue #21" in vusNatifs[0]),
        ("il n est donc PLUS declare sans lot", sansLot(natif) == []),
        ("un EPIC par le seul TITRE est vu", est_epic(natif[0])),
        ("« [chantier] » aussi", est_epic({"title": "[chantier] X", "labels": []})),
        (
            "les deux sources d enfants s UNISSENT",
            sorted(i["number"] for i in enfants(30, mixte)) == [31, 32],
        ),
        ("une sous-issue deja citee en prose ne compte pas deux fois", len(vusCites) == 1),
        ("et c est bien la ligne de prose qui a ete gardee", "sous-issue #41" not in vusCites[0]),
    ]
    # La definition partagee joue ses propres cas ICI, de ce cote de la barriere. L autre cote est
    # joue par `.github/scripts/verifie_cloture_consignee.py`, et les deux sont necessaires : ce
    # qu il faut prouver est que la MEME definition traverse les deux paquets.
    from _commun import epics

    communs = epics.verifie_grammaire()
    echoues = [libelle for libelle, ok in cas + communs if not ok]
    assert not echoues, f"cas rouges : {echoues}"

    # Les `assert` nus de cet auto-test ne sont pas mutes : ce garde est dans la moitie « mutes » du
    # banc, qui neutralise ses fonctions puis joue `verifie_scripts.py`, jamais ce `--auto-test`. Si
    # on le deplacait dans « autonomes », il faudrait passer par un `_verifie` qui echoue PROPREMENT,
    # sans quoi le banc classerait « non concluant » plutot que « tient » (#5637).
    print(
        f"auto-test : {len(cas)} cas pour #4967 et {len(communs)} pour la definition commune, "
        "plus les temoins d origine"
    )
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    issues = _issues()
    code = loupe(
        "4712",
        "un lot multi-PR s'ouvre en sous-chantier (jugement humain)",
        rapport(issues),
        # L'unite n'est pas le fichier : ce garde lit la FORGE, et ce qu'il a lu est le nombre
        # d'issues que la demande a rendues. Un zero y dirait que la forge n'a rien renvoye,
        # ce qui est justement le silence qu'un « aucun candidat » ne distingue pas.
        lus=len(issues),
    )
    print(
        "\nPour chaque lot ci-dessus : combien de PR ? Plus de deux, il lui fallait un sous-chantier."
    )
    muets = sansLot(issues)
    if muets:
        # « aucun lot OUVERT » et non « aucun lot ». Cette loupe ne collecte que les issues
        # ouvertes, donc un EPIC dont tous les lots sont CLOS lui parait muet. Mesure du
        # 2026-09-30 : sur les 14 qu elle nomme, 12 n ont aucun enfant du tout, mais #5504 en a
        # un et #4816 en a sept, tous clos. Dire « aucun lot » de ceux-la serait faux, et la
        # phrase est ce sur quoi le lecteur agit.
        print(
            f"{len(muets)} EPIC ouvert(s) ne portent aucun lot OUVERT, donc rien a relire "
            f"ici : {', '.join(f'#{n}' for n in muets)}."
        )
        print(
            "Cette loupe ne lit que les issues ouvertes : un EPIC dont tous les lots sont clos "
            "figure dans cette liste."
        )
    return code


CONTRAT = {
    "geste": "lot multi-PR qui aurait du s ouvrir en sous-chantier",
    "population": "les EPIC ouverts (label OU titre), leurs lots en prose et leurs sous-issues",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/loupe-4712-lots-multi-pr.py --auto-test",
    "decision": "ADR 4712",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    raise SystemExit(main())
