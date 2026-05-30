#!/bin/bash
# ============================================================
# Régénère la section AUTOGRADING de .github/workflows/classroom.yml
# en scannant les paquets d'exercices dans src/test/java/fr/univ_amu/iut/
#
# Utilise les actions GitHub Classroom modernes (forkées et maintenues
# côté R202, réutilisées par R203 - infrastructure partagée) :
#   - IUTInfoAix-R202/autograding-command-grader@main
#   - IUTInfoAix-R202/autograding-grading-reporter@main
#
# Granularité : un step de grading PAR MÉTHODE de test (et non par
# exercice). Cela permet une vraie note proportionnelle :
# si 1 test sur 3 d'un exercice passe, l'élève reçoit 1/3 des points
# de cet exercice.
#
# La commande de grading appelle le wrapper ./grade-test.sh qui
# exige que le test ait RÉELLEMENT tourné (pas @Disabled) ET passé.
# Sans ce wrapper, un TP vide aurait 100/100 car ./mvnw test sur un
# test @Disabled exit 0 (Skipped, pas Failed).
#
# Convention : un sous-paquet `exerciceN` = un exercice.
#
# Deux modes de répartition, détectés automatiquement :
#
# 1. Mode TDD (défaut) - aucun marqueur /* --student-- */ dans src/.
#    Répartition sur un total de 1000 :
#      - 100 pts compilation
#      - 900 pts équirépartis entre les exercices détectés (les
#        $ex_remainder premiers exercices prennent +1 pt pour
#        absorber le reste, pas de "winner-takes-all").
#      - À l'intérieur d'un exercice, les points sont équirépartis
#        entre ses méthodes de test (même règle de remainder).
#      Le total sur 1000 offre assez de granularité pour qu'aucun
#      test ne vale 0 pt, même sur les exercices à forte cardinalité.
#
# 2. Mode refactoring - au moins un marqueur /* --student-- */
#    présent dans src/ (générateur generate-student.sh le détecte
#    de la même façon). Répartition 10/10/80 sur un total de 1000 :
#      - 100 pts compilation (COMPILE_POINTS)
#      - 100 pts répartis entre TOUS les tests de caractérisation
#        détectés dans le projet (CARACT_POINTS, filet de sécurité).
#      - 800 pts répartis entre TOUS les tests de structure détectés
#        (STRUCTURE_POINTS, débloqués après refactoring).
#      Cette répartition garantit qu'un projet qui compile avec
#      juste les caractérisations vertes plafonne à 200/1000
#      (= 4/20). Les points viennent du refactoring effectivement
#      réalisé, pas du simple fait que le code smelly fonctionne.
#      Un test est classé "structure" s'il porte `@Disabled`
#      (directement ou wrappé dans un bloc
#      /* --student-- @Disabled --end-student-- */ côté solution),
#      sinon il est classé "caract" - cf. extract_test_methods_classified
#      plus bas. En mode refactoring, la convention est donc :
#      caract = actif au départ, structure = à débloquer.
#
# Le reporter GitHub Classroom affiche le score brut (ex :
# "Points 250/1000"). Les READMEs doivent donc documenter la base
# 1000 explicitement, ou la présenter en pourcentage (25 %).
#
# Usage: ./update-autograding.sh
# ============================================================

set -e

TEST_ROOT="src/test/java/fr/univ_amu/iut"
CLASSROOM_YML=".github/workflows/classroom.yml"
COMPILE_POINTS=100
TOTAL_EXERCISE_POINTS=900

# En mode refactoring (detecte automatiquement, voir plus bas) :
#   - CARACT_POINTS : total des points repartis entre les tests de
#     caracterisation (ceux qui passent DES LE DEBUT sur le code smelly).
#   - STRUCTURE_POINTS : total des points repartis entre les tests de
#     structure (ceux wrappes dans /* --student-- @Disabled --end-student-- */
#     et qui ne passent qu'APRES le refactoring demande).
# 100 + 800 = 900 (= TOTAL_EXERCISE_POINTS). Le 10/80 est un choix
# pedagogique : les tests de caracterisation ne sont qu'un filet de
# securite, 80% de la note vient du refactoring effectivement realise.
CARACT_POINTS=100
STRUCTURE_POINTS=800

TIMEOUT_MINUTES=5
TEST_PACKAGE_PREFIX="fr.univ_amu.iut"

START_MARKER="#@@@AUTOGRADING-BEGIN@@@"
END_MARKER="#@@@AUTOGRADING-END@@@"

# Le script vit dans scripts/ ; on remonte d'un niveau pour travailler à
# la racine du TP (où sont pom.xml et .github/workflows/classroom.yml).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

if [ ! -f "$CLASSROOM_YML" ]; then
    echo "ERREUR: $CLASSROOM_YML introuvable." >&2
    exit 1
fi

# Helper : extrait les noms de méthodes @Test d'un fichier de test.
# On ne prend que les méthodes annotées @Test (pas @BeforeEach, @Start,
# ni utilitaires void). L'annotation peut être sur la ligne au-dessus
# ou sur la même ligne que la signature.
extract_test_methods() {
    local file=$1
    awk '
        /@Test([^a-zA-Z0-9_]|$)/ { pending = 1; next }
        pending && /void[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*[[:space:]]*\(/ {
            match($0, /void[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*/)
            name = substr($0, RSTART, RLENGTH)
            sub(/^void[[:space:]]+/, "", name)
            print name
            pending = 0
            next
        }
        pending && /^[[:space:]]*$/ { next }
        pending && /^[[:space:]]*@/ { next }
        { pending = 0 }
    ' "$file" 2>/dev/null || true
}

# Helper : extrait les methodes @Test classifiees en "caract" ou "structure"
# pour les TP de refactoring. Un test est STRUCTURE s'il a une annotation
# @Disabled soit directement au-dessus, soit dans un bloc /* --student--
# @Disabled --end-student-- */ (cote solution). Sinon il est CARACT.
extract_test_methods_classified() {
    local file=$1
    awk '
        /\/\* --student--/ {
            in_student = 1
            block_has_disabled = 0
            next
        }
        /--end-student-- \*\// {
            if (block_has_disabled) pending_disabled = 1
            in_student = 0
            next
        }
        in_student {
            if (/@Disabled/) block_has_disabled = 1
            next
        }
        /^[[:space:]]*@Disabled/ {
            pending_disabled = 1
            next
        }
        /@Test([^a-zA-Z0-9_]|$)/ { pending_test = 1; next }
        pending_test && /void[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*[[:space:]]*\(/ {
            match($0, /void[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*/)
            name = substr($0, RSTART, RLENGTH)
            sub(/^void[[:space:]]+/, "", name)
            classification = pending_disabled ? "structure" : "caract"
            print name "|" classification
            pending_test = 0
            pending_disabled = 0
            next
        }
        pending_test && /^[[:space:]]*$/ { next }
        pending_test && /^[[:space:]]*@/ { next }
        { pending_test = 0; pending_disabled = 0 }
    ' "$file" 2>/dev/null || true
}

# --- Découverte des exercices ---
exercises=()
if [ -d "$TEST_ROOT" ]; then
    while IFS= read -r dir; do
        exercises+=("$(basename "$dir")")
    done < <(find "$TEST_ROOT" -mindepth 1 -maxdepth 1 -type d -name 'exercice*' | sort -V)
fi

num_ex=${#exercises[@]}
echo "Exercices détectés : $num_ex"

# --- Detection du mode refactoring ---
# Si au moins une source de test contient un bloc /* --student-- ...
# --end-student-- */, on bascule sur le schema 10/10/80 (compile / caract
# / structure). Sinon on reste sur l'equi-repartition TDD classique.
REFACTORING_MODE=false
if find "$TEST_ROOT" -name '*.java' -print0 2>/dev/null \
    | xargs -0 grep -l '/\* --student--' 2>/dev/null | grep -q .; then
    REFACTORING_MODE=true
    echo "Mode refactoring détecté : schéma 10/10/80"
fi

# --- Répartition des points entre exercices ---
if [ "$num_ex" -eq 0 ]; then
    compile_points=1000
else
    compile_points=$COMPILE_POINTS
    ex_base=$(( TOTAL_EXERCISE_POINTS / num_ex ))
    ex_remainder=$(( TOTAL_EXERCISE_POINTS - ex_base * num_ex ))
fi

# --- En mode refactoring : pre-compte des tests par categorie ---
if [ "$REFACTORING_MODE" = "true" ]; then
    total_caract=0
    total_structure=0
    for ex_name in "${exercises[@]}"; do
        ex_dir="$TEST_ROOT/$ex_name"
        while IFS= read -r f; do
            while IFS= read -r line; do
                [ -z "$line" ] && continue
                case "$line" in
                    *\|caract) total_caract=$((total_caract + 1)) ;;
                    *\|structure) total_structure=$((total_structure + 1)) ;;
                esac
            done < <(extract_test_methods_classified "$f")
        done < <(find "$ex_dir" -type f -name '*.java' 2>/dev/null | sort)
    done

    if [ "$total_caract" -gt 0 ]; then
        caract_base=$(( CARACT_POINTS / total_caract ))
        caract_remainder=$(( CARACT_POINTS - caract_base * total_caract ))
    else
        caract_base=0
        caract_remainder=0
    fi
    if [ "$total_structure" -gt 0 ]; then
        structure_base=$(( STRUCTURE_POINTS / total_structure ))
        structure_remainder=$(( STRUCTURE_POINTS - structure_base * total_structure ))
    else
        structure_base=0
        structure_remainder=0
    fi
    caract_idx=0
    structure_idx=0
    echo "  Caractérisation : $total_caract tests ($CARACT_POINTS pts)"
    echo "  Structure       : $total_structure tests ($STRUCTURE_POINTS pts)"
fi

# --- Génération du bloc YAML ---
block=$(mktemp)
trap 'rm -f "$block"' EXIT

{
    echo "      ${START_MARKER} (bloc auto-généré par l'outillage enseignant, ne pas éditer à la main)"
    echo "      - name: Compilation"
    echo "        id: compilation"
    echo "        uses: IUTInfoAix-R202/autograding-command-grader@main"
    echo "        with:"
    echo "          test-name: Compilation"
    echo "          setup-command: \"\""
    echo "          command: ./mvnw -B -q compile"
    echo "          timeout: ${TIMEOUT_MINUTES}"
    echo "          max-score: ${compile_points}"
    echo ""
    # --- Cache des rapports Surefire entre runs ---
    # Sans cache + scope, on devrait relancer un \`./mvnw test\` par méthode à
    # chaque push, soit ~15 min d'Actions par push. Avec cette mécanique, un
    # push qui ne touche qu'un exercice ne lance que les tests de cet exercice ;
    # les rapports XML des autres sont restaurés depuis le cache et lus tels
    # quels par grade-test.sh.
    echo "      - name: Restaurer le cache des rapports Surefire"
    echo "        id: surefire_cache"
    echo "        uses: actions/cache/restore@v5"
    echo "        with:"
    echo "          path: target/surefire-reports"
    echo "          key: surefire-\${{ github.ref_name }}-\${{ github.sha }}"
    echo "          restore-keys: |"
    echo "            surefire-\${{ github.ref_name }}-"
    echo "            surefire-main-"
    echo ""
    # --- Détection des exercices impactés par le push ---
    echo "      - name: Détecter les exercices impactés"
    echo "        id: scope"
    echo "        run: |"
    echo "          set +e"
    echo "          BEFORE=\"\${{ github.event.before }}\""
    echo "          # Cache vide (premier run, expiration GHA) -> regrade complet sinon les"
    echo "          # XMLs absents donneraient un score 0 à tout le monde."
    echo "          if [ ! -d target/surefire-reports ] || [ -z \"\$(ls -A target/surefire-reports 2>/dev/null)\" ]; then"
    echo "            echo \"filter=ALL\" >> \"\$GITHUB_OUTPUT\""
    echo "            echo \"reason=cache des rapports absent ou expiré\" >> \"\$GITHUB_OUTPUT\""
    echo "            exit 0"
    echo "          fi"
    echo "          # Premier push sur la branche, ou commit base introuvable -> tout regrader"
    echo "          if [ -z \"\$BEFORE\" ] || [ \"\$BEFORE\" = \"0000000000000000000000000000000000000000\" ] || ! git cat-file -e \"\$BEFORE^{commit}\" 2>/dev/null; then"
    echo "            echo \"filter=ALL\" >> \"\$GITHUB_OUTPUT\""
    echo "            echo \"reason=premier push sur la branche ou commit base introuvable\" >> \"\$GITHUB_OUTPUT\""
    echo "            exit 0"
    echo "          fi"
    echo "          # Fichiers globaux : invalident le cache (regrade complet)"
    echo "          if git diff --name-only \"\$BEFORE\" HEAD -- \\"
    echo "              pom.xml \\"
    echo "              '.github/workflows/**' \\"
    echo "              '*.sh' \\"
    echo "              'src/main/java/fr/univ_amu/iut/App.java' \\"
    echo "              'src/main/java/fr/univ_amu/iut/module-info.java' \\"
    echo "              'src/test/java/fr/univ_amu/iut/AppTest.java' \\"
    echo "              | grep -q .; then"
    echo "            echo \"filter=ALL\" >> \"\$GITHUB_OUTPUT\""
    echo "            echo \"reason=fichier global modifié (pom.xml / App / workflows / scripts)\" >> \"\$GITHUB_OUTPUT\""
    echo "            exit 0"
    echo "          fi"
    echo "          # Détection fine : paquets exerciceN / bonusN modifiés"
    echo "          pkgs=\$(git diff --name-only \"\$BEFORE\" HEAD -- \\"
    echo "              'src/main/java/fr/univ_amu/iut/exercice*/**' \\"
    echo "              'src/test/java/fr/univ_amu/iut/exercice*/**' \\"
    echo "              'src/main/java/fr/univ_amu/iut/bonus*/**' \\"
    echo "              'src/test/java/fr/univ_amu/iut/bonus*/**' \\"
    echo "              | grep -oE 'fr/univ_amu/iut/(exercice[0-9]+|bonus[0-9]+)' \\"
    echo "              | sort -u | tr '/' '.')"
    echo "          if [ -z \"\$pkgs\" ]; then"
    echo "            echo \"filter=NONE\" >> \"\$GITHUB_OUTPUT\""
    echo "            echo \"reason=aucune source d'exercice modifiée (mvnw test sauté, scores restaurés du cache)\" >> \"\$GITHUB_OUTPUT\""
    echo "            exit 0"
    echo "          fi"
    echo "          # Énumère les classes de test des paquets impactés (FQCN explicites)"
    echo "          classes=\"\""
    echo "          for pkg in \$pkgs; do"
    echo "            pkg_dir=\"src/test/java/\$(echo \"\$pkg\" | tr '.' '/')\""
    echo "            while IFS= read -r f; do"
    echo "              cls=\$(basename \"\$f\" .java)"
    echo "              classes=\"\${classes}\${classes:+,}\${pkg}.\${cls}\""
    echo "            done < <(find \"\$pkg_dir\" -type f -name '*.java' 2>/dev/null)"
    echo "          done"
    echo "          if [ -z \"\$classes\" ]; then"
    echo "            echo \"filter=NONE\" >> \"\$GITHUB_OUTPUT\""
    echo "            echo \"reason=paquets modifiés mais aucune classe de test trouvée\" >> \"\$GITHUB_OUTPUT\""
    echo "            exit 0"
    echo "          fi"
    echo "          echo \"filter=\$classes\" >> \"\$GITHUB_OUTPUT\""
    echo "          echo \"reason=paquets impactés : \$(echo \$pkgs | tr '\\\\n' ' ')\" >> \"\$GITHUB_OUTPUT\""
    echo ""
    echo "      - name: Lancer les tests impactés"
    echo "        id: run_tests"
    echo "        if: steps.scope.outputs.filter != 'NONE'"
    echo "        continue-on-error: true"
    echo "        run: |"
    echo "          echo \"Scope : \${{ steps.scope.outputs.reason }}\""
    echo "          if [ \"\${{ steps.scope.outputs.filter }}\" = \"ALL\" ]; then"
    echo "            ./mvnw -B test -DfailIfNoTests=false"
    echo "          else"
    echo "            ./mvnw -B test -Dtest='\${{ steps.scope.outputs.filter }}' -DfailIfNoTests=false"
    echo "          fi"

    runners="compilation"
    env_block="          COMPILATION_RESULTS: \"\${{ steps.compilation.outputs.result }}\""

    for i in "${!exercises[@]}"; do
        ex_name="${exercises[$i]}"
        # Points de cet exercice : les $ex_remainder premiers prennent +1 pt
        # pour absorber le reste, pas de "winner-takes-all" sur le dernier.
        # (Ignore en mode refactoring : la repartition est categorielle.)
        if [ "$i" -lt "$ex_remainder" ]; then
            ex_points=$(( ex_base + 1 ))
        else
            ex_points=$ex_base
        fi

        # Découverte des méthodes de test de cet exercice
        ex_dir="$TEST_ROOT/$ex_name"
        method_count=0
        # Tableaux locaux à cet exercice : (FQCN classe, nom méthode, classif)
        unset ex_classes ex_methods ex_classifications
        ex_classes=()
        ex_methods=()
        ex_classifications=()

        while IFS= read -r f; do
            class_name=$(basename "$f" .java)
            while IFS= read -r line; do
                [ -z "$line" ] && continue
                if [ "$REFACTORING_MODE" = "true" ]; then
                    m="${line%|*}"
                    classif="${line#*|}"
                else
                    m="$line"
                    classif="caract"
                fi
                ex_classes+=("${TEST_PACKAGE_PREFIX}.${ex_name}.${class_name}")
                ex_methods+=("$m")
                ex_classifications+=("$classif")
                method_count=$((method_count + 1))
            done < <(
                if [ "$REFACTORING_MODE" = "true" ]; then
                    extract_test_methods_classified "$f"
                else
                    extract_test_methods "$f"
                fi
            )
        done < <(find "$ex_dir" -type f -name '*.java' 2>/dev/null | sort)

        if [ "$method_count" -eq 0 ]; then
            echo "  - $ex_name : aucune méthode @Test trouvée, ignoré (${ex_points} pts perdus)" >&2
            continue
        fi

        if [ "$REFACTORING_MODE" = "true" ]; then
            ex_caract=0
            ex_structure=0
            for c in "${ex_classifications[@]}"; do
                [ "$c" = "structure" ] && ex_structure=$((ex_structure + 1)) || ex_caract=$((ex_caract + 1))
            done
            echo "  - $ex_name : $method_count méthode(s) ($ex_caract caract + $ex_structure structure)" >&2
        else
            echo "  - $ex_name : $method_count méthode(s) ($ex_points pts)" >&2
        fi

        # Répartition des points entre méthodes : les $m_remainder
        # premières méthodes prennent chacune +1 pt pour absorber le reste,
        # au lieu de tout donner à la dernière. La base à 1000 (au lieu
        # de 100) garantit m_base >= 1 dans tous les cas réalistes, donc
        # aucun test ne vaut 0 (ex : 180 pts / 27 tests -> 18 tests à 7 pts
        # + 9 tests à 6 pts).
        m_base=$(( ex_points / method_count ))
        m_remainder=$(( ex_points - m_base * method_count ))

        for j in "${!ex_methods[@]}"; do
            method="${ex_methods[$j]}"
            fqcn="${ex_classes[$j]}"
            classif="${ex_classifications[$j]}"

            if [ "$REFACTORING_MODE" = "true" ]; then
                # Repartition categorielle : chaque test caract vaut
                # CARACT_POINTS/total_caract (avec spread +1), chaque test
                # structure vaut STRUCTURE_POINTS/total_structure (spread +1).
                # Les indices caract_idx / structure_idx sont globaux (inter-exercices).
                if [ "$classif" = "structure" ]; then
                    if [ "$structure_idx" -lt "$structure_remainder" ]; then
                        m_points=$(( structure_base + 1 ))
                    else
                        m_points=$structure_base
                    fi
                    structure_idx=$((structure_idx + 1))
                else
                    if [ "$caract_idx" -lt "$caract_remainder" ]; then
                        m_points=$(( caract_base + 1 ))
                    else
                        m_points=$caract_base
                    fi
                    caract_idx=$((caract_idx + 1))
                fi
            else
                if [ "$j" -lt "$m_remainder" ]; then
                    m_points=$(( m_base + 1 ))
                else
                    m_points=$m_base
                fi
            fi

            step_id="${ex_name}_${method}"
            env_var_name=$(echo "$step_id" | tr '[:lower:]-' '[:upper:]_')

            cmd="./scripts/grade-test.sh ${fqcn} ${method}"

            echo ""
            echo "      - name: \"${ex_name} : ${method}\""
            echo "        id: ${step_id}"
            echo "        uses: IUTInfoAix-R202/autograding-command-grader@main"
            echo "        with:"
            echo "          test-name: \"${ex_name} : ${method}\""
            echo "          setup-command: \"\""
            echo "          command: ${cmd}"
            echo "          timeout: ${TIMEOUT_MINUTES}"
            echo "          max-score: ${m_points}"

            runners="${runners},${step_id}"
            env_block="${env_block}"$'\n'"          ${env_var_name}_RESULTS: \"\${{ steps.${step_id}.outputs.result }}\""
        done
    done

    echo ""
    echo "      - name: Autograding Reporter"
    echo "        uses: IUTInfoAix-R202/autograding-grading-reporter@main"
    # continue-on-error absorbe l'exit != 0 du reporter quand le score est
    # partiel. Le score reste publié (annotation + check runs), mais le
    # workflow global reste vert : un TP en cours ne donne plus un "CI
    # cassé". Les vrais problèmes techniques (compilation, tests individuels)
    # restent rouges car leurs steps n'ont PAS continue-on-error.
    echo "        continue-on-error: true"
    echo "        env:"
    echo "${env_block}"
    echo "        with:"
    echo "          runners: ${runners}"
    echo ""
    # --- Sauvegarde du cache (toujours, même si le reporter a râlé) ---
    # On écrit après le reporter pour s'assurer que les XMLs ont été lus
    # par grade-test.sh (cohérence avec le score affiché dans le check run).
    echo "      - name: Sauvegarder le cache des rapports Surefire"
    echo "        if: always()"
    echo "        uses: actions/cache/save@v5"
    echo "        with:"
    echo "          path: target/surefire-reports"
    echo "          key: surefire-\${{ github.ref_name }}-\${{ github.sha }}"
    echo "      ${END_MARKER}"
} > "$block"

# --- Splice dans classroom.yml entre les marqueurs ---
start_line=$(grep -n "$START_MARKER" "$CLASSROOM_YML" | head -1 | cut -d: -f1)
end_line=$(grep -n "$END_MARKER" "$CLASSROOM_YML" | head -1 | cut -d: -f1)

if [ -z "$start_line" ] || [ -z "$end_line" ]; then
    echo "ERREUR: marqueurs AUTOGRADING absents de ${CLASSROOM_YML}" >&2
    exit 1
fi

{
    head -n "$((start_line - 1))" "$CLASSROOM_YML"
    cat "$block"
    tail -n +"$((end_line + 1))" "$CLASSROOM_YML"
} > "${CLASSROOM_YML}.new"
mv "${CLASSROOM_YML}.new" "$CLASSROOM_YML"

echo ""
echo "=> ${CLASSROOM_YML} mis à jour."
