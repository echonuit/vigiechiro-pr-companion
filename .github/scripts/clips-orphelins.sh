#!/usr/bin/env bash
# Désigne les clips de la pré-version roulante que le DERNIER tournage n'a pas produits.
#
# ⚠️ Pourquoi ce calcul vit dans un script, et pas en trois lignes de YAML. Sa sortie sert à
# **supprimer** des pièces publiées. Une erreur d'orientation ne rend pas une erreur : elle rend
# l'exact contraire, la liste de ce qu'il fallait garder, et le job la supprime sans rien dire. Un
# script se met à l'épreuve ; une ligne de `run:` ne s'éprouve qu'en production.
#
# ## Le défaut qu'il ferme
#
# `gh release upload --clobber` remplace et ajoute ; il ne retire rien. Un cas de recette renommé ou
# supprimé laisse donc son clip en ligne, et ce clip continue de montrer un comportement que le
# produit n'a plus. Constaté après #4099, qui a renommé deux cas : leurs deux anciens clips sont
# restés téléchargeables, montrant une fenêtre de compte rendu supprimée depuis #4091.
#
# ## Ce qu'il refuse
#
# Un dossier de tournage **vide**. Le tournage filme toutes les classes citant un cas : s'il n'a rien
# produit, c'est le tournage qui a échoué, et non tous les cas qui ont disparu. Désigner alors la
# pré-version entière serait la panne la plus coûteuse de ce script, et la plus silencieuse.
#
# Usage :
#   clips-orphelins.sh <dossier-des-clips-tournes>   # la liste EN LIGNE arrive sur stdin
#   clips-orphelins.sh --auto-test
#
# Sortie : un nom de clip orphelin par ligne, sur stdout. Exit 0 s'il y en a ou non, 2 sur refus.
set -uo pipefail

orphelins() { # <dossier des clips tournés> ; la liste en ligne sur stdin
    local dossier="${1:-}"
    local tournes enligne
    tournes=$(mktemp)
    enligne=$(mktemp)
    trap 'rm -f "$tournes" "$enligne"' RETURN

    find "$dossier" -maxdepth 1 -name '*.mp4' -printf '%f\n' 2>/dev/null | sort > "$tournes"

    if [ ! -s "$tournes" ]; then
        echo "REFUS : aucun clip .mp4 dans « $dossier »." >&2
        echo "Un tournage qui n'a rien produit ne doit pas conduire à vider la pré-version." >&2
        return 2
    fi

    # Seuls les .mp4 sont concernés : l'index de la pré-version n'est pas un clip.
    grep -E '\.mp4$' | sort -u > "$enligne"

    # -13 : ce qui est EN LIGNE sans être TOURNÉ. L'orientation est le coeur du script, et l'auto-test
    # la garde des deux côtés (un clip tourné absent de la pré-version ne doit jamais être désigné).
    comm -13 "$tournes" "$enligne"
}

auto_test() {
    local bac total=0 echecs=0
    bac=$(mktemp -d)
    trap 'rm -rf "$bac"' RETURN
    echo "AUTO-TEST"

    essai() { # <nom> <clips tournés, séparés par des espaces> <liste en ligne> <sortie attendue>
        local nom="$1" tournes="$2" enligne="$3" attendu="$4" obtenu code
        rm -rf "$bac/clips"
        mkdir -p "$bac/clips"
        local clip
        for clip in $tournes; do : > "$bac/clips/$clip"; done
        obtenu=$(printf '%s\n' $enligne | orphelins "$bac/clips" 2>/dev/null)
        code=$?
        [ "$code" -eq 2 ] && obtenu="REFUS"
        total=$((total + 1))
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-56s -> %s\n' "$nom" "${obtenu:-（vide）}"
        else
            printf '  [ÉCHEC] %-56s -> %s (attendu %s)\n' "$nom" "${obtenu:-（vide）}" "${attendu:-（vide）}"
            echecs=$((echecs + 1))
        fi
    }

    essai "rien à retirer quand la pré-version colle au tournage" \
        "a.mp4 b.mp4" "a.mp4 b.mp4" ""

    essai "un cas renommé laisse son ancien clip, qui est désigné" \
        "a.mp4 nouveau.mp4" "a.mp4 ancien.mp4 nouveau.mp4" "ancien.mp4"

    essai "l'index de la pré-version n'est jamais désigné" \
        "a.mp4" "a.mp4 index.md" ""

    # ⚠️ Le cas qui garde l'ORIENTATION. Avec `comm -23` au lieu de `-13`, ce clip fraîchement tourné
    # mais pas encore en ligne serait proposé à la suppression - donc exactement ce qu'il faut garder.
    essai "un clip tourné mais absent de la pré-version n'est pas désigné" \
        "a.mp4 tout-neuf.mp4" "a.mp4" ""

    # ⚠️ Le cas le plus coûteux : un tournage qui n'a rien produit ne vide pas la pré-version.
    essai "un dossier de tournage vide est refusé" \
        "" "a.mp4 b.mp4" "REFUS"

    echo "  $((total - echecs))/$total"
    [ "$echecs" -eq 0 ]
}

case "${1:-}" in
    --auto-test) auto_test ;;
    "") echo "usage : $0 <dossier-des-clips-tournes>   (liste en ligne sur stdin)" >&2; exit 64 ;;
    *) orphelins "$1" ;;
esac
