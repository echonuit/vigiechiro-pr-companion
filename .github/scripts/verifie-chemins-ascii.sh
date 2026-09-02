#!/usr/bin/env bash
#
# Un chemin suivi ne porte que de l'ASCII (#5089).
#
# ## Pourquoi ce garde existe
#
# `git ls-files` et `git diff --name-only` ECHAPPENT les chemins non-ASCII par defaut :
# `brief/docs/Objectifs qualit\303\251s/...` la ou le disque porte `Objectifs qualites`. Tout
# outillage qui teste ensuite l'existence du fichier rejette la ligne, EN SILENCE. Le 2026-09-01,
# une passe de mise a jour du graphe a ainsi ecarte 50 fichiers et pris 6 renommages pour
# 56 suppressions. Rien n'a rougi : le corpus etait seulement incomplet.
#
# ## Ce qu'il refuse, et ce qu'il laisse passer
#
# Il refuse le seul octet qui cause le defaut : tout ce qui sort de l'ASCII dans un chemin SUIVI.
#
# Il ne dit RIEN de la casse, des espaces, des apostrophes ni des tirets. `core.quotePath` ne les
# echappe pas, ils ne sont pas la cause, et `C12`, `P17`, `M-CompteRendu` sont des identifiants,
# pas des phrases a normaliser. Un garde qui refuserait plus large refuserait sans mesure.
#
# ## Tolerance zero, et pourquoi c'est tenable
#
# Les 101 chemins fautifs ont ete renommes par #5089. La zone est a zero le jour de la decision,
# donc le seuil est zero et non un cliquet : c'est la regle de l'ADR sur les cliquets, qui reserve
# le cliquet aux zones qu'on ne peut pas vider d'un coup.
#
# Usage : verifie-chemins-ascii.sh              (juge le depot)
#         verifie-chemins-ascii.sh --auto-test  (s'eprouve lui-meme)
set -euo pipefail

export LC_ALL=C.UTF-8

# La liste des chemins suivis. Injectable pour l'auto-test : sans cela ses cas exigeraient de
# salir le depot, et un garde dont les cas ne tournent pas hors ligne ne se relance jamais.
#
# `-c core.quotePath=false` n'est pas un detail : sans lui git rend les chemins fautifs DEJA
# echappes en ASCII pur, et ce garde les declarerait conformes. Il serait vert precisement sur ce
# qu'il doit refuser.
cheminsSuivis() {
    if [ -n "${CHEMINS_FICHIER-}" ]; then
        cat "${CHEMINS_FICHIER}"
        return
    fi
    git -c core.quotePath=false ls-files
}

juge() {
    local fautifs
    fautifs=$(cheminsSuivis | grep -P '[^\x00-\x7F]' || true)

    if [ -z "${fautifs}" ]; then
        echo "Tous les chemins suivis sont en ASCII."
        return 0
    fi

    echo "REFUS : des chemins suivis portent des caracteres non-ASCII."
    echo
    printf '%s\n' "${fautifs}" | sed 's/^/  /'
    echo
    echo "Ces chemins sont invisibles a toute commande git qui les liste sans"
    echo "« -c core.quotePath=false », et le rejet est SILENCIEUX."
    echo "Renommez-les en translitterant les lettres accentuees (e pour e accent aigu, etc.)."
    return 1
}

if [ "${1-}" = "--auto-test" ]; then
    echecs=0
    cas=0
    rouges=0
    bac=$(mktemp -d)
    trap 'rm -rf "${bac}"' EXIT

    joue() { # <attendu: ok|rouge> <libelle> <contenu de la liste>
        local attendu="$1" libelle="$2" contenu="$3" code=0 obtenu=ok
        cas=$((cas + 1))
        [ "${attendu}" != ok ] && rouges=$((rouges + 1))
        printf '%s' "${contenu}" > "${bac}/liste"
        CHEMINS_FICHIER="${bac}/liste" "$0" > /dev/null 2>&1 || code=$?
        [ "${code}" != 0 ] && obtenu=rouge
        if [ "${obtenu}" = "${attendu}" ]; then
            echo "  OK  ${libelle}"
        else
            echo "  KO  ${libelle} : attendu ${attendu}, obtenu ${obtenu}"
            echecs=1
        fi
    }

    # Les cas qui comptent sont les ROUGES : sans eux, tous les verts ne valent rien.
    joue rouge "un nom de fichier accentue est refuse" \
        "$(printf 'src/Ok.java\nbrief/docs/Modele/C10 - Releve climatique.md\nbrief/docs/Mod\303\250le/x.md\n')"
    joue rouge "un REPERTOIRE accentue est refuse, meme si le fichier est propre" \
        "$(printf 'brief/docs/Objectifs qualit\303\251s/index.md\n')"
    joue rouge "un caractere non-latin est refuse aussi" \
        "$(printf 'docs/\346\227\245\346\234\254.md\n')"
    joue ok "des espaces et des majuscules passent : ils ne sont pas la cause" \
        "$(printf 'brief/docs/Analyse et conception/M-CompteRendu.md\nbrief/README.md\n')"
    joue ok "une apostrophe droite passe" \
        "$(printf "brief/docs/C3 - Point d'ecoute.md\n")"
    joue ok "une liste vide passe" ""

    # LE CHEMIN REEL, et non le leurre (ADR 4331). Les cas ci-dessus injectent tous
    # `CHEMINS_FICHIER` et n'exercent JAMAIS `git ls-files`. Sans ce cas, la fonction qui interroge
    # git n'est eprouvee par rien - et c'est elle qui porte le `-c core.quotePath=false` dont tout
    # depend. On le lance donc pour de vrai, sur le depot, et on exige qu'il conclue.
    cas=$((cas + 1))
    if juge > /dev/null 2>&1; then
        echo "  OK  le chemin reel (git ls-files) conclut sur le depot"
    else
        echo "  KO  le chemin reel refuse le depot : des chemins non-ASCII y subsistent"
        echecs=1
    fi

    echo
    echo "${cas} cas, dont ${rouges} qui doivent rougir."
    exit "${echecs}"
fi

juge
