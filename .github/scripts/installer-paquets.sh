#!/usr/bin/env bash
# Installe des paquets APT sur un runner, sans pendre quand un miroir tombe.
#
# ⚠️ Pourquoi cette porte unique existe. Trois étapes de trois workflows différents ont pendu le
# même jour, sur la même ligne : `apt-get update`. Le miroir Azure du runner rendait `Ign:` sur
# toutes ses sources, APT basculait sur l'archive amont, et l'attente durait jusqu'au butoir du job -
# 12 minutes pour `banc-filme`, 30 pour `capturer`, 40 pour `paquet`, y compris SUR `main`.
#
# Chacune portait un nom qui parlait d'autre chose : « Aligner la police système », « Installer de
# quoi afficher et filmer », « E2E CLI (bats) sur le fat-jar ». Lu de loin, le rouge accusait le
# code. Le log disait « Get:5 … InRelease » puis trente-huit minutes de silence.
#
# ## Ce que cette porte fait, et ce qu'elle ne fait pas
#
# Elle **borne** : des délais courts font échouer vite plutôt que traîner, et des reprises rattrapent
# un téléchargement coupé. Elle ne rend pas un miroir mort vivant - un runner sans réseau échouera,
# mais en une minute et en le DISANT, au lieu d'immobiliser une PR trois quarts d'heure.
#
# ⚠️ Jamais de `-qq` : c'est lui qui a rendu la première panne indéchiffrable. Une étape muette qui
# pend n'apprend rien à personne.
#
#   installer-paquets.sh [--avec-recommandations] <paquet>...
set -euo pipefail

RECOMMANDATIONS="--no-install-recommends"
if [ "${1:-}" = "--avec-recommandations" ]; then
    RECOMMANDATIONS=""
    shift
fi

if [ "$#" -eq 0 ]; then
    echo "installer-paquets.sh : aucun paquet demandé." >&2
    exit 1
fi

# Bornes : au-delà, on préfère un échec net à une attente muette.
BORNES=(-o Acquire::Retries=3 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20)

# ⚠️ On n'installe pas ce qui est déjà là. Le runner GitHub porte déjà `xvfb` : le demander le
# faisait résoudre, comparer, et parfois retélécharger des dépendances pour rien. Mesuré dans un log
# de `banc-filme` : « xvfb is already the newest version ».
manquants=()
for paquet in "$@"; do
    if dpkg -s "$paquet" >/dev/null 2>&1; then
        echo "→ $paquet : déjà installé sur ce runner"
    else
        manquants+=("$paquet")
    fi
done
if [ ${#manquants[@]} -eq 0 ]; then
    echo "→ rien à installer : tout est déjà là."
    exit 0
fi
set -- "${manquants[@]}"

essai=1
while [ "$essai" -le 2 ]; do
    echo "→ apt-get update (essai $essai/2)"
    if sudo apt-get update "${BORNES[@]}"; then
        # ⚠️ Le VOLUME se dit. Sans ce chiffre, une étape qui traîne se lit comme une panne : elle
        # peut n'être qu'un miroir lent devant 91 Mo. C'est la mesure qui a fait relever le butoir
        # de `banc-filme`, et c'est elle qu'il faudra relire pour le régler de nouveau.
        volume=$(apt-get install -y --no-install-recommends --print-uris "$@" 2>/dev/null \
            | grep -oE "^'[^']+' [^ ]+ [0-9]+" | awk '{ total += $NF } END { printf "%.0f", total / 1048576 }')
        echo "→ apt-get install : $* (${volume:-?} Mo à télécharger)"
        # shellcheck disable=SC2086
        if sudo apt-get install -y $RECOMMANDATIONS "${BORNES[@]}" "$@"; then
            exit 0
        fi
    fi
    echo "   … échec de l'essai $essai" >&2
    essai=$((essai + 1))
done

echo "✗ Installation impossible après deux essais : $*" >&2
echo "  Signature habituelle : le miroir du runner ne répond plus (« Ign: » sur toutes les sources)." >&2
echo "  Ce n'est pas le code de la PR qui est en cause." >&2
exit 1
