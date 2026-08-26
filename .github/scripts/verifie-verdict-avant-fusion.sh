#!/usr/bin/env bash
#
# Refuse de fusionner une pull request dont le commit de tête ne porte AUCUN verdict (#4571).
#
#     ./.github/scripts/verifie-verdict-avant-fusion.sh --pr 4560
#
# ## Le cas d'origine
#
# #4560 a été fusionnée le 26 août à 17:13:02Z. Son commit de tête `909aeafa8`, poussé à 17:01:25Z,
# ne portait alors **aucun run** : les sept qu'il a fini par avoir ont été créés à 17:15:05-06Z,
# deux minutes APRÈS la fusion, libérés par la fin de la panne Actions du jour. La PR a laissé
# `main` rouge sur un garde bloquant, et le garde en question n'avait pas manqué son travail -
# personne ne lui avait demandé son avis.
#
# ## Ce qu'il ne fait pas, et pourquoi
#
# **Il ne juge pas la couleur.** L'ADR 0041 a tranché que le rouge reste informatif : rendus
# bloquants, les checks requis ont cassé en une heure les deux chemins par lesquels ce dépôt écrit
# sur `main`, dont la chaîne de publication. La raison est structurelle et n'a pas bougé : aucun
# workflow n'est déclenché par un événement produit avec le `GITHUB_TOKEN`, donc un check requis
# reste muet sur les PR de bot - et un check requis muet bloque pour toujours.
#
# Ce que cette ADR assume est un rouge **visible** qu'on choisit d'ignorer. Ce garde ne ferme que
# l'autre cas, celui qu'elle n'avait pas prévu : quand il n'y a aucune couleur, il n'y a rien à
# assumer. Il complète cette décision, il ne la rouvre pas.
#
# **Il n'est donc pas un check requis**, et ne peut pas l'être sans repayer ce que l'ADR 0041 a
# mesuré. Il se lance à la main avant de fusionner. Seul son `--auto-test` tourne en CI.
#
# ## Ce qui le tient
#
# Huit cas, dont quatre contrôles négatifs - le témoin vert, la porte `[skip ci]`, et les deux
# réponses illisibles. Les cinq mutations montées contre lui (retirer la distinction entre
# conclusion probante et fin de course, le refus quand rien n'a conclu, celui quand des runs courent
# encore, la porte `[skip ci]`, le fail-closed) le font rougir. Éprouvé sur les 40 dernières PR
# fusionnées, chacune jugée dans l'état où elle était à l'instant de sa fusion : il en refuse une,
# #4560, et accepte les 39 autres.
#
# ## Ce qu'il ne tient pas, et qui est assumé
#
# **Une seule forme de la marque de saut.** GitHub en reconnaît plusieurs, dont `[ci skip]`. Ce
# garde ne cherche que `[skip ci]`, mesuré comme la seule employée ici : 46 occurrences sur les 400
# derniers commits de `main`, toutes sous cette forme, toutes produites par `capture-vues.yml`. Un
# commit portant une autre variante serait donc REFUSÉ à tort. L'asymétrie est du bon côté - un
# faux refus fait regarder, un faux vert laisse fusionner - et le cas ne s'est jamais produit.
#
# **`skipped` ne vaut pas verdict.** Un workflow filtré par chemins n'a rien jugé, et la conclusion
# est fréquente (12 runs sur 100). Elle ne bloque jamais à elle seule, puisqu'elle est terminée :
# son seul effet est de ne pas compter. Aucune des 40 PR mesurées n'a été refusée pour ce motif.
#
# Il exige que TOUT ait conclu, et non qu'un seul run ait parlé. Cette seconde version lui vient de
# sa propre demande : lancé dessus, il l'acceptait sur la foi de `Titre de PR` pendant que les
# gardes bloquants couraient encore. Exiger le tout n'a refusé aucune PR de plus sur les 40.
set -euo pipefail
export LC_ALL=C

auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    # shellcheck disable=SC2064
    trap "rm -rf '${bac}'" RETURN

    essai() { # <nom> <motif attendu> <code attendu> <json des runs> [message du commit]
        local nom="$1" motif="$2" code_attendu="$3" json="$4" message="${5:-un commit ordinaire}" obtenu code
        printf '%s\n' "${json}" > "${bac}/runs.json"
        obtenu=$(bash "$0" "${bac}/runs.json" "${message}" 2>&1) && code=0 || code=$?
        total=$((total + 1))
        if printf '%s' "${obtenu}" | grep -qF "${motif}" && [ "${code}" = "${code_attendu}" ]; then
            printf '  [OK   ] %-58s -> code %s\n' "${nom}" "${code}"
        else
            printf '  [ÉCHEC] %-58s -> code %s : %s\n' "${nom}" "${code}" "$(printf '%s' "${obtenu}" | tail -1)"
            echecs=$((echecs + 1))
        fi
    }

    echo "AUTO-TEST"
    # Le cas d'origine : #4560 a été fusionnée alors que ses runs n'avaient pas démarré.
    essai "aucun run conclu, tout est en attente" "AUCUN VERDICT" 1 \
        '{"workflow_runs":[{"name":"Quality gate","status":"queued","conclusion":null},
                           {"name":"Java CI with Maven","status":"queued","conclusion":null}]}'
    # Le contrôle de l'autre bord, sans lequel le garde pourrait tout refuser et paraître bon.
    essai "un verdict conclu suffit, quelle que soit sa couleur" "Verdict rendu" 0 \
        '{"workflow_runs":[{"name":"Quality gate","status":"completed","conclusion":"success"}]}'
    # Le contrôle qui empêche de rejouer l'ADR 0041 : les PR d'aperçus n'ont AUCUN run, par
    # construction, et les refuser casserait le chemin d'écriture que cette ADR a mesuré.
    essai "un commit [skip ci] est accepté, et dit la CI éteinte" "CI est ÉTEINTE" 0 \
        '{"workflow_runs":[]}' "chore(captures): mise à jour des aperçus des vues [skip ci]"
    # Trouvé en lançant ce garde sur SA PROPRE demande : un run léger avait conclu, `Quality gate`
    # et `Java CI` couraient encore, et il disait « verdict rendu ». Fusionner là refaisait #4560 à
    # une nuance près. Mesuré ensuite sur les 40 dernières PR : exiger que TOUT ait conclu n'en
    # refuse pas une de plus, la pratique du dépôt étant déjà celle-là. Le durcissement est gratuit.
    essai "un verdict ne suffit pas si le reste court encore" "PAS TOUT CONCLU" 1 \
        '{"workflow_runs":[{"name":"Titre de PR","status":"completed","conclusion":"success"},
                           {"name":"Quality gate","status":"in_progress","conclusion":null}]}'
    # Le cas de #4560 au plus près : le commit de tête n'avait PAS UN SEUL run à 17:13:02Z.
    essai "aucun run du tout, sans [skip ci], est REFUSÉ" "AUCUN VERDICT" 1 \
        '{"workflow_runs":[]}'
    # Un run annulé n'a rien jugé. Sur `fe78c1a7d`, `Java CI` était `cancelled` par concurrence, et
    # les sept runs de #4560 le sont devenus deux minutes après la fusion : les lire comme un
    # verdict rendrait ce garde vert exactement sur le cas qu'il existe pour attraper.
    essai "des runs annulés ne valent pas verdict" "AUCUN VERDICT" 1 \
        '{"workflow_runs":[{"name":"Quality gate","status":"completed","conclusion":"cancelled"},
                           {"name":"CodeQL","status":"completed","conclusion":"startup_failure"}]}'
    # Un garde qui ne sait pas lire doit REFUSER, jamais laisser passer. La panne qui a produit
    # #4560 est aussi celle où l'API rend des erreurs : tomber en marche passante rendrait ce garde
    # vert précisément au moment où il sert. C'est le défaut de #4544 sous une autre forme.
    essai "une réponse tronquée fait refuser, pas passer" "ÉTAT ILLISIBLE" 2 \
        '{"workflow_runs":[{"name":"Quality'
    essai "une réponse d'API sans liste de runs fait refuser" "ÉTAT ILLISIBLE" 2 \
        '{"message":"Not Found","status":"404"}'

    echo
    echo "${total} cas."
    if [ "${echecs}" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au verdict de ce script."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

# Le mode d'emploi réel : `--pr <numéro>`, juste avant de fusionner. Il va chercher lui-même le
# commit de tête, son message et ses runs, puis délègue au juge ci-dessous - qui, lui, ne connaît que
# des fichiers, et reste donc jouable hors ligne par l'auto-test.
if [ "${1:-}" = "--pr" ]; then
    # `set -e` fait de chacune des trois interrogations ci-dessous un refus quand elle échoue : le
    # script meurt sur un code non nul plutôt que de juger sur une réponse vide. C'est voulu, et
    # c'est fragile à la relecture - un `|| true` ajouté ici rendrait le garde vert dès que la forge
    # tousse, c'est-à-dire exactement quand il sert.
    pr="${2:?usage: $0 --pr <numéro de pull request>}"
    depot="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
    sha=$(gh pr view "${pr}" --repo "${depot}" --json headRefOid -q .headRefOid)
    message=$(gh api "repos/${depot}/commits/${sha}" --jq '.commit.message')
    runs=$(mktemp)
    # shellcheck disable=SC2064
    trap "rm -f '${runs}'" EXIT
    gh api "repos/${depot}/actions/runs?head_sha=${sha}&per_page=100" > "${runs}"
    echo "PR #${pr}, commit de tête ${sha}"
    bash "$0" "${runs}" "${message}"
    exit $?
fi

RUNS="${1:?usage: $0 --pr <numéro> | <fichier-json-des-runs> [message du commit] | --auto-test}"
MESSAGE="${2:-}"
[ -f "${RUNS}" ] || { echo "Fichier introuvable : ${RUNS}"; exit 2; }

# `[skip ci]` est un choix délibéré : GitHub ne déclenche alors AUCUN workflow, et l'absence de
# verdict est la conséquence voulue, pas un accident. Les PR d'aperçus de `capture-vues.yml` sont
# exactement dans ce cas. Les refuser reviendrait à casser un chemin d'écriture vers `main`, ce que
# l'ADR 0041 a déjà mesuré et payé.
if printf '%s' "${MESSAGE}" | grep -qF '[skip ci]'; then
    # Dire que la CI est ÉTEINTE, et non que tout va bien. GitHub lit le message entier, titre et
    # corps : un commit qui se contente de PARLER de la marque l'active pour de bon. Vu sur ce
    # dépôt - un corps de commit citant « hors [skip ci] » a valu zéro run là où le commit
    # precedent en avait sept. Une PR muette ressemble alors à une PR qui attend.
    echo "Aucun run attendu : ce commit porte la marque [skip ci], donc la CI est ÉTEINTE pour lui."
    echo "Si ce n'était pas voulu, la marque est quelque part dans le message - GitHub lit le corps"
    echo "autant que le titre - et il faut la retirer pour que les workflows repartent."
    exit 0
fi

# Un verdict, c'est un run terminé dont la conclusion porte sur le CONTENU. `cancelled`,
# `skipped`, `stale` et `startup_failure` sont des fins de course, pas des jugements : le run s'est
# arrêté avant d'avoir quoi que ce soit à dire. Les compter comme verdicts rendrait ce garde vert
# sur le cas même qu'il existe pour attraper, puisque les sept runs de #4560 ont fini `cancelled`.
PROBANTES='["success","failure","neutral","timed_out","action_required"]'

# Un garde qui ne sait pas lire REFUSE. Laisser passer sur une réponse illisible le rendrait vert
# au moment précis où il sert : la panne qui fait fusionner sans verdict est aussi celle qui fait
# répondre l'API de travers. C'est le défaut de #4544 - un compte rendu tronqué qui se déclare
# complet - sous une autre forme, et il vaut ici la même réponse.
if ! comptes=$(jq -r --argjson probantes "${PROBANTES}" '
    def a_juge: .status == "completed" and (.conclusion as $c | $probantes | index($c) != null);
    def s_est_arrete: .status == "completed" and (.conclusion as $c | $probantes | index($c) == null);
    [ ([.workflow_runs[] | select(a_juge)]                 | length),
      ([.workflow_runs[] | select(s_est_arrete)]           | length),
      ([.workflow_runs[] | select(.status != "completed")] | length)
    ] | @tsv' "${RUNS}" 2>/dev/null); then
    echo "::error title=ÉTAT ILLISIBLE::l'état des runs n'a pas pu être lu dans ${RUNS}. Ce garde refuse plutôt que de conclure sur ce qu'il n'a pas su lire."
    exit 2
fi
read -r verdicts steriles attente <<< "${comptes}"

if [ "${verdicts}" -eq 0 ]; then
    echo "::error title=AUCUN VERDICT sur le commit de tête::rien n'a conclu sur ce commit : ${attente} run(s) en cours ou en attente, ${steriles} terminé(s) sans rien juger. Fusionner ici, ce n'est pas passer outre un rouge, c'est fusionner sans avoir rien vu."
    exit 1
fi

# Un verdict partiel n'est pas un verdict. Ce garde a été trouvé trop indulgent en le lançant sur sa
# propre demande : `Titre de PR` avait conclu, `Quality gate` et `Java CI` couraient encore, et il
# annonçait « verdict rendu ». Fusionner là aurait refait #4560 à une nuance près - et c'est le
# workflow lent qui porte les gardes bloquants, jamais le rapide. Mesuré sur les 40 dernières PR
# fusionnées : exiger que tout ait conclu n'en refuse pas une de plus.
if [ "${attente}" -gt 0 ]; then
    echo "::error title=PAS TOUT CONCLU sur le commit de tête::${verdicts} run(s) ont rendu un verdict, mais ${attente} court(ent) encore. Ce sont les workflows lents qui portent les gardes bloquants."
    exit 1
fi

# La COULEUR ne se juge pas ici. L'ADR 0041 a décidé que le rouge reste informatif et que passer
# outre est un choix assumé ; ce garde ne ferme que le cas où il n'y a rien à assumer.
echo "Verdict rendu par ${verdicts} run(s) terminé(s). Ce garde ne dit rien de leur couleur (ADR 0041)."
exit 0
