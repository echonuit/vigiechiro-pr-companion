#!/usr/bin/env python3
"""Cliquet : un test TestFX qui pose une scene dimensionnee sans dimensionner son stage.

**Le stage primaire est PARTAGE** entre les classes d un meme fork surefire. Celui qui a exerce le
dimensionnement de l application le laisse a `TailleOuverture` - 900 x 600 - et un `setScene` sur un
stage DEJA DIMENSIONNE ne le redimensionne pas. La scene demandee a 980 x 980 est alors relue a
900 x 600, et tout ce qui vit sous 600 px tombe hors du rectangle que le clic exige.

**Le defaut est intermittent par construction** : il ne se produit que si une classe contaminante
tombe dans le meme fork, ce que `forkCount=1C` redistribue a chaque passe. Il a coute deux
diagnostics faux avant d etre reproduit - une fois reproduit, il l est au caractere pres.

**Ce que ce cliquet ne dit pas** : que le defaut se manifeste. Une boite de dialogue de 300 px de
haut tient dans 600 et ne verra jamais rien. Il rend des SUSPECTS, et le tri est humain - c est un
cliquet `probable`.

**Ce qu il tient** : que la population n augmente pas. 87 classes posent une scene dimensionnee, 2
dimensionnent leur stage. Sans cliquet, la 88e s ecrit sans que rien ne le remarque.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, TESTS_ANCRES, rapporte

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4475"


# Une scene posee AVEC des dimensions explicites : c est la seule forme ou le test AFFIRME une
# taille. Une scene sans dimensions s en remet a son contenu, et n a rien a garantir.
SCENE_DIMENSIONNEE = re.compile(
    r"(\w+)\.setScene\(\s*(?:new\s+Scene|Habillage\.scene)\([^;]*?,\s*[\d.]+\s*,\s*[\d.]+\s*\)",
    re.S,
)


def _dimensionne(texte: str, receveur: str) -> bool:
    """Le stage est-il dimensionne, ou remis a la taille de sa scene.

    Le receveur est celui du `setScene`, et non le nom `stage` : la moitie des `@Start` du depot
    l ont renomme - `modale`, `fenetre`. Chercher `stage.setWidth` laissait passer ceux-la, et le
    garde annoncait vert sur un fichier qu il n avait pas su lire.
    """
    return bool(
        re.search(
            rf"{re.escape(receveur)}\.setWidth\(|{re.escape(receveur)}\.setHeight\(|"
            rf"{re.escape(receveur)}\.sizeToScene\(",
            texte,
        )
    )


def _a_soi(texte: str, receveur: str) -> bool:
    """Le receveur est-il une fenetre que la classe cree pour elle ?

    Une fenetre a soi n est pas partagee : la figer ne coute rien a personne.
    `ConventionsDEcritureTest` l ecrit deja - « Pour une fenetre a soi, `new Stage()` - et alors
    elle n est plus recue, et ce garde ne la regarde plus ». Sans cette exemption le cliquet
    comptait deux modales privees parmi ses suspects, mesure en fermant #4582.
    """
    return bool(re.search(rf"\b{re.escape(receveur)}\s*=\s*new\s+Stage\s*\(", texte))


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par fichier dont AU MOINS une pose herite du stage du fork.

    Toutes les poses sont lues, et non la premiere seule : un fichier qui pose d abord sa modale
    privee verrait son vrai cas cache par le faux.
    """
    racine = racine or TESTS_ANCRES
    trouves = []
    for f in sorted(racine.rglob("*.java")):
        texte = f.read_text(encoding="utf-8")
        if "@Start" not in texte:
            continue
        for pose in SCENE_DIMENSIONNEE.finditer(texte):
            receveur = pose.group(1)
            if _a_soi(texte, receveur) or _dimensionne(texte, receveur):
                continue
            nom = f.relative_to(RACINE_DEPOT) if f.is_relative_to(RACINE_DEPOT) else f.name
            trouves.append(f"{nom}  pose une scène dimensionnée, hérite du stage du fork")
            break
    return trouves


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        def pose(contenu: str) -> None:
            (r / "A.java").write_text(contenu, encoding="utf-8")

        nu = (
            "class A {\n    @Start\n    void start(Stage stage) {\n"
            "        stage.setScene(new Scene(vue, 980, 980));\n        stage.show();\n    }\n}\n"
        )
        pose(nu)
        cas.append(
            ("une scène dimensionnée sans stage dimensionné est un suspect", len(suspects(r)) == 1)
        )

        pose(nu.replace("stage.show();", "stage.setWidth(980);\n        stage.show();"))
        cas.append(("dimensionner le stage lève le suspect", suspects(r) == []))

        pose(nu.replace("stage.show();", "stage.sizeToScene();\n        stage.show();"))
        cas.append(("`sizeToScene` aussi", suspects(r) == []))

        # LA borne : une scene SANS dimensions ne promet aucune taille, donc n a rien a tenir.
        pose(nu.replace("new Scene(vue, 980, 980)", "new Scene(vue)"))
        cas.append(("une scène sans dimensions n'est pas un suspect", suspects(r) == []))

        # Un test qui n est pas un test TestFX n a pas de stage a dimensionner.
        pose(nu.replace("@Start\n", ""))
        cas.append(("sans `@Start`, aucun stage n'est en jeu", suspects(r) == []))

        # La seconde forme rencontree dans le depot : le decorateur maison.
        pose(nu.replace("new Scene(vue, 980, 980)", "Habillage.scene(vue, 900, 400)"))
        cas.append(("`Habillage.scene` compte comme une scène dimensionnée", len(suspects(r)) == 1))

        # LE defaut que le garde a eu lui-meme : la moitie des `@Start` renomment leur stage, et
        # chercher `stage.setWidth` les declarait verts sans les avoir lus.
        pose(
            nu.replace("stage.", "modale.")
            .replace("Stage stage", "Stage modale")
            .replace("modale.show();", "modale.setWidth(980);\n        modale.show();")
        )
        cas.append(("un stage renomme compte comme dimensionne", suspects(r) == []))

        # La declaration s etale souvent sur plusieurs lignes : le motif doit la suivre.
        pose(
            nu.replace(
                "stage.setScene(new Scene(vue, 980, 980));",
                "stage.setScene(new Scene(\n                vue,\n                980,\n                980));",
            )
        )
        cas.append(("une déclaration repliée compte aussi", len(suspects(r)) == 1))

        # Une fenetre A SOI n est pas partagee : la figer ne coute rien a personne, et
        # `ConventionsDEcritureTest` l ecrit deja. Deux modales privees etaient comptees avant
        # cette exemption, mesurees en fermant #4582.
        pose(
            "class A {\n    @Start\n    void start(Stage recu) {\n"
            "        Stage modale = new Stage();\n"
            "        modale.setScene(new Scene(vue, 980, 980));\n        modale.show();\n    }\n}\n"
        )
        cas.append(("une fenêtre à soi n'est pas comptée", suspects(r) == []))

        # LE cas qui rend l exemption sure : une pose privee ne doit pas MASQUER la pose partagee
        # qui la suit. Sans lui, exempter reviendrait a rendre aveugle tout fichier qui ouvre une
        # modale avant de poser son ecran.
        pose(
            "class A {\n    @Start\n    void start(Stage stage) {\n"
            "        Stage modale = new Stage();\n"
            "        modale.setScene(new Scene(vue, 400, 300));\n"
            "        stage.setScene(new Scene(vue, 980, 980));\n        stage.show();\n    }\n}\n"
        )
        cas.append(("une pose privée ne masque pas la pose partagée", len(suspects(r)) == 1))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : le cliquet ne compte pas ce qu'il annonce.",
            file=sys.stderr,
        )
        return 1
    print(f"\n{len(cas)} cas : le cliquet voit le stage hérité, et laisse le reste.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes:
            print(f"  {s}")
        print(f"\n{len(listes)} classes héritent du stage de leur fork")
        sys.exit(0)
    sys.exit(rapporte(ADR, "test TestFX qui hérite du stage de son fork", listes, apercu=12))
