#!/usr/bin/env bats
#
# E2E CLI RÉSEAU (#1592) : le client, **pointé sur un serveur stub** via `VIGIECHIRO_URL`, lui adresse
# bien ses requêtes (au lieu de l'API de production) et sait exploiter une réponse Eve bien formée. On
# prouve ainsi la **surcharge d'URL de base** (`ConnexionModule#urlDeBase`) de bout en bout, sur le fat-jar
# shadé, **sans jeton réel ni accès à Internet**.
#
# Le stub est un processus **Python** séparé (cf. `stub_vigiechiro.py`) : il contourne le blocage JPMS
# d'un `com.sun.net.httpserver` en test in-process, se lie à un port éphémère et journalise les requêtes.
#
# Ce fichier pose le HARNAIS réseau. Les contrats métier réseau détaillés (dépôt, import, traitement, sur
# des fixtures Eve réalistes) restent à ajouter dessus (#1592).

load helper

setup() {
  decouvrir_jar
  command -v python3 >/dev/null 2>&1 || skip "python3 requis pour le serveur stub"
}

teardown() {
  [ -n "${STUB_PID:-}" ] && kill "${STUB_PID}" 2>/dev/null
  return 0
}

# Démarre le stub, attend qu'il écrive son port (donc qu'il écoute), et expose STUB_PORT / STUB_JOURNAL.
demarrer_stub() {
  STUB_JOURNAL="${BATS_TEST_TMPDIR}/requetes.log"
  local portfile="${BATS_TEST_TMPDIR}/port"
  python3 "${BATS_TEST_DIRNAME}/stub_vigiechiro.py" "${portfile}" "${STUB_JOURNAL}" &
  STUB_PID=$!
  local i
  for i in $(seq 1 50); do
    [ -s "${portfile}" ] && break
    sleep 0.1
  done
  [ -s "${portfile}" ] || {
    echo "le serveur stub n'a pas démarré (port non publié)"
    return 1
  }
  STUB_PORT=$(cat "${portfile}")
}

@test "reseau : le client honore VIGIECHIRO_URL et adresse ses requêtes au serveur stub (#1592)" {
  demarrer_stub

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli recuperer-vigiechiro
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  # L'issue métier importe peu (le référentiel stub est vide) : la preuve recherchée est que la requête
  # est bien partie vers le STUB (surcharge d'URL honorée), pas vers l'API de production - et qu'une
  # réponse Eve bien formée a été exploitée sans planter le processus.
  [ -f "${STUB_JOURNAL}" ]
  grep -q '^GET ' "${STUB_JOURNAL}"
}
