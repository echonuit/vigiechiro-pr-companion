#!/bin/bash
# ============================================================
# Transformations pures de génération de la version étudiante.
#
# Chaque fonction opère sur un répertoire (l'arbre extrait de la
# branche solution) et n'a AUCUNE dépendance git/maven : elles sont
# partagées par scripts/generate-student.sh (orchestration) et par la
# suite Bats scripts/test/student-transforms.bats (vérification).
#
# Marqueurs reconnus (sur la branche solution) :
#
#   // --solution--                 (.java)   bloc supprimé côté étudiant
#   // --end-solution--
#
#   /* --student--                  (.java)   commentaire inerte côté solution,
#   stub décommenté côté étudiant             décommenté (activé) côté étudiant
#   --end-student-- */
#
#   // --solution-only--            (.java, 20 premières lignes)
#                                             fichier entier supprimé côté étudiant
#
#   <!-- @@solution@@ -->           (.fxml)   bloc supprimé côté étudiant
#   <!-- @@end-solution@@ -->                 (jetons XML-safe : un commentaire
#   <!-- @@student@@                          XML ne peut pas contenir « -- »)
#   placeholder décommenté côté étudiant
#   @@end-student@@ -->
#
#   // --masquer-etudiant--         (.java)   code actif côté solution, mis entre
#   // --fin-masquer-etudiant--                /* */ côté étudiant (réactivable) :
#                                             pour les tests qui ne compileraient
#                                             plus une fois l'IHM strippée.
# ============================================================

# Texte de l'annotation @Disabled posée sur les tests neutralisés.
: "${DISABLED_TEXT:=Retire cette annotation pour activer le test}"

# --- 1a. Suppression des fichiers .java marqués // --solution-only-- ---
# Les fichiers portant ce marqueur dans leurs 20 premières lignes sont
# entièrement absents de la version étudiante. Émet une ligne par fichier.
strip_solution_only_files() {
    local root=$1 file
    while IFS= read -r -d '' file; do
        if head -20 "$file" | grep -q '// --solution-only--'; then
            rm -f "$file"
            echo "deleted: ${file#"$root"/}"
        fi
    done < <(find "$root/src" -name '*.java' -print0 2>/dev/null)
}

# --- 1b. Strip des blocs // --solution-- ... // --end-solution-- (.java) ---
strip_solution_blocks() {
    local root=$1 file
    while IFS= read -r -d '' file; do
        if grep -q '// --solution--' "$file"; then
            sed -i '/\/\/ --solution--/,/\/\/ --end-solution--/d' "$file"
            echo "stripped: ${file#"$root"/}"
        fi
    done < <(find "$root/src" -name '*.java' -print0 2>/dev/null)
}

# --- 1c. Traitement des .fxml : strip @@solution@@ + décommentage @@student@@ ---
# Mêmes deux gestes que pour le Java, mais avec des jetons XML-safe (un
# commentaire XML ne peut pas contenir « -- », donc pas de --solution--).
process_fxml_blocks() {
    local root=$1 file touched
    while IFS= read -r -d '' file; do
        touched=0
        if grep -q '@@solution@@' "$file"; then
            sed -i '/<!-- @@solution@@ -->/,/<!-- @@end-solution@@ -->/d' "$file"
            touched=1
        fi
        if grep -q '@@student@@' "$file"; then
            sed -i '/<!-- @@student@@/d; /@@end-student@@ -->/d' "$file"
            touched=1
        fi
        [ "$touched" = 1 ] && echo "fxml: ${file#"$root"/}"
    done < <(find "$root/src" -name '*.fxml' -print0 2>/dev/null)
    return 0
}

# --- 1d. Décommentage des blocs /* --student-- ... --end-student-- */ (.java) ---
# Sur la branche solution ces blocs sont un commentaire Java inerte (la version
# « stub » côté à côté avec l'implémentation). On retire les délimiteurs pour
# rendre le stub actif côté étudiant.
uncomment_student_blocks() {
    local root=$1 file
    while IFS= read -r -d '' file; do
        if grep -q '/\* --student--' "$file"; then
            sed -i '/\/\* --student--/d; /--end-student-- \*\//d' "$file"
            echo "uncommented: ${file#"$root"/}"
        fi
    done < <(find "$root/src" -name '*.java' -print0 2>/dev/null)
}

# --- 1e. Masquage des blocs // --masquer-etudiant-- ... // --fin-masquer-etudiant-- ---
# Le délimiteur d'ouverture devient /* et celui de fermeture */ : le bloc (actif
# et compilant côté solution) est commenté côté étudiant, réactivable d'un geste.
# Réservé aux tests qui ne compileraient plus une fois l'IHM d'une feature strippée.
mask_student_blocks() {
    local root=$1 file
    while IFS= read -r -d '' file; do
        if grep -q '// --masquer-etudiant--' "$file"; then
            sed -i 's#// --masquer-etudiant--#/*#; s#// --fin-masquer-etudiant--#*/#' "$file"
            echo "masked: ${file#"$root"/}"
        fi
    done < <(find "$root/src" -name '*.java' -print0 2>/dev/null)
}

# --- Insertion idempotente de l'import org.junit.jupiter.api.Disabled ---
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
    [ -n "$insert_after" ] || { echo "Impossible d'insérer l'import Disabled dans $file" >&2; return 1; }

    awk -v line_no="$insert_after" '
    NR == line_no {
        print
        print "import org.junit.jupiter.api.Disabled;"
        next
    }
    { print }
    ' "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
}

# --- Ajoute @Disabled aux groupes d'annotations portant @Test (un fichier) ---
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

# --- 2. @Disabled des tests sous exercice*/bonus* (mode TP historique) ---
disable_exercise_path_tests() {
    local root=$1 file
    while IFS= read -r -d '' file; do
        disable_exercise_tests_in_file "$file"
        local added
        added=$(grep -c "@Disabled" "$file" || true)
        [ "$added" -gt 0 ] && echo "@Disabled: ${file#"$root"/} ($added)"
    done < <(find "$root/src/test/java" \( -path '*/exercice*/*.java' -o -path '*/bonus*/*.java' \) -print0 2>/dev/null)
    return 0
}

# --- 2bis. @Disabled des tests HORS paquets de référence ---
# Tout @Test d'un fichier de test dont le paquet n'est pas l'un des paquets de
# référence passés en argument (ex. commun, sites) reçoit @Disabled. Couvre
# aussi e2e, cli, etc. Exécutée inconditionnellement (sans rapport avec le mode
# refactoring). Le chemin testé est relatif à fr/univ_amu/iut/<paquet>/.
disable_tests_outside_reference() {
    local root=$1
    shift
    local refs=("$@") file rel r skip
    while IFS= read -r -d '' file; do
        rel=${file#"$root"/}
        skip=0
        for r in "${refs[@]}"; do
            case "$rel" in */iut/"$r"/*) skip=1; break ;; esac
        done
        [ "$skip" = 1 ] && continue
        disable_exercise_tests_in_file "$file"
        local added
        added=$(grep -c "@Disabled" "$file" || true)
        [ "$added" -gt 0 ] && echo "@Disabled: ${rel} ($added)"
    done < <(find "$root/src/test/java" -name '*.java' -print0 2>/dev/null)
    return 0
}
