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
from _commun import rapporte, sort_si_contrat_demande

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

    # Le refus de conclure, qui est la raison d'etre du garde (ADR 2213). Sans ce temoin, remplacer
    # `if not TEMOIN.exists()` par `if False` laissait les six autres verts : le garde rendait alors
    # le compte TOTAL, c'est-a-dire exactement le defaut qu'il existe pour empecher. Trouve en
    # passe 6 de la cloture de #4859, par mutation.
    global TEMOIN
    garde, argv = TEMOIN, sys.argv
    try:
        with tempfile.TemporaryDirectory() as brut:
            TEMOIN = pathlib.Path(brut) / "releve-jamais-ecrit"
            sys.argv = ["compte-les-reliquats.py", "--apres"]
            try:
                main()
            except SystemExit as refus:
                assert "REFUSE" in str(refus), str(refus)
            else:
                raise AssertionError("sans releve d'avant, le garde a CONCLU au lieu de refuser")
    finally:
        TEMOIN, sys.argv = garde, argv

    print("auto-test : 7 temoins verts")
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
    # Ce garde ne declare PAS `lus`, et c est une decision, non un oubli (issue #5015).
    #
    # Ses deux populations valent legitimement ZERO. Sur un runner vierge dont la suite nettoie
    # bien, ni le temoin d avant ni les repertoires presents ne portent quoi que ce soit. Or
    # `lus=0` REFUSE : declarer l une ou l autre ferait rougir la reussite meme de ce garde, dont
    # le but est qu il ne reste rien. Aucune de ses populations n a de zero anormal.
    #
    # Ce que cela coute est ecrit ici pour ne pas se reperdre : si `MOTIFS` cessait d apparier,
    # ce garde rendrait « aucun reliquat » en silence et rien ne le dirait. La cecite est assumee,
    # faute d une population qui la revelerait.
    return rapporte(ADR, "répertoires temporaires laissés par la suite", restes)


CONTRAT = {
    "geste": "repertoire temporaire laisse par la suite de tests",
    "population": "les repertoires temporaires du systeme",
    "dispositif": "cliquet",
    "seuil": "4, polarite=descend",
    "temoin": "scripts/methode/compte-les-reliquats.py --auto-test",
    "decision": "ADR 4859",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(main())
