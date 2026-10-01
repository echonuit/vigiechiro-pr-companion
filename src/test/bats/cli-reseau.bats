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

# #5607 : un carré absent de Vigie-Chiro se crée, mais la commande dit tout de suite qu'il faudra
# l'activer sur le portail, au lieu de le laisser découvrir au dépôt. Le catalogue du stub ne porte que
# les carrés 130000 à 130249 : 999999 y est absent. La sortie standard reste l'identifiant du site.
@test "creer-site : un carré absent de Vigie-Chiro dit le geste du portail, sur la sortie d'erreur (#5607)" {
  demarrer_stub 250

  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-bidon"
  local site
  site=$(cli creer-site --carre 999999 2>"${BATS_TEST_TMPDIR}/erreur")
  local code=$?
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN

  [ "${code}" -eq 0 ]
  [[ "${site}" =~ ^[0-9]+$ ]]
  grep -q "n'existe pas sur Vigie-Chiro" "${BATS_TEST_TMPDIR}/erreur"
  grep -q "il faudra l'activer en Point Fixe sur le portail" "${BATS_TEST_TMPDIR}/erreur"
}

# #4867 : une nuit LIEE a une participation, l etat qu aucune commande ne sait poser.
#
# Le lien nait du depot sur le serveur, et aucune commande ne le cree : `lien-participation` l AFFICHE.
# Un banc le pose donc en base, par le MODULE `sqlite3` de Python, comme `cli.bats` le fait deja pour le
# lien de participation. Aucune exigence nouvelle sur le runner : pas de binaire `sqlite3`.
#
# Rend l identifiant du passage sur la sortie standard.
monter_une_nuit_liee() {
  local objectid="$1"
  local sd="${BATS_TEST_TMPDIR}/sd"
  fabriquer_carte_sd "${sd}"
  local site point
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli ajouter-point --site "${site}" --code A1 2>/dev/null)
  cli importer --point "${point}" --source "${sd}" >/dev/null 2>&1
  python3 - "${BATS_TEST_TMPDIR}/vigiechiro.db" "${objectid}" <<'FIN'
import sqlite3
import sys

with sqlite3.connect(sys.argv[1]) as connexion:
    connexion.execute(
        "INSERT INTO vigiechiro_link(entite, ref_locale, objectid) VALUES (?, ?, ?)",
        ("passage", "1", sys.argv[2]))
FIN
  echo 1
}

@test "metadonnees-passage : un envoi refusé par la plateforme sort en 1 et le dit (#4867)" {
  local objectid="6a4961f587bc8dba39481180"
  local passage
  passage=$(monter_une_nuit_liee "${objectid}")

  # Le detail est lisible et ne BOUGE pas entre les deux lectures : la garde de concurrence laisse
  # donc passer, et c est le PATCH qui refuse. Sans cela on mesurerait l autre motif.
  export VIGIECHIRO_STUB_PARTICIPATION="${objectid}"
  export VIGIECHIRO_STUB_CONFIG='{"detecteur": "PR1925492"}'
  export VIGIECHIRO_STUB_REFUS_PATCH=422
  demarrer_stub
  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-de-test"
  run cli metadonnees-passage --passage "${passage}" --envoyer
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN VIGIECHIRO_STUB_PARTICIPATION \
    VIGIECHIRO_STUB_CONFIG VIGIECHIRO_STUB_REFUS_PATCH

  [ "${status}" -eq 1 ]
  [[ "${output}" == *"Envoi refusé"* ]]
  # Le PATCH est bien parti : sans cette ligne, un refus survenu AVANT l ecriture passerait pour un
  # refus de la plateforme.
  grep -q "^PATCH /api/v1/participations/${objectid}" "${STUB_JOURNAL}"
}

@test "metadonnees-passage : une nuit modifiée entre-temps fait renoncer, sortie 1, rien n'est parti (#4867)" {
  local objectid="6a4961f587bc8dba39481181"
  local passage
  passage=$(monter_une_nuit_liee "${objectid}")

  # La garde est COTE CLIENT : elle relit avant d ecrire et renonce si la configuration a bouge depuis
  # sa reference (#4552, deplacee par #4707). Faute de base enregistree, la premiere lecture en tient
  # lieu, donc DEUX configurations differentes suffisent. Aucun 412 : #4523 a mesure que la plateforme
  # rend 200 avec ou sans `If-Match`, et le bouchon n a pas a lui inventer une garde.
  export VIGIECHIRO_STUB_PARTICIPATION="${objectid}"
  export VIGIECHIRO_STUB_CONFIG='{"detecteur": "PR1925492"}'
  export VIGIECHIRO_STUB_CONFIG_RELECTURE='{"detecteur": "PR1925492", "ajoute-par-un-autre-poste": "oui"}'
  demarrer_stub
  export VIGIECHIRO_URL="http://127.0.0.1:${STUB_PORT}/api/v1"
  export VIGIECHIRO_TOKEN="jeton-de-test"
  run cli metadonnees-passage --passage "${passage}" --envoyer
  unset VIGIECHIRO_URL VIGIECHIRO_TOKEN VIGIECHIRO_STUB_PARTICIPATION \
    VIGIECHIRO_STUB_CONFIG VIGIECHIRO_STUB_CONFIG_RELECTURE

  [ "${status}" -eq 1 ]
  [[ "${output}" == *"modifiee-entre-temps"* || "${output}" == *"Rien n'a été envoyé"* ]]
  # Le contraste qui porte le cas : AUCUN PATCH n est parti. C est ce que « rien n a ete envoye »
  # promet, et c est la seule facon de le distinguer du motif voisin.
  ! grep -q "^PATCH /api/v1/participations/" "${STUB_JOURNAL}"
}
