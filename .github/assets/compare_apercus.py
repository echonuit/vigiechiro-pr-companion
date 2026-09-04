#!/usr/bin/env python3
"""Montre, sur une PR, les ecrans que le diff va changer - et le dit quand il n y en a aucun.

Porte du bash en #5239.

## Le probleme

`capture-vues.yml` engendre les apercus sur chaque PR, puis `filtrer_bruit_cartes.py` rend leur
version committee a ceux dont seul le fond cartographique a bouge. Ce qui reste modifie dans le plan
de travail EST donc la liste des ecrans qui ont vraiment change.

Cette liste etait calculee, puis jetee : la publication ne se fait que sur `main`. Le seul endroit ou
l on VOIT un changement visuel etait le commit `chore(captures)`, c est-a-dire APRES la fusion. Qui
relit une PR devait croire sur parole que l ecran n avait pas bouge.

## Ce qu il produit

Un dossier d images `avant | apres` accolees, une par ecran modifie, et un index Markdown destine au
resume du job. Chaque ligne chiffre la part de pixels changes - le nombre ne juge pas, il oriente le
regard : 0,2 % sur un libelle n est pas 12 % sur une mise en page.

**Il ne juge rien et ne bloque rien.** Un ecran qui change est le resultat NORMAL d une PR qui touche
l interface ; ce qui manquait etait de le montrer.

## Le cas qui compte autant que les autres

**Aucun ecran modifie se DIT.** Sans cette ligne, une PR sans changement d ecran serait indiscernable
d une comparaison qui a echoue a s executer : deux silences identiques pour deux etats opposes
(ADR 2748).

Usage : python3 .github/assets/compare_apercus.py <dossier de sortie> [fichier…]
        python3 .github/assets/compare_apercus.py --auto-test
"""

from __future__ import annotations

import pathlib
import subprocess
import sys

ICI = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(ICI))

# La mesure des pixels est PARTAGEE avec l autre comparaison : elle portait le meme defaut aux deux
# endroits, corrige deux fois (#4295).
from mesure_pixels import part_changee


def accoler(
    avant: str | pathlib.Path, apres: str | pathlib.Path, sortie: str | pathlib.Path
) -> bool:
    """Compose l avant et l apres cote a cote. Faux si l une des deux images manque."""
    rendu = subprocess.run(
        ["convert", str(avant), str(apres), "+append", str(sortie)],
        capture_output=True,
        check=False,
    )
    return rendu.returncode == 0


def comparer(sortie: str | pathlib.Path, *fichiers: str) -> int:
    """Le dossier d images et l index, et le code de sortie qui va avec."""
    dossier = pathlib.Path(sortie)
    dossier.mkdir(parents=True, exist_ok=True)
    index = dossier / "index.md"

    # Aucun fichier n est PAS une panne : c est le cas le plus frequent, et il doit se lire comme tel.
    if not fichiers:
        index.write_text(
            "### Aperçus des écrans\n"
            "\n"
            "**Aucun écran ne change** : les aperçus régénérés sont identiques aux versions committées,\n"
            "bruit cartographique mis à part.\n",
            encoding="utf-8",
        )
        print("Aucun écran modifié.")
        return 0

    lignes = ["### Aperçus des écrans", "", "| Écran | Pixels changés | Aperçu |", "|---|---|---|"]

    manquants = traites = 0
    for chemin in fichiers:
        nom = pathlib.Path(chemin).stem
        avant = dossier / f"{nom}.avant.png"

        # L EXISTENCE se verifie AVANT de chercher l avant, et l auto-test a paye l ordre inverse :
        # un fichier ni dans git ni sur le disque etait annonce « ecran nouveau », c est-a-dire
        # presente comme un cas normal alors qu il signale un plan de travail incoherent.
        if not pathlib.Path(chemin).is_file():
            print(f"::warning::{chemin} est annoncé modifié mais absent du plan de travail.")
            manquants += 1
            continue

        # L avant vient de l index git : c est la version que la PR remplacerait. Un fichier NOUVEAU
        # n y est pas, et c est un cas normal - on le dit au lieu de le compter comme une erreur.
        rendu = subprocess.run(["git", "show", f"HEAD:{chemin}"], capture_output=True, check=False)
        if rendu.returncode != 0:
            avant.unlink(missing_ok=True)
            lignes.append(f"| `{nom}` | écran **nouveau** | pas d'avant à montrer |")
            traites += 1
            continue
        avant.write_bytes(rendu.stdout)

        part = part_changee(avant, chemin, 0, 2)
        if accoler(avant, chemin, dossier / f"{nom}.avant-apres.png"):
            lignes.append(f"| `{nom}` | {part} % | `{nom}.avant-apres.png` |")
        else:
            lignes.append(f"| `{nom}` | {part} % | ⚠️ montage impossible |")
            manquants += 1
        avant.unlink(missing_ok=True)
        traites += 1

    lignes += [
        "",
        f"_{traites} écran(s) modifié(s). Les images sont dans l'artefact « apercus-avant-apres »._",
    ]
    index.write_text("\n".join(lignes) + "\n", encoding="utf-8")

    print(f"{traites} écran(s) comparé(s), {manquants} problème(s).")
    return 0 if manquants == 0 else 1


def _auto_test() -> int:
    """Dix cas, dont la GRANDE capture que les autres ne peuvent pas voir."""
    import contextlib
    import io
    import shutil
    import tempfile

    if shutil.which("convert") is None:
        print("ImageMagick requis pour l'auto-test.", file=sys.stderr)
        return 2

    echecs = 0

    def verifie(libelle: str, attendu: str, obtenu: str) -> None:
        nonlocal echecs
        if attendu in obtenu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : « {attendu} » attendu, obtenu :")
            for l in obtenu.splitlines():
                print(f"      {l}")
            echecs = 1

    def joue(*arguments) -> str:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            comparer(*arguments)
        return tampon.getvalue()

    with tempfile.TemporaryDirectory(prefix="vc-apercus-") as tmp:
        bac = pathlib.Path(tmp)

        # 1. Aucun fichier : le cas le plus frequent, et celui qui doit se DIRE.
        sortie = joue(bac / "vide")
        verifie("aucun écran modifié le dit", "Aucun écran modifié", sortie)
        verifie(
            "et l'index le dit aussi",
            "Aucun écran ne change",
            (bac / "vide" / "index.md").read_text(encoding="utf-8"),
        )

        # 2. Un ecran modifie : l avant vient de git, donc on travaille dans un depot jetable.
        depot = bac / "depot"
        (depot / ".github/assets").mkdir(parents=True)
        for commande in (
            ["git", "-C", str(depot), "init", "-q"],
            ["git", "-C", str(depot), "config", "user.email", "auto@test"],
            ["git", "-C", str(depot), "config", "user.name", "auto"],
        ):
            subprocess.run(commande, check=True)
        ecran = depot / ".github/assets/ecran.png"
        subprocess.run(["convert", "-size", "80x40", "xc:white", str(ecran)], check=True)
        subprocess.run(["git", "-C", str(depot), "add", "-A"], check=True)
        subprocess.run(["git", "-C", str(depot), "commit", "-qm", "avant"], check=True)
        # 40 colonnes sur 80, soit exactement la moitie : `rectangle 0,0 39,39` et non `40,40`, qui
        # en couvrirait 41 et rendrait 51,25 % - l auto-test l a montre.
        subprocess.run(
            [
                "convert",
                "-size",
                "80x40",
                "xc:white",
                "-fill",
                "black",
                "-draw",
                "rectangle 0,0 39,39",
                str(ecran),
            ],
            check=True,
        )

        ici = pathlib.Path.cwd()
        try:
            import os

            os.chdir(depot)
            sortie = joue(bac / "change", ".github/assets/ecran.png")
            verifie("un écran modifié est comparé", "1 écran(s) comparé(s), 0 problème(s)", sortie)
            verifie(
                "sa part de pixels est chiffrée",
                "50.00 %",
                (bac / "change" / "index.md").read_text(encoding="utf-8"),
            )
            if (bac / "change" / "ecran.avant-apres.png").is_file():
                print("  ✔ le montage avant/après existe")
            else:
                print("  ✘ le montage avant/après manque")
                echecs = 1

            # 3. Un ecran NOUVEAU : pas d avant dans git, et ce n est pas une panne.
            subprocess.run(
                ["convert", "-size", "80x40", "xc:blue", str(depot / ".github/assets/neuf.png")],
                check=True,
            )
            sortie = joue(bac / "neuf", ".github/assets/neuf.png")
            verifie(
                "un écran nouveau est annoncé comme tel",
                "1 écran(s) comparé(s), 0 problème(s)",
                sortie,
            )
            verifie(
                "et l'index le nomme",
                "écran **nouveau**",
                (bac / "neuf" / "index.md").read_text(encoding="utf-8"),
            )

            # 4. Un fichier annonce mais absent : celui-la est un probleme, et il se compte.
            sortie = joue(bac / "absent", ".github/assets/ecran.png", ".github/assets/fantome.png")
            verifie("un fichier absent est signalé", "1 problème(s)", sortie)

            # 5. Une GRANDE capture, celle que les quatre cas precedents ne peuvent pas voir.
            #
            # Trente-trois captures du depot depassent le million de pixels, et sur celles-la
            # ImageMagick ecrit ses comptes en notation scientifique. Le calcul rendait « ? », ou
            # pire 0,00 % quand plus d un million de pixels changeaient. Des images de 80 × 40 ne
            # peuvent PAS montrer ce defaut : la taille est le coeur de ce cas.
            grand = depot / ".github/assets/grand.png"
            subprocess.run(["convert", "-size", "1100x1094", "xc:white", str(grand)], check=True)
            subprocess.run(["git", "-C", str(depot), "add", "-A"], check=True)
            subprocess.run(["git", "-C", str(depot), "commit", "-qm", "grand avant"], check=True)
            subprocess.run(["convert", "-size", "1100x1094", "xc:black", str(grand)], check=True)
            sortie = joue(bac / "grand", ".github/assets/grand.png")
            verifie(
                "une grande capture est comparée", "1 écran(s) comparé(s), 0 problème(s)", sortie
            )
            verifie(
                "et son écart vaut 100, pas « ? » ni 0",
                "100.00 %",
                (bac / "grand" / "index.md").read_text(encoding="utf-8"),
            )
        finally:
            os.chdir(ici)

    if echecs == 0:
        print(
            "Auto-test de la comparaison des aperçus : OK (10 cas, dont la grande capture et 1 rouge vérifié)."
        )
    else:
        print("Auto-test de la comparaison des aperçus : ÉCHEC.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) < 2 or not sys.argv[1]:
        print(
            f"usage : {pathlib.Path(sys.argv[0]).name} <dossier de sortie> [fichier…]",
            file=sys.stderr,
        )
        sys.exit(2)
    sys.exit(comparer(sys.argv[1], *sys.argv[2:]))
