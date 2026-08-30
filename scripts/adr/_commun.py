#!/usr/bin/env python3
"""Socle des scripts de vérification « probable » d'une ADR.

Un script `probable` ne prouve rien : il liste des **suspects** qu'un humain trie. Son signal utile
n'est donc pas « zéro », mais « aucun **nouveau** ». D'où le cliquet.

Le cliquet vit dans l'ADR elle-même, pas dans le script : c'est la seule façon qu'un lecteur de la
décision voie du même coup la marge en vigueur. Le script va l'y chercher, et le garde-fou
`DocumentationAJourTest` vérifie que la déclaration est bien formée.

Sortie normalisée, pour que le rapport hebdomadaire puisse agréger sans deviner :

    ADR 0010 | suspects=6 | cliquet=6 | verdict=ok
"""

import pathlib
import re
import sys

# La racine du dépôt, pour les gardes qui s'ancrent au lieu de dépendre du répertoire courant.
RACINE_DEPOT = pathlib.Path(__file__).resolve().parents[2]

# ANCRÉE, et non relative (issue #4781). Un garde lancé depuis un autre exemplaire du dépôt mesurait
# celui-là et rendait son verdict sans le dire ; `resserre_cliquets.py`, qui ÉCRIT, annonçait
# « 0 cliquet resserré » depuis `/tmp`. La forme relative de `PRODUCTION` et `TESTS` ci-dessous a une
# raison que celle-ci n'a pas : elles paraissent dans le rapport, où un chemin absolu serait illisible.
DECISIONS = RACINE_DEPOT / "dev-docs" / "decisions"

# LE CORPUS DES GARDES DE CODE, déclaré ici et nulle part ailleurs (ADR 4586).
#
# #4488 a décidé que les gardes de code lisent les DEUX arbres. La décision vivait en treize
# littéraux recopiés, et `verifie_scripts.py` la tenait par une liste ÉNUMÉRÉE qui avait dérivé :
# six gardes lisaient les deux arbres sans y figurer. Un corpus déclaré ici se LIT par le programme,
# ce qui permet à cette liste de se dériver au lieu de s'énumérer.
#
# Ces deux chemins-ci sont RELATIFS, et c'est le rapport qui l'exige : il les affiche, et un chemin
# absolu y serait illisible et différent d'une machine à l'autre. Un garde qui s'ancre écrit donc
# `RACINE_DEPOT / TESTS`, ou importe la variante ancrée plus bas. `DECISIONS` ne suit pas cette
# convention parce qu'elle ne paraît dans aucun rapport : elle est ancrée d'emblée.
#
# Un garde qui lit délibérément la production seule importe `PRODUCTION` et dit pourquoi dans SA
# javadoc : l'exception devient alors visible d'une commande, et sa raison se trouve là où on
# l'ouvre. `verifie_corpus_declare.py` refuse qu'un garde réécrive ces chemins.
PRODUCTION = pathlib.Path("src/main/java")
TESTS = pathlib.Path("src/test/java")
RACINES = (PRODUCTION, TESTS)

# Les mêmes, ancrés sur la racine, pour les gardes qui ne veulent pas dépendre du répertoire
# courant. Deux jeux de noms, un seul endroit où les segments du chemin sont écrits : c'est la
# duplication du chemin qui coûtait, pas celle du nom.
PRODUCTION_ANCREE = RACINE_DEPOT / PRODUCTION
TESTS_ANCRES = RACINE_DEPOT / TESTS
RACINES_ANCREES = (PRODUCTION_ANCREE, TESTS_ANCRES)

# Le cliquet est un CHAMP de l'en-tête OKF, plus une valeur noyée dans une phrase (chantier A).
# L'ancienne forme à puces se lisait par une expression qui devait tolérer deux séparateurs ; un
# champ typé n'a pas ce problème, et il se relit de la même façon par `graphify` et par le site.
CLIQUET = re.compile(r"^ratchet:\s*(\d+)\s*$", re.M)

# L'en-tête s'arrête au premier `---` fermant : une valeur qui ressemblerait à un champ, plus bas
# dans la prose, ne doit pas être lue comme une déclaration.
ENTETE = re.compile(r"\A---\n(.*?)\n---\n", re.S)


def entete(chemin: pathlib.Path) -> str:
    """L'en-tête YAML d'une ADR, ou une chaîne vide si elle n'en porte pas."""
    trouve = ENTETE.match(chemin.read_text(encoding="utf-8"))
    return trouve.group(1) if trouve else ""


def cliquet(numero: str) -> int:
    """Le cliquet déclaré par l'ADR `numero`, lu dans son en-tête OKF."""
    fichiers = sorted(DECISIONS.glob(f"{numero}-*.md"))
    if not fichiers:
        raise SystemExit(f"ADR {numero} introuvable sous {DECISIONS}")
    trouve = CLIQUET.search(entete(fichiers[0]))
    if not trouve:
        raise SystemExit(
            f"ADR {numero} ne déclare aucun cliquet lisible. Attendu, dans son en-tête :\n"
            f"  verification: probable\n"
            f"  enforced_by:\n"
            f"    - \"chemin/du/script\"\n"
            f"  ratchet: N"
        )
    return int(trouve.group(1))


# Le PLANCHER est l'inverse du cliquet, et il a son propre champ pour que la polarité se voie dans
# l'en-tête. Un cliquet borne ce qu'on tolère et doit descendre ; un plancher garde ce qu'on possède
# et doit monter. Les confondre dans un même champ ferait qu'un lecteur pressé lirait le mauvais sens.
PLANCHER = re.compile(r"^floor:\s*(\d+)\s*$", re.M)


def plancher(numero: str) -> int:
    """Le plancher déclaré par l'ADR `numero`, lu dans son en-tête OKF."""
    fichiers = sorted(DECISIONS.glob(f"{numero}-*.md"))
    if not fichiers:
        raise SystemExit(f"ADR {numero} introuvable sous {DECISIONS}")
    trouve = PLANCHER.search(entete(fichiers[0]))
    if not trouve:
        raise SystemExit(
            f"ADR {numero} ne déclare aucun plancher lisible. Attendu, dans son en-tête :\n"
            f"  verification: certaine\n"
            f"  enforced_by:\n"
            f"    - \"chemin/du/script\"\n"
            f"  floor: N"
        )
    return int(trouve.group(1))


def rapporte_plancher(numero: str, titre: str, mesure: int, unite: str) -> int:
    """Confronte une mesure à son plancher, et rend le code de sortie.

    **La polarité est l'inverse de `rapporte`, et c'est voulu.** Un cliquet compte ce qu'on tolère :
    monter est une régression. Un plancher compte ce qu'on possède : **descendre** est la perte, et
    monter est la bonne nouvelle. Le même code ne peut pas servir les deux sans que l'un des deux
    sens se lise de travers.

    Ce que le plancher garde ne se recompte pas non plus de la même façon. Un suspect se liste, parce
    qu'un humain le trie ; ce qui a disparu ne se liste pas, justement parce qu'il n'est plus là. La
    sortie annonce donc un nombre et un manque, pas une énumération.

    **Les deux écarts refusent, et pas seulement la perte** (issue #4683). Un plancher périmé - la
    mesure au-dessus du seuil - sortait en 0 : le message disait de relever, personne ne lit une
    sortie verte, et les deux planchers du dépôt se sont trouvés périmés de vingt-et-un renvois moins
    de vingt-quatre heures après avoir été posés. Ce qu'un plancher promet est de garder un gain ; un
    gain non verrouillé se reperd, et le refus est ce qui rend la promesse vraie.

    La polarité complète : `perte` refuse parce qu'on a perdu, `a-relever` refuse parce qu'on n'a pas
    encore gardé, `ok` seul passe.

    Sortie normalisée, pour que le rapport hebdomadaire puisse agréger sans deviner :

        PLANCHER 4395 | mesure=4026 | plancher=4026 | verdict=ok
    """
    seuil = plancher(numero)
    print(f"ADR {numero} - {titre}")

    verdict = "ok"
    if mesure < seuil:
        verdict = "perte"
    elif mesure > seuil:
        verdict = "a-relever"

    print(f"\nPLANCHER {numero} | mesure={mesure} | plancher={seuil} | verdict={verdict}")

    if verdict == "perte":
        print(
            f"\nÉCHEC : {mesure} {unite} pour un plancher de {seuil}. Il en manque {seuil - mesure}.\n"
            f"Un renvoi perdu ne casse rien : il cesse simplement d'ouvrir, et personne ne le voit.\n"
            f"Rendez-les, ou justifiez la perte et abaissez le plancher dans l'ADR - mais un plancher\n"
            f"qui descend est une décision, pas une formalité.",
            file=sys.stderr,
        )
        return 1
    if verdict == "a-relever":
        print(
            f"ÉCHEC : le dépôt en porte plus que son plancher ({mesure} > {seuil}).\n"
            f"Relevez-le à {mesure} dans l'ADR - l'en-tête `floor:` ET les balises qui le citent.\n"
            f"Un plancher périmé ne garde pas ce qu'on vient de gagner : la perte redevient gratuite\n"
            f"dès qu'on a oublié de relever, et un message sans conséquence n'est pas une règle.",
            file=sys.stderr,
        )
        return 1
    return 0


def rapporte(numero: str, titre: str, suspects: list[str], apercu: int | None = None) -> int:
    """Affiche les suspects, confronte leur nombre au cliquet, et rend le code de sortie.

    Dépasser le cliquet est un échec : c'est une régression, quelqu'un a ajouté un cas.
    Passer *sous* le cliquet n'est pas un échec, c'est une bonne nouvelle - mais elle est signalée,
    parce que la marge doit alors être resserrée. Un cliquet qu'on ne resserre jamais redevient un
    tapis sous lequel on pousse.
    """
    marge = cliquet(numero)
    print(f"ADR {numero} - {titre}")
    montres = suspects if apercu is None else suspects[:apercu]
    for suspect in montres:
        print(f"  {suspect}")
    # Un apercu qui ne dit pas ce qu il tait est un compte rendu partiel qui se donne pour complet.
    if len(montres) < len(suspects):
        print(f"  … et {len(suspects) - len(montres)} autres, non montrés (aperçu borné à {apercu})")

    verdict = "ok"
    if len(suspects) > marge:
        verdict = "regression"
    elif len(suspects) < marge:
        verdict = "a-resserrer"

    print(f"\nADR {numero} | suspects={len(suspects)} | cliquet={marge} | verdict={verdict}")

    if verdict == "regression":
        print(
            f"\nÉCHEC : {len(suspects)} suspects pour un cliquet de {marge}. Un cas a été ajouté.\n"
            f"Corrigez-le, ou justifiez-le et relevez le cliquet dans l'ADR, mais un cliquet qui\n"
            f"monte est une décision, pas une formalité.",
            file=sys.stderr,
        )
        return 1
    if verdict == "a-resserrer":
        print(
            f"\nLe dépôt fait mieux que sa marge ({len(suspects)} < {marge}) : resserrez le cliquet\n"
            f"à {len(suspects)} dans l'ADR, sinon la marge regagnée se reperdra en silence."
        )
    return 0


def loupe(numero: str, titre: str, candidats: list[str]) -> int:
    """Une LOUPE, pour une ADR « humaine » dont un pattern reconnaissable existe.

    Elle ne prouve rien et ne borne rien : elle surface une **surface de revue** - « voici les
    endroits à regarder pour cette décision », que l'humain confronte à l'ADR pendant la passe humaine.
    Elle ne bloque JAMAIS (toujours code 0) et n'a pas de cliquet : classer une infraction ici serait
    prétendre à une certitude qu'un motif ne donne pas.

    C'est le cran « moins formel que le cliquet » : le pattern démarre grossier et s'affine au fil des
    cas connus. Sa valeur n'est pas « zéro candidat », c'est « aucun candidat oublié à la revue ».

    Sortie normalisée pour le rapport :

        LOUPE 0020 | candidats=7
    """
    print(f"LOUPE {numero} - {titre}")
    for c in candidats:
        print(f"  {c}")
    print(f"\nLOUPE {numero} | candidats={len(candidats)}")
    return 0


# --- Retrait des commentaires, mutualisé -------------------------------------------------------------
#
# Un script `probable` qui compte un motif présent dans un COMMENTAIRE est faux par construction : le
# commentaire cite la chose, il ne la fait pas. La clôture du chantier a trouvé ce défaut sur trois
# scripts (0010, 0037, 0046) qui avaient chacun oublié de retirer les commentaires. Mettre les deux
# retraits ici en fait le chemin par défaut, découvrable, qu'un futur script réutilise au lieu de
# refaire l'oubli. Les sauts de ligne sont préservés pour que les numéros de ligne restent justes.

_BLOC_JAVA = re.compile(r"/\*.*?\*/", re.S)
_LIGNE_JAVA = re.compile(r"//[^\n]*")
_COMMENTAIRE_XML = re.compile(r"<!--.*?-->", re.S)


def _blanchir(motif, source: str) -> str:
    return motif.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), source)


def sans_commentaires_java(source: str) -> str:
    """Neutralise les commentaires Java : bloc `/* */`, ligne `//`, doc `///`."""
    return _LIGNE_JAVA.sub("", _blanchir(_BLOC_JAVA, source))


def sans_commentaires_xml(source: str) -> str:
    """Neutralise les commentaires XML/FXML `<!-- -->`."""
    return _blanchir(_COMMENTAIRE_XML, source)
