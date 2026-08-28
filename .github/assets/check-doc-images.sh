#!/usr/bin/env bash
# Garde-fou : toute capture d'écran référencée par une page de doc utilisateur
# (docs/**/*.md, motif apercu-*.png) doit À LA FOIS exister dans .github/assets/
# ET être déclarée dans captures.manifest (donc régénérée par capture-vues.yml).
#
# Complète check-captures.sh : celui-ci garantit qu'aucune VUE n'est livrée sans
# capture ; celui-là garantit qu'aucune PAGE ne référence une capture absente ou
# non régénérée. Lancé en CI par .github/workflows/docs.yml.
#
# Exit 0 si tout est cohérent, 1 sinon (détails sur stdout).
set -uo pipefail

# Auto-test (#3293), sur le modèle de `verifie-titre-pr.sh` (#2947) : ce script se réinvoque sur une
# arborescence jetable, donc le cas de test et le chemin réel sont le même code.
if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    # Le compte des cas et de ceux qui DOIVENT rougir (#3886) : un auto-test dont on ignore combien
    # de cas éprouvent le refus ne s'audite pas d'un coup d'oeil, et celui qui n'en aurait aucun ne
    # prouverait rien du tout.
    cas=0
    rouges=0
    verifie() { # <attendu> <libellé>
        code=0
        cas=$((cas + 1))
        if [ "$1" != 0 ]; then rouges=$((rouges + 1)); fi
        DOC_IMAGES_RACINE="$bac" "$0" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $2"
        else
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }

    # Le temoin du defaut de tube (#4642). Il n'passe pas par `verifie`, qui reinvoque le script
    # entier : ce qui s'eprouve ici est le MOTIF, sur une source assez grosse pour depasser le
    # tampon d'un tube. Un temoin bati sur une liste courte serait vert avant comme apres.
    temoin_tube() {
        local grosse cible avec sans
        grosse=$(seq 1 50000 | sed 's/^/apercu-x/;s/$/.png/')
        cible=$(printf '%s\n' "$grosse" | head -1)
        cas=$((cas + 1))
        rouges=$((rouges + 1))
        if printf '%s\n' "$grosse" | grep -qx "$cible"; then avec=trouve; else avec=RATE; fi
        if grep -qxF "$cible" <<< "$grosse"; then sans=trouve; else sans=RATE; fi
        if [ "$avec" = RATE ] && [ "$sans" = trouve ]; then
            echo "  ✔ le tube accuse a tort sur une grosse source, sa suppression corrige"
        else
            echo "  ✘ temoin du tube : avec tube=$avec, sans tube=$sans (attendu RATE puis trouve)"
            echecs=1
        fi
    }

    monter() { # un bac COMPLET : une page qui référence une capture présente et déclarée
        rm -rf "$bac"
        mkdir -p "$bac/.github/assets" "$bac/docs"
        printf 'fr/exemple/view/Ecran.fxml : apercu-exemple.png\n' > "$bac/.github/assets/captures.manifest"
        : > "$bac/.github/assets/apercu-exemple.png"
        printf '![Un écran](../.github/assets/apercu-exemple.png)\n' > "$bac/docs/page.md"
    }

    bac="$(mktemp -d)"
    trap 'rm -rf "$bac"' EXIT

    monter
    verifie 0 "une capture référencée, présente et déclarée passe"

    monter
    rm "$bac/.github/assets/apercu-exemple.png"
    verifie 1 "une capture référencée mais absente du disque est refusée"

    monter
    printf 'fr/exemple/view/Ecran.fxml : apercu-autre.png\n' > "$bac/.github/assets/captures.manifest"
    : > "$bac/.github/assets/apercu-autre.png"
    verifie 1 "une capture présente mais non déclarée au manifeste est refusée"

    # Contrôles NÉGATIFS : la règle ne vise que les captures citées par la doc.
    monter
    : > "$bac/.github/assets/apercu-jamais-citee.png"
    verifie 0 "une capture que la doc ne cite pas ne déclenche pas"

    monter
    rm "$bac/docs/page.md"
    verifie 0 "une doc sans aucune capture n'a rien à vérifier"

    echo
    temoin_tube
    echo "${cas} cas, dont ${rouges} qui DOIVENT rougir."
    if [ "${echecs}" = 0 ]; then
        echo "Auto-test de la garde images de doc : OK"
    else
        echo "Auto-test de la garde images de doc : ÉCHEC - les règles ne font plus ce qu'elles promettent."
    fi
    exit "${echecs}"
fi

# La racine est surchargeable : l'auto-test vise une arborescence jetable en réinvoquant CE script,
# donc sans recopier une seule règle. Par défaut, le dépôt réel.
ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="${DOC_IMAGES_RACINE:-$(cd "$ICI/../.." && pwd)}"
# Un `cd` qui échoue laisserait la garde inventorier un AUTRE dossier, et le déclarer conforme.
cd "$RACINE" || exit 1

ASSETS=".github/assets"
MANIFEST="$ASSETS/captures.manifest"
ERREURS=0

# Captures DÉCLARÉES au manifeste (jetons apercu-*.png après le « : »).
# shellcheck disable=SC2020  # Le doublon de `\n` est VOULU : espace et tabulation deviennent tous
# deux un saut de ligne, ce qui découpe la liste du manifeste sur n'importe quelle blancheur.
declarees="$(grep -v '^[[:space:]]*#' "$MANIFEST" \
  | sed 's/^[^:]*://' \
  | tr ' \t' '\n\n' \
  | grep -E '^apercu-[a-z0-9-]+\.png$' \
  | sort -u)"

# Captures RÉFÉRENCÉES par la doc.
referencees="$(grep -rhoE 'apercu-[a-z0-9-]+\.png' docs --include='*.md' 2>/dev/null | sort -u)"

if [ -z "$referencees" ]; then
  echo "Aucune capture référencée par la doc : rien à vérifier."
  exit 0
fi

while IFS= read -r png; do
  [ -z "$png" ] && continue
  if [ ! -f "$ASSETS/$png" ]; then
    echo "✗ $png : référencée par la doc mais ABSENTE de $ASSETS/"
    ERREURS=$((ERREURS + 1))
    continue
  fi
  # Sans tube, et c'est le fond : `grep -q` sort au premier match et referme le tuyau, si bien qu'un
  # `printf` encore en train d'ecrire recoit SIGPIPE - que `pipefail` propage ALORS MEME que `grep` a
  # trouve. Le garde accuse alors une capture parfaitement declaree.
  #
  # Mesure (#4642) : avec les 142 captures du manifeste, 4 492 octets, le defaut ne se declenche
  # jamais - 0 sur 200 essais - parce que l'ecriture tient sous le tampon de 65 536 octets d'un tube.
  # Avec 50 000 entrees, 888 893 octets, il se declenche 40 fois sur 40. Il est donc LATENT : il se
  # reveillerait vers deux mille captures, contre cent quarante aujourd'hui.
  #
  # L'echec observe sur #4641 n'est PAS explique par ce motif, et sa cause reste inconnue. Ce qui
  # suit previent une classe de defaut, cela ne corrige pas cet echec-la.
  if ! grep -qxF "$png" <<< "$declarees"; then
    echo "✗ $png : présente mais NON déclarée dans captures.manifest (à ajouter pour la régénération)"
    ERREURS=$((ERREURS + 1))
  fi
done <<< "$referencees"

if [ "$ERREURS" -eq 0 ]; then
  echo "✓ Toutes les captures référencées par la doc existent et sont déclarées au manifeste."
  exit 0
fi
echo ""
echo "✗ $ERREURS capture(s) de doc manquante(s)."
echo "  Pour en ajouter une : rendu dans le Capture* de la feature + entrée dans captures.manifest (cf. #191)."
exit 1
