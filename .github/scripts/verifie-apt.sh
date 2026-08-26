#!/usr/bin/env bash
# Garde-fou : aucun workflow n'appelle `apt-get` directement.
#
# Pourquoi. Trois étapes de trois workflows ont pendu le même jour sur la même ligne -
# `apt-get update` - jusqu'au butoir de leur job, y compris sur `main`. Le miroir du runner rendait
# « Ign: » sur toutes ses sources et APT attendait l'archive amont sans rien dire. Chacune portait un
# nom qui parlait d'autre chose : « Aligner la police système », « Installer de quoi afficher et
# filmer », « E2E CLI (bats) sur le fat-jar ».
#
# Le remède a été appliqué à UNE des trois, et les deux autres ont continué de pendre le lendemain.
# C'est le motif qu'on connaît : une leçon apprise à un seul endroit. D'où une porte unique,
# `installer-paquets.sh`, et cette garde pour qu'on ne la contourne pas.
#
# Ce que cette garde ne dit PAS : que la porte suffise. Elle borne et reprend ; un runner sans
# réseau échouera quand même - en une minute et en le disant, au lieu d'immobiliser une PR trois
# quarts d'heure.
#
# Exit 0 si aucun appel direct, 1 sinon (détails sur stdout).
set -uo pipefail

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé>
        cas=$((cas + 1))
        [ "$1" != 0 ] && rouges=$((rouges + 1))
        APT_RACINE="$bac" bash "$ICI/$(basename "${BASH_SOURCE[0]}")" >/dev/null 2>&1
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
    # La fixture de référence porte AUSSI son cache : depuis que la garde vérifie le câblage, une
    # installation sans cache est une faute, et un exemple « bon » qui n'en aurait pas serait faux.
    cat > "$bac/.github/workflows/bon.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - env:
          APT_CACHE: /tmp/c
        run: bash .github/scripts/installer-paquets.sh bats
YML
    verifie 0 "passer par la porte est accepté"

    printf 'jobs:\n  a:\n    steps:\n      - run: sudo apt-get install -y bats\n' \
        > "$bac/.github/workflows/nu.yml"
    verifie 1 "un apt-get direct est refusé"
    rm "$bac/.github/workflows/nu.yml"
    verifie 0 "le dépôt redevient conforme quand on le retire"

    # Un `apt-get` cité dans un COMMENTAIRE explique le défaut : l'interdire pousserait à ne plus
    # l'expliquer, ce qui est le contraire du but.
    printf 'jobs:\n  a:\n    # un apt-get nu pendait ici avant #4031\n    steps:\n      - run: echo ok\n' \
        > "$bac/.github/workflows/commente.yml"
    verifie 0 "un apt-get en COMMENTAIRE reste permis"

    # --- le cache, et ce qui le rend inutile sans le dire ---
    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - env:
          APT_CACHE: /tmp/c
        run: bash .github/scripts/installer-paquets.sh bats
YML
    verifie 0 "un cache branché est accepté"

    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - run: bash .github/scripts/installer-paquets.sh bats
YML
    verifie 1 "une installation SANS APT_CACHE est refusée"

    # La décision elle-même se garde. Sans ce cas, quelqu'un basculerait les polices sur l'action
    # rapide pour gagner vingt secondes, et les aperçus rendraient dans un repli sans que rien ne
    # rougisse.
    # L'auto-test de la porte n'installe rien : lui réclamer un cache ferait rougir la CI sur une
    # étape qui ne télécharge pas un octet.
    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - run: bash .github/scripts/installer-paquets.sh --auto-test
YML
    verifie 0 "l auto-test de la porte n exige aucun cache"

    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: awalsh128/cache-apt-pkgs-action@abc
        with:
          packages: fonts-noto-core
YML
    verifie 1 "une police par l action de cache est refusée"

    # ffmpeg est justement l'exemple qui a coûté cinq cas rouges : il TRAÎNE dix paquets de
    # polices. Ce cas garde la leçon.
    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: awalsh128/cache-apt-pkgs-action@abc
        with:
          packages: ffmpeg xdotool
YML
    verifie 1 "ffmpeg par l action de cache est refusé"

    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: awalsh128/cache-apt-pkgs-action@abc
        with:
          packages: bats xdotool
YML
    verifie 0 "des paquets sans post-install : accepté"

    cat > "$bac/.github/workflows/cache.yml" <<'YML'
jobs:
  a:
    steps:
      - uses: actions/cache@abc
      - uses: actions/cache@abc
      - env:
          APT_CACHE: /tmp/c
        run: bash .github/scripts/installer-paquets.sh bats
YML
    verifie 1 "DEUX caches dans un job sont refusés"
    rm "$bac/.github/workflows/cache.yml"

    rm -f "$bac/.github/workflows/"*.yml
    verifie 1 "sans aucun workflow, la garde REFUSE au lieu de passer"

    echo ""
    echo "$cas cas, dont $rouges qui DOIVENT rougir."
    [ "$echecs" -eq 0 ] && { echo "Auto-test concluant."; exit 0; }
    echo "AUTO-TEST EN ÉCHEC ($echecs) : ne pas se fier au verdict de cette garde."
    exit 1
fi

RACINE="${APT_RACINE:-$(cd "$ICI/../.." && pwd)}"
WORKFLOWS="$RACINE/.github/workflows"

if [ ! -d "$WORKFLOWS" ]; then
    echo "✗ $WORKFLOWS introuvable : rien ne peut être vérifié."
    exit 1
fi

fichiers=$(find "$WORKFLOWS" -maxdepth 1 -name '*.yml' | sort)
if [ -z "$fichiers" ]; then
    echo "✗ aucun workflow trouvé : la garde ne peut rien affirmer."
    exit 1
fi

# On retire la PROSE avant de chercher : commentaires et `name:` d'étape. Un `apt-get` cité là
# explique le défaut ou nomme la garde ; l'interdire pousserait à ne plus l'expliquer.
#
# Ce cas n'est pas théorique : la première version se refusait ELLE-MÊME, sur le nom de sa propre
# étape dans `lint.yml` - « Aucun workflow n appelle apt-get directement ». Un garde qui se compte
# parmi ses fautes, comme le compteur d'exigences ce matin.
fautes=$(printf '%s\n' "$fichiers" | while IFS= read -r f; do
    sed -e 's/#.*$//' -e 's/^[[:space:]]*-\{0,1\}[[:space:]]*name:.*$//' "$f" \
        | grep -n "apt-get" | sed "s#^#$(basename "$f"):#"
done)

if [ -n "$fautes" ]; then
    echo "✗ appel(s) direct(s) à apt-get dans un workflow :"
    printf '%s\n' "$fautes" | sed 's/^/   · /'
    echo ""
    echo "  Passer par .github/scripts/installer-paquets.sh : il borne les délais et reprend les"
    echo "  téléchargements coupés. Trois étapes ont pendu jusqu'au butoir de leur job, sur main"
    echo "  comme sur les PR, faute de cette porte."
    exit 1
fi

# Le cache ne sert que s'il est BRANCHÉ. Vérifié à la main une fois, il s'est révélé faux à deux
# endroits sur six : un job portait deux caches sur le même chemin - ils se seraient écrasés - et une
# étape d'installation n'avait pas la variable, donc téléchargeait tout en ayant l'air cachée.
python3 - "$WORKFLOWS" <<'PY'
import glob, os, sys

try:
    import yaml
except ImportError:
    print("✗ PyYAML absent : la garde ne peut pas lire les workflows.")
    sys.exit(1)

# Les paquets dont le POST-INSTALL fait le travail. `awalsh128/cache-apt-pkgs-action` restaure
# des fichiers ; elle ne garantit pas l'exécution des scripts `postinst`. Pour ceux-là, le gain de
# vitesse s'échangerait contre un défaut muet :
#
#   fonts-*   `fc-cache` n'est pas rejoué -> les aperçus rendent dans une police de REPLI, et rien
#             ne rougit. C'est précisément la famille de faux que ce dépôt traque.
#   flatpak*  services et alternatives.
#   ffmpeg    AJOUTÉ APRÈS COUP, et c'est une leçon payée. Je l'avais rangé parmi les « paquets de
#             fichiers » en regardant son nom ; sa FERMETURE DE DÉPENDANCES tire dix paquets de
#             polices (fonts-droid-fallback, fonts-noto-mono…). Le premier run qui a trouvé le cache
#             a fait tomber cinq cas du banc de recette - tous ceux qui écrivent du texte dans une
#             vidéo, `drawtext` cherchant sa police par fontconfig. Le run précédent, cache froid,
#             passait : la panne n'apparaît qu'au SECOND passage.
#
# La règle qu'il faut en retenir : ce qui compte n'est pas le paquet demandé, c'est ce qu'il TRAÎNE.
# Dans le doute, la porte - on perd le dépaquetage, on garde un système réellement configuré.
POST_INSTALL_COMPTE = ("fonts-", "flatpak", "ffmpeg")

ecarts = []
for chemin in sorted(glob.glob(os.path.join(sys.argv[1], "*.yml"))):
    with open(chemin, encoding="utf-8") as f:
        contenu = yaml.safe_load(f)
    for nomjob, job in ((contenu or {}).get("jobs") or {}).items():
        if not isinstance(job, dict):
            continue
        etapes = job.get("steps") or []
        nom = f"{os.path.basename(chemin)} / {nomjob}"

        # AVANT le « continue » ci-dessous : un job peut n'employer que l'action de cache, sans
        # aucune installation par la porte - et c'est justement là qu'une police mal placée
        # passerait. La première version de cette règle vivait après, donc ne s'exécutait jamais
        # pour ces jobs-là ; son cas restait vert.
        for etape in etapes:
            if not isinstance(etape, dict) or "awalsh128/cache-apt-pkgs-action" not in str(etape.get("uses", "")):
                continue
            demandes = str((etape.get("with") or {}).get("packages", "")).split()
            risques = [q for q in demandes if q.startswith(POST_INSTALL_COMPTE)]
            if risques:
                ecarts.append(
                    f"{nom} : {', '.join(risques)} passe(nt) par l'action de cache, qui n'exécute pas "
                    f"les scripts post-installation - à installer par installer-paquets.sh")

        # `--auto-test` n'installe RIEN : il éprouve la porte. L'exiger d'un cache ferait rougir la
        # CI sur une étape qui ne télécharge pas un octet - un garde qui accuse là où il n'y a rien.
        installs = [
            e
            for e in etapes
            if isinstance(e, dict)
            and "installer-paquets.sh" in str(e.get("run", ""))
            and "--auto-test" not in str(e.get("run", ""))
        ]
        if not installs:
            continue
        caches = [e for e in etapes if isinstance(e, dict) and "actions/cache@" in str(e.get("uses", ""))]
        if len(caches) != 1:
            ecarts.append(f"{nom} : {len(caches)} cache(s) pour {len(installs)} installation(s) - il en faut UN, partagé")
        sans = [e for e in installs if not (e.get("env") or {}).get("APT_CACHE")]
        if sans:
            ecarts.append(f"{nom} : {len(sans)} installation(s) sans APT_CACHE - elles retéléchargent tout")

if ecarts:
    print("✗ le cache APT est mal branché :")
    for e in ecarts:
        print(f"   · {e}")
    print("")
    print("  Un cache qui a l'air d'un cache et n'en est pas coûte le temps qu'il prétend gagner.")
    sys.exit(1)
PY
etat_cache=$?
[ "$etat_cache" -ne 0 ] && exit 1

echo "✓ Aucun appel direct à apt-get, et le cache est branché : les $(printf '%s\n' "$fichiers" | wc -l) workflows passent par la porte."
