#!/usr/bin/env bash
# Un emballage de distribution démarre-t-il ? (#3617, suite de #2213 et #2107)
#
# ## Ce qu'il vérifie, et ce que la CI vérifiait déjà
#
# `maven.yml` construit l'app-image à chaque PR (#2256) **et la lance** (#2299), parce que la v2.32.3
# était installable et incapable de démarrer. Ce contrôle porte sur la **charge utile**.
#
# Il ne portait sur **aucune enveloppe**. Or chaque emballage a été choisi POUR CE QU'IL PRÉSERVE, ce
# qui est une autre façon de dire que chacun a une façon connue de casser :
#
#   - `tar.gz` (Linux) garde le **bit exécutable** du lanceur ;
#   - `ditto` (macOS) est le seul outil qui préserve un bundle `.app` intact - un `zip -r` casse ses
#     liens symboliques et ses permissions, et l'application ne s'ouvre plus ;
#   - `appimagetool` produit un fichier unique, et a déjà fait échouer la release v2.21.0.
#
# Trois emballages, trois modes de rupture documentés, et rien qui les ouvrait pour regarder.
#
# ## Deux conditions, pas une
#
# Le processus doit **tenir debout**, ET sa sortie doit être **exempte d'erreur de chargement**. Une
# exception sur un fil de fond ne tue pas le processus : s'en tenir à la survie fabriquerait un vert
# creux. C'est la règle héritée de #2299, que ce script reprend au lieu de la dupliquer.
#
# ## Pourquoi un script, et pas des lignes dans le workflow
#
# Parce que la vérification des enveloppes vit dans `release.yml`, **qu'aucune PR ne traverse**. Une
# étape écrite là peut être fusionnée cassée et ne se découvrir qu'au train suivant, en bloquant la
# publication. Sortie en script, elle porte son `--auto-test`, joué à chaque PR par `lint.yml`, et
# `maven.yml` l'exerce en vrai sur l'app-image Linux.
#
# ⚠️ **Bash seulement, donc Linux et macOS.** L'archive portable Windows n'est pas couverte ici : son
# lanceur est en sous-système graphique et le suivi de processus depuis Git Bash n'a pas pu être
# éprouvé. Le `.msi`, lui, est installé ET lancé par `winget.yml`.
#
# Usage : verifie-demarrage-emballage.sh <lanceur> [--secondes N] [--libelle TEXTE]
#         verifie-demarrage-emballage.sh --auto-test
set -uo pipefail

MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

MOTIFS_DE_CHARGEMENT='NoClassDefFoundError|ClassNotFoundException|Exception in Application start method'

### Lance le binaire donné et rend 0 s'il tient debout sans erreur de chargement.
verifier_le_demarrage() {
  local lanceur="$1" secondes="$2" libelle="$3"

  if [ ! -e "$lanceur" ]; then
    echo "::error::${libelle} : le lanceur est absent de l'emballage ($lanceur)."
    echo "   L'archive s'est décompressée, mais elle ne contient pas ce qu'elle promet."
    return 1
  fi

  # Le bit exécutable est la raison d'être du choix de tar.gz : son absence est un défaut d'emballage,
  # pas une curiosité de permissions. On le NOMME au lieu de le réparer par un chmod complaisant.
  if [ ! -x "$lanceur" ]; then
    echo "::error::${libelle} : le lanceur a perdu son bit exécutable dans l'emballage."
    echo "   $(ls -l "$lanceur")"
    echo "   C'est exactement ce que le format tar.gz est censé préserver."
    return 1
  fi

  local journal
  journal=$(mktemp)
  env -u DISPLAY JAVA_TOOL_OPTIONS=-Dglass.platform=Headless "$lanceur" >"$journal" 2>&1 &
  local pid=$!

  local tenu=0
  while [ "$tenu" -lt "$secondes" ] && kill -0 "$pid" 2>/dev/null; do
    sleep 1
    tenu=$((tenu + 1))
  done

  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  else
    local code
    set +e; wait "$pid"; code=$?; set -e
    echo "::error::${libelle} : arrêté seul après ${tenu} s (code ${code}) - l'emballage ne démarre pas."
    sed -n '1,120p' "$journal"
    rm -f "$journal"
    return 1
  fi

  if grep -qE "$MOTIFS_DE_CHARGEMENT" "$journal"; then
    echo "::error::${libelle} : reste en vie, mais son démarrage a levé une erreur de chargement."
    grep -nE "$MOTIFS_DE_CHARGEMENT" "$journal" | head -20
    rm -f "$journal"
    return 1
  fi

  echo "${libelle} : démarre depuis l'emballage et tient ${tenu} s, sans erreur de chargement."
  rm -f "$journal"
  return 0
}

### Le lanceur de ligne de commande de l'emballage répond-il ? Rend 0 s'il rend sa version en code 0.
###
### ⚠️ Un emballage peut ouvrir sa fenêtre et n'exposer AUCUNE commande : c'est l'état dans lequel le
### produit a vécu jusqu'à #4071, et rien ici ne pouvait le dire - le contrôle du dessus n'ouvre que le
### lanceur graphique. On demande donc sa version au lanceur CLI, seule invocation qui ne touche ni la
### base, ni le réseau, ni le dossier de travail.
verifier_la_ligne_de_commande() {
  local cli="$1" libelle="$2"

  if [ ! -e "$cli" ]; then
    echo "::error::${libelle} : l'emballage n'expose aucun lanceur de ligne de commande ($cli)."
    echo "   La fenêtre s'ouvre, mais aucune des commandes n'est atteignable : c'est exactement ce que"
    echo "   #4071 a corrigé, et ce contrôle est là pour que ça ne revienne pas en silence."
    return 1
  fi

  if [ ! -x "$cli" ]; then
    echo "::error::${libelle} : le lanceur de ligne de commande a perdu son bit exécutable."
    echo "   $(ls -l "$cli")"
    return 1
  fi

  local sortie code=0
  sortie=$("$cli" --version 2>&1) || code=$?

  if [ "$code" != 0 ]; then
    echo "::error::${libelle} : le lanceur de ligne de commande rend ${code} au lieu de sa version."
    printf '%s\n' "$sortie" | sed -n '1,20p'
    return 1
  fi

  # ⚠️ Le cas qui justifie ce contrôle plutôt qu'un simple `$?`. Un lanceur bâti en sous-système
  # graphique n'écrit NULLE PART et rend 0 : sa panne est indiscernable d'un succès pour qui ne
  # regarde que le code de sortie. On exige donc d'avoir lu quelque chose.
  if [ -z "$(printf '%s' "$sortie" | tr -d '[:space:]')" ]; then
    echo "::error::${libelle} : le lanceur de ligne de commande rend 0 mais n'écrit RIEN."
    echo "   Un lanceur sans console se comporte exactement ainsi ; le vert serait creux."
    return 1
  fi

  echo "${libelle} : la ligne de commande répond (${sortie%%$'\n'*})."
  return 0
}

# ---------------------------------------------------------------------------------------------
# Auto-test, hors ligne : des faux lanceurs, pour éprouver les quatre issues sans construire
# d'app-image. Les cas rouges se vérifient sur leur MESSAGE, un `exit 1` pouvant venir du script.
# ---------------------------------------------------------------------------------------------
if [ "${1:-}" = "--auto-test" ]; then
  echecs=0
  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  faux() { printf '#!/usr/bin/env bash\n%s\n' "$2" > "$bac/$1"; chmod +x "$bac/$1"; }

  faux tient        'sleep 30'
  faux meurt        'echo "boum"; exit 3'
  faux bavard       'echo "Exception in Application start method"; sleep 30'
  faux sans_droit   'sleep 30'
  chmod -x "$bac/sans_droit"

  verifie() { # <attendu> <fragment attendu dans le message> <libellé> <lanceur>
    local sortie code=0
    sortie=$( "$MOI" "$bac/$4" --secondes 2 --libelle "essai" 2>&1 ) || code=$?
    if [ "$code" != "$1" ]; then
      echo "  ✘ $3 : attendu $1, obtenu $code"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=1
      return
    fi
    if ! printf '%s' "$sortie" | grep -qF "$2"; then
      echo "  ✘ $3 : code correct, mais le message ne dit pas « $2 »"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=1
      return
    fi
    echo "  ✔ $3"
  }

  verifie 0 "tient 2 s"                      "un emballage sain passe"                        tient
  verifie 1 "arrêté seul"                    "un lanceur qui meurt est refusé"                meurt
  # Le cas qui distingue ce contrôle d'un simple « le processus vit-il ? ».
  verifie 1 "erreur de chargement"           "un lanceur VIVANT mais en erreur est refusé"    bavard
  # Le défaut propre à l'emballage, invisible sur l'app-image d'origine.
  verifie 1 "bit exécutable"                 "un lanceur qui a perdu son bit x est refusé"    sans_droit

  sortie=$( "$MOI" "$bac/absent-de-l-archive" --secondes 2 --libelle "essai" 2>&1 ) || code=$?
  if printf '%s' "$sortie" | grep -qF "absent de l'emballage"; then
    echo "  ✔ un lanceur absent de l archive est refusé"
  else
    echo "  ✘ un lanceur absent de l archive est refusé : message inattendu"
    printf '%s\n' "$sortie" | sed 's/^/      /'
    echecs=1
  fi

  # La ligne de commande de l'emballage (#4071) : quatre cas, dont le seul qui compte vraiment - un
  # lanceur qui rend 0 SANS RIEN ÉCRIRE, c'est-à-dire une panne déguisée en succès.
  faux cli_repond 'echo "VigieChiro - compagnon PR (CLI) 9.9.9"'
  faux cli_muet   'exit 0'
  faux cli_casse  'echo "boum" >&2; exit 1'

  verifie_cli() { # <attendu> <fragment attendu> <libellé> <faux lanceur CLI>
    local sortie code=0
    sortie=$( "$MOI" "$bac/tient" --secondes 2 --libelle "essai" --cli "$bac/$4" 2>&1 ) || code=$?
    if [ "$code" != "$1" ]; then
      echo "  ✘ $3 : attendu $1, obtenu $code"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=1
      return
    fi
    if ! printf '%s' "$sortie" | grep -qF "$2"; then
      echo "  ✘ $3 : code correct, mais le message ne dit pas « $2 »"
      printf '%s\n' "$sortie" | sed 's/^/      /'
      echecs=1
      return
    fi
    echo "  ✔ $3"
  }

  verifie_cli 0 "la ligne de commande répond"  "une CLI qui rend sa version passe"          cli_repond
  verifie_cli 1 "n'écrit RIEN"                 "une CLI muette en code 0 est refusée"       cli_muet
  verifie_cli 1 "au lieu de sa version"        "une CLI qui échoue est refusée"             cli_casse
  verifie_cli 1 "n'expose aucun lanceur"       "une CLI absente de l emballage est refusée" cli_jamais_cree

  # Sans `--cli`, le script le DIT au lieu de laisser croire qu'il a tout vérifié.
  sortie=$( "$MOI" "$bac/tient" --secondes 2 --libelle "essai" 2>&1 ) || code=$?
  if printf '%s' "$sortie" | grep -qF "NON vérifiée"; then
    echo "  ✔ sans --cli, l absence de vérification est annoncée"
  else
    echo "  ✘ sans --cli, l absence de vérification devrait être annoncée"
    printf '%s\n' "$sortie" | sed 's/^/      /'
    echecs=1
  fi

  if [ "$echecs" = 0 ]; then
    echo "Auto-test du démarrage des emballages : OK (10 cas, dont 7 rouges vérifiés sur leur message)."
  else
    echo "Auto-test du démarrage des emballages : ÉCHEC - la règle ne fait plus ce qu'elle promet."
  fi
  exit "$echecs"
fi

# ---------------------------------------------------------------------------------------------

LANCEUR="${1:-}"
SECONDES=20
LIBELLE=""
CLI=""
shift || true
while [ $# -gt 0 ]; do
  case "$1" in
    --secondes) SECONDES="$2"; shift ;;
    --libelle)  LIBELLE="$2"; shift ;;
    --cli)      CLI="$2"; shift ;;
    *) echo "option inconnue : $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -z "$LANCEUR" ]; then
  echo "usage : $(basename "$MOI") <lanceur> [--secondes N] [--libelle TEXTE] [--cli <lanceur CLI>]" >&2
  exit 2
fi
[ -n "$LIBELLE" ] || LIBELLE="$(basename "$LANCEUR")"

verifier_le_demarrage "$LANCEUR" "$SECONDES" "$LIBELLE" || exit 1

# ⚠️ `--cli` est FACULTATIF, et son absence se dit : sans elle, ce script ne prouve rien de la ligne de
# commande de l'emballage. Un appelant qui l'oublie doit le voir passer, sans quoi son vert répondrait
# à une question qu'il n'a pas posée (ADR 2748).
if [ -z "$CLI" ]; then
  echo "${LIBELLE} : ligne de commande NON vérifiée (aucun --cli donné)."
  exit 0
fi

verifier_la_ligne_de_commande "$CLI" "$LIBELLE"
