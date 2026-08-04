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
# aucun jeton VigieChiro : on éprouve les contrats HORS-LIGNE.
cli() {
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${BATS_TEST_TMPDIR}" \
    -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}

# Variante qui insere UNE option JVM avant le -cp : c'est ainsi qu'un test abaisse une borne de
# ressources (#2732) pour eprouver son refus sur un vrai processus, sans fabriquer d'archive monstrueuse.
cli_avec_option_jvm() {
  local option="$1"
  shift
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${BATS_TEST_TMPDIR}" \
    "${option}" -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}

# Carte SD minimale mais REELLE : le journal du capteur (format LogPR du firmware Teensy), un relevé
# climatique, et un WAV de 1 s a 384 kHz. C'est le strict necessaire pour qu'un import aboutisse.
fabriquer_carte_sd() {
  local sd="$1"
  mkdir -p "${sd}"
  cat > "${sd}/LogPR1925492.txt" << 'FIN'
22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01, CPU 600000000, T4.1
22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes les 600s
22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%
FIN
  printf 'Date\tHour\n' > "${sd}/PaRecPR1925492_THLog.csv"
  python3 - "${sd}/PaRecPR1925492_20260422_203922.wav" << 'FIN'
import sys, wave, struct
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(384000)
    w.writeframes(b"".join(struct.pack("<h", ((i * 41) % 1000) - 500) for i in range(384000)))
FIN
}
