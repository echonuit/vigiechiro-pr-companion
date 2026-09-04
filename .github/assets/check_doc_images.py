#!/usr/bin/env python3
"""Toute capture citee par la doc utilisateur existe ET est declaree (#3293, porte du bash en #5229).

Une capture referencee par une page de `docs/**/*.md` sous le motif `apercu-*.png` doit A LA FOIS
exister dans `.github/assets/` ET figurer dans `captures.manifest`, donc etre regeneree par
`capture-vues.yml`.

Ce garde complete `check_captures.py` : celui-la garantit qu aucune VUE n est livree sans capture,
celui-ci qu aucune PAGE ne cite une capture absente ou non regeneree.

## Les deux refus, et pourquoi ils sont distincts

Une capture **absente du disque** casse la page tout de suite ; une capture **presente mais non
declaree** se fige, periemee, et la page continue de montrer un ecran que le produit n a plus. Le
second est le silencieux, et c est pour lui que ce garde existe.

## Le cas d auto-test que le portage RETIRE, et pourquoi

La version bash portait un temoin du defaut de tube (#4642) : `grep -q` sort au premier match et
referme le tuyau, un `printf` encore en train d ecrire recoit SIGPIPE, et `pipefail` le propage ALORS
MEME que `grep` a trouve - le garde accusait alors une capture parfaitement declaree. Mesure d
alors : jamais sur 200 essais avec 142 captures, 40 fois sur 40 avec 50 000.

Ce temoin ne se porte pas, parce que le defaut qu il garde n existe plus : l appartenance se teste
ici sur un ensemble, sans tube, sans SIGPIPE et sans `pipefail`. Le reproduire demanderait de
relancer `grep` depuis un script dont tout l objet est de ne plus etre du shell, et il ne pourrait
plus rougir. Un garde qui ne peut pas rougir se retire.

Usage : python3 .github/assets/check_doc_images.py [--auto-test]
        DOC_IMAGES_RACINE=<dir> python3 .github/assets/check_doc_images.py
"""

from __future__ import annotations

import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
RACINE_INJECTEE = "DOC_IMAGES_RACINE"

ASSETS = pathlib.Path(".github/assets")
MANIFEST = ASSETS / "captures.manifest"
JETON = re.compile(r"^apercu-[a-z0-9-]+\.png$")
CITATION = re.compile(r"apercu-[a-z0-9-]+\.png")


def racine_de(racine: pathlib.Path | None = None) -> pathlib.Path:
    return racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))


def declarees(base: pathlib.Path) -> set[str]:
    """Les captures que le manifeste declare, apres le « : » de chaque ligne."""
    manifeste = base / MANIFEST
    if not manifeste.is_file():
        return set()
    trouvees = set()
    for ligne in manifeste.read_text(encoding="utf-8").splitlines():
        if ligne.lstrip().startswith("#"):
            continue
        # `sed 's/^[^:]*://'` : la ligne perd ce qui precede son premier « : », s il y en a un.
        apres = ligne.split(":", 1)[1] if ":" in ligne else ligne
        trouvees.update(j for j in apres.replace("\t", " ").split(" ") if JETON.match(j))
    return trouvees


def referencees(base: pathlib.Path) -> set[str]:
    """Les captures que les pages de `docs/**` citent."""
    docs = base / "docs"
    if not docs.is_dir():
        return set()
    trouvees = set()
    for page in docs.rglob("*.md"):
        trouvees.update(CITATION.findall(page.read_text(encoding="utf-8", errors="ignore")))
    return trouvees


def juger(racine: pathlib.Path | None = None) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    base = racine_de(racine)
    if not base.is_dir():
        # Un `cd` qui echoue laisserait la garde inventorier un AUTRE dossier, et le declarer
        # conforme. Ici, elle refuse.
        return 1
    citees = referencees(base)
    if not citees:
        print("Aucune capture référencée par la doc : rien à vérifier.")
        return 0

    connues = declarees(base)
    erreurs = 0
    for png in sorted(citees):
        if not (base / ASSETS / png).is_file():
            print(f"✗ {png} : référencée par la doc mais ABSENTE de {ASSETS}/")
            erreurs += 1
            continue
        if png not in connues:
            print(
                f"✗ {png} : présente mais NON déclarée dans captures.manifest "
                "(à ajouter pour la régénération)"
            )
            erreurs += 1

    if erreurs == 0:
        print(
            "✓ Toutes les captures référencées par la doc existent et sont déclarées au manifeste."
        )
        return 0
    print()
    print(f"✗ {erreurs} capture(s) de doc manquante(s).")
    print(
        "  Pour en ajouter une : rendu dans le Capture* de la feature + entrée dans "
        "captures.manifest (cf. #191)."
    )
    return 1


def _monte(bac: pathlib.Path) -> pathlib.Path:
    """Un bac COMPLET : une page qui reference une capture presente et declaree."""
    import shutil

    shutil.rmtree(bac, ignore_errors=True)
    (bac / ASSETS).mkdir(parents=True)
    (bac / "docs").mkdir(parents=True)
    (bac / MANIFEST).write_text(
        "fr/exemple/view/Ecran.fxml : apercu-exemple.png\n", encoding="utf-8"
    )
    (bac / ASSETS / "apercu-exemple.png").touch()
    (bac / "docs/page.md").write_text(
        "![Un écran](../.github/assets/apercu-exemple.png)\n", encoding="utf-8"
    )
    return bac


def _absente_du_disque(bac: pathlib.Path) -> None:
    (bac / ASSETS / "apercu-exemple.png").unlink()


def _non_declaree(bac: pathlib.Path) -> None:
    (bac / MANIFEST).write_text("fr/exemple/view/Ecran.fxml : apercu-autre.png\n", encoding="utf-8")
    (bac / ASSETS / "apercu-autre.png").touch()


def _jamais_citee(bac: pathlib.Path) -> None:
    (bac / ASSETS / "apercu-jamais-citee.png").touch()


def _doc_sans_capture(bac: pathlib.Path) -> None:
    (bac / "docs/page.md").unlink()


CAS = (
    (0, "une capture référencée, présente et déclarée passe", None),
    (1, "une capture référencée mais absente du disque est refusée", _absente_du_disque),
    (1, "une capture présente mais non déclarée au manifeste est refusée", _non_declaree),
    # Controles NEGATIFS : la regle ne vise que les captures citees par la doc.
    (0, "une capture que la doc ne cite pas ne déclenche pas", _jamais_citee),
    (0, "une doc sans aucune capture n'a rien à vérifier", _doc_sans_capture),
)


def _auto_test() -> int:
    """Les cinq cas de la version bash. Le sixieme, le temoin du tube, ne se porte pas."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-docimg-") as tmp:
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
                code = juger(bac)
            if code == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                echecs = 1

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0:
        print("Auto-test de la garde images de doc : OK")
    else:
        print(
            "Auto-test de la garde images de doc : ÉCHEC - les règles ne font plus ce qu'elles promettent."
        )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
