#!/usr/bin/env bats
#
# E2E de la CLI vigiechiro (#1572, chantier #1565) au niveau SHELL, sur le fat-jar shadé : ce que les
# tests Java in-process (CliTest & co.) ne voient pas : le packaging réel, l'analyse des arguments par
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

@test "rattraper-communes : base vide, rien a rattraper, exit 0 (#2791)" {
  run cli rattraper-communes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"rien à rattraper"* ]]
}

@test "rattraper-communes : un point sans GPS reste hors du rattrapage, exit 0 (#2791)" {
  # Sans coordonnées, il n'y a rien à résoudre : la commande le dit sans toucher au réseau.
  local site
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  cli ajouter-point --site "${site}" --code A1

  run cli rattraper-communes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"rien à rattraper"* ]]
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

@test "importer : une archive .zip s importe comme un dossier, exit 0 (#3195)" {
  # Parite CLI/IHM sur un VRAI processus : le fat-jar recoit une archive, la decompresse sous le
  # workspace, importe, et efface son temporaire. Aucun test in-process ne voit le packaging.
  command -v python3 >/dev/null 2>&1 || skip "python3 requis pour fabriquer le WAV et l archive"

  local sd="${BATS_TEST_TMPDIR}/sd"
  fabriquer_carte_sd "${sd}"
  python3 -c "import shutil, sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2])" \
    "${BATS_TEST_TMPDIR}/carte" "${sd}"

  local site point
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli ajouter-point --site "${site}" --code A1 2>/dev/null)

  run cli importer --point "${point}" --source "${BATS_TEST_TMPDIR}/carte.zip"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Import réussi"* ]]

  # Le temporaire d extraction ne survit pas a la commande : un script n a aucun ecran ou repasser.
  [ -z "$(find "${BATS_TEST_TMPDIR}" -maxdepth 1 -type d -name 'import-zip-*' 2>/dev/null)" ]
}

@test "importer : une archive hors bornes est refusee, exit 2, rien d importe (#3195, #2732)" {
  # LE parcours que #2732 n avait aucun moyen d exercer bout en bout : ses bornes ne vivaient que
  # derriere l IHM. On abaisse la borne d entrees plutot que de fabriquer une bombe - ce qu on eprouve
  # est le CHEMIN du refus jusqu au code de sortie du processus.
  command -v python3 >/dev/null 2>&1 || skip "python3 requis pour fabriquer le WAV et l archive"

  local sd="${BATS_TEST_TMPDIR}/sd"
  fabriquer_carte_sd "${sd}"
  python3 -c "import shutil, sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2])" \
    "${BATS_TEST_TMPDIR}/carte" "${sd}"

  local site point
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli ajouter-point --site "${site}" --code A1 2>/dev/null)

  run cli_avec_option_jvm -Dvigiechiro.import.zip.max-entrees=1 \
    importer --point "${point}" --source "${BATS_TEST_TMPDIR}/carte.zip"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Archive zip refus"* ]]
  [[ "${output}" == *"max-entrees"* ]]

  # Rien d importe, et rien de laisse derriere.
  run cli lister-passages
  [[ "${output}" != *"130711"* ]]
  [ -z "$(find "${BATS_TEST_TMPDIR}" -maxdepth 1 -type d -name 'import-zip-*' 2>/dev/null)" ]
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

@test "lister-observations --a-enjeu : l option existe et le filtre passe, exit 0 (#2353)" {
  # Base jetable sans observation : la liste est vide, ce qui reste un resultat valide. Le test prouve ce
  # qu aucun test Java ne voit : le fat-jar declare l option et sait resoudre le referentiel de
  # conservation (port EspecesPrioritaires) au moment ou la selection s applique.
  run cli lister-observations --passage 1 --a-enjeu
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Aucune observation"* ]]
}

@test "lister-observations --a-enjeu : l aide decrit le filtre (#2353)" {
  run cli lister-observations --help
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"--a-enjeu"* ]]
  [[ "${output}" == *"prioritaires"* ]]
}

@test "valider-observations --a-enjeu : le meme filtre vise les gestes de revue (#2353)" {
  # La promesse de SelectionObservations : le MEME code choisit, pour lister et pour agir. Le geste doit
  # donc connaitre le filtre, sinon « lister puis valider » ne verrait pas le meme ensemble.
  run cli valider-observations --passage 1 --a-enjeu
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Aucune observation"* ]]
}

@test "synthetiser-passage : le CSV emporte la citation et l avertissement, exit 0 (#2351)" {
  # L enjeu du lot : un CSV quitte l application pour vivre dans un tableur, loin de l ecran qui
  # portait la mise en garde. Base vide : le tableau se reduit a son bloc de tete, ce qui suffit a
  # prouver que le contexte voyage AVEC la donnee.
  run cli synthetiser-passage --passage 1 --sortie "${BATS_TEST_TMPDIR}/synthese.csv"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/synthese.csv" ]
  run cat "${BATS_TEST_TMPDIR}/synthese.csv"
  [[ "${output}" == *"Bas Y."* ]]
  [[ "${output}" == *"niveau d enjeu de conservation"* || "${output}" == *"enjeu de conservation"* ]]
  # Fragment sans accent volontairement : le fichier .bats reste ASCII, le CSV lui est accentue.
  [[ "${output}" == *"Compar"* ]]
}

@test "synthetiser-passage sans --sortie : le CSV part sur la sortie standard, exit 0 (#2351)" {
  # Le chemin le plus evident de la commande, et celui qu on redirige : « > synthese.csv ». Il a
  # longtemps ete le seul non couvert, et il etait casse : la sortie se perdait, code 0 a l appui,
  # parce qu un PrintWriter en auto-flush ne se vide pas sur `print`. Un vert qui ne teste pas le
  # chemin par defaut ne dit rien du tout.
  run cli synthetiser-passage --passage 1
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Bas Y."* ]]
  [[ "${output}" == *"Code esp"* ]]
}

@test "synthetiser-passage --format json : le contexte est un objet a part, exit 0 (#2351)" {
  run cli synthetiser-passage --passage 1 --format json
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"\"contexte\""* ]]
  [[ "${output}" == *"\"source\""* ]]
  [[ "${output}" == *"Bas Y."* ]]
}

@test "synthetiser-passage --format xml : refus explique, exit 2 (#2351)" {
  run cli synthetiser-passage --passage 1 --format xml
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Format non pris en charge"* ]]
}

@test "exporter-activite : ecrit le CSV d activite sur une base vide, exit 0 (#2352)" {
  # Base jetable sans observation : le CSV se reduit a ses en-tetes, ce qui reste un resultat valide.
  # Le test prouve ce que le test Java in-process ne voit pas : le fat-jar sait produire le fichier.
  run cli exporter-activite --passage 1 --sortie "${BATS_TEST_TMPDIR}/activite.csv"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/activite.csv" ]
  run head -1 "${BATS_TEST_TMPDIR}/activite.csv"
  [[ "${output}" == *"Contacts"* ]]
}

@test "exporter-activite --tout : couvre tous les passages, exit 0 (#2613)" {
  run cli exporter-activite --tout --sortie "${BATS_TEST_TMPDIR}/tout.csv"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/tout.csv" ]
}

@test "lister-observations : les filtres qui QUALIFIENT passent sur une base vide, exit 0 (#3082)" {
  # Base jetable sans observation. Les deux criteres qui QUALIFIENT rendent legitimement un ensemble
  # vide (ADR 3082) : « aucune sequence en attente » est une reponse. Ce qui se prouve ici est que les
  # options sont dans le PAQUET et analysees par picocli, ce que les tests in-process ne voient pas.
  run cli lister-observations --passage 1 --non-identifie --heure-debut 21 --heure-fin 6
  [ "${status}" -eq 0 ]
}

@test "lister-observations : le taxon parent DESIGNE, donc il refuse sur une base vide (#3082)" {
  # L autre moitie de l ADR 3082, verifiee sur le vrai fat-jar : un critere qui designe refuse quand il
  # ne trouve rien, et NOMME ce qui est present. C est le bats qui a montre ce cas - mon premier jet
  # attendait un exit 0 pour les trois filtres ensemble, sans voir que la base de test est vide.
  run cli lister-observations --passage 1 --taxon-parent Chiropteres
  [ "${status}" -ne 0 ]
  [[ "${output}" == *"Taxons parents présents"* ]]
}

@test "lister-observations : une plage horaire a demi donnee est refusee (#3082)" {
  # « --heure-debut 21 » seul se lirait « depuis 21 h » ou « jusqu a 21 h » : choisir a la place de
  # l utilisateur produirait un resultat plausible et faux.
  run cli lister-observations --passage 1 --heure-debut 21
  [ "${status}" -ne 0 ]
  [[ "${output}" == *"--heure-fin"* ]]
}

@test "lister-observations : l aide decrit les trois filtres ajoutes (#3082)" {
  run cli lister-observations --help
  [[ "${output}" == *"--taxon-parent"* ]]
  [[ "${output}" == *"--non-identifie"* ]]
  [[ "${output}" == *"--heure-debut"* ]]
}

@test "exporter-activite : les cinq filtres sont acceptes ensemble, exit 0 (#3059)" {
  # Base jetable sans observation : ce qui se prouve ici n'est pas le RESULTAT du filtrage - les tests
  # Java le tiennent - mais que le FAT-JAR expose les cinq options, que picocli les analyse et qu'elles
  # se combinent. Une option declaree mais absente du paquet echoue ici, et nulle part ailleurs.
  run cli exporter-activite --tout --nature protocole --sortie "${BATS_TEST_TMPDIR}/cinq.csv"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/cinq.csv" ]
}

@test "exporter-activite --a-enjeu : l option existe et le filtre passe, exit 0 (#3079)" {
  # Le cas manquait : la passe 6 de la cloture avait lance PIT et saute les bats, alors que #3079 avait
  # change le comportement de la commande. C'est ici, et nulle part ailleurs, que se voit qu'une option
  # declaree est bien dans le PAQUET et analysee par picocli.
  run cli exporter-activite --tout --a-enjeu --sortie "${BATS_TEST_TMPDIR}/enjeu.csv"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/enjeu.csv" ]
}

@test "exporter-activite : l aide decrit les cinq filtres (#3059)" {
  # Une option qui marche mais que l'aide ne nomme pas est introuvable : la parite se juge aussi sur ce
  # que l'utilisateur peut DECOUVRIR sans lire le code.
  run cli exporter-activite --help
  [[ "${output}" == *"--lieu"* ]]
  [[ "${output}" == *"--nuit"* ]]
  [[ "${output}" == *"--taxon-parent"* ]]
  [[ "${output}" == *"--nature"* ]]
  [[ "${output}" == *"--a-enjeu"* ]]
}

@test "exporter-activite --nature inconnue : refus explique, exit 2 (#3059)" {
  # Le refus doit NOMMER les valeurs acceptees : borner en silence rendrait un fichier vide sans dire
  # pourquoi, et le script enchainerait.
  run cli exporter-activite --tout --nature aleatoire --sortie "${BATS_TEST_TMPDIR}/nature.csv"
  [ "${status}" -ne 0 ]
  [[ "${output}" == *"protocole"* ]]
}

@test "exporter-activite --nuit illisible : refus qui donne le format, exit 2 (#3059)" {
  run cli exporter-activite --tout --nuit 21/06/2026 --sortie "${BATS_TEST_TMPDIR}/nuit.csv"
  [ "${status}" -ne 0 ]
  [[ "${output}" == *"AAAA-MM-JJ"* ]]
}

@test "exporter-activite : --passage et --tout s excluent, exit 2 (#2613)" {
  run cli exporter-activite --passage 1 --tout --sortie "${BATS_TEST_TMPDIR}/a.csv"
  [ "${status}" -eq 2 ]
}

@test "exporter-activite --tranche 99 : refus explique, exit 2 (#2352)" {
  run cli exporter-activite --passage 1 --sortie "${BATS_TEST_TMPDIR}/a.csv" --tranche 99
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Tranche invalide"* ]]
}

@test "exporter-sons --espece : ecrit l archive et le dit, exit 0 (#2795)" {
  # Base jetable sans observation : l archive se reduit au CSV d en-tetes, resultat valide. Le test
  # prouve ce que le test Java in-process ne voit pas : le fat-jar sait produire le ZIP et le dire.
  run cli exporter-sons --espece Rhifer --sortie "${BATS_TEST_TMPDIR}/sons.zip"
  [ "${status}" -eq 0 ]
  [ -f "${BATS_TEST_TMPDIR}/sons.zip" ]
  [[ "${output}" == *"Archive écrite"* ]]
  [[ "${output}" == *"0 observation(s), 0 son(s)"* ]]
}

@test "exporter-sons : l archive produite contient le CSV et le son, et ils sont bons (#2795)" {
  # LE test qui traverse tout : une carte SD fabriquee ici, importee par le vrai fat-jar, un CSV
  # Tadarida qui cree l observation, puis l export - et l archive est OUVERTE pour verifier ce qu elle
  # contient. Les tests Java in-process voient la meme chaine, mais pas le packaging : un ZIP produit
  # par le jar shade pourrait etre illisible sans qu aucun d eux ne rougisse.
  command -v python3 >/dev/null 2>&1 || skip "python3 requis pour fabriquer le WAV et relire l archive"

  local sd="${BATS_TEST_TMPDIR}/sd"
  mkdir -p "${sd}"
  cat > "${sd}/LogPR1925492.txt" << 'EOF'
22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01, CPU 600000000, T4.1
22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes les 600s
22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz FL N FPH 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%
EOF
  printf 'Date\tHour\n' > "${sd}/PaRecPR1925492_THLog.csv"
  python3 - "${sd}/PaRecPR1925492_20260422_203922.wav" << 'EOF'
import sys, wave, struct
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(384000)
    w.writeframes(b"".join(struct.pack("<h", ((i * 41) % 1000) - 500) for i in range(384000)))
EOF

  local site point sequence
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  point=$(cli ajouter-point --site "${site}" --code A1 2>/dev/null)
  run cli importer --point "${point}" --source "${sd}"
  [ "${status}" -eq 0 ]

  # Le nom de sequence est produit par la transformation (prefixe R6) : on le relit sur le disque
  # plutot que de le deviner, puis on ecrit le CSV Tadarida qui cree l observation.
  sequence=$(basename "$(find "${BATS_TEST_TMPDIR}" -name '*_000.wav' | head -1)" .wav)
  [ -n "${sequence}" ]
  {
    printf '"nom du fichier";"temps_debut";"temps_fin";"frequence_mediane";"tadarida_taxon";"tadarida_probabilite";"tadarida_taxon_autre";"observateur_taxon";"observateur_probabilite";"validateur_taxon";"validateur_probabilite"\n'
    # Trois detections sur la MEME sequence (c est le format Tadarida : une ligne par detection), aux
    # probabilites distinctes. La troisieme n en a AUCUNE : c est le cas qui decide de --proba-min.
    printf '"%s";"0.3";"3.9";"45.0";"Rhifer";"0.93";"";"";"";"";""\n' "${sequence}"
    printf '"%s";"5.0";"6.0";"45.0";"Rhifer";"0.42";"";"";"";"";""\n' "${sequence}"
    printf '"%s";"8.0";"9.0";"45.0";"Rhifer";"";"";"";"";"";""\n' "${sequence}"
    # Une 4e detection, d une AUTRE espece et avec une probabilite : elle sert a eprouver
    # l avertissement, qui exige un lot dont AUCUNE ligne n est depourvue de probabilite.
    printf '"%s";"11.0";"12.0";"45.0";"Pipkuh";"0.50";"";"";"";"";""\n' "${sequence}"
  } > "${BATS_TEST_TMPDIR}/obs.csv"
  run cli importer-tadarida --passage 1 --csv "${BATS_TEST_TMPDIR}/obs.csv"
  [ "${status}" -eq 0 ]

  run cli exporter-sons --passage 1 --sortie "${BATS_TEST_TMPDIR}/sons.zip"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"4 observation(s), 1 son(s)"* ]]

  # L archive est OUVERTE : le CSV porte bien l observation (carre, point, fichier), et le son est
  # range sous sa nuit, avec des octets identiques a ceux du disque.
  run python3 - "${BATS_TEST_TMPDIR}/sons.zip" "${sequence}" << 'EOF'
import sys, zipfile
archive, sequence = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(archive) as zip:
    noms = zip.namelist()
    assert noms[0] == "observations.csv", noms
    sons = [n for n in noms if n.startswith("sons/")]
    assert sons == ["sons/Car130711-2026-Pass1-A1/" + sequence + ".wav"], sons
    lignes = zip.read("observations.csv").decode("utf-8").splitlines()
    assert len(lignes) == 5, lignes
    assert "130711" in lignes[1] and sequence in lignes[1], lignes[1]
    assert len(zip.read(sons[0])) > 1000, "le son emballe est vide ou tronque"
print("archive conforme")
EOF
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"archive conforme"* ]]

  # #2971 : le meme export RESTREINT par --lieu. Le carre de la nuit importee est 130711 ; la
  # correspondance etant partielle et insensible a la casse, « 1307 » suffit a le designer.
  run cli exporter-sons --passage 1 --lieu 1307 --sortie "${BATS_TEST_TMPDIR}/filtre.zip"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"4 observation(s), 1 son(s)"* ]]

  # Et l archive FILTREE est relue : le filtre ne doit pas seulement laisser passer la commande, il
  # doit produire une archive qui contient encore la bonne chose.
  run python3 - "${BATS_TEST_TMPDIR}/filtre.zip" "${sequence}" << 'EOF'
import sys, zipfile
archive, sequence = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(archive) as zip:
    lignes = zip.read("observations.csv").decode("utf-8").splitlines()
    assert len(lignes) == 5, lignes
    assert "130711" in lignes[1], lignes[1]
    sons = [n for n in zip.namelist() if n.startswith("sons/")]
    assert len(sons) == 1 and len(zip.read(sons[0])) > 1000, sons
print("archive filtree conforme")
EOF
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"archive filtree conforme"* ]]

  # #2971 : un lieu qui ne correspond a RIEN refuse en nommant ce qui existe, plutot que d ecrire une
  # archive vide en code 0. Un script qui enchainerait ne verrait pas la faute de frappe.
  run cli exporter-sons --passage 1 --lieu Marseille --sortie "${BATS_TEST_TMPDIR}/vide.zip"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Marseille"* ]]
  [[ "${output}" == *"130711"* ]]
  [ ! -f "${BATS_TEST_TMPDIR}/vide.zip" ]

  # #2971 : la meme option sur lister-observations, la surface jumelle.
  run cli lister-observations --passage 1 --lieu 1307
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Rhifer"* ]]

  run cli lister-observations --passage 1 --lieu Marseille
  [ "${status}" -eq 2 ]

  # #2971 : LE SCENARIO FONDATEUR du chantier, en un seul appel qui traverse tout. « Les grands
  # Rhinolophes de mes nuits sur ce carre, au-dessus de 90 % ». La commune serait le lieu naturel,
  # mais elle se derive du GPS par l API Geo : impossible hors ligne, et cette suite s execute hors
  # ligne. Le CARRE la remplace, c est la meme dimension du filtre.
  #
  # Ce qui se joue ici n est pas chaque filtre pris a part (les tests Java les couvrent) mais leur
  # COUTURE : espece x lieu x seuil composes dans une seule invocation du vrai fat-jar.
  run cli exporter-sons --espece Rhifer --lieu 1307 --proba-min 0.9 \
      --sortie "${BATS_TEST_TMPDIR}/experts.zip"
  [ "${status}" -eq 0 ]
  # Deux retenues sur trois : celle a 0,93, et celle SANS probabilite. Celle a 0,42 tombe.
  [[ "${output}" == *"2 observation(s), 1 son(s)"* ]]

  # L archive du scenario est relue : c est la seule preuve que le ZIP envoye a l expert contient
  # bien ces deux-la, et pas trois ni une.
  run python3 - "${BATS_TEST_TMPDIR}/experts.zip" << 'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as zip:
    lignes = zip.read("observations.csv").decode("utf-8").splitlines()
    assert len(lignes) == 3, lignes
    # La detection a 0,42 est tombee ; celle sans probabilite est restee, parce qu une absence de
    # probabilite n est pas une mauvaise probabilite.
    assert not any('"0.42"' in ligne or ";0.42;" in ligne for ligne in lignes), lignes
print("scenario conforme")
EOF
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"scenario conforme"* ]]

  # #2971 : le reflexe du pourcentage est refuse, avec l unite rappelee.
  run cli exporter-sons --espece Rhifer --proba-min 90 --sortie "${BATS_TEST_TMPDIR}/pourcent.zip"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"0.9"* ]]
  [ ! -f "${BATS_TEST_TMPDIR}/pourcent.zip" ]

  # #2971 : un seuil VALIDE qui ecarte tout produit une archive vide, resultat legitime mais muet.
  # L avertissement nomme la meilleure probabilite du lot (ici 0,50), pour dire de combien descendre.
  # L espece Pipkuh est choisie parce que sa seule detection PORTE une probabilite : sur un lot ou une
  # ligne en serait depourvue, elle serait conservee et le resultat ne serait jamais vide.
  run cli exporter-sons --espece Pipkuh --proba-min 0.99 --sortie "${BATS_TEST_TMPDIR}/haut.zip"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"0 observation(s)"* ]]
  [[ "${output}" == *"0,50"* ]]
  [[ "${output}" == *"abaissez le seuil"* ]]
}

@test "exporter-sons : --passage et --espece s excluent, exit 2 (#2795)" {
  run cli exporter-sons --passage 1 --espece Rhifer --sortie "${BATS_TEST_TMPDIR}/a.zip"
  [ "${status}" -eq 2 ]
}

@test "exporter-sons : passage inconnu refuse et explique, exit 2 (#2795)" {
  run cli exporter-sons --passage 999 --sortie "${BATS_TEST_TMPDIR}/a.zip"
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Passage introuvable"* ]]
  [ ! -f "${BATS_TEST_TMPDIR}/a.zip" ]
}

@test "exporter-activite --format json : refus explique, exit 2 (#2352)" {
  run cli exporter-activite --passage 1 --sortie "${BATS_TEST_TMPDIR}/a.json" --format json
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"Format non pris en charge"* ]]
}

@test "workflow campagne : creer-campagne -> lister-campagnes -> solde-saison --campagne filtre (#2355)" {
  # Un point SANS nuit suffit à prouver le filtre au niveau processus : il figure au solde complet, et
  # il en disparaît dès qu'on demande une campagne (aucun de ses passages n'y est rattaché).
  local site
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  run cli ajouter-point --site "${site}" --code A1
  [ "${status}" -eq 0 ]

  run cli creer-campagne --nom "Suivi ENS"
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Suivi ENS"* ]]

  run cli lister-campagnes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Suivi ENS"* ]]

  # Sans filtre : le point suivi est là.
  run cli solde-saison
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"130711"* ]]

  # Avec filtre : plus rien, le point n'ayant aucune nuit rattachée à cette campagne.
  run cli solde-saison --campagne "ens"
  [ "${status}" -eq 0 ]
  [[ "${output}" != *"130711"* ]]
  [[ "${output}" == *"Aucun point suivi"* ]]
}

@test "solde-saison : --lieu et --reste-a-faire filtrent les points, l'en-tete reste celui de la saison (#3092)" {
  # Ce que seul le fat-jar prouve : picocli accepte les deux options, et le drapeau booleen
  # --reste-a-faire ne reclame pas de valeur. Les tests Java pilotent la commande en processus.
  local site
  site=$(cli creer-site --carre 130711 --protocole STANDARD 2>/dev/null)
  run cli ajouter-point --site "${site}" --code A1
  [ "${status}" -eq 0 ]

  # --lieu retient le point cherche.
  run cli solde-saison --lieu 130711
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"130711"* ]]

  # --lieu qui ne retient rien le DIT, au lieu d'un en-tete suivi du vide.
  run cli solde-saison --lieu ZZZZZZ
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"aucun point ne correspond aux filtres"* ]]

  # --reste-a-faire est un drapeau : il ne prend pas de valeur.
  run cli solde-saison --reste-a-faire
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"130711"* ]]
}

@test "audit-coherence : --gravite et --categorie filtrent l'affichage, le code de sortie juge le workspace (#3092)" {
  # Le point qui ne se voit qu'au niveau processus : le code de sortie. Un script d'integration lit
  # ce code, et un filtre d'affichage ne doit pas faire passer un workspace abime pour sain.
  run cli audit-coherence --gravite INFO
  [ "${status}" -eq 0 ]

  # Une valeur hors de l'enumeration est refusee par picocli (exit 2), pas ignoree en silence.
  run cli audit-coherence --gravite PAS_UNE_GRAVITE
  [ "${status}" -eq 2 ]

  run cli audit-coherence --categorie DISQUE_MANQUANT
  [ "${status}" -eq 0 ]
}

@test "cycle de vie d'une campagne : creer -> modifier -> supprimer, relu par lister-campagnes (#2355)" {
  # Ce qui se joue ici est ENTRE les commandes : une modification qui ne se relit pas, ou une
  # suppression qui laisse la ligne en place, ne se voit qu'en enchaînant. Les tests Java pilotent
  # le service en mémoire ; seul le fat-jar prouve l'analyse d'arguments, la persistance et les
  # codes de sortie de ces deux commandes-là.
  local campagne
  campagne=$(cli creer-campagne --nom "Suivi ENS" --annee 2026 2>/dev/null | sed -E 's/.*#([0-9]+).*/\1/')
  [ -n "${campagne}" ]

  run cli modifier-campagne --campagne "${campagne}" --nom "Suivi ENS Sainte-Baume" --annee 2027
  [ "${status}" -eq 0 ]

  # Relecture : le nouveau nom a bien remplacé l'ancien, il ne s'y est pas ajouté.
  run cli lister-campagnes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Suivi ENS Sainte-Baume"* ]]
  [[ "${output}" == *"2027"* ]]
  [[ "${output}" != *"#${campagne}  Suivi ENS  "* ]]

  run cli supprimer-campagne --campagne "${campagne}"
  [ "${status}" -eq 0 ]

  run cli lister-campagnes
  [ "${status}" -eq 0 ]
  [[ "${output}" == *"Aucune campagne enregistrée"* ]]
}

@test "modifier-campagne / supprimer-campagne sur une campagne inconnue : refus métier, exit 2 (#2355)" {
  run cli modifier-campagne --campagne 999999 --nom "Fantôme" --annee 2026
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"introuvable"* ]]

  run cli supprimer-campagne --campagne 999999
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"introuvable"* ]]
}

@test "rattacher-campagne sur un passage inconnu : refus métier, exit 2 (#2355)" {
  # Le rattachement lui-même demande un passage, que la CLI ne sait pas créer sans import : c'est le
  # refus qui est vérifiable ici au niveau processus. Le chemin nominal est couvert par
  # CliRattacherCampagneTest.
  run cli rattacher-campagne --passage 999999
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"introuvable"* ]]
}

@test "traiter-passages : une action inconnue est refusée par picocli, exit 2 (#2357)" {
  # Vu du vrai binaire : le convertisseur d'énumération est bien câblé, et son refus nomme les
  # valeurs attendues plutôt que de laisser deviner. Un test in-process ne voit pas le code de sortie.
  run cli traiter-passages --action tout-refaire --passage 1
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"preparer-depot"* ]]
}

@test "traiter-passages : un passage inconnu arrête tout, exit 2, sans pile (#2357)" {
  # Traiter dix-neuf nuits sur vingt en passant la vingtième sous silence serait le pire des deux
  # comportements : la commande refuse le lot entier et NOMME l'identifiant fautif.
  run cli traiter-passages --action preparer-depot --passage 999999
  [ "${status}" -eq 2 ]
  [[ "${output}" == *"999999"* ]]
  [[ "${output}" != *"Exception"* ]]
}

@test "traiter-passages : le refus ne parle pas du menu ☰, il donne une commande (#2357, ADR 2635)" {
  # Pendant CLI de la garde posée sur « reactiver » : un terminal n'a pas de menu.
  run cli traiter-passages --action preparer-depot --passage 999999
  [[ "${output}" != *"☰"* ]]
}
