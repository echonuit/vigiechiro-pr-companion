#!/usr/bin/env bats
#
# E2E CLI de la **sauvegarde et de la restauration complètes** (#2726, #2727), sur le vrai fat-jar.
#
# Ce que ce fichier voit et qu'aucun test Java ne voit : le parcours entier d'une nuit, depuis une
# carte SD jusqu'à sa restauration **sur une autre machine**. C'est là que vit le défaut de #2727 :
# la base restaurée désignait les dossiers par les chemins de la machine d'origine, qui n'existent
# pas sur la machine cible. Un test qui se contente de relire `root_path` en base ne le dit pas ; ce
# qui le dit, c'est de demander ensuite à l'application **où est l'audio**.
#
# La carte SD est fabriquée par le générateur déterministe de la recette
# (cf. dev-docs/recette/fixtures.md) : une vraie carte, avec journal, relevé climatique et WAV
# valides, donc un import qui aboutit vraiment.

load helper

setup_file() {
  RACINE="${BATS_TEST_DIRNAME}/../../.."
  export CARTES="${BATS_FILE_TMPDIR}/cartes"
  # Le générateur vit en portée test (snakeyaml n'est pas dans le fat-jar) : on passe par le goal de
  # confort documenté dans le pom. Une seule fois pour tout le fichier.
  if ! (cd "${RACINE}" && ./mvnw -q test-compile exec:java@generer-sd \
    -Dexec.args="recette/fixtures/spec/sd-nominale.yaml ${CARTES}" >/dev/null 2>&1); then
    export CARTE_INDISPONIBLE=1
  fi
}

setup() {
  decouvrir_jar
  if [ -n "${CARTE_INDISPONIBLE:-}" ] || [ ! -d "${CARTES}/sd-nominale" ]; then
    skip "carte SD de recette non générée (./mvnw test-compile exec:java@generer-sd)"
  fi
  MACHINE_A="${BATS_TEST_TMPDIR}/machine-a"
  MACHINE_B="${BATS_TEST_TMPDIR}/machine-b"
  SAUVEGARDES="${BATS_TEST_TMPDIR}/sauvegardes"
  mkdir -p "${MACHINE_A}" "${MACHINE_B}" "${SAUVEGARDES}"
}

# Un processus CLI sur le workspace choisi : c'est ce qui permet de simuler DEUX machines, là où le
# `cli` partagé travaille toujours sur le tmpdir du test.
cli_sur() {
  local workspace="$1"
  shift
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${workspace}" \
    -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}

# Sème une nuit complète sur la machine A : site, point, puis import réel de la carte SD.
importer_une_nuit_sur_a() {
  local site point
  site=$(cli_sur "${MACHINE_A}" creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli_sur "${MACHINE_A}" ajouter-point --site "${site}" --code A1 2>/dev/null)
  cli_sur "${MACHINE_A}" importer --point "${point}" --source "${CARTES}/sd-nominale" >/dev/null 2>&1
}

@test "restauration complète sur une AUTRE machine : l'audio est retrouvé, pas déclaré perdu (#2727)" {
  importer_une_nuit_sur_a

  # Sur la machine d'origine, l'audio est sur le disque : c'est l'état de référence.
  run cli_sur "${MACHINE_A}" reset-guide
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"[disque ]"* ]]

  cli_sur "${MACHINE_A}" sauvegarder --complet --dossier "${SAUVEGARDES}" >/dev/null 2>&1
  local backup
  backup=$(ls -d "${SAUVEGARDES}"/vigiechiro-sauvegarde-complete-* | head -1)

  # La machine A disparaît : sans cela, ses dossiers existent encore et la restauration les y remet,
  # ce qui ne prouve rien. C'est l'erreur que ce test a d'abord commise.
  rm -rf "${MACHINE_A}"

  # La machine B a son workspace ailleurs : c'est TOUT le défaut. Les chemins persistés par la
  # machine A n'existent pas ici.
  run cli_sur "${MACHINE_B}" restaurer "${backup}" --complet --confirmer
  [ "${status}" -eq 0 ]

  # La question qui tranche : l'application retrouve-t-elle l'audio de cette nuit ? `reset-guide`
  # classe chaque nuit d'après la PRÉSENCE RÉELLE de ses fichiers, et sort en 2 dès qu'une nuit est
  # « perdue ». Avant #2727, la base restaurée désignait les chemins de la machine A : perdu.
  run cli_sur "${MACHINE_B}" reset-guide
  [[ "${output}" != *"[perdu"* ]]
  [[ "${output}" == *"[disque ]"* ]]
  [ "${status}" -eq 0 ]
}

@test "restauration complète : le compte rendu dit où les dossiers ont atterri (#2727)" {
  importer_une_nuit_sur_a
  cli_sur "${MACHINE_A}" sauvegarder --complet --dossier "${SAUVEGARDES}" >/dev/null 2>&1
  local backup
  backup=$(ls -d "${SAUVEGARDES}"/vigiechiro-sauvegarde-complete-* | head -1)
  rm -rf "${MACHINE_A}"

  run cli_sur "${MACHINE_B}" restaurer "${backup}" --complet --confirmer

  [ "${status}" -eq 0 ]
  # Un geste qui déplace des gigaoctets ne peut pas se contenter de « restauré » : le compte rendu
  # nomme l'ancienne et la nouvelle adresse.
  [[ "${output}" == *"n'ont pas retrouvé leur emplacement d'origine"* ]]
  [[ "${output}" == *"${MACHINE_B}"* ]]
}

@test "restaurer un fichier qui n'est pas une base : refus, et la base locale est intacte (#2730)" {
  importer_une_nuit_sur_a
  local faux="${BATS_TEST_TMPDIR}/faux.db"
  echo "ceci n'est pas une base SQLite" > "${faux}"

  run cli_sur "${MACHINE_A}" restaurer "${faux}" --confirmer

  # Code 2 : « j'ai refusé, l'état local est intact » (#2294, #3146). Le 1 dirait « j'ai échoué en
  # route », et un script qui enchaîne ne saurait plus s'il peut continuer. Le test se contentait
  # d'un « non nul », qui accepte les deux.
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Refus :"* ]]
  # La pile ne se voit QUE d'ici : elle est écrite par le journal, pas par le flux d'erreur, donc
  # aucun test in-process ne peut la surveiller.
  [[ "${output}" != *"	at fr.univ_amu"* ]]

  # Ce qui compte n'est pas le refus, c'est ce qu'il laisse derrière lui : la base d'avant, entière,
  # et pas de filet posé pour rien. Le refus précède le moindre remplacement (#2730).
  [ ! -f "${MACHINE_A}/vigiechiro.db.avant-restauration" ]
  run cli_sur "${MACHINE_A}" reset-guide
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"[disque ]"* ]]
}

@test "le verrou n'entrave pas l'enchaînement des commandes, et nomme son occupant (#2731)" {
  # La migration du premier lancement est une opération exclusive : elle prend le verrou. Ce que ce
  # test vérifie, c'est qu'il ne gêne personne ensuite, et que le fichier nomme bien un occupant :
  # c'est de ce fichier que se tire le message affiché à qui se voit refuser l'entrée.
  #
  # ⚠️ Il ne prouve PAS que close() relâche le verrou : le système le relâche de toute façon à la mort
  # du processus. C'est VerrouWorkspaceTest, dans une seule JVM où le système n'aide pas, qui le
  # prouve. Un test bats qui s'en attribuerait le mérite serait un faux témoin.
  run cli_sur "${MACHINE_A}" lister-sites
  [ "${status}" -eq 0 ]
  [ -f "${MACHINE_A}/.verrou" ]
  grep -q "processus" "${MACHINE_A}/.verrou"

  run cli_sur "${MACHINE_A}" creer-site --carre 130711 --protocole STANDARD
  [ "${status}" -eq 0 ]
  run cli_sur "${MACHINE_A}" lister-sites
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"130711"* ]]
}

@test "sauvegarder : le compte rendu dit ce que l'archive emporte (#3212)" {
  importer_une_nuit_sur_a

  # L'ADR 2736 a tranché de ne PAS chiffrer les sauvegardes. La contrepartie est que l'application
  # annonce ce qu'elle écrit : sans cette phrase, l'utilisateur range en aveugle un fichier qui porte
  # les localisations d'espèces protégées. Vérifié sur un vrai processus, pas seulement in-process.
  run cli_sur "${MACHINE_A}" sauvegarder --dossier "${SAUVEGARDES}"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"localisations"* ]]
  [[ "${output}" == *"en clair"* ]]

  run cli_sur "${MACHINE_A}" sauvegarder --complet --dossier "${SAUVEGARDES}"
  [ "${status}" -eq 0 ]
  # La complète emporte en plus l'audio : elle le dit.
  [[ "${output}" == *"enregistrements"* ]]
}

@test "sauvegarde complète : le manifeste dit d'où venait chaque dossier (#2726)" {
  importer_une_nuit_sur_a

  run cli_sur "${MACHINE_A}" sauvegarder --complet --dossier "${SAUVEGARDES}"
  [ "${status}" -eq 0 ]

  local backup
  backup=$(ls -d "${SAUVEGARDES}"/vigiechiro-sauvegarde-complete-* | head -1)
  [ -f "${backup}/manifeste.json" ]
  # Le chemin d'origine est l'information que la sauvegarde ne savait pas conserver, et sans laquelle
  # la restauration ne peut rien remettre en place.
  grep -q "cheminOrigine" "${backup}/manifeste.json"
  grep -q "${MACHINE_A}" "${backup}/manifeste.json"
}

@test "lister-sauvegardes : dossier vide, la commande le dit et sort en 0 (#3197)" {
  # Une installation qui n a jamais migre ni sauvegarde : un etat, pas une anomalie.
  run cli lister-sauvegardes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Aucune sauvegarde"* ]]
}

@test "lister-sauvegardes : ce que sauvegarder vient d ecrire est vu, avec son total (#3197)" {
  run cli sauvegarder
  [ "${status}" -eq 0 ]

  run cli lister-sauvegardes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"vigiechiro-sauvegarde-"* ]]
  [[ "${output}" == *"total"* ]]
}

@test "supprimer-sauvegarde : sans --confirmer, dit la perte et ne touche a rien, exit 2 (#3197)" {
  run cli sauvegarder
  [ "${status}" -eq 0 ]
  local nom
  nom=$(basename "$(find "${BATS_TEST_TMPDIR}/sauvegardes" -name 'vigiechiro-sauvegarde-*.db' | head -1)")
  [ -n "${nom}" ]

  run cli supprimer-sauvegarde --nom "${nom}"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Rien n"*"supprim"* ]]
  [ -f "${BATS_TEST_TMPDIR}/sauvegardes/${nom}" ]

  run cli supprimer-sauvegarde --nom "${nom}" --confirmer
  [ "${status}" -eq 0 ]
  [ ! -f "${BATS_TEST_TMPDIR}/sauvegardes/${nom}" ]
}

@test "supprimer-sauvegarde : un nom inconnu est une erreur d usage, pas un succes silencieux (#3197)" {
  run cli supprimer-sauvegarde --nom vigiechiro-sauvegarde-19700101-000000.db
  [ "${status}" -ne 0 ]
  [[ "${output}" == *"lister-sauvegardes"* ]]
}
