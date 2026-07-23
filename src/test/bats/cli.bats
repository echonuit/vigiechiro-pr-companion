#!/usr/bin/env bats
#
# E2E de la CLI vigiechiro (#1572, chantier #1565) au niveau SHELL, sur le fat-jar shadé : ce que les
# tests Java in-process (CliTest & co.) ne voient pas — le packaging réel, l'analyse des arguments par
# picocli, et les CODES DE SORTIE d'un vrai processus.
#
# Contrats HORS-LIGNE : aide générale, aide de CHAQUE sous-commande (un test parcourt les 35),
# validation d'arguments, refus métier, lectures et écritures locales sur base jetable. La couverture des chemins
# RÉSEAU (import, dépôt, ancrage) reste cadrée en suite (#1592).
#
# `--help` est activé sur chaque sous-commande (Cli.executer, #1592) : `reactiver --help` décrit la
# commande au lieu d'échouer « Unknown option ».
#
# Découverte du jar et lancement d'un processus : fixtures partagées (`helper.bash`). La couverture
# hors-ligne de TOUTE la surface CLI (chaque commande) vit dans `cli-surface.bats`.
#
# Lancer :  ./mvnw -DskipTests package   # produit target/vigiechiro-*-shaded.jar
#           bats src/test/bats
# (ou définir VIGIECHIRO_JAR=/chemin/vers/le-fat-jar.jar)

load helper

setup() {
  decouvrir_jar
}

@test "aide générale : liste les commandes du chantier, exit 0" {
  run cli --help
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Usage: vigiechiro"* ]]
  [[ "${output}" == *"reconstruire-passage"* ]]
  [[ "${output}" == *"reactiver"* ]]
}

@test "reactiver --help : décrit la commande et ses options, exit 0 (#1592)" {
  run cli reactiver --help
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Usage: vigiechiro reactiver"* ]]
  [[ "${output}" == *"--passage"* ]]
  [[ "${output}" == *"--source"* ]]
}

@test "reconstruire-passage --help : décrit la commande, exit 0 (#1592)" {
  run cli reconstruire-passage --help
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Usage: vigiechiro reconstruire-passage"* ]]
  [[ "${output}" == *"--participation"* ]]
}

@test "TOUTES les sous-commandes répondent à --help (exit 0 + usage) (#1592)" {
  # Le correctif --help (Cli.executer) vaut pour toutes les commandes d'un coup : on le prouve sur
  # CHACUNE. On extrait la liste depuis l'aide générale (1re colonne des lignes de la section Commands,
  # les lignes de description étant bien plus indentées), puis on interroge l'aide de chaque commande.
  #
  # La virgule est retirée : picocli rend une commande qui porte un alias sous la forme
  # « nom-principal, alias » (#1866), et le nom brut emporterait la virgule. On n'interroge que le nom
  # principal - l'alias a son propre test, dans cli-surface.bats.
  run cli --help
  [ "${status}" -eq 0 ]
  local commandes
  commandes=$(printf '%s\n' "${output}" | awk '/^Commands:/{f=1} f && /^  [a-z]/{sub(/,$/, "", $1); print $1}')
  [ -n "${commandes}" ]

  local n=0
  for commande in ${commandes}; do
    run cli "${commande}" --help
    [ "${status}" -eq 0 ] || {
      echo "« ${commande} --help » a échoué (exit ${status})"
      return 1
    }
    [[ "${output}" == *"Usage: vigiechiro ${commande}"* ]] || {
      echo "« ${commande} --help » n'affiche pas son usage"
      return 1
    }
    n=$((n + 1))
  done
  echo "sous-commandes vérifiées : ${n}"
  [ "${n}" -ge 20 ] # garde-fou : l'extraction a bien trouvé les commandes (35 attendues)
}

@test "reconstruire-passage hors connexion : refus métier expliqué, exit 2 (#2294)" {
  # Sans jeton, lister/reconstruire exige la plateforme : refus « non connecté » (pas un plantage muet).
  run cli reconstruire-passage
  [ "${status}" -eq 2 ]
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

@test "reactiver --passage 1 --source <dossier inexistant> : refus métier, exit 2 (#2294)" {
  run cli reactiver --passage 1 --source "${BATS_TEST_TMPDIR}/pas-la"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Dossier introuvable"* ]]
}

# --- Lectures locales (base jetable vide) : la CLI migre la base puis lit, sans réseau ------------

@test "lister-sites : base vide, exit 0" {
  run cli lister-sites
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Aucun site"* ]]
}

@test "lister-passages : base vide, exit 0" {
  run cli lister-passages
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Aucun passage"* ]]
}

@test "statut-passage --passage <inconnu> : refus métier, exit 2 (#2294)" {
  run cli statut-passage --passage 999999
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"introuvable"* ]]
}

# --- Écritures locales (base jetable) : creer-site / ajouter-point écrivent en base, sans réseau ----

@test "creer-site --carre 130711 : exit 0" {
  run cli creer-site --carre 130711 --protocole STANDARD
  [ "${status}" -eq 0 ]
}

@test "creer-site sans --carre : erreur d'usage picocli, exit 2" {
  run cli creer-site --protocole STANDARD
  [ "${status}" -eq 2 ]
}

@test "ajouter-point sans --site : erreur d'usage picocli, exit 2" {
  run cli ajouter-point --code A1
  [ "${status}" -eq 2 ]
}

@test "workflow local : creer-site -> ajouter-point -> lister-sites les montre (#1592)" {
  # Un vrai enchaînement scriptable, sur la même base jetable (le tmpdir du test).
  # creer-site écrit l'identifiant du site sur stdout (les logs partent sur stderr) : on le récupère.
  local site
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  [[ "${site}" =~ ^[0-9]+$ ]]

  run cli ajouter-point --site "${site}" --code A1
  [ "${status}" -eq 0 ]

  run cli lister-sites
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"130711"* ]]
  [[ "${output}" == *"A1"* ]]
}

@test "importer : --conserver-originaux et --sans-originaux s'excluent, exit 2 (#2181, #2294)" {
  # Contrat HORS-LIGNE : le conflit de flags est vérifié dès le lancement, AVANT toute lecture de la
  # source ou accès réseau. On sème un point (creer-site -> ajouter-point, qui écrit l'id sur stdout),
  # puis on passe les deux flags contradictoires avec une source qui n'a même pas besoin d'exister.
  local site point
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli ajouter-point --site "${site}" --code A1 2>/dev/null)
  [[ "${point}" =~ ^[0-9]+$ ]]

  run cli importer --point "${point}" --source "${BATS_TEST_TMPDIR}/carte-sd-absente" \
    --conserver-originaux --sans-originaux
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"s'excluent"* ]]
}
