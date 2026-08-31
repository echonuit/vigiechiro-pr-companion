#!/usr/bin/env python3
"""ADR 0008 - Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission.

Pourquoi « probable » et non « certaine » : décider si un `catch` « expose » vraiment son échec
demande de comprendre le corps. Un catch qui journalise, relance, ou traduit l'échec en un état visible
respecte l'ADR ; il n'existe pas de motif syntaxique qui les distingue tous à coup sûr des catch muets.

Le script se concentre donc sur le cas le moins ambigu : le `catch` dont le corps est **vide**, une fois
les commentaires retirés. Là, l'échec disparaît sans laisser de trace, quel que soit le contexte. Un
commentaire du type « ignoré volontairement : … » ne suffit pas à sortir du compte : l'ADR veut une
trace observable à l'exécution, pas une note dans le source. C'est précisément ce qu'un humain doit
arbitrer suspect par suspect.

Ce que la règle protège : un échec avalé se paie plus tard, ailleurs, sous une forme méconnaissable -
un état incohérent, une opération qui « n'a rien fait » sans dire pourquoi.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINES, rapporte, sans_commentaires_java

# Les DEUX arbres. Un test qui avale son echec ment de la meme facon qu une classe de production :
# il rend vert sans avoir rien prouve, et c est precisement le defaut que l ADR 0008 nomme. La
# production seule etait le corpus d origine, sans qu aucune decision ne l ait restreinte a elle -
# le garde est simplement ne avant que la question ne se pose. Les quatre catch vides que l arbre de
# test porte ont ete arbitres un par un a leur entree : une exception ATTENDUE dans une auto-garde,
# deux boucles de reprise dont l assertion de l appelant tranche, une fermeture de banc. Aucun n est
# un echec avale, et c est pourquoi ils entrent dans le cliquet plutot que de le faire rougir.

# Un bloc catch sans accolade imbriquée : suffisant pour repérer les corps vides ou quasi vides.
CATCH = re.compile(r"catch\s*\([^)]*\)\s*\{([^{}]*)\}", re.S)


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unités que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu'il RETENAIT. Un ciblage manqué
    donnait donc zéro suspect sur zéro fichier, et ce zéro passait pour un succès. L'extraire fait
    du compte des unités lues une valeur, au lieu d'un effet de bord invisible.
    """
    arbres = [racine / a for a in RACINES] if racine else list(RACINES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java"))


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    # Les commentaires sont retirés du fichier ENTIER d'abord (helper mutualisé) : le corps devient
    # vide s'il ne portait qu'un « // ignoré volontairement », et un catch écrit dans un commentaire ne
    # se fait pas prendre pour du code. L'ADR veut une trace observable À L'EXÉCUTION, pas une note.
    trouves = []
    for source in fichiers(racine):
        texte = sans_commentaires_java(source.read_text(encoding="utf-8"))
        for bloc in CATCH.finditer(texte):
            if not bloc.group(1).strip():
                ligne = texte[: bloc.start()].count("\n") + 1
                trouves.append(f"{source}:{ligne}  catch au corps vide")
    return trouves


if __name__ == "__main__":
    sys.exit(
        rapporte("0008", "échec silencieux : catch au corps vide", suspects(), lus=len(fichiers()))
    )
