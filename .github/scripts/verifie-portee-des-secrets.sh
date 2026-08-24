#!/usr/bin/env bash
# Garde « portée d'un secret de plateforme » (#4303, lot 1 du chantier #4291).
#
# ## Ce qu'elle empêche
#
# Un secret Vigie-Chiro posé dans l'`env:` d'un **job** n'est pas offert au pas qui en a besoin : il est
# offert à **tout** ce que le job exécute. Sur un job qui lance la suite de tests, cela veut dire la
# suite entière, pointée sur la production.
#
# Le chemin est court et aucun de ses trois pas ne se voit en lisant le YAML :
#
#   1. `ConnexionModule.jetonPonctuel()` lit `System.getenv("VIGIECHIRO_TOKEN")`, et ce jeton ponctuel
#      L'EMPORTE sur la connexion enregistrée ;
#   2. les forks surefire HÉRITENT de l'environnement du job - c'est ainsi que le job
#      `fuseau-alternatif` passe `TZ` ;
#   3. `ConnexionModule.urlDeBase()` vaut la PRODUCTION par défaut.
#
# La forme juste est donc l'`env:` d'un **pas**. C'est déjà celle d'`api-live.yml`, et rien ne
# l'imposait.
#
# ## Pourquoi ce garde et pas un rôle côté plateforme
#
# Une parade structurelle avait été envisagée : un compte rétrogradé au rôle `Lecteur`, pour que le
# SERVEUR refuse d'écrire plutôt que notre discipline. Elle n'existe pas. Le rôle est déclaré dans
# `ROLE_RULES` et **aucune route ne l'accepte** - `GET /sites`, `GET /participations`, `GET /donnees`
# et jusqu'au `GET /moi` portent tous `@requires_auth(roles='Observateur')`. Un jeton de `Lecteur` ne
# lirait rien.
#
# La lecture seule ne tient donc que par la PORTÉE du secret, et une propriété qui ne tient que par la
# discipline appelle un garde (ADR 4235 : le garde d'abord, l'abstraction ensuite).
#
# ## Ce qu'elle surveille, et pourquoi pas tout
#
# Les secrets dont le nom commence par `VIGIECHIRO_`, et eux seuls. `FLATPAK_GPG_KEY` et `WINGET_TOKEN`
# vivent dans des jobs qui n'exécutent aucun test du produit : les inclure ferait rougir là où le
# mécanisme ci-dessus n'existe pas, et une règle qui rougit sur du code juste apprend à ne plus lire sa
# sortie (ADR 3479). Le préfixe, plutôt qu'un nom, pour que le second secret du tournage connecté
# (#4304) soit couvert le jour où il naîtra, sans qu'on ait à y penser.
#
# ## Le second contrôle : `secrets: inherit` (#4349)
#
# Une déclaration nominale côté APPELÉ n'achète rien tant que l'appelant hérite. `release.yml` passait
# `secrets: inherit` à trois workflows appelés, dont un qui exécute les tests du produit : tous les
# secrets du dépôt - les deux clés Flatpak, `WINGET_TOKEN`, `DOCS_DEPLOY_TOKEN`, les deux jetons
# Vigie-Chiro - lui étaient offerts. Aucun n'était lu ; ce n'était pas une fuite, c'était une surface.
#
# Le raisonnement est celui de #2739, qui a retiré les droits d'écriture du plancher de `release.yml`
# pour les déclarer job par job : n'accorder que ce qui sert, là où ça sert.
#
# `GITHUB_TOKEN` n'entre pas dans ce compte : il est fourni d'office à un workflow appelé, sans
# héritage. Un appel qui ne lit que lui n'a donc rien à transmettre du tout.
#
# ## Ce qu'elle NE voit PAS, dit ici plutôt que supposé
#
# Elle lit du YAML. Un secret qu'un `run:` exporterait lui-même (`echo "X=..." >> "$GITHUB_ENV"`) lui
# échappe, et c'est un contournement possible. Elle ne juge pas non plus ce que le pas fait du secret
# une fois qu'il l'a : la portée est une condition nécessaire, pas une preuve.
#
# Usage : ./.github/scripts/verifie-portee-des-secrets.sh [--auto-test]
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

# Le PRÉFIXE, et non une liste de noms : le second secret du tournage connecté sera couvert sans
# qu'on ait à revenir ici.
SECRET = re.compile(r"secrets\.(VIGIECHIRO_[A-Za-z0-9_]*)")

fautives = []
heritages = []
legitimes = 0


def secrets_de(valeur):
    """Les secrets de plateforme cités quelque part sous `valeur`, quelle que soit sa forme."""
    return sorted(set(SECRET.findall(yaml.safe_dump(valeur, allow_unicode=True))))


for fichier in sorted(os.listdir(flux)):
    if not fichier.endswith((".yml", ".yaml")):
        continue
    with open(os.path.join(flux, fichier), encoding="utf-8") as f:
        contenu = yaml.safe_load(f)
    if not isinstance(contenu, dict):
        continue

    # ─── Le plancher du workflow : il descend dans TOUS les jobs, donc il est pire qu'un `env:` de job.
    for nom in secrets_de(contenu.get("env") or {}):
        fautives.append(f"{fichier} · `env:` du workflow · secrets.{nom}")

    jobs = contenu.get("jobs") or {}
    if not isinstance(jobs, dict):
        continue
    for identifiant, job in jobs.items():
        if not isinstance(job, dict):
            continue
        for nom in secrets_de(job.get("env") or {}):
            fautives.append(f"{fichier} · `env:` du job « {identifiant} » · secrets.{nom}")

        # `secrets: inherit` se lit comme la CHAÎNE « inherit », là où la forme juste est une table.
        # C'est ce qui permet de les distinguer sans deviner.
        if job.get("secrets") == "inherit":
            heritages.append(f"{fichier} · job « {identifiant} » · secrets: inherit")
        # ⚠️ Tout le reste du job est LÉGITIME et doit le rester : `secrets:` qui transmet à un
        # workflow appelé, `with:` d'un pas, `env:` d'un pas, interpolation dans un `run:`. Un garde
        # qui refuserait ces formes-là interdirait la seule façon juste de se servir d'un secret, et
        # se ferait contourner plutôt que corriger.
        pas = job.get("steps") or []
        if isinstance(pas, list):
            for etape in pas:
                if isinstance(etape, dict):
                    legitimes += len(secrets_de(etape))

if heritages:
    print(f"✗ {len(heritages)} appel(s) transmettant TOUT le trousseau du dépôt :")
    for h in heritages:
        print(f"   · {h}")
    print()
    print("  `secrets: inherit` passe au workflow appelé TOUS les secrets du dépôt, y compris ceux")
    print("  qu'il ne lit pas et ceux qui naîtront après. Sur un appelé qui exécute les tests du")
    print("  produit, c'est-à-dire du code qui change à chaque PR, la surface est celle du trousseau")
    print("  entier pour la commodité de ne pas écrire trois lignes.")
    print("  Les nommer : `secrets:` suivi de ce dont l'appelé se sert, et de rien d'autre.")
    print("  S'il ne lit que `GITHUB_TOKEN`, il n'y a RIEN à transmettre : celui-là est fourni d'office.")
    sys.exit(1)

if fautives:
    print(f"✗ {len(fautives)} secret(s) de plateforme posé(s) trop haut :")
    for f in fautives:
        print(f"   · {f}")
    print()
    print("  Un `env:` de job (ou de workflow) offre le secret à TOUT ce que le job exécute, suite de")
    print("  tests comprise. `ConnexionModule` lit `System.getenv`, les forks surefire héritent de")
    print("  l'environnement, et l'URL de base vaut la production : la suite entière serait armée")
    print("  face à la plateforme réelle, et non le seul pas visé.")
    print("  Le descendre dans l'`env:` du pas qui en a besoin.")
    sys.exit(1)

print(f"✓ Aucun secret de plateforme au-dessus d'un pas ({legitimes} usage(s) de pas relevé(s)).")
PY
}

auto_test() {
    local bac total=0 echecs=0 rouges=0
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN
    echo "AUTO-TEST"

    essai() { # <nom> <vert|rouge> <contenu du workflow>
        local nom="$1" attendu="$2" contenu="$3" obtenu=vert
        mkdir -p "$bac/.github/workflows"
        printf '%s\n' "$contenu" > "$bac/.github/workflows/essai.yml"
        FLUX="$bac/.github/workflows" verifier >/dev/null 2>&1 || obtenu=rouge
        total=$((total + 1))
        [ "$attendu" = rouge ] && rouges=$((rouges + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-56s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-56s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    essai "un secret de plateforme dans l env d un JOB est refusé" rouge \
'jobs:
  a:
    runs-on: ubuntu-latest
    env:
      VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
    steps:
      - run: ./mvnw test'

    essai "dans l env du WORKFLOW, refusé aussi"                  rouge \
'env:
  VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - run: ./mvnw test'

    # Le préfixe, et non un nom : le second secret du tournage connecté n aura pas le même nom.
    essai "un AUTRE secret de plateforme est couvert par le préfixe" rouge \
'jobs:
  a:
    runs-on: ubuntu-latest
    env:
      VIGIECHIRO_TOKEN_TOURNAGE: ${{ secrets.VIGIECHIRO_TOKEN_TOURNAGE }}
    steps:
      - run: ./mvnw test'

    # ⚠️ Contrôle négatif, et c est le cas qui empêche ce garde de tout refuser : la forme JUSTE.
    # Sans lui, la règle interdirait la seule manière correcte de se servir du secret.
    essai "dans l env d un PAS, la forme juste passe"             vert \
'jobs:
  a:
    runs-on: ubuntu-latest
    steps:
      - env:
          VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}
        run: ./mvnw -Papi-live test'

    # Second contrôle négatif : transmettre nommément à un workflow appelé n est pas une portée de job.
    essai "un secret transmis à un workflow appelé passe"         vert \
'jobs:
  a:
    uses: ./.github/workflows/appele.yml
    secrets:
      VIGIECHIRO_TOKEN: ${{ secrets.VIGIECHIRO_TOKEN }}'

    # Troisième contrôle négatif : la règle ne parle QUE des secrets de plateforme. Un secret de
    # publication au plancher d un job qui n exécute aucun test n a pas le mécanisme qu on garde ici.
    essai "un secret hors plateforme au niveau du job passe"      vert \
'jobs:
  a:
    runs-on: ubuntu-latest
    env:
      GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      WINGET_TOKEN: ${{ secrets.WINGET_TOKEN }}
    steps:
      - run: gh release list'

    # Le contrôle de #4349 : l'appelant qui hérite de tout.
    essai "un appel qui hérite de TOUT le trousseau est refusé"    rouge \
'jobs:
  a:
    uses: ./.github/workflows/appele.yml
    secrets: inherit'

    # Et son contrôle négatif : un appel qui ne transmet RIEN est la forme juste quand l'appelé ne
    # lit que `GITHUB_TOKEN`, fourni d'office. Le refuser aurait poussé à écrire `inherit` par dépit.
    essai "un appel qui ne transmet aucun secret passe"            vert \
'jobs:
  a:
    uses: ./.github/workflows/appele.yml
    with:
      version: v1.0.0'

    echo
    echo "${total} cas, dont ${rouges} qui DOIVENT rougir."
    if [ "$echecs" -ne 0 ]; then
        echo "AUTO-TEST EN ÉCHEC (${echecs}) : ne pas se fier au verdict de ce garde."
        return 1
    fi
    echo "Auto-test concluant."
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

verifier
