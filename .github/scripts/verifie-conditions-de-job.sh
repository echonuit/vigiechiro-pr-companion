#!/usr/bin/env bash
# Garde-fou : la condition d'un job qui dépend d'un `needs` porte une FONCTION D'ÉTAT.
#
# Pourquoi cette garde existe, et ce qu'elle a coûté. Dans GitHub Actions, la propagation du
# « sauté » est TRANSITIVE : un job dont un ancêtre a été sauté est sauté à son tour, sauf si son
# `if:` contient une fonction d'état (`always()`, `cancelled()`, `success()`, `failure()`). Sans
# elle, la condition écrite est implicitement enveloppée en `success() && (...)`, et ce `success()`
# porte sur TOUT le graphe amont, pas seulement sur le job nommé dans le `needs`.
#
# Autrement dit : la condition qu'on croit être la porte n'est jamais évaluée. Elle ne rougit pas,
# elle ne lève pas, elle ne s'exécute simplement pas.
#
# Mesuré sur le run 32224321373 de `release`, le 2026-08-19 (#4079). Le train du mercredi a créé le
# tag `v2.186.0`, l'entrée de CHANGELOG et la Release GitHub, puis n'a rien publié : `installers` et
# `publish` étaient gardés par
#
#     if: needs.release.outputs.tag != ''
#
# une expression nue. Le job `release` avait gagné un `needs` vers deux gardes (#3770), dont
# `contournement-declare` qui ne tourne QUE sur `workflow_dispatch` avec une raison écrite : sur le
# train programmé, il est toujours sauté. Le saut s'est propagé jusqu'aux installeurs, qui n'ont
# jamais tourné. La sortie `tag` valait pourtant bien `v2.186.0` - le journal du run le montre.
#
# Ce qui a rendu le défaut invisible : le run est VERT. Aucun job n'échoue, la Release existe, et
# seul l'absence d'assets trahit que la chaîne s'est arrêtée au milieu. La version 2.186.0 n'a
# jamais été livrée et personne ne l'a vu pendant huit jours.
#
# La forme juste nomme l'état qu'elle attend :
#
#     if: ${{ !cancelled() && needs.release.result == 'success' && needs.release.outputs.tag != '' }}
#
# `needs.<job>.result == 'success'` n'est pas décoratif : `!cancelled()` seul laisserait passer un
# ancêtre en ÉCHEC.
#
# Ce que la garde NE regarde PAS : un job sans `if:` du tout. Être sauté avec son ancêtre est alors
# le comportement attendu, et l'exiger autrement reviendrait à refuser une forme juste.
#
# Exit 0 si aucune condition exposée, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
FLUX="${FLUX:-$RACINE/.github/workflows}"

verifier() {
    python3 - "$FLUX" <<'PY'
import os
import re
import sys

import yaml

flux = sys.argv[1]
# Les quatre fonctions d'état de GitHub Actions. Leur seule présence dans un `if:` suffit à
# reprendre la main sur la propagation du « sauté » ; on ne juge pas ce qu'elles y font.
ETAT = re.compile(r"\b(always|cancelled|success|failure)\s*\(\s*\)")
exposes = []
total = 0

for fichier in sorted(os.listdir(flux)):
    if not fichier.endswith((".yml", ".yaml")):
        continue
    with open(os.path.join(flux, fichier), encoding="utf-8") as f:
        doc = yaml.safe_load(f)
    if not isinstance(doc, dict):
        continue
    jobs = doc.get("jobs") or {}
    if not isinstance(jobs, dict):
        continue

    def besoins(nom):
        spec = jobs.get(nom) or {}
        n = spec.get("needs") or [] if isinstance(spec, dict) else []
        return [n] if isinstance(n, str) else list(n)

    def ancetres(nom, vus=None):
        vus = vus if vus is not None else set()
        for parent in besoins(nom):
            if parent not in vus:
                vus.add(parent)
                ancetres(parent, vus)
        return vus

    for nom, spec in jobs.items():
        if not isinstance(spec, dict):
            continue
        condition = spec.get("if")
        # Un job sans `if:` est hors sujet : être sauté avec son ancêtre est ce qu'on attend.
        if condition is None or not besoins(nom):
            continue
        total += 1
        if ETAT.search(str(condition)):
            continue
        # Un ancêtre porteur d'un `if:` est un ancêtre qui PEUT être sauté.
        sautables = sorted(
            a for a in ancetres(nom)
            if isinstance(jobs.get(a), dict) and jobs[a].get("if") is not None
        )
        if sautables:
            exposes.append(
                f"{fichier} · job « {nom} » : sa condition ne porte aucune fonction d'état, "
                f"et il descend de {', '.join('« ' + a + ' »' for a in sautables)}"
            )

if exposes:
    print(f"✗ {len(exposes)} condition(s) de job exposée(s) à la propagation du « sauté » :")
    for e in exposes:
        print(f"   · {e}")
    print()
    print("  GitHub saute un job dès qu'un ancêtre a été sauté, SAUF si son `if:` porte une fonction")
    print("  d'état. Sans elle, la condition est enveloppée en `success() && (...)` sur tout le")
    print("  graphe amont : la porte qu'on croit tenir n'est jamais évaluée, et le run reste VERT.")
    print("  Écrire par exemple :")
    print("     if: ${{ !cancelled() && needs.X.result == 'success' && <la condition voulue> }}")
    sys.exit(1)

print(f"✓ Les {total} condition(s) de job appuyées sur un `needs` nomment l'état qu'elles attendent.")
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
            printf '  [OK   ] %-58s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-58s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    # Le cas de #4079, réduit : une garde qui peut se sauter, un job au milieu qui s'en protège,
    # et un job en aval qui ne s'en protège pas. C'est l'aval qui ne tournera jamais.
    essai "une condition nue sous un ancetre sautable est refusee" rouge \
'on: [push]
jobs:
  garde:
    if: github.event_name == '"'"'workflow_dispatch'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  milieu:
    needs: [garde]
    if: ${{ !cancelled() }}
    runs-on: ubuntu-latest
    outputs:
      tag: ${{ steps.t.outputs.v }}
    steps:
      - id: t
        run: echo "v=x" >> "$GITHUB_OUTPUT"
  aval:
    needs: [milieu]
    if: needs.milieu.outputs.tag != '"'"''"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo'

    # Le saut se propage sur TOUTE la chaine : deux crans plus bas, le defaut est le meme.
    essai "la propagation transitive est vue a deux crans"        rouge \
'on: [push]
jobs:
  garde:
    if: github.event_name == '"'"'workflow_dispatch'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  a:
    needs: [garde]
    if: ${{ always() }}
    runs-on: ubuntu-latest
    steps:
      - run: echo
  b:
    needs: [a]
    if: ${{ always() }}
    runs-on: ubuntu-latest
    steps:
      - run: echo
  c:
    needs: [b]
    if: github.ref == '"'"'refs/heads/main'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo'

    essai "la forme juste passe"                                  vert \
'on: [push]
jobs:
  garde:
    if: github.event_name == '"'"'workflow_dispatch'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [garde]
    if: ${{ !cancelled() && needs.garde.result == '"'"'success'"'"' }}
    runs-on: ubuntu-latest
    steps:
      - run: echo'

    # Le cas qui empeche la garde de tout refuser : sans ancetre sautable, une condition nue est
    # une forme JUSTE. La refuser ferait contourner la garde au lieu de la corriger.
    essai "sans ancetre sautable, une condition nue est permise"   vert \
'on: [push]
jobs:
  amont:
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [amont]
    if: github.ref == '"'"'refs/heads/main'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo'

    # Et celui qui garde le comportement PAR DEFAUT : un job sans `if:` est saute avec son
    # ancetre, et c'est ce qu'on attend de lui. L'exiger autrement serait un faux positif.
    essai "un job sans condition n est pas concerne"               vert \
'on: [push]
jobs:
  garde:
    if: github.event_name == '"'"'workflow_dispatch'"'"'
    runs-on: ubuntu-latest
    steps:
      - run: echo
  aval:
    needs: [garde]
    runs-on: ubuntu-latest
    steps:
      - run: echo'

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
