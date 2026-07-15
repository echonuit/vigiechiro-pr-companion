#!/usr/bin/env bats
#
# E2E de la CLI vigiechiro (#1572, chantier #1565) au niveau SHELL, sur le fat-jar shadé : ce que les
# tests Java in-process (CliTest & co.) ne voient pas — le packaging réel, l'analyse des arguments par
# picocli, et les CODES DE SORTIE d'un vrai processus.
#
# Amorce focalisée sur les commandes du chantier (reconstruire-passage, reactiver) en contrat
# HORS-LIGNE (aide générale, validation d'arguments, refus métier). La couverture bats complète des ~35
# commandes et des chemins réseau est cadrée en suite (#1592).
#
# Constat relevé à l'amorce (à traiter dans la suite) : les SOUS-commandes n'exposent pas `--help`
# (seule la racine porte `mixinStandardHelpOptions` : 1 commande sur 36). `reactiver --help` échoue donc
# au lieu d'afficher l'aide de la commande — à corriger d'un coup sur les 35 sous-commandes.
#
# Lancer :  ./mvnw -DskipTests package   # produit target/vigiechiro-*-shaded.jar
#           bats src/test/bats
# (ou définir VIGIECHIRO_JAR=/chemin/vers/le-fat-jar.jar)

setup() {
  JAR="${VIGIECHIRO_JAR:-$(ls "${BATS_TEST_DIRNAME}"/../../../target/vigiechiro-*-shaded.jar 2>/dev/null | head -1)}"
  if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
    skip "fat-jar introuvable : lancer './mvnw -DskipTests package' d'abord (ou définir VIGIECHIRO_JAR)"
  fi
}

# Un vrai processus : workspace jetable (base SQLite créée sous le tmpdir du test), aucun jeton VigieChiro
# (on éprouve les contrats hors-ligne). Même point d'entrée que le smoke-test CI (fr.univ_amu.iut.cli.Cli).
cli() {
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${BATS_TEST_TMPDIR}" \
    -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}

@test "aide générale : liste les commandes du chantier, exit 0" {
  run cli --help
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Usage: vigiechiro"* ]]
  [[ "${output}" == *"reconstruire-passage"* ]]
  [[ "${output}" == *"reactiver"* ]]
}

@test "reconstruire-passage hors connexion : refus métier expliqué, exit 1" {
  # Sans jeton, lister/reconstruire exige la plateforme : refus « non connecté » (pas un plantage muet).
  run cli reconstruire-passage
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"connect"* ]]
}

@test "reconstruire-passage --participation sans valeur : erreur d'usage picocli, exit 2" {
  run cli reconstruire-passage --participation
  [ "${status}" -eq 2 ]
}

@test "reactiver sans options requises : erreur d'usage picocli, exit 2" {
  run cli reactiver
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"passage"* ]]
}

@test "reactiver --passage 1 --source <dossier inexistant> : refus métier, exit 1" {
  run cli reactiver --passage 1 --source "${BATS_TEST_TMPDIR}/pas-la"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"Dossier introuvable"* ]]
}
