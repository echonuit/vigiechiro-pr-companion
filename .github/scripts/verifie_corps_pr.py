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

## Le cinquieme refus : la trace d outil (#4749)

Les cinq traces d outil qui se comptent - marque de citation d assistant, lien marque du nom de
l outil, caractere invisible, lettre sosie, gabarit non rempli - etaient tenues a zero dans les
fichiers suivis, par `scripts/adr/4783-traces-d-outil.py`, et nulle part ailleurs. Un corps de
demande n est pas un fichier suivi. Mesure du 2026-10-07 : ce garde rendait « Corps conforme. » sur
un corps qui en portait trois.

**La definition n est PAS ecrite ici.** Elle vit dans `scripts/_commun/traces.py`, que les deux
gardes lisent, pour la raison que ce fichier donne plus bas a propos de l elision : deux ecritures
divergent, chacune gardant son auto-test vert.

Ce qui est CITE se reconnait comme dans le garde des fichiers, entre accents graves, et un bloc
cloture reste epargne en entier. Un corps doit pouvoir PARLER d une trace sans en porter une.

**Une exemption propre au corps : la mention neutralisee.** Dependabot recopie des notes de version
et y glisse une espace sans chasse derriere chaque arobase, pour que la forge ne notifie pas les
auteurs cites. Ce caractere fait son travail, comme le liant d un pictogramme. Mesure du 2026-10-07
sur les 300 dernieres demandes fusionnees : 68 espaces sans chasse, les 68 derriere une arobase,
dans 4 des 13 corps de Dependabot. Sans l exemption, ces quatre demandes auraient rougi sur un
texte que personne ici n a ecrit. Partout ailleurs le caractere reste refuse.

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

import pathlib
import re
import sys
import unicodedata

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
# La direction de l import est celle de `_forge.py` et de `verifie_butoirs.py`, tous deux ici.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "scripts"))

from _commun import cas_d_auto_test
from _commun.traces import traces
from verifie_titre_pr import ELISION

# Construits, jamais ecrits : sans cela le garde des cadratins compterait la prose de son propre
# garde, et celui des fichiers compterait l apostrophe courbe comme un emploi.
CADRATIN = chr(0x2014)
COURBE = chr(0x2019)
OUVRANT, FERMANT = chr(0x00AB), chr(0x00BB)
INSECABLE = chr(0x00A0)

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
# Ce que Dependabot ecrit devant un nom d utilisateur pour ne pas le notifier : l arobase, puis une
# espace sans chasse. Voir la docstring pour la mesure. Construit, pour la raison dite plus haut.
MENTION_NEUTRALISEE = "@" + chr(0x200B)

# L elision sans apostrophe n a PAS de motif ici : `ELISION` vient de `verifie_titre_pr.py`, qui
# porte la regle et la mesure dont elle sort (#4837). Ce fichier en tenait une copie, et les deux
# ont diverge deux fois sans bruit, chacun gardant son auto-test vert : #4786 a corrige celle-ci
# seule, puis l espace insecable a ete vue ici et pas la-bas. Un temoin a espace insecable plus bas
# prouve que le motif lu est bien celui du titre : le casser LA-BAS fait rougir cet auto-test.


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
    "trace d'outil": (
        "Une chaîne que seul un outil écrit, ou le résidu d'un collage : marque de citation\n"
        "  d'assistant (T1), lien marqué du nom de l'outil (T2), caractère invisible (T3), lettre\n"
        "  cyrillique ou grecque dans un mot latin (T4), gabarit non rempli (T5). Retirez-la, ou\n"
        "  remplissez le gabarit : sa présence dit que le texte est parti sans être relu.\n"
        "  Si le corps PARLE d'une trace, citez-la entre accents graves ou dans un bloc clôturé par\n"
        "  trois accents graves, que ce garde épargne.\n"
        "  La définition est dans scripts/_commun/traces.py, et l'ADR 4783 dit pourquoi ce zéro se garde."
    ),
}


def juger(corps: str) -> int:
    """Les cinq refus, ligne par ligne, et le code de sortie qui va avec."""
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
        if ELISION.search(prose):
            fautes.append((numero, "élision sans apostrophe", ligne.strip()))
        if FERMETURE_FR.search(sans_accents(prose)):
            fautes.append((numero, "fermeture en français", ligne.strip()))
        # La ligne ENTIERE et non `prose` : `traces` porte sa propre regle de citation, la meme que
        # dans le garde des fichiers, et lui donner un texte deja ampute en ferait deux.
        fautes.extend(
            (numero, "trace d'outil", f"{trace} : {ligne.strip()}")
            for trace in traces(ligne.replace(MENTION_NEUTRALISEE, "@"))
        )

    if not fautes:
        print("Corps conforme.")
        return 0

    print(f"::error::Le corps de la PR porte {len(fautes)} defaut(s).")
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
JETON = "cite" + "turn0search0"
SUIVI = "utm_" + "source=chatgpt.com"
GABARIT = "[Votre " + "nom]"
SANS_CHASSE = chr(0x200B)
E_CYRILLIQUE = chr(0x0435)  # « e » cyrillique, sosie du latin

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
    # #4837. Le temoin du PARTAGE : la classe d espaces remise en ASCII dans le garde du titre le
    # fait rougir ICI, et c est la seule preuve que ce garde lit le motif de l autre.
    (
        1,
        f"Le garde tient, l amendement{INSECABLE}: il le dit.",
        "une élision devant une espace insécable est refusée",
    ),
    # #4749. Les cinq traces d outil qui se comptent, une par famille. Les chaines sont ASSEMBLEES
    # et non ecrites : ce fichier est suivi, donc lu par `4783-traces-d-outil.py`, et une chaine
    # litterale y ferait rougir le depot entier. C est le detour de `verifie_scripts.py`.
    (
        1,
        f"La source le confirme {JETON} dans son rapport.",
        "T1, une marque de citation, est refusée",
    ),
    (
        1,
        f"Voir [la page](https://exemple.org/guide?{SUIVI}) pour le detail.",
        "T2, un lien marqué du nom de l'outil, est refusé",
    ),
    (
        1,
        f"Le mot coupe par un inv{SANS_CHASSE}isible se recopie sans se voir.",
        "T3, un caractère invisible, est refusé",
    ),
    (
        1,
        f"La relectur{E_CYRILLIQUE} porte une lettre qui n'est pas latine.",
        "T4, une lettre sosie dans un mot latin, est refusée",
    ),
    (1, f"Redige par {GABARIT} un jour de relecture.", "T5, un gabarit non rempli, est refusé"),
    # Les controles NEGATIFS. La demande qui a livre ce refus devait elle-meme CITER des traces
    # pour en parler : un garde qui l en empecherait serait inutilisable des son premier jour.
    (
        0,
        f"Le garde refuse ceci :\n{TROISQUOTES}\n{JETON}\n{TROISQUOTES}\nEt rien d'autre.",
        "une trace citée dans un bloc clôturé passe",
    ),
    (0, f"Le garde cherche `{JETON}` dans le corps.", "une trace citée entre accents graves passe"),
    (
        0,
        f"Releve par {chr(0x1F468)}{chr(0x200D)}{chr(0x1F52C)} hier.",
        "le liant d'un pictogramme composé passe",
    ),
    # `traces` lit la LIGNE, pas la prose amputee de son code en ligne : retirer le code d abord
    # recollerait les deux moities qu il separe, et le garde inventerait une trace.
    (
        0,
        f"Les deux moities {JETON[:4]}`x`{JETON[4:]} ne se recollent pas.",
        "un code en ligne ne recolle pas deux moitiés de marque",
    ),
    # La mention neutralisee de Dependabot : le caractere du cas T3, a la seule place ou il sert.
    (
        0,
        f"<li>Fix the Temurin job by <code>{MENTION_NEUTRALISEE}brunoborges</code> upstream</li>",
        "l'espace sans chasse derrière une arobase passe",
    ),
    (
        1,
        f"<li>Fix the Temurin job by <code>@brunoborges{SANS_CHASSE}</code> upstream</li>",
        "la même, ailleurs que derrière l'arobase, reste refusée",
    ),
    # Epingle une DECISION, pas un comportement : un corps vide PASSE.
    (0, "", "un corps vide passe, faute de décision qui l'interdise"),
)


def _code_de(corps: str) -> int:
    """Le code que `juger` rend sur ce corps, sa sortie retenue."""
    import contextlib
    import io

    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        return juger(corps)


def _refus_de(corps: str) -> str:
    """Ce que `juger` ECRIT sur ce corps : le chemin de refus se lit, il ne se devine pas."""
    import contextlib
    import io

    sortie = io.StringIO()
    with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
        juger(corps)
    return sortie.getvalue()


def _auto_test() -> int:
    """Les corps CONNUS, chacun avec son code attendu ; la derniere ligne les recompte.

    Chaque cas passe par un APPELABLE : un cas dont l expression leve se nomme alors lui-meme, au
    lieu d arreter le temoin sur une trace de pile (#5444).
    """
    verifie, echecs = cas_d_auto_test()
    rouges = 0
    for attendu, corps, libelle in CAS:
        if attendu != 0:
            rouges += 1
        verifie(libelle, lambda corps=corps: _code_de(corps), attendu)

    # Le chemin de REFUS, et pas seulement le code : un refus qui ne nommerait ni la famille ni le
    # remede sortirait en 1 sans dire quoi retirer, et les cas ci-dessus ne le verraient pas.
    trace_t2 = f"Voir https://exemple.org/guide?{SUIVI} pour le detail."
    verifie("le refus nomme la FAMILLE de la trace", lambda: "T2 utm_" in _refus_de(trace_t2), True)
    verifie(
        "il nomme la LIGNE qui la porte",
        lambda: "ligne 3 : trace d'outil" in _refus_de(f"Un corps.\n\n{trace_t2}"),
        True,
    )
    verifie(
        "il donne le remède des traces, et où vit leur définition",
        lambda: "scripts/_commun/traces.py" in _refus_de(trace_t2),
        True,
    )
    verifie(
        "une ligne à deux traces rend deux refus",
        lambda: _refus_de(f"{JETON} et {GABARIT}").count("trace d'outil\n"),
        2,
    )
    verifie(
        "un corps sain ne parle d aucune trace",
        lambda: "trace" in _refus_de("Ce corps dit ce qui a ete fait."),
        False,
    )

    print()
    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{echecs.joues()} cas, dont {rouges} qui {verbe} rougir.")
    return echecs()


if __name__ == "__main__":
    corps = sys.argv[1] if len(sys.argv) > 1 else ""
    if corps == "--auto-test":
        sys.exit(_auto_test())
    sys.exit(juger(corps))
