#!/usr/bin/env bash
#
# Un EPIC clos sans trace de clôture ne se distingue pas d'un EPIC clos sans clôture (#4659).
#
# Le dépôt écrit à trois endroits que tout chantier se clôt par douze passes - `CONTRIBUTING.md` §5,
# `dev-docs/cycle-de-chantier.md`, la compétence `clore-un-chantier`. Rien ne le vérifiait, et
# **43 des 64 EPIC clos** n'en portaient aucune trace au 2026-08-28.
#
# ## Ce qu'il cherche, et pourquoi cette chaîne-là
#
# L'en-tête `## Clôture de chantier` du modèle que `cycle-de-chantier.md` demande de coller dans
# l'EPIC. C'est une convention, pas une preuve : un EPIC peut la porter sans que les passes aient eu
# lieu. Le garde ne mesure donc pas la QUALITÉ d'une clôture, il mesure son absence de trace là où la
# documentation la demande - ce qui suffit à rendre la règle vérifiable, et c'est tout ce qu'il
# prétend.
#
# ## Un cliquet, pas un butoir
#
# 43 clôtures manquent déjà. Refuser tout net rendrait le dépôt rouge sans qu'aucune PR soit fautive,
# et le garde se ferait désactiver la première semaine. Le cliquet ne peut que DESCENDRE : fermer un
# EPIC sans trace le fait monter à 44, et c'est ce mouvement-là qui rougit.
#
# Rejouer douze passes sur un chantier clos depuis un an n'aurait pas de sens. Les 43 sont donc
# assumées, une fois, par ce chiffre - et non rattrapées.
#
# ## Pourquoi il vit ici et non dans `scripts/adr/`
#
# Il interroge la forge, comme ses voisins de `.github/scripts/`. Les cliquets de `scripts/adr/` sont
# hors ligne et tournent dans la batterie locale : y mettre celui-ci ferait rougir quiconque travaille
# sans réseau. Ici, il peut REFUSER plutôt que conclure sans que cela coûte à personne.
#
# Usage : verifie-cloture-consignee.sh [--auto-test]
set -euo pipefail

export LC_ALL=C

# La chaîne cherchée : l'en-tête du modèle de `dev-docs/cycle-de-chantier.md`.
readonly MARQUE='## Clôture de chantier'

# L'ADR qui porte le cliquet. Il y vit et non ici : c'est la seule façon qu'un lecteur de la décision
# voie le chiffre qu'elle tient (doctrine de `scripts/adr/_commun.py`).
readonly ADR='dev-docs/decisions/4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md'

racine() { git rev-parse --show-toplevel 2>/dev/null || printf '.'; }

# Le cliquet déclaré par l'ADR, ou un refus si l'en-tête ne le porte pas.
cliquet() {
    local fichier="${CLOTURE_ADR_FICHIER:-$(racine)/${ADR}}"
    local valeur
    valeur=$(sed -n 's/^ratchet:[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' "${fichier}" 2>/dev/null | head -1)
    if [ -z "${valeur}" ]; then
        echo "REFUS : ${fichier} ne déclare aucun cliquet lisible (attendu « ratchet: N »)." >&2
        exit 2
    fi
    printf '%s' "${valeur}"
}

# Les EPIC clos, corps et commentaires. Injectable pour l'auto-test : sans cela ses cas exigeraient
# le réseau, et un garde dont les cas ne tournent pas hors ligne ne se relance jamais.
epics() {
    if [ -n "${CLOTURE_EPICS_FICHIER-}" ]; then
        cat "${CLOTURE_EPICS_FICHIER}"
        return
    fi
    if ! command -v gh > /dev/null 2>&1; then
        echo "REFUS : « gh » est absent. Ce garde ne conclut pas sur ce qu'il n'a pas lu." >&2
        exit 2
    fi
    gh issue list --label epic --state closed --limit 300 --json number,title 2> /dev/null \
        | jq -c '.[]' \
        | while read -r ligne; do
            numero=$(printf '%s' "${ligne}" | jq -r '.number')
            gh issue view "${numero}" --json number,title,body,comments 2> /dev/null \
                || { echo "REFUS : la forge n'a pas répondu pour #${numero}." >&2; exit 2; }
        done \
        | jq -s '.'
}

# Les numéros des EPIC clos SANS trace, un par ligne.
sansTrace() {
    jq -r --arg marque "${MARQUE}" '
        .[]
        | select(([(.body // "")] + [.comments[]?.body // ""] | map(contains($marque)) | any) | not)
        | .number
    '
}

if [ "${1-}" = "--auto-test" ]; then
    echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "${bac}"' EXIT
    printf 'ratchet: 2\n' > "${bac}/adr.md"

    cas=0
    rouges=0
    printf 'title: une ADR sans cliquet\n' > "${bac}/adr-muette.md"
    joue() { # <attendu: ok|rouge|refus> <libellé> <json> [fichier-adr]
        local attendu="$1" libelle="$2" json="$3" adr="${4:-${bac}/adr.md}"
        cas=$((cas + 1))
        [ "${attendu}" != ok ] && rouges=$((rouges + 1))
        printf '%s' "${json}" > "${bac}/epics.json"
        local code=0
        CLOTURE_EPICS_FICHIER="${bac}/epics.json" CLOTURE_ADR_FICHIER="${adr}" \
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

    # Le cas qui compte : le garde doit VOIR une clôture qui manque. Sans lui, tous ses verts ne
    # valent rien.
    joue rouge "un EPIC de plus sans trace fait monter le compte, et il refuse" \
        '[{"number":1,"body":"a","comments":[]},{"number":2,"body":"b","comments":[]},{"number":3,"body":"c","comments":[]}]'
    joue ok "le compte égal au cliquet passe" \
        '[{"number":1,"body":"a","comments":[]},{"number":2,"body":"b","comments":[]}]'
    joue ok "le compte SOUS le cliquet passe : un cliquet descend" \
        '[{"number":1,"body":"a","comments":[]}]'
    joue ok "la trace dans le CORPS compte" \
        '[{"number":1,"body":"## Clôture de chantier\n- [x] 0.","comments":[]},{"number":2,"body":"b","comments":[]}]'
    joue ok "la trace dans un COMMENTAIRE compte, c'est là qu'elle se colle" \
        '[{"number":1,"body":"b","comments":[{"body":"## Clôture de chantier\n- [x] 0."}]},{"number":2,"body":"b","comments":[]}]'
    joue refus "une ADR sans cliquet lisible fait REFUSER, pas conclure" \
        '[{"number":1,"body":"a","comments":[]}]' "${bac}/adr-muette.md"
    joue refus "une ADR introuvable fait REFUSER aussi" \
        '[{"number":1,"body":"a","comments":[]}]' "${bac}/nulle-part.md"

    echo
    echo "${cas} cas, dont ${rouges} qui DOIVENT refuser."
    [ "${echecs}" = 0 ] && echo "Auto-test concluant : le garde voit une clôture qui manque." \
        || echo "Auto-test EN ÉCHEC."
    exit "${echecs}"
fi

seuil=$(cliquet)
liste=$(epics | sansTrace || true)
compte=$(printf '%s' "${liste}" | grep -c . || true)

echo "CLIQUET 4659 | sans trace=${compte} | cliquet=${seuil}"
if [ "${compte}" -gt "${seuil}" ]; then
    echo
    echo "Un EPIC a été clos sans que sa clôture soit consignée."
    echo "Collez le modèle de « dev-docs/cycle-de-chantier.md » en commentaire sur l'EPIC, cases"
    echo "cochées, ou baissez le cliquet dans l'ADR si vous venez d'en rattraper une."
    echo
    printf 'EPIC sans trace : %s\n' "$(printf '%s' "${liste}" | tr '\n' ' ')"
    exit 1
fi
if [ "${compte}" -lt "${seuil}" ]; then
    echo "Le dépôt en porte MOINS que son cliquet : descendez-le à ${compte} dans l'ADR."
fi
exit 0
