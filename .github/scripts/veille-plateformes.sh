#!/usr/bin/env bash
#
# La preuve que la suite passe sous Windows et macOS est-elle **encore fraîche** ? (#3526, étape 4)
#
# ## Pourquoi une veille en plus du job programmé
#
# Le job tourne le mardi. Mais un `schedule` GitHub peut être **retardé**, **sauté sous charge**, et il
# est **désactivé automatiquement** après soixante jours sans activité sur le dépôt. Sans veille, son
# silence se confondrait avec son succès - et le train de publication du mercredi partirait sur une
# preuve qui n'existe plus. C'est l'ADR 2748 : un dispositif qui peut ne rien vérifier doit le dire.
#
# ## Il ne persiste rien, et c'est le point
#
# L'historique des exécutions **est** la date recherchée. Reprend le raisonnement de
# `veille-contrat-api.sh`, dont c'est la trouvaille : un fichier commité ajouterait un commit de CI par
# passage, un artefact expire à 90 jours et un cache à 7 - chacun deviendrait une seconde vérité à
# tenir à jour.
#
# ## Entrée
#
# Sur STDIN, une ligne par exécution, en TSV, de la plus récente à la plus ancienne :
#
#     dateISO<TAB>conclusion<TAB>déclencheur
#
# ⚠️ Seules les exécutions **programmées** comptent. Une exécution manuelle peut être **ciblée** depuis
# #3754 - deux classes au lieu de 4600 -, et l'API des runs ne dit pas quelles entrées lui ont été
# passées. La compter certifierait la fraîcheur de la suite entière sur la preuve de deux classes ;
# c'est exactement le malentendu que l'en-tête du tri évite un étage plus bas. Vu en branchant la
# veille sur les vraies données du dépôt, où elle a validé un passage ciblé.
#
# Une exécution **réelle et concluante**, ici, c'est `success` et lui seul - à la différence de
# `veille-contrat-api.sh`, où un `failure` prouve que le contrat a bien été exercé. Le job de
# plateformes, depuis #3526, **conclut** : il rougit quand la suite ne passe pas. Un `failure` y est
# donc l'inverse d'une preuve, et le compter rendrait la veille verte au moment précis où la suite est
# cassée.
#
# Usage : ./.github/scripts/veille-plateformes.sh [--jours-max N]
set -uo pipefail
export LC_ALL=C

# 10 jours = un passage hebdomadaire manqué, plus la marge d'un décalage de `schedule`. En deçà, c'est
# la vie normale ; au-delà, le train de mercredi s'apprêterait à publier sans preuve de la semaine.
JOURS_MAX=10

while [ $# -gt 0 ]; do
    case "$1" in
        --jours-max) JOURS_MAX="$2"; shift 2 ;;
        --auto-test) AUTO_TEST=oui; shift ;;
        *) echo "Option inconnue : $1" >&2; exit 2 ;;
    esac
done

horodatage() { date -u -d "$1" +%s 2>/dev/null; }

### Rend le verdict sur la sortie standard, et 0 (frais) ou 1 (à regarder).
juger() {
    local historique="$1" jours_max="$2" maintenant="$3"

    if [ -z "$(printf '%s' "${historique}" | tr -d '[:space:]')" ]; then
        echo "❌ Historique vide : aucune exécution examinée."
        echo "   Ce n'est pas « la suite n'a jamais tourné », c'est « la question n'a pas été posée »."
        echo "   Regarder l'appel à l'API GitHub (droit actions:read ? workflow renommé ?)."
        return 1
    fi

    local derniere
    derniere=$(printf '%s\n' "${historique}" \
        | awk -F'\t' '$2 == "success" && $3 == "schedule" {print $1}' | sort -r | head -1)

    if [ -z "${derniere}" ]; then
        local examinees
        examinees=$(printf '%s\n' "${historique}" | grep -c . || true)
        echo "❌ Aucune exécution PROGRAMMÉE et réussie parmi les ${examinees} examinées."
        echo "   ⚠️ Les passages manuels ne comptent pas : depuis #3754 ils peuvent être ciblés sur"
        echo "   quelques classes, et l'API ne dit pas lesquelles. Un passage ciblé n'est pas une preuve."
        echo "   Une suite qui échoue à chaque passage n'est pas une preuve périmée, c'est une preuve"
        echo "   absente : le train ne doit pas partir davantage que si le job ne tournait plus."
        return 1
    fi

    local instant age
    instant=$(horodatage "${derniere}")
    if [ -z "${instant}" ]; then
        echo "❌ Date illisible dans l'historique : « ${derniere} »."
        echo "   Un format inattendu se lirait « très ancien » ou « tout frais » selon le hasard du"
        echo "   calcul : on refuse plutôt que de deviner."
        return 1
    fi
    age=$(( (maintenant - instant) / 86400 ))

    if [ "${age}" -gt "${jours_max}" ]; then
        echo "❌ Dernière preuve réelle il y a ${age} jours (${derniere}), au-delà des ${jours_max} tolérés."
        echo "   Le `schedule` du mardi a peut-être été sauté, retardé, ou désactivé après soixante"
        echo "   jours sans activité sur le dépôt. Relancer « Suite complète sous Windows et macOS »."
        return 1
    fi

    echo "✔ Suite éprouvée sous Windows et macOS il y a ${age} jour(s) (${derniere})."
    return 0
}

if [ "${AUTO_TEST:-non}" = "oui" ]; then
    echecs=0
    MAINTENANT=$(date -u -d "2026-08-14T12:00:00Z" +%s)
    verifie() { # <attendu> <libellé> <historique>
        local code=0
        juger "$3" 10 "${MAINTENANT}" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $2"
        else
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }
    verifie 0 "une preuve d'hier est fraîche" "$(printf '2026-08-13T06:00:00Z\tsuccess\tschedule')"
    verifie 1 "une preuve de trois semaines ne l'est plus" "$(printf '2026-07-24T06:00:00Z\tsuccess\tschedule')"
    # ⚠️ Les trois refus explicites, ceux qu'on oublie : sans eux, la veille rendrait un « 0 jour »
    # rassurant là où elle ne sait rien. Une mesure vide n'est pas un zéro.
    verifie 1 "un historique vide est un refus, pas un zéro" ""
    verifie 1 "aucune exécution réussie est un refus" "$(printf '2026-08-13T06:00:00Z\tfailure\tschedule\n2026-08-06T06:00:00Z\tfailure\tschedule')"
    # ⚠️ Le cas qui a motivé le filtre : un passage MANUEL réussi, tout frais, ne prouve rien - il a pu
    # être ciblé sur deux classes. Sans cette ligne, la veille certifiait la suite entière sur lui.
    verifie 1 "un passage manuel, même réussi et récent, n'est pas une preuve" \
        "$(printf '2026-08-14T06:00:00Z\tsuccess\tworkflow_dispatch')"
    verifie 1 "une date illisible est un refus" "$(printf 'avant-hier\tsuccess\tschedule')"
    # Contrôle NÉGATIF : un échec récent ne doit pas masquer une réussite récente.
    verifie 0 "une réussite reste vue même si un échec la suit" \
        "$(printf '2026-08-14T06:00:00Z\tfailure\tschedule\n2026-08-13T06:00:00Z\tsuccess\tschedule')"
    exit "${echecs}"
fi

juger "$(cat)" "${JOURS_MAX}" "$(date -u +%s)"
