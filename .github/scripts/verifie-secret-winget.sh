#!/usr/bin/env bash
# Garde du secret de soumission winget (#2213, lot 5 de l'EPIC #2104).
#
# ## Ce qu'elle empêche
#
# `winget.yml` refusait de soumettre quand `WINGET_TOKEN` manquait, et sortait **en vert**, avec un
# simple `::notice::`. Ce choix était le bon tant que le workflow se déclenchait sur `release:
# released` : rougir à chaque publication aurait été du bruit, sur un canal qu'on savait inerte.
#
# Il a cessé de l'être quand le workflow est passé en `workflow_dispatch` **seul**. Un dispatch manuel
# est un geste délibéré : on le lance parce qu'on veut soumettre une version. Répondre « vert » à ce
# geste sans avoir rien soumis, c'est annoncer une publication qui n'a pas eu lieu - le seul type de
# défaut qui se présente sous la forme d'un succès.
#
# ## Pourquoi elle ne se contente plus de la PRÉSENCE
#
# Première version : « le secret est-il posé ? ». On y avait écrit, noir sur blanc, qu'elle ne
# vérifierait pas que le jeton FONCTIONNE, au motif que cet échec-là serait de toute façon bruyant.
#
# L'expérience a démenti ce raisonnement le 2026-08-11. Le jeton était posé, la garde verte, et la
# soumission a échoué sur :
#
#     Echonuit.VigieChiroCompanion does not exist in microsoft/winget-pkgs
#
# Ce message est FAUX : le paquet y était, et `komac` le trouve en local avec un jeton valide. Un
# jeton que `komac` ne peut pas employer rend une réponse vide, et komac annonce l'absence du paquet.
# L'échec était donc bruyant, oui, mais il désignait le mauvais coupable - et il coûtait un runner
# Windows, un téléchargement de MSI et une installation complète pour être atteint.
#
# Bruyant ne suffit pas : il faut que le bruit NOMME la cause. D'où deux contrôles de plus.
#
# ## Les trois contrôles
#
#   1. PRÉSENCE  : le secret est posé et n'est pas vide (hors ligne).
#   2. FORME     : il ne porte ni espace ni retour à la ligne autour (hors ligne). Un `\n` capturé au
#      moment de poser le secret rend l'en-tête `Authorization` invalide, et c'est invisible partout
#      ailleurs : le jeton « marche » quand on le teste à la main, et pas dans la CI.
#   3. ACCÈS     : le jeton s'authentifie ET voit le paquet (en ligne, `--verifie-l-acces`).
#
# La sonde réseau du contrôle 3 est **injectable** (`WINGET_SONDE`), ce qui permet à l'auto-test de la
# jouer hors ligne, dans ses trois issues : jeton muet, jeton authentifié mais aveugle, jeton bon.
# Sans cela, ce contrôle serait le seul du dépôt à ne pas porter sa preuve.
#
# Usage : WINGET_TOKEN=… ./.github/scripts/verifie-secret-winget.sh [--verifie-l-acces]
#         ./.github/scripts/verifie-secret-winget.sh --auto-test
set -uo pipefail

# Chemin absolu : l'auto-test se réinvoque, et il doit pouvoir le faire depuis n'importe quel dossier.
MOI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

# Le chemin du paquet dans winget-pkgs, tel que komac le résout : `manifests/<initiale>/<éditeur>/<paquet>`.
CHEMIN_PAQUET="manifests/e/Echonuit/VigieChiroCompanion"
SONDE="${WINGET_SONDE:-gh}"

### Contrôle 3, en ligne. Rend 0 si le jeton s'authentifie ET voit le paquet.
verifier_l_acces() {
  local login vu
  login=$(GH_TOKEN="$1" "$SONDE" api user --jq .login 2>/dev/null) || login=""
  if [ -z "$login" ]; then
    echo "❌ WINGET_TOKEN est posé, mais il ne s'authentifie pas auprès de GitHub."
    echo
    echo "   Le jeton est expiré, révoqué, ou son contenu n'est pas celui qu'on croit."
    echo "   ⚠️ Un jeton valide COPIÉ AVEC UN RETOUR À LA LIGNE se comporte exactement ainsi :"
    echo "   il marche quand on le teste à la main, et pas ici. Le reposer sans :"
    echo "     printf '%s' \"\$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
    return 1
  fi

  vu=$(GH_TOKEN="$1" "$SONDE" api "repos/microsoft/winget-pkgs/contents/$CHEMIN_PAQUET" --jq '.[].name' 2>/dev/null) || vu=""
  if [ -z "$vu" ]; then
    echo "❌ WINGET_TOKEN s'authentifie (« $login »), mais ne voit pas le paquet dans winget-pkgs."
    echo
    echo "   Chemin interrogé : $CHEMIN_PAQUET"
    echo "   C'est CE constat que komac annonce sous la forme trompeuse « does not exist in"
    echo "   microsoft/winget-pkgs ». Le paquet existe ; c'est le jeton qui ne le lit pas."
    echo "   Un PAT « classic » doit porter le scope public_repo ; un jeton restreint par une"
    echo "   autorisation SSO d'organisation non validée donne le même silence."
    return 1
  fi

  echo "Accès winget-pkgs : OK (jeton « $login », versions vues : $(printf '%s' "$vu" | tr '\n' ' '))."
  return 0
}

if [ "${1:-}" = "--auto-test" ]; then
  echecs=0
  verifie() { # <attendu> <libellé> <commande>
    code=0
    ( eval "$3" ) >/dev/null 2>&1 || code=$?
    if [ "${code}" = "$1" ]; then
      echo "  ✔ $2"
    else
      echo "  ✘ $2 : attendu $1, obtenu ${code}"
      echecs=1
    fi
  }

  bac="$(mktemp -d)"
  trap 'rm -rf "$bac"' EXIT

  # Trois sondes de comptoir, pour jouer le contrôle d'accès hors ligne.
  cat > "$bac/sonde-bonne" <<'FIN'
#!/usr/bin/env bash
case "$*" in
  *"api user"*) echo "echonuit" ;;
  *contents*)   echo "2.34.2" ;;
esac
FIN
  cat > "$bac/sonde-muette" <<'FIN'
#!/usr/bin/env bash
exit 1
FIN
  cat > "$bac/sonde-aveugle" <<'FIN'
#!/usr/bin/env bash
case "$*" in
  *"api user"*) echo "echonuit" ;;
  *contents*)   exit 1 ;;
esac
FIN
  chmod +x "$bac"/sonde-*

  # ---- Contrôle 1 : présence. Le défaut d'origine, celui qui sortait en vert. -------------------
  verifie 1 "un secret ABSENT de l environnement est refusé" \
    "env -u WINGET_TOKEN '$MOI'"
  verifie 1 "un secret VIDE est refusé" \
    "WINGET_TOKEN= '$MOI'"
  verifie 1 "un secret réduit à des espaces est refusé" \
    "WINGET_TOKEN='   ' '$MOI'"

  # ---- Contrôle 2 : forme. Le suspect n°1, et le seul invisible à l oeil nu. --------------------
  verifie 1 "un secret avec un RETOUR A LA LIGNE final est refusé" \
    "WINGET_TOKEN=\$'ghp_unJeton\n' '$MOI'"
  verifie 1 "un secret avec une espace finale est refusé" \
    "WINGET_TOKEN='ghp_unJeton ' '$MOI'"
  verifie 1 "un secret avec une espace initiale est refusé" \
    "WINGET_TOKEN=' ghp_unJeton' '$MOI'"

  # ---- Contrôles NÉGATIFS : la règle doit rester étroite. ---------------------------------------
  verifie 0 "un secret présent et propre passe" \
    "WINGET_TOKEN=ghp_unJetonDeTest '$MOI'"
  verifie 0 "un secret présent, d une autre forme, passe aussi" \
    "WINGET_TOKEN=github_pat_unAutreJeton '$MOI'"

  # ---- Contrôle 3 : accès, joué par sonde. ------------------------------------------------------
  verifie 1 "un jeton qui ne s authentifie pas est refusé" \
    "WINGET_TOKEN=ghp_x WINGET_SONDE='$bac/sonde-muette' '$MOI' --verifie-l-acces"
  verifie 1 "un jeton authentifié mais AVEUGLE au paquet est refusé" \
    "WINGET_TOKEN=ghp_x WINGET_SONDE='$bac/sonde-aveugle' '$MOI' --verifie-l-acces"
  verifie 0 "un jeton qui voit le paquet passe" \
    "WINGET_TOKEN=ghp_x WINGET_SONDE='$bac/sonde-bonne' '$MOI' --verifie-l-acces"

  # Et le contrôle d accès ne doit pas se déclencher quand on ne le demande pas : sinon la garde
  # exigerait le réseau à chaque appel, y compris là où elle n a qu à lire une variable.
  verifie 0 "sans --verifie-l-acces, aucune sonde n est appelée" \
    "WINGET_TOKEN=ghp_x WINGET_SONDE='$bac/sonde-muette' '$MOI'"

  if [ "${echecs}" = 0 ]; then
    echo "Auto-test de la garde secret winget : OK (12 cas, dont 8 rouges)."
  else
    echo "Auto-test de la garde secret winget : ÉCHEC - la règle ne fait plus ce qu'elle promet."
  fi
  exit "${echecs}"
fi

jeton="${WINGET_TOKEN:-}"

# ---- 1. Présence ------------------------------------------------------------------------------
if [ -z "${jeton//[[:space:]]/}" ]; then
  echo "❌ WINGET_TOKEN est absent : aucune soumission ne peut partir vers winget-pkgs."
  echo
  echo "   Ce workflow ne se déclenche qu'à la main. L'avoir lancé veut dire qu'on attend une"
  echo "   soumission : sortir en vert sans rien soumettre annoncerait une publication qui n'a pas"
  echo "   eu lieu."
  echo
  echo "   Poser le secret (PAT « classic », scope public_repo, un fork echonuit/winget-pkgs) :"
  echo "     printf '%s' \"\$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
  echo
  echo "   Détail des prérequis : l'en-tête de .github/workflows/winget.yml."
  exit 1
fi

# ---- 2. Forme ---------------------------------------------------------------------------------
# `${jeton#"${jeton%%[![:space:]]*}"}` retire l'espace de tête, l'autre celui de queue.
sans_bords="${jeton#"${jeton%%[![:space:]]*}"}"
sans_bords="${sans_bords%"${sans_bords##*[![:space:]]}"}"
if [ "$sans_bords" != "$jeton" ]; then
  echo "❌ WINGET_TOKEN porte une espace ou un retour à la ligne autour de sa valeur."
  echo
  echo "   C'est le défaut le plus difficile à voir : le jeton est BON, il s'authentifie quand on le"
  echo "   teste à la main, et l'en-tête Authorization qu'il produit ici est invalide. L'échec"
  echo "   ressort alors très loin d'ici, sous la forme « le paquet n'existe pas »."
  echo
  echo "   Le reposer sans retour à la ligne (printf, pas echo) :"
  echo "     printf '%s' \"\$PAT\" | gh secret set WINGET_TOKEN --repo echonuit/vigiechiro-pr-companion"
  exit 1
fi

echo "Garde secret winget : OK (WINGET_TOKEN présent, sans espace parasite)."

# ---- 3. Accès, à la demande -------------------------------------------------------------------
if [ "${1:-}" = "--verifie-l-acces" ]; then
  verifier_l_acces "$jeton" || exit 1
fi
