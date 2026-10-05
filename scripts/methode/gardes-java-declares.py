#!/usr/bin/env python3
"""Une classe de test qui juge la PROSE est declaree, ou la porte ne la lancera jamais (#5373).

    python3 scripts/methode/gardes-java-declares.py
    python3 scripts/methode/gardes-java-declares.py --auto-test

## Ce que ce garde ferme

`scripts/batterie.py` repond a « quels controles ce diff engage-t-il ? », et sa population s arretait
au **Python**. Or ce depot teste sa documentation comme du code : des classes Java lisent des `.md`
et refusent quand ils derivent.

La demande #5356 a rougi sur `build` pour cette raison - un `enforced_by` qui portait un argument, la
ou l invariant cherche un fichier - et la porte ne pouvait pas le voir. Elle coute pourtant 2,4 s pour vingt et un invariants - temps de la CLASSE sur un arbre chaud. Mesure du 2026-10-05 : 2,8 s a chaud, 5,1 s sur un arbre plus froid, et 11,4 s de PAROI pour l invocation entiere. Les trois decrivent la meme classe, et un chiffre sans son protocole se compare a tort (#5884), soit
pour vingt et un invariants, contre huit minutes de `build` entier : c est exactement le profil qu une
batterie locale cherche, bon marche et qui rattrape souvent.

## Pourquoi une liste, alors que l ADR 3450 les refuse

Parce que ce garde la TIENT. L ADR 3450 refuse « une liste de classes sensibles » au motif qu « une
liste ne voit que ce qu on y a mis et se perime ». La difference est ici : la population est
**derivee** de l arbre - toute classe de test qui CONSTRUIT un chemin vers de la prose - et la
declaration lui est confrontee. Une sixieme classe qui lirait un `.md` sans etre declaree fait
rougir.

Une liste qu un garde confronte n est plus une liste, c est un inventaire.

## Ce qu il cherche, et ce qu il ne voit pas

Un chemin **construit** (`Path.of`, `Paths.get`, `new File`) vers `docs/`, `dev-docs/`, `brief/`, un
`mkdocs*.yml`, un `.md` racine ou `.github/workflows`. Une simple mention en commentaire ne compte
pas : c est la meme regle que `porte_l_option`, « le commentaire cite la chose, il ne la fait pas ».

**Il ne voit pas** une classe qui passerait par un HELPER pour lire la prose. `EnregistreurDeFilm`
est dans ce cas, et il n est pas une classe de test : surefire ne le lance pas, et les classes qui
l emploient sont deja declarees. La faille est reelle et connue ; elle se refermera si on la
constate, pas par precaution.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, rapporte, sort_si_contrat_demande

ADR = "5373"

# Le paquet ou le depot rassemble ses invariants de structure. La population du code y est
# BORNEE : hors de lui, « construit un chemin vers `src/` » attrape des tests d IHM qui citent
# une source sans juger la structure (#5884).
PAQUET_DES_INVARIANTS = "architecture"

# Un chemin CONSTRUIT, par opposition a un chemin cite.
CONSTRUIT = re.compile(r"(?:Path\.of|Paths\.get|new File)\s*\(([^;]{0,200})")
PROSE = re.compile(
    r'"(?:dev-)?docs?["/]|"brief["/]|mkdocs|'
    r'"(?:README|CONTRIBUTING|TESTING|REMERCIEMENTS|CLAUDE|AGENTS)\.md"|'
    r'"\.github/workflows"'
)
# ⟨le pendant pour le CODE (#5884)⟩ Meme dessin que `PROSE`, applique aux racines du code et au
# fichier de bati. `pom.xml` y figure parce que deux classes n en dependent que par lui.
CODE = re.compile(r'"src["/,]|"pom\.xml"|"main"|"migration')
# ArchUnit lit le BYTECODE et ne construit aucun chemin : `ArchitectureTest` serait invisible de
# `CONSTRUIT`. Le signal est donc l importeur lui-meme, nomme et non devine.
ARCHUNIT = re.compile(r"\bClassFileImporter\b")


def lisent_la_prose(racine: pathlib.Path | None = None) -> set[str]:
    """Les classes de TEST qui construisent un chemin vers de la prose.

    `*Test.java` seulement : c est ce que surefire lance ici, et une classe qu on ne peut pas lancer
    n a pas sa place dans une batterie qui lance.
    """
    arbre = (racine or RACINE_DEPOT) / "src" / "test" / "java"
    if not arbre.is_dir():
        return set()
    trouves = set()
    for f in arbre.rglob("*Test.java"):
        texte = f.read_text(encoding="utf-8", errors="ignore")
        for m in CONSTRUIT.finditer(texte):
            if PROSE.search(m.group(1)):
                trouves.add(f.stem)
                break
    return trouves


def jugent_le_code(racine: pathlib.Path | None = None) -> set[str]:
    """Les classes de TEST qui jugent le CODE : par un chemin construit, ou par ArchUnit.

    Pendant de `lisent_la_prose`, et il manquait : treize classes du paquet `architecture` jugeaient la
    structure du depot sans que la porte les nomme, ni jouees ni imprimees. Un lot qui en cassait une
    l apprenait de `build`, huit minutes plus tard (#5884).

    **Deux signaux, parce qu un seul ne suffit pas.** Dix classes construisent un chemin vers
    `src/`, `pom.xml` ou les migrations, et `CONSTRUIT` les voit. `ArchitectureTest` n en construit
    AUCUN : elle lit le bytecode par ArchUnit, donc le signal est `ClassFileImporter`.

    ## La limite, nommee plutot que tue

    **Une classe qui ne lit RIEN echappe aux deux.** `ButoirsTestFxTest` verifie que les
    coupe-circuits d interblocage de TestFX sont ceux que le `pom.xml` a calibres, et elle le fait en
    lisant des PROPRIETES SYSTEME que surefire lui passe. Elle ne construit pas de chemin et n importe
    pas de bytecode : sa dependance au `pom.xml` est reelle et **non derivable**.

    Elle est donc declaree a la main dans `GARDES_JAVA`, et une quatorzieme classe de la meme forme
    naitrait non declaree sans que ce garde la voie. C est le premier trou connu de cette population,
    et il est ecrit ici plutot que decouvert par quelqu un d autre.

    ## Et la population est BORNEE au paquet des invariants, par mesure

    Sans cette borne, le signal rend **37** classes au lieu de treize : vingt-quatre classes d ailleurs
    construisent un chemin vers `src/` sans juger la structure pour autant. `ChargementFxmlTest` charge
    des FXML, `ContrasteAATest` lit des feuilles de style, `ClipDeModaleTest` filme une modale : ce
    sont des tests d IHM qui CITENT une source, pas des invariants qui la jugent.

    Les declarer ferait jouer des tests d IHM par la porte, ce qui est une autre decision et un autre
    cout. Le signal « construit un chemin vers `src/` » conflond donc **juger la structure** et
    **referencer une source**, exactement comme un releve de chemins litteraux confond un corpus et
    des donnees de test. La borne est le PAQUET, qui est ce que le depot a deja choisi en les y
    rassemblant.

    **Les vingt-quatre sont un constat, pas une dette de ce lot** : rien ne dit qu elles devraient
    entrer dans la porte, et la question se pose separement. Le compte est ecrit ici pour que personne
    n ait a le remesurer.
    """
    arbre = (racine or RACINE_DEPOT) / "src" / "test" / "java"
    if not arbre.is_dir():
        return set()
    trouves = set()
    for f in arbre.rglob("*Test.java"):
        if PAQUET_DES_INVARIANTS not in f.parts:
            continue
        texte = f.read_text(encoding="utf-8", errors="ignore")
        if ARCHUNIT.search(texte):
            trouves.add(f.stem)
            continue
        for m in CONSTRUIT.finditer(texte):
            if CODE.search(m.group(1)):
                trouves.add(f.stem)
                break
    return trouves


def declarees(racine: pathlib.Path | None = None) -> set[str]:
    """Ce que `scripts/batterie.py` declare connaitre."""
    sys.path.insert(0, str((racine or RACINE_DEPOT) / "scripts"))
    try:
        import importlib

        import batterie

        importlib.reload(batterie)
        return set(batterie.GARDES_JAVA)
    except (ImportError, AttributeError) as erreur:
        # ⟨on ne se tait pas sur une porte cassee⟩ Rendre un ensemble vide en silence ferait de
        # TOUTES les classes des suspectes, donc un rouge tonitruant pour la mauvaise raison - « un
        # rouge pour la mauvaise raison ne prouve rien » (ADR 4918). On dit donc ce qui manque.
        print(
            f"La porte est illisible ({erreur.__class__.__name__}: {erreur}) : aucune declaration"
            " n a pu etre lue, et toutes les classes vont paraitre suspectes.",
            file=sys.stderr,
        )
        return set()


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les classes qui jugent la prose OU le code sans que la porte les connaisse.

    Les deux populations sont confrontees a la MEME declaration, et chaque suspect dit laquelle des
    deux l a trouve : le remede n est pas le meme, une classe de prose etant engagee par `docs/` et
    une classe de code par `src/`.
    """
    connues = declarees(racine)
    return sorted(
        [
            f"{c}  lit de la prose sans etre declaree dans batterie.GARDES_JAVA"
            for c in lisent_la_prose(racine) - connues
        ]
        + [
            f"{c}  juge le code sans etre declaree dans batterie.GARDES_JAVA"
            for c in jugent_le_code(racine) - connues
        ]
    )


def _auto_test() -> int:
    """Les DEUX sens, et le bord ou la detection crierait sur du juste."""
    import tempfile

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-java-") as bac:
        faux = pathlib.Path(bac)
        d = faux / "src" / "test" / "java" / "fr"
        d.mkdir(parents=True)
        (d / "LitLaProseTest.java").write_text(
            'class X { Path p = Path.of("dev-docs", "recette", "x.md"); }\n', encoding="utf-8"
        )
        (d / "LitDuJavaTest.java").write_text(
            'class Y { Path p = Path.of("src/main/java"); }\n', encoding="utf-8"
        )
        (d / "EnParleTest.java").write_text(
            '// on cite "dev-docs/x.md" en commentaire, on ne le lit pas\nclass Z {}\n',
            encoding="utf-8",
        )
        (d / "UnHelper.java").write_text(
            'class H { Path p = Path.of("dev-docs", "y.md"); }\n', encoding="utf-8"
        )
        vus = lisent_la_prose(faux)
        for attendu, nom, libelle in (
            (True, "LitLaProseTest", "une classe qui construit un chemin vers la prose est vue"),
            (False, "LitDuJavaTest", "une classe qui lit du JAVA n'est pas vue"),
            (False, "EnParleTest", "une mention en commentaire ne compte pas"),
            (False, "UnHelper", "un helper, que surefire ne lance pas, n'est pas vu"),
        ):
            if (nom in vus) is attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : vus={sorted(vus)}")
                echecs += 1
        # ⟨la population du CODE, ses deux signaux et ses deux bords (#5884)⟩ Le paquet des
        # invariants est la borne : hors de lui, « construit un chemin vers `src/` » attrape des tests
        # d IHM qui citent une source sans juger la structure - 37 classes au lieu de treize, mesure
        # du 2026-10-05.
        paquet = faux / "src" / "test" / "java" / "fr" / PAQUET_DES_INVARIANTS
        paquet.mkdir(parents=True)
        (paquet / "JugeLeCodeTest.java").write_text(
            'class A { Path p = Path.of("src", "main", "java"); }\n', encoding="utf-8"
        )
        (paquet / "ParArchUnitTest.java").write_text(
            'class B { void x() { new ClassFileImporter().importPackages("fr"); } }\n',
            encoding="utf-8",
        )
        (paquet / "LitLePomTest.java").write_text(
            'class C { Path p = Path.of("pom.xml"); }\n', encoding="utf-8"
        )
        (paquet / "NeLitRienTest.java").write_text(
            "// elle lit des PROPRIETES SYSTEME que le pom calibre, et aucun fichier\n"
            'class D { void x() { System.getProperty("testfx.butoir"); } }\n',
            encoding="utf-8",
        )
        codes = jugent_le_code(faux)
        for attendu, nom, libelle in (
            (True, "JugeLeCodeTest", "un chemin construit vers `src/` est vu, dans le paquet"),
            (True, "ParArchUnitTest", "ArchUnit est vu, qui ne construit AUCUN chemin"),
            (True, "LitLePomTest", "le `pom.xml` compte, deux classes n en dependant que par lui"),
            # ⟨LA LIMITE NOMMEE⟩ Une classe qui ne lit RIEN echappe aux deux signaux. Le cas l EXIGE
            # plutot que de la laisser passer en silence : le trou est connu, borne et ecrit.
            (False, "NeLitRienTest", "une classe qui ne lit rien echappe aux deux signaux"),
            # ⟨LA BORNE⟩ Hors du paquet, le meme chemin ne compte pas. Sans ce cas, elargir la
            # population ferait entrer vingt-quatre tests d IHM dans la porte.
            (False, "LitDuJavaTest", "hors du paquet, un chemin vers `src/` ne compte PAS"),
        ):
            if (nom in codes) is attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : codes={sorted(codes)}")
                echecs += 1
    print("\n9 cas de détection et de bord, sur les deux populations.")
    return 1 if echecs else 0


CONTRAT = {
    "geste": "classe de test qui juge la prose ou le CODE sans que la porte la connaisse",
    "population": "les classes *Test.java qui construisent un chemin vers de la prose, et celles du paquet architecture qui jugent le code",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/methode/gardes-java-declares.py --auto-test",
    "decision": "ADR 5373",
    "chemins": """
src/test/java/**
scripts/batterie.py
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(
        rapporte(
            ADR,
            "classe Java qui juge la prose ou le code sans etre declaree",
            suspects(),
            # ⟨`lus` compte LES DEUX populations⟩ Il n en comptait qu une, et la seconde a double la
            # taille du corpus : un `lus` qui sous-estime cache l elargissement aussi surement qu un
            # `lus` qui depasse annonce un faux vert (ADR 5007). L union, parce qu une classe peut
            # juger la prose ET le code - `DocumentationAJourTest` et `CorrespondanceRecetteTest` sont
            # dans les deux, et les additionner les compterait deux fois.
            lus=len(lisent_la_prose() | jugent_le_code()),
        )
    )
