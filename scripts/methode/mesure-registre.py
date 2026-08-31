#!/usr/bin/env python3
"""Recompte les motifs editoriaux sur le corpus, et eprouve ses compteurs avant de les croire.

`dev-docs/registre-editorial.md` dit pourquoi sept tics sont opposables et pourquoi les autres ne
le sont pas. Ses chiffres viennent d ici, et se refont : un chiffre recopie a la main vieillit au
premier fichier ajoute, et son vieillissement est silencieux.

**Pourquoi un auto-test sur un compteur, et pas seulement sur un garde.** Le resultat qui porte la
page est un ZERO : aucun connecteur lourd en ouverture de phrase. Or un compteur qui ne trouve rien
peut etre juste ou casse, et la sortie est la meme. Le banc fabrique six phrases dont il connait le
verdict, quatre qui DOIVENT compter et deux qui ne doivent pas, et exige les six.

    --verifie   : refuse si un connecteur lourd reapparait EN OUVERTURE de phrase.
    --auto-test : eprouve le compteur d ouvertures sur six phrases au verdict connu.

**Ce que `--verifie` garde, et ce qu il ne garde pas.** Il tient la seule affirmation de la page qui
puisse devenir fausse sans que personne ne s en apercoive : le zero. Les autres chiffres sont des
mesures DATEES, que l article A5 admet comme telles ; ils vieillissent en disant leur date. Le zero,
lui, est le motif pour lequel une regle n a pas ete retenue, et il se retournerait en silence.
"""

import argparse
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
# `openspec` entre ici avant de porter quoi que ce soit (#4513). La mesure qui l a decide : deux
# connecteurs lourds en ouverture, places dans `openspec/`, laissaient `--verifie` au VERT, quand le
# meme texte place dans `dev-docs/` le faisait rougir en nommant les deux occurrences. Le temoin
# positif est ce qui distingue un compteur aveugle d un corpus propre : sans lui, les deux rendent
# la meme sortie.
#
# La zone ne mesurera rien jusqu au premier artefact, et c est assume : le moment ou il arrive est
# precisement celui ou personne ne pensera a l ajouter.
ZONES = ("dev-docs", "docs", "brief", "openspec")

# La page que ce script alimente CITE les motifs en exemple. Sans cette exemption elle se compte
# elle-meme, et chaque chiffre monte du nombre de fois qu il est illustre : la premiere mesure a
# rendu 7 « mise en place » pour 6 reels, et 134 connecteurs pour 116. Un compteur qui lit sa
# propre demonstration mesure sa demonstration.
#
# C est la regle de l ADR 3645, « un detecteur textuel s exclut de son propre corpus », etablie sur
# un cas ou un detecteur certifiait gardees les cinq classes que sa propre documentation nommait.
# Le defaut a ete re-decouvert ici avant d etre reconnu : la decision existait.
TEXTES_DU_MOTIF = ("dev-docs/registre-editorial.md",)

CONNECTEURS = [
    "Cependant",
    "Toutefois",
    "Néanmoins",
    "Par ailleurs",
    "En outre",
    "De surcroît",
    "Par conséquent",
    "En conséquence",
    "De ce fait",
    "Dès lors",
    "En effet",
    "En somme",
    "En résumé",
    "En définitive",
    "De plus",
    "Dorénavant",
    "À cet égard",
]

FAMILLES = {
    "formules creuses": [
        "mettre en place",
        "mise en place",
        "mettre en œuvre",
        "il convient de",
        "dans ce cadre",
        "à l'ère de",
        "en conclusion",
        "besoin urgent",
    ],
    "mots surchargés": [
        "crucial",
        "primordial",
        "fondamental",
        "significatif",
        "révolutionnaire",
        "transformateur",
        "disruptif",
        "incontournable",
        "fascinant",
        "captivant",
    ],
    "remplissage": [
        "afin de pouvoir",
        "dans le but de",
        "du fait que",
        "à l'heure actuelle",
        "il est important de noter",
        "force est de constater",
    ],
    "fausse révélation": ["au fond,", "en réalité,", "la vraie question", "ce qui compte vraiment"],
    "calques de l'anglais": ["faire du sens", "au final", "en termes de", "digital", "opportunité"],
    "« nous » de commentaire": [
        "nous avons choisi",
        "nous devons",
        "nous pouvons voir",
        "comme nous l'avons vu",
        "on notera que",
    ],
    "annonce avant la chose": [
        "voici ce qu'il faut savoir",
        "entrons dans",
        "décortiquons",
        "sans plus attendre",
    ],
}


def ouvertures(connecteur: str, texte: str) -> int:
    """Les emplois du connecteur EN OUVERTURE de phrase, seule position qui fasse le tic.

    Employe au milieu d une phrase, un connecteur est du francais ordinaire. Quatre ouvertures
    comptent : le debut d une ligne, l apres-point, l apres-deux-points, et la tete de puce.
    """
    return len(re.findall(rf"(?:^|[.!?:]\s+|^[-*]\s+){re.escape(connecteur)}\b", texte, re.M))


def corpus(racine: pathlib.Path = None) -> tuple[str, int, int]:
    """Le texte des zones de prose, son nombre de lignes et son nombre de fichiers."""
    racine = racine or RACINE
    textes = []
    for z in ZONES:
        for f in sorted((racine / z).rglob("*.md")):
            if str(f.relative_to(racine)) in TEXTES_DU_MOTIF:
                continue
            textes.append(f.read_text(encoding="utf-8", errors="ignore"))
    tout = "\n".join(textes)
    return tout, len(tout.split("\n")), len(textes)


def auto_test() -> int:
    """Eprouve le compteur d ouvertures sur six phrases dont le verdict est connu."""
    cas = [
        ("en tête de ligne", "Cependant, la mesure tient.", 1),
        ("après un point", "La mesure tient. Cependant, elle coûte.", 1),
        ("après un deux-points", "Le constat : Cependant reste rare.", 1),
        ("en tête de puce", "- Cependant, le garde rougit.", 1),
        ("au MILIEU d une phrase", "La mesure tient, cependant elle coûte.", 0),
        ("dans un mot plus long", "Cependantesque n est pas un mot.", 0),
    ]
    echecs = []
    print("Auto-test du compteur d ouvertures :\n")
    for titre, texte, attendu in cas:
        obtenu = ouvertures("Cependant", texte)
        ok = obtenu == attendu
        print(f"  {'✔' if ok else '✘'} {titre:26} -> {obtenu} (attendu {attendu})")
        if not ok:
            echecs.append(titre)
    print()
    if echecs:
        print(
            f"ÉCHEC : {len(echecs)} cas. Un zéro rendu par ce compteur ne prouverait rien.",
            file=sys.stderr,
        )
        return 1
    print("Auto-test concluant : quatre positions comptent, deux ne comptent pas.")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Mesure des motifs éditoriaux du corpus")
    p.add_argument("--auto-test", action="store_true", help="éprouve le compteur d'ouvertures")
    p.add_argument(
        "--verifie",
        action="store_true",
        help="refuse un connecteur lourd revenu en ouverture de phrase",
    )
    args = p.parse_args()
    if args.auto_test:
        return auto_test()

    if args.verifie:
        tout, _, _ = corpus()
        revenus = [(c, ouvertures(c, tout)) for c in CONNECTEURS]
        revenus = [(c, n) for c, n in revenus if n]
        if revenus:
            print(
                f"{len(revenus)} connecteur(s) lourd(s) en ouverture de phrase :", file=sys.stderr
            )
            for c, n in revenus:
                print(f"  {c} : {n}", file=sys.stderr)
            print(
                "\ndev-docs/registre-editorial.md écarte ce motif AU MOTIF qu'il rend zéro.\n"
                "Le motif ne tient plus : réécrivez ces ouvertures, ou rouvrez la décision.",
                file=sys.stderr,
            )
            return 1
        print("Registre : aucun connecteur lourd en ouverture de phrase.")
        return 0

    tout, lignes, fichiers = corpus()
    print(f"corpus : {fichiers} fichiers, {lignes} lignes de " + ", ".join(ZONES) + "\n")

    print("connecteur              total   en ouverture")
    total_o = total_c = 0
    for c in CONNECTEURS:
        n = len(re.findall(rf"\b{re.escape(c.lower())}\b", tout, re.I))
        o = ouvertures(c, tout)
        total_c += n
        total_o += o
        if n:
            print(f"  {c:22} {n:5d}   {o:5d}")
    print(f"  {'TOTAL':22} {total_c:5d}   {total_o:5d}\n")

    for famille, motifs in FAMILLES.items():
        trouves = [(m, len(re.findall(rf"\b{m}\b", tout, re.I))) for m in motifs]
        trouves = [(m, n) for m, n in trouves if n]
        total = sum(n for _, n in trouves)
        detail = ", ".join(f"« {m} » ({n})" for m, n in trouves) or "aucun"
        print(f"  {famille:26} {total:4d}   {detail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
