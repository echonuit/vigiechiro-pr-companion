#!/usr/bin/env bash
#
# Un chantier clos sans répondre à la passe de spécification ne dit pas si le produit a été spécifié
# ou si la question ne s'est pas posée (#4922).
#
# ## Ce que ce garde mesure, et ce qu'il ne mesure pas
#
# La passe 10 du cycle demande d'archiver le changement OpenSpec, ou de dire pourquoi il n'y en avait
# pas. Ce garde vérifie que la trace de clôture porte cette ligne, cochée. C'est une convention, pas
# une preuve : une clôture peut la cocher à tort. Il ne mesure donc pas si la spécification est JUSTE,
# il mesure qu'un jugement a été rendu là où le cycle le demande, ce qui est tout ce qu'il prétend.
#
# ## Pourquoi un cliquet de déficit, et pas une fraction de couverture
#
# La question « combien de capacités sur combien » n'a pas de dénominateur : le dépôt en offre cinq
# incompatibles (16 écrans, 74 commandes, 416 cas de recette, 35 EPIC, 27 services de domaine), et
# choisir entre eux revient à décider ce qu'est une capacité du produit. Un déficit se compte sans
# dénominateur, exactement comme celui d'ADR 4659 dont ce garde est le voisin et la copie.
#
# La COUVERTURE, elle, se regarde sans juger : c'est `scripts/methode/couverture-openspec.py`, une
# loupe qui sort 0 en signalant.
#
# ## Le corpus s'arrête à la naissance de la passe
#
# La passe 10 est entrée dans le cycle par #4840, le 2026-08-30 à 09:00:15Z. Un EPIC clos avant ne
# pouvait pas y répondre, et le compter serait lui reprocher d'ignorer une règle qui n'existait pas.
# Cette borne est un fait historique : elle ne se met pas à jour.
#
# ## Ce qu'il laisse à son voisin
#
# Un EPIC clos SANS AUCUNE trace n'entre pas dans ce compte : c'est le gibier d'ADR 4659, dont le
# cliquet monte alors. Compter la même faute deux fois ferait rougir deux gardes pour un seul
# manquement, et le second passerait pour un défaut du premier.
#
# Usage : verifie-specification-consignee.sh [--auto-test]
set -euo pipefail

export LC_ALL=C

# L'en-tête du modèle de `dev-docs/cycle-de-chantier.md`, comme chez ADR 4659.
readonly MARQUE='## Clôture de chantier'

# L'ancrage est « (^|\n) » et non « ^ » : dans le dialecte de jq, « ^ » ne vaut qu'au début de
# la CHAÎNE, et le drapeau « m » ne le rend pas multiligne, il fait seulement traverser les
# retours à la ligne par « . » - ce qui produirait un faux positif sur une ligne cochée suivie,
# bien plus bas, du mot cherché. L'auto-test a rattrapé les deux.
#
# Le motif exige DEUX mots sur la même ligne, « OpenSpec » et « archiv », dans l'un ou l'autre ordre.
# Chacun seul ne suffit pas, et les deux raisons sont vécues.
#
# Le modèle du cycle écrit « Changement OpenSpec archivé », les clôtures réelles écrivent « Archivage
# OpenSpec » : exiger la formulation du modèle faisait compter #4882 fautive alors qu'elle avait
# répondu. L'ordre des deux mots est donc libre.
#
# Mais « OpenSpec » seul ne suffit pas non plus, et cet en-tête a affirmé le contraire pendant une
# demi-journée : « aucune autre passe ne mentionne OpenSpec dans une trace de clôture ». La trace de
# #4882 le démentait le jour même, à la passe 6 - « sept gardes de méthode verts, dont les deux
# d'OpenSpec ». Le mot nomme aussi deux gardes que la passe des tests lance, et une clôture qui les
# citerait sans répondre à la passe 10 passerait pour spécifiée (#4938).
#
# **Ce qui reste heuristique, et se déclare.** Deux mots sur une ligne ne sont pas une reconnaissance
# exacte : une passe 6 qui parlerait des « gardes d'archivage OpenSpec » tromperait encore ce motif.
# Ce garde vérifie qu'une trace PORTE une ligne cochée qui parle d'archiver un changement, il ne
# prouve pas que cette ligne est la passe 10. C'est l'article A3, et c'est la raison du niveau
# `probable`.
#
# La ligne de la passe 10, reconnue par son CONTENU et non par son numéro. #4840 a renuméroté le
# cycle : dans une trace antérieure, « 10. » désigne la passe des ADR. Un garde qui matcherait le
# numéro compterait douze clôtures comme spécifiées alors qu'aucune ne l'était.
readonly LIGNE='(^|\n)- \[x\] [0-9]+[a-z]?\.[^\n]*(OpenSpec[^\n]*[Aa]rchiv|[Aa]rchiv[^\n]*OpenSpec)'

# Le commit qui a créé la passe 10 (#4840). Fait historique, jamais mis à jour.
readonly DEPUIS='2026-08-30T09:00:15Z'

readonly ADR='dev-docs/decisions/4922-l-adoption-d-une-specification-se-tient-par-un-cliquet.md'

racine() { git rev-parse --show-toplevel 2>/dev/null || printf '.'; }

cliquet() {
    local fichier="${SPEC_ADR_FICHIER:-$(racine)/${ADR}}"
    local valeur
    valeur=$(sed -n 's/^ratchet:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' "${fichier}" 2>/dev/null | head -1)
    if [ -z "${valeur}" ]; then
        echo "REFUS : ${fichier} ne déclare aucun cliquet lisible (attendu « ratchet: N »)." >&2
        exit 2
    fi
    printf '%s' "${valeur}"
}

# Les EPIC clos depuis la naissance de la passe, corps et commentaires réunis. Injectable pour
# l'auto-test : sans cela ses cas exigeraient le réseau, et un garde dont les cas ne tournent pas
# hors ligne ne se relance jamais.
epics() {
    if [ -n "${SPEC_EPICS_FICHIER-}" ]; then
        cat "${SPEC_EPICS_FICHIER}"
        return
    fi
    if ! command -v gh > /dev/null 2>&1; then
        echo "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu." >&2
        exit 2
    fi
    gh issue list --label epic --state closed --limit 300 --json number,closedAt 2> /dev/null \
        | jq -r --arg d "${DEPUIS}" '.[] | select(.closedAt > $d) | .number' \
        | while read -r numero; do
            gh issue view "${numero}" --json number,body,comments 2> /dev/null \
                || { echo "REFUS : la forge n'a pas répondu pour #${numero}." >&2; exit 2; }
        done \
        | jq -s '.'
}

# Les numéros des EPIC qui PORTENT une trace mais n'y ont pas répondu, un par ligne.
sansReponse() {
    jq -r --arg m "${MARQUE}" --arg l "${LIGNE}" '
        .[]
        | . as $e
        | ([($e.body // "")] + [$e.comments[]?.body // ""] | join("\n")) as $texte
        | select($texte | contains($m))
        | select(($texte | test($l)) | not)
        | $e.number
    '
}

if [ "${1-}" = "--auto-test" ]; then
    echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "${bac}"' EXIT
    printf 'ratchet: 1\n' > "${bac}/adr.md"
    printf 'title: une ADR sans cliquet\n' > "${bac}/adr-muette.md"

    # Raccourcis de lisibilité : une trace de clôture, avec ou sans la réponse à la passe.
    TRACE='## Clôture de chantier\n- [x] 0. Relecture des ADR'
    REPONDU="${TRACE}"'\n- [x] 10. Changement OpenSpec archivé : **sans objet**, aucune capacité.'
    ANCIENNE="${TRACE}"'\n- [x] 10. **ADR** · décisions énumérées, pas fichiers comptés.'

    cas=0
    rouges=0
    joue() { # <attendu: ok|rouge|refus> <libellé> <json> [fichier-adr]
        local attendu="$1" libelle="$2" json="$3" adr="${4:-${bac}/adr.md}"
        cas=$((cas + 1))
        [ "${attendu}" != ok ] && rouges=$((rouges + 1))
        printf '%s' "${json}" > "${bac}/epics.json"
        local code=0
        SPEC_EPICS_FICHIER="${bac}/epics.json" SPEC_ADR_FICHIER="${adr}" \
            "$0" > /dev/null 2>&1 || code=$?
        local obtenu=ok
        [ "${code}" = 1 ] && obtenu=rouge
        [ "${code}" = 2 ] && obtenu=refus
        if [ "${obtenu}" = "${attendu}" ]; then
            echo "  ✔ ${libelle}"
        else
            echo "  ✘ ${libelle} : attendu ${attendu}, obtenu ${obtenu}"
            echecs=1
        fi
    }

    # Le cas qui compte : sans lui, tous les verts de ce garde seraient creux.
    joue rouge "un chantier de plus clos sans répondre à la passe fait monter le compte" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${TRACE}\",\"comments\":[]}]"
    joue ok "le compte égal au cliquet passe" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]}]"
    joue ok "le compte SOUS le cliquet passe : un cliquet descend" \
        '[]'
    joue ok "une passe 10 répondue ne compte pas" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${REPONDU}\",\"comments\":[]}]"
    joue ok "la réponse dans un COMMENTAIRE compte, c'est là que la trace se colle" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"x\",\"comments\":[{\"body\":\"${REPONDU}\"}]}]"
    joue ok "un EPIC SANS trace n'entre pas dans ce compte : il est le gibier d'ADR 4659" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"rien\",\"comments\":[]}]"

    # Le cas qui a piégé l'écriture de ce garde, et la raison pour laquelle il lit le CONTENU.
    joue rouge "une ANCIENNE passe 10 (les ADR) ne vaut pas réponse : #4840 a renuméroté" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${ANCIENNE}\",\"comments\":[]}]"
    joue rouge "une passe 6 qui NOMME OpenSpec sans archiver ne vaut pas réponse (#4938)" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${TRACE}\\n- [x] 6. Tests : sept gardes de méthode verts, dont les deux d'OpenSpec.\",\"comments\":[]}]"
    joue ok "la formulation « Archivage OpenSpec » vaut réponse : c'est celle de #4882" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${TRACE}\\n- [x] 10. Archivage OpenSpec : **sans objet**.\",\"comments\":[]}]"
    joue ok "un AUTRE numéro portant le même contenu vaut réponse : le numéro ne fait pas foi" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]},{\"number\":2,\"body\":\"${TRACE}\\n- [x] 12. Changement OpenSpec archivé : fusionné.\",\"comments\":[]}]"

    # La premisse, eprouvee plutot qu ecrite : si la marque est renommee, le corpus tombe a zero et
    # ce garde passerait VERT en n ayant rien lu (#4948).
    joue refus "des EPIC clos dont AUCUN ne porte la marque : corpus vide, on REFUSE" \
        '[{"number":1,"body":"## Bilan de chantier\n- [x] 10. Archivage OpenSpec : sans objet","comments":[]}]'
    joue ok "AUCUN EPIC clos : rien a juger, ce n est pas un corpus vide suspect" \
        '[]'
    joue refus "une ADR sans cliquet lisible fait REFUSER, pas conclure" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]}]" "${bac}/adr-muette.md"
    joue refus "une ADR introuvable fait REFUSER aussi" \
        "[{\"number\":1,\"body\":\"${TRACE}\",\"comments\":[]}]" "${bac}/nulle-part.md"

    echo
    echo "${cas} cas, dont ${rouges} qui DOIVENT refuser."
    [ "${echecs}" = 0 ] && echo "Auto-test concluant : le garde voit une passe de spécification sautée." \
        || echo "Auto-test EN ÉCHEC."
    exit "${echecs}"
fi

seuil=$(cliquet)
corpus=$(epics)

# La PREMISSE, verifiee a chaque passage plutot qu ecrite dans un en-tete (#4948).
#
# Ce garde ne juge que les traces qui PORTENT la marque. Si la marque est renommee, son corpus tombe
# a zero, il compte zero manquement et passe VERT en n ayant rien lu. Mesure le 2026-08-31 sur un bac
# ou toutes les traces portaient « ## Bilan de chantier » : « sans reponse=0 », code 0.
#
# Un EPIC clos sans AUCUNE trace est legitime et appartient a ADR 4659. Ce qui ne l est pas, c est
# qu AUCUN des EPIC clos ne porte la marque alors qu il y en a : cela designe la marque, pas les
# cloturers. C est l article A3, « un dispositif dit ce qu il n a pas pu lire ».
clos=$(printf '%s' "${corpus}" | jq 'length' 2> /dev/null || echo 0)
portent=$(printf '%s' "${corpus}" | jq -r --arg m "${MARQUE}" '
    [.[] | select(([(.body // "")] + [.comments[]?.body // ""] | join("\n")) | contains($m))] | length
' 2> /dev/null || echo 0)
if [ "${clos}" -gt 0 ] && [ "${portent}" = "0" ]; then
    echo "REFUS : ${clos} EPIC clos, et AUCUN ne porte « ${MARQUE} »." >&2
    echo "Ce garde ne lit que les traces qui portent cette marque : son corpus est vide, et un" >&2
    echo "compte de zero ne voudrait rien dire. La marque a probablement ete renommee dans" >&2
    echo "« dev-docs/cycle-de-chantier.md » ; alignez-la ici." >&2
    exit 2
fi

liste=$(printf '%s' "${corpus}" | sansReponse || true)
compte=$(printf '%s' "${liste}" | grep -c . || true)

echo "CLIQUET 4922 | sans réponse à la passe de spécification=${compte} | cliquet=${seuil}"
if [ "${compte}" -gt "${seuil}" ]; then
    echo
    echo "Un chantier a été clos sans répondre à la passe 10 du cycle."
    echo "Cochez-la dans la trace de l'EPIC : soit le changement OpenSpec est archivé, soit écrivez"
    echo "« sans objet » AVEC sa raison. Une capacité métier touchée sans spécification laisse la"
    echo "spécification vivante décrire un produit que le code a déjà dépassé."
    echo
    printf 'EPIC sans réponse : %s\n' "$(printf '%s' "${liste}" | tr '\n' ' ')"
    exit 1
fi
if [ "${compte}" -lt "${seuil}" ]; then
    echo "Le dépôt en porte MOINS que son cliquet : descendez-le à ${compte} dans l'ADR."
fi
exit 0
