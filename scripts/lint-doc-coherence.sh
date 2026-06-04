#!/usr/bin/env bash
# ============================================================
# lint-doc-coherence.sh — linter de cohérence documentation/code
# pour les TP R2.03.
#
# Vérifie qu'un TP ne contient pas d'incohérence entre ce qui est
# écrit dans sa doc (README, AGENTS.md, Copilot instructions,
# commentaires de scripts) et ce qui est réellement implémenté
# (tests, marqueurs pédagogiques).
#
# Usage :
#   ./scripts/lint-doc-coherence.sh
#
# Exit codes :
#   0 : aucune incohérence détectée.
#   1 : au moins une incohérence, détails sur stderr.
#
# Ce script est uniquement destiné au pre-commit hook enseignant
# (teacher-block) et à la CI sur branche `solution`. Il est
# automatiquement supprimé côté étudiant par generate-student.sh.
# ============================================================

set -u
# pas de -e : on veut collecter toutes les erreurs avant d'exit.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

ERRORS=0

red()   { printf '\033[31m%s\033[0m\n' "$1"; }
green() { printf '\033[32m%s\033[0m\n' "$1"; }
yellow() { printf '\033[33m%s\033[0m\n' "$1"; }

fail() {
    red "  ✗ $1"
    ERRORS=$((ERRORS + 1))
}

pass() {
    green "  ✓ $1"
}

header() {
    echo
    echo "== $1 =="
}

# ------------------------------------------------------------
# Détection du mode (TDD ou refactoring) - même règle que
# generate-student.sh.
# ------------------------------------------------------------
detect_mode() {
    # Starter SAÉ (A2) : les coquilles FXML à jetons @@solution@@ signent le projet
    # « construis l'IHM », distinct des TP TDD/refactoring. Ses ViewModels utilisent aussi
    # /* --student-- */ pour des stubs compilants, ce qui ne doit PAS le faire passer pour un
    # TP de refactoring (sinon le README serait exigé en vocabulaire « tests de caractérisation »).
    if grep -rq '@@solution@@' src/ 2>/dev/null; then
        echo "sae"
    elif grep -rq '/\* --student--' src/ 2>/dev/null; then
        echo "refactoring"
    else
        echo "tdd"
    fi
}

MODE=$(detect_mode)

header "Mode détecté"
echo "  $MODE"

# ------------------------------------------------------------
# 1. README : formulations incompatibles avec le mode détecté.
#    Les 5 findings corrigés sur tp4 étaient tous de ce type.
# ------------------------------------------------------------
header "README vs mode"

if [ ! -f README.md ]; then
    fail "README.md introuvable."
else
    if [ "$MODE" = "sae" ]; then
        # Starter SAÉ « construis l'IHM » : ni TDD baby-steps ni refactoring de
        # caractérisation. Le README décrit « fourni vs à construire » (validé ailleurs) :
        # la dichotomie tdd/refactoring ne s'applique pas.
        pass "README.md : mode SAÉ, dichotomie tdd/refactoring non applicable."
    elif [ "$MODE" = "refactoring" ]; then
        # Formulations interdites en mode refactoring (héritées de
        # templates TDD et inadaptées à un TP de refactoring).
        BAD_PATTERNS=(
            'seul[^.]*test[^.]*actif[^.]*AppTest'
            'seul[^.]*AppTest[^.]*actif'
            '900 points.*répartis entre'
            '900 points.*répartis entre les'
            'activer le premier test'
            'Vérifier que le test est rouge'
            'pratiquez du TDD strict'
        )
        for pattern in "${BAD_PATTERNS[@]}"; do
            if grep -qEi "$pattern" README.md; then
                line=$(grep -nEi "$pattern" README.md | head -1 | cut -d: -f1)
                fail "README.md:$line contient \"$pattern\" (inadapté en mode refactoring)."
            fi
        done
        # Formulations attendues en mode refactoring.
        if ! grep -qiE 'tests? de caract' README.md; then
            fail "README.md ne mentionne pas les \"tests de caractérisation\" (attendu en mode refactoring)."
        fi
        [ $ERRORS -eq 0 ] && pass "README.md est cohérent avec le mode refactoring."
    else
        # Mode TDD : le README ne doit pas utiliser le vocabulaire
        # refactoring s'il n'y en a pas.
        if grep -qiE 'tests? de caract[éè]risation' README.md; then
            line=$(grep -niE 'tests? de caract[éè]risation' README.md | head -1 | cut -d: -f1)
            fail "README.md:$line mentionne des \"tests de caractérisation\" mais aucun marqueur refactoring détecté dans src/."
        fi
        [ $ERRORS -eq 0 ] && pass "README.md est cohérent avec le mode TDD."
    fi
fi

# ------------------------------------------------------------
# 3. AGENTS.md et .github/copilot-instructions.md — sync des
#    blocs TDD-PLAYBOOK et REFACTORING-MODE.
# ------------------------------------------------------------
header "Sync AGENTS.md ↔ .github/copilot-instructions.md"

check_block_sync() {
    local block_name=$1
    local start_marker="<!-- ${block_name}-START -->"
    local end_marker="<!-- ${block_name}-END -->"

    local agents_file="AGENTS.md"
    local copilot_file=".github/copilot-instructions.md"

    if [ ! -f "$agents_file" ] || [ ! -f "$copilot_file" ]; then
        return
    fi

    local agents_has_block
    local copilot_has_block
    agents_has_block=$(grep -c "$start_marker" "$agents_file" 2>/dev/null || echo 0)
    copilot_has_block=$(grep -c "$start_marker" "$copilot_file" 2>/dev/null || echo 0)

    if [ "$agents_has_block" = "0" ] && [ "$copilot_has_block" = "0" ]; then
        return
    fi
    if [ "$agents_has_block" != "$copilot_has_block" ]; then
        fail "Bloc $block_name présent dans un seul des deux fichiers (AGENTS=$agents_has_block, copilot=$copilot_has_block)."
        return
    fi

    local agents_block
    local copilot_block
    agents_block=$(sed -n "/$start_marker/,/$end_marker/p" "$agents_file")
    copilot_block=$(sed -n "/$start_marker/,/$end_marker/p" "$copilot_file")

    if ! diff -q <(echo "$agents_block") <(echo "$copilot_block") >/dev/null 2>&1; then
        fail "Bloc $block_name diverge entre AGENTS.md et .github/copilot-instructions.md."
        diff <(echo "$agents_block") <(echo "$copilot_block") | head -20 >&2
    else
        pass "Bloc $block_name synchronisé."
    fi
}

check_block_sync "TDD-PLAYBOOK"
check_block_sync "REFACTORING-MODE"

# ------------------------------------------------------------
# 4. Marqueurs pédagogiques balancés dans les .java.
# ------------------------------------------------------------
header "Marqueurs pédagogiques"

find src -name '*.java' 2>/dev/null | while read -r f; do
    # --solution-- / --end-solution--
    open=$(grep -c '// --solution--' "$f")
    close=$(grep -c '// --end-solution--' "$f")
    if [ "$open" != "$close" ]; then
        echo "FAIL:$f:--solution-- ($open open / $close close)"
    fi

    # --student-- / --end-student--
    # Utiliser awk pour exclure les mentions intra-commentaire où les
    # deux marqueurs apparaissent sur la même ligne (ex : "utilisés
    # dans les blocs /* --student-- */"). Un VRAI marqueur d'ouverture
    # n'a pas de */ sur la même ligne ; un vrai marqueur de fermeture
    # n'a pas de /* avant sur la même ligne.
    s_open=$(awk '/\/\* --student--/ && !/\*\//  {c++} END {print c+0}' "$f")
    s_close=$(awk '/--end-student-- \*\//       {c++} END {print c+0}' "$f")
    if [ "$s_open" != "$s_close" ]; then
        echo "FAIL:$f:--student-- ($s_open open / $s_close close)"
    fi

    # --solution-only-- (doit être dans les 20 premières lignes si présent)
    if grep -q '// --solution-only--' "$f"; then
        if ! head -20 "$f" | grep -q '// --solution-only--'; then
            echo "FAIL:$f:--solution-only-- présent mais hors des 20 premières lignes"
        fi
    fi
done > /tmp/lint-markers.$$.log 2>/dev/null

if [ -s /tmp/lint-markers.$$.log ]; then
    while IFS= read -r line; do
        # line format: FAIL:<file>:<msg>
        fail "${line#FAIL:}"
    done < /tmp/lint-markers.$$.log
else
    pass "Tous les marqueurs sont balancés."
fi
rm -f /tmp/lint-markers.$$.log

# ------------------------------------------------------------
# Bilan
# ------------------------------------------------------------
echo
if [ "$ERRORS" -eq 0 ]; then
    green "✓ Aucune incohérence détectée."
    exit 0
else
    red "✗ $ERRORS incohérence(s) détectée(s)."
    exit 1
fi
