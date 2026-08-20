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

# ⚠️ Le CACHE, et c'est lui qui règle le fond. Borner et reprendre évite de pendre ; cela ne fait pas
# descendre 91 Mo plus vite. Les runners ralentissent avant de bloquer - la panne est en amont, chez
# l'hébergeur - et le seul levier qui nous reste est de NE PAS RETÉLÉCHARGER.
#
# `APT_CACHE` désigne un dossier que le workflow fait survivre d'un run à l'autre (actions/cache).
# APT y dépose ses `.deb` et les y retrouve : le second run n'a plus que les index à chercher.
# Absent, tout fonctionne comme avant - un poste de développement n'a pas besoin de ce détour.
if [ -n "${APT_CACHE:-}" ]; then
    mkdir -p "$APT_CACHE/partial"
    # ⚠️ APT tourne sous sudo : sans ces droits, il ne sait pas écrire dans un dossier du runner, et
    # il retéléchargerait en silence - un cache qui a l'air d'un cache et n'en est pas. Le dossier
    # nous appartient (on vient de le créer), donc `chmod` nu suffit : réclamer sudo ici ferait
    # échouer le script sur un poste de développement sans terminal.
    chmod -R 777 "$APT_CACHE" 2>/dev/null || true
    BORNES+=(-o "Dir::Cache::archives=$APT_CACHE")
    echo "→ cache APT : $APT_CACHE ($(find "$APT_CACHE" -maxdepth 1 -name '*.deb' 2>/dev/null | wc -l) paquet(s) déjà là)"
fi

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
        # ⚠️ Le volume ne se recalcule PAS ici. Cette porte l'a annoncé un temps - « 87 Mo à
        # télécharger » - en sommant la taille des paquets par `--print-uris`. Le chiffre était juste
        # comme POIDS et faux comme ANNONCE : le cache servant, APT n'en téléchargeait aucun, et la
        # ligne d'à côté disait « Need to get 0 B/91.2 MB ». Deux mesures voisines qui se
        # contredisent, dont une seule fait foi.
        #
        # APT dit lui-même ce qui reste à chercher, et il le dit mieux : « 0 B/91.2 MB » donne le
        # reste ET le total. Dupliquer cette mesure, c'était s'exposer à la voir vieillir - ce qui
        # est arrivé en une journée.
        echo "→ apt-get install : $*"
        # shellcheck disable=SC2086
        if sudo apt-get install -y $RECOMMANDATIONS "${BORNES[@]}" "$@"; then
            # ⚠️ Les droits se remettent à plat APRÈS l'installation : `actions/cache` archive le
            # dossier en tant qu'utilisateur ordinaire, et des `.deb` déposés par root sous des
            # droits stricts ne seraient pas lisibles - le cache se remplirait sans jamais servir.
            [ -n "${APT_CACHE:-}" ] && sudo chmod -R a+rX "$APT_CACHE"
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
