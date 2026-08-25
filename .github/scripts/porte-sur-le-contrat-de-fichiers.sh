#!/usr/bin/env bash
#
# Dit si le diff courant touche le **contrat de système de fichiers** (#3525).
#
# ## Pourquoi ce script existe plutôt qu'un filtre `paths:`
#
# Un `paths:` aurait empêché le job de démarrer sur la plupart des PR. Or ce dépôt n'a **aucune
# protection de branche** : un job absent du récapitulatif est indiscernable d'un job vert, y compris
# pour une boucle d'attente qui lit « aucune rouge, rien en cours ». C'est le motif de l'ADR 2748 -
# un dispositif qui peut ne rien vérifier doit le dire - et c'est ce que le lot 2 a corrigé deux fois.
#
# Le job tourne donc toujours, et **cette étape** décide. Quand elle rend `non`, le job finit vert en
# ayant écrit pourquoi : c'est un silence explicite, pas une absence.
#
# ## Ce qu'il regarde
#
# Les classes dont le comportement dépend du système sous-jacent, et leurs tests : renommage atomique,
# verrou de fichier, extraction ZIP, copie et déplacement entre volumes, emplacement de la
# configuration d'amorçage. Plus le workflow et ce script eux-mêmes - sans quoi une modification du
# dispositif ne serait jamais éprouvée par le dispositif.
set -euo pipefail

# Chemins surveillés, un par ligne. ⚠️ Le nom de CHAQUE entrée est une décision : ajouter une classe
# ici, c'est déclarer que son comportement dépend du système. Le faire à la légère rallonge le gate de
# trois plateformes ; l'oublier laisse un chemin de disque non vérifié.
#
# ⚠️ `GestesFichiers` et `TailleFichier` y figurent depuis #3794, et ils méritent un mot : ce ne sont
# pas des classes qui *font* du disque, ce sont les **points d'injection** par lesquels tout le reste y
# accède. Leurs implémentations par défaut - `Files.walk`, `Files.list`, `deleteIfExists`, la taille -
# SONT le comportement réel du produit. Changer l'une d'elles change ce que font les huit classes
# ci-dessus sur les trois plateformes, et jusqu'ici cela ne déclenchait **rien**.
#
# La garde `verifie-inventaires-ci.sh` ne pouvait pas le trouver : elle confronte des inventaires, et
# qu'une classe MÉRITE d'être surveillée reste un jugement. C'est écrit dans son en-tête, et c'est
# pourquoi cette entrée-ci est posée à la main.
SURVEILLES=$(
    cat <<'FIN'
src/main/java/fr/univ_amu/iut/commun/model/EcritureAtomique.java
src/main/java/fr/univ_amu/iut/commun/model/ConfigurationAmorcage.java
src/main/java/fr/univ_amu/iut/commun/persistence/VerrouWorkspace.java
src/main/java/fr/univ_amu/iut/commun/persistence/ArborescenceFichiers.java
src/main/java/fr/univ_amu/iut/commun/persistence/BasculeRacines.java
src/main/java/fr/univ_amu/iut/commun/persistence/RestaurationComplete.java
src/main/java/fr/univ_amu/iut/importation/model/ExtracteurZip.java
src/main/java/fr/univ_amu/iut/importation/model/BornesExtraction.java
src/main/java/fr/univ_amu/iut/commun/persistence/GestesFichiers.java
src/main/java/fr/univ_amu/iut/commun/model/TailleFichier.java
src/test/java/fr/univ_amu/iut/commun/model/EcritureAtomiqueTest.java
src/test/java/fr/univ_amu/iut/commun/model/ConfigurationAmorcageTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/VerrouWorkspaceTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/ArborescenceFichiersTest.java
src/test/java/fr/univ_amu/iut/commun/persistence/RestaurationCompleteTest.java
src/test/java/fr/univ_amu/iut/importation/ExtracteurZipTest.java
src/test/java/fr/univ_amu/iut/importation/ExtracteurZipQuotasTest.java
src/test/java/fr/univ_amu/iut/importation/BornesExtractionTest.java
.github/workflows/maven.yml
.github/scripts/porte-sur-le-contrat-de-fichiers.sh
FIN
)

# La base de comparaison : le point de divergence pour une PR, le commit précédent sinon. `|| true`
# parce qu'un `merge-base` qui échoue (historique tronqué) ne doit pas tuer le job avant qu'il juge -
# dans ce cas on préfère TOUT vérifier plutôt que de conclure « rien à faire » depuis une erreur.
base=""
# ⟨profondeur⟩ On demande a GitHub le SHA de base de la PR plutot que de le CALCULER par
# `merge-base`. Le calcul exigeait l historique des DEUX cotes, donc un `fetch-depth: 0` au
# checkout - et ce clone integral partait en vrille jusqu a epuiser les vingt minutes du job,
# 1 199 s et 1 200 s le meme jour sur ubuntu ET macos, quand l etape prend 3 s a profondeur 1
# (#4440). Le SHA, lui, se recupere seul et se rapatrie a profondeur 1.
if [ -n "${GITHUB_BASE_SHA:-}" ]; then
    git fetch --no-tags --depth=1 origin "${GITHUB_BASE_SHA}" >/dev/null 2>&1 || true
    git cat-file -e "${GITHUB_BASE_SHA}^{commit}" 2>/dev/null && base="${GITHUB_BASE_SHA}"
fi
# Repli sur le calcul, hors PR ou si le SHA n arrive pas. La profondeur peut alors ne pas suffire,
# et l absence de base fait VERIFIER TOUT plus bas plutot que conclure au silence.
if [ -z "${base}" ] && [ -n "${GITHUB_BASE_REF:-}" ]; then
    git fetch --no-tags --depth=50 origin "${GITHUB_BASE_REF}" >/dev/null 2>&1 || true
    base=$(git merge-base HEAD "origin/${GITHUB_BASE_REF}" 2>/dev/null || true)
fi
[ -n "${base}" ] || base=$(git rev-parse HEAD~1 2>/dev/null || true)

if [ -z "${base}" ]; then
    echo "Base de comparaison introuvable : on vérifie tout plutôt que de conclure au silence."
    echo "concerne=oui" >> "${GITHUB_OUTPUT:-/dev/stdout}"
    exit 0
fi

modifies=$(git diff --name-only "${base}" HEAD || true)
touches=$(printf '%s\n' "${modifies}" | grep -Fxf <(printf '%s\n' "${SURVEILLES}") || true)

{
    echo "### Contrat de système de fichiers"
    echo
    if [ -n "${touches}" ]; then
        echo "Ce diff touche $(printf '%s\n' "${touches}" | wc -l) fichier(s) surveillé(s) :"
        echo
        printf '%s\n' "${touches}" | sed 's/^/- `/;s/$/`/'
    else
        echo "**Sans objet** : aucun fichier surveillé dans ce diff, les tests de contrat ne sont pas"
        echo "rejoués sur les trois plateformes."
        echo
        echo "Le job s'exécute quand même, et le dit : un job absent du récapitulatif se lirait comme"
        echo "un job vert, dans un dépôt qui n'a aucune protection de branche."
    fi
} >> "${GITHUB_STEP_SUMMARY:-/dev/stdout}"

if [ -n "${touches}" ]; then
    printf '%s\n' "${touches}" | sed 's/^/  · /'
    echo "concerne=oui" >> "${GITHUB_OUTPUT:-/dev/stdout}"
else
    echo "Aucun fichier surveillé dans ce diff : sans objet."
    echo "concerne=non" >> "${GITHUB_OUTPUT:-/dev/stdout}"
fi
