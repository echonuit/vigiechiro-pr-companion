#!/usr/bin/env python3
"""Le lecteur d'arbre des gardes : ce qu'un motif ne sait pas répondre sur du Java (chantier #5402).

Trente-sept scripts lisent les 2 125 fichiers Java du dépôt, et soixante-sept des cent dix-neuf
scripts d'outillage le font par expression régulière. Neuf **déclarent** leur approximation en toutes
lettres, dont celui-ci, que ce module sert d'abord :

> « Comptage naïf des accolades : il ne comprend ni les chaînes ni les caractères. »

Une accolade dans un littéral déplace alors la profondeur, et un commentaire change de population
sans que rien ne le dise. Mesuré : sur 5 079 blocs, l'arbre contredit le comptage **huit fois**, dans
les deux sens.

## Ce que ce module donne, et ce qu'il ne donne pas

Il donne la **structure** : quel nœud entoure cette ligne, et quels sont ses parents. C'est ce qu'un
motif ne peut pas approcher, parce que la structure n'est pas locale.

Il ne donne **pas les types** : `tree-sitter` analyse un fichier isolé, sans classpath. « Cette
expression est-elle un `ReponseApi` » ne se demande pas ici. C'est le rôle de l'étage 2 du chantier,
qui lit un index produit par une compilation, et il n'existe pas encore.

## Pourquoi la grammaire est épinglée, et pourquoi ça ne suffit pas

Les noms de nœuds appartiennent à une version de grammaire. Un relèvement pourrait déplacer une
population **sans que rien ne rougisse**, ce qui est le faux vert que le dépôt traque partout ailleurs.
La version est donc épinglée dans `pyproject.toml`, et `verifie_grammaire()` plus bas confronte les
noms dont ce module dépend à une source témoin. Une grammaire qui les renommerait ferait rougir le
témoin au lieu de faire taire un garde.

L'épinglage seul ne suffirait pas : il empêche la dérive subie, pas la dérive choisie. C'est le
témoin qui rend un relèvement délibéré.

## Quand la grammaire manque, ce module REFUSE et ne plante pas

`LecteurAbsent` est levée si le module n'est pas importable par le python qui lance le garde, et son
message dit quoi faire. Une `ModuleNotFoundError` nue ressemble à un défaut du changement en cours,
et c'est le reproche que la docstring de `verifie-dependances-declarees.py` adresse à l'état d'avant
l'issue #5008.

Le cas n'est pas théorique : il est arrivé le lendemain de la livraison de ce module (#5424). En CI
rien ne casse, `pip install --group gardes` installant dans le python du runner ; le défaut vit en
local, c'est-à-dire là où l'on cherche ses erreurs.

**Le message avertit du piège qui coûte le plus cher** : poser le module dans un venv ne sert à rien
tant que les gardes sont lancés par un autre python, ce que font `scripts/batterie.py` et `lint.yml`.
L'issue #5426 tranche la question de fond ; en attendant, la phrase évite l'heure perdue.

## Ce que « illisible » veut dire ici, et pourquoi un garde doit le compter

`tree-sitter` est tolérant : ce qu'il ne sait pas lire devient un nœud `ERROR` **local**, et le reste
du fichier reste interrogeable. C'est précieux et c'est un piège. Un garde qui ignore ces zones rend
zéro suspect sur une population amputée, et ce zéro ressemble à un succès - le défaut que l'issue
#5007 a corrigé en faisant compter les unités LUES.

Mesure du 2026-09-07, `tree-sitter-language-pack` 1.16.1 : **un seul** fichier du dépôt porte un nœud
`ERROR`. Il y perd cinq lignes sur 374, et **aucune méthode ni aucun commentaire** n'y tombe.

La borne est plus étroite qu'il n'y paraît, et se nomme exactement : un motif de déconstruction
d'enregistrement dont le type est **qualifié**, `case B.P(int x)`. La forme simple `case P(int x)` se
lit, la forme générique `case B.P<C>(int x)` aussi, et la variable anonyme `_` de Java 22 également.
C'est cette seule combinaison qui manque.

Le coût est donc nul aujourd'hui, et c'est précisément pourquoi la discipline s'installe maintenant :
elle ne coûte rien à poser, et elle coûterait un faux verdict à poser trop tard.
"""

import functools
import pathlib

# Les nœuds qui OUVRENT un corps de code, par opposition au corps d'un type. Un commentaire dont un
# de ces nœuds est un ancêtre s'adresse à qui lit des INSTRUCTIONS ; entre les membres d'une classe,
# il documente une section. C'est la distinction que le comptage d'accolades approchait en supposant
# qu'une profondeur de deux valait « dans une méthode », ce que dément toute classe imbriquée.
CORPS_DE_CODE = frozenset(
    {
        "method_declaration",
        "constructor_declaration",
        "compact_constructor_declaration",
        "static_initializer",
        "lambda_expression",
    }
)


class LecteurAbsent(RuntimeError):
    """La grammaire n'est pas importable par le python qui lance le garde.

    Une exception NOMMÉE plutôt qu'un `SystemExit` levé ici : ce module est une bibliothèque, et un
    `SystemExit` tuerait un appelant qui voulait seulement savoir. Le garde, lui, la rend en refus.

    Elle ne se lève pas non plus à l'import du module : un garde qui importe le fonds commun sans
    lire de Java n'a aucune raison d'échouer.
    """


# Ce que le refus dit, et pourquoi ces quatre lignes. La première nomme ce qui manque, la deuxième
# reprend mot pour mot la formule du cliquet 4617 devant un `target/pmd.xml` absent, les deux
# dernières disent quoi faire.
#
# **La quatrième ligne est celle qui coûte le plus cher à omettre.** Poser le module dans un venv ne
# sert à rien tant que les gardes sont lancés par un autre python, ce que font `scripts/batterie.py`
# et `lint.yml`, qui appellent `python3` du PATH. Un contributeur qui installe puis relance sans
# changer de python voit la même erreur et conclut que l'installation a échoué. L'issue #5426 tranche
# la question de fond ; en attendant, la phrase évite l'heure perdue.
_REFUS = (
    "Le lecteur d arbre est absent : `tree_sitter_language_pack` n est pas importable.\n"
    "Ce garde REFUSE plutot que de conclure sur ce qu il n a pas lu.\n"
    "Lancez d abord : pip install --group gardes\n"
    'Et verifiez que c est le MEME python qui lance le garde : `python3 -c "import '
    'tree_sitter_language_pack"` doit passer (issue #5426).'
)


@functools.cache
def _analyseur():
    """L'analyseur Java, construit une fois par processus.

    Chargé PARESSEUSEMENT, et non à l'import : un garde qui importe le fonds commun sans lire de Java
    ne doit pas payer la grammaire. Le coût mesuré est de 0,02 s à chaud, et de plusieurs dizaines de
    secondes au tout premier lancement d'un environnement neuf, où il ne mesure que la compilation du
    bytecode de Python.
    """
    try:
        from tree_sitter_language_pack import get_parser
    except ImportError as absent:
        raise LecteurAbsent(_REFUS) from absent

    return get_parser("java")


def arbre(source: bytes):
    """L'arbre syntaxique d'une source Java, à partir de ses OCTETS et non de son texte.

    Les octets, parce que les positions que rend `tree-sitter` sont des décalages d'octets : décoder
    d'abord ferait diverger les colonnes sur toute ligne portant un caractère non ASCII, ce que la
    prose française de ce dépôt garantit.
    """
    return _analyseur().parse(source)


def noeud_a_la_ligne(racine, ligne: int):
    """Le nœud le plus intérieur qui couvre le début de `ligne`, comptée à partir de 1."""
    return racine.descendant_for_point_range((ligne - 1, 0), (ligne - 1, 0))


def ancetre_parmi(noeud, types) -> bool:
    """`noeud` a-t-il un ancêtre dont le type est dans `types` ? Le nœud lui-même compte."""
    while noeud is not None:
        if noeud.type in types:
            return True
        noeud = noeud.parent
    return False


def dans_un_corps_de_code(racine, ligne: int) -> bool:
    """Cette ligne tombe-t-elle dans un corps de méthode, de constructeur ou de lambda ?

    C'est la question que le cliquet 4472 posait en comptant des accolades. Elle est ici posée à la
    structure, donc une accolade dans une chaîne ne la déplace plus, et une classe imbriquée cesse
    d'être prise pour une méthode.
    """
    return ancetre_parmi(noeud_a_la_ligne(racine, ligne), CORPS_DE_CODE)


def noeuds_de_type(racine, types) -> list:
    """Tous les nœuds dont le type est dans `types`, dans l'ordre du fichier.

    C'est ce que trois gardes obtenaient en équilibrant des accolades pour découper un corps de
    méthode ou de `catch`. Le nœud porte son texte : `noeud.text.decode()` rend le corps exact, sans
    qu'une accolade dans une chaîne ne déplace la fin (#5430).
    """
    trouves, a_voir = [], [racine]
    while a_voir:
        noeud = a_voir.pop()
        if noeud.type in types:
            trouves.append(noeud)
        a_voir.extend(reversed(noeud.children))
    return sorted(trouves, key=lambda n: n.start_byte)


def arguments(appel) -> list:
    """Les arguments d'un appel, découpés par la STRUCTURE et non aux virgules de niveau zéro.

    L'équilibrage de parenthèses qu'employaient `5068` et `5278` compte une parenthèse dans un
    littéral et coupe sur une virgule qui vit dans une chaîne. La liste d'arguments est un nœud, et
    ses enfants nommés sont les arguments (#5430).
    """
    liste = next((e for e in appel.children if e.type == "argument_list"), None)
    return [] if liste is None else [e for e in liste.named_children]


def zones_illisibles(racine) -> list[tuple[int, int]]:
    """Les intervalles de lignes que la grammaire n'a pas su lire, bornes comprises.

    Rendus pour être COMPTÉS, pas pour être réparés : un garde qui en trouve doit le dire dans son
    verdict plutôt que de conclure sur une population amputée.
    """
    trouvees: list[tuple[int, int]] = []

    def descend(noeud) -> None:
        if noeud.type == "ERROR":
            trouvees.append((noeud.start_point[0] + 1, noeud.end_point[0] + 1))
            return
        for enfant in noeud.children:
            if enfant.has_error or enfant.type == "ERROR":
                descend(enfant)

    if racine.has_error:
        descend(racine)
    return trouvees


def fichiers_java(racines) -> list[pathlib.Path]:
    """Les sources Java sous `racines`, triées. Le parcours que la plupart des gardes refont."""
    return sorted(f for r in racines if r.is_dir() for f in r.rglob("*.java"))


# --- Le témoin de la grammaire -----------------------------------------------------------------
#
# Il ne teste pas `tree-sitter`, qui a ses propres tests : il teste que les noms dont CE module
# dépend désignent encore ce que ce module croit. Un relèvement de grammaire qui les renommerait
# ferait rougir ici, au lieu de vider silencieusement la population d'un cliquet.

# Chaque commentaire porte SON numéro de ligne, pour que le témoin cite des lignes vérifiables à
# l'œil plutôt que comptées.
#
# **L'ORDRE des cas n'est pas libre.** Les deux défauts du comptage d'accolades se compensent quand ils
# se suivent : une accolade fermante posée dans une chaîne décale la profondeur de tout ce qui vient
# après, si bien qu'une classe imbriquée placée ensuite se retrouve classée JUSTE, pour une mauvaise
# raison. Une première rédaction de ce témoin est tombée dans le piège. Les deux cas fautifs sont donc
# ISOLÉS : la classe imbriquée d'abord, à profondeur non déviée, et la chaîne en dernier.
TEMOIN = b"""class Exterieure {
    // L2 : entre les membres d'une classe.
    private int champ = 1;

    class Imbriquee {
        // L6 : entre les membres d'une classe IMBRIQUEE, sans aucune methode ouverte.
        private int autre = 2;
    }

    Runnable r = () -> {
        // L11 : dans une lambda.
        int y = 2;
    };

    void methode() {
        // L16 : dans un corps de methode.
        String accolade = "}";
        // L18 : encore dans le corps, APRES une accolade fermante posee dans une chaine.
        int x = 1;
    }

    private void aide() {
        // L23 : le corps d une methode PRIVEE, ce que 4974 et 5278 cherchent.
        clic(cible(", ici"), autre(1, 2));
    }
}
"""

# Ce que la STRUCTURE répond sur TEMOIN, ligne par ligne, et qui fait foi.
#
# Le comptage d'accolades se trompe sur deux de ces cinq lignes, chacune pour une cause distincte et
# vérifiable séparément : **L6** parce qu'une classe imbriquée ouvre une seconde accolade sans ouvrir
# de méthode, et **L18** parce qu'une accolade fermante dans un littéral lui fait fermer un bloc qui
# n'était pas ouvert. Sur les trois autres, les deux dispositifs s'accordent : un témoin dont TOUS les
# cas rougiraient sur l'ancien dispositif ne dirait pas où sont ses bornes.
_ATTENDU = {2: False, 6: False, 11: True, 16: True, 18: True}


def _texte(noeuds) -> list[str]:
    """Le texte de chaque nœud, ou sa représentation s'il n'en est pas un.

    Sert aux cas de [verifie_grammaire] : sous un dispositif fautif, `arguments` peut rendre autre
    chose que des nœuds, et un cas qui déréférencerait `.text` lèverait au lieu de rougir.
    """
    return [n.text.decode() if hasattr(n, "text") else str(n).strip() for n in noeuds]


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas que la grammaire doit continuer de classer ainsi. Rend `(nom, réussi)`."""
    racine = arbre(TEMOIN).root_node
    dit = {ligne: dans_un_corps_de_code(racine, ligne) for ligne in _ATTENDU}
    privees = [
        n
        for n in noeuds_de_type(racine, {"method_declaration"})
        if b"private" in n.text.split(b"(")[0]
    ]
    corpsAide = privees[0].text.decode() if privees else ""
    appels = noeuds_de_type(racine, {"method_invocation"})
    clic = next((a for a in appels if a.text.startswith(b"clic(")), None)
    argsDuClic = arguments(clic) if clic is not None else []
    appelVide = next((a for a in appels if a.text.endswith(b"()")), None)
    return [
        ("L2, entre les membres d'une classe : hors corps", dit[2] is False),
        ("L6, entre les membres d'une classe IMBRIQUÉE : hors corps", dit[6] is False),
        ("L11, dans une lambda : dedans", dit[11] is True),
        ("L16, dans un corps de méthode : dedans", dit[16] is True),
        ("L18, après une accolade fermante DANS une chaîne : toujours dedans", dit[18] is True),
        ("le témoin lui-même est lisible", zones_illisibles(racine) == []),
        # LES DEUX PRIMITIVES de #5430, éprouvées sur le même témoin.
        ("une seule méthode PRIVÉE dans le témoin", len(privees) == 1),
        ("son corps est rendu ENTIER, accolade de chaîne comprise", "clic(" in corpsAide),
        (
            "et il s'arrête à SA fermante, sans emporter la classe",
            "class Exterieure" not in corpsAide,
        ),
        ("l'appel `clic` a DEUX arguments", len(argsDuClic) == 2),
        # Robuste à dessein : un dispositif fautif rend autre chose qu'un nœud, et le cas doit
        # ROUGIR plutôt que lever - un cas qui plante masque son verdict au lieu de le rendre.
        (
            "la virgule DANS la chaîne n'en fait pas un troisième",
            _texte(argsDuClic[:1]) == ['cible(", ici")'],
        ),
        ("un appel sans argument en rend zéro", arguments(appelVide) == [] if appelVide else True),
    ]
