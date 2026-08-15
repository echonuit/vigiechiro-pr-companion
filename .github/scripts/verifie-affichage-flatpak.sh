#!/usr/bin/env bash
# Le Flatpak ne démarrait sur AUCUN bureau Wayland, et trois dispositifs l'ont laissé passer.
#
# ## Le défaut
#
# Le manifeste déclarait `--socket=wayland` et `--socket=fallback-x11`. Or `fallback-x11`
# n'accorde X11 que si Wayland est **absent**, tandis que JavaFX rend par GTK en X11 et ne
# parle pas Wayland. Sur une session Wayland - GNOME et KDE par défaut aujourd'hui - le bac à
# sable recevait donc `WAYLAND_DISPLAY` mais ni `DISPLAY` ni socket X, et l'application mourait
# au démarrage :
#
#     java.lang.UnsupportedOperationException: Unable to open DISPLAY
#         at com.sun.glass.ui.gtk.GtkApplication.<init>(GtkApplication.java:153)
#
# Le repli était exactement à l'envers : il retirait X11 précisément là où il est indispensable.
#
# ## Pourquoi rien ne l'a vu
#
#   - le pas « Démarrage réel du Flatpak » de `flatpak.yml` tourne avec
#     `-Dglass.platform=Headless`, donc SANS affichage : il ne peut pas voir un défaut
#     d'affichage. Il attestait « le paquet se charge », pas « le paquet s'affiche » ;
#   - les essais locaux passaient tous `--nosocket=wayland` (pour un écran virtuel), ce qui
#     active `fallback-x11` et prend PAR ACCIDENT le seul chemin qui fonctionne ;
#   - la construction d'essai du dépôt Flatpak construit, elle ne lance pas avec un écran.
#
# Trois verts, aucun ne portait sur la propriété qui a cassé.
#
# ## Ce que ce garde vérifie, et pourquoi statiquement
#
# Reproduire une session Wayland sur un runner coûterait un compositeur ; et le défaut n'est pas
# une subtilité de rendu, c'est une **politique de permissions** lisible dans le manifeste. On
# vérifie donc la politique, hors ligne et sans rien lancer :
#
#   1. `--socket=x11` est déclaré ;
#   2. `--socket=fallback-x11` ne l'est PAS ;
#   3. `x11` et `wayland` ne sont pas déclarés ensemble - `flatpak-builder-lint` refuse cette
#      combinaison (`finish-args-contains-both-x11-and-wayland`), donc une PR qui la porterait
#      serait rejetée en amont plutôt qu'ici.
#
# Ce garde ne remplace pas un démarrage avec écran ; il ferme la porte par laquelle ce
# défaut-ci est entré.
#
# Usage :
#   verifie-affichage-flatpak.sh [chemin-du-manifeste]
#   verifie-affichage-flatpak.sh --auto-test
#
# Refs #2191.

set -uo pipefail

MANIFESTE_DEFAUT="flatpak/fr.echonuit.VigieChiroCompanion.yml"

# Rend la liste des motifs de refus, un par ligne. Vide = conforme.
controler() {
    local fichier="$1"
    local sockets motifs=()

    if [ ! -f "$fichier" ]; then
        echo "le manifeste est introuvable : $fichier"
        return
    fi

    # Les sockets déclarées dans finish-args, une par ligne, sans le préfixe.
    sockets=$(grep -oE '^[[:space:]]*-[[:space:]]*--socket=[a-z0-9-]+' "$fichier" \
              | sed 's/.*--socket=//' | sort -u)

    if printf '%s\n' "$sockets" | grep -qx 'fallback-x11'; then
        motifs+=("--socket=fallback-x11 est déclaré : X11 ne serait accordé QUE si Wayland est absent, or JavaFX exige X11")
    fi

    if ! printf '%s\n' "$sockets" | grep -qx 'x11'; then
        motifs+=("--socket=x11 n'est pas déclaré : sans lui, aucune fenêtre ne s'ouvre sur une session Wayland")
    fi

    if printf '%s\n' "$sockets" | grep -qx 'x11' \
       && printf '%s\n' "$sockets" | grep -qx 'wayland'; then
        motifs+=("x11 et wayland sont déclarés ensemble : flatpak-builder-lint refuse finish-args-contains-both-x11-and-wayland")
    fi

    printf '%s\n' "${motifs[@]+"${motifs[@]}"}"
}

verdict() {
    local fichier="$1" motifs
    motifs=$(controler "$fichier" | grep -v '^$')

    if [ -z "$motifs" ]; then
        echo "✅ Affichage : --socket=x11 déclaré, sans fallback-x11 ni wayland concurrent."
        return 0
    fi

    echo "❌ Le Flatpak ne démarrerait pas sur une session Wayland (GNOME, KDE) :"
    while IFS= read -r motif; do
        echo "   - $motif"
    done <<< "$motifs"
    echo
    echo "   Rappel : JavaFX rend par GTK en X11. Sur Wayland, l'hôte expose XWayland, et"
    echo "   c'est --socket=x11 qui y donne accès. Voir l'entête de ce script."
    return 1
}

auto_test() {
    local tmp rouges=0 total=0 echecs=0
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' RETURN

    # nom | contenu des sockets | doit-il passer ?
    essai() {
        local nom="$1" corps="$2" attendu="$3" obtenu
        total=$((total + 1))
        [ "$attendu" = "rouge" ] && rouges=$((rouges + 1))
        # `%b` et non `%s` : les `\n` du cas d'essai doivent devenir de VRAIES lignes.
        # Avec `%s` ils restaient littéraux, le manifeste d'essai tenait sur une seule
        # ligne, et le garde n'y voyait qu'une socket sur deux - trois cas passaient
        # au vert sans rien vérifier. C'est l'auto-test qui l'a dit.
        printf 'finish-args:\n' > "$tmp/m.yml"
        printf '%b' "$corps" >> "$tmp/m.yml"
        if verdict "$tmp/m.yml" >/dev/null 2>&1; then obtenu=vert; else obtenu=rouge; fi
        if [ "$obtenu" = "$attendu" ]; then
            printf '  [OK   ] %-52s -> %s\n' "$nom" "$obtenu"
        else
            printf '  [ÉCHEC] %-52s -> %s (attendu %s)\n' "$nom" "$obtenu" "$attendu"
            echecs=$((echecs + 1))
        fi
    }

    echo "AUTO-TEST"
    essai "x11 seul (la forme corrigée)" \
          "  - --socket=x11\n  - --socket=pulseaudio\n" vert
    essai "le défaut d'origine : wayland + fallback-x11" \
          "  - --socket=wayland\n  - --socket=fallback-x11\n" rouge
    essai "fallback-x11 seul" \
          "  - --socket=fallback-x11\n" rouge
    essai "x11 ET fallback-x11 (le linter refuserait aussi)" \
          "  - --socket=x11\n  - --socket=fallback-x11\n" rouge
    essai "x11 et wayland ensemble" \
          "  - --socket=x11\n  - --socket=wayland\n" rouge
    essai "wayland seul" \
          "  - --socket=wayland\n" rouge
    essai "aucune socket d'affichage" \
          "  - --socket=pulseaudio\n" rouge
    essai "x11 avec un commentaire qui NOMME fallback-x11" \
          "  # surtout pas --socket=fallback-x11 ici\n  - --socket=x11\n" vert

    # Un manifeste absent doit rougir, pas passer en silence.
    total=$((total + 1)); rouges=$((rouges + 1))
    if verdict "$tmp/inexistant.yml" >/dev/null 2>&1; then
        printf '  [ÉCHEC] %-52s -> vert (attendu rouge)\n' "manifeste introuvable"
        echecs=$((echecs + 1))
    else
        printf '  [OK   ] %-52s -> rouge\n' "manifeste introuvable"
    fi

    echo
    echo "$total cas, dont $rouges qui DOIVENT rougir."
    if [ "$echecs" -eq 0 ]; then
        echo "Auto-test concluant."
        return 0
    fi
    echo "AUTO-TEST EN ÉCHEC ($echecs) : le garde est faux, ne pas se fier à son verdict."
    return 1
}

if [ "${1:-}" = "--auto-test" ]; then
    auto_test
    exit $?
fi

verdict "${1:-$MANIFESTE_DEFAUT}"
