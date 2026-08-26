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
# Deux comptes séparés, TestFX et le reste. Les 140 classes annotées
# `@ExtendWith(ApplicationExtension.class)` sont 21 % de la suite, et leur comportement headless hors
# Linux est la grande inconnue : si elles échouent en masse pour une raison unique, elles noieraient
# le signal des autres. Les compter à part rend les deux lisibles sans rien exclure.
#
# Le tri se fait sur les **rapports** et non en relançant la suite par sous-ensembles : passer 536
# noms de classes à `-Dtest=` dépasse la limite de ligne de commande de Windows, et un dispositif qui
# se casse sur la plateforme qu'il vient mesurer ne mesure rien.
set -euo pipefail
export LC_ALL=C

# L'auto-test, avant tout le reste : ce script décide de la couleur du passage hebdomadaire, dont le
# train de publication fait sa condition, et il était l'un des trois de `.github/scripts` à n'avoir
# aucun témoin. Le défaut de #4544 a vécu là : rien ne pouvait le voir.
#
#     ./.github/scripts/trie-les-echecs-de-plateforme.sh --auto-test
auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    # shellcheck disable=SC2064
    trap "rm -rf '${bac}'" RETURN

    mkdir -p "${bac}/sources"
    # Une classe TestFX et une autre, pour que le tri des deux familles soit exercé.
    printf '@ExtendWith(ApplicationExtension.class)\n' > "${bac}/sources/UneVueTest.java"
    printf 'class UnModeleTest {}\n' > "${bac}/sources/UnModeleTest.java"

    essai() { # <nom> <motif attendu> <code attendu> <marqueur: oui|non> <classes: valeur de CLASSES>
        local nom="$1" motif="$2" code_attendu="$3" marqueur="$4" classes="$5" obtenu code
        rm -rf "${bac:?}/rapports" && mkdir -p "${bac}/rapports"
        printf '<testsuite name="fr.essai.UneVueTest" tests="2" failures="0" errors="0" skipped="0"/>\n' \
            > "${bac}/rapports/TEST-fr.essai.UneVueTest.xml"
        printf '<testsuite name="fr.essai.UnModeleTest" tests="3" failures="0" errors="0" skipped="0"/>\n' \
            > "${bac}/rapports/TEST-fr.essai.UnModeleTest.xml"
        rm -f "${bac}/temoin"
        [ "${marqueur}" = "oui" ] && : > "${bac}/temoin"
        obtenu=$(CLASSES="${classes}" GITHUB_STEP_SUMMARY="" \
            bash "$0" "${bac}/rapports" "${bac}/sources" "${bac}/temoin" 2>&1) && code=0 || code=$?
        total=$((total + 1))
        if printf '%s' "${obtenu}" | grep -qF "${motif}" && [ "${code}" = "${code_attendu}" ]; then
            printf '  [OK   ] %-58s -> %s\n' "${nom}" "code ${code}"
        else
            printf '  [ÉCHEC] %-58s -> code %s : %s\n' "${nom}" "${code}" "$(printf '%s' "${obtenu}" | tail -2 | head -1)"
            echecs=$((echecs + 1))
        fi
    }

    echo "AUTO-TEST"
    # Le défaut de #4544, dans les deux sens. Sans marqueur, les mêmes rapports sans un seul échec
    # doivent rougir : c'est tout l'objet du garde, puisqu'un passage tronqué rend zéro échec.
    essai "sans témoin, un passage sans échec est REFUSÉ" "INTERROMPU" 1 non ""
    essai "sans témoin, le journal nomme le nombre de classes lues" "2 classe(s) ont rendu un rapport" 1 non ""
    # Le contrôle de l'autre bord, sans lequel le garde pourrait refuser TOUT et paraître bon.
    essai "avec témoin, le même passage est accepté" "Aucun échec." 0 oui ""
    essai "avec témoin, il se dit complet" "Passage **complet**" 0 oui ""
    # Un passage ciblé n'a jamais prétendu être complet : le marqueur ne le concerne pas, et exiger
    # le témoin l'aurait fait rougir sans raison.
    essai "un passage ciblé passe sans témoin" "Passage ciblé" 0 non "UnModeleTest"
    essai "un passage ciblé ne se déclare pas complet" "ne dit **rien** du reste" 0 non "UnModeleTest"

    echo
    echo "${total} cas, dont 4 contrôles négatifs."
    if [ "${echecs}" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au verdict de ce script."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

RAPPORTS="${1:-target/surefire-reports}"
SOURCES="${2:-src/test/java}"
# Le témoin que la suite est allée AU BOUT. Le workflow ne l'écrit qu'après le retour de Maven : un
# job coupé par `timeout-minutes` meurt avant, et son absence est alors le seul fait qui distingue un
# passage entier d'un passage tronqué (#4544).
MARQUEUR="${3:-target/suite-est-allee-au-bout}"

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
lues=$((cfx + cre))

{
    echo "### Ce que la suite donne sur ${RUNNER_OS:-cette plateforme}"
    echo
    # Dire SUR QUOI le compte a porté. Sans cette ligne, « aucun échec » sur trois classes se relirait
    # comme « aucun échec » tout court - et c'est le genre de malentendu qui fait programmer un train de
    # publication sur une preuve qui n'existe pas (#3754).
    if [ -n "${CLASSES:-}" ]; then
        echo "⚠️ **Passage ciblé** : \`${CLASSES}\`. Ce compte ne dit **rien** du reste de la suite."
    elif [ -f "${MARQUEUR}" ]; then
        echo "Passage **complet** : toutes les classes de test."
    else
        # Le cas qui a motivé ce garde. Un job coupé à 92 minutes sur un plafond de 90 a rendu ce
        # même tableau, avec 618 classes au lieu de 758, sous le titre « toutes les classes de
        # test » et sans un échec. Le compte était exact ; la phrase au-dessus ne l'était pas, et
        # c'est elle qu'on lit (#4544).
        echo "⚠️ **Passage INTERROMPU** : la suite ne s'est pas terminée. Les ${lues} classes comptées"
        echo "ci-dessous sont celles qui ont eu le temps de rendre un rapport. Ce compte ne dit **rien**"
        echo "des autres, et un passage tronqué n'est pas une preuve."
    fi
    echo
    echo "| Famille | Classes | Échecs | Erreurs |"
    echo "|---|---|---|---|"
    echo "| TestFX (\`ApplicationExtension\`) | ${cfx} | **${efx}** | **${rfx}** |"
    echo "| Le reste | ${cre} | **${ere}** | **${rre}** |"
    echo
    if [ $((efx + rfx + ere + rre)) -eq 0 ]; then
        # La conclusion suit le PÉRIMÈTRE. Dire « la suite passe » après un passage ciblé
        # contredisait l'en-tête posé trois lignes plus haut - et c'était la moitié rassurante de la
        # contradiction, donc celle qu'on retient. Vu sur la première utilisation réelle de l'outil.
        if [ -n "${CLASSES:-}" ]; then
            echo "**Aucun échec** sur les classes demandées. Le reste de la suite n'a pas été exécuté."
        else
            echo "**Aucun échec.** La suite passe telle quelle sur cette plateforme."
        fi
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

# Il CONCLUT désormais, il ne se contente plus de compter (#3526, étape 3).
#
# Tant qu'il rendait toujours 0, la couleur du job ne disait rien de ce qu'il avait trouvé - c'est le
# motif que ce chantier corrige partout ailleurs. Et surtout, la veille de fraîcheur qui garde le train
# de publication cherche « la dernière exécution RÉUSSIE » : sur un job qui réussit toujours, elle
# aurait certifié la fraîcheur d'une preuve inexistante.
#
# `-Dmaven.test.failure.ignore=true` garde tout son sens : Maven va au bout et produit le compte
# COMPLET, et c'est ce compte-là - pas le premier échec - qui décide ici.
# L'interruption se juge AVANT les échecs, et c'est l'ordre qui compte : une suite coupée rend zéro
# échec sur les classes qu'elle n'a pas atteintes, donc le test d'en dessous la laisserait passer en
# vert. C'est exactement ce qui s'est produit (#4544).
if [ -z "${CLASSES:-}" ] && [ ! -f "${MARQUEUR}" ]; then
    echo "::error title=La suite a été interrompue sur ${RUNNER_OS:-cette plateforme}::${lues} classe(s) ont rendu un rapport, et la suite ne s'est pas terminée. Ce passage n'est pas une preuve."
    exit 1
fi

total=$((efx + rfx + ere + rre))
if [ "${total}" -gt 0 ]; then
    echo "::error title=La suite ne passe pas sur ${RUNNER_OS:-cette plateforme}::${total} échec(s) et erreur(s) - voir le tableau du résumé."
    exit 1
fi
exit 0
