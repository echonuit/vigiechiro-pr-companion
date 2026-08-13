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

besoin=$((FENETRE * 2))

# Durées en minutes des exécutions RÉUSSIES sur `main`, de la plus récente à la plus ancienne. Les
# échouées sont écartées : une suite qui s'arrête au premier rouge est courte pour une mauvaise raison.
durees=$(insiste "repos/${DEPOT}/actions/workflows/${WORKFLOW}/runs?branch=main&status=success&per_page=${besoin}" \
  --jq '[.workflow_runs[] | select(.run_started_at != null and .updated_at != null)
         | ((.updated_at | fromdateiso8601) - (.run_started_at | fromdateiso8601)) / 60]' || true)

if [ -z "${durees}" ]; then
  echo "::warning title=Durée du portail::Historique des exécutions illisible après trois tentatives."
  exit 0
fi

nombre=$(printf '%s' "${durees}" | jq 'length')
if [ "${nombre}" -lt "${besoin}" ]; then
  {
    echo "### Durée du portail qualité"
    echo
    echo "Pas encore assez d'historique : ${nombre} exécution(s) réussie(s) sur ${besoin} nécessaires."
  } >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"
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

{
  echo "### Durée du portail qualité"
  echo
  echo "| Fenêtre | Médiane |"
  echo "|---|---|"
  echo "| ${FENETRE} dernières exécutions | **$(fmt "${recente}") min** |"
  echo "| les ${FENETRE} d'avant | $(fmt "${precedente}") min |"
  echo "| dérive | **${derive_affichee} %** (seuil d'avertissement : ${SEUIL_POURCENT} %) |"
  echo
  echo "Comparaison de deux **médianes** : une exécution isolément longue ne la déplace pas."
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# `jq` rend `true`/`false` ; `|| true` parce qu'un test qui rend faux tuerait le script sous `set -e`
# AVANT le message, et le dispositif se tairait au moment précis où il a quelque chose à dire.
depasse=$(jq -n "${derive} > ${SEUIL_POURCENT}" || true)
if [ "${depasse}" = "true" ]; then
  echo "::warning title=Le portail qualité s'allonge::Médiane $(fmt "${recente}") min sur les ${FENETRE} dernières exécutions, contre $(fmt "${precedente}") min sur les ${FENETRE} précédentes (${derive_affichee} %). Rien n'est bloqué : c'est une tendance à instruire, pas une PR fautive."
fi
