#!/usr/bin/env python3
"""Ce que la suite de tests laisse derriere elle dans le dossier temporaire.

La suite abandonnait 13 730 repertoires dans `/tmp`, jusqu'a remplir un tmpfs de 16 Go et a se faire
echouer elle-meme sur un message qui envoie chercher une regression de code (#4737). Les trois plus
gros contributeurs sont traites ; il en reste 198 par passage, et rien ne les comptait.

**Le compte est DIFFERENTIEL.** Compter le total ferait rougir le reliquat de la veille, sur le poste
de n'importe qui, sans que rien ait change dans le depot : le garde serait desarme en une semaine, et
le depot aurait un dispositif de plus qui ne juge rien (ADR 2748).

**Il se lance sur une machine QUI NE FAIT QUE CA.** Le compte attribue a la suite tout ce qui
apparait pendant, y compris ce qu'une autre session cree au meme moment : mesure le 30 aout, une
classe seule a rendu 237 alors que la suite complete en laisse 198, parce qu'un second plan de
travail jouait ses tests en parallele. En CI le runner est dedie, donc la question ne se pose pas ;
en local, on lance la suite seul ou on ne croit pas le chiffre.

Usage : compte-les-reliquats.py --avant | --apres [--auto-test]
"""

from __future__ import annotations

import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent.parent / "adr"))
from _commun import rapporte  # noqa: E402

ADR = "4859"

# Ce que la suite cree, et rien d'autre : un `find` sans motif compterait les dossiers du systeme.
MOTIFS = ("vc-*", "vues-test*", "junit*", ".gluonmaps*", "vigiechiro-*", "import-zip-*")

# Le releve d'avant, ecrit ici pour que les deux pas de job se parlent sans variable d'environnement.
TEMOIN = pathlib.Path(tempfile.gettempdir()) / ".vigiechiro-reliquats-avant"


def presents(racine: pathlib.Path) -> set[str]:
    """Les noms des repertoires de test presents sous `racine`, a plat."""
    vus = set()
    for motif in MOTIFS:
        vus |= {chemin.name for chemin in racine.glob(motif) if chemin.is_dir()}
    return vus


def laisses(avant: set[str], apres: set[str]) -> set[str]:
    """Ce que la suite a AJOUTE : le reliquat d'avant ne lui appartient pas."""
    return apres - avant


def _autoTest() -> int:
    """Les temoins, sur un dossier jetable qui joue le role de /tmp."""
    with tempfile.TemporaryDirectory() as brut:
        racine = pathlib.Path(brut)
        (racine / "vc-vieux1").mkdir()
        (racine / "vc-vieux2").mkdir()
        (racine / "sans-rapport").mkdir()
        avant = presents(racine)
        assert avant == {"vc-vieux1", "vc-vieux2"}, avant

        (racine / "vc-neuf").mkdir()
        (racine / "junit-neuf").mkdir()
        apres = presents(racine)

        # LE temoin qui compte : le reliquat d'AVANT ne doit pas etre impute a la suite. Sans le
        # differentiel, ce compte vaudrait 4 et le garde accuserait un depot qui n'a rien fait.
        assert laisses(avant, apres) == {"vc-neuf", "junit-neuf"}, laisses(avant, apres)
        assert len(laisses(avant, apres)) == 2, "le compte est differentiel, jamais total"

        # Un dossier qui ne vient pas de la suite reste dehors, avant comme apres.
        assert "sans-rapport" not in presents(racine)

        # Une suite qui ne laisse rien rend zero, et non l'ensemble d'avant.
        assert laisses(avant, avant) == set()

    print("auto-test : 6 temoins verts")
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    racine = pathlib.Path(tempfile.gettempdir())
    if "--avant" in sys.argv:
        TEMOIN.write_text("\n".join(sorted(presents(racine))), encoding="utf-8")
        print(f"reliquats avant la suite : {len(presents(racine))} (relevé pour comparaison)")
        return 0
    if "--apres" not in sys.argv:
        raise SystemExit("Usage : compte-les-reliquats.py --avant | --apres [--auto-test]")
    if not TEMOIN.exists():
        # Sans le releve d'avant, le compte serait TOTAL, donc faux. Refuser vaut mieux que rendre un
        # chiffre plausible : c'est le defaut que ce garde existe pour empecher.
        raise SystemExit(
            f"Le relevé d'avant est absent ({TEMOIN}). Ce garde REFUSE de compter le total :\n"
            "il accuserait le reliquat de la veille. Lancez d'abord `--avant`."
        )
    avant = set(TEMOIN.read_text(encoding="utf-8").split())
    restes = sorted(laisses(avant, presents(racine)))
    return rapporte(ADR, "répertoires temporaires laissés par la suite", restes)


if __name__ == "__main__":
    sys.exit(main())
