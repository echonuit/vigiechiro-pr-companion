#!/usr/bin/env python3
"""Refuse de fusionner une PR dont le commit de tete ne porte AUCUN verdict (#4571, porte du bash).

    python3 .github/scripts/verifie_verdict_avant_fusion.py --pr 4560

## Le cas d origine

#4560 a ete fusionnee le 26 aout a 17:13:02Z. Son commit de tete `909aeafa8`, pousse a 17:01:25Z, ne
portait alors **aucun run** : les sept qu il a fini par avoir ont ete crees a 17:15:05-06Z, deux
minutes APRES la fusion, liberes par la fin de la panne Actions du jour. La PR a laisse `main` rouge
sur un garde bloquant, et le garde en question n avait pas manque son travail - personne ne lui avait
demande son avis.

## Ce qu il ne fait pas, et pourquoi

**Il ne juge pas la couleur.** L ADR 0041 a tranche que le rouge reste informatif : rendus bloquants,
les checks requis ont casse en une heure les deux chemins par lesquels ce depot ecrit sur `main`. La
raison est structurelle - aucun workflow n est declenche par un evenement produit avec le
`GITHUB_TOKEN`, donc un check requis reste muet sur les PR de bot, et un check requis muet bloque pour
toujours. Ce garde ne ferme que l autre cas, celui qu elle n avait pas prevu : quand il n y a aucune
couleur, il n y a rien a assumer.

**Il n est donc pas un check requis**, et ne peut pas l etre sans repayer ce que l ADR 0041 a mesure.
Il se lance a la main avant de fusionner ; seul son `--auto-test` tourne en CI.

## Ce qu il ne tient pas, et qui est assume

**Une seule forme de la marque de saut.** GitHub en reconnait plusieurs, dont `[ci skip]`. Ce garde
ne cherche que `[skip ci]`, mesure comme la seule employee ici : 46 occurrences sur les 400 derniers
commits de `main`. L asymetrie est du bon cote - un faux refus fait regarder, un faux vert laisse
fusionner.

**`skipped` ne vaut pas verdict.** Un workflow filtre par chemins n a rien juge, et la conclusion est
frequente (12 runs sur 100). Elle ne bloque jamais a elle seule, puisqu elle est terminee.

Il exige que TOUT ait conclu, et non qu un seul run ait parle. Cette seconde version lui vient de sa
propre demande : lance dessus, il l acceptait sur la foi de `Titre de PR` pendant que les gardes
bloquants couraient encore.

Il exige aussi que le DERNIER run de chaque atelier ait juge (#4581). Le tableau final de #4560 porte
quatre ateliers `cancelled` a cote de trois controles legers verts, et ce garde y lisait un verdict
rendu. Un atelier annule n a pas de couleur : le refuser ne juge donc pas la couleur.

Usage : python3 .github/scripts/verifie_verdict_avant_fusion.py --pr <numéro>
        python3 .github/scripts/verifie_verdict_avant_fusion.py <fichier-json-des-runs> [message]
        python3 .github/scripts/verifie_verdict_avant_fusion.py --auto-test
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import tempfile

# Un verdict, c est un run termine dont la conclusion porte sur le CONTENU. `cancelled`, `skipped`,
# `stale` et `startup_failure` sont des fins de course, pas des jugements : le run s est arrete avant
# d avoir quoi que ce soit a dire. Les compter rendrait ce garde vert sur un commit ou rien n a juge.
# Ce filtre ne suffit pas a attraper #4560, dont quatre runs sur sept ont fini `cancelled` a cote de
# trois verts : c est `ateliers_interrompus` qui les voit (#4581).
PROBANTES = ("success", "failure", "neutral", "timed_out", "action_required")

# Une fin de course SUBIE : le run a ete arrete avant d avoir juge. `skipped` n en est pas une,
# l atelier a conclu de lui-meme qu il n avait rien a dire.
INTERROMPUES = ("cancelled", "startup_failure", "stale")


def ateliers_interrompus(liste: list) -> list[str]:
    """Les ateliers dont le DERNIER run sur ce commit a ete interrompu, par leur nom.

    Le dernier, et non un seul : `Corps de PR` et `Titre de PR` sont rejoues a chaque edition de la
    demande, et la concurrence annule alors le run precedent. Refuser sur un seul run interrompu
    refuse trois demandes saines sur 70 (#5894 a #6038, mesure du 2026-10-06) ; cette regle, aucune.

    L atelier se reconnait a son `workflow_id`, pas a son `name`, qui est le titre d EXECUTION des
    qu un atelier porte un `run-name`. Le dernier se lit sur `run_number`, monotone par atelier, et
    non sur `created_at` : deux runs d un meme atelier naissent dans la meme seconde (#5993, #5842).

    Deux runs d un meme atelier dont le numero manque ou ne se compare pas font LEVER, et c est
    voulu : `juger` appelle cette fonction sous son filet, et rend l etat illisible.
    """
    dernier: dict = {}
    for run in liste:
        atelier = run.get("workflow_id", run.get("name"))
        if atelier not in dernier or run["run_number"] > dernier[atelier]["run_number"]:
            dernier[atelier] = run
    return sorted(
        str(run.get("name")) for run in dernier.values() if run.get("conclusion") in INTERROMPUES
    )


def juger(runs: str | pathlib.Path, message: str = "") -> int:
    """Le verdict sur le commit de tete, et le code de sortie qui va avec."""
    chemin = pathlib.Path(runs)
    if not chemin.is_file():
        print(f"Fichier introuvable : {runs}")
        return 2

    # `[skip ci]` est un choix delibere : GitHub ne declenche alors AUCUN workflow, et l absence de
    # verdict est la consequence voulue, pas un accident.
    if "[skip ci]" in message:
        # Dire que la CI est ETEINTE, et non que tout va bien. GitHub lit le message entier, titre
        # et corps : un commit qui se contente de PARLER de la marque l active pour de bon. Vu sur
        # ce depot - un corps citant « hors [skip ci] » a valu zero run la ou le precedent en avait
        # sept. Une PR muette ressemble alors a une PR qui attend.
        print(
            "Aucun run attendu : ce commit porte la marque [skip ci], donc la CI est ÉTEINTE pour lui."
        )
        print(
            "Si ce n'était pas voulu, la marque est quelque part dans le message - GitHub lit le corps"
        )
        print("autant que le titre - et il faut la retirer pour que les workflows repartent.")
        return 0

    # Un garde qui ne sait pas lire REFUSE. Laisser passer sur une reponse illisible le rendrait
    # vert au moment precis ou il sert : la panne qui fait fusionner sans verdict est aussi celle qui
    # fait repondre l API de travers.
    try:
        charge = json.loads(chemin.read_text(encoding="utf-8"))
        liste = charge["workflow_runs"]
        verdicts = sum(
            1 for r in liste if r.get("status") == "completed" and r.get("conclusion") in PROBANTES
        )
        steriles = sum(
            1
            for r in liste
            if r.get("status") == "completed" and r.get("conclusion") not in PROBANTES
        )
        attente = sum(1 for r in liste if r.get("status") != "completed")
        interrompus = ateliers_interrompus(liste)
    except (json.JSONDecodeError, KeyError, TypeError, AttributeError):
        print(
            f"::error title=ÉTAT ILLISIBLE::l'état des runs n'a pas pu être lu dans {runs}. "
            "Ce garde refuse plutôt que de conclure sur ce qu'il n'a pas su lire."
        )
        return 2

    if verdicts == 0:
        print(
            f"::error title=AUCUN VERDICT sur le commit de tête::rien n'a conclu sur ce commit : "
            f"{attente} run(s) en cours ou en attente, {steriles} terminé(s) sans rien juger. "
            "Fusionner ici, ce n'est pas passer outre un rouge, c'est fusionner sans avoir rien vu."
        )
        return 1

    # Un verdict partiel n est pas un verdict. C est le workflow lent qui porte les gardes
    # bloquants, jamais le rapide.
    if attente > 0:
        print(
            f"::error title=PAS TOUT CONCLU sur le commit de tête::{verdicts} run(s) ont rendu un "
            f"verdict, mais {attente} court(ent) encore. Ce sont les workflows lents qui portent les "
            "gardes bloquants."
        )
        return 1

    # Un atelier arrete avant d avoir juge n a rendu aucune couleur : il n y a rien a assumer. C est
    # le cas d une panne ou seuls les ateliers lourds perdent leur runner (#4581).
    if interrompus:
        print(
            f"::error title=ATELIER INTERROMPU sur le commit de tête::{len(interrompus)} atelier(s) "
            "n'ont rien jugé, leur dernier run ayant été interrompu. Les relancer "
            "(`gh run rerun <id>`) avant de fusionner : " + ", ".join(interrompus) + "."
        )
        return 1

    # La COULEUR ne se juge pas ici (ADR 0041).
    print(
        f"Verdict rendu par {verdicts} run(s) terminé(s). Ce garde ne dit rien de leur couleur (ADR 0041)."
    )
    return 0


def juger_la_pr(pr: str) -> int:
    """Va chercher le commit de tete, son message et ses runs, puis delegue au juge.

    Chaque interrogation qui echoue est un REFUS : mieux vaut mourir que juger sur une reponse vide.
    Un repli ajoute ici rendrait le garde vert des que la forge tousse, c est-a-dire exactement quand
    il sert.
    """

    def forge(*arguments: str) -> str:
        rendu = subprocess.run(list(arguments), capture_output=True, text=True, check=False)
        if rendu.returncode != 0:
            raise SystemExit(rendu.returncode)
        return rendu.stdout.strip()

    depot = os.environ.get("GITHUB_REPOSITORY") or forge(
        "gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"
    )
    sha = forge(
        "gh", "pr", "view", pr, "--repo", depot, "--json", "headRefOid", "-q", ".headRefOid"
    )
    message = forge("gh", "api", f"repos/{depot}/commits/{sha}", "--jq", ".commit.message")
    charge = forge("gh", "api", f"repos/{depot}/actions/runs?head_sha={sha}&per_page=100")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as f:
        f.write(charge)
        runs = pathlib.Path(f.name)
    try:
        print(f"PR #{pr}, commit de tête {sha}")
        return juger(runs, message)
    finally:
        runs.unlink(missing_ok=True)


def _etat(*runs: tuple) -> str:
    """L etat d un commit comme l API le rend, reduit aux champs que le juge lit.

    Chaque run : (identifiant d atelier, numero de run, nom, conclusion, date de naissance). L ORDRE
    des arguments est celui de la liste, et il compte : l API rend les plus recents d abord, et deux
    cas ci-dessous n existent que pour le renverser.
    """
    return json.dumps(
        {
            "workflow_runs": [
                {
                    "workflow_id": atelier,
                    "run_number": numero,
                    "name": nom,
                    "status": "completed",
                    "conclusion": conclusion,
                    "created_at": nee,
                }
                for atelier, numero, nom, conclusion, nee in runs
            ]
        }
    )


# ⟨le tableau de #4581, tel que l API le rend⟩ Identifiants, numeros et dates releves le 2026-10-06
# sur `909aeafa8` : quatre ateliers lourds annules, trois controles legers verts.
_TABLEAU_DE_4560 = _etat(
    (299766348, 4284, "docs", "success", "2026-08-26T17:15:06Z"),
    (286171790, 4924, "Quality gate", "cancelled", "2026-08-26T17:15:06Z"),
    (316596334, 1693, "Titre de PR", "success", "2026-08-26T17:15:06Z"),
    (342366035, 113, "Corps de PR", "success", "2026-08-26T17:15:05Z"),
    (289487060, 3824, "Aperçus des vues", "cancelled", "2026-08-26T17:15:05Z"),
    (327342481, 1586, "CodeQL", "cancelled", "2026-08-26T17:15:05Z"),
    (286171791, 5104, "Java CI with Maven", "cancelled", "2026-08-26T17:15:05Z"),
)

# (nom, motif attendu, code attendu, json des runs, message du commit)
CAS = (
    # Le cas d origine : #4560 a ete fusionnee alors que ses runs n avaient pas demarre.
    (
        "aucun run conclu, tout est en attente",
        "AUCUN VERDICT",
        1,
        (
            '{"workflow_runs":[{"name":"Quality gate","status":"queued","conclusion":null},\n'
            '                   {"name":"Java CI with Maven","status":"queued","conclusion":null}]}'
        ),
        "un commit ordinaire",
    ),
    # Le controle de l autre bord, sans lequel le garde pourrait tout refuser et paraitre bon.
    (
        "un verdict conclu suffit, quelle que soit sa couleur",
        "Verdict rendu",
        0,
        '{"workflow_runs":[{"name":"Quality gate","status":"completed","conclusion":"success"}]}',
        "un commit ordinaire",
    ),
    # Le controle qui empeche de rejouer l ADR 0041 : les PR d apercus n ont AUCUN run.
    (
        "un commit [skip ci] est accepté, et dit la CI éteinte",
        "CI est ÉTEINTE",
        0,
        '{"workflow_runs":[]}',
        "chore(captures): mise à jour des aperçus des vues [skip ci]",
    ),
    # Trouve en lancant ce garde sur SA PROPRE demande : un run leger avait conclu, les lourds
    # couraient encore, et il disait « verdict rendu ».
    (
        "un verdict ne suffit pas si le reste court encore",
        "PAS TOUT CONCLU",
        1,
        (
            '{"workflow_runs":[{"name":"Titre de PR","status":"completed","conclusion":"success"},\n'
            '                   {"name":"Quality gate","status":"in_progress","conclusion":null}]}'
        ),
        "un commit ordinaire",
    ),
    (
        "aucun run du tout, sans [skip ci], est REFUSÉ",
        "AUCUN VERDICT",
        1,
        '{"workflow_runs":[]}',
        "un commit ordinaire",
    ),
    # Un run annule n a rien juge. Les lire comme un verdict rendrait ce garde vert exactement sur
    # le cas qu il existe pour attraper.
    (
        "des runs annulés ne valent pas verdict",
        "AUCUN VERDICT",
        1,
        (
            '{"workflow_runs":[{"name":"Quality gate","status":"completed","conclusion":"cancelled"},\n'
            '                   {"name":"CodeQL","status":"completed","conclusion":"startup_failure"}]}'
        ),
        "un commit ordinaire",
    ),
    # Un garde qui ne sait pas lire doit REFUSER, jamais laisser passer.
    (
        "une réponse tronquée fait refuser, pas passer",
        "ÉTAT ILLISIBLE",
        2,
        '{"workflow_runs":[{"name":"Quality',
        "un commit ordinaire",
    ),
    (
        "une réponse d'API sans liste de runs fait refuser",
        "ÉTAT ILLISIBLE",
        2,
        '{"message":"Not Found","status":"404"}',
        "un commit ordinaire",
    ),
    # ⟨#4581⟩ Le cas que le titre de l issue nomme, et que ce garde acceptait sur la foi de trois
    # controles legers : rejoue le 2026-10-06 sur #4560, il sortait en 0.
    (
        "le tableau de #4560 : quatre annulés, trois verts",
        "ATELIER INTERROMPU",
        1,
        _TABLEAU_DE_4560,
        "un commit ordinaire",
    ),
    (
        "ce refus nomme les quatre ateliers annulés",
        "Aperçus des vues, CodeQL, Java CI with Maven, Quality gate.",
        1,
        _TABLEAU_DE_4560,
        "un commit ordinaire",
    ),
    # Le controle de l autre bord : `Corps de PR` et `Titre de PR` sont rejoues a chaque edition de
    # la demande, et la concurrence annule le run precedent. Mesure sur 70 demandes fusionnees,
    # #5894 a #6038 : « un seul run interrompu suffit » en refuse trois, cette regle aucune.
    # L annule est liste EN PREMIER : garder le premier vu ne suffit pas.
    (
        "un atelier annulé puis rejoué vert est accepté",
        "Verdict rendu",
        0,
        _etat(
            (342366035, 1149, "Corps de PR", "cancelled", "2026-10-05T05:29:41Z"),
            (342366035, 1153, "Corps de PR", "success", "2026-10-05T05:45:39Z"),
            (286171790, 6500, "Quality gate", "success", "2026-10-05T05:45:39Z"),
        ),
        "un commit ordinaire",
    ),
    # Les deux runs de #5993, nes dans la meme seconde. La date ne les departage pas : seul le
    # numero dit lequel est le dernier. Dans l ordre de l API, puis renverse, parce qu un ordre pris
    # sur la date tombe juste dans l un des deux selon la facon dont il traite l egalite.
    (
        "deux runs nés dans la même seconde, ordre de l'API",
        "Verdict rendu",
        0,
        _etat(
            (342366035, 1254, "Corps de PR", "success", "2026-10-06T11:53:09Z"),
            (342366035, 1253, "Corps de PR", "cancelled", "2026-10-06T11:53:09Z"),
        ),
        "un commit ordinaire",
    ),
    (
        "deux runs nés dans la même seconde, ordre renversé",
        "Verdict rendu",
        0,
        _etat(
            (342366035, 1253, "Corps de PR", "cancelled", "2026-10-06T11:53:09Z"),
            (342366035, 1254, "Corps de PR", "success", "2026-10-06T11:53:09Z"),
        ),
        "un commit ordinaire",
    ),
    # Le `name` d un run est son TITRE D EXECUTION des que l atelier porte un `run-name` : mesure
    # sur 23 lancements manuels de `suite-sous-windows-et-macos.yml`, dix valeurs distinctes.
    # Regroupe par nom, un run annule puis relance sous un autre titre resterait le dernier du sien.
    (
        "un atelier sous deux titres d'exécution reste un atelier",
        "Verdict rendu",
        0,
        _etat(
            (411, 12, "suite complète sur windows", "success", "2026-10-05T07:10:00Z"),
            (411, 11, "[ciblé] ImportViewTest", "cancelled", "2026-10-05T07:00:00Z"),
        ),
        "un commit ordinaire",
    ),
    # `skipped` n est pas une interruption : l atelier a conclu qu il n avait rien a dire.
    (
        "un atelier sauté à côté d'un vert ne refuse pas",
        "Verdict rendu",
        0,
        _etat(
            (299766348, 7000, "docs", "skipped", "2026-10-05T07:00:00Z"),
            (286171790, 6500, "Quality gate", "success", "2026-10-05T07:00:00Z"),
        ),
        "un commit ordinaire",
    ),
    # Le sens de l ordre : c est le DERNIER run qui compte, pas « au moins un vert par atelier ».
    # Le vert est liste en premier, pour que garder le premier vu ne suffise pas non plus. Et l arret
    # est un `startup_failure` : les trois fins de course subies ont chacune leur cas.
    (
        "un atelier vert puis interrompu est refusé",
        "ATELIER INTERROMPU",
        1,
        _etat(
            (286171790, 6500, "Quality gate", "success", "2026-10-05T07:00:00Z"),
            (286171790, 6501, "Quality gate", "startup_failure", "2026-10-05T07:20:00Z"),
            (316596334, 2733, "Titre de PR", "success", "2026-10-05T07:20:00Z"),
        ),
        "un commit ordinaire",
    ),
    # Un etat sans identifiant d atelier : le nom en tient lieu, et deux noms font deux ateliers.
    (
        "sans identifiant d'atelier, le nom en tient lieu",
        "ATELIER INTERROMPU",
        1,
        (
            '{"workflow_runs":[{"name":"Titre de PR","status":"completed","conclusion":"success"},\n'
            '                   {"name":"Quality gate","status":"completed","conclusion":"stale"}]}'
        ),
        "un commit ordinaire",
    ),
    # Deux runs d un meme atelier que rien ne departage : le garde ne sait pas lequel est le dernier,
    # et un garde qui ne sait pas lire REFUSE.
    (
        "un numéro de run illisible fait refuser, pas passer",
        "ÉTAT ILLISIBLE",
        2,
        _etat(
            (286171790, None, "Quality gate", "success", "2026-10-05T07:00:00Z"),
            (286171790, None, "Quality gate", "cancelled", "2026-10-05T07:20:00Z"),
        ),
        "un commit ordinaire",
    ),
)


def _auto_test() -> int:
    """Les cas de `CAS`, hors ligne : chacun attend un code ET un motif dans ce que le juge ecrit."""
    import contextlib
    import io

    total = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-verdict-") as tmp:
        runs = pathlib.Path(tmp) / "runs.json"
        for nom, motif, code_attendu, charge, message in CAS:
            runs.write_text(charge + "\n", encoding="utf-8")
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                # Un juge qui LEVE est un cas en echec, nomme comme les autres, et non la fin de
                # l auto-test sur une trace : c est ainsi qu un chemin de refus mute se voit rouge.
                try:
                    code = juger(runs, message)
                except Exception as leve:  # noqa: BLE001
                    code = f"a levé {type(leve).__name__}"
            obtenu = tampon.getvalue()
            total += 1
            if motif in obtenu and code == code_attendu:
                print(f"  [OK   ] {nom:<58} -> code {code}")
            else:
                lignes = obtenu.splitlines()
                print(f"  [ÉCHEC] {nom:<58} -> code {code} : {lignes[-1] if lignes else ''}")
                echecs += 1

    print()
    print(f"{total} cas.")
    if echecs != 0:
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : ne pas se fier au verdict de ce script.")
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if sys.argv[1:2] == ["--pr"]:
        if len(sys.argv) < 3:
            print(f"usage: {sys.argv[0]} --pr <numéro de pull request>", file=sys.stderr)
            sys.exit(1)
        sys.exit(juger_la_pr(sys.argv[2]))
    if len(sys.argv) < 2:
        print(
            f"usage: {sys.argv[0]} --pr <numéro> | <fichier-json-des-runs> [message du commit] | --auto-test",
            file=sys.stderr,
        )
        sys.exit(1)
    sys.exit(juger(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else ""))
