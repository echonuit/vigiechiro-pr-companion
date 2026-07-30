#!/usr/bin/env bash
# Vérifie qu'un titre de PR suit Conventional Commits (#2105).
#
# Le dépôt fusionne en SQUASH avec `squash_merge_commit_title = PR_TITLE` : le titre de la PR devient
# le sujet du commit sur `main`, et les messages des commits de la branche sont écartés à la fusion.
# C'est donc ce titre, et lui seul, que semantic-release lira.
#
# Ce contrôle existe parce que son absence a coûté cher : la pratique a dérivé vers `type(scope) :`
# (avec l'espace français avant les deux-points), que Conventional Commits n'admet pas. semantic-release
# a cessé de publier le 2026-07-18 en finissant VERT à chaque push, et 58 commits releasables se sont
# accumulés sans que rien ne le signale. Cf. ADR 0040.
#
# Usage : verifie-titre-pr.sh "<titre>"   (sortie 0 si conforme, 1 sinon)
set -euo pipefail

TITRE="${1-}"

# Le glyphe se CONSTRUIT plutôt que de s'écrire, pour que ce fichier n'en porte aucun : c'est
# l'idiome de `verifie_scripts.py`, dont les fixtures bâtissent le cadratin par `chr(0x2014)`.
# Sans cela, le garde des cadratins compterait la prose de son propre garde.
CADRATIN=$(printf '\u2014')

# Types réellement pratiqués dans le dépôt. `feat` -> mineure, `fix`/`perf` -> patch ; les autres ne
# déclenchent pas de version (cf. CONTRIBUTING.md §3).
MOTIF='^(feat|fix|perf|refactor|docs|test|chore|ci|build|style|revert)(\([a-z0-9._-]+\))?!?: .+'

# Auto-test (#2947). Ce script est lui-même un garde, et un garde qui cesse de détecter reste vert :
# c'est le faux vert que `verifie_scripts.py` interdit aux scripts d'ADR, appliqué ici. Chaque cas
# nomme un titre CONNU et le code attendu. Lancé par `lint.yml`, à côté de l'auto-test des ADR.
if [ "${TITRE}" = "--auto-test" ]; then
    echecs=0
    verifie() { # <attendu> <titre> <libellé>
        code=0
        "$0" "$2" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $3"
        else
            echo "  ✘ $3 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }
    verifie 0 "docs(adr): un titre conforme et sans cadratin" "un titre conforme passe"
    verifie 1 "docs(adr): un titre ${CADRATIN} avec un cadratin" "un cadratin est refusé"
    verifie 1 "docs(adr) : un espace avant les deux-points" "l'espace avant le deux-points est refusé"
    verifie 1 "un titre qui ignore Conventional Commits" "un titre non conventionnel est refusé"
    verifie 1 "" "un titre vide est refusé"
    # Épingle une DÉCISION, pas seulement un comportement : le titre n'exempte AUCUNE citation, là où
    # le garde des fichiers épargne le glyphe entre guillemets français. Qui voudrait aligner les deux
    # règles fera d'abord rougir ce cas, et lira la raison juste au-dessus.
    verifie 1 "fix(audio): le glyphe « — » ne se rend plus" "aucune exemption de citation dans un titre"
    verifie 1 "docs(adr): 2843 renvoie vers l amendement" "une élision sans apostrophe est refusée"
    verifie 1 "fix(cli): d une nuit a l autre" "plusieurs élisions, même refus"
    # Contrôles NÉGATIFS : la règle doit rester étroite. Un code de point, un numéro et une élision
    # correcte ne déclenchent pas, faute d'une lettre après l'espace ou faute d'espace.
    verifie 0 "fix(passage): le point C3 et le carre A1 sont distincts" "un code de point ne déclenche pas"
    verifie 0 "feat(cli): le n° 4 est traite" "un numéro ne déclenche pas"
    verifie 0 "test(fixture): deux semeurs prennent l'entree legere" "une élision correcte ne déclenche pas"
    exit "${echecs}"
fi

if [ -z "${TITRE}" ]; then
    echo "::error::Titre de PR vide."
    exit 1
fi

echo "Titre : ${TITRE}"

# Le tiret cadratin (#2947). Le chantier #2365 a mis tout le dépôt en tolérance zéro, à une exception
# assumée : `CHANGELOG.md`, produit par semantic-release depuis les sujets de commits déjà fusionnés.
# Le corriger falsifierait le compte rendu de ce qui a été livré, et la ligne réécrite reviendrait à
# la génération suivante. Sa SOURCE, en revanche, est ce titre : c'est le seul endroit où la règle
# peut s'appliquer avant que le report ne soit gravé.
#
# Aucune exemption de citation ici, contrairement au garde des fichiers (ADR 2843), qui épargne le
# glyphe entre guillemets français ou chevrons de code. Un titre fait soixante-dix caractères et
# devient une ligne de journal : qui doit vraiment parler du glyphe écrit « le glyphe de valeur
# absente ». La règle stricte s'explique en une phrase, là où reconnaître une citation en bash
# demanderait de découper des guillemets multi-octets pour un cas qui ne s'est jamais présenté.
if printf '%s' "${TITRE}" | grep -qF "${CADRATIN}"; then
    echo "::error::Le titre de la PR contient un tiret cadratin."
    echo
    echo "  écrit : ${TITRE}"
    echo
    echo "Écrivez un deux-points, une virgule ou un tiret simple."
    echo
    echo "Ce titre devient la ligne du CHANGELOG (fusion en squash), et le CHANGELOG est le seul"
    echo "fichier que la règle typographique ne peut pas rattraper après coup : le corriger"
    echo "falsifierait le compte rendu de ce qui a été livré. C'est donc ici que ça se joue."
    echo "Cf. dev-docs/decisions/2843-typographie-cliquet-plutot-que-nettoyage.md"
    exit 1
fi

# L'élision sans apostrophe. Ce défaut-ci ne vient pas d'une faute de frappe mais d'une HABITUDE :
# écrire « l ADR », « d une nuit », « n est pas » pour survivre au quoting d'un shell, puis continuer
# une fois la contrainte disparue. Le titre part ensuite tel quel dans le CHANGELOG publié.
#
# La règle est étroite à dessein : un mot d'UNE lettre élidable (ou « qu ») suivi d'une espace puis
# d'une lettre. Mesurée sur les 250 derniers titres fusionnés du dépôt, elle en touche 12, et les 12
# sont de vrais défauts : aucun faux positif. Les codes de point (« A1 », « C3 »), les numéros
# (« n° 4 ») et les élisions correctes (« l'entrée », « qu'il ») ne déclenchent pas, faute d'une lettre
# après l'espace ou d'une espace tout court.
#
# Ce qu'elle ne voit PAS, et c'est assumé : un accent manquant sur un mot qui existe aussi sans accent
# (« garde » / « gardé », « complete » / « complète »). Aucun motif ne tranche sans dictionnaire, et un
# garde à faux positifs se contourne. Pour ceux-là, il reste la relecture.
ELISION="(^|[^[:alnum:]'’])([LlDdNnSsCcJjMmTt]|[Qq]u) +[[:alpha:]]"
if printf '%s' "${TITRE}" | grep -qE "${ELISION}"; then
    echo "::error::Le titre de la PR contient une élision sans apostrophe."
    echo
    echo "  écrit : ${TITRE}"
    echo
    echo "Rétablissez l'apostrophe : « l'ADR », « d'une nuit », « n'est pas », « qu'il »."
    echo
    echo "Ce titre devient la ligne du CHANGELOG publié. L'habitude d'amputer les apostrophes vient du"
    echo "quoting shell ; elle survit à la disparition de sa cause, et cinq titres l'ont montré le"
    echo "2026-07-30. Rédigez le titre en français correct, le quoting se règle autrement."
    exit 1
fi

if printf '%s' "${TITRE}" | grep -qE "${MOTIF}"; then
    echo "Titre conforme."
    exit 0
fi

echo "::error::Le titre de la PR ne suit pas Conventional Commits."
echo

# L'erreur la plus fréquente mérite d'être nommée : c'est celle qui a arrêté la release.
if printf '%s' "${TITRE}" | grep -qE '^[a-z]+(\([a-z0-9._-]+\))? +:'; then
    ATTENDU=$(printf '%s' "${TITRE}" | sed -E 's/^([a-z]+(\([a-z0-9._-]+\))?) +:/\1:/')
    echo "Cause probable : un ESPACE avant les deux-points."
    echo
    echo "  écrit   : ${TITRE}"
    echo "  attendu : ${ATTENDU}"
    echo
    echo "Dans 'feat(scope): sujet', le ':' est un token de syntaxe, pas une ponctuation de phrase :"
    echo "la règle typographique française de l'espace avant ':' ne s'y applique pas. Un espace y rend"
    echo "le titre illisible pour semantic-release, qui cesse de publier SANS rougir."
    echo "Cf. dev-docs/decisions/0040-le-sujet-de-commit-est-une-syntaxe.md"
else
    echo "Forme attendue : type(scope): sujet en français"
    echo
    echo "  feat(passage): écran pivot d'une nuit (statut + navigation)"
    echo "  fix(importation): import hors fil JavaFX gelait l'écran"
    echo
    echo "Types admis : feat, fix, perf, refactor, docs, test, chore, ci, build, style, revert."
fi
exit 1
