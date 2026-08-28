#!/usr/bin/env python3
"""Cliquet sur ce que le portail qualite trouve, zone de test comprise (#4617).

**Ce qui a ouvert cette ADR.** Une methode privee jamais appelee a vecu dans
`SonsValidationArchiveViewTest`, a soixante-quinze lignes de la version correcte du meme helper,
et rien ne l a vue (#4554). Deux trous l expliquent, et il ne faut pas les confondre :

- le JEU de regles ne portait que sept regles de conception, aucune `Unused*`. Le job
  `analyser-ecj` compile pourtant les tests, mais ne cherche que les divergences entre
  compilateurs. Le code mort n etait donc couvert NULLE PART, production comprise ;
- l ASSIETTE laissait dehors `src/test/java`, soit 143 052 lignes contre 127 814, la moitie
  la plus grande du Java du depot.

Corriger le second seul aurait donne le sentiment d avoir ferme le trou sans rien fermer.

**Pourquoi les litteraux dupliques ne comptent pas dans la zone de test.** Repeter un litteral est
ce qu un test DOIT faire : le meme identifiant de table dans vingt cas, un nom d espece, une date.
Mesure : `AvoidDuplicateLiterals` rend 1 366 des 1 428 signalements du depot, tous dans les tests,
et ZERO en production. C est elle, et elle seule, qui rendait l inclusion des tests impraticable.

Ce filtre se fait ICI et non dans le ruleset, parce que PMD ne sait pas l exprimer : il refuse
`<exclude-pattern>` a l interieur d une regle - « Unexpected element » - et une suppression par nom
de fichier en XPath s est revelee couper bien au-dela de sa cible, production comprise. Une seconde
execution du plugin ne marche pas davantage : le goal `check` declenche `pmd:pmd` en fork, et ce
fork lit la configuration GLOBALE, jamais celle de l execution. Les quatre formes ont ete essayees.

**Pourquoi 62.** C est la mesure du jour, pas une cible : 31 `NcssCount`, 23 `UnusedPrivateMethod`
dont 9 en production, 5 `GodClass`, 2 `ExcessiveParameterList`, 1 `CyclomaticComplexity`. Le
cliquet descendra quand ces cas seront traites ; les 23 methodes mortes demandent une lecture par
site et feront leur propre issue, PMD y visant des SURCHARGES precises qu aucun comptage par nom ne
sait distinguer.

**Ce qu il ne lit pas.** Le rapport de PMD, et rien d autre. Si `target/pmd.xml` manque, il REFUSE
au lieu de rendre zero : un garde qui conclut sur ce qu il n a pas lu est vert au moment ou il sert,
et c est le defaut de #4544 sous une autre forme.
"""

import pathlib
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, rapporte  # noqa: E402

ADR = "4617"
RAPPORT = RACINE_DEPOT / "target" / "pmd.xml"
# La seule regle que la zone de test n a pas a tenir, et la mesure qui le justifie est dans le
# docstring. Ailleurs qu en test, elle compte comme les autres.
TOLEREES_EN_TEST = {"AvoidDuplicateLiterals"}


def _zone(chemin: str) -> str:
    return "test" if "/src/test/" in chemin.replace("\\", "/") else "production"


def suspects(rapport: pathlib.Path | None = None) -> list[str]:
    """Les violations retenues, la zone de chacune, de la plus frequente a la plus rare.

    `rapport` n existe que pour l auto-test, qui a besoin d un corpus qu il maitrise : mesurer sur
    le depot ne separerait pas ce que le garde compte de ce qu il epargne.
    """
    source = rapport or RAPPORT
    if not source.exists():
        raise SystemExit(
            f"{source} est absent : PMD n a pas tourne.\n"
            "Ce garde REFUSE plutot que de conclure sur ce qu il n a pas lu.\n"
            "Lancez d abord : ./mvnw -B -o test-compile pmd:pmd"
        )
    arbre = ET.parse(source).getroot()
    espace = arbre.tag.split("}")[0].strip("{") if "}" in arbre.tag else None
    balise = (lambda n: f"{{{espace}}}{n}") if espace else (lambda n: n)

    retenus = []
    for fichier in arbre.findall(f".//{balise('file')}"):
        chemin = fichier.get("name") or ""
        zone = _zone(chemin)
        for violation in fichier.findall(balise("violation")):
            regle = violation.get("rule")
            if zone == "test" and regle in TOLEREES_EN_TEST:
                continue
            retenus.append((regle, zone, pathlib.Path(chemin).name, violation.get("beginline")))
    retenus.sort()
    return [f"{regle}  {nom}:{ligne}  ({zone})" for regle, zone, nom, ligne in retenus]


if __name__ == "__main__":
    sys.exit(rapporte(ADR, "violations du portail, zone de test comprise", suspects(), apercu=12))
