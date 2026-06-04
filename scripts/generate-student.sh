#!/bin/bash
# ============================================================
# Génère la version étudiante du starter à partir de la branche
# solution.
#
# La branche "solution" combine plusieurs marqueurs (détaillés dans
# scripts/lib/student-transforms.sh) :
#
#   // --solution-- ... // --end-solution--     (.java)  bloc SUPPRIMÉ étudiant
#   /* --student-- ... --end-student-- */        (.java)  stub DÉCOMMENTÉ étudiant
#   // --solution-only--                         (.java)  fichier ENTIER supprimé
#   <!-- @@solution@@ --> ... <!-- @@end-solution@@ -->   (.fxml) bloc supprimé
#   <!-- @@student@@ ... @@end-student@@ -->               (.fxml) placeholder décommenté
#   // --masquer-etudiant-- ... // --fin-masquer-etudiant-- (.java) bloc mis entre /* */
#
# Le script :
#   1a. Copie la branche solution dans un répertoire temporaire
#   1b. Supprime les fichiers marqués // --solution-only--
#   1c. Supprime les blocs // --solution-- ... // --end-solution-- (.java)
#   1d. Traite les .fxml (@@solution@@ supprimé, @@student@@ décommenté)
#   1e. Décommente les blocs /* --student-- ... --end-student-- */ (.java)
#   1f. Masque (/* */) les blocs // --masquer-etudiant-- (.java)
#   2.  Ajoute @Disabled aux tests des exercices (mode TP) PUIS aux tests
#       hors paquets de référence (commun, sites) — couvre la SAÉ (e2e, cli,
#       8 features non construites)
#   3.  Lance Spotless (supprime imports inutilisés, reformate)
#   4.  Vérifie que la version étudiante compile
#   5.  Copie le résultat dans le repo cible (si --apply)
#
# Mode par défaut : DRY-RUN (affiche les fichiers qui seraient
# modifiés sans rien écrire). Ajoute --apply pour appliquer.
#
# Usage :
#   ./scripts/generate-student.sh <repo_dir>            # dry-run
#   ./scripts/generate-student.sh --apply <repo_dir>    # applique
#
# Exemples (depuis la racine du dépôt) :
#   ./scripts/generate-student.sh .
#   ./scripts/generate-student.sh --apply .
#
# Prérequis : le dépôt doit avoir une branche "solution" et un
# worktree propre pour --apply. Le script ne modifie jamais la
# branche solution.
# ============================================================

set -euo pipefail

# --- Couleurs (si terminal) ---
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    RED='\033[0;31m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    GREEN='' YELLOW='' RED='' CYAN='' BOLD='' NC=''
fi

info()  { echo -e "${CYAN}▸${NC} $*"; }
ok()    { echo -e "${GREEN}✓${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC} $*"; }
fail()  { echo -e "${RED}✗${NC} $*" >&2; exit 1; }

# --- Bibliothèque de transformations (partagée avec les tests Bats) ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/student-transforms.sh
source "$SCRIPT_DIR/lib/student-transforms.sh"

# Paquets livrés complets (référence) : leurs tests restent actifs côté étudiant.
REFERENCE_PACKAGES=(commun sites)

# Affiche (indentées) les lignes émises par une passe.
show_indent() {
    [ -n "$1" ] && printf '%s\n' "$1" | sed 's/^/  /'
    return 0
}

# Nombre de lignes non vides (fichiers touchés par une passe).
nb_lines() {
    printf '%s' "$1" | grep -c . || true
}

ensure_clean_worktree() {
    local status
    status=$(git -C "$TP_DIR" status --short --untracked-files=all)
    if [ -n "$status" ]; then
        fail "Le worktree de $TP_DIR n'est pas propre. Commit/stash/review manuellement avant --apply."
    fi
}

checkout_main_branch() {
    if git -C "$TP_DIR" checkout main -q 2>/dev/null; then
        return 0
    fi

    if git -C "$TP_DIR" show-ref --verify --quiet refs/remotes/origin/main; then
        git -C "$TP_DIR" checkout -B main origin/main -q
        return 0
    fi

    fail "Impossible de basculer sur la branche main dans $TP_DIR"
}

# --- Arguments ---
APPLY=false
if [ "${1:-}" = "--apply" ]; then
    APPLY=true
    shift
fi

if [ $# -lt 1 ]; then
    echo "Usage: $0 [--apply] <tp_dir>"
    exit 1
fi

TP_DIR="$(cd "$1" && pwd)"

# --- Validation ---
[ -f "$TP_DIR/pom.xml" ] || fail "$TP_DIR ne contient pas de pom.xml"
[ -d "$TP_DIR/.git" ]    || fail "$TP_DIR n'est pas un dépôt git"

git -C "$TP_DIR" rev-parse --verify solution >/dev/null 2>&1 \
    || fail "La branche 'solution' n'existe pas dans $TP_DIR"

if [ "$APPLY" = true ]; then
    ensure_clean_worktree
fi

# --- Répertoire temporaire ---
TMP_DIR=$(mktemp -d)
KEEP_TMP_DIR=false
cleanup() {
    if [ "$KEEP_TMP_DIR" = false ]; then
        rm -rf "$TMP_DIR"
    else
        warn "Répertoire temporaire conservé pour inspection : $TMP_DIR"
    fi
}
trap cleanup EXIT

info "Extraction de la branche solution..."
git -C "$TP_DIR" archive solution | tar -C "$TMP_DIR" -x

# --- Détection du mode refactoring ---
# Si au moins une source contient un bloc /* --student-- ... --end-student-- */,
# on bascule en mode refactoring : l'auto-@Disabled des exercices est skippé (les
# tests de caractérisation doivent rester actifs). La désactivation des tests de
# la SAÉ passe de toute façon par disable_tests_outside_reference (inconditionnel).
REFACTORING_MODE=false
if find "$TMP_DIR/src" -name '*.java' -print0 2>/dev/null \
    | xargs -0 grep -l '/\* --student--' 2>/dev/null | grep -q .; then
    REFACTORING_MODE=true
    info "Mode refactoring détecté (bloc /* --student-- */ présent)"
fi

# --- 1a. Suppression des fichiers marqués // --solution-only-- ---
info "Suppression des fichiers marqués // --solution-only--"
OUT=$(strip_solution_only_files "$TMP_DIR")
show_indent "$OUT"
ok "$(nb_lines "$OUT") fichier(s) entier(s) supprimé(s)"

# --- 1b. Strip des blocs // --solution-- ... // --end-solution-- (.java) ---
info "Suppression des blocs // --solution-- ... // --end-solution--"
OUT=$(strip_solution_blocks "$TMP_DIR")
show_indent "$OUT"
ok "$(nb_lines "$OUT") fichier(s) source strippé(s)"

# --- 1c. Traitement des .fxml (@@solution@@ / @@student@@) ---
info "Traitement des .fxml (@@solution@@ supprimé, @@student@@ décommenté)"
OUT=$(process_fxml_blocks "$TMP_DIR")
show_indent "$OUT"
ok "$(nb_lines "$OUT") fichier(s) FXML transformé(s)"

# --- 1e. Décommentage des blocs /* --student-- ... --end-student-- */ ---
if [ "$REFACTORING_MODE" = "true" ]; then
    info "Décommentage des blocs /* --student-- ... --end-student-- */"
    OUT=$(uncomment_student_blocks "$TMP_DIR")
    show_indent "$OUT"
    ok "$(nb_lines "$OUT") fichier(s) source décommenté(s)"
fi

# --- 1f. Masquage des blocs // --masquer-etudiant-- (mise entre /* */) ---
info "Masquage (/* */) des blocs // --masquer-etudiant--"
OUT=$(mask_student_blocks "$TMP_DIR")
show_indent "$OUT"
ok "$(nb_lines "$OUT") fichier(s) avec bloc(s) masqué(s)"

# --- 2. Ajout de @Disabled aux tests ---
if [ "$REFACTORING_MODE" = "true" ]; then
    info "Mode refactoring : auto-@Disabled des exercices skippé (les tests de caractérisation restent actifs)"
else
    info "Ajout de @Disabled aux tests des exercices (exercice*/bonus*)..."
    OUT=$(disable_exercise_path_tests "$TMP_DIR")
    show_indent "$OUT"
    ok "$(nb_lines "$OUT") fichier(s) de tests d'exercice désactivé(s)"
fi

info "Ajout de @Disabled aux tests hors paquets de référence (${REFERENCE_PACKAGES[*]})..."
OUT=$(disable_tests_outside_reference "$TMP_DIR" "${REFERENCE_PACKAGES[@]}")
show_indent "$OUT"
ok "$(nb_lines "$OUT") fichier(s) de tests non-référence désactivé(s)"

# --- 3. Spotless (supprime imports inutilisés, reformate) ---
info "Exécution de Spotless (formatage + nettoyage imports)..."

if [ -f "$TMP_DIR/mvnw" ]; then
    chmod +x "$TMP_DIR/mvnw" 2>/dev/null || true
fi

# Initialiser un repo git temporaire (le plugin git-build-hook en a besoin)
git -C "$TMP_DIR" init -q 2>/dev/null || true

(cd "$TMP_DIR" && ./mvnw -B -q spotless:apply 2>&1) \
    || warn "Spotless a renvoyé une erreur (non bloquant)"

ok "Spotless terminé"

# --- 4. Vérification compilation ---
info "Vérification de la compilation..."

(cd "$TMP_DIR" && ./mvnw -B -q compile 2>&1) \
    || {
        KEEP_TMP_DIR=true
        fail "La version étudiante ne compile pas."
    }

ok "Compilation réussie"

# --- 5. Vérification rapide : tests skipped ---
info "Vérification que les tests sont bien @Disabled..."

TEST_OUTPUT=$(cd "$TMP_DIR" && ./mvnw -B -q test 2>&1 || true)
SKIPPED=$(echo "$TEST_OUTPUT" | grep -oP 'Skipped: \K[0-9]+' | tail -1 || true)
TOTAL=$(echo "$TEST_OUTPUT" | grep -oP 'Tests run: \K[0-9]+' | tail -1 || true)

if [ -n "$SKIPPED" ] && [ -n "$TOTAL" ]; then
    ok "Tests : $TOTAL exécutés, $SKIPPED skippés"
else
    warn "Impossible de parser le résultat des tests"
fi

# --- 6. Application ou dry-run ---
if [ "$APPLY" = true ]; then
    info "Application des modifications sur $TP_DIR..."

    CURRENT_BRANCH=$(git -C "$TP_DIR" branch --show-current)
    if [ "$CURRENT_BRANCH" != "main" ]; then
        info "Basculement sur la branche main..."
        checkout_main_branch
    fi

    rsync -a --delete \
        --exclude='.git' \
        --exclude='target' \
        --exclude='generate-student.sh' \
        --exclude='.github/workflows/generate-student.yml' \
        --exclude='.github/workflows/template-sync.yml' \
        "$TMP_DIR/" "$TP_DIR/"

    # Les --exclude empêchent la COPIE depuis TMP_DIR, mais préservent
    # aussi les fichiers --exclude'd côté destination (comportement
    # voulu pour .git et target). Pour les artefacts enseignant, on
    # veut explicitement les retirer de la version étudiante générée.
    rm -f "$TP_DIR/generate-student.sh" \
          "$TP_DIR/.github/workflows/generate-student.yml" \
          "$TP_DIR/.github/workflows/template-sync.yml"

    # Le hook pre-commit contient deux blocs enseignant qui n'ont aucun
    # sens côté étudiant :
    #   1. "Protection main" : détection branche solution, crée une fuite
    #      de contexte (mention de generate-student.sh, ../template-tp-java)
    #   2. "@@@TEACHER-LINT-BEGIN@@@ ... @@@TEACHER-LINT-END@@@" : appels
    #      aux linters enseignant (lint-doc-coherence.sh et PMD gate).
    # On strip les deux lors de la génération étudiante.
    if [ -f "$TP_DIR/.githooks/pre-commit" ]; then
        sed -i '/^# Protection : bloquer les commits sur main/,/^fi$/d' \
            "$TP_DIR/.githooks/pre-commit"
        sed -i '/@@@TEACHER-LINT-BEGIN@@@/,/@@@TEACHER-LINT-END@@@/d' \
            "$TP_DIR/.githooks/pre-commit"
    fi

    # Le script scripts/ (outils enseignant : generate-student, lib,
    # lint-doc-coherence) n'a pas d'utilité côté étudiant.
    rm -rf "$TP_DIR/scripts"

    # Le workflow .github/workflows/lint.yml est le gate strict côté
    # branche solution uniquement.
    rm -f "$TP_DIR/.github/workflows/lint.yml"

    ok "Version étudiante générée dans $TP_DIR/"
    echo ""
    echo -e "${BOLD}Prochaines étapes :${NC}"
    echo "  cd $TP_DIR"
    echo "  git diff                    # reviewer les changements"
    echo "  git add -A"
    echo "  ALLOW_MAIN_COMMIT=1 git commit -m \"chore: régénère version étudiante depuis solution\""
else
    echo ""
    echo -e "${BOLD}=== DRY-RUN ===${NC}"
    echo "Aucune modification n'a été appliquée."
    echo ""

    if command -v diff >/dev/null 2>&1; then
        DIFF_OUTPUT=$(diff -rq \
            --exclude='.git' --exclude='target' \
            --exclude='generate-student.sh' \
            --exclude='generate-student.yml' \
            --exclude='template-sync.yml' \
            "$TMP_DIR" "$TP_DIR" 2>/dev/null || true)
        DIFF_COUNT=$(echo "$DIFF_OUTPUT" | grep -c '.' || true)
        echo "$DIFF_COUNT fichier(s) différent(s) entre solution strippée et main actuel."
        echo ""
        echo "$DIFF_OUTPUT"
    fi

    echo ""
    echo "Pour appliquer : $0 --apply $1"
fi
