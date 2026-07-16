#!/usr/bin/env bash
#
# Fixtures partagées des E2E CLI (#1592), chargées par les fichiers `*.bats` via `load helper` : la
# découverte du fat-jar shadé et le lancement d'un vrai processus sur un workspace jetable. Un seul point
# d'entrée (`fr.univ_amu.iut.cli.Cli`, celui du smoke-test CI) et une seule découverte du jar, partagés
# par tous les fichiers de la suite.
#
# Lancer :  ./mvnw -DskipTests package   # produit target/vigiechiro-*-shaded.jar
#           bats src/test/bats
# (ou définir VIGIECHIRO_JAR=/chemin/vers/le-fat-jar.jar)

# À appeler depuis le `setup()` de chaque fichier : localise le fat-jar (ou saute le test s'il manque).
decouvrir_jar() {
  JAR="${VIGIECHIRO_JAR:-$(ls "${BATS_TEST_DIRNAME}"/../../../target/vigiechiro-*-shaded.jar 2>/dev/null | head -1)}"
  if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
    skip "fat-jar introuvable : lancer './mvnw -DskipTests package' d'abord (ou définir VIGIECHIRO_JAR)"
  fi
}

# Un vrai processus : workspace jetable (base SQLite créée sous le tmpdir du test, migrée au démarrage),
# aucun jeton VigieChiro — on éprouve les contrats HORS-LIGNE.
cli() {
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${BATS_TEST_TMPDIR}" \
    -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}
