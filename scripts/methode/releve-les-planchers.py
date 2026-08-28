#!/usr/bin/env python3
"""Releve les planchers a leur mesure, aux TROIS endroits ou un seuil s ecrit (issue #4683).

Un plancher vit a trois endroits : le champ `floor:` de l en-tete OKF, la balise du corps de son
ADR, et celle du journal des decisions. N en tenir qu un laisse les deux autres mentir, et c est
arrive : `DocumentationAJourTest` a rougi sur deux balises restees en arriere pendant que l en-tete
etait juste.

**Le piege qui a coute ce script.** La balise porte le chiffre avec une espace insecable - `3 136`
et non `3136`. Un remplacement litteral la manque, ET le `grep` de verification la manque aussi,
pour la meme raison : deux controles aveugles par le meme caractere. Ce script remplace le CONTENU
de la balise en preservant le separateur qu il y trouve, quel qu il soit.

**Il ne devine aucun chiffre** : il lance le garde et lit le verdict qu il rend. Un releve qui
recopierait une mesure faite ailleurs serait le defaut qu il repare.

Usage :
    python3 scripts/methode/releve-les-planchers.py            # ce qui serait releve
    python3 scripts/methode/releve-les-planchers.py --ecrire   # et l ecrire
    python3 scripts/methode/releve-les-planchers.py --auto-test
"""

import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
DECISIONS = RACINE / "dev-docs" / "decisions"
GARDE = RACINE / "scripts" / "adr" / "4395-renvois-en-javadoc.py"

VERDICT = re.compile(r"^PLANCHER (\d+) \| mesure=(\d+) \| plancher=(\d+) \| verdict=(\S+)$", re.M)
CHAMP = re.compile(r"^floor: \d+$", re.M)


def mesures() -> dict:
    """Ce que le garde rend, et rien d autre. Son code de sortie ne nous regarde pas ici."""
    rendu = subprocess.run([sys.executable, str(GARDE)], capture_output=True, text=True, cwd=RACINE)
    return {m.group(1): (int(m.group(2)), int(m.group(3))) for m in VERDICT.finditer(rendu.stdout)}


def cle_inventaire(adr: pathlib.Path) -> str | None:
    """La balise que l en-tete declare, celle qui porte le chiffre dans la prose."""
    trouve = re.search(r"^inv_key: (\S+)$", adr.read_text(encoding="utf-8"), re.M)
    return trouve.group(1) if trouve else None


def fichier_de(numero: str) -> pathlib.Path | None:
    for f in sorted(DECISIONS.glob(numero + "-*.md")):
        return f
    return None


def pose(texte: str, cle: str, valeur: int) -> str:
    """Le contenu de la balise, separateur de milliers PRESERVE tel qu il s y trouve."""
    motif = re.compile(r"(<!--inv:" + re.escape(cle) + r"-->)([^<]*)(<!--/inv-->)")

    def remplace(m):
        separateur = "".join(c for c in m.group(2) if not c.isdigit())[:1]
        chiffres = str(valeur)
        if separateur and len(chiffres) > 3:
            chiffres = chiffres[:-3] + separateur + chiffres[-3:]
        return m.group(1) + chiffres + m.group(3)

    return motif.sub(remplace, texte)


def releve(ecrire: bool) -> int:
    a_relever = {n: m for n, (m, s) in mesures().items() if m != s}
    if not a_relever:
        print("Aucun plancher à relever : chaque seuil colle à sa mesure.")
        return 0
    for numero, mesure in sorted(a_relever.items()):
        adr = fichier_de(numero)
        if adr is None:
            print("  ADR %s introuvable" % numero, file=sys.stderr)
            return 1
        cle = cle_inventaire(adr)
        touches = []
        for cible in (adr, DECISIONS / "index.md"):
            texte = cible.read_text(encoding="utf-8")
            neuf = CHAMP.sub("floor: %d" % mesure, texte) if cible == adr else texte
            if cle:
                neuf = pose(neuf, cle, mesure)
            if neuf != texte:
                touches.append(cible.name)
                if ecrire:
                    cible.write_text(neuf, encoding="utf-8")
        print("  ADR %s → %d   (%s)" % (numero, mesure, ", ".join(touches) or "rien à changer"))
    print("\n%d plancher(s) %s." % (len(a_relever), "relevé(s)" if ecrire else "à relever"))
    return 0


def auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print("  ✔ %s" % libelle)
        else:
            print("  ✘ %s : attendu %r, obtenu %r" % (libelle, attendu, obtenu))
            echecs = 1

    balise = "vaut <!--inv:essai-->3 136<!--/inv--> aujourd'hui"
    verifie("l'espace insécable du séparateur est préservée",
            pose(balise, "essai", 3160), "vaut <!--inv:essai-->3 160<!--/inv--> aujourd'hui")
    verifie("un chiffre sans séparateur le reste",
            pose("<!--inv:essai-->996<!--/inv-->", "essai", 1003), "<!--inv:essai-->1003<!--/inv-->")
    verifie("une autre balise n'est pas touchée",
            pose("<!--inv:autre-->12<!--/inv-->", "essai", 99), "<!--inv:autre-->12<!--/inv-->")
    # Le sens NEGATIF : sans ce cas, un `pose` qui rendrait toujours son entrée passerait le
    # troisième et n'aurait rien prouvé.
    verifie("la balise visée change bel et bien",
            pose("<!--inv:essai-->12<!--/inv-->", "essai", 99) != "<!--inv:essai-->12<!--/inv-->", True)
    verifie("le garde rend bien deux planchers", len(mesures()), 2)
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    raise SystemExit(releve("--ecrire" in sys.argv))
