#!/usr/bin/env python3
"""Cliquet sur les blocs de `//` qui debordent DANS un corps de methode.

Un commentaire au milieu d une methode ne se lit pas comme une javadoc. La javadoc s adresse a qui
**appelle**, et un paragraphe de pourquoi y est a sa place. Un bloc de quinze lignes entre deux
instructions s adresse a qui **lit le corps**, et il dit presque toujours l une de trois choses : que
le code d en dessous est trop obscur pour se passer d explication, qu une decision aurait du monter
dans une ADR, ou qu un pan d histoire est reste la.

**Le cliquet 4359 ne les voit pas** : il ne compte que les lignes `///`, et sa javadoc declare cette
cecite. Ces blocs-ci sont donc restes hors de toute mesure, sur 10 198 lignes reparties en 4 656
blocs.

**La mesure rassure, et c est pour cela qu elle vaut d etre tenue.** Mediane de 2 lignes, 9e decile a
4, AUCUN bloc au-dessus de 15. Le depot n a pas ce defaut aujourd hui - mais rien ne le tenait, et un
fait mesure une fois n est pas un fait garde. Le seuil de 8 est pose au-dessus du 9e decile, comme
ceux du cliquet 4359, et il rend 79 suspects.

**Ce qu il ferme, et c est la raison de l ecrire maintenant.** Raccourcir une javadoc en poussant son
recit trois lignes plus bas faisait DESCENDRE le cliquet 4359 sans que rien ne soit resorbe. Douze
tranches de #4394 ont eu ce chemin ouvert devant elles.

**Pourquoi un compteur separe du cliquet A30.** Une seule population par compteur : les meler
laisserait un raccourcissement de javadoc compenser un debordement en corps de methode, pour un total
stable et un verdict vert (ADR « Une dette qu'on migre au fil de l'eau se tient par un cliquet, et
toute exclusion nomme son repreneur », regle 2).

**Ce qu il ne tient pas.** Ce qu il faut couper. Un bloc long peut etre justifie - une formule, un
protocole, un contre-exemple - et le script rend des SUSPECTS qu un humain trie. C est un cliquet
`probable`, comme celui de la javadoc.
"""

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, RACINES_ANCREES, rapporte, sort_si_contrat_demande
from _commun.arbre import arbre, dans_un_corps_de_code, verifie_grammaire, zones_illisibles

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4472"

RACINES = RACINES_ANCREES

# Le 9e decile du depot est a 4 lignes. Le seuil est pose a 8, soit le double : il laisse passer le
# regime normal, et il ne signale que ce qui en sort franchement.
SEUIL = 8


def _fin_du_bloc(lignes: list[str], depart: int) -> tuple[int, int]:
    """La ligne qui suit le bloc de `//` commence a `depart`, et son nombre de lignes NON VIDES.

    Une ligne `//` nue aere le bloc, elle ne dit rien : elle ne doit pas l allonger.
    """
    j, compte = depart, 0
    while j < len(lignes):
        suite = lignes[j].strip()
        if not suite.startswith("//") or suite.startswith("///"):
            break
        if suite[2:].strip():
            compte += 1
        j += 1
    return j, compte


def blocs(source: bytes, racine_ast) -> list[tuple[int, int, bool]]:
    """Les blocs de `//` d une source : (ligne de depart, lignes non vides, dans un corps de code).

    **La troisieme valeur est la reponse de la STRUCTURE**, depuis le chantier #5402. Elle etait un
    comptage d accolades, qui declarait lui-meme ne comprendre ni les chaines ni les caracteres : une
    accolade fermante posee dans un litteral lui faisait fermer un bloc qui n etait pas ouvert, et
    toute classe imbriquee passait pour une methode ouverte. Sur les cinq cas du temoin de
    `_commun.arbre`, il se trompait deux fois, une par cause.

    Les `///` sont exclus - ils sont de la javadoc, et le cliquet A30 les compte deja.
    """
    lignes = source.decode("utf-8").split("\n")
    trouves: list[tuple[int, int, bool]] = []
    i = 0
    while i < len(lignes):
        nu = lignes[i].strip()
        if not nu.startswith("//") or nu.startswith("///"):
            i += 1
            continue
        fin, compte = _fin_du_bloc(lignes, i)
        trouves.append((i + 1, compte, dans_un_corps_de_code(racine_ast, i + 1)))
        i = fin
    return trouves


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unités que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu'il RETENAIT. Un ciblage manqué
    donnait donc zéro suspect sur zéro fichier, et ce zéro passait pour un succès.
    """
    racines = [racine] if racine else list(RACINES)
    return sorted(f for r in racines for f in r.rglob("*.java"))


def analyse(racine: pathlib.Path | None = None) -> tuple[list[str], list[str]]:
    """UNE passe sur le corpus, DEUX sorties : les suspects, et ce que la grammaire n a pas lu.

    Les deux ensemble, et non deux fonctions qui parcourent chacune. Mesure du 2026-09-07 sur les
    2 125 fichiers du corpus : deux passes coutaient 2,11 s, une seule en coute 1,13. Le depot retire
    du temps a sa batterie en ce moment meme (#5400), et un garde qui en rajoute par negligence irait
    contre ce travail.
    """
    trouves, zones_dites = [], []
    for f in fichiers(racine):
        source = f.read_bytes()
        racine_ast = arbre(source).root_node
        nom = f.relative_to(RACINE_DEPOT) if f.is_relative_to(RACINE_DEPOT) else f.name
        zones_dites += [f"{nom}:{d}-{b}" for d, b in zones_illisibles(racine_ast)]
        for depart, compte, dans_un_corps in blocs(source, racine_ast):
            if not dans_un_corps:
                continue
            for i in range(compte - SEUIL):
                trouves.append(f"{nom}:{depart}  ligne {SEUIL + i + 1} d un bloc de {compte}")
    return trouves, zones_dites


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par LIGNE au-dela du seuil, comme le cliquet de la javadoc."""
    return analyse(racine)[0]


def non_lus(racine: pathlib.Path | None = None) -> list[str]:
    """Les zones qu aucune grammaire n a su lire, nommees pour etre DITES et non reparees.

    Un garde qui ignore ces zones rend zero suspect sur une population amputee, et ce zero ressemble
    a un succes : c est le defaut que l issue #5007 a corrige en faisant compter les unites LUES.
    La borne est plus fine que le fichier, parce que `tree-sitter` echoue LOCALEMENT - le reste
    demeure interrogeable, et compter le fichier entier comme non lu exagererait la perte.

    Mesure du 2026-09-07 : un seul fichier du depot porte une telle zone, cinq lignes sur 374, et
    **aucun bloc de `//` n y tombe**. Le compte est donc nul en pratique, et c est pourquoi il
    s ecrit maintenant : il ne coute rien a poser, et il couterait un faux verdict a poser trop tard.
    """
    return analyse(racine)[1]


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        def pose(contenu: str) -> None:
            (r / "A.java").write_text(contenu, encoding="utf-8")

        def corps(n: int) -> str:
            lignes = "\n".join(f"        // Ligne {i}." for i in range(n))
            return "class A {\n    void f() {\n" + lignes + "\n        int x = 1;\n    }\n}\n"

        pose(corps(SEUIL))
        cas.append(("un bloc au seuil passe", suspects(r) == []))

        pose(corps(SEUIL + 3))
        vus = suspects(r)
        cas.append((f"un bloc de {SEUIL + 3} lignes coute trois", len(vus) == 3))
        cas.append(("le suspect dit la taille du bloc", f"bloc de {SEUIL + 3}" in vus[0]))

        # LA borne du dispositif : le MEME bloc, entre les membres d une classe, ne coute rien.
        # C est ce qui distingue « le corps est obscur » de « cette section a besoin d un titre ».
        entete = "\n".join(f"    // Ligne {i}." for i in range(SEUIL + 3))
        pose("class A {\n" + entete + "\n    private int x = 1;\n}\n")
        cas.append(("le meme bloc hors corps ne coute rien", suspects(r) == []))

        # LES DEUX CAS QUE LE COMPTAGE D ACCOLADES CLASSAIT FAUX, et la raison d avoir migre.
        # Chacun isole SA cause : les enchainer les laisserait se compenser, et le temoin passerait
        # sans rien prouver.
        #
        # Premier : entre les membres d une classe IMBRIQUEE, deux accolades sont ouvertes sans
        # qu aucune methode le soit. L ancien dispositif lisait une profondeur de 2 et retenait le
        # bloc ; la structure repond « hors corps ».
        imbrique = "\n".join(f"        // Ligne {i}." for i in range(SEUIL + 3))
        pose("class A {\n    class B {\n" + imbrique + "\n        private int x = 1;\n    }\n}\n")
        cas.append(
            ("un bloc entre les membres d une classe imbriquee ne coute rien", suspects(r) == [])
        )

        # Second : une accolade fermante posee dans une CHAINE fermait un bloc qui n etait pas
        # ouvert, et faisait SORTIR de la population tout ce qui suivait dans la methode.
        apres = "\n".join(f"        // Ligne {i}." for i in range(SEUIL + 3))
        pose(
            'class A {\n    void f() {\n        String s = "}";\n'
            + apres
            + "\n        int x = 1;\n    }\n}\n"
        )
        cas.append(("un bloc apres une accolade en chaine coute toujours", len(suspects(r)) == 3))

        # Le garde doit VOIR ce qu il n a pas lu. La borne exacte de la grammaire epinglee est le
        # motif de deconstruction d enregistrement dont le type est QUALIFIE : `case B.P(int x)`
        # rend un noeud ERROR, la forme simple `case P(int x)` et la forme generique `case B.P<C>(..)`
        # se lisent. Mesure du 2026-09-07, `tree-sitter-language-pack` 1.16.1.
        #
        # L echec est LOCAL : le reste du fichier demeure interrogeable, et c est ce que le cas
        # suivant verifie. Le garde nomme la zone au lieu de conclure en aveugle.
        pose(
            "class A {\n    String f(Object o) {\n        return switch (o) {\n"
            '            case B.Point(int x) -> "ok";\n            default -> "";\n'
            "        };\n    }\n}\n"
        )
        cas.append(("une zone que la grammaire ne sait pas lire est NOMMEE", non_lus(r) != []))
        cas.append(("et le fichier reste lisible autour", suspects(r) == []))

        # Un bloc a la profondeur 0, avant la classe (licence, en-tete de fichier), non plus.
        pose("\n".join(f"// Ligne {i}." for i in range(SEUIL + 3)) + "\nclass A {}\n")
        cas.append(("un en-tete de fichier ne coute rien", suspects(r) == []))

        # La javadoc n est PAS de ce compteur : elle a le sien, et les meler laisserait l un
        # compenser l autre.
        javadoc = "\n".join(f"        /// Ligne {i}." for i in range(SEUIL + 3))
        pose("class A {\n    void f() {\n" + javadoc + "\n        int x = 1;\n    }\n}\n")
        cas.append(("la javadoc n entre pas dans ce compte", suspects(r) == []))

        # Une ligne `//` vide aere, elle ne dit rien : elle ne doit pas allonger le bloc.
        vides = "\n".join(f"        // Ligne {i}.\n        //" for i in range(SEUIL))
        pose("class A {\n    void f() {\n" + vides + "\n        int x = 1;\n    }\n}\n")
        cas.append(("les lignes vides n allongent pas le bloc", suspects(r) == []))

        # Deux blocs d un meme corps cumulent : le grain est la ligne, comme pour la javadoc.
        deux = corps(SEUIL + 3).replace(
            "        int x = 1;",
            "        int x = 1;\n"
            + "\n".join(f"        // Autre {i}." for i in range(SEUIL + 2))
            + "\n        int y = 2;",
        )
        pose(deux)
        cas.append(("deux blocs cumulent leur dette", len(suspects(r)) == 5))

    # Le temoin de la grammaire elle-meme : ce sont les noms de noeuds dont ce garde depend, et un
    # relevement qui les renommerait doit rougir ICI plutot que vider la population en silence.
    cas += [(f"grammaire : {nom}", ok) for nom, ok in verifie_grammaire()]

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : le cliquet ne compte pas ce qu'il annonce.",
            file=sys.stderr,
        )
        return 1
    print(f"\n{len(cas)} cas : le cliquet voit le débordement en corps, et laisse le reste.")
    return 0


CONTRAT = {
    "geste": "commentaire qui deborde en corps de methode",
    "population": "PRODUCTION + TESTS",
    "dispositif": "cliquet",
    "seuil": "43, polarite=descend",
    "temoin": "scripts/adr/4472-commentaire-en-corps.py --auto-test",
    "decision": "ADR 4472",
    # Ce garde coute 1,13 s par demande, contre 0,23 s avant sa migration - le prix de l arbre,
    # mesure le 2026-09-07 sur les memes 2 125 fichiers. Declarer ses chemins rend cette hausse
    # indolore sur toute demande qui ne touche pas de Java (ADR 5340).
    #
    # **Un `chemins` INCOMPLET est plus dangereux qu un `chemins` absent** : il tait le garde en
    # silence, la ou son absence le fait LANCER. Ces cinq lignes couvrent donc tout ce dont le
    # verdict depend, et chacune a sa raison :
    #
    #  - les deux arbres Java, qui sont la population ;
    #  - **ce fichier meme**, et c est le piege : `batterie.engage()` ne confronte que les `chemins`
    #    au diff, sans regle particuliere sur la source du garde. Sans cette ligne, une demande qui
    #    REECRIT ce cliquet ne le lance pas. Verifie a la porte locale le 2026-09-07, sur cette
    #    demande-ci, qui l a d abord ecarte ;
    #  - `scripts/_commun/**`, ou vivent `rapporte`, `cliquet` et desormais le lecteur d arbre dont
    #    ce garde delegue sa question centrale ;
    #  - la ligne `ratchet:` de sa propre decision, que `resserre_cliquets.py` deplace.
    "chemins": """
src/main/java/**
src/test/java/**
scripts/adr/4472-commentaire-en-corps.py
scripts/_commun/**
dev-docs/decisions/4472-un-commentaire-long-en-corps-de-methode-est-un-signal.md
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    # UNE passe, dont sortent le verdict et ce que la grammaire n a pas su lire. Ce dernier se DIT
    # sur la sortie d erreur avant le verdict : le compte est nul aujourd hui, et le jour ou il ne
    # le sera plus, le silence serait un faux vert.
    listes, zones = analyse()
    for zone in zones:
        print(f"zone non lue par la grammaire : {zone}", file=sys.stderr)
    if "--releve" in sys.argv:
        for s in listes:
            print(f"  {s}")
        print(f"\n{len(listes)} lignes de commentaire au-delà du seuil, en corps de méthode")
        sys.exit(0)
    sys.exit(
        rapporte(
            ADR,
            "commentaire qui déborde en corps de méthode",
            listes,
            apercu=15,
            lus=len(fichiers()),
        )
    )
