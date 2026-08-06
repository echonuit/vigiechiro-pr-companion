#!/usr/bin/env bash
# Depuis combien de temps le contrat API n'a-t-il pas RÉELLEMENT tourné ? (#2748, lot #2724, chantier #2720)
#
# ## Le défaut corrigé
#
# `api-live.yml` reste vert quand le jeton est expiré ou la plateforme injoignable : c'est voulu, un
# jeton VigieChiro vit 14 jours face à un passage hebdomadaire, il expire donc régulièrement et un
# rouge permanent ne signalerait plus rien. Mais la conséquence est qu'un passage vert ne distingue
# pas « contrat vérifié » de « contrat sauté », et qu'une longue période sans vérification réelle est
# invisible. Mesuré au triage : deux passages verts d'affilée n'avaient rien vérifié, la dernière
# exécution réelle remontant à 16 jours. Personne ne l'avait vu, et c'est le point : il n'y avait rien
# à voir.
#
# ## Ce qu'il fait, et ce qu'il ne stocke pas
#
# Il ne persiste RIEN. L'historique des passages EST la date recherchée : le triage n'a eu besoin
# d'aucun état stocké, seulement de regarder passage par passage si l'étape du contrat avait tourné ou
# été sautée. Un fichier commité, un artefact (90 jours) ou un cache (7 jours) deviendraient chacun
# une seconde chose à surveiller, dont la première panne serait, ici encore, un silence.
#
# Entrée : sur STDIN, une ligne par étape de l'historique, en TSV
#
#     dateISO<TAB>nom de l'étape<TAB>conclusion
#
# Sortie : l'âge de la dernière exécution réelle, et un échec au-delà du seuil.
#
# ## Il refuse de conclure quand c'est LUI qui est cassé
#
# Le détecteur cherche l'étape par son NOM, une chaîne écrite dans `api-live.yml`. La renommer
# casserait la détection en silence, ce qui refabriquerait exactement le défaut corrigé ici. Trois
# refus explicites, donc, plutôt qu'un « 0 jour » rassurant : historique vide, nom introuvable dans
# tout l'historique, et aucune exécution réelle. Une mesure vide n'est pas un zéro.
#
# Usage : ./.github/scripts/veille-contrat-api.sh [--jours-max N] [--joue-maintenant]
#                                                 [--aujourdhui <ISO>] [--auto-test]
set -uo pipefail

# Le nom EXACT de l'étape dans `api-live.yml`. Seul endroit où il est écrit hors du workflow.
ETAPE_CONTRAT='Contrat API (lecture seule)'

# 21 jours = trois passages hebdomadaires manqués. En deçà, c'est la vie normale d'un jeton de 14
# jours ; au-delà, plus personne ne le renouvelle et le contrat n'est plus vérifié du tout.
JOURS_MAX=21

JOUE_MAINTENANT=non
AUJOURDHUI=""

# Une exécution RÉELLE, c'est une étape qui a tourné. `failure` en fait partie : le contrat a bien été
# exercé, il a trouvé une dérive. `skipped` et `cancelled`, non.
REELLE='^(success|failure)$'

horodatage() { date -u -d "$1" +%s 2>/dev/null; }

### Rend l'âge en jours de la dernière exécution réelle, ou un refus motivé.
###
### Écrit son verdict sur la sortie standard et rend 0 (frais) ou 1 (à regarder).
juger() {
  local historique="$1" jours_max="$2" joue_maintenant="$3" maintenant="$4"

  if [ -z "$(printf '%s' "$historique" | tr -d '[:space:]')" ]; then
    echo "❌ Historique vide : aucun passage examiné."
    echo "   Ce n'est pas « le contrat n'a jamais tourné », c'est « la question n'a pas été posée »."
    echo "   Regarder l'appel à l'API GitHub dans api-live.yml (droit actions:read ? workflow renommé ?)."
    return 1
  fi

  local lignes_etape
  lignes_etape=$(printf '%s\n' "$historique" | awk -F'\t' -v e="$ETAPE_CONTRAT" '$2 == e')

  if [ -z "$lignes_etape" ]; then
    echo "❌ L'étape « $ETAPE_CONTRAT » est introuvable dans tout l'historique examiné."
    echo "   C'est le DÉTECTEUR qui est en cause, pas la fraîcheur du contrat : l'étape a"
    echo "   probablement été renommée dans api-live.yml. Reporter le nom dans ETAPE_CONTRAT"
    echo "   (veille-contrat-api.sh), sinon cette veille se tait pour toujours."
    return 1
  fi

  local derniere
  derniere=$(printf '%s\n' "$lignes_etape" | awk -F'\t' -v r="$REELLE" '$3 ~ r {print $1}' | sort -r | head -1)

  if [ "$joue_maintenant" = "oui" ]; then
    echo "✔ Contrat API joué à l'instant : la vérification est fraîche du jour."
    if [ -n "$derniere" ]; then
      echo "  (précédente exécution réelle : $derniere)"
    fi
    return 0
  fi

  if [ -z "$derniere" ]; then
    local examines
    examines=$(printf '%s\n' "$lignes_etape" | wc -l)
    echo "❌ Aucune exécution réelle du contrat dans les $examines derniers passages : tous sautés."
    echo "   Les passages étaient verts et n'ont rien vérifié. Renouveler le jeton :"
    echo "   gh secret set VIGIECHIRO_TOKEN --repo \"\$GITHUB_REPOSITORY\"  (validité 14 jours)"
    return 1
  fi

  local age_secondes age_jours
  age_secondes=$(( maintenant - $(horodatage "$derniere") ))
  age_jours=$(( age_secondes / 86400 ))

  if [ "$age_jours" -gt "$jours_max" ]; then
    echo "❌ Le contrat API n'a plus été vérifié depuis $age_jours jours (dernière fois : $derniere)."
    echo "   Seuil : $jours_max jours, soit $(( jours_max / 7 )) passage(s) hebdomadaire(s) manqué(s)."
    echo "   Les passages d'ici là étaient verts sans rien vérifier. Renouveler le jeton :"
    echo "   gh secret set VIGIECHIRO_TOKEN --repo \"\$GITHUB_REPOSITORY\"  (validité 14 jours)"
    return 1
  fi

  echo "✔ Dernière vérification réelle du contrat : $derniere, il y a $age_jours jour(s) (seuil : $jours_max)."
  return 0
}

# ---------------------------------------------------------------------------------------------
# Autotest : la garde se prouve à chaque exécution.
#
# Les cas rouges sont vérifiés sur leur MESSAGE, pas sur leur code de sortie : un `exit 1` peut venir
# d'une erreur du script lui-même, et c'est déjà arrivé dans ce dépôt de lire un plantage comme une
# détection réussie.
# ---------------------------------------------------------------------------------------------

MAINTENANT_TEST=$(date -u -d '2026-08-06T12:00:00Z' +%s)

# Une ligne d'historique, datée relativement au « maintenant » du test.
ligne() { printf '%s\t%s\t%s\n' "$(date -u -d "@$(( MAINTENANT_TEST - $1 * 86400 ))" +%Y-%m-%dT%H:%M:%SZ)" "$2" "$3"; }

autotest() {
  local echecs=0 sortie code

  # Un cas : nom, historique, attendu (vert|rouge), fragment attendu du message, joue_maintenant.
  verifier() {
    local nom="$1" historique="$2" attendu="$3" fragment="$4" joue="${5:-non}"
    sortie=$(juger "$historique" "$JOURS_MAX" "$joue" "$MAINTENANT_TEST")
    code=$?
    local obtenu=vert
    [ "$code" -ne 0 ] && obtenu=rouge
    if [ "$obtenu" != "$attendu" ]; then
      echo "❌ autotest « $nom » : attendu $attendu, obtenu $obtenu"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=$((echecs + 1))
      return
    fi
    if ! printf '%s' "$sortie" | grep -qF "$fragment"; then
      echo "❌ autotest « $nom » : $obtenu attendu et obtenu, mais le message ne dit pas « $fragment »"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=$((echecs + 1))
    fi
  }

  local frais ancien_25 tous_sautes autre_nom bord_21 bord_22 echec_recent

  frais="$(ligne 3 "$ETAPE_CONTRAT" success)
$(ligne 10 "$ETAPE_CONTRAT" skipped)"
  verifier "joué il y a 3 jours" "$frais" vert "il y a 3 jour(s)"

  ancien_25="$(ligne 25 "$ETAPE_CONTRAT" success)
$(ligne 4 "$ETAPE_CONTRAT" skipped)
$(ligne 11 "$ETAPE_CONTRAT" skipped)"
  verifier "25 jours sans vérification" "$ancien_25" rouge "depuis 25 jours"

  # Le seuil se vérifie des DEUX côtés : une borne n'est fiable que si on l'a vue basculer.
  bord_21="$(ligne 21 "$ETAPE_CONTRAT" success)"
  verifier "21 jours pile, sous le seuil" "$bord_21" vert "il y a 21 jour(s)"
  bord_22="$(ligne 22 "$ETAPE_CONTRAT" success)"
  verifier "22 jours, au-delà du seuil" "$bord_22" rouge "depuis 22 jours"

  # Un contrat qui a tourné et trouvé une dérive A vérifié quelque chose : la fraîcheur est bonne,
  # le rouge de ce passage-là est un autre sujet.
  echec_recent="$(ligne 2 "$ETAPE_CONTRAT" failure)
$(ligne 30 "$ETAPE_CONTRAT" success)"
  verifier "échec récent = vérification réelle" "$echec_recent" vert "il y a 2 jour(s)"

  tous_sautes="$(ligne 3 "$ETAPE_CONTRAT" skipped)
$(ligne 10 "$ETAPE_CONTRAT" skipped)"
  verifier "tous les passages sautés" "$tous_sautes" rouge "tous sautés"

  # Le cas qui empêche cette veille de devenir, à son tour, un silence.
  autre_nom="$(ligne 3 'Contrat API renommé entre-temps' success)
$(ligne 3 'Set up JDK 25' success)"
  verifier "étape renommée" "$autre_nom" rouge "DÉTECTEUR"

  verifier "historique vide" "" rouge "Historique vide"

  # Fraîchement joué : c'est vrai même quand l'historique, lui, est vieux.
  verifier "joué à l'instant" "$ancien_25" vert "joué à l'instant" oui

  if [ "$echecs" -gt 0 ]; then
    echo "Autotest de la veille : $echecs échec(s)."
    return 1
  fi
  echo "Autotest de la veille : OK (9 cas, dont 5 rouges vérifiés sur leur message)."
}

# ---------------------------------------------------------------------------------------------

while [ $# -gt 0 ]; do
  case "$1" in
    --auto-test) autotest; exit $? ;;
    --jours-max) JOURS_MAX="$2"; shift ;;
    --joue-maintenant) JOUE_MAINTENANT=oui ;;
    --aujourdhui) AUJOURDHUI="$2"; shift ;;
    *) echo "option inconnue : $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -n "$AUJOURDHUI" ]; then
  MAINTENANT=$(horodatage "$AUJOURDHUI")
else
  MAINTENANT=$(date -u +%s)
fi

juger "$(cat)" "$JOURS_MAX" "$JOUE_MAINTENANT" "$MAINTENANT"
