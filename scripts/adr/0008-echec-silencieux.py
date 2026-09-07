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
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINES, rapporte, sort_si_contrat_demande
from _commun.arbre import arbre, noeuds_de_type

# Les DEUX arbres. Un test qui avale son echec ment de la meme facon qu une classe de production :
# il rend vert sans avoir rien prouve, et c est precisement le defaut que l ADR 0008 nomme. La
# production seule etait le corpus d origine, sans qu aucune decision ne l ait restreinte a elle -
# le garde est simplement ne avant que la question ne se pose. Les quatre catch vides que l arbre de
# test porte ont ete arbitres un par un a leur entree : une exception ATTENDUE dans une auto-garde,
# deux boucles de reprise dont l assertion de l appelant tranche, une fermeture de banc. Aucun n est
# un echec avale, et c est pourquoi ils entrent dans le cliquet plutot que de le faire rougir.


# Les types de noeud qu une grammaire Java donne aux commentaires. Un corps qui n en porte que
# reste VIDE : l ADR veut une trace a l execution, pas une note dans le source.
COMMENTAIRES = {"line_comment", "block_comment", "comment"}


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unités que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu'il RETENAIT. Un ciblage manqué
    donnait donc zéro suspect sur zéro fichier, et ce zéro passait pour un succès. L'extraire fait
    du compte des unités lues une valeur, au lieu d'un effet de bord invisible.
    """
    arbres = [racine / a for a in RACINES] if racine else list(RACINES)
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java"))


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    r"""Les `catch` dont le corps ne porte aucune instruction, commentaires exceptés.

    **La lecture se fait par la STRUCTURE depuis #5430**, et l'ancienne dépendait d'un motif faux.
    Le corps était découpé par `catch\s*\([^)]*\)\s*\{([^{}]*)\}` sur un texte préalablement
    débarrassé de ses commentaires par `sans_commentaires_java`, qui est une expression régulière et
    ne sait pas ce qu'est une chaîne :

        String url = "https://exemple.fr/a";   ->   String url = "https:

    Le `//` d'une URL est pris pour un commentaire de ligne, et la chaîne est tronquée. Le corpus
    porte 161 lignes où un `://` vit dans une chaîne. Aucune ne fausse le verdict AUJOURD'HUI,
    parce que la troncature détruit le motif au lieu de le déplacer, et un catch qu'on ne voit plus
    n'est ni un faux positif ni un faux négatif. C'est une fragilité, pas un défaut constaté, et
    elle disparaît avec la lecture par l'arbre.

    Un `catch` dont le corps porte des accolades imbriquées devient visible au passage : le motif
    `[^{}]*` l'écartait par construction. Mesuré sur le corpus le 2026-09-07, 630 `catch` vus par
    le motif contre 638 par l'arbre.

    **Ces huit-là ne pouvaient pas être des faux négatifs**, et il faut le dire plutôt que de
    laisser croire à une correction : un `catch` VIDE ne porte aucune accolade, donc le motif ne
    pouvait pas le manquer pour cette raison. Ce lot ne fait donc rougir aucun site nouveau. Ce
    qu'il retire est une dépendance à un découpage faux, sur un garde dont le cliquet est à zéro et
    dont un seul faux négatif suffirait à rendre le verdict muet.

    Les commentaires n'ont plus à être retirés : ce sont des nœuds, et un corps qui n'en porte que
    reste vide. L'ADR veut une trace observable À L'EXÉCUTION, pas une note dans le source.
    """
    trouves = []
    for source in fichiers(racine):
        racine_ast = arbre(source.read_bytes()).root_node
        for attrape in noeuds_de_type(racine_ast, {"catch_clause"}):
            corps = attrape.child_by_field_name("body")
            if corps is None:
                continue
            # `named_children` écarte la ponctuation ET les commentaires : un corps qui ne porte
            # qu un « // ignoré volontairement » est vide au sens de l ADR.
            if not [e for e in corps.named_children if e.type not in COMMENTAIRES]:
                trouves.append(f"{source}:{attrape.start_point[0] + 1}  catch au corps vide")
    return trouves


# Ce que ce garde DÉCLARE être (issue #5009), plutôt que ce qu'un relevé devine de lui.
CONTRAT = {
    "geste": "échec silencieux : catch au corps vide",
    "population": "PRODUCTION + TESTS",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_0008_echec_silencieux",
    "decision": "ADR 0008",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(
        rapporte("0008", "échec silencieux : catch au corps vide", suspects(), lus=len(fichiers()))
    )
