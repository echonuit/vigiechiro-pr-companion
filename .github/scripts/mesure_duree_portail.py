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

# ⟨l ecart minimal, en dispersions ROBUSTES⟩ Un ecart de medianes ne se conclut pas sans sa
# dispersion : sur `mutation-ihm.yml`, +59 % annonces valaient 0,42 dispersion, c est-a-dire du bruit.
#
# Le seuil vaut UN, et pour deux raisons qui se rejoignent. La premiere est de sens : un ecart egal a
# la dispersion est un ecart aussi grand que l ecart habituel, ce qui est le minimum pour le dire
# autre chose qu un tirage. La seconde est mesuree, sur les ateliers qui ATTEIGNENT cette regle le
# 2026-10-01 - c est-a-dire au-dela de 20 % de derive, et dont les compositions sont comparables :
#
#     recette-filmee.yml   +224 %   ecart/dispersion 3,24   -> avertit
#     ------------------------------- le trou -------------------------------
#     mutation-ihm.yml      +59 %   ecart/dispersion 0,42   -> se tait
#     mutation-model.yml  +20,5 %   ecart/dispersion 0,41   -> se tait
#
# UN est dans le trou avec de la marge des deux cotes : 2,4 fois au-dessus du plus haut des muets,
# 3,2 fois en dessous du seul parlant. Aucun des deux cotes ne se decide a une decimale.
#
# TROIS POINTS, ET UNE PREMIERE DERIVATION FAUSSE. J avais d abord lu un trou entre 0,42 et 1,01 et
# propose 0,7, en comptant `release.yml` et ses +708 % parmi les parlants. Il n atteint jamais cette
# regle : le refus des compositions non comparables (#5626) l arrete avant, ses fenetres melant
# `schedule` et `push`. Un seuil ajuste sur un cas que la regle ne juge pas aurait ete ajuste sur
# rien. Le chiffre se perimera si la distribution bouge ; d ou cette mesure, datee (ADR 5628).
ECART_MINIMAL_EN_MAD = 1.0

# ⟨la racine, depuis ce fichier⟩ Ce script vit dans `.github/scripts/`, donc deux crans au-dessus.
RACINE = pathlib.Path(__file__).resolve().parents[2]


def insiste(chemin: str) -> list | None:
    """Trois tentatives : l API bafouille.

    Un appel qui rend la liste attendue peut revenir vide trente secondes plus tard. Sans reprise, ce
    hoquet se lirait « pas d historique », donc silence - et un dispositif muet qui se presente en
    succes est exactement ce que ce lot corrige (ADR 2748).
    """
    for essai in (1, 2, 3):
        # ⟨`gh` ABSENT est un silence, et il LEVAIT⟩ `check=False` couvre un code non nul, pas un
        # executable introuvable : `subprocess.run` leve alors un `FileNotFoundError`. Trouve par un
        # cas qui retire `gh` du `PATH` (#5615), et c est le meme defaut que `mesure_minutes_par_pr`
        # portait - deux instruments freres, la meme promesse non tenue.
        try:
            rendu = subprocess.run(
                ["gh", "api", chemin], capture_output=True, text=True, check=False
            )
        except OSError:
            return None
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


def evenements_de(charge: dict) -> list[str]:
    """Le declencheur de chaque execution, dans le meme ordre que `durees_de`.

    Deux listes paralleles plutot qu une liste de couples : la couture d injection
    `SERIE_DUREES_FICHIER` porte des flottants depuis l origine, et changer sa forme casserait les
    cas qui s en servent. Le depot prefere un second sceau a une rupture de contrat.
    """
    evenements = []
    for run in charge.get("workflow_runs") or []:
        if run.get("run_started_at") and run.get("updated_at"):
            evenements.append(run.get("event") or "?")
    return evenements


def composition(evenements: list[str]) -> dict[str, int]:
    """Combien d executions par declencheur, pour DIRE de quoi une fenetre est faite (ADR 5562)."""
    compte: dict[str, int] = {}
    for e in evenements:
        compte[e] = compte.get(e, 0) + 1
    return dict(sorted(compte.items(), key=lambda kv: (-kv[1], kv[0])))


def compositions_comparables(recents: list[str], anciens: list[str]) -> bool:
    """Les deux fenetres sont-elles faites des MEMES declencheurs ?

    Une mediane compare ce qu on lui donne. Si une fenetre est faite de poussees et l autre de
    releases nocturnes, l ecart mesure le changement de POPULATION et non un allongement - et il ne
    se resorbera jamais si le declencheur d avant a ete retire. Vecu sur `release.yml` : douze
    poussees d aout, retirees du bloc `on:` depuis, contre des releases de septembre, soit +709 %.

    L egalite porte sur l ENSEMBLE des declencheurs, pas sur leurs proportions : exiger les memes
    proportions ferait refuser presque toujours, et une release nocturne de plus qu hier ne change
    pas la nature de ce qu on mesure.
    """
    if not recents or not anciens:
        return True
    return set(recents) == set(anciens)


def mediane(valeurs: list[float]) -> float:
    """La mediane, et non la moyenne : c est tout l objet du dispositif."""
    tries = sorted(valeurs)
    n = len(tries)
    return tries[n // 2] if n % 2 else (tries[n // 2 - 1] + tries[n // 2]) / 2


def dispersion_robuste(valeurs: list[float]) -> float:
    """L ecart absolu median : la dispersion qui va avec une MEDIANE.

    L ecart-type ne va pas avec elle, et deux mesures du 2026-10-01 le disent. Groupe sur les 24, il
    inclut le changement de regime lui-meme, donc plus l ecart est reel plus il gonfle : aucun des dix
    ateliers n atteignait 1, `release.yml` et ses +708 % compris, et un seuil la aurait rendu cet
    avertisseur ENTIEREMENT muet. Calcule par fenetre, il reste trompe par une population BIMODALE -
    la fenetre recente de `release.yml` va de 1 a 50 min, donc son ecart-type de 19,5 ecrase un ecart
    de 7,6 min qui est pourtant un triplement.

    Une mediane resiste aux aberrantes (ADR 3560) ; sa dispersion doit y resister aussi.
    """
    centre = mediane(valeurs)
    return mediane([abs(valeur - centre) for valeur in valeurs])


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


def peut_tourner_sur_main(workflow: str, racine: pathlib.Path | None = None) -> bool | None:
    """Cet atelier peut-il produire une execution sur `main` ? `None` si on n a pas pu le lire.

    La question decide de ce qu un historique VIDE veut dire. Un atelier declenche par
    `pull_request` seul n en aura jamais : son vide est une absence NORMALE, pas un echec de lecture,
    et annoncer « illisible » produit un avertissement qu on ne peut jamais lever (#5615).

    **`on:` est lu par YAML comme le booleen True** (norme YAML 1.1). Les deux ecritures sont
    acceptees, comme `verifie_renvois_workflows.py` le fait deja : ce piege est connu du depot, il ne
    se redecouvre pas.
    """
    try:
        import yaml
    except ImportError:
        return None
    base = (racine or RACINE) / ".github" / "workflows"
    chemin = base / workflow
    if not chemin.is_file():
        return None
    try:
        document = yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}
    except (yaml.YAMLError, OSError):
        return None
    declencheurs = document.get("on", document.get(True)) or {}
    if not isinstance(declencheurs, dict):
        declencheurs = dict.fromkeys(declencheurs if isinstance(declencheurs, list) else [])
    # ⟨ce qui produit une execution attribuee a `main`⟩ Une poussee, un horaire, un appel depuis un
    # atelier qui y tourne, un declenchement par une issue - et un **declenchement manuel**, qu on
    # lance depuis une branche, donc souvent `main`.
    #
    # `workflow_dispatch` a failli etre exclu d ici, et la mesure l a rattrape : `comparer-tournages`
    # et `winget` ne portent QUE lui, et rendent pourtant 3 et 1 executions sur `main`. Un predicat
    # derive des declencheurs doit se confronter aux executions reelles, sans quoi il decrit ce qu on
    # croit de la forge et non ce qu elle fait.
    return any(
        d in declencheurs
        for d in (
            "push",
            "schedule",
            "workflow_call",
            "workflow_run",
            "issues",
            "workflow_dispatch",
        )
    )


def mesurer(depot: str, workflow: str, fenetre: int = 12) -> int:
    """La mesure, et 0 quoi qu elle dise : ce dispositif avertit, il ne bloque pas."""
    besoin = fenetre * 2

    injectee = os.environ.get("SERIE_DUREES_FICHIER")
    evenements: list[str] = []
    if injectee:
        durees = json.loads(pathlib.Path(injectee).read_text(encoding="utf-8"))
        # ⟨un SECOND sceau, pour la composition⟩ Le premier porte des flottants depuis l origine ;
        # on ne change pas sa forme, on en ajoute un a cote (#5626).
        injectes = os.environ.get("SERIE_EVENEMENTS_FICHIER")
        if injectes:
            evenements = json.loads(pathlib.Path(injectes).read_text(encoding="utf-8"))
    else:
        charge = insiste(
            f"repos/{depot}/actions/workflows/{workflow}/runs"
            f"?branch=main&status=success&per_page={besoin}"
        )
        durees = durees_de(charge) if charge else None
        evenements = evenements_de(charge) if charge else []

    if not durees:
        # ⟨TROIS issues, pas deux⟩ Un historique vide et une forge muette se ressemblaient : les deux
        # tombaient dans le meme `if`. `charge is None` dit que la forge n a pas repondu apres trois
        # tentatives ; une charge valide et vide dit qu il n y a rien, et les declencheurs disent si
        # c est pour toujours.
        muette = not injectee and charge is None
        if muette:
            print(
                f"::warning title={titre(workflow)}::Historique des exécutions illisible après trois tentatives."
            )
        elif peut_tourner_sur_main(workflow) is False:
            rendre(
                f"### {titre(workflow)}\n\n"
                f"Sans objet : cet atelier ne se déclenche pas sur `main`, donc son historique y est "
                f"vide par construction. Rien à mesurer, et rien à corriger."
            )
        else:
            rendre(
                f"### {titre(workflow)}\n\n"
                f"Pas encore d'historique : aucune exécution réussie sur `main`."
            )
        return 0

    if len(durees) < besoin:
        rendre(
            f"### {titre(workflow)}\n\n"
            f"Pas encore assez d'historique : {len(durees)} exécution(s) réussie(s) sur "
            f"{besoin} nécessaires."
        )
        return 0

    # ⟨DIRE de quoi les deux fenetres sont faites, toujours⟩ L ADR 5562 : une mesure declare la
    # composition de ce qu elle resume. Un lecteur doit pouvoir juger sans relancer la mesure.
    rec_ev, anc_ev = evenements[0:fenetre], evenements[fenetre:besoin]
    if not compositions_comparables(rec_ev, anc_ev):
        rendre(
            f"### {titre(workflow)}\n\n"
            f"**Refus de conclure : les deux fenetres ne sont pas faites des memes declencheurs.**\n\n"
            f"| Fenetre | Composition |\n|---|---|\n"
            f"| {fenetre} dernieres | {composition(rec_ev)} |\n"
            f"| les {fenetre} d avant | {composition(anc_ev)} |\n\n"
            f"L ecart mesurerait le changement de POPULATION et non un allongement. Si le "
            f"declencheur d avant a ete retire du bloc `on:`, il ne se resorbera jamais."
        )
        return 0

    recente = mediane(durees[0:fenetre])
    precedente = mediane(durees[fenetre:besoin])
    derive = ((recente / precedente) - 1) * 100

    # ⟨la dispersion se calcule TOUJOURS, et s affiche toujours⟩ #5626 a appris qu une declaration
    # qu on ne montre que lorsqu elle fait taire ne se relit pas : le lecteur ne sait alors pas si
    # elle a ete regardee. Elle est donc dans le tableau meme quand elle laisse conclure.
    dispersion = max(
        dispersion_robuste(durees[0:fenetre]), dispersion_robuste(durees[fenetre:besoin])
    )
    ecart = abs(recente - precedente)
    # ⟨une dispersion NULLE laisse conclure⟩ Elle dit que la moitie des executions ont la meme duree
    # a la seconde pres : un atelier parfaitement stable qui se deplace EST un signal. Le cas n a pas
    # ete observe le 2026-10-01 - la plus petite dispersion du depot vaut une seconde, sur
    # `critere-de-fin.yml` - mais la forge rend des secondes entieres et un job de moins de dix
    # secondes peut l atteindre. Declare plutot que subi.
    en_dispersions = float("inf") if dispersion == 0 else ecart / dispersion

    rendre(
        f"### {titre(workflow)}\n\n"
        "| Fenêtre | Médiane |\n"
        "|---|---|\n"
        f"| {fenetre} dernières exécutions | **{recente:.1f} min** |\n"
        f"| les {fenetre} d'avant | {precedente:.1f} min |\n"
        f"| dérive | **{derive:+.1f} %** (seuil d'avertissement : {SEUIL_POURCENT} %) |\n\n"
        f"| dispersion | **{dispersion:.1f} min**, et l'écart en vaut "
        f"**{'∞' if en_dispersions == float('inf') else f'{en_dispersions:.2f}'}** "
        f"(minimum pour conclure : {ECART_MINIMAL_EN_MAD}) |\n\n"
        "Comparaison de deux **médianes** : une exécution isolément longue ne la déplace pas, et la "
        "dispersion est l'**écart absolu médian**, qui y résiste comme elle."
        + (
            f"\n\nComposition : {composition(rec_ev)} contre {composition(anc_ev)} - "
            "mêmes déclencheurs des deux côtés, donc l'écart mesure bien une durée."
            if rec_ev and anc_ev
            else ""
        )
    )

    # ⟨l instrument REFUSE de conclure quand la dispersion ecrase l ecart⟩ Troisieme raison de
    # refuser, apres l historique illisible (#5615) et les compositions non comparables (#5626).
    # Celle-ci ne porte pas sur ce qu il LIT mais sur ce qu il CONCLUT : un ecart plus petit que la
    # dispersion de ses propres fenetres est un tirage, pas une derive, et l annoncer fait chercher
    # une cause qui n existe pas (ADR 5628).
    if derive > SEUIL_POURCENT and en_dispersions < ECART_MINIMAL_EN_MAD:
        print(
            f"::notice title={titre(workflow)}::JE NE CONCLUS PAS : l'écart des deux médianes vaut "
            f"{ecart:.1f} min pour une dispersion de {dispersion:.1f} min, soit "
            f"{en_dispersions:.2f} dispersion. Les {derive:+.1f} % annoncés ne se distinguent pas "
            f"d'un tirage, et le minimum pour conclure est {ECART_MINIMAL_EN_MAD}."
        )
        return 0

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
    # ⟨LA DISPERSION QUI ECRASE L ECART⟩ Calque sur `mutation-ihm.yml` : les deux fenetres sont
    # enormement dispersees, les medianes se deplacent de 37 %, et l ecart ne vaut qu une fraction
    # de dispersion. L annoncer fait chercher une cause qui n existe pas (ADR 5628).
    (
        "refuse",
        "une dérive dont l'écart est plus petit que la dispersion ne se conclut pas",
        [1.0, 100.0, 50.0, 120.0, 20.0, 90.0, 30.0, 110.0, 60.0, 15.0, 130.0, 70.0]
        + [1.0, 80.0, 40.0, 90.0, 15.0, 70.0, 25.0, 85.0, 45.0, 10.0, 95.0, 50.0],
    ),
    # ⟨LE SENS INVERSE, et il compte autant⟩ Deux fenetres NETTES dont les centres se deplacent : la
    # regle doit laisser passer. Sans ce cas, refuser pourrait devenir se taire tout court, et
    # j aurais rendu l avertisseur muet au lieu de le rendre juste.
    (
        "avertit",
        "deux fenêtres nettes dont les centres se déplacent avertissent encore",
        [30.0, 31.0, 30.0, 32.0, 31.0, 30.0, 31.0, 32.0, 30.0, 31.0, 30.0, 31.0]
        + [20.0, 21.0, 20.0, 22.0, 21.0, 20.0, 21.0, 22.0, 20.0, 21.0, 20.0, 21.0],
    ),
    # ⟨UNE POPULATION BIMODALE, et c est le cas qui exige une dispersion ROBUSTE⟩ Dix executions
    # serrees autour de 8 et deux a 50 : l ecart absolu median vaut 0,1, l ecart-type une quinzaine.
    # Un quadruplement REEL - de 2 a 8 min - se conclut avec la mediane et s eteindrait avec
    # l ecart-type. Calque sur la fenetre recente de `release.yml`, qui va de 1 a 50 min.
    #
    # Ce cas a ete ajoute parce qu une mutation SURVIVAIT : remplacer la dispersion robuste par
    # `statistics.pstdev` ne faisait rougir personne, alors que c est tout le propos de l ADR 5628.
    (
        "avertit",
        "une fenêtre BIMODALE se juge sur l'écart absolu médian, pas sur l'écart-type",
        [7.9, 8.0, 8.1, 8.0, 7.9, 8.1, 8.0, 7.9, 8.1, 8.0, 50.0, 50.0]
        + [2.0, 2.1, 1.9, 2.0, 2.1, 1.9, 2.0, 2.1, 1.9, 2.0, 2.0, 2.1],
    ),
    # ⟨LA PLUS GRANDE des deux dispersions, et non la plus petite⟩ Conclure demande que les DEUX
    # fenetres soient assez nettes : une fenetre dispersee face a une fenetre serree ne permet pas
    # de dire ou son centre est. Ici la recente a une dispersion de 5 et l ancienne de 0,1, pour un
    # ecart de 3 : avec le maximum on se tait, avec le minimum on crierait.
    #
    # Ajoute pour la meme raison que le precedent - la mutation `max` -> `min` survivait.
    (
        "refuse",
        "une fenêtre dispersée face à une fenêtre nette ne laisse pas conclure",
        [13.0, 3.0, 23.0, 13.0, 3.0, 23.0, 13.0, 3.0, 23.0, 13.0, 13.0, 13.0]
        + [10.0, 10.1, 9.9, 10.0, 10.1, 9.9, 10.0, 10.1, 9.9, 10.0, 10.0, 10.1],
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

    echecs = cas = avertit = court = refuse = 0
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
                if attendu == "refuse":
                    refuse += 1
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
                if "JE NE CONCLUS PAS" in sortie:
                    obtenu = "refuse"
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

    # ⟨la COMPOSITION, et le cas NEGATIF qui protege les vraies derives⟩ Une mediane compare ce
    # qu on lui donne : deux fenetres faites de declencheurs differents mesurent un changement de
    # POPULATION, pas un allongement (#5626). Vecu sur `release.yml`, +709 % entre douze poussees
    # d aout - retirees du bloc `on:` depuis - et des releases de septembre.
    cas += 1
    ok = (
        compositions_comparables(["push"] * 12, ["schedule"] * 12) is False
        and compositions_comparables(["schedule"] * 12, ["schedule"] * 12) is True
        and compositions_comparables(["schedule"] * 6 + ["push"] * 6, ["push", "schedule"]) is True
    )
    if ok:
        print("  ✔ deux fenetres de declencheurs differents ne se comparent pas")
    else:
        print("  ✘ la comparabilite des compositions est mal derivee")
        echecs = 1

    # ⟨le cas NEGATIF⟩ Sans lui, refuser sur une composition differente pourrait devenir refuser
    # tout court, et les deux derives REELLES du depot - `recette-filmee` a +196 %, `mutation-ihm`
    # a +59 % - s eteindraient avec le faux positif.
    cas += 1
    ancien_d = os.environ.get("SERIE_DUREES_FICHIER")
    ancien_e = os.environ.get("SERIE_EVENEMENTS_FICHIER")
    with tempfile.TemporaryDirectory(prefix="vc-compo-") as dossier:
        base = pathlib.Path(dossier)
        (base / "d.json").write_text(json.dumps([60.0] * 12 + [10.0] * 12), encoding="utf-8")
        sorties = {}
        for nom, evs in (("meme", ["schedule"] * 24), ("mixte", ["schedule"] * 12 + ["push"] * 12)):
            (base / f"{nom}.json").write_text(json.dumps(evs), encoding="utf-8")
            os.environ["SERIE_DUREES_FICHIER"] = str(base / "d.json")
            os.environ["SERIE_EVENEMENTS_FICHIER"] = str(base / f"{nom}.json")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                mesurer("depot/quelconque", "maven.yml")
            sorties[nom] = tampon.getvalue()
        for cle, ancien in (
            ("SERIE_DUREES_FICHIER", ancien_d),
            ("SERIE_EVENEMENTS_FICHIER", ancien_e),
        ):
            if ancien is None:
                os.environ.pop(cle, None)
            else:
                os.environ[cle] = ancien
    # ⟨la composition se DIT meme quand elle est homogene⟩ Ce cas a ete ajoute parce qu une
    # mutation SURVIVAIT : retirer la composition du rendu normal ne faisait rougir personne, alors
    # que le critere l exigeait « dans tous les cas ». Un survivant designe le cas qui manque.
    compo_dite = "Composition :" in sorties["meme"]
    avertit_meme = "::warning" in sorties["meme"]
    refuse_mixte = "Refus de conclure" in sorties["mixte"] and "::warning" not in sorties["mixte"]
    if avertit_meme and refuse_mixte and compo_dite:
        print("  ✔ une composition homogene AVERTIT toujours, la DIT, et une mixte refuse")
    else:
        print(
            f"  ✘ homogene avertit={avertit_meme}, composition dite={compo_dite}, "
            f"mixte refuse={refuse_mixte} : le remede a eteint une vraie derive, tu la "
            "composition, ou n a pas eteint le faux positif"
        )
        echecs = 1

    # ⟨RIEN A LIRE n est pas ILLISIBLE, et le predicat se confronte a la forge⟩ Un atelier declenche
    # par `pull_request` seul n aura jamais d execution sur `main`. Annoncer « illisible » y produit
    # un avertissement qu on ne peut JAMAIS lever, et un avertissement inlevable s apprend a ignorer
    # (#5615). Le predicat a ete confronte aux executions reelles des vingt ateliers : 0 desaccord.
    cas += 1
    jamais = [w for w in ("titre-pr.yml", "corps-pr.yml") if peut_tourner_sur_main(w) is not False]
    peuvent = [
        w for w in ("maven.yml", "lint.yml", "winget.yml") if peut_tourner_sur_main(w) is not True
    ]
    if jamais:
        print(f"  ✘ un atelier de `pull_request` seul devrait etre vu sans objet : {jamais}")
        echecs = 1
    elif peuvent:
        print(f"  ✘ un atelier qui tourne sur `main` devrait etre vu comme tel : {peuvent}")
        echecs = 1
    else:
        print("  ✔ « rien a lire » se distingue de « illisible », et `workflow_dispatch` compte")

    # ⟨le cas NEGATIF : le signal utile ne disparait pas avec le bruit⟩ Une forge muette sur un
    # atelier qui PEUT tourner sur `main` doit continuer d avertir. Sans ce cas, le remede de #5615
    # supprimerait l avertissement partout, et la panne reelle deviendrait un silence.
    cas += 1
    chemin_avant = os.environ.get("PATH", "")
    ancien_injecte = os.environ.pop("SERIE_DUREES_FICHIER", None)
    os.environ["PATH"] = "/nonexistant"
    try:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            mesurer("depot/quelconque", "maven.yml")
        muet = tampon.getvalue()
    finally:
        os.environ["PATH"] = chemin_avant
        if ancien_injecte is not None:
            os.environ["SERIE_DUREES_FICHIER"] = ancien_injecte
    if "::warning" in muet and "illisible" in muet:
        print("  ✔ une forge muette sur un atelier qui tourne sur `main` avertit toujours")
    else:
        print(f"  ✘ le remede a supprime l avertissement d une vraie panne : {muet.strip()[:90]!r}")
        echecs = 1

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
    # ⟨chaque etat AFFICHE son compte⟩ Le compteur de `refuse` existait et ne se voyait pas : la
    # ligne ne montrait que `court`, et un etat qu on ne compte pas a voix haute est un etat qu on
    # peut perdre sans que la ligne bouge.
    print(
        f"{cas} cas, dont {avertit} qui {v1} avertir, {court} qui {v2} refuser faute"
        f" d'historique, et {refuse} qui doi{'vent' if refuse > 1 else 't'} refuser parce que la"
        f" dispersion ecrase l ecart."
    )
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
