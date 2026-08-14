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
#     dateISO<TAB>conclusion<TAB>titre du run
#
# ⚠️ Seules les exécutions **complètes** comptent. Une exécution peut être **ciblée** depuis #3754 -
# deux classes au lieu de 4600 -, et l'API des runs ne dit pas quelles entrées ont été passées à un
# `workflow_dispatch`. La compter certifierait la fraîcheur de la suite entière sur la preuve de deux
# classes ; c'est le malentendu que l'en-tête du tri évite un étage plus bas. Vu en branchant la veille
# sur les vraies données du dépôt, où elle a d'abord validé un passage ciblé de trois classes.
#
# Le workflow porte donc son périmètre dans le **titre du run** (`run-name:`), lu ici en 3e colonne.
# Filtrer sur le déclencheur aurait été plus simple et laissait le train **sans issue de secours** : si
# le mardi rougit sur une instabilité, aucune preuve ne peut plus naître avant le mardi suivant. Un
# passage manuel *complet* vaut un passage programmé ; c'est le passage *ciblé* qui ne vaut rien.
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

# Les marqueurs posés par `run-name:` dans suite-sous-windows-et-macos.yml. Les renommer d'un seul côté
# casse la détection - et la veille le dit plutôt que de conclure au hasard.
MARQUEUR_COMPLET="[complet]"
MARQUEUR_CIBLE="[ciblé]"

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

    # ⚠️ Aucun titre marqué du tout = le `run-name:` a été renommé, ou tous les runs le précèdent. On
    # refuse en disant que c'est la VEILLE qui est en cause, plutôt que d'annoncer « jamais éprouvé » -
    # même raisonnement que `ETAPE_CONTRAT` dans `veille-contrat-api.sh`.
    # `-F` : les crochets d'un marqueur sont une CLASSE DE CARACTÈRES en expression rationnelle, donc
    # « [complet] » y accepterait n'importe quel titre contenant un « c », un « o »... (vu à l'écriture).
    if ! printf '%s\n' "${historique}" | grep -qF -e "${MARQUEUR_COMPLET}" -e "${MARQUEUR_CIBLE}"; then
        echo "❌ Aucun run ne porte de marqueur de périmètre (« ${MARQUEUR_COMPLET} » / « ${MARQUEUR_CIBLE} »)."
        echo "   C'est la veille qui est en cause, pas la suite. Deux causes, dans cet ordre :"
        echo "   1. les exécutions examinées sont TOUTES antérieures à la pose du marqueur (#3526) -"
        echo "      normal le temps qu'un premier passage marqué ait lieu, et ça se résout tout seul ;"
        echo "   2. le « run-name: » du workflow a été renommé sans reporter le nom ici."
        echo "   Dans les deux cas on refuse : sans marqueur, un passage ciblé serait compté comme une"
        echo "   preuve complète."
        return 1
    fi

    local derniere
    derniere=$(printf '%s\n' "${historique}" \
        | awk -F'\t' -v m="${MARQUEUR_COMPLET}" \
              '$2 == "success" && index($3, m) == 1 {print $1}' | sort -r | head -1)

    if [ -z "${derniere}" ]; then
        local examinees
        examinees=$(printf '%s\n' "${historique}" | grep -c . || true)
        echo "❌ Aucune exécution COMPLÈTE et réussie parmi les ${examinees} examinées."
        echo "   ⚠️ Les passages ciblés (#3754) ne comptent pas : quelques classes sur 4600."
        echo "   Relancer « Suite complète sous Windows et macOS » en laissant « classes » VIDE."
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
    # ⚠️ Le 4e argument, facultatif, est un motif que la SORTIE doit contenir. Sans lui, un cas ne juge
    # que le code de retour - et deux refus pour des raisons opposées se ressemblent alors trait pour
    # trait. Vu en éprouvant cet autotest : en retirant la garde du marqueur absent, AUCUN cas n'a
    # rougi, parce que le refus tombait quand même, en accusant la suite au lieu de la veille.
    verifie() { # <attendu> <libellé> <historique> [motif attendu dans la sortie]
        local code=0 sortie
        sortie=$(juger "$3" 10 "${MAINTENANT}" 2>&1) || code=$?
        if [ "${code}" != "$1" ]; then
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            echecs=1
        elif [ -n "${4:-}" ] && ! printf '%s' "${sortie}" | grep -qF -- "$4"; then
            echo "  ✘ $2 : code ${code} attendu, mais le motif « $4 » manque au verdict"
            echecs=1
        else
            echo "  ✔ $2"
        fi
    }
    C="[complet] toute la suite"
    K="[ciblé] CartesAccueilTest"

    verifie 0 "une preuve d'hier est fraîche" "$(printf '2026-08-13T06:00:00Z\tsuccess\t%s' "$C")"
    verifie 1 "une preuve de trois semaines ne l'est plus" "$(printf '2026-07-24T06:00:00Z\tsuccess\t%s' "$C")"
    # ⚠️ Les refus explicites, ceux qu'on oublie : sans eux, la veille rendrait un « 0 jour » rassurant
    # là où elle ne sait rien. Une mesure vide n'est pas un zéro.
    verifie 1 "un historique vide est un refus, pas un zéro" "" "Historique vide"
    verifie 1 "aucune exécution réussie est un refus" \
        "$(printf '2026-08-13T06:00:00Z\tfailure\t%s\n2026-08-06T06:00:00Z\tfailure\t%s' "$C" "$C")"
    # ⚠️ Le cas qui a motivé le marqueur : un passage CIBLÉ, réussi et tout frais, ne prouve rien - il ne
    # portait que quelques classes. Sans cette ligne, la veille certifiait la suite entière sur lui.
    verifie 1 "un passage ciblé, même réussi et récent, n'est pas une preuve" \
        "$(printf '2026-08-14T06:00:00Z\tsuccess\t%s' "$K")" \
        "Aucune exécution COMPLÈTE"
    # L'ISSUE DE SECOURS, et c'est tout l'intérêt de lire le titre plutôt que le déclencheur : un passage
    # complet lancé À LA MAIN vaut preuve. Sans lui, un mardi rouge bloquerait le train une semaine.
    verifie 0 "un passage complet lancé à la main vaut preuve" \
        "$(printf '2026-08-13T18:00:00Z\tsuccess\t%s' "$C")"
    # ⚠️ Le marqueur a disparu (run-name renommé) : refuser en accusant la VEILLE, et surtout ne pas
    # prendre le premier succès venu - sans quoi le renommage ferait passer un ciblé pour un complet.
    verifie 1 "un historique sans aucun marqueur accuse la veille" \
        "$(printf '2026-08-14T06:00:00Z\tsuccess\tSuite sous Windows et macOS')" \
        "C'est la veille qui est en cause"
    verifie 1 "une date illisible est un refus" "$(printf 'avant-hier\tsuccess\t%s' "$C")"
    # Contrôle NÉGATIF : un échec récent ne doit pas masquer une réussite récente.
    verifie 0 "une réussite reste vue même si un échec la suit" \
        "$(printf '2026-08-14T06:00:00Z\tfailure\t%s\n2026-08-13T06:00:00Z\tsuccess\t%s' "$C" "$C")"
    # Contrôle NÉGATIF : un ciblé récent ne doit pas masquer un complet plus ancien mais encore frais.
    verifie 0 "un ciblé récent ne masque pas un complet encore frais" \
        "$(printf '2026-08-14T06:00:00Z\tsuccess\t%s\n2026-08-12T06:00:00Z\tsuccess\t%s' "$K" "$C")"
    exit "${echecs}"
fi

juger "$(cat)" "${JOURS_MAX}" "$(date -u +%s)"
