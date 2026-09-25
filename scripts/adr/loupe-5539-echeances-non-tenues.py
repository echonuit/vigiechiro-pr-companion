#!/usr/bin/env python3
"""Loupe de l'ADR 5539 : les engagements datés que personne n'a tenus.

Ce dépôt écrit des engagements datés dans ses issues : « À tenir vers le 2026-09-20 », « À relever à
partir du 2026-09-20 ». Rien ne les lit. Ils n'existent que le jour où quelqu'un pense à les
chercher, et ce jour-là il est trop tard dans les deux sens.

## Le signal n'est pas la date

Mesure du 2026-09-25, sur 160 issues ouvertes : **45** portent une date, et **56** de ces dates sont
déjà passées. Presque toutes sont des dates de **mesure** - « mesuré le 2026-09-07 » - qui ne
promettent rien. Une loupe indexée sur la date rendrait quarante-cinq lignes pour en désigner deux,
c'est-à-dire qu'elle noierait sa sortie.

Le signal est le **verbe qui engage**, collé à la date. Il ramène la population à deux.

## Les deux moitiés, et pourquoi il en faut deux

| | L'engagement | Ce qui s'est passé |
|---|---|---|
| #5384 | « À tenir vers le 2026-09-20 » | **fermée le 2026-09-07**, treize jours trop tôt |
| #5327 | « À relever à partir du 2026-09-20 » | **ouverte et échue**, jamais relevée |

Une loupe qui ne regarderait que les issues ouvertes manquerait la première, qui est celle qui a
coûté le plus : un bilan publié sur une mesure prématurée, corrigé dix-huit jours plus tard.

## La règle d'exclusion, dérivée d'un faux positif

Un troisième candidat, #5381, portait « la seconde clôture de #5294, prévue **vers le 2026-09-20** ».
Il **mentionne** l'échéance d'un autre sans en porter aucune, et sa fermeture était juste.

D'où la règle : **une ligne qui nomme une autre issue parle de l'échéance d'un autre.** Elle sépare
correctement les trois cas connus, et c'est la seule qui le fasse. C'est aussi ce qui fait la
différence entre reconnaître ce qu'une ligne FAIT et reconnaître à quoi elle ressemble.

## Ce qu'elle est, et ce qu'elle n'est pas

Une LOUPE. Elle rend 0 en signalant. Une échéance dépassée peut être un choix, et un garde qui la
refuserait obligerait à retirer la date pour livrer : il supprimerait le signal au lieu de le lire.
"""

import datetime
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from _commun import loupe, sort_si_contrat_demande

# Le plafond de `gh issue list`. Au-dela, il tronque SANS le dire (#4834). Mesure du 2026-09-25 :
# le depot porte 1856 issues, et un plafond a 1600 en taisait 256. La marge est volontaire, et la
# troncature se DIT plutot que de se deviner.
PLAFOND = 4000

# Un ENGAGEMENT est un verbe colle a une date. La liste vient des formes reellement ecrites dans le
# depot, relevees le 2026-09-25 : rien n y est ajoute par anticipation.
ENGAGEMENT = re.compile(
    r"(?:[àa]\s+tenir|[àa]\s+relever|[àa]\s+remesurer|[àa]\s+refaire|[àa]\s+rejouer"
    r"|vers\s+le|[àa]\s+partir\s+du)"
    r"[\s*_]*(20\d{2}-\d{2}-\d{2})",
    re.I,
)

# Un numero d issue cite dans la ligne.
AUTRE_ISSUE = re.compile(r"#(\d{3,5})")

# Ce qu une ligne CITE, entre guillemets francais. Une issue qui donne un engagement en exemple
# n en prend aucun : l EPIC #5538 citait les deux cas connus, et remontait comme un troisieme.
# Limite assumee : une citation coupee par un retour a la ligne n est vue que sur sa premiere.
CITATION = re.compile(r"«[^»]*»")


def engagements(issue: dict) -> list[str]:
    """Les dates que CETTE issue s engage a tenir, titre et corps confondus.

    Une ligne qui nomme une AUTRE issue est ecartee : elle parle de l echeance d un autre. Sans cette
    regle, #5381 remonterait, qui citait celle de #5294 et dont la fermeture etait juste.
    """
    texte = (issue.get("title") or "") + "\n" + (issue.get("body") or "")
    trouves = []
    for ligne in texte.splitlines():
        dates = ENGAGEMENT.findall(CITATION.sub(" ", ligne))
        if not dates:
            continue
        autres = {int(n) for n in AUTRE_ISSUE.findall(ligne)} - {issue.get("number")}
        if autres:
            continue
        trouves += dates
    return sorted(set(trouves))


def non_tenus(issues: list[dict], aujourd_hui: datetime.date) -> list[str]:
    """Les engagements qu on n a pas tenus, dans les deux sens.

    Une issue OUVERTE dont la date est passee, et une issue FERMEE avant la sienne. Les deux moitiés
    sont disjointes, et aucune ne couvre l autre.
    """
    rendus = []
    for issue in sorted(issues, key=lambda i: i.get("number", 0)):
        for brut in engagements(issue):
            try:
                echeance = datetime.date.fromisoformat(brut)
            except ValueError:
                continue
            numero = issue.get("number")
            if issue.get("state", "").upper() == "OPEN":
                if echeance < aujourd_hui:
                    jours = (aujourd_hui - echeance).days
                    rendus.append(f"#{numero}  echeance {brut}, ouverte et echue depuis {jours} j")
                continue
            ferme = (issue.get("closedAt") or "")[:10]
            if not ferme:
                continue
            tot = (echeance - datetime.date.fromisoformat(ferme)).days
            if tot > 0:
                rendus.append(f"#{numero}  echeance {brut}, fermee le {ferme}, {tot} j trop tot")
    return rendus


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


def corpus() -> list[dict]:
    """Toutes les issues, ouvertes et fermees. La couture passe AVANT l appel a la forge.

    Sans elle, un cas d auto-test devrait fabriquer des issues sur la forge, et la verification se
    reduirait a des lancements a la main, qui ne se rejouent pas (ADR 3624).
    """
    injecte = os.environ.get("ECHEANCES_RELEVE_FICHIER")
    if injecte:
        return json.loads(pathlib.Path(injecte).read_text(encoding="utf-8"))
    return json.loads(
        _forge(
            [
                "issue",
                "list",
                "--state",
                "all",
                "--limit",
                str(PLAFOND),
                "--json",
                "number,title,body,state,closedAt",
            ]
        )
    )


def _auto_test() -> int:
    """Les deux moities, leurs contraires, et la regle d exclusion.

    Le cas de l exclusion est celui qui rend cette loupe non decorative : le retirer fait remonter un
    faux positif reel, mesure sur #5381.
    """
    hier = datetime.date(2026, 9, 25)
    cas = (
        (
            "une issue ouverte dont l echeance est passee",
            {"number": 1, "title": "x", "body": "A tenir vers le 2026-09-20.", "state": "OPEN"},
            True,
        ),
        (
            "une issue ouverte dont l echeance est A VENIR",
            {"number": 2, "title": "x", "body": "A tenir vers le 2026-10-30.", "state": "OPEN"},
            False,
        ),
        (
            "une issue fermee AVANT son echeance",
            {
                "number": 3,
                "title": "x",
                "body": "A relever a partir du 2026-09-20.",
                "state": "CLOSED",
                "closedAt": "2026-09-07T05:34:15Z",
            },
            True,
        ),
        (
            "une issue fermee APRES son echeance",
            {
                "number": 4,
                "title": "x",
                "body": "A relever a partir du 2026-09-20.",
                "state": "CLOSED",
                "closedAt": "2026-09-24T10:00:00Z",
            },
            False,
        ),
        (
            "une ligne qui nomme une AUTRE issue parle de l echeance d un autre",
            {
                "number": 5,
                "title": "x",
                "body": "La seconde cloture de #5294, prevue vers le 2026-09-20, rendra son bilan.",
                "state": "OPEN",
            },
            False,
        ),
        (
            "une date de MESURE ne promet rien",
            {
                "number": 6,
                "title": "x",
                "body": "Mesure du 2026-09-07 : 45 issues.",
                "state": "OPEN",
            },
            False,
        ),
        (
            "une CITATION n est pas un engagement",
            {
                "number": 8,
                "title": "x",
                "body": "Ce depot ecrit « a tenir vers le 2026-09-20 », et rien ne les lit.",
                "state": "OPEN",
            },
            False,
        ),
        (
            "l engagement peut vivre dans le TITRE",
            {
                "number": 7,
                "title": "Seconde cloture, vers le 2026-09-20",
                "body": "",
                "state": "OPEN",
            },
            True,
        ),
    )
    echecs = 0
    for libelle, issue, attendu in cas:
        vu = bool(non_tenus([issue], hier))
        if vu is not attendu:
            echecs += 1
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {vu}")
        else:
            print(f"  ✔ {libelle}")

    # ⟨la non-vacuite⟩ Sans ce controle, les quatre cas « pas nomme » seraient satisfaits par une
    # loupe qui ne nomme JAMAIS rien, et ils ne prouveraient rien.
    tous = [c[1] for c in cas]
    rendus = non_tenus(tous, hier)
    attendus = sum(1 for c in cas if c[2])
    if len(rendus) != attendus:
        echecs += 1
        print(f"  ✘ sur le lot entier : {len(rendus)} releve(s) pour {attendus} attendu(s)")
    else:
        print(f"  ✔ sur le lot entier, {attendus} releve(s) et pas un de plus")

    # ⟨la couture se lit⟩ Un relevé injecté DOIT remplacer la forge, sans quoi les cas ci-dessus ne
    # disent rien de ce que le script fait lancé nu.
    import tempfile

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
        json.dump([tous[0]], f)
        chemin = f.name
    ancien = os.environ.get("ECHEANCES_RELEVE_FICHIER")
    os.environ["ECHEANCES_RELEVE_FICHIER"] = chemin
    try:
        lu = corpus()
    finally:
        os.environ.pop("ECHEANCES_RELEVE_FICHIER", None)
        if ancien is not None:
            os.environ["ECHEANCES_RELEVE_FICHIER"] = ancien
        pathlib.Path(chemin).unlink(missing_ok=True)
    if lu != [tous[0]]:
        echecs += 1
        print("  ✘ la couture d injection ne remplace pas la forge")
    else:
        print("  ✔ la couture d injection remplace la forge")

    if echecs:
        print(f"\n{echecs} cas en échec.", file=sys.stderr)
        return 1
    print("\nAuto-test concluant : les deux moitiés, leurs contraires, et la règle d'exclusion.")
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _auto_test()
    issues = corpus()
    if len(issues) >= PLAFOND:
        print(
            f"AVERTISSEMENT : {len(issues)} issues lues, soit le plafond. La forge en porte"
            " peut-etre davantage, et cette loupe ne conclut pas sur ce qu elle n a pas lu."
        )
    # ⟨le fuseau de la SOURCE⟩ `closedAt` vient de la forge en UTC. Comparer a un « aujourd hui »
    # local decalerait d un jour les deux bords, et « echue depuis 0 j » se lirait comme un
    # engagement tenu.
    candidats = non_tenus(issues, datetime.datetime.now(tz=datetime.UTC).date())
    code = loupe(
        "5539",
        f"engagements dates non tenus ({len(candidats)})",
        candidats,
        lus=len(issues),
    )
    if candidats:
        print("\nPour chacun : tenir l engagement, ou ecrire pourquoi il ne le sera pas.")
    return code


CONTRAT = {
    "geste": "engagement date qu une issue n a pas tenu",
    "population": "les issues de la forge, ouvertes et fermees",
    "dispositif": "loupe",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/loupe-5539-echeances-non-tenues.py --auto-test",
    "decision": "ADR 5539",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    raise SystemExit(main())
