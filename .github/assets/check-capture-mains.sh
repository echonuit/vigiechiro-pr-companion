#!/usr/bin/env bash
#
# Garde-fou : tout outil de capture (classe `*/outils/Capture*.java` avec une méthode `main`)
# doit être enregistré dans le tableau `MAINS` de `capture-screenshots.sh`. Sinon son ou ses
# PNG ne seraient JAMAIS (re)générés par le workflow `capture-vues` : la capture existerait dans
# le manifeste (donc `check-captures.sh` passerait) mais se figerait, périmée, sans que rien ne le
# signale. On vérifie aussi l'inverse : chaque entrée `MAINS` pointe une classe qui existe encore.
#
# Complète `check-captures.sh` (aucune VUE sans capture) et `check-doc-images.sh` (aucune PAGE
# référençant une capture absente) : ici, aucun OUTIL de capture n'est oublié du script de rendu.
# Léger : aucune compilation ni rendu, juste des fichiers. Lancé en CI (Quality gate).
#
# Exit 0 si tout est cohérent, 1 sinon (détails sur stdout).

set -euo pipefail

# Auto-test (#3293), sur le modèle de `verifie-titre-pr.sh` (#2947) : ce script se réinvoque sur une
# arborescence jetable, donc le cas de test et le chemin réel sont le même code.
if [ "${1:-}" = "--auto-test" ]; then
    echecs=0
    verifie() { # <attendu> <libellé>
        code=0
        MAINS_ASSETS="$bac/assets" MAINS_SOURCES="$bac/src" "$0" >/dev/null 2>&1 || code=$?
        if [ "${code}" = "$1" ]; then
            echo "  ✔ $2"
        else
            echo "  ✘ $2 : attendu $1, obtenu ${code}"
            echecs=1
        fi
    }

    outil() { # <chemin relatif sous src> <avec main ?>
        mkdir -p "$bac/src/$(dirname "$1")"
        if [ "${2:-oui}" = "oui" ]; then
            printf 'class X { public static void main(String[] a) {} }\n' > "$bac/src/$1"
        else
            printf 'class X { }\n' > "$bac/src/$1"
        fi
    }

    monter() { # un bac COMPLET et cohérent : un outil déclaré, langue et fuseau épinglés
        rm -rf "$bac"
        mkdir -p "$bac/assets"
        outil fr/univ_amu/iut/exemple/outils/CaptureExemple.java
        printf 'MAINS=(\n  "fr.univ_amu.iut.exemple.outils.CaptureExemple"\n)\nLOCALISATION="%s"\n' \
            "-Duser.language=fr -Duser.country=FR -Duser.timezone=Europe/Paris" \
            > "$bac/assets/capture-screenshots.sh"
    }

    bac="$(mktemp -d)"
    trap 'rm -rf "$bac"' EXIT

    monter
    verifie 0 "un outil enregistré dans MAINS passe"

    monter
    outil fr/univ_amu/iut/exemple/outils/CaptureOubliee.java
    verifie 1 "un outil de capture absent de MAINS est refusé"

    monter
    rm "$bac/src/fr/univ_amu/iut/exemple/outils/CaptureExemple.java"
    verifie 1 "une entrée MAINS sans classe correspondante est refusée"

    # Contrôles NÉGATIFS : la règle vise les outils AVEC un main, sous outils/, nommés Capture*.
    monter
    outil fr/univ_amu/iut/exemple/outils/CaptureSocle.java non
    verifie 0 "un Capture* sans main (socle partagé) ne déclenche pas"

    monter
    outil fr/univ_amu/iut/exemple/ailleurs/CaptureHorsOutils.java
    verifie 0 "un Capture* hors d'un dossier outils/ ne déclenche pas"

    # L'épinglage de la langue et du fuseau (#3389) : sans lui, un aperçu montre la machine qui l'a
    # rendu et non le produit, et RIEN ne le signale - c'est ce qui a laissé « Cancel » dans une
    # galerie francophone. Le cas nominal est couvert par `monter`, qui l'inclut.
    monter
    verifie 0 "un script qui épingle langue et fuseau passe"

    monter
    grep -v 'user.language' "$bac/assets/capture-screenshots.sh" > "$bac/tmp" \
        && mv "$bac/tmp" "$bac/assets/capture-screenshots.sh"
    verifie 1 "un script qui n'épingle plus la langue est refusé"

    if [ "${echecs}" = 0 ]; then
        echo "Auto-test de la garde MAINS captures : OK"
    else
        echo "Auto-test de la garde MAINS captures : ÉCHEC - les règles ne font plus ce qu'elles promettent."
    fi
    exit "${echecs}"
fi

# Les deux racines sont surchargeables : l'auto-test vise une arborescence jetable en réinvoquant CE
# script, donc sans recopier une seule règle. Par défaut, le dépôt réel.
ICI="${MAINS_ASSETS:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
SOURCES="${MAINS_SOURCES:-$(cd "$ICI/../.." && pwd)/src/main/java}"
SCRIPT="$ICI/capture-screenshots.sh"
erreurs=0

# Entrées MAINS déclarées : les littéraux FQCN entre guillemets du script (seul endroit où ils
# apparaissent). Normalisées, triées, dédoublonnées.
mains="$(grep -oE '"fr\.univ_amu\.iut\.[A-Za-z0-9_.]+"' "$SCRIPT" | tr -d '"' | sort -u)"

# 1. Chaque outil de capture (Capture*.java sous **/outils/ avec un `main`) doit figurer dans MAINS.
nb_outils=0
while IFS= read -r fichier; do
  grep -qE 'static void main' "$fichier" || continue # helpers sans main (ex. socles) : ignorés
  rel="${fichier#"$SOURCES"/}"
  fqcn="${rel%.java}"
  fqcn="${fqcn//\//.}"
  nb_outils=$((nb_outils + 1))
  if ! grep -qxF "$fqcn" <<<"$mains"; then
    echo "❌ Outil de capture absent de MAINS (ses PNG ne seraient jamais régénérés) : $fqcn"
    erreurs=$((erreurs + 1))
  fi
done < <(find "$SOURCES" -path '*/outils/Capture*.java' | sort)

# 2. Chaque entrée MAINS doit pointer une classe qui existe (détecte un renommage/suppression).
while IFS= read -r fqcn; do
  [ -z "$fqcn" ] && continue
  if [[ ! -f "$SOURCES/${fqcn//.//}.java" ]]; then
    echo "❌ Entrée MAINS sans classe correspondante (renommée/supprimée ?) : $fqcn"
    erreurs=$((erreurs + 1))
  fi
done <<<"$mains"

# 3. La langue et le fuseau doivent rester ÉPINGLÉS (#3389). Sans eux, JavaFX résout les libellés par
# défaut de ButtonType via `Locale.getDefault()` et formate les horodatages dans le fuseau du système :
# la galerie montre alors la machine qui l'a rendue plutôt que le produit, sans que rien ne le signale.
# C'est un contrôle STATIQUE : le vérifier par le rendu coûterait deux passes complètes de capture.
for propriete in "-Duser.language=fr" "-Duser.country=FR" "-Duser.timezone=Europe/Paris"; do
  if ! grep -qF -- "$propriete" "$SCRIPT"; then
    echo "❌ Épinglage perdu dans capture-screenshots.sh (la galerie redeviendrait dépendante de la machine) : $propriete"
    erreurs=$((erreurs + 1))
  fi
done

if [[ $erreurs -gt 0 ]]; then
  echo "Garde MAINS captures : $erreurs problème(s) : voir ci-dessus."
  exit 1
fi
echo "Garde MAINS captures : OK ($nb_outils outils de capture, tous enregistrés dans MAINS)."
