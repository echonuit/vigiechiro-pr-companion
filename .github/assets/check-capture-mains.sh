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

ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RACINE="$(cd "$ICI/../.." && pwd)"
SCRIPT="$ICI/capture-screenshots.sh"
SOURCES="$RACINE/src/main/java"
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

if [[ $erreurs -gt 0 ]]; then
  echo "Garde MAINS captures : $erreurs problème(s) — voir ci-dessus."
  exit 1
fi
echo "Garde MAINS captures : OK ($nb_outils outils de capture, tous enregistrés dans MAINS)."
