#!/usr/bin/env python3
"""Une attente qui lit le graphe de scene le lit SUR le fil JavaFX (issue #5278).

`Attente.queSurLeFil` existe depuis #4408, et sa javadoc dit pourquoi :

    Un predicat qui touche le graphe de scene doit etre lu sur le fil FX, qui n est pas partageable :
    sinon il lit un graphe qu un autre fil est en train d ecrire.

Le patron n a pas essaime. Mesure du 2026-09-05 : 138 appels a `Attente.que` ou `queSurLeFil`, dont
HUIT sur le fil, et 63 qui lisent le graphe depuis le fil du test. Les lots #5269 et #5279 les ont tous
convertis : le cliquet est a zero, et une attente ajoutee sans etre lue sur le fil rougit.

## Ce que cela produit, et ce n est pas une hypothese

`RetourApresVerificationE2ETest.depuis_multisite_la_verification_se_propage` a leve en CI :

    java.lang.IndexOutOfBoundsException: Index 6 out of bounds for length 6

Son predicat lisait `getItems()` d une `TableView` que le chargement asynchrone remplacait. Un index
egal a la longueur est la signature d une lecture concurrente, pas d un decalage applicatif. Le banc
est au releve des bancs instables, 2 chutes en tete sur 884 tirages et 3 de plus comme victime.

## La regle se DERIVE des lectures de noeuds

Un predicat touche le graphe s il appelle `lookup(`, `queryAs`, `getItems()`, `getScene()`,
`getChildren()` ou `getText()`. Ce sont des lectures de noeuds, et elles n ont de sens que sur le fil
FX.

Deriver plutot qu enumerer une liste de classes est delibere : une liste a tenir a la main derive de
ce qu elle decrit sans que rien ne rougisse, ce que l ADR 5258 vient de mesurer sur une autre
population.

## Ce que ce garde NE fait pas, et c est declare

**Il ne dit pas qu une conversion est due.** `queSurLeFil` fait un aller-retour sur le fil FX a chaque
tour de boucle : la ou le predicat ne touche le graphe que par un chemin sur, la conversion couterait
sans rien tenir. Le garde COMPTE ; le jugement reste au site, et un site laisse en `que` ecrit sa
raison plutot que de sortir du compte en silence.

**Il SUIT un predicat qui delegue, depuis #5353, et cette limite-la est levee.** Elle disait : « la
suivre demanderait un graphe d appels, la ou le motif textuel attrape deja la population entiere ». La
seconde moitie etait fausse - TRENTE-NEUF sites lisaient le graphe par une aide de leur propre fichier,
et aucun n etait compte - et la premiere l etait pour le cas courant : une aide du MEME fichier se
trouve par deux passes textuelles, sans graphe d appels.

Ce que le garde fait desormais : il releve les methodes du fichier dont le corps lit le graphe, ferme
cet ensemble transitivement, puis compte tout predicat qui les appelle. `robot.interact(` exempte une
aide comme il exempte un predicat, sans quoi le garde accusait `GesteVisible.amenerDansLeCadre`.

**Ce qu il ne suit toujours pas** : une aide heritee d une classe mere, ou definie dans un autre
fichier. Le motif ne lit qu un fichier a la fois. Limite declaree, et mesuree a zero site connu -
mais cette mesure-la n a PAS de dispositif, contrairement au reste.

Usage :
    python3 scripts/adr/5278-attente-hors-du-fil.py
    python3 scripts/adr/5278-attente-hors-du-fil.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import TESTS_ANCRES, rapporte, sort_si_contrat_demande
from _commun.arbre import LecteurAbsent, arbre, noeuds_de_type, zones_illisibles

# `Attente.que` ET le `waitFor` NU, qui porte la meme faute. La limite etait declaree et non
# comptee : un site y vivait, `AttenteAvantClic.attendreCliquable`, dont le predicat lisait le
# graphe depuis le fil du test en boucle et dont le revelateur y ECRIVAIT (#5330).
#
# `queSurLeFil` reste dans le motif pour que l auto-test puisse montrer qu il SORT du compte :
# un detecteur qui ne verrait jamais la forme juste ne prouverait pas qu il la distingue.
APPEL = re.compile(r"Attente\.(que|queSurLeFil)\s*\(|(WaitForAsyncUtils\.waitFor)\s*\(")
# Les lectures de noeuds, en DEUX familles.
#
# La premiere CHERCHE un noeud dans le graphe : `lookup(`, `queryAs`, et les lectures de collection
# qui la suivent. `getText()` y est parce qu un libelle se lit sur le noeud qui le porte, et que c est
# la forme la plus frequente de l attente « le texte a change ».
#
# La seconde lit une PROPRIETE sur un noeud deja capture, et n emploie aucun motif de la premiere :
#
#     Attente.que(bandeau::isVisible, "que les compteurs reflètent la base restaurée");
#     Attente.que(() -> !verifier.isDisabled(), "le bouton devient actif", 5_000L);
#
# Elle manquait, et le trou n etait pas neutre : dix sites, dont un dans chacun des TROIS bancs les
# plus accuses du releve. `MainViewTest` est tombe quatre fois sur l assertion que gardait une telle
# attente, posee par #4694 - le bon geste sous la mauvaise forme, que rien ne pouvait dire (#5323).
LECTURE_DE_NOEUD = re.compile(
    r"lookup\(|queryAs|\.getItems\(\)|\.getScene\(\)|getChildren\(\)|\.getText\(\)"
    r"|(?:::|\.)(?:isVisible|isDisabled|isManaged|isSelected|isFocused)\b"
    # Les formes qui INTERROGENT une `NodeQuery` deja construite : le predicat peut n avoir
    # aucun `lookup(` visible et parcourir le graphe quand meme, parce qu une aide le lui a
    # prepare. Un seul site du depot etait dans ce cas, et c est celui que #5330 corrige.
    r"|\.tryQuery\(\)|\.queryAll\(\)|\.tryQueryAs\("
)

# Les DECLARATIONS de methode du fichier, pour suivre un predicat qui delegue.
#
# Le garde declarait autrefois qu il ne suivait pas la delegation, « la suivre demanderait un graphe
# d appels, la ou le motif textuel attrape deja la population entiere ». La seconde moitie etait
# fausse : 37 sites du depot lisaient le graphe par une aide de leur propre fichier, et aucun n etait
# compte. La premiere l etait aussi pour le cas courant - une aide du MEME fichier se trouve par deux
# passes textuelles, sans graphe d appels (#5353).
#
# Les mots-cles sont exclus : `if (...) {` et `for (...) {` ressemblent a une declaration.
DECLARATION = re.compile(
    r"^[ \t]+(?:[\w<>\[\],?@. \t]+?[ \t])(\w+)[ \t]*\([^;{)]*\)[ \t]*(?:throws [\w, .]+)?\{",
    re.M,
)
MOTS_CLES = frozenset(
    (
        "if",
        "for",
        "while",
        "switch",
        "catch",
        "try",
        "synchronized",
        "else",
        "do",
        "return",
        "new",
        "case",
    )
)

# Au-dela, ce n est plus un appel mais un fichier mal ferme : la borne evite de balayer la source
# entiere si une parenthese manque, et le dit par un suspect plutot qu en bouclant.
BORNE = 4000


def aides_qui_lisent(racine_ast) -> frozenset[str]:
    """Les methodes du fichier dont le corps lit le graphe, DIRECTEMENT ou par une autre aide.

    La fermeture est transitive : une aide qui appelle une aide qui lit, lit. Deux tours suffisent en
    pratique, mais la boucle va jusqu au point fixe plutot que de parier sur la profondeur.

    **Le corps se borne par la STRUCTURE depuis #5430.** L equilibrage d accolades qui le decoupait
    s arretait sur une accolade vivant dans une CHAINE, et rendait un corps tronque :

        void aide() { String s = "}"; lookup(".x"); }   ->   '{ String s = "}'

    Le `lookup(` disparaissait, l aide n etait donc pas reconnue comme lisant le graphe, et
    l attente qui lui delegue echappait au cliquet. Un faux negatif SILENCIEUX, sur un cliquet a
    zero. Et l equilibrage abandonnait au-dela de `BORNE`, soit 4 000 caracteres : 27 corps du
    corpus la depassent, le plus long faisant 11 248 caracteres, et tout ce qu ils ecrivent
    au-dela etait invisible.

    Les mots-cles n ont plus a etre exclus : `if (...) {` n est pas une declaration de methode pour
    la grammaire, alors qu il en avait l apparence pour un motif.
    """
    corps = {}
    for declaration in noeuds_de_type(
        racine_ast, {"method_declaration", "constructor_declaration"}
    ):
        nom_noeud = declaration.child_by_field_name("name")
        corps_noeud = declaration.child_by_field_name("body")
        if nom_noeud is None or corps_noeud is None:
            continue
        corps[nom_noeud.text.decode()] = corps_noeud.text.decode()

    # `robot.interact(` exempte une AIDE comme il exempte un predicat, et pour la meme raison : ce
    # qu il enveloppe est lu SUR le fil. Sans cette symetrie, le garde accusait
    # `GesteVisible.amenerDansLeCadre`, dont l aide fait tout son travail dans deux `interact` - du bon
    # travail, et l ADR 4002 dit ce qu il advient d un garde qui crie dessus.
    #
    # L approximation est la MEME que pour les predicats : la presence de l appel suffit, on ne verifie
    # pas que toute lecture y est enfermee. La declarer ici plutot que de la laisser deviner.
    surLeFil = {nom for nom, texte in corps.items() if "robot.interact(" in texte}
    lisent = {
        nom
        for nom, texte in corps.items()
        if LECTURE_DE_NOEUD.search(texte) and nom not in surLeFil
    }
    while True:
        gagnees = {
            nom
            for nom, texte in corps.items()
            if nom not in lisent
            and nom not in surLeFil
            and any(re.search(rf"\b{re.escape(a)}\s*\(", texte) for a in lisent)
        }
        if not gagnees:
            return frozenset(lisent)
        lisent |= gagnees


def appelle_une_aide(corps: str, aides: frozenset[str]) -> bool:
    """Le predicat DELEGUE-t-il a une aide qui lit le graphe ?"""
    return any(re.search(rf"\b{re.escape(aide)}\s*\(", corps) for aide in aides)


# Les gestes d attente que ce garde regarde, par le NOM de la methode appelee. `queSurLeFil` est
# la forme juste, elle sort du compte plus bas.
ATTENTES = {"que", "queSurLeFil", "waitFor"}


def appels_d_attente(racine_ast) -> list:
    """Les appels d attente du fichier, par la structure et non par un motif.

    Un motif compte ce qui est ECRIT `Attente.que(`, y compris dans une chaine ou un commentaire.
    La grammaire ne rend que des appels.
    """
    trouves = []
    for appel in noeuds_de_type(racine_ast, {"method_invocation"}):
        nom = appel.child_by_field_name("name")
        objet = appel.child_by_field_name("object")
        if nom is None or objet is None or nom.text.decode() not in ATTENTES:
            continue
        if objet.text.decode() in {"Attente", "WaitForAsyncUtils"}:
            trouves.append(appel)
    return trouves


def sites(source: str) -> list[int]:
    """Les lignes des `Attente.que` dont l argument lit le graphe de scene."""
    trouves = []
    racine_ast = arbre(source.encode("utf-8")).root_node
    aides = aides_qui_lisent(racine_ast)
    for appel in appels_d_attente(racine_ast):
        if appel.child_by_field_name("name").text.decode() == "queSurLeFil":
            continue
        corps = appel.text.decode()
        if not LECTURE_DE_NOEUD.search(corps) and not appelle_une_aide(corps, aides):
            continue
        # Un predicat qui passe par `robot.interact(...)` lit SUR le fil FX : c est la forme juste
        # pour un `waitFor` nu, et `GesteVisible.amenerDansLeCadre` l emploie. Sans cette exception,
        # le garde accuserait un site correct, et l ADR 4002 dit ce qu il advient d un garde qui crie
        # sur du bon travail.
        if "robot.interact(" in corps:
            continue
        trouves.append(appel.start_point[0] + 1)
    return trouves


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les sites fautifs sous `racine`, nommes par leur chemin et leur ligne.

    La racine s INJECTE parce que `verifie_scripts.py` exerce ce detecteur sur un arbre temporaire :
    un detecteur qui ne lit qu un chemin fixe n est tenu que par son cliquet, c est-a-dire par un
    compte qui ne monte pas.
    """
    racine = TESTS_ANCRES if racine is None else racine
    fautifs = []
    for fichier in sorted(racine.rglob("*.java")):
        source = fichier.read_text(encoding="utf-8", errors="replace")
        ou = fichier.relative_to(racine)
        fautifs += [f"{ou}:{ligne}" for ligne in sites(source)]
    return fautifs


def lus(racine: pathlib.Path | None = None) -> int:
    """Le nombre d appels a `Attente` lus : ce que le garde a REGARDE, pas ce qu il a retenu."""
    racine = TESTS_ANCRES if racine is None else racine
    return sum(
        len(appels_d_attente(arbre(f.read_bytes()).root_node)) for f in racine.rglob("*.java")
    )


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    lecture = '() -> !robot.lookup("#t").queryAll().isEmpty()'
    fautif = f'Attente.que(\n {lecture},\n "que ca paraisse");\n'
    verifie("un que qui lit le graphe est vu", sites(fautif), [1])

    surlefil = fautif.replace("Attente.que(", "Attente.queSurLeFil(")
    verifie("le MEME site en queSurLeFil ne l est plus", sites(surlefil), [])

    # Le sens NEGATIF, sans quoi un motif qui accepterait tout passerait le premier cas et rendrait le
    # garde vert sur un depot entierement fautif.
    sobre = 'Attente.que(() -> vm.chargement().not().get(), "que le chargement finisse");\n'
    verifie("un predicat qui ne touche pas le graphe est ignore", sites(sobre), [])

    verifie("une source sans Attente ne rend rien", sites("int x = lookup(3);\n"), [])

    multiple = fautif + "\n" + sobre + "\n" + fautif
    verifie("plusieurs sites sont tous vus", len(sites(multiple)), 2)
    # Les deux sauts de jointure decalent le second site : la ligne comptee est la 7, pas la 6.
    verifie("et leurs lignes sont justes", sites(multiple), [1, 7])

    # LES DEUX FORMES DE LA LECTURE DE PROPRIETE, eprouvees separement : une reference de methode
    # et une lambda ne s ecrivent pas pareil, et un motif qui n en verrait qu une laisserait passer
    # la moitie des dix sites que #5323 a trouves.
    parRef = 'Attente.que(bandeau::isVisible, "que ca paraisse");\n'
    verifie("une reference de methode sur un noeud est vue", sites(parRef), [1])
    parLambda = 'Attente.que(() -> !verifier.isDisabled(), "que ca s active");\n'
    verifie("une lambda qui lit une propriete est vue", sites(parLambda), [1])
    verifie(
        "les memes en queSurLeFil sortent du compte",
        sites(parRef.replace("que(", "queSurLeFil(") + parLambda.replace("que(", "queSurLeFil(")),
        [],
    )

    # LE `waitFor` NU, qui porte la meme faute et que la population ignorait. Sa limite etait
    # DECLAREE et non comptee, et un site y vivait (#5330).
    nu = "WaitForAsyncUtils.waitFor(5, S, () -> q.tryQuery().isPresent());\n"
    verifie("un waitFor nu qui interroge le graphe est vu", sites(nu), [1])

    # Et la forme JUSTE d un waitFor nu : le predicat passe par `robot.interact`, donc sur le fil FX.
    # Sans ce cas, le garde accuserait `GesteVisible.amenerDansLeCadre`, qui est correct.
    surFil = (
        "WaitForAsyncUtils.waitFor(5, S, () -> {\n"
        "  robot.interact(() -> v.set(q.tryQuery().isPresent()));\n  return v.get(); });\n"
    )
    verifie("le meme, passant par robot.interact, sort du compte", sites(surFil), [])

    # Les cinq lectures de noeud, une par une : sans cela un motif qui n en verrait qu une passerait
    # tout ce qui precede, le premier cas employant `lookup(` ET `queryAll`.
    for lecture in (".getItems()", ".getScene()", "getChildren()", ".getText()", "queryAs"):
        un = f'Attente.que(() -> n{lecture}.isEmpty(), "que ca vienne");\n'
        verifie(f"la lecture {lecture} est vue", sites(un), [1])

    # LA DELEGATION A UNE AIDE DU FICHIER, qui etait une limite DECLAREE du garde. Sa raison disait
    # que le motif « attrape deja la population entiere » : elle en manquait quarante (#5353).
    parAide = (
        'Attente.que(() -> !texte(robot, "#lbl").isBlank(), "que ca vienne");\n'
        "    private static String texte(FxRobot robot, String sel) {\n"
        "        return robot.lookup(sel).queryAs(Label.class).getText();\n    }\n"
    )
    verifie("une attente qui lit par une aide du fichier est vue", sites(parAide), [1])

    # Le sens NEGATIF de la delegation, et c est lui qui distingue « suivre les aides » de « accuser
    # tout appel » : une aide qui ne touche pas le graphe ne doit rien declencher.
    parAideSobre = (
        'Attente.que(() -> compte(vm) > 3, "que ca monte");\n'
        "    private static int compte(Vm vm) {\n        return vm.total();\n    }\n"
    )
    verifie("une aide qui NE lit PAS le graphe ne compte pas", sites(parAideSobre), [])

    # La MEME delegation en queSurLeFil sort du compte, comme les formes directes.
    verifie(
        "la meme, en queSurLeFil, sort du compte",
        sites(parAide.replace("Attente.que(", "Attente.queSurLeFil(")),
        [],
    )

    # La chaine TRANSITIVE : l attente appelle une aide qui appelle l aide qui lit. Sans la fermeture,
    # ce cas passerait au travers, et c est la forme qu un refactoring produit naturellement.
    parChaine = (
        'Attente.que(() -> pret(robot), "que ca vienne");\n'
        "    private static boolean pret(FxRobot robot) {\n        return !texte(robot).isBlank();\n    }\n"
        "    private static String texte(FxRobot robot) {\n"
        '        return robot.lookup("#l").queryAs(Label.class).getText();\n    }\n'
    )
    verifie("une delegation en DEUX crans est vue", sites(parChaine), [1])

    # UNE AIDE QUI TRAVAILLE DANS `robot.interact` sort du compte, comme un predicat qui le fait.
    # `GesteVisible.amenerDansLeCadre` est dans ce cas, et le garde l accusait.
    parAideSurFil = (
        "WaitForAsyncUtils.waitFor(5, S, () -> unePasse(robot, sel));\n"
        "    private static boolean unePasse(FxRobot robot, String sel) {\n"
        "        robot.interact(() -> v.set(robot.lookup(sel).query().isVisible()));\n"
        "        return v.get();\n    }\n"
    )
    verifie("une aide qui travaille dans robot.interact sort du compte", sites(parAideSurFil), [])

    # Et TRANSITIVEMENT : l aide protegee appelle une aide qui lit, et cela ne doit pas la ramener
    # dans le compte. C est le defaut qu a eu ce garde en s elargissant - la fermeture rattrapait par
    # un cran ce que l exemption venait de retirer.
    parAideSurFilTransitive = (
        "WaitForAsyncUtils.waitFor(5, S, () -> unePasse(robot, sel));\n"
        "    private static boolean unePasse(FxRobot robot, String sel) {\n"
        "        robot.interact(() -> v.set(dansLeCadre(robot, sel)));\n        return v.get();\n    }\n"
        "    private static boolean dansLeCadre(FxRobot robot, String sel) {\n"
        "        return robot.lookup(sel).query().isVisible();\n    }\n"
    )
    verifie(
        "et transitivement : l aide protegee ne revient pas par celle qu elle appelle",
        sites(parAideSurFilTransitive),
        [],
    )

    # Un `if (...) {` ressemble a une declaration de methode POUR UN MOTIF. S il etait pris pour une
    # aide, son nom `if` finirait dans l ensemble et n importe quel predicat portant `if (` serait
    # accuse. Depuis #5430 la grammaire tranche : un `if_statement` n est pas une declaration.
    verifie(
        "un mot-cle n est pas pris pour une aide",
        "if"
        in aides_qui_lisent(arbre(b"class T { void f() { if (x) { n.getText(); } } }").root_node),
        False,
    )

    # UNE ACCOLADE DANS UNE CHAINE ne borne pas un corps. L equilibrage rendait `{ String s = "}`,
    # donc le `lookup(` disparaissait, donc l aide n etait pas reconnue, donc l attente qui lui
    # delegue echappait au cliquet : un faux negatif silencieux, sur un cliquet a zero.
    verifie(
        "une accolade dans une chaine ne tronque pas le corps d une aide",
        sorted(
            aides_qui_lisent(
                arbre(b'class T { void aide() { String s = "}"; lookup(".x"); } }').root_node
            )
        ),
        ["aide"],
    )

    # UNE ATTENTE CITEE EN DOC-COMMENT n est pas un appel. Le motif en comptait une de plus dans
    # `AttenteAvantClic`, ou la doc-comment cite `WaitForAsyncUtils.waitFor(...)` pour l expliquer :
    # le garde s attribuait la lecture d un appel qui n existe pas.
    verifie(
        "une attente citee en doc-comment n est pas lue",
        len(
            appels_d_attente(
                arbre(
                    b"class T {\n    /// Voir Attente.que(...) pour la forme.\n    void f() {}\n}"
                ).root_node
            )
        ),
        0,
    )

    # Un appel non ferme ne doit ni boucler ni faire planter le garde. Il n est PLUS compte comme
    # un site depuis #5430, et c est le bon comportement : la grammaire ne rend pas un appel qu elle
    # n a pas su lire. Mais un garde qui se tait sur ce qu il n a pas lu conclut sur une population
    # amputee, et ce zero ressemble a un succes (#5007) : la zone se DIT.
    verifie("un appel non ferme ne fait pas planter", sites('Attente.que(() -> lookup("#a")'), [])
    verifie(
        "et la zone illisible est NOMMEE plutot que tue",
        len(
            zones_illisibles(
                arbre(b'class T { void f() { Attente.que(() -> lookup("#a") } }').root_node
            )
        )
        > 0,
        True,
    )

    verifie("le garde a lu des appels reels", lus() > 0, True)
    return echecs


CONTRAT = {
    "geste": "attente dont le predicat lit le graphe de scene depuis le fil du test",
    "population": "les appels a `Attente.que` de src/test/java, l argument etant delimite par la "
    "STRUCTURE depuis #5430. `queSurLeFil` en est exclu : c est la forme JUSTE. Un predicat qui "
    "delegue a une aide du meme fichier EST suivi, transitivement, depuis #5353. Un "
    "`WaitForAsyncUtils.waitFor` NU porte la meme faute sans etre compte, et une aide vivant dans un "
    "AUTRE fichier echappe encore : deux limites declarees, la seconde trouvee a la passe 7 de la "
    "cloture de #5277 (#5330)",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/5278-attente-hors-du-fil.py --auto-test",
    "decision": "ADR 5278",
    # Lire par l arbre coute, et #5400 retire du temps a la batterie. Declarer les chemins rend la
    # hausse indolore sur toute demande qui ne touche pas de Java (ADR 5340). Un `chemins`
    # INCOMPLET tait le garde en silence, la ou son absence le fait LANCER.
    "chemins": """
src/test/java/**
scripts/adr/5278-attente-hors-du-fil.py
scripts/_commun/**
dev-docs/decisions/5278-une-attente-qui-lit-le-graphe-le-lit-sur-le-fil.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    # Le cliquet est a ZERO : un garde qui ne sait pas lire rendrait zero suspect, et ce zero-la
    # serait indiscernable d un succes. Il REFUSE plutot, et il DIT ce que la grammaire n a pas lu.
    try:
        fautifs = suspects()
        combien = lus()
    except LecteurAbsent as absent:
        raise SystemExit(str(absent)) from absent
    for fichier in sorted(TESTS_ANCRES.rglob("*.java")):
        for depart, borne in zones_illisibles(arbre(fichier.read_bytes()).root_node):
            print(
                f"zone non lue par la grammaire : {fichier.name}:{depart}-{borne}", file=sys.stderr
            )
    raise SystemExit(
        rapporte(
            "5278",
            "attentes qui lisent le graphe de scene hors du fil JavaFX",
            fautifs,
            apercu=12,
            lus=combien,
        )
    )
