#!/usr/bin/env bats
#
# E2E des scripts de `.github/scripts/` (#3508).
#
# ⚠️ Premier fichier de cette suite à ne pas éprouver la CLI : les dix scripts de CI du dépôt
# n'étaient couverts par rien, et leur vérification se réduisait à des lancements à la main, qui ne se
# rejouent pas. Celui de la durée du portail y entre parce qu'il est le plus récent et qu'il porte un
# **jugement** : un dispositif qui juge et que personne n'éprouve est ce que ce lot corrige ailleurs.
#
# La série est injectée par `SERIE_DUREES_FICHIER`, sans quoi le script irait chercher ses données sur
# l'API et aucun cas ne serait fabricable.

setup() {
  MESURE="${BATS_TEST_DIRNAME}/../../../.github/scripts/mesure-duree-portail.sh"
  [ -x "${MESURE}" ] || skip "script introuvable : ${MESURE}"
  SERIE="${BATS_TEST_TMPDIR}/serie.json"
}

# Douze durées récentes puis douze anciennes, dans l'ordre où l'API les rend.
serie() {
  printf '[%s]' "$(printf '%s,' "$@" | sed 's/,$//')" > "${SERIE}"
}

@test "la série réelle du dépôt, aberrantes comprises, ne déclenche rien (#3508)" {
  # Les vraies mesures : vingt-huit exécutions entre 9,7 et 12,1 min, et DEUX à 21,8 et 23,7. Un
  # dispositif qui crie sur l'historique existant s'apprend à ignorer dès le premier jour, et c'est le
  # cas que ce test protège en premier.
  serie 12.1 10.6 11.2 12.1 21.8 10.8 11.1 11.6 12.1 10.6 10.0 10.6 \
        11.0 10.9 23.7 10.4 11.3 10.7 10.5 11.9 10.2 11.4 10.8 9.7

  SERIE_DUREES_FICHIER="${SERIE}" run bash "${MESURE}" depot/quelconque maven.yml

  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Durée du portail qualité"* ]]
  [[ "${output}" != *"::warning"* ]]
}

@test "une dérive nette au-delà du seuil déclenche l'avertissement, sans bloquer (#3508)" {
  # Douze récentes à ~14 min contre douze anciennes à ~11 : l'allongement par accumulation que le
  # dispositif existe pour voir.
  serie 14.0 14.2 13.8 14.1 13.9 14.3 14.0 13.7 14.2 14.1 13.8 14.0 \
        11.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1

  SERIE_DUREES_FICHIER="${SERIE}" run bash "${MESURE}" depot/quelconque maven.yml

  # Il AVERTIT : le code de sortie reste 0. Un rouge se relève, un avertissement s'instruit.
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"::warning"* ]]
  [[ "${output}" == *"s'allonge"* ]]
}

@test "une aberrante isolée ne suffit pas à faire crier le dispositif (#3508)" {
  # Une seule exécution à plus du double, dans une série par ailleurs stable. C'est le cas qui a
  # écarté le butoir « médiane + 30 % » : une médiane ne bouge pas pour une valeur extrême sur douze.
  serie 24.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1 \
        11.0 10.9 11.2 10.8 11.1 10.7 11.3 10.9 11.0 11.2 10.8 11.1

  SERIE_DUREES_FICHIER="${SERIE}" run bash "${MESURE}" depot/quelconque maven.yml

  [ "${status}" -eq 0 ]
  [[ "${output}" != *"::warning"* ]]
}

@test "sans assez d'historique, il le dit au lieu de conclure (#3508)" {
  serie 11.0 10.9 11.2 10.8 11.1

  SERIE_DUREES_FICHIER="${SERIE}" run bash "${MESURE}" depot/quelconque maven.yml

  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Pas encore assez d'historique"* ]]
  [[ "${output}" != *"::warning"* ]]
}
