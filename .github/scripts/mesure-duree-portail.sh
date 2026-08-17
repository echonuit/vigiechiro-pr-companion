#!/usr/bin/env bash
#
# Surveille l'ALLONGEMENT du portail qualité (#3508).
#
# Une CI riche ne se dégrade jamais d'un coup : chaque ajout coûte trente secondes que personne ne
# remarque, et le temps de retour double en six mois sans qu'aucune PR ne soit fautive. Rien ne le
# mesurait ; les deux seuls `timeout-minutes` du dépôt sont des garde-fous d'exécution, pas un suivi.
#
# ## Ce qu'on compare, et pourquoi pas autre chose
#
# On compare **deux médianes glissantes** - les FENETRE dernières exécutions réussies contre les
# FENETRE d'avant - et non la durée d'une exécution à une médiane.
#
# ⚠️ C'est la mesure qui a imposé ce choix, contre ce que l'issue supposait. Sur trente exécutions :
# médiane 10,9 min, vingt-huit entre 9,7 et 12,1 (écart-type 0,64)... et **deux à 21,8 et 23,7 min**,
# soit le double. L'issue, mesurée sur sept exécutions, n'en avait attrapé aucune et concluait « la
# variance est faible ». Un butoir sur « médiane + 30 % » aurait donc rougi deux fois sur trente sans
# qu'aucune PR soit fautive, et se serait fait relever au troisième coup.
#
# Une médiane, elle, ne bouge pas pour deux valeurs extrêmes sur douze. Mesuré sur la série réelle,
# aberrantes comprises : dérive de +5,3 %, très en deçà du seuil. Le dispositif reste donc muet sur
# l'historique existant - condition sans laquelle un avertisseur s'apprend à ignorer dès le premier
# jour.
#
# ## Il avertit, il ne bloque pas
#
# Un rouge se relève ; un avertissement qui porte la comparaison en clair - « 13,1 min contre 10,9 sur
# les douze précédentes » - se lit et s'instruit. Et il s'exécute sur CHAQUE déclenchement, PR
# comprise : une étape réservée à `main` ne serait jamais exercée par une PR, et pourrait être
# fusionnée cassée (leçon des étapes sous `if: push`).
#
# Usage : mesure-duree-portail.sh <depot> <fichier-workflow> [fenetre]
set -euo pipefail

# ⚠️ Locale numérique figée. Sans elle, `printf '%.1f'` refuse un point décimal sur un poste en
# `fr_FR` (« nombre non valable ») : le script mourrait chez le développeur et marcherait en CI, ce qui
# est la pire des deux façons de casser. Vu en le lançant la première fois.
export LC_ALL=C

# Auto-test (#3661). Doctrine du dépôt : chaque garde de CI porte ses propres cas, lancés par
# `lint.yml`. Ce script est arrivé avec un fichier `bats` à la place - la seule exception des onze -,
# ce qui faisait attendre à ses cas l'assemblage d'un fat-jar de 80 Mo dont ils n'ont aucun besoin.
#
# ⚠️ Le premier cas est celui qui compte : la série RÉELLE du dépôt, ses deux aberrantes comprises,
# doit rester muette. Un avertisseur qui crie sur l'historique existant s'apprend à ignorer dès le
# premier jour, et c'est cette propriété-là qui décide s'il sera lu.
if [ "${1-}" = "--auto-test" ]; then
    echecs=0
    serie=$(mktemp)
    trap 'rm -f "${serie}"' EXIT

    # Le compte des cas et de ceux qui doivent PARLER (#3886).
    #
    # ⚠️ « Rougir » n'a pas de sens ici : cette mesure n'est pas un garde qui refuse, elle avertit ou
    # se tait. Et elle porte TROIS états, pas deux - `court` n'est ni un silence ni une alerte, c'est
    # le refus de conclure faute d'historique. Les fondre en « rouges » ferait disparaître de la
    # ligne l'état le plus facile à casser sans le voir.
    cas=0
    avertit=0
    court=0
    joue() { # <attendu: muet|avertit|court> <libellé> <durées…>
        local attendu="$1" libelle="$2"
        cas=$((cas + 1))
        if [ "$attendu" = avertit ]; then avertit=$((avertit + 1)); fi
        if [ "$attendu" = court ]; then court=$((court + 1)); fi
        shift 2
        printf '[%s]' "$(printf '%s,' "$@" | sed 's/,$//')" > "${serie}"
        sortie=$(SERIE_DUREES_FICHIER="${serie}" "$0" depot/quelconque maven.yml 2>&1)
        obtenu=muet
        printf '%s' "${sortie}" | grep -q "::warning" && obtenu=avertit
        printf '%s' "${sortie}" | grep -q "Pas encore assez d.historique" && obtenu=court
        if [ "${obtenu}" = "${attendu}" ]; then
            echo "  ✔ ${libelle}"
        else
            echo "  ✘ ${libelle} : attendu ${attendu}, obtenu ${obtenu}"
            echecs=1
        fi
    }

    joue muet "la série réelle du dépôt, aberrantes comprises, ne déclenche rien" \
        12.1 10.6 11.2 12.1 21.8 10.8 11.1 11.6 12.1 10.6 10.0 10.6 \
        11.0 10.9 23.7 10.4 11.3 10.7 10.5 11.9 10.2 11.4 10.8 9.7
    joue avertit "une dérive nette au-delà du seuil avertit" \
        14.0 14.2 13.8 14.1 13.9 14.3 14.0 13.7 14.2 14.1 13.8 14.0 \
        11.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1
    # Contrôle NÉGATIF : la règle doit rester étroite. Une seule exécution à plus du double, dans une
    # série par ailleurs stable, ne doit pas suffire - c'est le cas qui a écarté le butoir « médiane
    # + 30 % », et sans lui rien ne distinguerait les deux dispositifs.
    joue muet "une aberrante isolée ne suffit pas à faire crier" \
        24.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1 \
        11.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1
    joue court "sans assez d'historique, il le dit au lieu de conclure" 11.0 10.9 11.2 10.8 11.1

    echo
    if [ "${avertit}" -eq 1 ]; then v1=DOIT; else v1=DOIVENT; fi
    if [ "${court}" -eq 1 ]; then v2=DOIT; else v2=DOIVENT; fi
    echo "${cas} cas, dont ${avertit} qui ${v1} avertir et ${court} qui ${v2} refuser de conclure."
    exit "${echecs}"
fi

DEPOT="${1:?depot attendu, ex. echonuit/vigiechiro-pr-companion}"
WORKFLOW="${2:?fichier de workflow attendu, ex. maven.yml}"
FENETRE="${3:-12}"
SEUIL_POURCENT=20

# ⚠️ Trois tentatives : l'API bafouille, et un appel qui rend la liste attendue peut revenir vide
# trente secondes plus tard. Sans reprise, ce hoquet se lirait « pas d'historique », donc silence -
# et un dispositif muet qui se présente en succès est exactement ce que ce lot corrige (ADR 2748).
insiste() {
  local essai resultat
  for essai in 1 2 3; do
    resultat=$(gh api "$@" 2>/dev/null || true)
    [ -n "$resultat" ] && { printf '%s' "$resultat"; return 0; }
    [ "$essai" -lt 3 ] && sleep 3
  done
  return 1
}

# Le verdict va dans le JOURNAL **et** dans le résumé d'exécution. Le résumé seul ne suffit pas : une
# étape verte dont le journal est vide ne distingue pas « la CI n'a pas dérivé » de « le script s'est
# tu ». Vu sur la première exécution en CI, où le journal ne portait que la ligne de commande.
rendre() {
  printf '%s\n' "$1"
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$1" >> "${GITHUB_STEP_SUMMARY}"
  return 0
}

besoin=$((FENETRE * 2))

# Durées en minutes des exécutions RÉUSSIES sur `main`, de la plus récente à la plus ancienne. Les
# échouées sont écartées : une suite qui s'arrête au premier rouge est courte pour une mauvaise raison.
#
# ⚠️ La série est INJECTABLE par `SERIE_DUREES_FICHIER` (un tableau JSON de minutes), et c'est ce qui
# rend ce script éprouvable. Écrit sans cette couture, il allait chercher ses propres données : aucun
# test ne pouvait lui en fabriquer, et sa vérification se réduisait à trois lancements à la main, qui
# ne se rejouent pas. C'est exactement ce que l'ADR 3624 nomme - un fait que rien ne peut faire rougir.
if [ -n "${SERIE_DUREES_FICHIER:-}" ]; then
  durees=$(cat "${SERIE_DUREES_FICHIER}")
else
  durees=$(insiste "repos/${DEPOT}/actions/workflows/${WORKFLOW}/runs?branch=main&status=success&per_page=${besoin}" \
    --jq '[.workflow_runs[] | select(.run_started_at != null and .updated_at != null)
           | ((.updated_at | fromdateiso8601) - (.run_started_at | fromdateiso8601)) / 60]' || true)
fi

if [ -z "${durees}" ]; then
  echo "::warning title=Durée du portail::Historique des exécutions illisible après trois tentatives."
  exit 0
fi

nombre=$(printf '%s' "${durees}" | jq 'length')
if [ "${nombre}" -lt "${besoin}" ]; then
  rendre "### Durée du portail qualité

Pas encore assez d'historique : ${nombre} exécution(s) réussie(s) sur ${besoin} nécessaires."
  exit 0
fi

# La médiane, et non la moyenne : c'est tout l'objet du dispositif (cf. en-tête).
mediane='def mediane: sort | if length == 0 then null
                             elif length % 2 == 1 then .[length/2 | floor]
                             else (.[length/2 - 1] + .[length/2]) / 2 end;'

recente=$(printf '%s' "${durees}" | jq "${mediane} .[0:${FENETRE}] | mediane")
precedente=$(printf '%s' "${durees}" | jq "${mediane} .[${FENETRE}:${besoin}] | mediane")
derive=$(jq -n "((${recente} / ${precedente}) - 1) * 100")

fmt() { printf '%.1f' "$1"; }
derive_affichee=$(printf '%+.1f' "${derive}")

rendre "### Durée du portail qualité

| Fenêtre | Médiane |
|---|---|
| ${FENETRE} dernières exécutions | **$(fmt "${recente}") min** |
| les ${FENETRE} d'avant | $(fmt "${precedente}") min |
| dérive | **${derive_affichee} %** (seuil d'avertissement : ${SEUIL_POURCENT} %) |

Comparaison de deux **médianes** : une exécution isolément longue ne la déplace pas."

# `jq` rend `true`/`false` ; `|| true` parce qu'un test qui rend faux tuerait le script sous `set -e`
# AVANT le message, et le dispositif se tairait au moment précis où il a quelque chose à dire.
depasse=$(jq -n "${derive} > ${SEUIL_POURCENT}" || true)
if [ "${depasse}" = "true" ]; then
  echo "::warning title=Le portail qualité s'allonge::Médiane $(fmt "${recente}") min sur les ${FENETRE} dernières exécutions, contre $(fmt "${precedente}") min sur les ${FENETRE} précédentes (${derive_affichee} %). Rien n'est bloqué : c'est une tendance à instruire, pas une PR fautive."
fi
