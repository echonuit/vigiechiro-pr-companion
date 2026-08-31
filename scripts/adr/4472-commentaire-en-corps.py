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

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINES_ANCREES, RACINE_DEPOT, rapporte  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4472"

RACINES = RACINES_ANCREES

# Le 9e decile du depot est a 4 lignes. Le seuil est pose a 8, soit le double : il laisse passer le
# regime normal, et il ne signale que ce qui en sort franchement.
SEUIL = 8

# Une profondeur d accolades de 2 ou plus place la ligne DANS un corps de methode : la premiere
# accolade ouvre le type, la seconde la methode. A la profondeur 1 on est entre les membres d une
# classe, ou un bloc de `//` documente une section et non du code.
PROFONDEUR_CORPS = 2


def blocs(fichier: pathlib.Path) -> list[tuple[int, int, int]]:
    """Les blocs de `//` du fichier : (ligne de depart, lignes non vides, profondeur d accolades).

    La profondeur est celle du code qui PRECEDE le bloc, et non celle du bloc lui-meme : un
    commentaire ne change pas l imbrication. Les `///` sont exclus - ils sont de la javadoc, et le
    cliquet A30 les compte deja.
    """
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    trouves, profondeur, i = [], 0, 0
    while i < len(lignes):
        nu = lignes[i].strip()
        if nu.startswith("//") and not nu.startswith("///"):
            j, compte = i, 0
            while j < len(lignes):
                suite = lignes[j].strip()
                if not suite.startswith("//") or suite.startswith("///"):
                    break
                if suite[2:].strip():
                    compte += 1
                j += 1
            trouves.append((i + 1, compte, profondeur))
            i = j
            continue
        if not nu.startswith("//"):
            # Comptage naif des accolades : il ne comprend ni les chaines ni les caracteres. Une
            # accolade dans un litteral fausserait la profondeur d une methode - assume, parce que
            # le verdict ne bascule que si elle deplace un bloc de part et d autre de la borne,
            # et qu un faux positif se trie a la lecture comme les autres.
            profondeur += nu.count("{") - nu.count("}")
        i += 1
    return trouves


def suspects(racine: pathlib.Path = None) -> list[str]:
    """Un suspect par LIGNE au-dela du seuil, comme le cliquet de la javadoc."""
    racines = [racine] if racine else list(RACINES)
    trouves = []
    for f in sorted(f for r in racines for f in r.rglob("*.java")):
        nom = f.relative_to(RACINE_DEPOT) if f.is_relative_to(RACINE_DEPOT) else f.name
        for depart, compte, profondeur in blocs(f):
            if profondeur < PROFONDEUR_CORPS:
                continue
            for i in range(compte - SEUIL):
                trouves.append(f"{nom}:{depart}  ligne {SEUIL + i + 1} d un bloc de {compte}")
    return trouves


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


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes:
            print(f"  {s}")
        print(f"\n{len(listes)} lignes de commentaire au-delà du seuil, en corps de méthode")
        sys.exit(0)
    sys.exit(rapporte(ADR, "commentaire qui déborde en corps de méthode", listes, apercu=15))
