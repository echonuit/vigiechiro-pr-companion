#!/usr/bin/env bats
#
# Couverture HORS-LIGNE de la surface CLI complète (#1592). Au-delà des commandes du chantier #1565
# (éprouvées une à une dans `cli.bats`) et de l'aide de chacune (le test « TOUTES les sous-commandes
# répondent à --help » de `cli.bats`), on vérifie ici que CHAQUE sous-commande empaquetée honore son
# contrat de niveau processus SANS réseau :
#
#   - options requises manquantes -> refus picocli (exit 2), AVANT toute logique (donc hors-ligne, même
#     pour les commandes réseau : la validation des arguments précède l'appel serveur) ;
#   - commandes locales sans option requise -> exécution sur base fraîche (exit 0) ;
#   - une commande réseau sans jeton -> refus métier explicite (exit 2, #2294), pas un plantage muet.
#
# Les chemins RÉSEAU eux-mêmes (dépôt, import, traitement, ancrage effectifs) restent cadrés en suite
# de #1592 (stub de serveur local ou profil `-Papi-live`).

load helper

setup() {
  decouvrir_jar
}

# Commandes à options (ou paramètres) requis : sans argument, picocli refuse (exit 2) avant d'exécuter
# quoi que ce soit. Une régression de packaging, d'enregistrement d'une commande ou de déclaration d'une
# option requise la ferait tomber. (Les commandes réseau y figurent : leur validation est hors-ligne.)
COMMANDES_OPTIONS_REQUISES=(
  lister-observations discussion synthetiser-passage
  exporter-observations exporter-vu exporter-lot exporter-activite exporter-sons
  importer importer-tadarida
  marquer-douteux marquer-reference poser-certitude
  valider-observations corriger-observations qualifier qualifier-fichier lister-selection pre-check constituer-selection
  restaurer reinitialiser-depot supprimer-passage
  deposer deposer-vigiechiro importer-vigiechiro publier-corrections-vigiechiro
  etat-traitement-vigiechiro lancer-traitement-vigiechiro verifier-depot-vigiechiro traiter-passages
  vigiechiro
  creer-campagne rattacher-campagne modifier-campagne supprimer-campagne
)

@test "surface : chaque commande à options requises refuse l'absence d'arguments (exit 2) (#1592)" {
  local n=0
  for commande in "${COMMANDES_OPTIONS_REQUISES[@]}"; do
    run cli "${commande}"
    [ "${status}" -eq 2 ] || {
      echo "« ${commande} » sans argument : attendu exit 2 (usage picocli), obtenu ${status}"
      return 1
    }
    n=$((n + 1))
  done
  echo "commandes à options requises vérifiées : ${n}"
  [ "${n}" -ge 20 ]
}

# Commandes LOCALES sans option requise : s'exécutent telles quelles sur la base fraîche (migrée au
# démarrage), sans réseau, exit 0. Chacune écrit/lit uniquement sous le workspace jetable du test.
COMMANDES_LOCALES_SANS_ARG=(
  audit-coherence sauvegarder reset-guide retro-empreintes
  solde-saison lister-campagnes rattraper-communes
)

@test "surface : les commandes locales sans option requise s'exécutent sur base fraîche (exit 0) (#1592)" {
  local n=0
  for commande in "${COMMANDES_LOCALES_SANS_ARG[@]}"; do
    run cli "${commande}"
    [ "${status}" -eq 0 ] || {
      echo "« ${commande} » sur base fraîche : attendu exit 0, obtenu ${status} : ${output}"
      return 1
    }
    n=$((n + 1))
  done
  echo "commandes locales sans argument vérifiées : ${n}"
  [ "${n}" -eq 7 ]
}

@test "audit-coherence : base fraîche, aucun écart disque/base annoncé, exit 0 (#1592)" {
  run cli audit-coherence
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"aucun écart"* ]]
}

@test "lister-participations-vigiechiro hors connexion : refus métier explicite, exit 2 (#3005)" {
  run cli lister-participations-vigiechiro
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"jeton"* ]]
  [[ "${output}" != *"Exception"* ]]
}

@test "lister-sites-vigiechiro hors connexion : refus métier explicite, exit 2 (#3003)" {
  # Commande réseau SANS option requise : elle ne va donc dans aucune des deux listes ci-dessus, et
  # prend son test dédié, comme recuperer-vigiechiro. Sans jeton, elle refuse avant tout appel.
  run cli lister-sites-vigiechiro --portee plateforme
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"jeton"* ]]
  [[ "${output}" != *"Exception"* ]]
}

@test "recuperer-vigiechiro hors connexion : refus métier explicite (jeton absent), exit 2 (#1592, #2294)" {
  # La validation des arguments passe (aucune option requise) : la commande s'exécute puis refuse faute
  # de jeton, sans jamais joindre le réseau - refus lisible, pas un plantage muet.
  run cli recuperer-vigiechiro
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"jeton"* ]]
}

@test "reactiver sur un passage inconnu : refus métier, exit 2, jamais une pile (#2554)" {
  # Le chantier #2554 a fait circuler la phase 0 (récupération des observations) sur cette commande.
  # Ce test regarde ce qu'aucun test Java in-process ne voit : le VRAI fat-jar, l'analyse picocli des
  # arguments, et le CODE DE SORTIE. Un refus qui remonterait en pile aurait un exit 1 et un stacktrace.
  mkdir -p "${BATS_TEST_TMPDIR}/carte"
  run cli reactiver --passage 9999 --source "${BATS_TEST_TMPDIR}/carte"
  [ "${status}" -eq 2 ]
  [[ "${output}" != *"Exception"* ]]
  [[ "${output}" != *"\tat fr.univ_amu"* ]]
}

@test "reactiver : le refus ne parle ni du menu ☰ ni de « reconstruire » (#2554)" {
  # Les lectures distantes servent la reconstruction, la réactivation, l'IHM et la CLI. Leur refus disait
  # « avant de reconstruire un passage (menu ☰ > Se connecter) » : un menu dans un terminal, et un geste
  # qu'on n'a pas demandé. Vu du vrai binaire, c'est ce que l'utilisateur lit.
  mkdir -p "${BATS_TEST_TMPDIR}/carte"
  run cli reactiver --passage 9999 --source "${BATS_TEST_TMPDIR}/carte"
  [[ "${output}" != *"☰"* ]]
}

@test "synchroniser-vigiechiro reste un alias : les scripts existants ne cassent pas (#1866)" {
  # Le renommage (ADR 0022) ne doit pas rompre un contrat déjà publié : l'ancien nom mène à la même
  # commande, avec le même refus. Sans ce test, l'alias pourrait disparaître sans que rien ne le dise.
  run cli synchroniser-vigiechiro
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"jeton"* ]]
}
