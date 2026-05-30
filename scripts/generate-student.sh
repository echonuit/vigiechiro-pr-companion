#!/bin/bash
# ============================================================
# Génère la version étudiante d'un TP à partir de la branche
# solution.
#
# La branche "solution" peut combiner trois marqueurs :
#
#   // --solution--                       (bloc à SUPPRIMER côté étudiant)
#   code propre que l'étudiant doit produire
#   // --end-solution--
#
#   /* --student--                        (bloc à ACTIVER côté étudiant)
#   code de départ commenté inerte côté solution,
#   décommenté côté étudiant
#   --end-student-- */
#
#   // --solution-only--                  (en en-tête de fichier, 20 premières lignes)
#   classe entière qui n'existe que côté solution
#   (le fichier est supprimé côté étudiant)
#
# Le script :
#   1a. Copie la branche solution dans un répertoire temporaire
#   1b. Supprime les fichiers marqués // --solution-only--
#   1c. Supprime les blocs // --solution-- ... // --end-solution--
#   1d. Décommente les blocs /* --student-- ... --end-student-- */
#   2.  Ajoute @Disabled aux tests des exercices
#   3.  Lance Spotless (supprime imports inutilisés, reformate)
#   4.  Vérifie que la version étudiante compile
#   5.  Copie le résultat dans le TP cible (si --apply)
#
# Mode par défaut : DRY-RUN (affiche les fichiers qui seraient
# modifiés sans rien écrire). Ajoute --apply pour appliquer.
#
# Usage :
#   ./generate-student.sh <tp_dir>                # dry-run
#   ./generate-student.sh --apply <tp_dir>        # applique
#
# Exemples :
#   ./generate-student.sh ../tp1
#   ./generate-student.sh --apply ../tp1
#
# Prérequis : le TP doit avoir une branche "solution" et un
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

ensure_clean_worktree() {
    local status
    status=$(git -C "$TP_DIR" status --short --untracked-files=all)
    if [ -n "$status" ]; then
        fail "Le worktree de $TP_DIR n'est pas propre. Commit/stash/review manuellement avant --apply."
    fi
}

insert_disabled_import() {
    local file=$1
    local insert_after

    if grep -q '^import org\.junit\.jupiter\.api\.Disabled;$' "$file"; then
        return 0
    fi

    insert_after=$(grep -n '^import org\.junit\.jupiter\.api\.' "$file" | tail -1 | cut -d: -f1 || true)
    if [ -z "$insert_after" ]; then
        insert_after=$(grep -n '^import ' "$file" | head -1 | cut -d: -f1 || true)
    fi
    if [ -z "$insert_after" ]; then
        insert_after=$(grep -n '^package ' "$file" | head -1 | cut -d: -f1 || true)
    fi
    [ -n "$insert_after" ] || fail "Impossible d'insérer l'import Disabled dans $file"

    awk -v line_no="$insert_after" '
    NR == line_no {
        print
        print "import org.junit.jupiter.api.Disabled;"
        next
    }
    { print }
    ' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
}

disable_exercise_tests_in_file() {
    local file=$1

    awk -v disabled="$DISABLED_TEXT" '
    function flush_annotations(    i, has_test, has_disabled, indent) {
        if (annotation_count == 0) {
            return
        }

        has_test = 0
        has_disabled = 0
        for (i = 1; i <= annotation_count; i++) {
            if (annotation_lines[i] ~ /^[[:space:]]*@Disabled(\(|$)/) {
                has_disabled = 1
            }
            if (annotation_lines[i] ~ /^[[:space:]]*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)(\(|[[:space:]]|$)/) {
                has_test = 1
            }
        }

        if (has_test && !has_disabled) {
            match(annotation_lines[1], /^[[:space:]]*/)
            indent = substr(annotation_lines[1], RSTART, RLENGTH)
            print indent "@Disabled(\"" disabled "\")"
        }

        for (i = 1; i <= annotation_count; i++) {
            print annotation_lines[i]
            delete annotation_lines[i]
        }
        annotation_count = 0
    }

    /^[[:space:]]*@/ {
        annotation_lines[++annotation_count] = $0
        next
    }

    {
        flush_annotations()
        print $0
    }

    END {
        flush_annotations()
    }
    ' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"

    if grep -q '@Disabled("' "$file"; then
        insert_disabled_import "$file"
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

DISABLED_TEXT='Retire cette annotation pour activer le test'

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
# Si au moins une source ou test contient un bloc /* --student-- ...
# --end-student-- */, on bascule en mode refactoring : smelly et refactored
# cohabitent dans la meme source, l'auto-@Disabled est skippe (les tests
# de caracterisation doivent rester actifs cote etudiant). La detection
# scanne tout src/, y compris le launcher App.java.
REFACTORING_MODE=false
if find "$TMP_DIR/src" -name '*.java' -print0 2>/dev/null \
    | xargs -0 grep -l '/\* --student--' 2>/dev/null | grep -q .; then
    REFACTORING_MODE=true
    info "Mode refactoring detecte (bloc /* --student-- */ present)"
fi

# --- 1a. Suppression des fichiers marques // --solution-only-- ---
# Les fichiers qui contiennent le marqueur // --solution-only-- sur une
# ligne sont entierement absents de la version etudiante. Typique pour
# une classe extraite par Extract Class : elle est presente cote
# enseignant comme reference, absente cote etudiant·e qui doit la creer.
info "Suppression des fichiers marques // --solution-only--"
DELETED=0
while IFS= read -r -d '' file; do
    if head -20 "$file" | grep -q '// --solution-only--'; then
        echo "  deleted: ${file#"$TMP_DIR"/}"
        rm -f "$file"
        DELETED=$((DELETED + 1))
    fi
done < <(find "$TMP_DIR/src" -name '*.java' -print0 2>/dev/null)
ok "$DELETED fichier(s) entier(s) supprime(s)"

# --- 1b. Strip des blocs solution (source) ---
info "Suppression des blocs // --solution-- ... // --end-solution--"

STRIPPED=0
while IFS= read -r -d '' file; do
    if grep -q '// --solution--' "$file"; then
        sed -i '/\/\/ --solution--/,/\/\/ --end-solution--/d' "$file"
        STRIPPED=$((STRIPPED + 1))
        echo "  stripped: ${file#"$TMP_DIR"/}"
    fi
done < <(find "$TMP_DIR/src" -name '*.java' -print0 2>/dev/null)

ok "$STRIPPED fichier(s) source strippe(s)"

# --- 1bis. Decommentage des blocs /* --student-- ... --end-student-- */ ---
# Sur la branche solution ces blocs sont un commentaire Java inerte (ils
# portent la version smelly du code, cote-a-cote avec la version
# refactorisee). On retire juste les delimiteurs pour rendre le bloc
# actif cote etudiant.
if [ "$REFACTORING_MODE" = "true" ]; then
    info "Decommentage des blocs /* --student-- ... --end-student-- */"
    UNCOMMENTED=0
    while IFS= read -r -d '' file; do
        if grep -q '/\* --student--' "$file"; then
            sed -i '/\/\* --student--/d; /--end-student-- \*\//d' "$file"
            UNCOMMENTED=$((UNCOMMENTED + 1))
            echo "  uncommented: ${file#"$TMP_DIR"/}"
        fi
    done < <(find "$TMP_DIR/src" -name '*.java' -print0 2>/dev/null)
    ok "$UNCOMMENTED fichier(s) source decommente(s)"
fi

# --- 2. Ajout de @Disabled aux tests ---
if [ "$REFACTORING_MODE" = "true" ]; then
    info "Mode refactoring : auto-@Disabled des tests skippe (les caracterisation tests restent actifs)"
else
    info "Ajout de @Disabled aux tests des exercices..."

    DISABLED_COUNT=0
    while IFS= read -r -d '' file; do
        disable_exercise_tests_in_file "$file"

        ADDED=$(grep -c "@Disabled" "$file" || true)
        if [ "$ADDED" -gt 0 ]; then
            DISABLED_COUNT=$((DISABLED_COUNT + ADDED))
            echo "  @Disabled: ${file#"$TMP_DIR"/} ($ADDED tests)"
        fi
    done < <(find "$TMP_DIR/src/test/java" \( -path '*/exercice*/*.java' -o -path '*/bonus*/*.java' \) -print0 2>/dev/null)

    ok "$DISABLED_COUNT annotation(s) @Disabled ajoutee(s)"
fi

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
        --exclude='update-autograding.sh' \
        --exclude='.github/workflows/generate-student.yml' \
        --exclude='.github/workflows/template-sync.yml' \
        "$TMP_DIR/" "$TP_DIR/"

    # Les --exclude empechent la COPIE depuis TMP_DIR, mais preservent
    # aussi les fichiers --exclude'd cote destination (comportement
    # voulu pour .git et target). Pour les artefacts enseignant, on
    # veut explicitement les retirer de la version etudiante generee.
    rm -f "$TP_DIR/generate-student.sh" \
          "$TP_DIR/update-autograding.sh" \
          "$TP_DIR/.github/workflows/generate-student.yml" \
          "$TP_DIR/.github/workflows/template-sync.yml"

    # Le hook pre-commit contient deux blocs enseignant qui n'ont aucun
    # sens cote etudiant :
    #   1. "Protection main" : detection branche solution, cree une fuite
    #      de contexte (mention de generate-student.sh, ../template-tp-java)
    #   2. "@@@TEACHER-LINT-BEGIN@@@ ... @@@TEACHER-LINT-END@@@" : appels
    #      aux linters enseignant (lint-doc-coherence.sh et PMD gate).
    # On strip les deux lors de la generation etudiante.
    if [ -f "$TP_DIR/.githooks/pre-commit" ]; then
        sed -i '/^# Protection : bloquer les commits sur main/,/^fi$/d' \
            "$TP_DIR/.githooks/pre-commit"
        sed -i '/@@@TEACHER-LINT-BEGIN@@@/,/@@@TEACHER-LINT-END@@@/d' \
            "$TP_DIR/.githooks/pre-commit"
    fi

    # Le script scripts/lint-doc-coherence.sh est un outil enseignant ;
    # pas d'utilite cote etudiant.
    rm -rf "$TP_DIR/scripts"

    # Le workflow .github/workflows/lint.yml est le gate strict cote
    # branche solution uniquement. Son chemin serait inutile cote main
    # etudiant meme si le filtre sur `on:` aurait suffi.
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
            --exclude='update-autograding.sh' \
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
