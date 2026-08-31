#!/usr/bin/env bash
# Trois décisions du tournage connecté vivent dans du YAML, et rien ne les gardait (#4331,
# chantier #4291).
#
# ## Pourquoi celles-là, et pas d'autres
#
# Inventaire de la passe 6, fait depuis le diff. Le reste du chantier a des `--auto-test` joués à
# chaque `lint` ; ces trois-là n'en avaient pas, et la différence n'était pas la volonté : leur
# logique vit dans un `if:` de workflow, pas dans un script.
#
# La première est la plus sournoise, parce qu'elle EST un garde. Un `if` mal écrit la neutraliserait
# sans que rien ne rougisse, et la comparaison des tournages recommencerait à mesurer la plateforme
# en croyant mesurer le rendu.
#
# ## Ce que chacune tient
#
#   1. `comparer-tournages.yml` REFUSE la source `clips-connectes`. Ses clips sont tournés contre la
#      vraie plateforme : deux tournages du même commit y diffèrent parce qu'une nuit a été traitée
#      entre les deux, et le plancher de bruit de #4287 mesurerait la plateforme au lieu du produit.
#      Le refus vaut mieux que le chiffre (#4306).
#
#   2. `publier-connecte` dépend de `filmer` ET porte une FONCTION D'ÉTAT. Sans elle, la propagation
#      du « sauté » est transitive et la condition écrite n'est jamais évaluée - le défaut que
#      `verifie-conditions-de-job.sh` a déjà payé une fois, run 32224321373, huit jours sans le voir.
#
#   3. Le contrôle du jeton vient AVANT le pas qui filme. L'ordre est une propriété que le YAML perd
#      en silence : sonder après avoir filmé ne coûte rien et ne sert à rien.
#
# ## Le refus n'est pas relu, il est LANCÉ
#
# Il ne vit pas dans un `if:` de YAML mais dans un `run:`, c'est-à-dire du shell. On l'extrait donc et
# on l'exécute pour de bon, avec `gh` remplacé par un leurre pour que rien ne dépende du réseau. Un
# garde qu'on exécute vaut mieux qu'un garde qu'on relit : la technique a sorti un défaut réel
# d'`api-live.yml` le jour où elle a été essayée (#4328).
#
# Et on juge sur le MESSAGE, pas sur le code de sortie : avec un leurre qui échoue, toute source
# sortirait non nulle, et le garde passerait au vert en croyant avoir vu le refus.
#
# ## L'auto-test porte ses propres mutations
#
# Il ne se contente pas de rejouer les workflows tels quels - ils sont justes, donc tout serait vert
# et ne prouverait rien. Il fabrique trois copies CASSÉES, une par décision, et exige que chacune
# rougisse SA case et elle seule.
#
# Usage : ./.github/scripts/verifie-decisions-du-tournage-connecte.sh [--auto-test] [répertoire]
set -uo pipefail

# PyYAML est requis : un YAML de workflow ne se lit pas a la ligne sans se tromper (blocs, ancres,
# `on:` que YAML interprete en booleen). S'il manque, la garde REFUSE bruyamment - une garde qui se
# saute quand son outillage manque est un faux vert de plus.
#
# Ce controle DEFINIT une fonction et ne s'execute pas ici : il est appele au point d'usage. Un refus
# pose en tete de fichier passerait AVANT le dispatch des options, et rendrait un refus la ou
# l'appelant demandait autre chose (#5008).
exige_pyyaml() {
  python3 -c 'import yaml' 2>/dev/null && return 0
  echo "❌ PyYAML est absent : cette garde ne peut pas lire les workflows."
  echo "   Installer avec « pip install --group gardes », qui lit pyproject.toml."
  return 1
}

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"

# --- 1. Le refus de `clips-connectes`, exécuté ------------------------------------------------------

refus_de_la_source_connectee() { # <répertoire de workflows>
    local flux="$1" bac bloc sortie
    bac=$(mktemp -d)

    # Un leurre pour `gh` : le refus doit tomber AVANT tout appel réseau, et si un jour il tombe
    # après, on veut le voir ici plutôt qu'en production.
    mkdir -p "${bac}/bin"
    printf '#!/usr/bin/env bash\nexit 1\n' > "${bac}/bin/gh"
    chmod +x "${bac}/bin/gh"

    bloc="${bac}/reprendre.sh"
    exige_pyyaml || return 1
    if ! python3 - "${flux}/comparer-tournages.yml" "${bloc}" <<'PY'; then
import sys

import yaml

flux, sortie = sys.argv[1], sys.argv[2]
f = yaml.safe_load(open(flux, encoding="utf-8"))
blocs = [
    e["run"]
    for j in f["jobs"].values()
    for e in j.get("steps", [])
    if "reprendre()" in e.get("run", "")
]
if len(blocs) != 1:
    print("❌ %d pas définissent `reprendre()` dans comparer-tournages.yml, attendu 1." % len(blocs))
    print("   La forme a changé : ce garde ne sait plus quoi lancer, et il le dit plutôt que")
    print("   de rendre un vert qui ne vaudrait rien.")
    sys.exit(1)
open(sortie, "w", encoding="utf-8").write(blocs[0])
PY
        rm -rf "${bac}"
        return 1
    fi

    sortie=$(cd "${bac}" && PATH="${bac}/bin:${PATH}" AVANT=clips-connectes APRES=v1.0.0 \
        bash "${bloc}" 2>&1)

    # On juge sur le MESSAGE, et il faut y tenir. Le leurre fait échouer toutes les sources : un verdict pris sur le
    # code de sortie serait vert quoi qu'il arrive.
    if ! printf '%s' "${sortie}" | grep -q "ne se compare pas"; then
        echo "❌ comparer-tournages.yml n a pas refusé la source « clips-connectes »."
        echo "   Ce refus EST un garde : sans lui, la comparaison mesure la plateforme au lieu du"
        echo "   produit et rend un chiffre qui a l air juste (#4306)."
        rm -rf "${bac}"
        return 1
    fi

    # Le contrôle de l autre bord : une source ordinaire ne doit PAS déclencher ce refus.
    sortie=$(cd "${bac}" && PATH="${bac}/bin:${PATH}" AVANT=v2.186.0 APRES=v2.187.0 \
        bash "${bloc}" 2>&1)
    if printf '%s' "${sortie}" | grep -q "ne se compare pas"; then
        echo "❌ comparer-tournages.yml refuse AUSSI une source ordinaire : le refus ne discrimine plus."
        rm -rf "${bac}"
        return 1
    fi

    rm -rf "${bac}"
    return 0
}

# --- 2. et 3. Les deux propriétés qui se lisent dans le YAML ----------------------------------------

versement_conditionne() { # <répertoire de workflows>
    exige_pyyaml || return 1
    python3 - "$1/tournage-recette.yml" <<'PY'
import re
import sys

import yaml

f = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
job = f["jobs"].get("publier-connecte")
if job is None:
    print("❌ Le job `publier-connecte` a disparu de tournage-recette.yml.")
    sys.exit(1)

besoins = job.get("needs") or []
if isinstance(besoins, str):
    besoins = [besoins]
if "filmer" not in besoins:
    print("❌ `publier-connecte` ne dépend plus de `filmer` : un tournage amputé pourrait publier.")
    sys.exit(1)

condition = str(job.get("if", ""))
# La fonction d'état, et non la condition elle-même. Sans elle, GitHub enveloppe la condition en
# `success() && (...)` sur TOUT le graphe amont : la porte qu'on croit avoir écrite n'est jamais
# évaluée, rien ne rougit, et le job est simplement sauté.
if not re.search(r"\b(always|success|failure|cancelled)\s*\(\s*\)", condition):
    print("❌ La condition de `publier-connecte` ne porte aucune fonction d'état :")
    print("     if: %s" % (condition or "(absente)"))
    print("   Le « sauté » se propage transitivement : cette porte ne serait jamais évaluée.")
    sys.exit(1)
PY
}

controle_avant_le_tournage() { # <répertoire de workflows>
    exige_pyyaml || return 1
    python3 - "$1/tournage-recette.yml" <<'PY'
import re
import sys

import yaml

f = yaml.safe_load(open(sys.argv[1], encoding="utf-8"))
pas = f["jobs"]["filmer"]["steps"]
noms = [str(e.get("name", "")) for e in pas]

sondes = [i for i, n in enumerate(noms) if re.search(r"jeton.*vivant", n, re.I)]
tournages = [i for i, n in enumerate(noms) if n.strip() == "Filmer"]
if not sondes:
    print("❌ Aucun pas ne contrôle le jeton dans le job `filmer`.")
    sys.exit(1)
if not tournages:
    print("❌ Le pas « Filmer » a disparu du job `filmer` : ce garde ne sait plus par rapport à quoi")
    print("   juger l'ordre, et il le dit plutôt que de rendre un vert vide.")
    sys.exit(1)

if min(sondes) > min(tournages):
    print("❌ Le contrôle du jeton vient APRÈS le pas qui filme (%d puis %d)." % (min(tournages) + 1, min(sondes) + 1))
    print("   Sonder après avoir filmé ne coûte rien et ne sert à rien : le clip hors ligne est")
    print("   déjà tourné quand on apprend que le jeton était mort.")
    sys.exit(1)

condition = str(pas[min(sondes)].get("if", ""))
if "inputs.connecte" not in condition:
    print("❌ Le contrôle du jeton n'est plus gardé par `inputs.connecte` :")
    print("     if: %s" % (condition or "(absente)"))
    print("   Il refuserait alors tout tournage hors ligne, qui n'a pas de jeton et n'en veut pas.")
    sys.exit(1)
PY
}

# --- Le verdict d'ensemble ---------------------------------------------------------------------------

verdict() { # <répertoire de workflows>  -> 0 si les trois tiennent
    local flux="$1" echecs=0
    refus_de_la_source_connectee "${flux}" || echecs=$((echecs + 1))
    versement_conditionne "${flux}" || echecs=$((echecs + 1))
    controle_avant_le_tournage "${flux}" || echecs=$((echecs + 1))
    [ "${echecs}" -eq 0 ]
}

# --- L'auto-test, qui fabrique ses propres cassures ---------------------------------------------------

auto_test() {
    local total=0 echecs=0 bac
    bac=$(mktemp -d)
    echo "AUTO-TEST"

    # `sain` : les workflows tels qu'ils sont. Il doit être VERT, sinon tout le reste ment.
    mkdir -p "${bac}/sain"
    cp "${RACINE}/.github/workflows/tournage-recette.yml" \
       "${RACINE}/.github/workflows/comparer-tournages.yml" "${bac}/sain/"

    fabriquer() { # <nom> <recette python>
        local nom="$1"
        mkdir -p "${bac}/${nom}"
        cp "${bac}/sain/"*.yml "${bac}/${nom}/"
        exige_pyyaml || return 1
        python3 - "${bac}/${nom}" <<PY
import sys
$2
PY
    }

    essai() { # <répertoire> <attendu vert|rouge> <libellé>
        local ou="$1" attendu="$2" nom="$3" obtenu=vert
        verdict "${bac}/${ou}" >/dev/null 2>&1 || obtenu=rouge
        total=$((total + 1))
        if [ "${obtenu}" = "${attendu}" ]; then
            printf '  [OK   ] %-62s -> %s\n' "${nom}" "${obtenu}"
        else
            printf '  [ÉCHEC] %-62s -> %s (attendu %s)\n' "${nom}" "${obtenu}" "${attendu}"
            echecs=$((echecs + 1))
        fi
    }

    essai sain vert "les workflows tels qu ils sont"

    # Les trois cassures, et c'est là que ce fichier gagne son verdict. Chacune retire UNE décision, et rien d'autre.
    fabriquer sans-refus '
import io, os, re
p = os.path.join(sys.argv[1], "comparer-tournages.yml")
t = open(p, encoding="utf-8").read()
t = t.replace("""            if [ "$source" = "clips-connectes" ]; then""", """            if false; then""", 1)
open(p, "w", encoding="utf-8").write(t)
'
    essai sans-refus rouge "le refus de clips-connectes neutralisé"

    fabriquer sans-fonction-d-etat '
import os
p = os.path.join(sys.argv[1], "tournage-recette.yml")
t = open(p, encoding="utf-8").read()
t = t.replace(
    """    if: ${{ success() && inputs.connecte && needs.revoquer.outputs.revoque == \x27oui\x27 }}""",
    """    if: ${{ inputs.connecte && needs.revoquer.outputs.revoque == \x27oui\x27 }}""",
    1,
)
open(p, "w", encoding="utf-8").write(t)
'
    essai sans-fonction-d-etat rouge "publier-connecte privé de sa fonction d état"

    fabriquer sonde-apres-le-tournage '
import os
import yaml
p = os.path.join(sys.argv[1], "tournage-recette.yml")
f = yaml.safe_load(open(p, encoding="utf-8"))
pas = f["jobs"]["filmer"]["steps"]
i = [n for n, e in enumerate(pas) if "vivant" in str(e.get("name", ""))][0]
j = [n for n, e in enumerate(pas) if str(e.get("name", "")).strip() == "Filmer"][0]
pas.insert(j + 1, pas.pop(i))
yaml.safe_dump(f, open(p, "w", encoding="utf-8"), allow_unicode=True, sort_keys=False)
'
    essai sonde-apres-le-tournage rouge "le contrôle du jeton déplacé après le tournage"

    rm -rf "${bac}"
    echo
    echo "${total} cas : les workflows sains, puis une cassure par décision gardée."
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

FLUX="${1:-${RACINE}/.github/workflows}"
if verdict "${FLUX}"; then
    echo "✓ Les trois décisions du tournage connecté tiennent : refus de clips-connectes, versement"
    echo "  conditionné, contrôle du jeton avant le tournage."
    exit 0
fi
echo "::error::Une décision du tournage connecté n est plus tenue par le YAML, cf. ci-dessus."
exit 1
