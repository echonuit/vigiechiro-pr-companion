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

# À appeler depuis le `setup()` de chaque fichier : localise ce qu'on va lancer, saute le test si rien
# n'est là, et ÉCHOUE si ce qu'on a demandé manque.
#
# Deux cibles possibles, et c'est délibéré. Par défaut le fat-jar, qui n'exige qu'un `package` : c'est
# le mode local, le plus rapide. `VIGIECHIRO_LANCEUR` vise à la place le **lanceur empaqueté**
# (`bin/vigiechiro` d'une app-image jpackage), et c'est le mode de la CI : les 66 commandes passent
# alors par le chemin RÉEL de l'utilisateur, runtime jlink compris - celui-là même où #2299 avait
# laissé partir un paquet incapable de démarrer.
decouvrir_jar() {
  JAR="${VIGIECHIRO_JAR:-$(ls "${BATS_TEST_DIRNAME}"/../../../target/vigiechiro-*-shaded.jar 2>/dev/null | head -1)}"
  if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
    skip "fat-jar introuvable : lancer './mvnw -DskipTests package' d'abord (ou définir VIGIECHIRO_JAR)"
  fi
  # ⚠️ Le fat-jar est exigé MÊME quand on vise le lanceur : l'app-image est faite de ce jar, et un test
  # d'ici l'inspecte au `unzip` (aucune classe d'outillage embarquée, #2746). Les deux sont donc
  # présents ensemble, ou aucun des deux.
  #
  # ⚠️ Et un lanceur DEMANDÉ mais introuvable est un ÉCHEC, jamais un `skip`. La nuance décide de ce
  # qu'on apprend : sauté, `VIGIECHIRO_LANCEUR` mal orthographié rendrait 111 tests verts n'ayant rien
  # traversé - un vert creux, exactement ce que l'ADR 2748 refuse. Qui pose cette variable demande à
  # éprouver ce lanceur-là ; son absence est une panne du dispositif.
  if [ -n "${VIGIECHIRO_LANCEUR:-}" ] && [ ! -x "${VIGIECHIRO_LANCEUR}" ]; then
    echo "VIGIECHIRO_LANCEUR pointe sur un lanceur introuvable ou non exécutable :" >&2
    echo "  ${VIGIECHIRO_LANCEUR}" >&2
    echo "  (construire l'app-image, ou ne pas définir la variable pour viser le fat-jar)" >&2
    return 1
  fi
}

# Un vrai processus sur le workspace demandé. C'est le SEUL endroit qui sait ce qu'on lance, et c'est
# ce qui permet de basculer toute la suite d'une cible à l'autre sans toucher aux tests.
#
# Le paramètre existe pour les tests qui simulent DEUX machines (sauvegarde puis restauration
# ailleurs) : le tmpdir d'un test ne suffit pas à représenter deux postes.
#
# ⚠️ Le workspace se pose en OPTION (`--workspace`) et non en propriété JVM quand on vise le lanceur
# empaqueté : celui-ci n'accepte aucun `-D`, et `JAVA_TOOL_OPTIONS` écrirait « Picked up… » sur la
# sortie d'erreur, que des tests d'ici comparent.
cli_sur() {
  local workspace="$1"
  shift
  if [ -n "${VIGIECHIRO_LANCEUR:-}" ]; then
    "${VIGIECHIRO_LANCEUR}" --workspace "${workspace}" "$@"
    return
  fi
  java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace="${workspace}" \
    -cp "${JAR}" fr.univ_amu.iut.cli.Cli "$@"
}

# La même invocation sur le workspace jetable du test : base SQLite créée sous le tmpdir, migrée au
# démarrage, aucun jeton VigieChiro. On éprouve les contrats HORS-LIGNE.
cli() {
  cli_sur "${BATS_TEST_TMPDIR}" "$@"
}

# La même invocation rendue en CHAÎNE, pour le seul test qui exige un vrai pseudo-terminal : `script
# -qec` prend une commande à interpréter, pas un tableau d'arguments. À n'employer que sur des
# arguments sans espace ni guillemet, ce qui est le cas (`--help`).
ligne_cli() {
  if [ -n "${VIGIECHIRO_LANCEUR:-}" ]; then
    echo "${VIGIECHIRO_LANCEUR} --workspace ${BATS_TEST_TMPDIR} $*"
  else
    echo "java --enable-native-access=ALL-UNNAMED -Dvigiechiro.workspace=${BATS_TEST_TMPDIR}" \
      "-cp ${JAR} fr.univ_amu.iut.cli.Cli $*"
  fi
}

# ⚠️ `cli_avec_option_jvm` et `exige_le_fat_jar` ont ete RETIREES (#4075). Elles n existaient que
# parce qu une borne ne se relevait que par propriete JVM, que le lanceur empaquete n accepte pas :
# le cas devait donc viser le fat-jar, et le DIRE en sautant. Depuis que `--reglage` existe, la borne
# se releve par la ligne de commande - donc sur le chemin reel de l utilisateur - et la suite entiere
# traverse le lanceur sans exception.

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
