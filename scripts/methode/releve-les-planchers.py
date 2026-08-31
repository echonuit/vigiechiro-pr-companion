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

# Les champs SE LISENT PAR LEUR NOM, et le motif tolere ceux qu il ne connait pas. #5014 a insere un
# champ `lus=` entre le numero et la mesure : un motif positionnel a cesse de reconnaitre la ligne,
# sans rien dire, et ce script a annonce que tout allait bien pendant qu il ne lisait rien (#5021).
# Apprendre le champ de #5014 aurait repare ce cas-la et laisse le suivant.
VERDICT = re.compile(
    r"^PLANCHER (\d+) \|.*?\bmesure=(\d+)\b.*?\bplancher=(\d+)\b.*?\bverdict=(\S+)\s*$", re.M)

# Ce qui ANNONCE un plancher, quel que soit le reste de la ligne. Sert a distinguer « le garde n en a
# rendu aucun » de « il en a rendu et je n ai pas su les lire ».
ANNONCE = re.compile(r"^PLANCHER \d+ \|", re.M)
CHAMP = re.compile(r"^floor: \d+$", re.M)


class VerdictIllisible(Exception):
    """Le garde a rendu des planchers, et aucun n a pu etre lu."""


def mesures() -> dict:
    """Ce que le garde rend, et rien d autre. Son code de sortie ne nous regarde pas ici.

    N avoir rien reconnu n est pas avoir constate que tout allait bien : si le garde ANNONCE des
    planchers et qu aucun ne se lit, ce script refuse au lieu de conclure. C est le defaut qui l a
    rendu muet le 2026-08-31, et son message rassurait (#5021).
    """
    rendu = subprocess.run([sys.executable, str(GARDE)], capture_output=True, text=True, cwd=RACINE)
    return lire(rendu.stdout)


def lire(sortie: str) -> dict:
    """Les planchers d une sortie de garde, ou un REFUS si elle en annonce sans qu aucun se lise.

    Fonction pure, et c est deliberé : le refus se mute et s eprouve ici, la ou il n exige aucun
    sous-processus. Il vivait dans `mesures()` et aucun cas ne pouvait le faire rougir (#5021).
    """
    lues = {m.group(1): (int(m.group(2)), int(m.group(3))) for m in VERDICT.finditer(sortie)}
    annoncees = len(ANNONCE.findall(sortie))
    if annoncees and not lues:
        raise VerdictIllisible(
            "REFUS : le garde annonce %d plancher(s) et aucun ne se lit. Son format a change, et ce\n"
            "script ne conclut pas sur ce qu il n a pas su lire." % annoncees)
    return lues


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

    # Les trois cas du format, et c est le troisieme qui manquait le 2026-08-31. Le motif se lit sur
    # une sortie fabriquee : lancer le vrai garde ne dirait rien d un format qu il n emet pas encore.
    ancien = "PLANCHER 4395 | mesure=3246 | plancher=3245 | verdict=a-relever"
    actuel = "PLANCHER 4395 | lus=? | mesure=3246 | plancher=3245 | verdict=a-relever"
    futur = "PLANCHER 4395 | lus=12 | source=git | mesure=3246 | plancher=3245 | verdict=a-relever"
    verifie("le format d origine se lit", bool(VERDICT.search(ancien)), True)
    verifie("le champ lus= inséré par #5014 ne fait plus perdre la ligne", bool(VERDICT.search(actuel)), True)
    verifie("un champ INCONNU de plus ne la fera pas perdre non plus", bool(VERDICT.search(futur)), True)
    verifie("la mesure lue est la bonne, et non le premier nombre venu",
            VERDICT.search(actuel).group(2), "3246")

    # Le sens NEGATIF, celui qui donne son prix aux trois precedents : n avoir rien reconnu n est pas
    # avoir constate que tout allait bien. Sans ce cas, un motif qui cesserait de reconnaitre quoi que
    # ce soit rendrait un dictionnaire vide et le script conclurait au calme, ce qu il a fait.
    annoncees = len(ANNONCE.findall(actuel))
    verifie("une ligne d un format inconnu reste ANNONCÉE, donc comptée comme illisible", annoncees, 1)
    verifie("une sortie sans aucun plancher n annonce rien, et c est legitime",
            len(ANNONCE.findall("rien à signaler")), 0)

    # Le REFUS lui-meme, et non son calcul. Sans ces trois cas, retirer la condition de refus laissait
    # le banc vert : le chemin qui rend ce script honnete n etait garde par rien.
    verifie("une sortie lisible rend ses planchers", len(lire(actuel)), 1)
    verifie("une sortie SANS plancher rend un dictionnaire vide, sans refuser", lire("rien"), {})
    try:
        lire("PLANCHER 4395 | format=inconnu sans mesure ni verdict")
        verifie("une annonce illisible REFUSE au lieu de conclure", "a conclu", "a refusé")
    except VerdictIllisible:
        verifie("une annonce illisible REFUSE au lieu de conclure", "a refusé", "a refusé")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    try:
        raise SystemExit(releve("--ecrire" in sys.argv))
    except VerdictIllisible as illisible:
        print(illisible, file=sys.stderr)
        raise SystemExit(2) from illisible
