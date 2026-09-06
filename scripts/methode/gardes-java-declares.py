#!/usr/bin/env python3
"""Une classe de test qui juge la PROSE est declaree, ou la porte ne la lancera jamais (#5373).

    python3 scripts/methode/gardes-java-declares.py
    python3 scripts/methode/gardes-java-declares.py --auto-test

## Ce que ce garde ferme

`scripts/batterie.py` repond a « quels controles ce diff engage-t-il ? », et sa population s arretait
au **Python**. Or ce depot teste sa documentation comme du code : des classes Java lisent des `.md`
et refusent quand ils derivent.

La demande #5356 a rougi sur `build` pour cette raison - un `enforced_by` qui portait un argument, la
ou l invariant cherche un fichier - et la porte ne pouvait pas le voir. Elle coute pourtant 2,4 s
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

# Un chemin CONSTRUIT, par opposition a un chemin cite.
CONSTRUIT = re.compile(r"(?:Path\.of|Paths\.get|new File)\s*\(([^;]{0,200})")
PROSE = re.compile(
    r'"(?:dev-)?docs?["/]|"brief["/]|mkdocs|'
    r'"(?:README|CONTRIBUTING|TESTING|REMERCIEMENTS|CLAUDE|AGENTS)\.md"|'
    r'"\.github/workflows"'
)


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
    """Les classes qui jugent la prose sans que la porte les connaisse."""
    return sorted(
        f"{c}  lit de la prose sans etre declaree dans batterie.GARDES_JAVA"
        for c in lisent_la_prose(racine) - declarees(racine)
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
    print("\n4 cas de détection et de bord.")
    return 1 if echecs else 0


CONTRAT = {
    "geste": "classe de test qui juge la prose sans que la porte de la batterie la connaisse",
    "population": "les classes *Test.java qui CONSTRUISENT un chemin vers de la prose",
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
            "classe Java qui juge la prose sans etre declaree",
            suspects(),
            lus=len(lisent_la_prose()),
        )
    )
