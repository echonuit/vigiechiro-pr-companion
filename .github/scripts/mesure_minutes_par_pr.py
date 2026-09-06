#!/usr/bin/env python3
"""Ce qu une demande PAIE et ce qu elle ATTEND, par job, sur une fenetre de demandes (#5304).

    python3 .github/scripts/mesure_minutes_par_pr.py <depot> [fenetre]
    python3 .github/scripts/mesure_minutes_par_pr.py --auto-test

## Pourquoi un instrument neuf, alors qu il en existe deja un

`mesure_duree_portail.py` compare deux medianes glissantes des executions reussies **sur `main`**.
Or sur `main` il n y a pas de base de comparaison : toutes les portees du chantier #5294 y rendent
`oui`, et les runs de `main` sont donc **rigoureusement inchanges par construction**.

Cet instrument-la affichera zero gain, quoi qu il arrive. S en servir comme preuve serait une mesure
qui affirme ce qu elle n a pas regarde. Il garde en revanche la non-regression du chemin complet, et
c est utile : si une portee cassait quelque chose sur `main`, il le verrait.

Ce script-ci lit donc les **demandes**, pas `main`.

## Trois chiffres, et le troisieme est le plus utile

1. **Les minutes FACTUREES** par demande. Elles se lisent dans `.../runs/<id>/timing`, champ
   `billable`, et non en soustrayant deux horodatages : GitHub arrondit **chaque job** a la minute
   superieure, et un job neutralise qui dure dix secondes en coute une. Une mesure calculee sur les
   horodatages sous-estime donc le cout d autant de minutes qu il y a de jobs.
2. **L horloge jusqu au dernier verdict**, c est-a-dire le job le plus long : l attente reelle.
3. **Le taux de « sans objet » par job**, qui est l instrument PERMANENT et se lit dans les deux sens.
   Tombe a 0 % sur un job, sa portee s est elargie en silence et le gain s est evapore sans que rien
   ne rougisse. Monte a 100 % sur toute la fenetre, ce job n a **rien juge** depuis, et il ne se
   distingue plus d un job qu on aurait supprime.

## La mediane, jamais la moyenne

Deux runs sur trente durent le double des autres, et une moyenne les laisse deformer le chiffre
qu on publie. C est le meme parti que `mesure_duree_portail.py`, pour la meme raison mesuree.

## La couture qui le rend eprouvable

Les relevés sont INJECTABLES par `RELEVE_DEMANDES_FICHIER`, un tableau JSON de demandes. Sans cette
couture, ce script irait chercher ses propres donnees sur la forge : aucun cas ne pourrait lui en
fabriquer, et sa verification se reduirait a des lancements a la main, qui ne se rejouent pas
(ADR 3624). C est le patron que `mesure_duree_portail.py` a pose ici.

## Ce qu il ne fait PAS

**Il n est pas un garde et ne refuse rien.** Il releve. Un chantier qui rend moins que promis est un
sujet de bilan, pas un rouge de CI - et le rendre bloquant ferait refuser une demande pour une
mesure qui ne parle pas d elle.
"""

from __future__ import annotations

import collections
import json
import math
import os
import pathlib
import statistics
import subprocess
import sys

# Une conclusion qui porte sur le CONTENU. `cancelled`, `skipped` et `stale` sont des fins de course,
# pas des jugements : les compter melerait des runs qui n ont rien mesure a ceux qui ont conclu.
PROBANTES = ("success", "failure", "neutral", "timed_out")


def _gh(*arguments: str) -> str:
    """Un appel a la forge, ou une chaine vide : l appelant decide de ce que le silence vaut."""
    sortie = subprocess.run(["gh", *arguments], capture_output=True, text=True, check=False)
    return sortie.stdout if sortie.returncode == 0 else ""


def releve(depot: str, fenetre: int = 40) -> list[dict]:
    """Les demandes de la fenetre, chacune avec ses jobs et ses minutes facturees.

    La couture d injection passe AVANT l appel a la forge : un cas d auto-test fabrique son releve,
    et le script ne sait pas d ou il vient.
    """
    injecte = os.environ.get("RELEVE_DEMANDES_FICHIER")
    if injecte:
        return json.loads(pathlib.Path(injecte).read_text(encoding="utf-8"))

    brut = _gh(
        "run",
        "list",
        "--repo",
        depot,
        "--event",
        "pull_request",
        "--limit",
        str(fenetre * 12),
        "--json",
        "databaseId,headSha,workflowName,conclusion",
    )
    if not brut:
        return []
    runs = [r for r in json.loads(brut) if r.get("conclusion") in PROBANTES]

    # Une DEMANDE est un `headSha` : ses ateliers sont plusieurs runs, et c est leur somme qu on paie.
    par_sha: dict[str, list[dict]] = collections.defaultdict(list)
    for r in runs:
        par_sha[r["headSha"]].append(r)

    demandes = []
    for sha in list(par_sha)[:fenetre]:
        jobs, facturees = [], 0
        for r in par_sha[sha]:
            minutage = _gh("api", f"repos/{depot}/actions/runs/{r['databaseId']}/timing")
            if minutage:
                facture = json.loads(minutage).get("billable", {})
                facturees += sum(p.get("total_ms", 0) for p in facture.values()) / 60000
            detail = _gh("api", f"repos/{depot}/actions/runs/{r['databaseId']}/jobs?per_page=100")
            if detail:
                for j in json.loads(detail).get("jobs", []):
                    jobs.append(
                        {
                            "nom": j["name"],
                            "minutes": _duree(j),
                            "sans_objet": _sans_objet(j),
                            "porte_une_portee": porte_une_portee(j),
                        }
                    )
        demandes.append({"sha": sha, "facturees": facturees, "jobs": jobs})
    return demandes


def _duree(job: dict) -> float:
    import datetime

    debut, fin = job.get("started_at"), job.get("completed_at")
    if not debut or not fin:
        return 0.0
    # `fromisoformat` lit le `Z` depuis 3.11 : pas de substitution a faire.
    a = datetime.datetime.fromisoformat(debut)
    b = datetime.datetime.fromisoformat(fin)
    return (b - a).total_seconds() / 60


# Le NOM d un pas de portee. L API rend les noms des etapes, jamais leur `id` : c est donc le nom qui
# sert de marque, et `verifie_portees_de_ci.py` l exige desormais plutot que de l esperer.
MARQUE_DE_PORTEE = "Ce diff "


def porte_une_portee(job: dict) -> bool:
    return any((e.get("name") or "").startswith(MARQUE_DE_PORTEE) for e in job.get("steps") or [])


def _sans_objet(job: dict) -> bool:
    """Un job dont la PORTEE a dit non : son pas de portee a conclu, et la suite est sautee.

    On le lit dans les etapes plutot que dans la duree : **un job court n est pas un job qui s est
    tu**, et c est precisement la confusion que ce chantier existe pour lever. `titre` met six
    secondes en ayant juge.
    """
    etapes = job.get("steps") or []
    return porte_une_portee(job) and any(e.get("conclusion") == "skipped" for e in etapes)


def mediane(valeurs: list[float]) -> float:
    return statistics.median(valeurs) if valeurs else 0.0


def rendre(depot: str, fenetre: int = 40) -> int:
    demandes = releve(depot, fenetre)
    if not demandes:
        print("Aucune demande relevee : la forge n a pas repondu, ou la fenetre est vide.")
        print("Ce releve REFUSE de conclure plutot que d annoncer zero.")
        return 1

    attentes = [max((j["minutes"] for j in d["jobs"]), default=0) for d in demandes]

    # ⟨la source qui fait foi ne repond pas ici, et il faut le DIRE⟩ `billable.total_ms` est la seule
    # mesure des minutes reellement facturees, arrondi compris. Sur ce depot elle rend ZERO : la
    # facturation ne s applique pas aux depots publics, et l API le traduit par zero plutot que par
    # une absence. Annoncer « 0 minute » serait exactement le contraire de ce qu on a mesure.
    #
    # On retombe donc sur un MODELE, et il est nomme comme tel : la somme des durees de job, chacune
    # arrondie a la minute superieure, qui est la regle de facturation de la forge.
    facturees = [d["facturees"] for d in demandes if d["facturees"]]
    modelisees = [sum(math.ceil(j["minutes"]) for j in d["jobs"]) for d in demandes]
    lues = bool(facturees)

    par_job: dict[str, list[dict]] = collections.defaultdict(list)
    for d in demandes:
        for j in d["jobs"]:
            par_job[j["nom"]].append(j)

    print(f"Fenetre : {len(demandes)} demande(s).")
    if lues:
        print(
            f"  minutes FACTUREES par demande (mediane) : {mediane(facturees):6.1f}   (source : billable)"
        )
    else:
        print(f"  minutes MODELISEES par demande (mediane): {mediane(modelisees):6.1f}")
        print(
            "     `billable` rend zero sur ce depot : la facturation ne s'y applique pas. Ce chiffre"
        )
        print(
            "     est donc la somme des durees de job ARRONDIES a la minute, pas un releve de facture."
        )
    print(f"  attente jusqu au dernier verdict        : {mediane(attentes):6.1f} min")
    print()
    print(f"  {'job':28s} {'mediane':>8s} {'sans objet':>12s}")
    for nom, jobs in sorted(
        par_job.items(), key=lambda kv: -mediane([j["minutes"] for j in kv[1]])
    ):
        muets = sum(1 for j in jobs if j["sans_objet"])
        # ⟨un job SANS portee n a pas a se taire⟩ Les deux avertissements ne valent que pour un job
        # qui en porte une. Sans ce filtre, ils criaient sur `titre`, `corps` et `build`, qui sont
        # inconditionnels par decision - et un dispositif qui crie sur du juste apprend a etre ignore.
        porte = any(j.get("porte_une_portee", j["sans_objet"]) for j in jobs)
        part = f"{muets * 100 / len(jobs):.0f} % ({muets}/{len(jobs)})" if porte else "sans portee"
        print(f"  {nom:28s} {mediane([j['minutes'] for j in jobs]):7.1f}m {part:>12s}")
        if not porte or len(jobs) < 10:
            continue
        if muets == len(jobs):
            print(
                f"      ⚠ ce job n a RIEN juge sur {len(jobs)} demandes : il ne se distingue plus d un job supprime."
            )
        if muets == 0:
            print(
                "      ⚠ sa portee ne s est jamais tue : elle s est peut-etre elargie en silence."
            )
    return 0


CAS = (
    # (libelle, releve injecte, minutes facturees attendues, attente attendue)
    (
        "une fenetre ou rien n est neutralise",
        [
            {
                "sha": "a",
                "facturees": 82.0,
                "jobs": [
                    {"nom": "build", "minutes": 11.0, "sans_objet": False},
                    {"nom": "paquet", "minutes": 16.3, "sans_objet": False},
                ],
            },
            {
                "sha": "b",
                "facturees": 80.0,
                "jobs": [
                    {"nom": "build", "minutes": 10.0, "sans_objet": False},
                    {"nom": "paquet", "minutes": 15.0, "sans_objet": False},
                ],
            },
        ],
        81.0,
        15.65,
    ),
    (
        "une fenetre ou l emballage se tait",
        [
            {
                "sha": "a",
                "facturees": 46.0,
                "jobs": [
                    {"nom": "build", "minutes": 11.0, "sans_objet": False},
                    {"nom": "paquet", "minutes": 0.2, "sans_objet": True},
                ],
            },
            {
                "sha": "b",
                "facturees": 44.0,
                "jobs": [
                    {"nom": "build", "minutes": 11.0, "sans_objet": False},
                    {"nom": "paquet", "minutes": 0.2, "sans_objet": True},
                ],
            },
        ],
        45.0,
        11.0,
    ),
)


def _auto_test() -> int:
    """Les cas eprouvent la LECTURE, et le bord ou le releve est vide.

    Le dernier cas est le controle negatif du dispositif : un releve vide doit faire REFUSER, jamais
    annoncer zero. Un instrument qui rend « 0 minute » sur une forge muette dirait exactement le
    contraire de ce qu il a mesure.
    """
    import contextlib
    import io
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-minutes-") as tmp:
        fichier = pathlib.Path(tmp) / "releve.json"
        ancien = os.environ.get("RELEVE_DEMANDES_FICHIER")
        os.environ["RELEVE_DEMANDES_FICHIER"] = str(fichier)
        try:
            for libelle, donnees, minutes, attente in CAS:
                fichier.write_text(json.dumps(donnees), encoding="utf-8")
                tampon = io.StringIO()
                with contextlib.redirect_stdout(tampon):
                    code = rendre("peu-importe")
                sortie = tampon.getvalue()
                bon = code == 0 and f"{minutes:6.1f}" in sortie and f"{attente:6.1f}" in sortie
                print(f"  {'✔' if bon else '✘'} {libelle}")
                if not bon:
                    echecs += 1
                    print(sortie)

            # ⟨le repli modelise⟩ Quand `billable` rend zero, le releve doit BASCULER et le dire,
            # jamais annoncer « 0 minute ». Deux jobs de 0,2 et 11,0 min font 1 + 11 = 12 minutes
            # modelisees, l arrondi a la minute etant la regle de facturation de la forge.
            fichier.write_text(
                json.dumps(
                    [
                        {
                            "sha": "a",
                            "facturees": 0,
                            "jobs": [
                                {"nom": "paquet", "minutes": 0.2, "sans_objet": True},
                                {"nom": "build", "minutes": 11.0, "sans_objet": False},
                            ],
                        }
                    ]
                ),
                encoding="utf-8",
            )
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon):
                code = rendre("peu-importe")
            sortie = tampon.getvalue()
            if code == 0 and "MODELISEES" in sortie and "  12.0" in sortie:
                print("  ✔ `billable` a zero fait basculer sur un modele, qui se nomme")
            else:
                print("  ✘ `billable` a zero n a pas fait basculer, ou l arrondi est faux")
                echecs += 1
                print(sortie)

            # Le controle negatif : un releve VIDE refuse de conclure.
            fichier.write_text("[]", encoding="utf-8")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon):
                code = rendre("peu-importe")
            if code == 1 and "REFUSE" in tampon.getvalue():
                print("  ✔ un releve vide REFUSE de conclure, au lieu d annoncer zero")
            else:
                print("  ✘ un releve vide n a pas refuse de conclure")
                echecs += 1

            # ⟨la lecture du « sans objet », eprouvee sur la FONCTION⟩ Ce cas comparait deux
            # dictionnaires litteraux, ce qui ne prouvait rien : il aurait ete vert avec
            # `_sans_objet` supprimee. Il appelle donc la fonction, sur trois formes de job.
            muet = {
                "steps": [
                    {"name": "Ce diff concerne-t-il l emballage ?", "conclusion": "success"},
                    {"name": "Set up JDK 25", "conclusion": "skipped"},
                ]
            }
            juge = {
                "steps": [
                    {"name": "Ce diff concerne-t-il l emballage ?", "conclusion": "success"},
                    {"name": "Set up JDK 25", "conclusion": "success"},
                ]
            }
            # Un job COURT sans pas de portee : bref, mais il a juge. C est la confusion que ce
            # chantier existe pour lever, donc elle a son cas.
            bref = {"steps": [{"name": "Titre de la demande", "conclusion": "success"}]}
            for attendu, libelle, job in (
                (True, "un job dont la portee s est tue est vu muet", muet),
                (False, "un job dont la portee a dit oui n est pas muet", juge),
                (False, "un job COURT sans portee n est pas muet", bref),
            ):
                if _sans_objet(job) is attendu:
                    print(f"  ✔ {libelle}")
                else:
                    print(f"  ✘ {libelle}")
                    echecs += 1
        finally:
            os.environ.pop("RELEVE_DEMANDES_FICHIER", None)
            if ancien is not None:
                os.environ["RELEVE_DEMANDES_FICHIER"] = ancien

    print(f"\n{len(CAS) + 5} cas de lecture et de bord.")
    return 1 if echecs else 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    if len(sys.argv) < 2:
        print("usage : python3 .github/scripts/mesure_minutes_par_pr.py <depot> [fenetre]")
        sys.exit(2)
    sys.exit(rendre(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 40))
