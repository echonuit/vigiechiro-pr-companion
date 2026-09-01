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

**Les deux seuils.** La zone de test est a 40 : 32 `NcssCount`, 5 `GodClass`,
2 `ExcessiveParameterList`, 1 `CyclomaticComplexity`. La production est a ZERO, et l ADR 4682 dit
que cela se lit comme un REFUS et non comme une marge a resserrer.

Pose a 62, le cliquet a d abord ete unique sur les deux zones. Le chantier #4656 l a fait descendre
a 40 en retirant vingt-trois methodes mortes, puis #4682 l a separe : un compteur unique laissait
une regression d un cote se payer par un gain de l autre.

**Ce qu il ne lit pas.** Le rapport de PMD, et rien d autre. Si `target/pmd.xml` manque, il REFUSE
au lieu de rendre zero : un garde qui conclut sur ce qu il n a pas lu est vert au moment ou il sert,
et c est le defaut de #4544 sous une autre forme.
"""

import pathlib
import sys
import xml.etree.ElementTree as ET

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import PRODUCTION, RACINE_DEPOT, TESTS, rapporte

# DEUX cliquets, un par zone, et surtout pas un seul sur les deux (#4682).
#
# Un compteur unique laisserait une regression d un cote se payer par un gain de l autre : total
# stable, verdict vert, et une methode morte reapparue en production compensee par un `NcssCount`
# retire d un test. C est le defaut que l ADR 4587 refuse pour les planchers de renvois, et il vaut
# ici mot pour mot. Le chantier #4656 a ramene la production a ZERO violation : c est precisement
# l acquis qu un compteur unique ne garde pas.
#
# Les deux populations sont donc DISJOINTES, chacune avec son ADR et son seuil, et le script rend le
# PIRE des deux codes.
ADR_TEST = "4617"
ADR_PRODUCTION = "4682"
RAPPORT = RACINE_DEPOT / "target" / "pmd.xml"
# La seule regle que la zone de test n a pas a tenir, et la mesure qui le justifie est dans le
# docstring. Ailleurs qu en test, elle compte comme les autres.
TOLEREES_EN_TEST = {"AvoidDuplicateLiterals"}


# Les chemins RELATIFS, que `fichiers()` ancre lui-meme : c est ce qui rend une zone videable.
# Ancres ici, ils auraient fige la racine, et aucun cas n aurait pu pointer le garde vers un arbre
# vide pour voir son refus partir (issue #5054).
ZONES = {"production": PRODUCTION, "test": TESTS}


def fichiers(racine: pathlib.Path | None = None, zone: str | None = None) -> list[pathlib.Path]:
    """Les fichiers Java que PMD a ANALYSES dans la zone, pour que `lus` les compte (issue #5007).

    Le compte vient de l ARBRE VISE et non du rapport, et cette distinction est tout l interet du
    champ ici. PMD ne liste que les fichiers FAUTIFS : une zone irreprochable n a aucune entree
    dans `pmd.xml`. Mesure du 2026-09-01 : 425 balises `file` et 1 493 violations, toutes en zone
    de test ; la production n en porte aucune, et son cliquet vaut zero parce qu elle est propre.

    Compter les entrees du rapport ferait donc REFUSER la production pour etre irreprochable,
    puisque `lus=0` refuse. Ce que ce garde doit pouvoir distinguer, c est « zero violation » de
    « PMD n a pas tourne sur cette zone », et seul le compte de l arbre le dit.
    """
    base = racine or RACINE_DEPOT
    choisies = [ZONES[zone]] if zone else list(ZONES.values())
    arbres = [base / c for c in choisies]
    return sorted(f for a in arbres if a.is_dir() for f in a.rglob("*.java"))


def _zone(chemin: str) -> str:
    return "test" if "/src/test/" in chemin.replace("\\", "/") else "production"


def suspects(rapport: pathlib.Path | None = None, zone: str | None = None) -> list[str]:
    """Les violations retenues, la zone de chacune, de la plus frequente a la plus rare.

    `rapport` n existe que pour l auto-test, qui a besoin d un corpus qu il maitrise : mesurer sur
    le depot ne separerait pas ce que le garde compte de ce qu il epargne.

    `zone` borne le compte a « production » ou « test ». Sans elle, les deux sont rendues ensemble,
    ce que l auto-test utilise pour montrer la compensation qu un compteur unique laisse passer.
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
        zone_du_fichier = _zone(chemin)
        for violation in fichier.findall(balise("violation")):
            regle = violation.get("rule")
            if zone_du_fichier == "test" and regle in TOLEREES_EN_TEST:
                continue
            if zone is not None and zone_du_fichier != zone:
                continue
            retenus.append(
                (regle, zone_du_fichier, pathlib.Path(chemin).name, violation.get("beginline"))
            )
    retenus.sort()
    return [f"{regle}  {nom}:{ligne}  ({z})" for regle, z, nom, ligne in retenus]


if __name__ == "__main__":
    # Les deux cliquets, l un apres l autre. Le code de sortie est le PIRE des deux : une regression
    # dans une zone doit faire rougir, meme si l autre a gagne. C est la disjonction en pratique.
    codes = [
        rapporte(
            ADR_PRODUCTION,
            "violations du portail en production",
            suspects(zone="production"),
            apercu=12,
            lus=len(fichiers(zone="production")),
        ),
        rapporte(
            ADR_TEST,
            "violations du portail en zone de test",
            suspects(zone="test"),
            apercu=12,
            lus=len(fichiers(zone="test")),
        ),
    ]
    sys.exit(max(codes))
