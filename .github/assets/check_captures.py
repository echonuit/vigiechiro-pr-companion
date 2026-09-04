#!/usr/bin/env python3
"""Completude des captures d ecran (#86, porte du bash en #5229).

A partir du manifeste `captures.manifest`, ce garde verifie que :

1. chaque vue FXML sous `src/main/**/view/*.fxml` est declaree au manifeste ;
2. chaque vue declaree existe reellement dans le code ;
3. chaque capture declaree existe dans `.github/assets/` ;
4. chaque capture presente sur le disque est PRESENTEE dans la galerie `README.md` ;
5. chaque capture qu un outil ECRIT y est presentee aussi.

Leger : aucune compilation ni rendu, juste des fichiers.

## Pourquoi la regle 5 existe a cote de la regle 4

La 4 part des PNG **presents sur le disque**. Or ils ne naissent pas dans la branche : le job
`capturer` les produit sur `main`, APRES fusion. Sur une PR qui ajoute une capture, le fichier
n existe pas encore, la 4 ne voit rien, et la PR passe au vert de bonne foi ; le manque n apparait
qu une fois `main` deja rouge, et le cout est paye par TOUTES les PR ouvertes. Vecu sur #3119.

Ce qui EST dans la branche, c est le **code de l outil**. La 5 lit donc les noms qu il ecrit, en
ecartant les lignes de commentaire : elles citent volontiers des captures **passees**, remplacees
depuis, qui n existent plus et n ont rien a faire en galerie.

## La tolerance des sous-vues, et pourquoi elle reste etroite

Une sous-vue incluse par `fx:include` (ADR 2745) n a pas de capture propre : elle n est jamais
affichee seule, elle est rendue DANS la vue qui l inclut. Lui reclamer une entree ferait declarer
deux fois la meme image, qui divergerait ensuite. Mais une vue que **personne** n inclut la reclame
toujours, et un cas d auto-test tient ce bord-la.

Usage : python3 .github/assets/check_captures.py [--auto-test]
        CAPTURES_ASSETS=<dir> CAPTURES_SOURCES=<dir> python3 .github/assets/check_captures.py
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

ICI = pathlib.Path(__file__).resolve().parent
COMMENTAIRE = re.compile(r"^[\s]*(///|\*|//)")
CAPTURE = re.compile(r"apercu-[a-z0-9-]+\.png")


def racines(
    assets: pathlib.Path | None = None, sources: pathlib.Path | None = None
) -> tuple[pathlib.Path, pathlib.Path]:
    """Les deux racines, surchargeables ; `sources` se derive d `assets` comme en bash."""
    base = assets or pathlib.Path(os.environ.get("CAPTURES_ASSETS", ICI))
    if sources is not None:
        return base, sources
    depuis = os.environ.get("CAPTURES_SOURCES")
    if depuis is not None:
        return base, pathlib.Path(depuis)
    return base, base.parent.parent / "src" / "main" / "java"


def _vue_de(ligne: str) -> str:
    """La partie AVANT le premier « : », sans aucune blancheur."""
    return re.sub(r"\s", "", ligne.split(":", 1)[0])


def declarees(manifeste: pathlib.Path) -> set[str]:
    """Les vues que le manifeste declare, commentaires et lignes vides ecartes."""
    if not manifeste.is_file():
        return set()
    return {
        _vue_de(l)
        for l in manifeste.read_text(encoding="utf-8").splitlines()
        if l.strip() and not l.lstrip().startswith("#")
    }


def juger(assets: pathlib.Path | None = None, sources: pathlib.Path | None = None) -> int:
    """Les cinq regles, dans l ordre, et le code de sortie qui va avec."""
    base, source = racines(assets, sources)
    manifeste = base / "captures.manifest"
    galerie = base / "README.md"
    connues = declarees(manifeste)
    erreurs = 0

    fxml = sorted(p for p in source.rglob("*.fxml") if "/view/" in p.as_posix())
    inclusions = {p.read_text(encoding="utf-8", errors="ignore") for p in source.rglob("*.fxml")}

    # 1. Chaque *.fxml sous **/view/ doit etre declare, SAUF s il est inclus par une autre vue.
    for vue in fxml:
        rel = vue.relative_to(source).as_posix()
        if rel in connues:
            continue
        motif = re.compile(r'fx:include[^>]*source="' + re.escape(vue.name) + '"')
        if any(motif.search(t) for t in inclusions):
            continue
        print(f"❌ Vue sans capture déclarée au manifeste : {rel}")
        erreurs += 1

    # 2 + 3. Chaque vue declaree existe ; chaque capture declaree existe.
    nb_vues = 0
    for ligne in manifeste.read_text(encoding="utf-8").splitlines() if manifeste.is_file() else []:
        if ligne == "" or ligne.startswith("#"):
            continue
        vue = _vue_de(ligne)
        captures = ligne.split(":", 1)[1] if ":" in ligne else ligne
        nb_vues += 1
        if not (source / vue).is_file():
            print(f"❌ Vue déclarée au manifeste mais absente du code : {vue}")
            erreurs += 1
        pngs = captures.split()
        for png in pngs:
            if not (base / png).is_file():
                print(f"❌ Capture déclarée mais absente de .github/assets/ : {png} (vue {vue})")
                erreurs += 1
        if not pngs:
            print(f"❌ Vue sans aucune capture déclarée : {vue}")
            erreurs += 1

    presentees = galerie.read_text(encoding="utf-8", errors="ignore") if galerie.is_file() else ""

    # 4. Chaque capture du disque est PRESENTEE dans la galerie.
    nb_galerie = 0
    for png in sorted(base.glob("apercu-*.png")):
        nb_galerie += 1
        if png.name not in presentees:
            print(f"❌ Capture absente de la galerie README.md : {png.name}")
            erreurs += 1

    # 5. Chaque capture qu un outil ECRIT est presentee dans la galerie (#3129).
    ecrites: set[str] = set()
    for outil in source.rglob("Capture*.java"):
        for ligne in outil.read_text(encoding="utf-8", errors="ignore").splitlines():
            if not CAPTURE.search(ligne) or COMMENTAIRE.match(ligne):
                continue
            ecrites.update(CAPTURE.findall(ligne))
    nb_ecrites = 0
    for png in sorted(ecrites):
        nb_ecrites += 1
        if png not in presentees:
            print(f"❌ Capture écrite par un outil mais absente de la galerie README.md : {png}")
            erreurs += 1

    if erreurs > 0:
        print(f"Garde captures : {erreurs} problème(s) : voir ci-dessus.")
        return 1
    print(
        f"Garde captures : OK ({nb_vues} vues couvertes, {nb_galerie} captures sur disque et "
        f"{nb_ecrites} écrites par un outil, toutes présentées dans la galerie)."
    )
    return 0


VUE = "src/fr/exemple/vue/view/Ecran.fxml"


def _monte(bac: pathlib.Path) -> pathlib.Path:
    """Un bac COMPLET et coherent, que chaque cas abime ensuite d une seule facon."""
    import shutil

    shutil.rmtree(bac, ignore_errors=True)
    (bac / "assets").mkdir(parents=True)
    (bac / "src/fr/exemple/vue/view").mkdir(parents=True)
    (bac / VUE).touch()
    (bac / "assets/captures.manifest").write_text(
        "fr/exemple/vue/view/Ecran.fxml : apercu-ecran.png\n", encoding="utf-8"
    )
    (bac / "assets/apercu-ecran.png").touch()
    (bac / "assets/README.md").write_text("apercu-ecran.png\n", encoding="utf-8")
    return bac


def _outil(bac: pathlib.Path, corps: str) -> None:
    (bac / "src/fr/exemple/vue/outils").mkdir(parents=True, exist_ok=True)
    (bac / "src/fr/exemple/vue/outils/CaptureX.java").write_text(corps, encoding="utf-8")


CAS = (
    (0, "un manifeste complet et cohérent passe", None),
    (
        1,
        "une vue non déclarée au manifeste est refusée",
        lambda b: (b / "src/fr/exemple/vue/view/Oubliee.fxml").touch(),
    ),
    (1, "une vue déclarée mais absente du code est refusée", lambda b: (b / VUE).unlink()),
    (
        1,
        "une capture déclarée mais absente du disque est refusée",
        lambda b: (b / "assets/apercu-ecran.png").unlink(),
    ),
    (
        1,
        "une vue sans aucune capture déclarée est refusée",
        lambda b: (b / "assets/captures.manifest").write_text(
            "fr/exemple/vue/view/Ecran.fxml :\n", encoding="utf-8"
        ),
    ),
    (
        1,
        "une capture absente de la galerie est refusée",
        lambda b: (b / "assets/README.md").write_text("", encoding="utf-8"),
    ),
    (
        1,
        "une capture écrite par un outil, absente de la galerie, est refusée (#3129)",
        lambda b: _outil(b, 'class CaptureX { String f = "apercu-neuve.png"; }\n'),
    ),
    # Controles NEGATIFS : la regle doit rester etroite.
    (
        0,
        "un .fxml hors d'un dossier view/ ne déclenche pas",
        lambda b: (
            (b / "src/fr/exemple/ailleurs").mkdir(parents=True, exist_ok=True),
            (b / "src/fr/exemple/ailleurs/PasUneVue.fxml").touch(),
        ),
    ),
    (
        0,
        "commentaires et lignes vides du manifeste sont ignorés",
        lambda b: (b / "assets/captures.manifest").write_text(
            (b / "assets/captures.manifest").read_text(encoding="utf-8") + "# un commentaire\n\n",
            encoding="utf-8",
        ),
    ),
    # Sous-vues (ADR 2745) : incluses, donc couvertes par la capture de leur hote.
    (
        0,
        "une sous-vue incluse par une autre vue n'a pas besoin de sa propre capture (#2745)",
        lambda b: (
            (b / "src/fr/exemple/vue/view/Morceau.fxml").touch(),
            (b / VUE).write_text(
                '<fx:include fx:id="m" source="Morceau.fxml"/>\n', encoding="utf-8"
            ),
        ),
    ),
    # ... mais la tolerance reste ETROITE : une vue que personne n inclut la reclame toujours.
    (
        1,
        "une vue non incluse reste refusée, même si des inclusions existent ailleurs (#2745)",
        lambda b: (
            (b / "src/fr/exemple/vue/view/Morceau.fxml").touch(),
            (b / VUE).write_text(
                '<fx:include fx:id="m" source="UneAutre.fxml"/>\n', encoding="utf-8"
            ),
        ),
    ),
    (
        0,
        "une capture citée en commentaire seulement ne déclenche pas (#3129)",
        lambda b: _outil(
            b,
            "class CaptureX {\n  /// remplace apercu-disparue.png, une replique reconstruite\n}\n",
        ),
    ),
)


def _auto_test() -> int:
    """Les douze cas de la version bash, dont SEPT qui doivent rougir."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-captures-") as tmp:
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

    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test de la garde captures : OK")
    else:
        print(
            "Auto-test de la garde captures : ÉCHEC - les règles ne font plus ce qu'elles promettent."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
