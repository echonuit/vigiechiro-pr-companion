#!/usr/bin/env bats
# Tests des transformations de génération de la version étudiante.
# Chaque test bâtit un mini-arbre fixture puis exerce une fonction pure de
# scripts/lib/student-transforms.sh et vérifie le résultat. Aucune dépendance
# git/maven : on teste uniquement les transformations de texte.

setup() {
    LIB="${BATS_TEST_DIRNAME}/../lib/student-transforms.sh"
    # shellcheck source=../lib/student-transforms.sh
    source "$LIB"
    ROOT="${BATS_TEST_TMPDIR}/repo"
    mkdir -p "$ROOT/src/main/java" "$ROOT/src/test/java"
}

# --- // --solution-only-- : fichier entier supprimé -------------------------
@test "strip_solution_only_files supprime le fichier marqué, garde les autres" {
    mkdir -p "$ROOT/src/main/java/x"
    printf '// --solution-only--\npackage x;\nclass A {}\n' > "$ROOT/src/main/java/x/A.java"
    printf 'package x;\nclass B {}\n' > "$ROOT/src/main/java/x/B.java"

    run strip_solution_only_files "$ROOT"

    [ ! -f "$ROOT/src/main/java/x/A.java" ]
    [ -f "$ROOT/src/main/java/x/B.java" ]
    [[ "$output" == *"deleted: src/main/java/x/A.java"* ]]
}

# --- // --solution-- ... // --end-solution-- : bloc supprimé ----------------
@test "strip_solution_blocks retire le bloc solution et garde le reste" {
    mkdir -p "$ROOT/src/main/java/x"
    cat > "$ROOT/src/main/java/x/Vm.java" <<'EOF'
package x;
class Vm {
    int f() {
        // --solution--
        return 42;
        // --end-solution--
    }
}
EOF
    strip_solution_blocks "$ROOT"

    run cat "$ROOT/src/main/java/x/Vm.java"
    [[ "$output" != *"return 42;"* ]]
    [[ "$output" != *"--solution--"* ]]
    [[ "$output" == *"int f() {"* ]]
}

# --- VM : --solution-- + /* --student-- */ → stub compilant côté étudiant ---
@test "combo solution+student : code réel retiré, stub décommenté actif" {
    mkdir -p "$ROOT/src/main/java/x"
    cat > "$ROOT/src/main/java/x/Vm.java" <<'EOF'
package x;
class Vm {
    int f() {
        // --solution--
        return 42;
        // --end-solution--
        /* --student--
        throw new UnsupportedOperationException("TODO");
        --end-student-- */
    }
}
EOF
    strip_solution_blocks "$ROOT"
    uncomment_student_blocks "$ROOT"

    run cat "$ROOT/src/main/java/x/Vm.java"
    [[ "$output" != *"return 42;"* ]]
    [[ "$output" == *'throw new UnsupportedOperationException("TODO");'* ]]
    [[ "$output" != *"--student--"* ]]
    [[ "$output" != *"--end-student--"* ]]
}

# --- .fxml : @@solution@@ supprimé, @@student@@ décommenté ------------------
@test "process_fxml_blocks remplace le layout par le placeholder" {
    mkdir -p "$ROOT/src/main/java/x"
    cat > "$ROOT/src/main/java/x/Foo.fxml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>
<?import javafx.scene.control.Label?>
<!-- @@solution@@ -->
<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="x.FooController">
   <Label text="VRAI"/>
</VBox>
<!-- @@end-solution@@ -->
<!-- @@student@@
<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="x.FooController">
   <Label text="TODO a construire"/>
</VBox>
@@end-student@@ -->
EOF
    process_fxml_blocks "$ROOT"

    run cat "$ROOT/src/main/java/x/Foo.fxml"
    [[ "$output" != *"VRAI"* ]]
    [[ "$output" == *"TODO a construire"* ]]
    [[ "$output" != *"@@"* ]]
    [[ "$output" == *'fx:controller="x.FooController"'* ]]
}

# --- // --masquer-etudiant-- : bloc mis entre /* */ ------------------------
@test "mask_student_blocks commente le bloc avec /* */" {
    mkdir -p "$ROOT/src/test/java/x"
    cat > "$ROOT/src/test/java/x/FooTest.java" <<'EOF'
package x;
class FooTest {
    // --masquer-etudiant--
    void casse() { membreSupprime(); }
    // --fin-masquer-etudiant--
}
EOF
    mask_student_blocks "$ROOT"

    run cat "$ROOT/src/test/java/x/FooTest.java"
    [[ "$output" == *"/*"* ]]
    [[ "$output" == *"*/"* ]]
    [[ "$output" != *"--masquer-etudiant--"* ]]
    [[ "$output" != *"--fin-masquer-etudiant--"* ]]
    [[ "$output" == *"void casse()"* ]]
}

# --- @Disabled hors paquets de référence ----------------------------------
@test "disable_tests_outside_reference désactive hors commun/sites" {
    creer_test() {
        local pkg=$1
        mkdir -p "$ROOT/src/test/java/fr/univ_amu/iut/$pkg"
        cat > "$ROOT/src/test/java/fr/univ_amu/iut/$pkg/T.java" <<EOF
package fr.univ_amu.iut.$pkg;
import org.junit.jupiter.api.Test;
class T {
    @Test
    void t() {}
}
EOF
    }
    creer_test commun
    creer_test sites
    creer_test passage
    creer_test e2e

    disable_tests_outside_reference "$ROOT" commun sites

    grep -q '@Disabled' "$ROOT/src/test/java/fr/univ_amu/iut/passage/T.java"
    grep -q '@Disabled' "$ROOT/src/test/java/fr/univ_amu/iut/e2e/T.java"
    grep -q 'import org.junit.jupiter.api.Disabled;' "$ROOT/src/test/java/fr/univ_amu/iut/passage/T.java"
    ! grep -q '@Disabled' "$ROOT/src/test/java/fr/univ_amu/iut/commun/T.java"
    ! grep -q '@Disabled' "$ROOT/src/test/java/fr/univ_amu/iut/sites/T.java"
}

# --- idempotence : ré-exécuter ne double pas les @Disabled -----------------
@test "disable_tests_outside_reference est idempotent" {
    mkdir -p "$ROOT/src/test/java/fr/univ_amu/iut/passage"
    cat > "$ROOT/src/test/java/fr/univ_amu/iut/passage/T.java" <<'EOF'
package fr.univ_amu.iut.passage;
import org.junit.jupiter.api.Test;
class T {
    @Test
    void t() {}
}
EOF
    disable_tests_outside_reference "$ROOT" commun sites
    disable_tests_outside_reference "$ROOT" commun sites

    run grep -c '@Disabled(' "$ROOT/src/test/java/fr/univ_amu/iut/passage/T.java"
    [ "$output" -eq 1 ]
    run grep -c 'import org.junit.jupiter.api.Disabled;' "$ROOT/src/test/java/fr/univ_amu/iut/passage/T.java"
    [ "$output" -eq 1 ]
}
