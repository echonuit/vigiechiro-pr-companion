#!/usr/bin/env python3
"""Rend leur version committee aux apercus de CARTE dont seul le fond a change (porte du bash).

## Le probleme

Les apercus qui portent un fond OpenStreetMap changent a presque chaque execution de la CI sans
qu aucun code n ait bouge : `apercu-analyse-carte.png` a change dans 28 des 30 commits d apercus
precedant #3359. Trois couts, dont le dernier est le seul qui compte : l historique se remplit de
commits qui ne disent rien ; les PR d apercus **conflictent entre elles** en permanence, les PNG etant
binaires ; et le jour ou une **vraie** regression touche une de ces images, elle devient indiscernable
du bruit.

## Pourquoi un masque, et non plus un seuil

La premiere version comparait un **pourcentage de pixels** a un seuil de 4 %. Mesure faite apres
#3375, ce seuil ne pouvait pas tenir : le bruit de tuiles **seul** vaut jusqu a **23,8 %** de l image
sur `apercu-multisite-carte-pleine`. Aucun pourcentage global ne separe le bruit du signal, les deux
vivant dans la meme zone.

Ce que #3375 a rendu possible : **hors de la carte, la CI et un poste rendent au pixel pres**. La
bonne question n est donc plus « de combien ca differe ? » mais « quelque chose a-t-il change **hors**
de la carte ? », a tolerance **zero**.

Ce que le masque ne voit pas : un changement **a l interieur** de la carte. C est le prix, et c est
l arbitrage de l ADR 3068 - sauf qu il ne porte desormais que sur le rectangle.

## Les rectangles se DERIVENT, ils ne se recopient plus (#3439)

Ils sont deposes par le rendu dans un `apercu-<nom>.png.carte` a cote de chaque apercu, et ces
fichiers ne sont jamais committes. La liste etait recopiee a la main, et un rectangle recopie se
demode en silence : `apercu-sites-modale-point` declarait `18,331,464,457` pour une carte reellement
en `25,363,535,601`. Faux des DEUX cotes - 144 lignes de carte laissees dehors, ou le bruit repassait,
et 31 lignes de texte d aide effacees, ou une regression n aurait fait rougir personne.

## Les deux invocations d ImageMagick, et pourquoi les deux (#3370)

La **7** regroupe tous les outils sous `magick`, la **6** - celle du paquet d Ubuntu 24.04, donc du
runner - expose `compare` et `convert` comme commandes propres. La premiere version exigeait
`magick` : elle passait sur un poste en ImageMagick 7 et echouait en CI.

Usage : python3 .github/assets/filtrer_bruit_cartes.py
        python3 .github/assets/filtrer_bruit_cartes.py --auto-test
"""

from __future__ import annotations

import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ICI_DEFAUT = pathlib.Path(__file__).resolve().parent
CHIFFRES_DE_TETE = re.compile(r"^[0-9]*")


def outils() -> tuple[list[str], list[str]] | None:
    """Les deux invocations d ImageMagick, ou None s il est absent."""
    if shutil.which("magick"):
        return ["magick", "compare"], ["magick"]
    if shutil.which("compare") and shutil.which("convert"):
        return ["compare"], ["convert"]
    return None


def zones(ici: pathlib.Path) -> list[tuple[str, str]] | None:
    """Les apercus a fond cartographique et le rectangle de leur carte, ou None sur refus.

    Zero rectangle ne veut PAS dire « aucune carte » : le produit en porte quatre. Cela veut dire que
    le rendu n a pas depose ses zones, et un dispositif qui ne verifie plus rien doit le dire.
    """
    injectee = os.environ.get("CARTES_LISTE")
    if injectee:
        nom, rect = injectee.split()
        return [(nom, rect)]
    trouvees = []
    for zone in sorted(ici.glob("apercu-*.png.carte")):
        trouvees.append(
            (zone.name[: -len(".carte")], zone.read_text(encoding="utf-8").splitlines()[0])
        )
    return trouvees or None


def filtrer() -> int:
    """Le filtre, et le code de sortie qui va avec."""
    ici = pathlib.Path(os.environ.get("CARTES_ASSETS") or ICI_DEFAUT)
    cartes = zones(ici)
    if cartes is None:
        print(
            f"::error::Aucune zone de carte trouvée à côté des aperçus ({ici}/apercu-*.png.carte).",
            file=sys.stderr,
        )
        print(
            "::error::Ces fichiers sont déposés par le rendu : lancez "
            ".github/assets/capture_screenshots.py d'abord.",
            file=sys.stderr,
        )
        return 1

    invocations = outils()
    if invocations is None:
        print(
            "::error::ImageMagick est requis pour filtrer le bruit des cartes (paquet « imagemagick »).",
            file=sys.stderr,
        )
        return 1
    comparer, masquer = invocations

    restaurees = gardees = 0
    for nom, rect in cartes:
        chemin = f".github/assets/{nom}"

        if not (ici / nom).is_file():
            print(
                f"::error::{nom} est déclarée dans CARTES mais n'existe pas. Liste à corriger.",
                file=sys.stderr,
            )
            return 1
        if subprocess.run(["git", "diff", "--quiet", "--", chemin], check=False).returncode == 0:
            continue

        avant = pathlib.Path(tempfile.mkstemp(suffix=".png")[1])
        rendu = subprocess.run(["git", "show", f"HEAD:{chemin}"], capture_output=True, check=False)
        if rendu.returncode != 0:
            avant.unlink(missing_ok=True)
            print(f"  {nom} : nouvelle capture, gardée.")
            gardees += 1
            continue
        avant.write_bytes(rendu.stdout)

        # La carte est noircie des DEUX cotes : ce qui reste est tout le produit, et rien que lui.
        x1, y1, x2, y2 = rect.split(",")
        avant_masque = pathlib.Path(tempfile.mkstemp(suffix=".png")[1])
        apres_masque = pathlib.Path(tempfile.mkstemp(suffix=".png")[1])
        for source, cible in ((avant, avant_masque), (ici / nom, apres_masque)):
            subprocess.run(
                masquer
                + [
                    str(source),
                    "-fill",
                    "black",
                    "-draw",
                    f"rectangle {x1},{y1} {x2},{y2}",
                    str(cible),
                ],
                capture_output=True,
                check=False,
            )

        mesure = subprocess.run(
            comparer + ["-metric", "AE", str(avant_masque), str(apres_masque), "null:"],
            capture_output=True,
            text=True,
            check=False,
        )
        for f in (avant, avant_masque, apres_masque):
            f.unlink(missing_ok=True)
        # Les CHIFFRES DE TETE, comme en bash. C est le meme decoupage que `mesure_pixels` corrige,
        # et il est sans consequence ici : seule la comparaison a zero compte, et « 1.2034e+06 » rend
        # « 1 », donc non nul, donc gardee - le bon verdict. Un message d erreur rend une chaine vide,
        # qui se lit « comparaison impossible ».
        differents = CHIFFRES_DE_TETE.match((mesure.stdout + mesure.stderr).strip()).group(0)

        if not differents:
            print(f"  {nom} : comparaison impossible (dimensions différentes ?), gardée.")
            gardees += 1
        elif int(differents) == 0:
            subprocess.run(["git", "checkout", "--", chemin], check=False)
            print(f"  {nom:<46} hors carte : identique -> version committée rendue")
            restaurees += 1
        else:
            print(f"  {nom:<46} hors carte : {differents} pixel(s) changé(s) -> gardée")
            gardees += 1

    print(f"Bruit des cartes : {restaurees} rendue(s), {gardees} gardée(s).")
    return 0


def _auto_test() -> int:
    """Trois cas, et le vocabulaire est « rendue » ou « gardee », pas « rouge ».

    Ce script n est pas un garde qui refuse, c est un FILTRE qui distingue le bruit d un vrai
    changement. Son equivalent du rouge est `gardee` - le changement compte et ne doit pas etre avale.
    Plaquer le vocabulaire des autres auto-tests rendrait la ligne fausse en la rendant uniforme.
    """
    if outils() is None:
        print("auto-test ignore : ImageMagick absent.", file=sys.stderr)
        return 0
    _, dessiner = outils()

    echecs = cas = gardes = 0
    with tempfile.TemporaryDirectory(prefix="vc-cartes-") as tmp:
        bac = pathlib.Path(tmp)
        depot = bac / "depot"
        capture = depot / ".github/assets/apercu-test-carte.png"

        def monter() -> None:
            shutil.rmtree(depot, ignore_errors=True)
            (depot / ".github/assets").mkdir(parents=True)
            for commande in (
                ["git", "-C", str(depot), "init", "-q"],
                ["git", "-C", str(depot), "config", "user.email", "t@t"],
                ["git", "-C", str(depot), "config", "user.name", "t"],
            ):
                subprocess.run(commande, check=True)
            subprocess.run(
                dessiner
                + [
                    "-size",
                    "200x200",
                    "xc:white",
                    "-fill",
                    "gray",
                    "-draw",
                    "rectangle 50,50 150,150",
                    str(capture),
                ],
                check=True,
            )
            subprocess.run(["git", "-C", str(depot), "add", "-A"], check=True)
            subprocess.run(["git", "-C", str(depot), "commit", "-qm", "base"], check=True)

        def lancer() -> str:
            """« rendue » si la capture a ete remise a sa version committee, « gardee » sinon."""
            import contextlib
            import io

            ancien = os.getcwd()
            ancien_env = {k: os.environ.get(k) for k in ("CARTES_ASSETS", "CARTES_LISTE")}
            os.chdir(depot)
            os.environ["CARTES_ASSETS"] = str(depot / ".github/assets")
            os.environ["CARTES_LISTE"] = "apercu-test-carte.png 50,50,150,150"
            try:
                with (
                    contextlib.redirect_stdout(io.StringIO()),
                    contextlib.redirect_stderr(io.StringIO()),
                ):
                    filtrer()
                propre = subprocess.run(
                    ["git", "diff", "--quiet", "--", ".github/assets/apercu-test-carte.png"],
                    check=False,
                ).returncode
            finally:
                os.chdir(ancien)
                for cle, valeur in ancien_env.items():
                    if valeur is None:
                        os.environ.pop(cle, None)
                    else:
                        os.environ[cle] = valeur
            return "rendue" if propre == 0 else "gardee"

        def verifie(attendu: str, libelle: str) -> None:
            nonlocal echecs, cas, gardes
            cas += 1
            if attendu == "gardee":
                gardes += 1
            obtenu = lancer()
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendue {attendu}, obtenue {obtenu}")
                echecs = 1

        def dessine(*arguments: str) -> None:
            subprocess.run(dessiner + [str(capture), *arguments, str(capture)], check=True)

        monter()
        dessine("-fill", "black", "-draw", "rectangle 60,60 140,140")
        verifie("rendue", "un changement ENTIEREMENT dans la carte est du bruit")

        monter()
        dessine("-fill", "black", "-draw", "rectangle 10,10 30,20")
        verifie("gardee", "un changement HORS carte est garde, si petit soit-il")

        monter()
        dessine(
            "-fill",
            "black",
            "-draw",
            "rectangle 60,60 140,140",
            "-fill",
            "black",
            "-draw",
            "rectangle 10,10 30,20",
        )
        verifie("gardee", "du bruit de carte NE MASQUE PAS un changement hors carte")

    # Le compte se DERIVE. Cette ligne disait « les trois cas passent » en toutes lettres : elle
    # aurait continue a le dire sur un quatrieme cas.
    print()
    print(f"{cas} cas, dont {gardes} où le changement DOIT être gardé.")
    if echecs == 0:
        print("Auto-test : tous les cas passent.")
    else:
        print("Auto-test : ECHEC.", file=sys.stderr)
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    sys.exit(filtrer())
