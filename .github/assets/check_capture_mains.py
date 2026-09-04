#!/usr/bin/env python3
"""Aucun outil de capture n est oublie du script de rendu (#3293, porte du bash en #5229).

Tout outil de capture - une classe `*/outils/Capture*.java` portant une methode `main` - doit etre
enregistre dans le tableau `MAINS` de `capture-screenshots.sh`. Sinon ses PNG ne seraient JAMAIS
regeneres par le workflow `capture-vues` : la capture existerait au manifeste, donc
`check_captures.py` passerait, mais elle se figerait, perimee, sans que rien ne le signale.

L inverse se verifie aussi : chaque entree `MAINS` pointe une classe qui existe encore.

Ce garde complete `check_captures.py` (aucune VUE sans capture) et `check_doc_images.py` (aucune PAGE
citant une capture absente). Leger : aucune compilation ni rendu, juste des fichiers.

## Le troisieme controle, qui n a l air de rien

La langue et le fuseau doivent rester EPINGLES (#3389). Sans eux, JavaFX resout les libelles par
defaut de `ButtonType` via `Locale.getDefault()` et formate les horodatages dans le fuseau du
systeme : la galerie montre alors la machine qui l a rendue plutot que le produit, et c est ce qui a
laisse « Cancel » dans une galerie francophone. Le controle est STATIQUE, parce que le verifier par
le rendu couterait deux passes completes de capture.

Il lit `capture-screenshots.sh`, qui reste du shell : ce garde y cherche des chaines, il ne le lance
pas.

Usage : python3 .github/assets/check_capture_mains.py [--auto-test]
        MAINS_ASSETS=<dir> MAINS_SOURCES=<dir> python3 .github/assets/check_capture_mains.py
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

ICI = pathlib.Path(__file__).resolve().parent
FQCN = re.compile(r'"(fr\.univ_amu\.iut\.[A-Za-z0-9_.]+)"')
EPINGLAGES = ("-Duser.language=fr", "-Duser.country=FR", "-Duser.timezone=Europe/Paris")


def racines(
    assets: pathlib.Path | None = None, sources: pathlib.Path | None = None
) -> tuple[pathlib.Path, pathlib.Path]:
    """Les deux racines, surchargeables : l auto-test vise une arborescence jetable.

    `sources` se derive d `assets` quand elle n est pas donnee, exactement comme la version bash :
    poser la premiere seule deplace donc les deux.
    """
    base = assets or pathlib.Path(os.environ.get("MAINS_ASSETS", ICI))
    depuis = os.environ.get("MAINS_SOURCES")
    if sources is not None:
        return base, sources
    if depuis is not None:
        return base, pathlib.Path(depuis)
    return base, base.parent.parent / "src" / "main" / "java"


def declares(script: pathlib.Path) -> set[str]:
    """Les entrees MAINS : les litteraux FQCN entre guillemets, seul endroit ou ils paraissent."""
    if not script.is_file():
        return set()
    return set(FQCN.findall(script.read_text(encoding="utf-8", errors="ignore")))


def juger(assets: pathlib.Path | None = None, sources: pathlib.Path | None = None) -> int:
    """Les trois controles, et le code de sortie qui va avec."""
    base, source = racines(assets, sources)
    script = base / "capture-screenshots.sh"
    mains = declares(script)
    erreurs = 0

    # 1. Chaque outil de capture porteur d un `main` doit figurer dans MAINS.
    nb_outils = 0
    outils = sorted(p for p in source.rglob("Capture*.java") if p.parent.name == "outils")
    for fichier in outils:
        texte = fichier.read_text(encoding="utf-8", errors="ignore")
        if not re.search(r"static void main", texte):
            continue  # helpers sans main (ex. socles) : ignores
        fqcn = str(fichier.relative_to(source).with_suffix("")).replace(os.sep, ".")
        nb_outils += 1
        if fqcn not in mains:
            print(
                f"❌ Outil de capture absent de MAINS (ses PNG ne seraient jamais régénérés) : {fqcn}"
            )
            erreurs += 1

    # 2. Chaque entree MAINS doit pointer une classe qui existe (renommage, suppression).
    for fqcn in sorted(mains):
        if not (source / (fqcn.replace(".", "/") + ".java")).is_file():
            print(f"❌ Entrée MAINS sans classe correspondante (renommée/supprimée ?) : {fqcn}")
            erreurs += 1

    # 3. La langue et le fuseau restent epingles.
    texte = script.read_text(encoding="utf-8", errors="ignore") if script.is_file() else ""
    for propriete in EPINGLAGES:
        if propriete not in texte:
            print(
                "❌ Épinglage perdu dans capture-screenshots.sh (la galerie redeviendrait "
                f"dépendante de la machine) : {propriete}"
            )
            erreurs += 1

    if erreurs > 0:
        print(f"Garde MAINS captures : {erreurs} problème(s) : voir ci-dessus.")
        return 1
    print(
        f"Garde MAINS captures : OK ({nb_outils} outils de capture, tous enregistrés dans MAINS)."
    )
    return 0


EXEMPLE = "fr/univ_amu/iut/exemple/outils/CaptureExemple.java"
LOCALISATION = "-Duser.language=fr -Duser.country=FR -Duser.timezone=Europe/Paris"


def _outil(bac: pathlib.Path, relatif: str, avec_main: bool = True) -> None:
    chemin = bac / "src" / relatif
    chemin.parent.mkdir(parents=True, exist_ok=True)
    corps = "class X { public static void main(String[] a) {} }\n" if avec_main else "class X { }\n"
    chemin.write_text(corps, encoding="utf-8")


def _monte(bac: pathlib.Path) -> pathlib.Path:
    """Un bac COMPLET et coherent : un outil declare, langue et fuseau epingles."""
    import shutil

    shutil.rmtree(bac, ignore_errors=True)
    (bac / "assets").mkdir(parents=True)
    _outil(bac, EXEMPLE)
    (bac / "assets" / "capture-screenshots.sh").write_text(
        'MAINS=(\n  "fr.univ_amu.iut.exemple.outils.CaptureExemple"\n)\n'
        f'LOCALISATION="{LOCALISATION}"\n',
        encoding="utf-8",
    )
    return bac


def _outil_oublie(bac: pathlib.Path) -> None:
    _outil(bac, "fr/univ_amu/iut/exemple/outils/CaptureOubliee.java")


def _classe_disparue(bac: pathlib.Path) -> None:
    (bac / "src" / EXEMPLE).unlink()


def _capture_sans_main(bac: pathlib.Path) -> None:
    _outil(bac, "fr/univ_amu/iut/exemple/outils/CaptureSocle.java", avec_main=False)


def _capture_hors_outils(bac: pathlib.Path) -> None:
    _outil(bac, "fr/univ_amu/iut/exemple/ailleurs/CaptureHorsOutils.java")


def _langue_desepinglee(bac: pathlib.Path) -> None:
    script = bac / "assets" / "capture-screenshots.sh"
    garde = [l for l in script.read_text(encoding="utf-8").splitlines() if "user.language" not in l]
    script.write_text("\n".join(garde) + "\n", encoding="utf-8")


CAS = (
    (0, "un outil enregistré dans MAINS passe", None),
    (1, "un outil de capture absent de MAINS est refusé", _outil_oublie),
    (1, "une entrée MAINS sans classe correspondante est refusée", _classe_disparue),
    # Controles NEGATIFS : la regle vise les outils AVEC un main, sous outils/, nommes Capture*.
    (0, "un Capture* sans main (socle partagé) ne déclenche pas", _capture_sans_main),
    (0, "un Capture* hors d'un dossier outils/ ne déclenche pas", _capture_hors_outils),
    # L epinglage de la langue et du fuseau (#3389). Le cas nominal est couvert par `_monte`.
    (0, "un script qui épingle langue et fuseau passe", None),
    (1, "un script qui n'épingle plus la langue est refusé", _langue_desepinglee),
)


def _auto_test() -> int:
    """Les sept cas de la version bash, dont TROIS qui doivent rougir."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-mains-") as tmp:
        for attendu, libelle, degrade in CAS:
            cas += 1
            if attendu != 0:
                rouges += 1
            bac = _monte(pathlib.Path(tmp) / "bac")
            if degrade is not None:
                degrade(bac)
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                code = juger(bac / "assets", bac / "src")
            if code == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                echecs = 1

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test de la garde MAINS captures : OK")
    else:
        print(
            "Auto-test de la garde MAINS captures : ÉCHEC - les règles ne font plus ce qu'elles promettent."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
