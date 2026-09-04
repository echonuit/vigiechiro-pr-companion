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
# d avoir quoi que ce soit a dire. Les compter rendrait ce garde vert sur le cas meme qu il existe
# pour attraper, les sept runs de #4560 ayant fini `cancelled`.
PROBANTES = ("success", "failure", "neutral", "timed_out", "action_required")


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
)


def _auto_test() -> int:
    """Huit cas hors ligne, dont quatre controles negatifs."""
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
                code = juger(runs, message)
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
