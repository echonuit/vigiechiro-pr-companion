#!/usr/bin/env python3
"""La typographie du CORPS d une pull request (#4453, porte du bash en #5231).

Le corps de la PR est ce qu atteint quiconque remonte depuis `git log` : la competence
`clore-une-issue` en fait, avec le corps de l issue, l un des deux textes qui se relisent dans six
mois sans le fil. C est donc de la prose visible au sens de l article A31.

Cette prose echappait pourtant a toute regle opposable, pour une raison de mecanique : A31
declenchait « avant d etre commise », et un corps de PR n est jamais commis.

## Ce qu elle refuse, et sur quelle mesure

Quatre defauts, chacun adosse a une decision deja prise ailleurs. Les trois premiers ont ete mesures
sur les dix-huit corps de PR du depot le 2026-08-25, le quatrieme sur ceux du 2026-08-26 : le tiret
cadratin de prose (ADR 2843, 2 lignes dans 1 PR), l apostrophe courbe (ADR 4368, 0), l elision sans
apostrophe (meme regle que le garde du TITRE, 0), et la fermeture ecrite en francais (#4350, 2 en une
seule session).

Le quatrieme differe des trois autres : il ne refuse pas une typographie, il refuse une PROMESSE QUI
NE SERA PAS TENUE. « Ferme #N » ne ferme rien, et surtout ne signale rien - la demande fusionne
verte, l issue reste ouverte, et on ne s en apercoit qu en balayant les issues. #4350 avait ecarte un
garde parce qu il aurait cherche ce qui MANQUE et rougi sur tout lot d un EPIC ; celui-ci cherche ce
qui est PRESENT, forme qui n est jamais legitime, et laisse « Refs #N » tranquille.

## Ce qu elle ne voit PAS, et c est assume

**Les quatre tics rhetoriques** de `CONTRIBUTING.md`. Aucun motif ne les distingue d une phrase
legitime : la grille sert a relire, les sept tics servent a refuser.

**Un corps vide.** Qu une PR doive porter un corps est une decision que personne n a prise ici, et ce
garde ne la prendra pas a sa place. Son auto-test l epingle, pour qu on le sache voulu.

**Le corps d une ISSUE.** Une issue n a pas de controle qui puisse rougir : un atelier declenche sur
`issues` finirait rouge dans un onglet que personne n ouvre.

Usage : python3 .github/scripts/verifie_corps_pr.py "<corps>"   (sortie 0 si conforme, 1 sinon)
"""

from __future__ import annotations

import re
import sys
import unicodedata

# Construits, jamais ecrits : sans cela le garde des cadratins compterait la prose de son propre
# garde, et celui des fichiers compterait l apostrophe courbe comme un emploi.
CADRATIN = chr(0x2014)
COURBE = chr(0x2019)
OUVRANT, FERMANT = chr(0x00AB), chr(0x00BB)

# La prose seule. Un corps de PR colle des sorties de commande, des extraits de code et des motifs
# d expression reguliere : les lire comme de la prose ferait rougir le garde sur un corps juste.
CITE = re.compile(
    "|".join(
        [
            "`[^`]*`",  # code en ligne
            re.escape(OUVRANT) + r"\s*" + re.escape(CADRATIN) + r"\s*" + re.escape(FERMANT),
        ]
    )
)
# L elision sans apostrophe, MEME regle que le garde du titre, et meme retrecissement (#4483) : une
# lettre isolee precedee d un NOMBRE est un symbole d unite, et une MAJUSCULE isolee au fil d une
# phrase est un symbole, jamais une elision. Le detail de la mesure est dans `verifie_titre_pr.py`,
# qui porte la regle mere.
BORNE = r"[^\w'" + COURBE + r"]"

# Ce qui SUIT decide, et non ce qui precede (#4786). Une elision est suivie d un MOT francais, qui
# peut porter trait d union et apostrophe. Un symbole est suivi de ce qu il etiquette : un chemin, un
# compte entre parentheses, un identifiant.
MOT = r"[^\W\d_](?:[^\W\d_]|['" + COURBE + r"-])*"

# Une decoration Markdown peut s ouvrir entre la lettre et le mot. Ce qui separe les deux cas est de
# savoir si la lettre isolee est DEDANS la decoration - un symbole etiquete - ou DEHORS, le mot seul
# etant decore, qui est une elision.
OUVRE = r"(?:\*\*|\*|__|_|«\s*|\[)?"

# La fin du mot admet la decoration FERMANTE, sans quoi « L **amendement** » se couperait sur
# l etoile et la detection retomberait.
FIN_MOT = r"(\s|[*_)\]»,.;:!?]|$)"

ELISION_MIN = re.compile(
    r"(^" + BORNE + r"?|[^\d]" + BORNE + r")([ldnscjmt]|qu) +" + OUVRE + MOT + FIN_MOT
)
ELISION_MAJ = re.compile(r"(^" + BORNE + r"?|[^\w]\s)([LDNSCJMT]|Qu) +" + OUVRE + MOT + FIN_MOT)


def sans_accents(texte: str) -> str:
    """Le texte deplie, accents retires : « Resout » et « Résout » sont le meme verbe.

    Ecrire les deux formes dans le motif aurait double chaque alternative, et la moitie aurait
    vieilli en silence le jour ou une flexion manque.
    """
    return "".join(c for c in unicodedata.normalize("NFD", texte) if not unicodedata.combining(c))


FERMETURE_FR = re.compile(
    # `close` est ABSENT de cette liste, et c est le point delicat : « Close #N » est un mot-cle
    # ANGLAIS valide, que la forge honore. Le refuser rendrait le garde faux dans le sens qui coute
    # le plus cher - il ferait recrire en francais ce qui marchait.
    r"\b(ferme|fermee?s?|clot|clos|resout|resoud|resolue?s?|corrige|corrigee?s?"
    r"|repare|reparee?s?|termine|terminee?s?|acheve|achevee?s?)"
    r"\s+(l[ae']\s*)?(issue\s+)?#\d{1,5}\b",
    re.I,
)

REMEDES = {
    "tiret cadratin": (
        "Écrivez un deux-points, une virgule ou un tiret simple. La zone de prose du dépôt est à\n"
        "  tolérance zéro, et ce corps est publié sur la forge : il ne se retire pas.\n"
        "  Cf. dev-docs/decisions/typographie-cliquet-plutot-que-nettoyage.md"
    ),
    "apostrophe courbe": (
        "Le dépôt n'écrit que l'apostrophe droite ('), et ce corps ne fait pas exception.\n"
        "  Cf. dev-docs/decisions/le-depot-n-ecrit-qu-une-apostrophe.md"
    ),
    "fermeture en français": (
        "Écrivez le mot-clé en anglais : « Closes #N », « Fixes #N » ou « Resolves #N ». La forge ne\n"
        "  reconnaît qu'eux, et une fermeture écrite en français ne ferme rien ni ne signale rien : la\n"
        "  demande fusionne verte et l'issue reste ouverte. Pour renvoyer SANS clore - un lot dans un\n"
        "  EPIC - écrivez « Refs #N » ou « Rattaché à #N », qui ne prétendent rien."
    ),
    "élision sans apostrophe": (
        "Rétablissez l'apostrophe : « l'ADR », « d'une nuit », « n'est pas », « qu'il ».\n"
        "  L'habitude d'amputer les apostrophes vient du quoting shell des messages de commit ;\n"
        "  elle survit à la disparition de sa cause, et ce corps-ci se lit sur la forge.\n"
        "  Si la ligne CITE la sortie d'un outil, ce n'est pas une élision : mettez-la dans un bloc\n"
        "  clôturé par trois accents graves, que ce garde épargne entièrement. Indenter ne suffit\n"
        "  pas, une indentation portant aussi bien de la prose."
    ),
}


def juger(corps: str) -> int:
    """Les quatre refus, ligne par ligne, et le code de sortie qui va avec."""
    fautes = []
    dans_un_bloc = False
    for numero, ligne in enumerate(corps.splitlines(), 1):
        if ligne.lstrip().startswith("```"):
            dans_un_bloc = not dans_un_bloc
            continue
        if dans_un_bloc:
            continue
        prose = CITE.sub("", ligne)
        if CADRATIN in prose:
            fautes.append((numero, "tiret cadratin", ligne.strip()))
        if COURBE in prose:
            fautes.append((numero, "apostrophe courbe", ligne.strip()))
        if ELISION_MIN.search(prose) or ELISION_MAJ.search(prose):
            fautes.append((numero, "élision sans apostrophe", ligne.strip()))
        if FERMETURE_FR.search(sans_accents(prose)):
            fautes.append((numero, "fermeture en français", ligne.strip()))

    if not fautes:
        print("Corps conforme.")
        return 0

    print(f"::error::Le corps de la PR porte {len(fautes)} defaut(s) de typographie.")
    print()
    for numero, defaut, ligne in fautes:
        print(f"  ligne {numero} : {defaut}")
        print(f"    {ligne[:110]}")
    print()
    for defaut in dict.fromkeys(d for _, d, _ in fautes):
        print(f"  {defaut} : {REMEDES[defaut]}")
        print()
    print(
        "Ce corps est de la prose visible au sens de l'article A31 : il se relit dans six mois sans le"
    )
    print(
        "fil, et il est publié dès qu'il part. La grille complète est dans la compétence humaniser ;"
    )
    print("ce garde ne tient que ce qu'un motif peut voir.")
    return 1


TROISQUOTES = "`" * 3

# (attendu, corps, libelle)
CAS = (
    (0, "Ce corps dit ce qui a ete fait, et pourquoi.", "un corps conforme passe"),
    (1, f"Le seuil tient {CADRATIN} la mesure le dit.", "un cadratin de prose est refusé"),
    (1, f"L{COURBE}apostrophe courbe est refusée.", "une apostrophe courbe est refusée"),
    (1, "Le garde tient, l ADR le dit.", "une élision sans apostrophe est refusée"),
    # #4546. La forge ne reconnait que `close`, `fix` et `resolve`.
    (1, "Ce que ce lot fait. Ferme #4502.", "« Ferme #N » est refusé"),
    # ACCENTUE, et c est le cas qui eprouve le depliage : le motif est ecrit sans accent.
    (1, "Clôt #4502.", "« Clôt #N » aussi, et il éprouve le dépliage des accents"),
    (1, "Résout le #4502.", "et la forme avec article, accentuée elle aussi"),
    (1, "Corrige l issue #4502.", "et celle qui nomme l issue"),
    # Les controles NEGATIFS, et ce sont eux qui rendent ce refus utilisable.
    (0, "Refs #4502", "« Refs #N » ne prétend rien et passe"),
    (0, "Rattache a #4502", "« Rattaché à #N » non plus"),
    (0, "Voir #4502 pour le detail", "un simple renvoi passe"),
    (0, "Le correctif de #4502 tient", "un renvoi au fil d une phrase passe"),
    (0, "#4502 a pose la question", "un renvoi en tete de phrase passe"),
    # `close` est un mot-cle ANGLAIS valide : le refuser ferait recrire en francais ce qui marchait.
    (0, "Close #4502", "le mot-clé anglais reste vert"),
    (0, "Fixes #4502", "et ses deux jumeaux"),
    (0, "Resolves #4502", "aussi"),
    # Les exemptions. Un corps de PR colle des sorties de commande et cite des glyphes.
    (
        0,
        f"Sortie collee :\n{TROISQUOTES}\nverdict {CADRATIN} 0 regle, l ADR absente\n{TROISQUOTES}",
        "un bloc de code cloture est épargné",
    ),
    (
        0,
        f"Le glyphe de valeur absente s'ecrit « {CADRATIN} ».",
        "le glyphe entre guillemets français est épargné",
    ),
    (
        0,
        f"Le motif `[-{CADRATIN}]` accepte les deux formes.",
        "le glyphe en code en ligne est épargné",
    ),
    # Controles NEGATIFS : la regle de l elision reste etroite, comme dans le garde du titre.
    (0, "Le point C3 et le carre A1 sont distincts.", "un code de point ne déclenche pas"),
    (0, "Le n° 4 est traite.", "un numéro ne déclenche pas"),
    (0, "Deux semeurs prennent l'entree legere.", "une élision correcte ne déclenche pas"),
    # #4483. Une lettre isolee employee comme SYMBOLE n est pas une elision.
    (
        0,
        "Le decoupage se fait a 5 s reelles, mesure a 10,5 s.",
        "un symbole d'unité après un nombre ne déclenche pas",
    ),
    (
        0,
        "Un ecran qui compose N lignes paie N requetes.",
        "une majuscule-symbole au fil d'une phrase ne déclenche pas",
    ),
    (0, "    participant S as Service", "un alias de diagramme ne déclenche pas"),
    (0, "Le seuil est -S info, et non warning.", "un drapeau de commande ne déclenche pas"),
    # La contrepartie : en TETE de phrase la majuscule reste vue.
    (
        1,
        "Le garde tient. L amendement le dit.",
        "une élision majuscule en tête de phrase reste refusée",
    ),
    # #4786. Ce qui SUIT decide.
    (0, "M scripts/adr/truc.py", "une sortie d'outil en tête de ligne ne déclenche pas"),
    (0, "  M scripts/adr/truc.py", "la même, indentée, ne déclenche pas non plus"),
    (
        0,
        "Le seuil monte. N observation(s) ont ete vues.",
        "un symbole suivi d'un compte entre parenthèses ne déclenche pas",
    ),
    # Et la contrepartie du meme rejet : ce qui suit reste un MOT.
    (
        1,
        "Le garde tient. L auto-test le prouve.",
        "une élision suivie d'un mot composé reste refusée",
    ),
    (1, "Le garde tient. D abord, la mesure.", "une élision suivie d'une virgule reste refusée"),
    # #4786, second volet : la decoration Markdown ouverte entre la lettre et le mot.
    (
        1,
        "Le garde tient. L **amendement** le dit.",
        "une élision devant un mot en gras est refusée",
    ),
    (
        1,
        "Le garde tient. L « amendement » le dit.",
        "une élision devant des guillemets est refusée",
    ),
    (1, "un truc l **ADR** dit", "la même règle vaut pour les minuscules"),
    (0, "| **C** Conformité | à établir |", "un symbole DANS le gras ne déclenche pas"),
    (0, "- **N** saute à la prochaine observation.", "une touche en gras ne déclenche pas"),
    # Epingle une DECISION, pas un comportement : un corps vide PASSE.
    (0, "", "un corps vide passe, faute de décision qui l'interdise"),
)


def _auto_test() -> int:
    """Les trente-sept corps CONNUS, chacun avec son code attendu."""
    import contextlib
    import io

    echecs = cas = rouges = 0
    for attendu, corps, libelle in CAS:
        cas += 1
        if attendu != 0:
            rouges += 1
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = juger(corps)
        if code == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
            echecs = 1

    print()
    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{cas} cas, dont {rouges} qui {verbe} rougir.")
    return echecs


if __name__ == "__main__":
    corps = sys.argv[1] if len(sys.argv) > 1 else ""
    if corps == "--auto-test":
        sys.exit(_auto_test())
    sys.exit(juger(corps))
