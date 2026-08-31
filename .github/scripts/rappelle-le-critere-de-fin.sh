#!/usr/bin/env bash
#
# Un lot ouvert sans critère de fin ne provoque aucun signal (#4977).
#
# ## Ce qu'il fait, et ce qu'il ne fait PAS
#
# `AGENTS.md` exige depuis #4975 que chaque lot dise dans son CORPS comment on saura qu'il est fini.
# Rien ne le tenait : le manque ne se découvrait qu'à la clôture du chantier, des semaines plus tard.
#
# Ce script est une LOUPE, pas un garde. Il ne refuse rien, il rend un texte à poster en commentaire
# sur le lot. Il ne peut faire rougir aucune demande de fusion, et c'est délibéré : les trois
# auditeurs de l'arbitrage de #4961 ont écarté d'une seule voix le rouge qui tombe sur qui n'a pas la
# main, des jours après l'ouverture qu'il juge.
#
# Il ne juge pas non plus la QUALITÉ d'un critère. « Fini quand c'est fait » lui convient. Deux
# dessins de garde mécanique sur de la prose d'EPIC ont déjà été mesurés puis écartés dans ce dépôt,
# faute de signal lisible, et l'arbitrage a repris cette conclusion.
#
# ## Pourquoi il reconnaît un lot de deux façons
#
# La forge enregistre `parent_issue_added` dans le fil d'une issue, mais **un workflow ne peut pas
# s'y abonner** : les types d'activité de l'évènement `issues` s'arrêtent à `opened`, `edited`,
# `labeled` et quinze autres, dont aucun ne concerne les sous-issues. Mesuré le 2026-08-31.
#
# Un lot est donc reconnu par son `parent`, lu à l'exécution, OU par la marque « Fait partie de #N »
# de son corps, qui est la moitié versionnée du même rattachement. Une issue rattachée après coup et
# jamais rééditée échappe à ce script : c'est le lot 3 de #4961, la loupe hebdomadaire, qui balaie le
# stock et rattrape ce que l'évènement a manqué.
#
# ## Le coût d'une erreur, et ce qu'il autorise
#
# Le motif partagé connaît cinq formulations, et leur provenance est dans `critere-de-fin.motif.md`.
# Une sixième apparaîtra, et il ne la verra pas.
#
# **Signaler à tort coûte un commentaire inutile ; se taire à tort ne coûte rien de plus**, le lot 3
# balayant le stock chaque semaine. C'est ce qui sépare cette loupe d'un cliquet : un cliquet faux
# bloque, celui-ci parle à côté ou se tait. Le motif peut donc rester généreux là où un cliquet aurait
# dû être exact.
#
# Sorties :
#   0, rien sur la sortie standard  : il n'y a rien à dire
#   0, un commentaire               : un rappel à poster, première ligne = MARQUE
#   2                               : il n'a pas pu lire, et ne conclut pas
#
# Usage : rappelle-le-critere-de-fin.sh <numéro d'issue>
#         rappelle-le-critere-de-fin.sh --auto-test
set -euo pipefail

export LC_ALL=C

# La marque que le workflow cherche dans les commentaires existants avant de poster. Sans elle, un
# lot rééditité trois fois recevrait trois fois le même rappel.
readonly MARQUE='<!-- rappel-critere-de-fin -->'

# Le sas des suites : on y consigne, on n'y prend rien. Une issue qui n'y pend qu'est pas un lot, et
# `verifie-chantier-de-l-issue.sh` tient déjà cette moitié-là.
readonly SAS=4562

# Le motif vit dans UN fichier, lu aussi par `loupe-4992-lots-sans-critere.py`. Il portait sa propre
# copie, et les deux avaient divergé sur deux caractères le jour de leur écriture : la cinquième
# formulation manquait aux deux, et rien ne pouvait le dire (#4995, #4837). Voir
# `scripts/adr/critere-de-fin.motif.md` pour les contraintes de dialecte.
# Injectable pour l auto-test, comme les corpus des deux cliquets de forge : sans cela le chemin
# de refus n est exerce par rien, et une mutation l a montre en le laissant vert.
readonly MOTIF="${CRITERE_MOTIF_FICHIER:-$(cd "$(dirname "$0")/../.." && pwd)/scripts/adr/critere-de-fin.motif}"
if [ ! -r "${MOTIF}" ]; then
    echo "REFUS : « ${MOTIF} » est illisible. Cette loupe ne conclut pas sans son motif." >&2
    exit 2
fi
CRITERE="$(head -1 "${MOTIF}")"
readonly CRITERE

# La marque de rattachement versionnée, celle que `ouvrir-une-issue` demande d'écrire dans le corps.
readonly RATTACHEMENT='Fait partie de #[0-9]+'

# Le corps et le parent d'une issue. Injectables : sans cela l'auto-test exigerait la forge, et un
# garde dont les cas ne tournent pas hors ligne ne se relance jamais.
lit() { # <numéro> <corps|parent>
    local numero="$1" champ="$2"
    if [ -n "${CRITERE_ISSUES_FICHIER-}" ]; then
        jq -r --arg n "${numero}" --arg c "${champ}" '.[$n][$c] // empty' "${CRITERE_ISSUES_FICHIER}"
        return
    fi
    if ! command -v gh > /dev/null 2>&1; then
        echo "REFUS : « gh » est absent. Cette loupe ne conclut pas sur ce qu'elle n'a pas lu." >&2
        exit 2
    fi
    case "${champ}" in
        corps)  gh issue view "${numero}" --json body -q '.body // ""' 2> /dev/null ;;
        parent) gh issue view "${numero}" --json parent -q '.parent.number // empty' 2> /dev/null ;;
    esac || { echo "REFUS : la forge n'a pas répondu pour #${numero}." >&2; exit 2; }
}

# Cette issue est-elle un LOT ? Deux voies, et la seconde rattrape ce que l'évènement ne dit pas.
estUnLot() { # <corps> <parent>
    local corps="$1" parent="$2"
    [ -n "${parent}" ] && [ "${parent}" != "${SAS}" ] && return 0
    [ -z "${parent}" ] && printf '%s' "${corps}" | grep -qE "${RATTACHEMENT}" && return 0
    return 1
}

rappel() { # <numéro>
    cat <<RAPPEL
${MARQUE}
Ce lot ne dit pas **comment on saura qu'il est fini**, et \`AGENTS.md\` le demande dans le **corps** de
l'issue depuis #4975.

Un critère se vérifie. « Fini quand la loupe est écrite » ne dit rien de plus que le titre ; « fini
quand elle rougit sur un lot muet et se tait sur un lot qui dit son critère » se joue.

Le corps, et non un commentaire : un commentaire descend sous le fil, le corps est ce que la clôture
relit. Rien ne bloque, et cette loupe ne repassera pas.
RAPPEL
}

juge() { # <numéro>
    local numero="$1" corps parent code=0

    # L'affectation est séparée de la déclaration : `local x=$(...)` rend toujours 0 et avalerait le
    # refus de la forge, qui deviendrait un silence.
    corps=$(lit "${numero}" corps) || code=$?
    [ "${code}" != 0 ] && exit 2
    parent=$(lit "${numero}" parent) || code=$?
    [ "${code}" != 0 ] && exit 2

    estUnLot "${corps}" "${parent}" || return 0
    printf '%s' "${corps}" | grep -qiE "${CRITERE}" && return 0
    rappel "${numero}"
}

if [ "${1-}" != "--auto-test" ]; then
    [ -z "${1-}" ] && { echo "Usage : $0 <numéro d'issue> | --auto-test" >&2; exit 2; }
    juge "$1"
    exit 0
fi

echecs=0
bac=$(mktemp -d)
trap 'rm -rf "${bac}"' EXIT

cat > "${bac}/issues.json" <<'JSON'
{
  "1": {"corps": "Un lot sans rien.", "parent": "4961"},
  "2": {"corps": "Un lot.\n\n## Fini quand\n\nLe garde rougit.", "parent": "4961"},
  "3": {"corps": "Un lot.\n\n**Fait quand** : les six y sont.", "parent": "4961"},
  "4": {"corps": "Un lot.\n\n## Comment on saura que chaque lot est fini\n\nIl rougit.", "parent": "4961"},
  "5": {"corps": "Une trouvaille consignee.", "parent": "4562"},
  "6": {"corps": "Une issue libre, sans parent ni marque."},
  "7": {"corps": "Un lot pas encore rattache.\n\nFait partie de #4961"},
  "8": {"corps": "Un lot pas encore rattache, qui dit son critere.\n\nFini quand il rougit.\n\nFait partie de #4961"},
  "9": {"corps": "Un lot.\n\nOn ne saura pas s il est fini. Rien d autre.", "parent": "4961"},
  "10": {"corps": "Un lot.\n\n**Ce que je vérifierai** : le garde rougit.", "parent": "4961"}
}
JSON

cas=0
signale=0
joue() { # <attendu: rappel|silence|refus> <libellé> <numéro> [fichier]
    local attendu="$1" libelle="$2" numero="$3" fic="${4:-${bac}/issues.json}" sortie code=0
    cas=$((cas + 1))
    [ "${attendu}" = rappel ] && signale=$((signale + 1))
    sortie=$(CRITERE_ISSUES_FICHIER="${fic}" "$0" "${numero}" 2> /dev/null) || code=$?
    local obtenu=silence
    [ -n "${sortie}" ] && obtenu=rappel
    [ "${code}" = 2 ] && obtenu=refus
    if [ "${obtenu}" = "${attendu}" ]; then
        echo "  ✔ ${libelle}"
    else
        echo "  ✘ ${libelle} : attendu ${attendu}, obtenu ${obtenu}"
        echecs=1
    fi
}

# Le cas qui compte : sans lui, tous les silences ne valent rien.
joue rappel  "un lot muet reçoit un rappel" 1
joue silence "« Fini quand » en section suffit" 2
joue silence "« Fait quand » suffit aussi, c'est le mot de quatre EPIC" 3
joue silence "la section « Comment on saura... » suffit, c'est le mot de deux autres" 4
joue silence "une issue du sas n'est pas un lot : rien ne s'y prend" 5
joue silence "une issue sans parent ni marque n'est pas un lot" 6
joue rappel  "la marque « Fait partie de » suffit à faire un lot, sans parent posé" 7
joue silence "un lot déclaré par la marque et qui dit son critère se tait" 8
joue rappel  "« on ne saura pas s'il est fini » n'est pas un critère : la négation ne compte pas" 9
joue silence "« Ce que je vérifierai » compte : c'est le mot que CLAUDE.md prescrit" 10

# Le motif illisible fait REFUSER, pas conclure. Sans ce cas, retirer le refus laisse l auto-test vert.
cas=$((cas + 1))
signale=$((signale + 1))
sortie=$(CRITERE_MOTIF_FICHIER="${bac}/nulle-part.motif" CRITERE_ISSUES_FICHIER="${bac}/issues.json" \
    "$0" 1 2>&1) && code=0 || code=$?
if [ "${code}" = 2 ] && printf '%s' "${sortie}" | grep -q 'est illisible'; then
    echo "  ✔ un motif illisible fait REFUSER au lieu de se taire"
else
    echo "  ✘ un motif illisible fait REFUSER au lieu de se taire : code ${code}, dit « ${sortie} »"
    echecs=1
fi

# La MARQUE doit être dans le rappel, sinon le workflow reposterait à chaque édition.
cas=$((cas + 1))
if CRITERE_ISSUES_FICHIER="${bac}/issues.json" "$0" 1 | head -1 | grep -q 'rappel-critere-de-fin'; then
    echo "  ✔ le rappel porte sa marque d'idempotence en première ligne"
else
    echo "  ✘ le rappel porte sa marque d'idempotence en première ligne"
    echecs=1
fi

# L'APPEL, et non le verdict (ADR 4331). Les cas ci-dessus injectent tous un leurre et n'exercent
# jamais `lit` par son chemin réel. Celui-ci le lance avec `gh` hors du PATH : aucun réseau, et il
# rougit en une milliseconde. Un PATH vidé ferait échouer `grep` AVANT le contrôle, et le refus
# tomberait pour la mauvaise raison ; le leurre ne retire donc que `gh`.
mkdir -p "${bac}/bin"
# `dirname` s ajoute depuis que le motif vit dans un fichier : le leurre ne retire que « gh », et
# lier moins ferait tomber le refus pour la mauvaise raison.
for outil in grep jq head dirname; do ln -sf "$(command -v "${outil}")" "${bac}/bin/${outil}"; done
cas=$((cas + 1))
signale=$((signale + 1))
sortie=$(PATH="${bac}/bin" "$(command -v bash)" "$0" 1 2>&1) && code=0 || code=$?
if [ "${code}" = 2 ] && printf '%s' "${sortie}" | grep -q 'est absent'; then
    echo "  ✔ sans « gh », l'appel REFUSE au lieu de se taire"
else
    echo "  ✘ sans « gh », l'appel REFUSE au lieu de se taire : code ${code}, dit « ${sortie} »"
    echecs=1
fi

# Un numéro absent du corpus injecté : la loupe ne doit pas conclure au silence sur ce qu'elle n'a
# pas lu. Sans parent ni corps, elle se tait, et c'est le bon comportement : une issue vide n'est
# pas un lot. Le cas est ici pour que ce choix soit écrit plutôt que subi.
joue silence "un numéro inconnu du corpus se lit comme une issue sans rattachement" 999

echo
echo "${cas} cas, dont ${signale} qui DOIVENT signaler ou refuser."
if [ "${echecs}" = 0 ]; then
    echo "Auto-test concluant : la loupe voit un lot muet, et se tait sur les formulations du motif."
else
    echo "Auto-test EN ÉCHEC."
fi
exit "${echecs}"
