#!/usr/bin/env python3
"""Une cible cliquable declaree fait au moins 24 x 24 px (WCAG 2.5.8, niveau AA).

Une cible trop petite se rate. Le cout ne tombe pas sur tout le monde de la meme facon : il tombe
sur qui vise moins bien, sur un ecran tactile, ou avec une souris tenue d une main qui tremble.

**Ce que ce garde lit, et ce qu il ne lit pas.** Il lit les dimensions DECLAREES sur un noeud
cliquable dans un FXML : `prefHeight`, `minHeight`, `prefWidth`, `minWidth`. Il ne calcule rien.
La plupart des boutons du produit ne declarent aucune taille - la leur vient de leur police et de
leur marge interne, et vaut environ 34 px. Ces boutons-la sont hors de portee de ce garde, et le
dire est la moitie de son travail (article A3).

Une classe CSS peut aussi poser une hauteur. Le garde ne la lit pas : rien dans la feuille ne dit
si la classe habille un bouton ou un separateur, et la mesure du 2026-08-23 l a montre - huit
dimensions sous 24 px, toutes portees par des elements presentationnels (un separateur de 1 px,
une jauge de 14 px, une puce de compte rendu de 10 px).

Exit 0 si aucune cible declaree ne passe sous le seuil, 1 sinon.
"""

import argparse
import pathlib
import re
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import PRODUCTION_ANCREE, cas_d_auto_test, sort_si_contrat_demande

VUES = PRODUCTION_ANCREE

SEUIL = 24.0

# Les noeuds qu on clique. Un `Label` ou une `HBox` n en est pas un, meme habille comme un bouton.
CLIQUABLE = re.compile(
    r"<(Button|ToggleButton|CheckBox|MenuButton|RadioButton|Hyperlink)\b([^>]*)", re.S
)

DIMENSIONS = ("prefHeight", "minHeight", "prefWidth", "minWidth")


def fautes(racine: pathlib.Path | None = None) -> list[str]:
    """Les cibles declarees sous le seuil, une par ligne."""
    racine = racine or VUES
    trouvees = []
    for f in sorted(racine.rglob("*.fxml")):
        texte = f.read_text(encoding="utf-8")
        for m in CLIQUABLE.finditer(texte):
            attrs = m.group(2)
            nom = re.search(r'fx:id="([^"]+)"', attrs)
            for dim in DIMENSIONS:
                v = re.search(rf'{dim}="([\d.]+)"', attrs)
                if v and 0 < float(v.group(1)) < SEUIL:
                    trouvees.append(
                        f"{f.name} : {m.group(1)} "
                        f"« {nom.group(1) if nom else 'sans fx:id'} » déclare {dim}={v.group(1)}, "
                        f"sous le seuil de {SEUIL:.0f} px"
                    )
    return trouvees


def _auto_test() -> int:
    # Les cas passent par le fonds (#5461) : leur expression est DIFFEREE, donc celle qui lève
    # nomme son cas au lieu d'arrêter le témoin. La fabrique l'appelle AUSSITÔT, ce qui préserve
    # l'ordre d'évaluation, plusieurs de ces auto-tests réécrivant leur fixture entre deux cas.
    verifie, echecs = cas_d_auto_test()
    joues = [0]

    def cas_de(libelle, juger):
        joues[0] += 1
        verifie(libelle, juger, True)

    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)
        (r / "sain.fxml").write_text(
            '<VBox><Button fx:id="a" prefHeight="34.0" text="Bien"/></VBox>', encoding="utf-8"
        )
        cas_de(
            "une cible de 34 px passe",
            lambda: fautes(r) == [],
        )

        (r / "petit.fxml").write_text(
            '<VBox><Button fx:id="b" prefHeight="16.0" text="Trop bas"/></VBox>', encoding="utf-8"
        )
        f = fautes(r)
        cas_de(
            "une cible de 16 px rougit",
            lambda: len(f) == 1 and "prefHeight=16.0" in f[0],
        )
        cas_de(
            "le refus nomme le contrôle",
            lambda: any("« b »" in x for x in f),
        )

        # LE cas qui borne la portee : un noeud NON cliquable a le droit d etre petit, et le dire
        # est ce qui empeche le garde de crier sur une jauge ou un separateur.
        (r / "petit.fxml").write_text(
            '<VBox><Region fx:id="c" prefHeight="1.0"/><Label prefHeight="14.0"/></VBox>',
            encoding="utf-8",
        )
        cas_de(
            "un noeud non cliquable a le droit d être petit",
            lambda: fautes(r) == [],
        )

        (r / "petit.fxml").write_text(
            '<VBox><CheckBox fx:id="d" minWidth="12.0"/></VBox>', encoding="utf-8"
        )
        cas_de(
            "la largeur compte aussi, sur une case à cocher",
            lambda: any("minWidth=12.0" in x for x in fautes(r)),
        )

        (r / "petit.fxml").unlink()
        (r / "sain.fxml").unlink()
        cas_de(
            "sans aucune vue, le garde ne prétend rien",
            lambda: fautes(r) == [],
        )

    if echecs():
        print("\nDes cas en échec : le garde ne dit pas ce qu'il vérifie.", file=sys.stderr)
        return 1
    print(f"\n{joues[0]} cas : le garde voit une cible trop petite et laisse passer le reste.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Taille minimale des cibles cliquables déclarées")
    p.add_argument("--auto-test", action="store_true", help="éprouve le refus sur des fixtures")
    args = p.parse_args()
    if args.auto_test:
        return _auto_test()
    trouvees = fautes()
    for f in trouvees:
        print(f"  {f}", file=sys.stderr)
    if trouvees:
        print(f"\n{len(trouvees)} cible(s) déclarée(s) sous {SEUIL:.0f} px.", file=sys.stderr)
        return 1
    print(f"Cibles cliquables : aucune dimension déclarée sous {SEUIL:.0f} px.")
    return 0


CONTRAT = {
    "geste": "cible cliquable declaree sous 24 x 24 px",
    "population": "PRODUCTION",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/adr/verifie_taille_des_cibles.py --auto-test",
    "decision": "WCAG 2.5.8 niveau AA, sans ADR",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(main())
