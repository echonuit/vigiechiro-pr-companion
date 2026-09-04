#!/usr/bin/env python3
"""Le Flatpak ne demarrait sur AUCUN bureau Wayland (#2191, porte du bash en #5231).

## Le defaut

Le manifeste declarait `--socket=wayland` et `--socket=fallback-x11`. Or `fallback-x11` n accorde
X11 que si Wayland est **absent**, tandis que JavaFX rend par GTK en X11 et ne parle pas Wayland.
Sur une session Wayland - GNOME et KDE par defaut aujourd hui - le bac a sable recevait donc
`WAYLAND_DISPLAY` mais ni `DISPLAY` ni socket X, et l application mourait au demarrage sur un
`UnsupportedOperationException: Unable to open DISPLAY`. Le repli etait exactement a l envers : il
retirait X11 precisement la ou il est indispensable.

## Pourquoi rien ne l a vu

Le pas « Demarrage reel du Flatpak » tourne avec `-Dglass.platform=Headless`, donc SANS affichage :
il attestait « le paquet se charge », pas « le paquet s affiche ». Les essais locaux passaient tous
`--nosocket=wayland`, ce qui active `fallback-x11` et prend PAR ACCIDENT le seul chemin qui
fonctionne. Et la construction d essai construit sans lancer. Trois verts, aucun ne portait sur la
propriete qui a casse.

## Ce que ce garde verifie, et pourquoi statiquement

Reproduire une session Wayland sur un runner couterait un compositeur ; et le defaut n est pas une
subtilite de rendu, c est une **politique de permissions** lisible dans le manifeste :

1. `--socket=x11` est declare ;
2. `--socket=fallback-x11` ne l est PAS ;
3. `x11` et `wayland` ne sont pas declares ensemble - `flatpak-builder-lint` refuse cette
   combinaison, donc une PR qui la porterait serait rejetee en amont plutot qu ici.

Ce garde ne remplace pas un demarrage avec ecran ; il ferme la porte par laquelle ce defaut-ci est
entre.

Usage :
  python3 .github/scripts/verifie_affichage_flatpak.py [chemin-du-manifeste]
  python3 .github/scripts/verifie_affichage_flatpak.py --auto-test
"""

from __future__ import annotations

import pathlib
import re
import sys

MANIFESTE_DEFAUT = "flatpak/fr.echonuit.VigieChiroCompanion.yml"
# Une socket DECLAREE : un tiret de liste, puis l option. Un commentaire qui NOMME `fallback-x11`
# ne commence pas par un tiret, et un cas d auto-test tient ce bord-la.
SOCKET = re.compile(r"^[ \t]*-[ \t]*--socket=([a-z0-9-]+)", re.M)


def controler(fichier: str | pathlib.Path) -> list[str]:
    """Les motifs de refus, un par entree. Liste vide = conforme."""
    chemin = pathlib.Path(fichier)
    if not chemin.is_file():
        return [f"le manifeste est introuvable : {fichier}"]

    sockets = set(SOCKET.findall(chemin.read_text(encoding="utf-8", errors="ignore")))
    motifs = []
    if "fallback-x11" in sockets:
        motifs.append(
            "--socket=fallback-x11 est déclaré : X11 ne serait accordé QUE si Wayland est absent, "
            "or JavaFX exige X11"
        )
    if "x11" not in sockets:
        motifs.append(
            "--socket=x11 n'est pas déclaré : sans lui, aucune fenêtre ne s'ouvre sur une session Wayland"
        )
    if "x11" in sockets and "wayland" in sockets:
        motifs.append(
            "x11 et wayland sont déclarés ensemble : flatpak-builder-lint refuse "
            "finish-args-contains-both-x11-and-wayland"
        )
    return motifs


def verdict(fichier: str | pathlib.Path) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    motifs = controler(fichier)
    if not motifs:
        print("✅ Affichage : --socket=x11 déclaré, sans fallback-x11 ni wayland concurrent.")
        return 0

    print("❌ Le Flatpak ne démarrerait pas sur une session Wayland (GNOME, KDE) :")
    for motif in motifs:
        print(f"   - {motif}")
    print()
    print("   Rappel : JavaFX rend par GTK en X11. Sur Wayland, l'hôte expose XWayland, et")
    print("   c'est --socket=x11 qui y donne accès. Voir l'entête de ce script.")
    return 1


# (nom, corps des sockets, verdict attendu)
CAS = (
    ("x11 seul (la forme corrigée)", "  - --socket=x11\n  - --socket=pulseaudio\n", "vert"),
    (
        "le défaut d'origine : wayland + fallback-x11",
        "  - --socket=wayland\n  - --socket=fallback-x11\n",
        "rouge",
    ),
    ("fallback-x11 seul", "  - --socket=fallback-x11\n", "rouge"),
    (
        "x11 ET fallback-x11 (le linter refuserait aussi)",
        "  - --socket=x11\n  - --socket=fallback-x11\n",
        "rouge",
    ),
    ("x11 et wayland ensemble", "  - --socket=x11\n  - --socket=wayland\n", "rouge"),
    ("wayland seul", "  - --socket=wayland\n", "rouge"),
    ("aucune socket d'affichage", "  - --socket=pulseaudio\n", "rouge"),
    # Le cas qui garde la DECLARATION contre la MENTION : un commentaire qui nomme fallback-x11
    # n en declare pas une.
    (
        "x11 avec un commentaire qui NOMME fallback-x11",
        "  # surtout pas --socket=fallback-x11 ici\n  - --socket=x11\n",
        "vert",
    ),
)


def _auto_test() -> int:
    """Les huit manifestes d essai, puis le manifeste ABSENT, qui doit rougir."""
    import contextlib
    import io
    import tempfile

    total = rouges = echecs = 0
    print("AUTO-TEST")
    with tempfile.TemporaryDirectory(prefix="vc-flatpak-") as tmp:
        manifeste = pathlib.Path(tmp) / "m.yml"
        for nom, corps, attendu in CAS:
            total += 1
            if attendu == "rouge":
                rouges += 1
            manifeste.write_text("finish-args:\n" + corps, encoding="utf-8")
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                code = verdict(manifeste)
            obtenu = "vert" if code == 0 else "rouge"
            if obtenu == attendu:
                print(f"  [OK   ] {nom:<52} -> {obtenu}")
            else:
                print(f"  [ÉCHEC] {nom:<52} -> {obtenu} (attendu {attendu})")
                echecs += 1

        # Un manifeste absent doit rougir, pas passer en silence.
        total += 1
        rouges += 1
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = verdict(pathlib.Path(tmp) / "inexistant.yml")
        if code == 0:
            print(f"  [ÉCHEC] {'manifeste introuvable':<52} -> vert (attendu rouge)")
            echecs += 1
        else:
            print(f"  [OK   ] {'manifeste introuvable':<52} -> rouge")

    print()
    print(f"{total} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs}) : le garde est faux, ne pas se fier à son verdict.")
    return 1


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(verdict(sys.argv[1] if len(sys.argv) > 1 else MANIFESTE_DEFAUT))
