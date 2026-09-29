#!/usr/bin/env python3
"""Surveille l ALLONGEMENT du portail qualite (#3508, porte du bash.)

Une CI riche ne se degrade jamais d un coup : chaque ajout coute trente secondes que personne ne
remarque, et le temps de retour double en six mois sans qu aucune PR ne soit fautive. Rien ne le
mesurait ; les deux seuls `timeout-minutes` du depot sont des garde-fous d execution, pas un suivi.

## Ce qu on compare, et pourquoi pas autre chose

On compare **deux medianes glissantes** - les FENETRE dernieres executions reussies contre les
FENETRE d avant - et non la duree d une execution a une mediane.

C est la mesure qui a impose ce choix, contre ce que l issue supposait. Sur trente executions :
mediane 10,9 min, vingt-huit entre 9,7 et 12,1 (ecart-type 0,64)... et **deux a 21,8 et 23,7 min**,
soit le double. L issue, mesuree sur sept executions, n en avait attrape aucune et concluait « la
variance est faible ». Un butoir sur « mediane + 30 % » aurait donc rougi deux fois sur trente sans
qu aucune PR soit fautive, et se serait fait relever au troisieme coup.

Une mediane, elle, ne bouge pas pour deux valeurs extremes sur douze. Mesure sur la serie reelle,
aberrantes comprises : derive de +5,3 %, tres en deca du seuil. Le dispositif reste donc muet sur
l historique existant - condition sans laquelle un avertisseur s apprend a ignorer des le premier jour.

## Il avertit, il ne bloque pas

Un rouge se releve ; un avertissement qui porte la comparaison en clair se lit et s instruit. Et il
s execute sur CHAQUE declenchement, PR comprise : une etape reservee a `main` ne serait jamais
exercee par une PR, et pourrait etre fusionnee cassee.

## La couture qui le rend eprouvable

La serie est INJECTABLE par `SERIE_DUREES_FICHIER`, un tableau JSON de minutes. Ecrit sans cette
couture, ce script allait chercher ses propres donnees : aucun test ne pouvait lui en fabriquer, et
sa verification se reduisait a trois lancements a la main, qui ne se rejouent pas (ADR 3624).

Usage : python3 .github/scripts/mesure_duree_portail.py <depot> <fichier-workflow> [fenetre]
        python3 .github/scripts/mesure_duree_portail.py --auto-test
"""

from __future__ import annotations

import datetime as dt
import json
import os
import pathlib
import re
import subprocess
import sys
import time

SEUIL_POURCENT = 20


def insiste(chemin: str) -> list | None:
    """Trois tentatives : l API bafouille.

    Un appel qui rend la liste attendue peut revenir vide trente secondes plus tard. Sans reprise, ce
    hoquet se lirait « pas d historique », donc silence - et un dispositif muet qui se presente en
    succes est exactement ce que ce lot corrige (ADR 2748).
    """
    for essai in (1, 2, 3):
        rendu = subprocess.run(["gh", "api", chemin], capture_output=True, text=True, check=False)
        if rendu.stdout.strip():
            try:
                return json.loads(rendu.stdout)
            except json.JSONDecodeError:
                pass
        if essai < 3:
            time.sleep(3)
    return None


def durees_de(charge: dict) -> list[float]:
    """Les durees en minutes des executions qui portent leurs deux horodatages."""
    minutes = []
    for run in charge.get("workflow_runs") or []:
        debut, fin = run.get("run_started_at"), run.get("updated_at")
        if not debut or not fin:
            continue
        a = dt.datetime.fromisoformat(debut)
        b = dt.datetime.fromisoformat(fin)
        minutes.append((b - a).total_seconds() / 60)
    return minutes


def mediane(valeurs: list[float]) -> float:
    """La mediane, et non la moyenne : c est tout l objet du dispositif."""
    tries = sorted(valeurs)
    n = len(tries)
    return tries[n // 2] if n % 2 else (tries[n // 2 - 1] + tries[n // 2]) / 2


def rendre(texte: str) -> None:
    """Le verdict va dans le JOURNAL et dans le resume d execution.

    Le resume seul ne suffit pas : une etape verte dont le journal est vide ne distingue pas « la CI
    n a pas derive » de « le script s est tu ».
    """
    print(texte)
    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as f:
            f.write(texte + "\n")


def titre(workflow: str) -> str:
    """Le titre du rendu NOMME le workflow mesure, et ce n est pas cosmetique.

    Il etait « Durée du portail qualité » en dur, du temps ou l instrument ne mesurait que
    `maven.yml`. Depuis #5540 il en mesure deux, et deux jobs publiaient le MEME en-tete dont un
    mentait : `lint.yml` n est pas le portail. Un resume qui nomme le mauvais sujet est pire qu un
    resume absent, parce qu on le lit.
    """
    # Le titre ENTIER, non composé : « Durée du » + « atelier des gardes » rend « Durée du atelier »,
    # et l élision ne se devine pas depuis un nom de workflow.
    connus = {
        "maven.yml": "Durée du portail qualité",
        "lint.yml": "Durée de l'atelier des gardes",
    }
    return connus.get(workflow, f"Durée de `{workflow}`")


def mesurer(depot: str, workflow: str, fenetre: int = 12) -> int:
    """La mesure, et 0 quoi qu elle dise : ce dispositif avertit, il ne bloque pas."""
    besoin = fenetre * 2

    injectee = os.environ.get("SERIE_DUREES_FICHIER")
    if injectee:
        durees = json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))
    else:
        charge = insiste(
            f"repos/{depot}/actions/workflows/{workflow}/runs"
            f"?branch=main&status=success&per_page={besoin}"
        )
        durees = durees_de(charge) if charge else None

    if not durees:
        print(
            f"::warning title={titre(workflow)}::Historique des exécutions illisible après trois tentatives."
        )
        return 0

    if len(durees) < besoin:
        rendre(
            f"### {titre(workflow)}\n\n"
            f"Pas encore assez d'historique : {len(durees)} exécution(s) réussie(s) sur "
            f"{besoin} nécessaires."
        )
        return 0

    recente = mediane(durees[0:fenetre])
    precedente = mediane(durees[fenetre:besoin])
    derive = ((recente / precedente) - 1) * 100

    rendre(
        f"### {titre(workflow)}\n\n"
        "| Fenêtre | Médiane |\n"
        "|---|---|\n"
        f"| {fenetre} dernières exécutions | **{recente:.1f} min** |\n"
        f"| les {fenetre} d'avant | {precedente:.1f} min |\n"
        f"| dérive | **{derive:+.1f} %** (seuil d'avertissement : {SEUIL_POURCENT} %) |\n\n"
        "Comparaison de deux **médianes** : une exécution isolément longue ne la déplace pas."
    )

    if derive > SEUIL_POURCENT:
        print(
            f"::warning title=Le portail qualité s'allonge::Médiane {recente:.1f} min sur les "
            f"{fenetre} dernières exécutions, contre {precedente:.1f} min sur les {fenetre} "
            f"précédentes ({derive:+.1f} %). Rien n'est bloqué : c'est une tendance à instruire, "
            "pas une PR fautive."
        )
    return 0


# (attendu, libelle, series). Le premier cas est celui qui compte : la serie REELLE du depot, ses
# deux aberrantes comprises, doit rester muette. Un avertisseur qui crie sur l historique existant
# s apprend a ignorer des le premier jour.
CAS = (
    (
        "muet",
        "la série réelle du dépôt, aberrantes comprises, ne déclenche rien",
        [
            12.1,
            10.6,
            11.2,
            12.1,
            21.8,
            10.8,
            11.1,
            11.6,
            12.1,
            10.6,
            10.0,
            10.6,
            11.0,
            10.9,
            23.7,
            10.4,
            11.3,
            10.7,
            10.5,
            11.9,
            10.2,
            11.4,
            10.8,
            9.7,
        ],
    ),
    (
        "avertit",
        "une dérive nette au-delà du seuil avertit",
        [
            14.0,
            14.2,
            13.8,
            14.1,
            13.9,
            14.3,
            14.0,
            13.7,
            14.2,
            14.1,
            13.8,
            14.0,
            11.0,
            10.9,
            11.2,
            10.8,
            11.1,
            10.7,
            11.3,
            10.9,
            11.0,
            11.2,
            10.8,
            11.1,
        ],
    ),
    # Controle NEGATIF : la regle doit rester etroite. Une seule execution a plus du double, dans
    # une serie par ailleurs stable, ne doit pas suffire - c est le cas qui a ecarte le butoir
    # « mediane + 30 % », et sans lui rien ne distinguerait les deux dispositifs.
    (
        "muet",
        "une aberrante isolée ne suffit pas à faire crier",
        [
            24.0,
            10.9,
            11.2,
            10.8,
            11.1,
            10.7,
            11.3,
            10.9,
            11.0,
            11.2,
            10.8,
            11.1,
            11.0,
            10.9,
            11.2,
            10.8,
            11.1,
            10.7,
            11.3,
            10.9,
            11.0,
            11.2,
            10.8,
            11.1,
        ],
    ),
    (
        "court",
        "sans assez d'historique, il le dit au lieu de conclure",
        [11.0, 10.9, 11.2, 10.8, 11.1],
    ),
)


def invocations(racine: pathlib.Path | None = None) -> list[tuple[str, str]]:
    """Les couples (atelier qui LANCE, workflow MESURE), lus dans les ateliers.

    Derives et non enumeres : une liste ecrite ici se perimerait au premier pas ajoute, et c est
    exactement ce que ce dispositif reproche aux inventaires tenus a la main.
    """
    base = (racine or pathlib.Path(__file__).resolve().parents[2]) / ".github" / "workflows"
    motif = re.compile(r"mesure_duree_portail\.py[^\n]*?\s([a-z0-9_-]+\.yml)")
    couples = []
    for atelier in sorted(base.glob("*.yml")):
        for mesure in motif.findall(atelier.read_text(encoding="utf-8")):
            couples.append((atelier.name, mesure))
    return couples


def auto_mesure(racine: pathlib.Path | None = None) -> list[str]:
    """Les ateliers qui se mesurent EUX-MEMES, ce qui rend la mesure aveugle a son propre cout.

    Un atelier qui se mesure entre dans la fenetre qu il calcule, et ce n est pas une affaire de
    magnitude : un classement decale d une unite une fois, une RETROACTION se compose, chaque
    execution nourrissant la suivante. L instrument ne peut alors plus separer son propre cout de la
    derive qu il annonce.
    """
    return [f"{ou} mesure {quoi}" for ou, quoi in invocations(racine) if ou == quoi]


def _auto_test() -> int:
    """Quatre series connues, et TROIS etats : muet, avertit, ou refuse de conclure.

    « Rougir » n a pas de sens ici : cette mesure n est pas un garde qui refuse. Et elle porte trois
    etats, pas deux - `court` n est ni un silence ni une alerte, c est le refus de conclure faute
    d historique. Les fondre ferait disparaitre de la ligne l etat le plus facile a casser sans le voir.
    """
    import contextlib
    import io
    import tempfile

    echecs = cas = avertit = court = 0
    with tempfile.TemporaryDirectory(prefix="vc-portail-") as tmp:
        serie = pathlib.Path(tmp) / "serie.json"
        ancien = os.environ.get("SERIE_DUREES_FICHIER")
        os.environ["SERIE_DUREES_FICHIER"] = str(serie)
        try:
            for attendu, libelle, durees in CAS:
                cas += 1
                if attendu == "avertit":
                    avertit += 1
                if attendu == "court":
                    court += 1
                serie.write_text(json.dumps(durees), encoding="utf-8")
                tampon = io.StringIO()
                with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                    mesurer("depot/quelconque", "maven.yml")
                sortie = tampon.getvalue()
                obtenu = "muet"
                if "::warning" in sortie:
                    obtenu = "avertit"
                if "Pas encore assez d'historique" in sortie:
                    obtenu = "court"
                if obtenu == attendu:
                    print(f"  ✔ {libelle}")
                else:
                    print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                    echecs = 1
        finally:
            if ancien is None:
                os.environ.pop("SERIE_DUREES_FICHIER", None)
            else:
                os.environ["SERIE_DUREES_FICHIER"] = ancien

    # ⟨le titre est de la prose, donc il s eprouve⟩ Il etait en dur du temps d un seul workflow, et
    # deux jobs publiaient le meme en-tete dont un mentait. Le cas tient aussi l elision, qu un titre
    # compose rendait faux : « Durée du atelier des gardes ».
    cas += 1
    attendus = {
        "maven.yml": "Durée du portail qualité",
        "lint.yml": "Durée de l'atelier des gardes",
    }
    faux_titres = [f"{w} -> {titre(w)}" for w, a in attendus.items() if titre(w) != a]
    if faux_titres:
        print(f"  ✘ le titre nomme le workflow mesure : {', '.join(faux_titres)}")
        echecs = 1
    elif titre("inconnu.yml") == attendus["maven.yml"]:
        print("  ✘ un workflow inconnu ne doit pas heriter du titre du portail")
        echecs = 1
    else:
        print(f"  ✔ le titre nomme le workflow mesure ({titre('lint.yml')})")

    # ⟨le cas que #5540 ajoute, et il ne porte pas sur l arithmetique⟩ Les quatre series ci-dessus
    # prouvent que l instrument PARLE quand il doit. Elles ne disent rien de l endroit d ou il
    # regarde, et c est la que le placement peut casser la mesure en silence : un pas deplace dans
    # l atelier qu il mesure paraitrait plus lisible et rendrait la fenetre aveugle a son propre
    # cout. Vert aujourd hui par construction ; il rougira le jour du deplacement.
    # Le cas affirme DEUX choses, et la premiere n y etait pas a la premiere ecriture : la population
    # lue n est pas vide. Sans elle, vider la derivation rendait zero coupable, donc un VERT - et une
    # mutation de `invocations` passait sans rien faire rougir. C est la forme exacte de #5500, « deux
    # listes vides sont disjointes », reproduite ici par celui qui venait de la citer.
    cas += 1
    vues = invocations()
    coupables = auto_mesure()
    if not vues:
        print("  ✘ la derivation ne lit aucune invocation : le cas suivant serait vert a vide")
        echecs = 1
    elif coupables:
        print(f"  ✘ aucun atelier ne se mesure lui-meme : {', '.join(coupables)}")
        echecs = 1
    else:
        vus = ", ".join(f"{ou}->{quoi}" for ou, quoi in vues)
        print(f"  ✔ {len(vues)} invocation(s) lue(s), aucun atelier ne se mesure lui-meme ({vus})")

    print()
    v1 = "DOIT" if avertit == 1 else "DOIVENT"
    v2 = "DOIT" if court == 1 else "DOIVENT"
    print(f"{cas} cas, dont {avertit} qui {v1} avertir et {court} qui {v2} refuser de conclure.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) < 3:
        print(
            "depot attendu, ex. echonuit/vigiechiro-pr-companion ; puis le fichier de workflow, ex. maven.yml",
            file=sys.stderr,
        )
        sys.exit(1)
    sys.exit(mesurer(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 12))
