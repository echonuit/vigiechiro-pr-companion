#!/bin/bash
# ============================================================
# Helper d'autograding : grade UN test à partir du rapport
# Surefire déjà généré (pas d'invocation Maven ici).
#
# Usage : ./grade-test.sh <FQCN> <method>
#   FQCN   : nom complet de la classe de test
#            (ex: fr.univ_amu.iut.exercice1.PremiereFenetreTest)
#   method : nom de la méthode de test (sans parenthèses)
#            (ex: laFenetreEstVisible)
#
# Codes de sortie :
#   0     test exécuté et passé
#   != 0  test absent du rapport, @Disabled, échoué ou erroré
#
# Pourquoi ne PAS lancer Maven ici ?
# ----------------------------------
# Le workflow Classroom contient un step "Run impacted tests" qui
# fait UN seul `./mvnw test` (filtré sur les paquets impactés par
# le push, avec rapports cachés pour les paquets inchangés). On
# économise ~10x les minutes Actions par rapport à l'ancienne
# stratégie d'un `./mvnw test` par méthode (chaque démarrage JVM
# coûtait ~10-15s). Ici on lit juste le XML déjà sur disque.
#
# La sémantique reste la même : un test @Disabled est marqué
# `<skipped/>` dans le XML et compte comme un échec.
# ============================================================

set -e

fqcn=$1
method=$2

if [ -z "$fqcn" ] || [ -z "$method" ]; then
    echo "Usage: $0 <FQCN> <method>" >&2
    exit 2
fi

xml="target/surefire-reports/TEST-${fqcn}.xml"

if [ ! -f "$xml" ]; then
    echo "Rapport XML absent : $xml (le step 'Run impacted tests' n'a pas couvert cette classe)" >&2
    exit 1
fi

python3 - "$xml" "$method" <<'PY'
import sys, xml.etree.ElementTree as ET

xml_path, method = sys.argv[1], sys.argv[2]

try:
    root = ET.parse(xml_path).getroot()
except ET.ParseError as e:
    print(f"XML invalide : {xml_path} ({e})", file=sys.stderr)
    sys.exit(1)

# JUnit 5 ajoute parfois les types de paramètres au nom du testcase
# (ex: "laFenetreEstVisible(FxRobot)"). On compare avant la première
# parenthèse.
for tc in root.iter("testcase"):
    name = tc.get("name", "")
    if name.split("(", 1)[0] == method:
        for child in tc:
            tag = child.tag.rsplit("}", 1)[-1]
            if tag in ("skipped", "failure", "error"):
                print(f"Test {method} : {tag}", file=sys.stderr)
                sys.exit(1)
        sys.exit(0)

print(f"Méthode {method} absente de {xml_path}", file=sys.stderr)
sys.exit(1)
PY
