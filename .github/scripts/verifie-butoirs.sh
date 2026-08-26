#!/usr/bin/env bash
# Garde-fou : tout job de workflow porte un `timeout-minutes`.
#
# Pourquoi cette garde existe. Un job sans butoir que GitHub laisse courir SIX HEURES a bloqué
# une PR pendant 4 h 15 sur un `apt-get install` de police, en `-qq` - donc sans une ligne de log
# pour dire ce qui traînait. Le butoir de 12 minutes posé sur `banc-filme` (#3883) avait été
# apprécié pour ce seul job ; vingt-six autres n'en avaient aucun, dont ceux qui publient.
#
# Un job qui échoue apprend quelque chose. Un job qui pend n'apprend rien ET retient tout le monde.
#
# Ce que cette garde ne dit PAS : que le butoir soit bien choisi. Un butoir trop large ne protège
# de rien, un butoir trop serré rend le rouge illisible. Les valeurs viennent d'une mesure - environ
# quatre fois le maximum observé sur les quarante derniers runs réussis - et se révisent en mesurant
# de nouveau, pas en discutant.
#
# Exit 0 si tout job en porte un, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé>
        cas=$((cas + 1))
        [ "$1" != 0 ] && rouges=$((rouges + 1))
        BUTOIRS_RACINE="$bac" bash "$ICI/$(basename "${BASH_SOURCE[0]}")" >/dev/null 2>&1
        code=$?
        if [ "$code" = "$1" ]; then
            printf '  [OK   ] %-52s -> %s\n' "$2" "$code"
        else
            printf '  [ÉCHEC] %-52s -> %s (attendu %s)\n' "$2" "$code" "$1"
            echecs=$((echecs + 1))
        fi
    }
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' EXIT
    mkdir -p "$bac/.github/workflows"

    echo "AUTO-TEST"
    cat > "$bac/.github/workflows/bon.yml" <<'YML'
name: bon
on: [push]
jobs:
  un-job:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - run: echo ok
YML
    verifie 0 "un job avec butoir est accepté"

    cat > "$bac/.github/workflows/nu.yml" <<'YML'
name: nu
on: [push]
jobs:
  sans-butoir:
    runs-on: ubuntu-latest
    steps:
      - run: echo ok
YML
    verifie 1 "un job SANS butoir est refusé"
    rm "$bac/.github/workflows/nu.yml"
    verifie 0 "le dépôt redevient conforme quand on le retire"

    # Un job qui DÉLÈGUE (`uses:`) n'a pas de butoir à lui : c'est le workflow appelé qui le porte.
    cat > "$bac/.github/workflows/delegue.yml" <<'YML'
name: delegue
on: [push]
jobs:
  appelant:
    uses: ./.github/workflows/bon.yml
YML
    verifie 0 "un job qui DÉLÈGUE n est pas exigé d en porter un"

    rm -f "$bac/.github/workflows/"*.yml
    verifie 1 "sans aucun workflow, la garde REFUSE au lieu de passer"

    echo ""
    echo "$cas cas, dont $rouges qui DOIVENT rougir."
    [ "$echecs" -eq 0 ] && { echo "Auto-test concluant."; exit 0; }
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de cette garde."
    exit 1
fi

RACINE="${BUTOIRS_RACINE:-$(cd "$ICI/../.." && pwd)}"
WORKFLOWS="$RACINE/.github/workflows"

if [ ! -d "$WORKFLOWS" ]; then
    echo "✗ $WORKFLOWS introuvable : rien ne peut être vérifié."
    exit 1
fi

python3 - "$WORKFLOWS" <<'PY'
import glob, os, sys

try:
    import yaml
except ImportError:
    print("✗ PyYAML absent : la garde ne peut pas lire les workflows.")
    sys.exit(1)

dossier = sys.argv[1]
fichiers = sorted(glob.glob(os.path.join(dossier, "*.yml")))
if not fichiers:
    print("✗ aucun workflow trouvé : la garde ne peut rien affirmer.")
    sys.exit(1)

nus = []
total = 0
for chemin in fichiers:
    with open(chemin, encoding="utf-8") as f:
        contenu = yaml.safe_load(f)
    for nom, job in ((contenu or {}).get("jobs") or {}).items():
        if not isinstance(job, dict):
            continue
        # Un job qui délègue à un workflow réutilisable n'a pas de butoir à lui.
        if "uses" in job:
            continue
        total += 1
        if "timeout-minutes" not in job:
            nus.append(f"{os.path.basename(chemin)} / {nom}")

if nus:
    print(f"✗ {len(nus)} job(s) sans `timeout-minutes` :")
    for n in nus:
        print(f"   · {n}")
    print()
    print("  Sans butoir, GitHub laisse courir SIX HEURES. Un job qui échoue apprend quelque chose ;")
    print("  un job qui pend n'apprend rien et retient tout le monde.")
    sys.exit(1)

print(f"✓ Les {total} job(s) portent un butoir.")
PY
