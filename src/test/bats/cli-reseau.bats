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
# Ce fichier pose le HARNAIS réseau, et l'exerce sur le catalogue des sites (#2999) : la pagination
# bornée et son dénominateur ne se voient qu'en traversant le vrai fat-jar, parce que c'est là que
# picocli analyse « --pages » et que le code de sortie devient observable. Les autres contrats métier
# réseau (dépôt, import, traitement) restent à ajouter dessus (#1592).

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
  python3 "${BATS_TEST_DIRNAME}/stub_vigiechiro.py" "${portfile}" "${STUB_JOURNAL}" "${1:-0}" &
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

# --- Catalogue des sites : la pagination bornée, vue du vrai fat-jar (#2999) ---------------------
#
# L'enjeu n'est pas de relire les sites : c'est que la commande **distingue un échantillon d'un
# recensement**. Un préfixe de collection rendu sans le dire est exactement le défaut qui a coûté
# #1277, et le type de retour seul (`LotPagine.complet`) ne prouve rien tant que la ligne de bilan
# n'a pas été lue en sortie réelle.

@test "sites plateforme : 1 page sur un catalogue de 250 annonce l'échantillon, pas le total (#2999)" {
  demarrer_stub 250

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli lister-sites-vigiechiro --portee plateforme --pages 1 --json
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "$status" -eq 0 ]
  # Le compte lu, le total annoncé et l'aveu d'incomplétude : les trois ensemble, sinon on ne saurait
  # pas que 100 n'est qu'un début.
  echo "$output" | grep -q '"sitesLus": *100'
  echo "$output" | grep -q '"totalAnnonce": *250'
  echo "$output" | grep -q '"complet": *false'
}

@test "sites plateforme : --tout épuise la collection et la déclare complète (#2999)" {
  demarrer_stub 150

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli lister-sites-vigiechiro --portee plateforme --tout --json
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "$status" -eq 0 ]
  echo "$output" | grep -q '"sitesLus": *150'
  echo "$output" | grep -q '"complet": *true'
  # La sortie sur page vide se voit dans le journal : 2 pages pleines, puis la 3e qui clôt.
  [ "$(grep -c 'GET /api/v1/sites' "${STUB_JOURNAL}")" -eq 3 ]
}

@test "sites plateforme : --recenser compte les points sur ce qui a été lu (#2999)" {
  demarrer_stub 150

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli lister-sites-vigiechiro --portee plateforme --tout --recenser
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "$status" -eq 0 ]
  # Deux sites sur trois portent Z1 : 100 sur 150. C'est la mesure qui a ouvert le chantier (« Z1
  # partagé par presque tous les carrés »), rejouée ici de bout en bout.
  echo "$output" | grep -E '^Z1 +100'
}

@test "sites plateforme : --carre est filtré par le SERVEUR, en une requête (#3769)" {
  demarrer_stub 250

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli lister-sites-vigiechiro --portee plateforme --carre 130001
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "$status" -eq 0 ]
  # Le carré demandé est là, et le bilan annonce une RECHERCHE : « collection complète » laisserait
  # croire qu'on a parcouru les 250 sites, « échantillon » qu'on n'en a vu qu'une part. Ni l'un ni l'autre.
  [[ "${output}" == *"130001"* ]]
  [[ "${output}" == *"Recherche du carré 130001"* ]]
  [[ "${output}" != *"collection complète"* ]]
  # Une seule requête, portant q : la preuve que le catalogue n'a pas été parcouru.
  [ "$(grep -c 'q=130001' "${STUB_JOURNAL}")" -eq 1 ]
  [ "$(grep -c 'page=' "${STUB_JOURNAL}")" -eq 0 ]
}

@test "sites plateforme : --carre inexistant rend une liste vide SANS prétendre à un échantillon (#3769)" {
  demarrer_stub 250

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  run cli lister-sites-vigiechiro --portee plateforme --carre 999999
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "$status" -eq 0 ]
  # C'est ici que le défaut d'origine se voyait : la commande rendait un tableau vide sur un carré qui
  # existe, faute d'avoir lu la bonne page. Le zéro doit désormais être un vrai zéro, dit comme tel.
  [[ "${output}" == *"Recherche du carré 999999"* ]]
  [[ "${output}" == *"0 site(s) trouvé(s)"* ]]
}
