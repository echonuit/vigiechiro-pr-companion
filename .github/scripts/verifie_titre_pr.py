#!/usr/bin/env python3
"""Un titre de PR suit Conventional Commits (#2105, porte du bash en #5231).

Le depot fusionne en SQUASH avec `squash_merge_commit_title = PR_TITLE` : le titre de la PR devient
le sujet du commit sur `main`, et les messages des commits de la branche sont ecartes a la fusion.
C est donc ce titre, et lui seul, que semantic-release lira.

Ce controle existe parce que son absence a coute cher : la pratique a derive vers `type(scope) :`,
avec l espace francais avant les deux-points, que Conventional Commits n admet pas. semantic-release
a cesse de publier le 2026-07-18 en finissant VERT a chaque push, et 58 commits releasables se sont
accumules sans que rien ne le signale. Cf. ADR 0040.

## Les trois refus typographiques, et pourquoi ils sont ici

Le cadratin (#2947) et l apostrophe courbe (#4377) partent tels quels dans le CHANGELOG publie, que
la regle typographique ne peut pas rattraper apres coup : le corriger falsifierait le compte rendu de
ce qui a ete livre. Sa SOURCE est ce titre, et c est le seul endroit ou la regle peut s appliquer.

**Aucune exemption de citation ici**, contrairement au garde des fichiers (ADR 2843), qui epargne le
glyphe entre guillemets francais. Un titre fait soixante-dix caracteres et devient une ligne de
journal : qui doit vraiment parler du glyphe ecrit « le glyphe de valeur absente ». Un cas d auto-test
epingle cette DECISION, pour que qui voudrait aligner les deux regles le fasse d abord rougir.

## L elision sans apostrophe, et les deux formes qui lui ressemblent

Ce defaut ne vient pas d une faute de frappe mais d une HABITUDE : ecrire « l ADR », « d une nuit »
pour survivre au quoting d un shell, puis continuer une fois la contrainte disparue.

Deux formes lui ressemblent a s y meprendre et n en sont PAS (#4483) : la lettre est un SYMBOLE
D UNITE apres un nombre (« 143 s la ») ; ou un SYMBOLE au fil d une phrase (« les N fichiers »), une
majuscule ne s elidant qu en TETE de phrase. Mesure du 2026-08-26 sur 21 234 lignes de prose : le
motif d origine en designait 120, celui-ci 70, et les 50 ecartees sont des faux positifs des deux
formes ci-dessus.

Ce qui SUIT decide, et non ce qui precede (#4786, harmonise en #4803) : une elision est suivie d un
MOT francais, un symbole de ce qu il etiquette - un chemin, un compte entre parentheses. Le garde du
CORPS porte la meme regle, et cette page en est la source.

## La locale, epinglee ici parce qu elle a menti

Le verdict ne doit pas dependre de l endroit d ou on appelle. `grep -E` faisait entrer `é` dans la
plage `[a-z]` sous une locale francaise et l en sortait sous `C` : le meme titre passait en local et
echouait en CI (#4456). En Python, `[a-z]` est strictement `a-z` et les classes Unicode sont
explicites, donc la question ne se pose plus - mais un cas d auto-test la tient quand meme.

Usage : python3 .github/scripts/verifie_titre_pr.py "<titre>"   (sortie 0 si conforme, 1 sinon)
"""

from __future__ import annotations

import re
import sys

# Le glyphe se CONSTRUIT plutot que de s ecrire, pour que ce fichier n en porte aucun : c est
# l idiome de `verifie_scripts.py`. Sans cela, le garde des cadratins compterait la prose de son
# propre garde, et celui des fichiers compterait l apostrophe courbe comme un emploi (#4377).
CADRATIN = chr(0x2014)
COURBE = chr(0x2019)

# Types reellement pratiques dans le depot. `feat` -> mineure, `fix`/`perf` -> patch ; les autres ne
# declenchent pas de version (cf. CONTRIBUTING.md §3).
MOTIF = re.compile(
    r"^(feat|fix|perf|refactor|docs|test|chore|ci|build|style|revert)(\([a-z0-9._-]+\))?!?: .+"
)
ESPACE_AVANT = re.compile(r"^[a-z]+(\([a-z0-9._-]+\))? +:")

# Les classes POSIX de la version bash, ecrites ici sans ambiguite de locale.
_ALPHA = r"[^\W\d_]"
_ESPACE = r"[ \t\n\r\f\v]"
_MOT = rf"{_ALPHA}({_ALPHA}|['" + COURBE + r"-])*"
_OUVRE = r"(\*\*|\*|__|_|«" + _ESPACE + r"*|\[)?"
_FIN_MOT = rf"({_ESPACE}|[*_)]|]|»|,|\.|;|:|!|\?|$)"
_ELISION_MIN = (
    r"(^[^\w'"
    + COURBE
    + r"]?|[^0-9][^\w'"
    + COURBE
    + r"])([ldnscjmt]|qu) +"
    + _OUVRE
    + _MOT
    + _FIN_MOT
)
_ELISION_MAJ = (
    r"(^[^\w'" + COURBE + r"]?|\W" + _ESPACE + r")([LDNSCJMT]|Qu) +" + _OUVRE + _MOT + _FIN_MOT
)
ELISION = re.compile(f"{_ELISION_MIN}|{_ELISION_MAJ}")


def juger(titre: str) -> int:
    """Le verdict, et le code de sortie qui va avec."""
    if not titre:
        print("::error::Titre de PR vide.")
        return 1

    print(f"Titre : {titre}")

    if COURBE in titre:
        print("::error::Le titre de la PR contient une apostrophe courbe.")
        print()
        print(f"  écrit : {titre}")
        print()
        print("Écrivez l'apostrophe ASCII. Le titre part tel quel dans le CHANGELOG publié.")
        return 1

    if CADRATIN in titre:
        print("::error::Le titre de la PR contient un tiret cadratin.")
        print()
        print(f"  écrit : {titre}")
        print()
        print("Écrivez un deux-points, une virgule ou un tiret simple.")
        print()
        print(
            "Ce titre devient la ligne du CHANGELOG (fusion en squash), et le CHANGELOG est le seul"
        )
        print("fichier que la règle typographique ne peut pas rattraper après coup : le corriger")
        print("falsifierait le compte rendu de ce qui a été livré. C'est donc ici que ça se joue.")
        print("Cf. dev-docs/decisions/2843-typographie-cliquet-plutot-que-nettoyage.md")
        return 1

    if ELISION.search(titre):
        print("::error::Le titre de la PR contient une élision sans apostrophe.")
        print()
        print(f"  écrit : {titre}")
        print()
        print("Rétablissez l'apostrophe : « l'ADR », « d'une nuit », « n'est pas », « qu'il ».")
        print()
        print(
            "Ce titre devient la ligne du CHANGELOG publié. L'habitude d'amputer les apostrophes vient du"
        )
        print(
            "quoting shell ; elle survit à la disparition de sa cause, et cinq titres l'ont montré le"
        )
        print("2026-07-30. Rédigez le titre en français correct, le quoting se règle autrement.")
        return 1

    if MOTIF.search(titre):
        print("Titre conforme.")
        return 0

    print("::error::Le titre de la PR ne suit pas Conventional Commits.")
    print()

    # L erreur la plus frequente merite d etre nommee : c est celle qui a arrete la release.
    if ESPACE_AVANT.search(titre):
        attendu = re.sub(r"^([a-z]+(\([a-z0-9._-]+\))?) +:", r"\1:", titre)
        print("Cause probable : un ESPACE avant les deux-points.")
        print()
        print(f"  écrit   : {titre}")
        print(f"  attendu : {attendu}")
        print()
        print(
            "Dans 'feat(scope): sujet', le ':' est un token de syntaxe, pas une ponctuation de phrase :"
        )
        print(
            "la règle typographique française de l'espace avant ':' ne s'y applique pas. Un espace y rend"
        )
        print("le titre illisible pour semantic-release, qui cesse de publier SANS rougir.")
        print("Cf. dev-docs/decisions/0040-le-sujet-de-commit-est-une-syntaxe.md")
    else:
        print("Forme attendue : type(scope): sujet en français")
        print()
        print("  feat(passage): écran pivot d'une nuit (statut + navigation)")
        print("  fix(importation): import hors fil JavaFX gelait l'écran")
        print()
        print(
            "Types admis : feat, fix, perf, refactor, docs, test, chore, ci, build, style, revert."
        )
    return 1


# (attendu, titre, libelle)
CAS = (
    (0, "docs(adr): un titre conforme et sans cadratin", "un titre conforme passe"),
    (1, f"docs(adr): un titre {CADRATIN} avec un cadratin", "un cadratin est refusé"),
    (1, "docs(adr) : un espace avant les deux-points", "l'espace avant le deux-points est refusé"),
    (1, "un titre qui ignore Conventional Commits", "un titre non conventionnel est refusé"),
    (1, "", "un titre vide est refusé"),
    # Epingle une DECISION, pas seulement un comportement : le titre n exempte AUCUNE citation, la
    # ou le garde des fichiers epargne le glyphe entre guillemets francais.
    (
        1,
        f"fix(audio): le glyphe « {CADRATIN} » ne se rend plus",
        "aucune exemption de citation dans un titre",
    ),
    (1, "docs(adr): 2843 renvoie vers l amendement", "une élision sans apostrophe est refusée"),
    (1, f"fix(cli): le jeton d{COURBE}un tournage expire", "une apostrophe courbe est refusée"),
    (0, "fix(cli): le jeton d'un tournage expire", "l'apostrophe ASCII passe"),
    (1, "fix(cli): d une nuit a l autre", "plusieurs élisions, même refus"),
    # Controles NEGATIFS : la regle doit rester etroite.
    (
        0,
        "fix(passage): le point C3 et le carre A1 sont distincts",
        "un code de point ne déclenche pas",
    ),
    (0, "feat(cli): le n° 4 est traite", "un numéro ne déclenche pas"),
    (
        0,
        "test(fixture): deux semeurs prennent l'entree legere",
        "une élision correcte ne déclenche pas",
    ),
    # #4483. La lettre isolee employee comme SYMBOLE n est pas une elision.
    (
        0,
        "fix(ci): le job a dure 143 s la ou il en prenait 1200",
        "un symbole d'unité après un nombre ne déclenche pas",
    ),
    (
        0,
        "docs(adr): les N fichiers de la tranche sont relus",
        "une majuscule-symbole au fil d'une phrase ne déclenche pas",
    ),
    (
        0,
        "test(banc): participant S as Service, dans le diagramme",
        "un alias de diagramme ne déclenche pas",
    ),
    (
        0,
        "refactor(api): Succes<T>(T valeur) perd son enveloppe",
        "un paramètre générique ne déclenche pas",
    ),
    # Et la contrepartie : la majuscule reste vue LA OU une phrase commence.
    (
        1,
        "docs(adr): L amendement 2843 est cite",
        "une élision majuscule en tête de phrase reste refusée",
    ),
    # #4786, harmonise ici : ce qui SUIT decide.
    (0, "fix(garde): M scripts/adr/truc.py a change", "une sortie d'outil ne déclenche pas"),
    (
        0,
        "docs(adr): N observation(s) ont ete relues",
        "un symbole suivi d'un compte entre parenthèses ne déclenche pas",
    ),
    (1, "fix(garde): L **amendement** le dit", "une élision devant un mot en gras est refusée"),
    (1, "fix(garde): L auto-test le prouve", "une élision suivie d'un mot composé reste refusée"),
    # Le SCOPE est en ASCII, et ce cas le tient contre la locale (#4456).
    (1, "feat(méthode): sujet", "un scope hors ASCII est refuse, quelle que soit la locale"),
)


def _auto_test() -> int:
    """Les vingt-deux titres CONNUS, chacun avec son code attendu."""
    import contextlib
    import io

    echecs = cas = rouges = 0
    for attendu, titre, libelle in CAS:
        cas += 1
        if attendu != 0:
            rouges += 1
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = juger(titre)
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
    titre = sys.argv[1] if len(sys.argv) > 1 else ""
    if titre == "--auto-test":
        sys.exit(_auto_test())
    sys.exit(juger(titre))
