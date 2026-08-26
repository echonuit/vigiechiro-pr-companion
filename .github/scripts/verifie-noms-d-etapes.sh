#!/usr/bin/env bash
# Garde-fou : un nom d'étape ou de job dit à l'écran ce qu'il écrit dans le fichier.
#
# En YAML, un `#` précédé d'une espace ouvre un commentaire, y compris au milieu d'un scalaire
# non cité. Un nom d'étape écrit
#
#     - name: Auto-test des scripts ADR (bloquant, #2467)
#
# vaut donc `Auto-test des scripts ADR (bloquant,` : le numéro d'issue est mangé avant que GitHub
# ne voie quoi que ce soit. Six étapes de ce dépôt étaient dans ce cas (#4255), quatre dans
# `lint.yml` et deux dans `maven.yml`.
#
# Ce qui rend le défaut durable : le fichier a raison. On relit le YAML, on y voit le numéro,
# et on conclut que c'est l'interface qui coupe à l'affichage. Rien ne rougit, rien ne prévient, et
# ce qu'on perd est précisément ce qui sert à retrouver POURQUOI une étape existe.
#
# Le remède tient en deux guillemets :
#
#     - name: "Auto-test des scripts ADR (bloquant, #2467)"
#
# Ce que la garde NE regarde PAS : les `#` non cités ailleurs que dans un `name:`. Un commentaire
# au bout d'un `run:` ou d'un `if:` est légitime, et le confondre avec celui-ci ferait refuser des
# formes justes. Elle ne juge pas non plus la formulation d'un nom : on cite, on ne réécrit pas.
#
# Exit 0 si aucun nom ne perd de texte, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
FLUX="${FLUX:-$RACINE/.github/workflows}"

verifier() {
    python3 - "$FLUX" <<'PY'
import glob
import os
import re
import sys

try:
    import yaml
except ImportError:
    print("❌ PyYAML est absent : la garde ne peut rien analyser.")
    print("   C'est la GARDE qui est en cause, pas les workflows.")
    sys.exit(1)

flux = sys.argv[1]
fichiers = sorted(glob.glob(os.path.join(flux, "*.yml")) + glob.glob(os.path.join(flux, "*.yaml")))
if not fichiers:
    print(f"❌ Aucun workflow sous {flux}")
    print("   La garde ne peut rien confronter : chemin déplacé ?")
    sys.exit(1)

# Une ligne `name:` dont la valeur n'est ni citée ni un scalaire de bloc.
LIGNE = re.compile(r"^\s*(?:-\s+)?name:[ \t]+(?![|>&*!\"'])(.+?)[ \t]*$")

ecarts = []
total = 0

for chemin in fichiers:
    texte = open(chemin, encoding="utf-8").read()
    try:
        arbre = yaml.safe_load(texte)
    except yaml.YAMLError as erreur:
        print(f"❌ {os.path.basename(chemin)} : YAML illisible ({erreur.__class__.__name__})")
        sys.exit(1)
    if not isinstance(arbre, dict):
        continue

    # Les noms que YAML a réellement retenus. Sert de recoupement : sans lui, une ligne de shell
    # dans un `run: |` qui ressemble à `name: quelque chose #x` serait prise pour un nom d'étape.
    lus = set()
    if isinstance(arbre.get("name"), str):
        lus.add(arbre["name"])
    for job in (arbre.get("jobs") or {}).values():
        if not isinstance(job, dict):
            continue
        if isinstance(job.get("name"), str):
            lus.add(job["name"])
        for etape in job.get("steps") or []:
            if isinstance(etape, dict) and isinstance(etape.get("name"), str):
                lus.add(etape["name"])

    for numero, ligne in enumerate(texte.splitlines(), 1):
        trouve = LIGNE.match(ligne)
        if not trouve:
            continue
        brut = trouve.group(1).strip()
        try:
            retenu = yaml.safe_load(brut)
        except yaml.YAMLError:
            retenu = None
        if not isinstance(retenu, str):
            continue
        retenu = retenu.strip()
        if retenu == brut:
            continue
        # Recoupement : ce que YAML a gardé doit être un nom que l'arbre porte vraiment.
        if retenu not in lus:
            continue
        total += 1
        ecarts.append((os.path.basename(chemin), numero, brut, retenu))

if ecarts:
    print(f"❌ {len(ecarts)} nom(s) perdent du texte à l'analyse YAML :")
    for fichier, numero, brut, retenu in ecarts:
        print(f"   {fichier}:{numero}")
        print(f"      écrit   : {brut}")
        print(f"      retenu  : {retenu}")
    print()
    print("   Un `#` précédé d'une espace ouvre un commentaire. Citer le nom entre guillemets :")
    print('      - name: "… (bloquant, #1234)"')
    sys.exit(1)

print("✓ Aucun nom d'étape ou de job ne perd de texte à l'analyse YAML.")
PY
}

auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN
    echo "AUTO-TEST"

    essai() { # <nom> <vert|rouge> <contenu du workflow>
        local nom="$1" attendu="$2" contenu="$3" obtenu=vert
        mkdir -p "$bac/.github/workflows"
        printf '%s\n' "$contenu" > "$bac/.github/workflows/essai.yml"
        FLUX="$bac/.github/workflows" verifier >/dev/null 2>&1 || obtenu=rouge
        total=$((total + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-62s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-62s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    # Le cas de #4255, réduit : le numéro d'issue disparaît avant GitHub.
    essai "un nom d etape non cite perd ce qui suit le diese" rouge \
'on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Auto-test des scripts ADR (bloquant, #2467)
        run: echo'

    # Deux guillemets suffisent, et c est tout ce que la garde demande.
    essai "le meme nom, cite, passe" vert \
'on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: "Auto-test des scripts ADR (bloquant, #2467)"
        run: echo'

    # Un nom de JOB est atteint de la meme facon, et la garde le voit aussi.
    essai "un nom de job non cite est vu lui aussi" rouge \
'on: [push]
jobs:
  j:
    name: Portail qualite (PMD, #3300)
    runs-on: ubuntu-latest
    steps:
      - run: echo'

    # Sans espace avant le diese, YAML ne coupe rien : refuser ici serait refuser une forme juste.
    essai "un diese colle ne mange rien, la forme reste acceptee" vert \
'on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Auto-test des scripts ADR (bloquant,#2467)
        run: echo'

    # Un commentaire au bout d un `run:` est legitime : la garde ne regarde que les `name:`.
    essai "un commentaire hors d un name reste permis" vert \
'on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Une etape ordinaire
        run: echo bonjour  # ceci est un vrai commentaire'

    # Une ligne de shell qui ressemble a un `name:` ne doit pas etre prise pour un nom d etape.
    essai "une ligne de shell qui ressemble a un name est ignoree" vert \
'on: [push]
jobs:
  j:
    runs-on: ubuntu-latest
    steps:
      - name: Une etape ordinaire
        run: |
          echo "name: quelque chose (bloquant, #2467)"'

    echo
    echo "$total cas, dont 2 qui DOIVENT rougir."
    if [ "$echecs" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de ce garde."
        return 1
    fi
    echo "Auto-test concluant."
}

case "${1:-}" in
    --auto-test) auto_test ;;
    *) verifier ;;
esac
