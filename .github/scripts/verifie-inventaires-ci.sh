#!/usr/bin/env bash
# Garde des inventaires que la CI tient sur elle-même (#3794, lot 2 de #3802).
#
# ## Ce qu'elle empêche
#
# Ce dépôt exige de son **produit** que ses inventaires soient prouvés : commandes CLI contre
# `dev-docs/cli.md` (`DocumentationAJourTest`, dans les deux sens), surface CLI contre le compteur
# verrouillé de `cli-surface.bats`, vues contre captures contre doc, ADR contre index contre nav.
#
# De sa **propre description**, il n'exigeait rien. Trois inventaires étaient tenus à la main, et les
# trois avaient dérivé - mesuré le 2026-08-15 à la clôture du lot 3 (#3561) :
#
# | Inventaire | Écart |
# |---|---|
# | tableau des workflows | 2 absents : `codeql.yml`, `securite-dependances.yml` |
# | tableau des gardes autotestées | 6 absents (#3771) |
# | chemins surveillés ↔ classes jouées | à vérifier ici |
#
# Les deux workflows absents étaient **ceux de sécurité**. Ce n'est pas un hasard : un inventaire
# non gardé perd d'abord ce qu'on regarde le moins.
#
# Et le défaut se reproduit **pendant qu'on le décrit** : #3771 annonçait cinq gardes manquantes, il
# y en avait six. Les cinq avaient été listées à l'œil ; la sixième est sortie d'un comptage. C'est la
# raison d'être de ce script - obtenir la liste plutôt que la refaire.
#
# ## Ce qu'elle ne vérifie PAS, et pourquoi
#
# **Le contenu des colonnes.** La colonne « où elle tourne » porte des nuances vraies :
# `lance-test-filme.sh` vit dans un workflow manuel, `mesure-duree-portail.sh` avertit sans bloquer.
# Exiger un libellé pousserait à compléter le tableau **en l'aplatissant** - complet et trompeur, ce
# qui est pire que lacunaire. La garde vérifie la **présence**, pas la description.
#
# **Qu'une classe surveillée mérite de l'être.** Que `GestesFichiers` doive figurer parmi les chemins
# surveillés du contrat de fichiers est une **décision**, pas une déduction : aucun script ne peut la
# tirer du code. Elle reste à la charge d'un humain, et c'est écrit ici pour qu'on ne croie pas le
# contraire.
#
# Usage : ./.github/scripts/verifie-inventaires-ci.sh [--auto-test]
set -uo pipefail
export LC_ALL=C.UTF-8 2>/dev/null || export LC_ALL=C

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="${INVENTAIRES_RACINE:-$(cd "$ICI/../.." && pwd)}"

juger() {
    python3 - "$1" <<'FIN'
import glob
import os
import re
import sys

racine = sys.argv[1]
doc = os.path.join(racine, "dev-docs", "ci-cd-release.md")
workflows = os.path.join(racine, ".github", "workflows")

if not os.path.isfile(doc):
    print(f"❌ Page introuvable : {doc}")
    print("   C'est la GARDE qui est en cause, pas les inventaires : le document a-t-il été renommé ?")
    sys.exit(1)

texte = open(doc, encoding="utf-8").read()
ecarts = []


def bloc_de(titre):
    """La section qui commence par `titre`, jusqu'au prochain titre de même niveau."""
    debut = texte.find(titre)
    if debut < 0:
        return None
    suite = texte.find("\n## ", debut + len(titre))
    return texte[debut : suite if suite > 0 else len(texte)]


# ─── 1. Les workflows ────────────────────────────────────────────────────────────────────────────
# On compare des FICHIERS CITÉS, jamais un nombre de lignes : `maven.yml` occupe cinq lignes du
# tableau, une par job, et une garde qui compterait rougirait sur un tableau juste.
cites = set(re.findall(r"blob/main/\.github/workflows/([A-Za-z0-9_.-]+\.yml)", texte))
reels = {os.path.basename(p) for p in glob.glob(os.path.join(workflows, "*.yml"))}

if not reels:
    print(f"❌ Aucun workflow trouvé sous {workflows}")
    print("   La garde ne peut rien confronter : chemin déplacé ?")
    sys.exit(1)

for absent in sorted(reels - cites):
    ecarts.append(f"workflow `{absent}` existe mais n'est cité nulle part dans le tableau")
for fantome in sorted(cites - reels):
    ecarts.append(f"le tableau cite `{fantome}`, qui n'existe plus")

# ─── 2. Les gardes qui portent leur preuve ───────────────────────────────────────────────────────
SECTION_GARDES = "## Toute garde de CI porte sa propre preuve"
bloc = bloc_de(SECTION_GARDES)
if bloc is None:
    print(f"❌ Section « {SECTION_GARDES} » introuvable dans {doc}")
    print("   C'est la GARDE qui est en cause : la section a-t-elle été renommée ?")
    sys.exit(1)

# Le périmètre inclut `scripts/**`, et pas seulement `.github/`. Il s'est arrêté à `.github/`
# pendant tout le temps où les gardes y vivaient - puis un banc de soixante-cinq cas est arrivé sous
# `scripts/doc-video/`, il n'était lancé par aucun workflow, et cette garde ne pouvait pas le dire :
# elle ne regardait pas là. Un inventaire aveugle à un dossier annonce la complétude qu'il n'a pas
# (#4013).
autotestes = set()
# Et pas seulement le shell. Le tableau porte `scripts/adr/verifie_scripts.py` de longue date,
# mais ces motifs s'arrêtaient à `*.sh` : les deux gardes du graphe (#4231) y sont entrées parce que
# leur auteur savait qu'il fallait le faire, pas parce que la garde l'a exigé. Aveugle à un dossier
# hier (#4013), aveugle à une EXTENSION aujourd'hui - le même défaut, et toujours sous la forme d'un
# vert (#4255).
motifs = [
    os.path.join(racine, ".github", "scripts", "*.sh"),
    os.path.join(racine, ".github", "scripts", "*.py"),
    os.path.join(racine, ".github", "assets", "*.sh"),
    os.path.join(racine, ".github", "assets", "*.py"),
    os.path.join(racine, "scripts", "**", "*.sh"),
    os.path.join(racine, "scripts", "**", "*.py"),
]
for motif in motifs:
    for chemin in glob.glob(motif, recursive=True):
        if "--auto-test" in open(chemin, encoding="utf-8", errors="ignore").read():
            autotestes.add(os.path.basename(chemin))

if not autotestes:
    print("❌ Aucun script portant « --auto-test » trouvé.")
    print("   La garde ne peut rien confronter : le motif ou le chemin a-t-il changé ?")
    sys.exit(1)

# On ne retient que le nom de fichier : le tableau écrit tantôt `verifie-jeton.sh`, tantôt
# `scripts/adr/verifie_scripts.py`, et le chemin n'est pas ce qui est vérifié ici.
tableau = {c.split("/")[-1] for c in re.findall(r"^\| `([^`]+)`", bloc, re.M)}
for absent in sorted(autotestes - tableau):
    ecarts.append(f"garde `{absent}` répond à --auto-test mais n'est pas au tableau des gardes")

# ─── 3. Le contrat de système de fichiers ────────────────────────────────────────────────────────
filtre = os.path.join(racine, ".github", "scripts", "porte-sur-le-contrat-de-fichiers.sh")
maven = os.path.join(racine, ".github", "workflows", "maven.yml")
if os.path.isfile(filtre) and os.path.isfile(maven):
    surveilles = open(filtre, encoding="utf-8").read()
    tests_surveilles = {
        os.path.basename(m)[:-5]
        for m in re.findall(r"^(src/test/java/\S+\.java)$", surveilles, re.M)
    }
    corps = open(maven, encoding="utf-8").read()
    joue = re.search(r"-Dtest='([^']+)'", corps)
    if joue is None:
        ecarts.append("le job `contrat-fichiers` ne porte plus de `-Dtest='…'` : la garde ne sait plus quoi lire")
    else:
        classes = {c.strip() for c in joue.group(1).split(",") if c.strip()}
        for absent in sorted(tests_surveilles - classes):
            ecarts.append(f"`{absent}` est surveillé par le filtre mais n'est pas joué par la matrice")
        for orphelin in sorted(classes - tests_surveilles):
            ecarts.append(f"`{orphelin}` est joué par la matrice mais son fichier n'est pas surveillé")
else:
    ecarts.append("filtre du contrat de fichiers ou `maven.yml` introuvable : la garde est en cause")

# ─── Verdict ─────────────────────────────────────────────────────────────────────────────────────
if ecarts:
    print(f"❌ {len(ecarts)} écart(s) entre ce que la CI fait et ce qu'elle dit d'elle-même :")
    for e in ecarts:
        print(f"   · {e}")
    print()
    print("   Un inventaire qui se dit exhaustif et ne l'est pas rend l'inverse du service qu'il")
    print("   annonce : on le lit pour savoir ce qui tourne, et il omet ce qu'on regarde le moins.")
    sys.exit(1)

print(f"✔ {len(reels)} workflow(s) et {len(autotestes)} garde(s) autotestée(s) : les inventaires concordent.")
sys.exit(0)
FIN
}

if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "${bac}"' EXIT

    # Monte un dépôt jouet complet et cohérent, puis laisse chaque cas le dégrader d'une seule façon.
    monter() {
        rm -rf "${bac}/depot"
        mkdir -p "${bac}/depot/.github/workflows" "${bac}/depot/.github/scripts" \
            "${bac}/depot/.github/assets" "${bac}/depot/dev-docs"
        printf 'name: A\n' > "${bac}/depot/.github/workflows/a.yml"
        printf 'name: B\n' > "${bac}/depot/.github/workflows/b.yml"
        printf 'echo --auto-test\n' > "${bac}/depot/.github/scripts/verifie-truc.sh"
        cat > "${bac}/depot/.github/scripts/porte-sur-le-contrat-de-fichiers.sh" <<'SUR'
SURVEILLES=$(cat <<'FIN'
src/test/java/fr/univ_amu/iut/UnTest.java
FIN
)
SUR
        printf "        -Dtest='UnTest'\n" >> "${bac}/depot/.github/workflows/a.yml"
        cp "${bac}/depot/.github/workflows/a.yml" "${bac}/depot/.github/workflows/maven.yml"
        cat > "${bac}/depot/dev-docs/ci-cd-release.md" <<'DOC'
| [a.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/a.yml) | x | y | z |
| [b.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/b.yml) | x | y | z |
| [maven.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml) · job `un` | x | y | z |
| [maven.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml) · job `deux` | x | y | z |

## Toute garde de CI porte sa propre preuve

| Garde | Ce qu'elle vérifie | Où elle tourne |
|---|---|---|
| `verifie-truc.sh` | un truc | `lint.yml` |

## Suite
DOC
    }

    # Le compte des cas et de ceux qui DOIVENT rougir (#3886).
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé> [motif attendu dans la sortie]
        local code=0 sortie
        cas=$((cas + 1))
        if [ "$1" != 0 ]; then rouges=$((rouges + 1)); fi
        sortie=$(INVENTAIRES_RACINE="${bac}/depot" juger "${bac}/depot" 2>&1) || code=$?
        if [ "${code}" != "$1" ]; then
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            printf '%s\n' "${sortie}" | sed 's/^/       /'
            echecs=1
        elif [ -n "${3:-}" ] && ! printf '%s' "${sortie}" | grep -qF -- "$3"; then
            echo "  ✘ $2 : code ${code} attendu, mais le motif « $3 » manque au verdict"
            echecs=1
        else
            echo "  ✔ $2"
        fi
    }

    # Le contrôle NÉGATIF d'abord, et il est le plus important : une règle qui refuse tout est aussi
    # inutile qu'une règle qui accepte tout. Un dépôt cohérent - `maven.yml` cité DEUX fois, une ligne
    # par job - doit rester vert.
    monter
    verifie 0 "un dépôt cohérent reste vert, maven.yml cité deux fois compris"

    monter && printf 'name: C\n' > "${bac}/depot/.github/workflows/c.yml"
    verifie 1 "un workflow non cité est vu" "c.yml"

    monter && sed -i 's|blob/main/.github/workflows/b.yml|blob/main/.github/workflows/disparu.yml|' \
        "${bac}/depot/dev-docs/ci-cd-release.md"
    verifie 1 "un workflow cité qui n'existe plus est vu" "disparu.yml"

    monter && printf 'echo --auto-test\n' > "${bac}/depot/.github/scripts/verifie-oublie.sh"
    verifie 1 "une garde autotestée absente du tableau est vue" "verifie-oublie.sh"

    # Le même cas depuis `.github/assets/` : les deux dossiers portent des gardes, et n'en balayer
    # qu'un laissait `check-captures.sh` et ses voisins hors de portée.
    monter && printf 'echo --auto-test\n' > "${bac}/depot/.github/assets/check-oublie.sh"
    verifie 1 "une garde autotestée de assets/ est vue aussi" "check-oublie.sh"

    # Le même cas en Python. Le tableau porte `scripts/adr/verifie_scripts.py` depuis longtemps,
    # mais les motifs ne balayaient que le shell : les deux gardes du graphe (#4231) y sont entrées
    # parce que leur auteur savait qu'il fallait le faire, pas parce que la garde l'a exigé. Une
    # exigence tenue de mémoire finit par tomber - c'est la raison d'être de ce script.
    monter && mkdir -p "${bac}/depot/scripts/outil" \
        && printf "if '--auto-test' in argv:\n    pass\n" > "${bac}/depot/scripts/outil/garde_oubliee.py"
    verifie 1 "une garde autotestée en Python est vue aussi" "garde_oubliee.py"

    # Et son pendant : inscrite au tableau, elle ne doit plus rien reprocher. Une regle qui
    # refuse tout est aussi inutile qu'une regle qui accepte tout.
    monter && mkdir -p "${bac}/depot/scripts/outil" \
        && printf "if '--auto-test' in argv:\n    pass\n" > "${bac}/depot/scripts/outil/garde_oubliee.py" \
        && sed -i 's@^## Suite$@| `scripts/outil/garde_oubliee.py` | un autre truc | `lint.yml` |\n\n## Suite@' \
            "${bac}/depot/dev-docs/ci-cd-release.md"
    verifie 0 "la même garde Python, inscrite au tableau, repasse au vert"

    monter && sed -i "s/-Dtest='UnTest'/-Dtest='AutreTest'/" "${bac}/depot/.github/workflows/maven.yml"
    verifie 1 "une classe jouée dont le fichier n'est pas surveillé est vue" "AutreTest"

    monter && sed -i "s|src/test/java/fr/univ_amu/iut/UnTest.java|src/test/java/fr/univ_amu/iut/UnTest.java\nsrc/test/java/fr/univ_amu/iut/PasJoueTest.java|" \
        "${bac}/depot/.github/scripts/porte-sur-le-contrat-de-fichiers.sh"
    verifie 1 "un test surveillé que la matrice ne joue pas est vu" "PasJoueTest"

    # Les refus qui accusent la GARDE, et non ce qu'elle surveille. Sans eux, un renommage de
    # section rendrait un vert rassurant là où plus rien n'est confronté.
    monter && sed -i 's/^## Toute garde de CI porte sa propre preuve$/## Autre titre/' \
        "${bac}/depot/dev-docs/ci-cd-release.md"
    verifie 1 "une section renommée accuse la garde" "C'est la GARDE qui est en cause"

    monter && rm "${bac}/depot/dev-docs/ci-cd-release.md"
    verifie 1 "la page absente accuse la garde" "C'est la GARDE qui est en cause"

    echo
    if [ "${rouges}" -eq 1 ]; then verbe=DOIT; else verbe=DOIVENT; fi
    echo "${cas} cas, dont ${rouges} qui ${verbe} rougir."
    exit "${echecs}"
fi

juger "${RACINE}"
