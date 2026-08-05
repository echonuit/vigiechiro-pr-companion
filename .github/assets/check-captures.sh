#!/usr/bin/env bash
#
# Garde de complétude des captures d'écran (issue #86).
#
# Vérifie, à partir du manifeste `captures.manifest`, que :
#   1. chaque vue FXML sous `src/main/**/view/*.fxml` est déclarée au manifeste ;
#   2. chaque vue déclarée existe réellement dans le code ;
#   3. chaque capture déclarée existe dans `.github/assets/`.
# Échoue (exit 1) au moindre manquement. Léger : aucune compilation ni rendu, juste des fichiers.
#
# Lancé en CI (Quality gate). Pour le mettre à jour : ajouter la nouvelle vue + ses captures au
# manifeste, et générer les PNG via `capture-screenshots.sh`.

set -euo pipefail

# Auto-test (#3293), sur le modèle de `verifie-titre-pr.sh` (#2947) : un garde qui cesse de détecter
# reste vert, et c'est le seul défaut qui se présente sous la forme d'un succès. Chaque cas monte une
# arborescence jetable, RÉINVOQUE ce script dessus, et compare le code de sortie à l'attendu - le cas
# de test et le chemin réel sont donc le même code, par construction.
if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    verifie() { # <attendu> <libellé> ; l'arborescence est déjà montée dans $bac
        code=0
        CAPTURES_ASSETS="$bac/assets" CAPTURES_SOURCES="$bac/src" "$0" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $2"
        else
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }

    monter() { # remet un bac COMPLET et cohérent, que chaque cas abîme ensuite d'une seule façon
        rm -rf "$bac"
        mkdir -p "$bac/assets" "$bac/src/fr/exemple/vue/view"
        : > "$bac/src/fr/exemple/vue/view/Ecran.fxml"
        printf 'fr/exemple/vue/view/Ecran.fxml : apercu-ecran.png\n' > "$bac/assets/captures.manifest"
        : > "$bac/assets/apercu-ecran.png"
        printf 'apercu-ecran.png\n' > "$bac/assets/README.md"
    }

    bac="$(mktemp -d)"
    trap 'rm -rf "$bac"' EXIT

    monter
    verifie 0 "un manifeste complet et cohérent passe"

    monter
    : > "$bac/src/fr/exemple/vue/view/Oubliee.fxml"
    verifie 1 "une vue non déclarée au manifeste est refusée"

    monter
    rm "$bac/src/fr/exemple/vue/view/Ecran.fxml"
    verifie 1 "une vue déclarée mais absente du code est refusée"

    monter
    rm "$bac/assets/apercu-ecran.png"
    verifie 1 "une capture déclarée mais absente du disque est refusée"

    monter
    printf 'fr/exemple/vue/view/Ecran.fxml :\n' > "$bac/assets/captures.manifest"
    verifie 1 "une vue sans aucune capture déclarée est refusée"

    monter
    : > "$bac/assets/README.md"
    verifie 1 "une capture absente de la galerie est refusée"

    monter
    mkdir -p "$bac/src/fr/exemple/vue/outils"
    printf 'class CaptureX { String f = "apercu-neuve.png"; }\n' > "$bac/src/fr/exemple/vue/outils/CaptureX.java"
    verifie 1 "une capture écrite par un outil, absente de la galerie, est refusée (#3129)"

    # Contrôles NÉGATIFS : la règle doit rester étroite.
    monter
    mkdir -p "$bac/src/fr/exemple/ailleurs"
    : > "$bac/src/fr/exemple/ailleurs/PasUneVue.fxml"
    verifie 0 "un .fxml hors d'un dossier view/ ne déclenche pas"

    monter
    printf '# un commentaire\n\n' >> "$bac/assets/captures.manifest"
    verifie 0 "commentaires et lignes vides du manifeste sont ignorés"

    monter
    mkdir -p "$bac/src/fr/exemple/vue/outils"
    printf 'class CaptureX {\n  /// remplace apercu-disparue.png, une replique reconstruite\n}\n' \
      > "$bac/src/fr/exemple/vue/outils/CaptureX.java"
    verifie 0 "une capture citée en commentaire seulement ne déclenche pas (#3129)"

    if [ "${echecs}" = 0 ]; then
        echo "Auto-test de la garde captures : OK"
    else
        echo "Auto-test de la garde captures : ÉCHEC - les règles ne font plus ce qu'elles promettent."
    fi
    exit "${echecs}"
fi

# Les deux racines sont surchargeables : l'auto-test vise une arborescence jetable en réinvoquant CE
# script, donc sans recopier une seule règle. Par défaut, le dépôt réel.
ICI="${CAPTURES_ASSETS:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
SOURCES="${CAPTURES_SOURCES:-$(cd "$ICI/../.." && pwd)/src/main/java}"
MANIFESTE="$ICI/captures.manifest"
erreurs=0

# Vues déclarées au manifeste (partie avant le « : »), normalisées.
declarees="$(grep -vE '^[[:space:]]*(#|$)' "$MANIFESTE" | sed 's/[[:space:]]*:.*//' | sed 's/[[:space:]]//g')"

# 1. Chaque *.fxml sous **/view/ doit être déclaré.
while IFS= read -r fxml; do
  rel="${fxml#"$SOURCES"/}"
  if ! grep -qxF "$rel" <<< "$declarees"; then
    echo "❌ Vue sans capture déclarée au manifeste : $rel"
    erreurs=$((erreurs + 1))
  fi
done < <(find "$SOURCES" -path '*/view/*.fxml' | sort)

# 2 + 3. Chaque vue déclarée existe ; chaque capture déclarée existe.
nb_vues=0
while IFS= read -r ligne; do
  case "$ligne" in ''|\#*) continue ;; esac
  vue="$(sed 's/[[:space:]]*:.*//;s/[[:space:]]//g' <<< "$ligne")"
  captures="$(sed 's/^[^:]*://' <<< "$ligne")"
  nb_vues=$((nb_vues + 1))
  if [[ ! -f "$SOURCES/$vue" ]]; then
    echo "❌ Vue déclarée au manifeste mais absente du code : $vue"
    erreurs=$((erreurs + 1))
  fi
  nb_captures=0
  for png in $captures; do
    nb_captures=$((nb_captures + 1))
    if [[ ! -f "$ICI/$png" ]]; then
      echo "❌ Capture déclarée mais absente de .github/assets/ : $png (vue $vue)"
      erreurs=$((erreurs + 1))
    fi
  done
  if [[ $nb_captures -eq 0 ]]; then
    echo "❌ Vue sans aucune capture déclarée : $vue"
    erreurs=$((erreurs + 1))
  fi
done < "$MANIFESTE"

# 4. Chaque capture du disque est PRÉSENTÉE dans la galerie (README.md).
#
# Le manifeste garantit qu'aucune vue n'est livrée sans capture ; il ne garantissait pas qu'on puisse
# la REGARDER. La galerie sert aux passes visuelles : une capture qui n'y figure pas n'est jamais
# ouverte, et le document laisse croire qu'il couvre le produit entier. Vécu : 33 captures présentées
# sur 126.
nb_galerie=0
for png in "$ICI"/apercu-*.png; do
  [[ -f "$png" ]] || continue
  nb_galerie=$((nb_galerie + 1))
  if ! grep -qF "$(basename "$png")" "$ICI/README.md"; then
    echo "❌ Capture absente de la galerie README.md : $(basename "$png")"
    erreurs=$((erreurs + 1))
  fi
done

# 5. Chaque capture qu'un outil ÉCRIT est présentée dans la galerie (#3129).
#
# La règle 4 part des PNG **présents sur le disque**. Or ils ne naissent pas dans la branche : le job
# `capturer` les produit sur `main`, APRÈS fusion. Sur une PR qui ajoute une capture, le fichier
# n'existe pas encore, la règle 4 ne voit rien, et la PR passe au vert de bonne foi. Le manque
# n'apparaît qu'une fois `main` déjà rouge, et le coût est payé par TOUTES les PR ouvertes.
#
# Vécu : #3119 a ajouté deux aperçus sans les inscrire à la galerie. CI verte, `main` rouge après
# fusion, diagnostic parti dans la mauvaise direction. Corrigé par #3126, puis par cette règle.
#
# Ce qui EST dans la branche, c'est le **code de l'outil**. On lit donc les noms qu'il écrit. Les
# lignes de commentaire sont écartées : elles citent volontiers des captures **passées** (une réplique
# remplacée par un rendu réel), qui n'existent plus et n'ont rien à faire en galerie.
nb_ecrites=0
while IFS= read -r png; do
  [[ -n "$png" ]] || continue
  nb_ecrites=$((nb_ecrites + 1))
  if ! grep -qF "$png" "$ICI/README.md"; then
    echo "❌ Capture écrite par un outil mais absente de la galerie README.md : $png"
    erreurs=$((erreurs + 1))
  fi
done < <(grep -rh --include='Capture*.java' -E 'apercu-[a-z0-9-]+\.png' "$SOURCES" 2>/dev/null \
           | grep -vE '^[[:space:]]*(///|\*|//)' \
           | grep -oE 'apercu-[a-z0-9-]+\.png' | sort -u)

if [[ $erreurs -gt 0 ]]; then
  echo "Garde captures : $erreurs problème(s) : voir ci-dessus."
  exit 1
fi
echo "Garde captures : OK ($nb_vues vues couvertes, $nb_galerie captures sur disque et $nb_ecrites écrites par un outil, toutes présentées dans la galerie)."
