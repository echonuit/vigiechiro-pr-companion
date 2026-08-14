#!/usr/bin/env bash
#
# Trie les échecs d'une exécution de la suite, pour l'**étape 1** de #3526.
#
# ## Ce qu'il produit, et pourquoi c'est le livrable
#
# On ne sait pas ce que la suite donne sous Windows et macOS : elle n'y a jamais tourné. Avant de
# programmer quoi que ce soit, il faut un **chiffre** - combien d'échecs, et de quelle nature. Ce
# script le rend, et rien d'autre : il ne corrige rien et ne juge rien.
#
# ⚠️ Deux comptes séparés, TestFX et le reste. Les 140 classes annotées
# `@ExtendWith(ApplicationExtension.class)` sont 21 % de la suite, et leur comportement headless hors
# Linux est la grande inconnue : si elles échouent en masse pour une raison unique, elles noieraient
# le signal des autres. Les compter à part rend les deux lisibles sans rien exclure.
#
# ⚠️ Le tri se fait sur les **rapports** et non en relançant la suite par sous-ensembles : passer 536
# noms de classes à `-Dtest=` dépasse la limite de ligne de commande de Windows, et un dispositif qui
# se casse sur la plateforme qu'il vient mesurer ne mesure rien.
set -euo pipefail
export LC_ALL=C

RAPPORTS="${1:-target/surefire-reports}"
SOURCES="${2:-src/test/java}"

[ -d "${RAPPORTS}" ] || { echo "Aucun rapport dans ${RAPPORTS} : la suite n'a pas produit de compte rendu."; exit 1; }

# Les classes TestFX, reconnues à leur extension JUnit. `|| true` : un grep qui ne trouve rien rend 1,
# et sous `set -e` il tuerait le script AVANT le tri - le cas se produit si l'arborescence bouge.
fx=$(grep -rl "@ExtendWith(ApplicationExtension.class)" "${SOURCES}" --include='*.java' 2>/dev/null || true)
liste_fx=$(printf '%s\n' "${fx}" | sed 's|.*/||; s|\.java$||' | sort -u)

compte() { # <filtre: fx|reste> -> "classes echecs erreurs"
    local mode="$1" classes=0 echecs=0 erreurs=0
    for xml in "${RAPPORTS}"/TEST-*.xml; do
        [ -f "${xml}" ] || continue
        local nom
        nom=$(basename "${xml}" .xml); nom="${nom#TEST-}"; nom="${nom##*.}"
        local estFx="non"
        printf '%s\n' "${liste_fx}" | grep -qx "${nom}" && estFx="oui"
        { [ "${mode}" = "fx" ] && [ "${estFx}" = "non" ]; } && continue
        { [ "${mode}" = "reste" ] && [ "${estFx}" = "oui" ]; } && continue
        classes=$((classes + 1))
        # `|| echo 0` : un rapport tronqué (fork tué) n'a pas d'attribut, et vaut zéro plutôt que de
        # faire échouer le dénombrement - c'est un compte, pas un verdict.
        echecs=$((echecs + $(grep -oE 'failures="[0-9]+"' "${xml}" | head -1 | grep -oE '[0-9]+' || echo 0)))
        erreurs=$((erreurs + $(grep -oE 'errors="[0-9]+"' "${xml}" | head -1 | grep -oE '[0-9]+' || echo 0)))
    done
    echo "${classes} ${echecs} ${erreurs}"
}

read -r cfx efx rfx <<< "$(compte fx)"
read -r cre ere rre <<< "$(compte reste)"

{
    echo "### Ce que la suite donne sur ${RUNNER_OS:-cette plateforme}"
    echo
    echo "| Famille | Classes | Échecs | Erreurs |"
    echo "|---|---|---|---|"
    echo "| TestFX (\`ApplicationExtension\`) | ${cfx} | **${efx}** | **${rfx}** |"
    echo "| Le reste | ${cre} | **${ere}** | **${rre}** |"
    echo
    if [ $((efx + rfx + ere + rre)) -eq 0 ]; then
        echo "**Aucun échec.** La suite passe telle quelle sur cette plateforme."
    else
        echo "#### Les classes en cause"
        echo
        for xml in "${RAPPORTS}"/TEST-*.xml; do
            [ -f "${xml}" ] || continue
            grep -qE 'failures="[1-9]|errors="[1-9]' "${xml}" || continue
            nom=$(basename "${xml}" .xml); nom="${nom#TEST-}"
            court="${nom##*.}"
            famille="reste"
            printf '%s\n' "${liste_fx}" | grep -qx "${court}" && famille="TestFX"
            echo "- \`${nom}\` (${famille})"
        done
    fi
} > "${compte_rendu:=$(mktemp)}"

# Au journal ET au résumé, jamais deux fois au même endroit : `tee -a /dev/stdout` doublait la sortie
# quand la variable de résumé est absente, c'est-à-dire hors CI - là où on relit le plus.
cat "${compte_rendu}"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && cat "${compte_rendu}" >> "${GITHUB_STEP_SUMMARY}"
rm -f "${compte_rendu}"
exit 0
