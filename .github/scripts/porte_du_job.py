#!/usr/bin/env python3
"""La PORTEE d un job : ce diff l engage-t-il, ou n a-t-il rien a y apprendre (chantier #5294) ?

    python3 .github/scripts/porte_du_job.py outillage-release

Rend `concerne=oui` ou `concerne=non` sur la sortie du pas, ECRIT pourquoi dans le recapitulatif, et
sort toujours 0. Le job qui l appelle tourne donc toujours et conclut : un job absent du
recapitulatif serait indiscernable d un job vert, dans un depot qui n a aucune protection de branche
(ADR 2748), et un atelier qui ne rend aucun verdict fait refuser la fusion (ADR 4571).

## La cle est le nom YAML du job, et pas son libelle

`second-compilateur` porte `name: analyser-ecj`. Deux identifiants pour une meme chose, dont un seul
est stable et machine-lisible. `verifie_portees_de_ci.py` confronte les cles d ici aux cles du YAML.

## Chaque portee nomme SON PROPRE atelier, et les deux scripts du mecanisme

Sans quoi une modification du dispositif ne serait jamais eprouvee par le dispositif : on changerait
la portee d un job, et le job ne tournerait pas pour le verifier. C est la regle que la docstring de
`porte_sur_le_contrat_de_fichiers.py` posait en prose, et que le garde tient desormais.

Le prix en est connu et assume : toute demande qui touche `maven.yml` rallume les quatre jobs qui le
nomment. Le gain vit sur les demandes de documentation, d ADR, de brief et de recette.

## UNE LIGNE PAR CHEMIN

Meme forme, et meme raison, que la chaine `SURVEILLES` du contrat de fichiers : elle est lisible par
un motif, donc gardable de l exterieur. Une liste Python d elements sur une meme ligne rendrait le
garde MUET sans qu il rougisse.

## Ce que ce fichier ne decide pas

Qu une portee nomme les VRAIES dependances d un job reste un jugement, pas une deduction. Le garde
verifie qu aucun chemin ecrit noir sur blanc dans le job n echappe a sa portee ; il ne peut pas
verifier qu on n a rien oublie d implicite.
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from _portee import ajoute, base_de_comparaison, fichiers_modifies, resume, sans_base

MECANISME = """
.github/scripts/porte_du_job.py
.github/scripts/_portee.py
"""

# Les portees, une ligne par chemin. La cle EST la cle YAML du job.
PORTEES: dict[str, str] = {
    # ⟨#5379⟩ Ces deux jobs etaient les derniers sur le `paths:` que le chantier #5294 a interdit.
    # Leurs chemins sont ceux que ce filtre portait : ce qui change ce qu on EMBARQUE, et rien
    # d autre. Ni `src/**` - le code de production ne change aucune dependance - ni la prose.
    "inventaire": """
pom.xml
mvnw
.mvn/**
.github/workflows/securite-dependances.yml
"""
    + MECANISME,
    # `verifie_fraicheur_actions.py` est ici pour une raison qui lui est propre : le job ne tourne
    # POUR DE VRAI que le lundi, par `schedule`. Une etape que seul un `schedule` exerce peut etre
    # fusionnee cassee, donc la demande qui touche ce script doit l engager.
    "fraicheur-des-actions": """
.github/workflows/securite-dependances.yml
.github/scripts/verifie_fraicheur_actions.py
"""
    + MECANISME,
    # `paquet` assemble et eprouve ce qu on EMBALLE. Il ne depend ni de la suite de tests ni de la
    # couverture - il construit avec `-DskipTests` - donc la documentation, les ADR et les
    # competences ne lui apprennent rien. C est le seul job dont la compétence `clore-une-pr` disait
    # deja qu il ne juge pas un `.md`.
    #
    # `mvnw` et `.mvn/**` ne sont pas decoratifs : ils gouvernent la version de Maven qui construit
    # le paquet. `src/test/bats/**` non plus - le harnais bats vit ici, et il fait 80 % du job.
    # `emballage` et `bats` portent la MEME portee, et ce n est pas une commodite : `bats` consomme
    # l app-image que l autre depose. Une portee plus large reveillerait `bats` sans artefact a lire ;
    # une plus etroite laisserait l app-image sans personne pour l eprouver.
    "emballage": """
src/main/**
src/test/bats/**
pom.xml
mvnw
.mvn/**
jpackage/**
.github/scripts/verifie_demarrage_emballage.py
.github/scripts/installer_paquets.py
.github/workflows/maven.yml
"""
    + MECANISME,
    "bats": """
src/main/**
src/test/bats/**
pom.xml
mvnw
.mvn/**
jpackage/**
.github/scripts/verifie_demarrage_emballage.py
.github/scripts/installer_paquets.py
.github/workflows/maven.yml
"""
    + MECANISME,
    # `capturer` rend les apercus PNG des vues hors-ecran. Il depend de ce qui DESSINE - le code de
    # production et les ressources - et de l outillage d images, jamais de la documentation.
    "capturer": """
src/main/**
pom.xml
mvnw
.mvn/**
.github/assets/**
.github/scripts/installer_paquets.py
.github/scripts/verifie_titre_pr.py
.github/workflows/capture-vues.yml
"""
    + MECANISME,
    # `analyser` (CodeQL) surveille `src/**` et NON `src/main/java` : son pas « Compiler » fait
    # `-DskipTests package`, qui compile aussi les tests. CodeQL les analyse donc, et une portee
    # limitee a la production laisserait passer un changement de test sans l analyser.
    "analyser": """
src/**
pom.xml
mvnw
.mvn/**
.github/workflows/codeql.yml
"""
    + MECANISME,
    # Les deux rejeux verifient une PROPRIETE DU CODE : que la suite ne depend ni de son ordre
    # (`ordre-alternatif`, fork unique et ordre inverse) ni du fuseau (`fuseau-alternatif`,
    # America/Cayenne). Quand ni `src/**` ni ce qui gouverne la construction ne bougent, cette
    # propriete rend le meme verdict que sur la base.
    #
    # Ce que le depot accepte alors de ne pas verifier est nomme, et c est etroit : l ordre et le
    # fuseau, sur un arbre Java IDENTIQUE a celui que la base a deja juge. Une modification de `.md`
    # peut faire rougir un test documentaire - `build` reste inconditionnel et le voit - mais elle ne
    # peut pas introduire une dependance a l ordre ou au fuseau, qui sont des proprietes du code.
    #
    # La portee reste VOLONTAIREMENT grossiere, `src/**` et non une liste de classes sensibles :
    # l ADR 3450 a tranche que « toute la suite, et non une liste de classes sensibles - une liste ne
    # voit que ce qu on y a mis et se perime ». On ne cherche pas a raffiner ces deux-la.
    "ordre-alternatif": """
src/**
pom.xml
mvnw
.mvn/**
.github/scripts/partition_de_la_suite.py
.github/workflows/maven.yml
"""
    + MECANISME,
    "fuseau-alternatif": """
src/**
pom.xml
mvnw
.mvn/**
.github/workflows/maven.yml
"""
    + MECANISME,
    # `second-compilateur` recompile les DEUX arbres avec ecj, sans jouer ni tests ni couverture. Il
    # depend donc de ce qui se compile, et de rien d autre. Il porte `name: analyser-ecj` : la cle
    # d une portee est celle du JOB, pas son libelle.
    "second-compilateur": """
src/**
pom.xml
mvnw
.mvn/**
.github/workflows/maven.yml
"""
    + MECANISME,
    # `temoins` porte les deux bancs de mutation : ils neutralisent chaque garde et le relancent,
    # donc leur cout croit avec le corpus. Ils n apprennent rien d une demande qui ne touche aucun
    # garde - c est la meme these que l ADR 5345, portee ici au JOB plutot qu a l etape.
    #
    # C est ce qui rend le lot #5303 sans objet : il proposait un concept neuf, des portees d ETAPE
    # dans un job inconditionnel. Sortir le banc dans son propre job le rend soluble avec ce qui
    # existe deja.
    "temoins": """
scripts/**
.github/scripts/**
.github/assets/**
pyproject.toml
.github/workflows/lint.yml
"""
    + MECANISME,
    "outillage-release": """
.github/release/**
.github/openspec/**
openspec/**
scripts/methode/verifie-specs-valides.py
scripts/methode/verifie-sous-commandes-openspec.py
.github/workflows/lint.yml
"""
    + MECANISME,
}

# Les jobs qui tournent ENTIER a chaque demande, et la raison de chacun. Une liste d exemptions
# NOMMEES, jamais un compte : c est l idiome de `verifie_verdicts_declares.HORS_PORTEE`.
INCONDITIONNELS: dict[str, str] = {
    "build": "la suite et le seuil de couverture ; la documentation de ce depot est testee comme du code, aucune demande n en est independante",
    "lint": "les formateurs et les analyseurs : Spotless, ruff, shellcheck, PMD. Ils lisent tout l arbre, aucune demande n en est independante",
    "methode": "les gardes de methode : la prose, les inventaires, les cliquets, les concordances. Ils lisent les competences, les ADR et les pages, donc presque toute demande les engage",
    "corps": "il lit le corps de la demande, pas l arbre : une portee de chemins n y a aucun sens",
    "titre": "il lit le titre de la demande, pas l arbre",
    "duree-du-portail": "il porte `needs: build`, mesure une serie de la forge et n execute rien du depot",
    "contrat-fichiers": "il porte sa propre porte depuis #3525, `porte_sur_le_contrat_de_fichiers.py`",
    "banc-filme": "ecarte par ecrit au chantier #5294 : il lance les auto-tests de six dispositifs, et le conditionner en sauterait cinq pour gagner une minute",
}


def chemins_surveilles(job: str) -> list[str]:
    """Les chemins que la portee de ce job declare, un par ligne.

    Exposee, et consommee par l auto-test, pour que la mutation qui la vide fasse RATER un cas plutot
    que planter le garde : un rouge pour la mauvaise raison ne prouve rien (ADR 4918).
    """
    return [l.strip() for l in PORTEES.get(job, "").splitlines() if l.strip()]


def correspond(chemin: str, motif: str) -> bool:
    """`**` traverse les `/`, `*` ne les traverse pas, le reste est litteral.

    Ni `fnmatch` (dont le `*` traverse les `/`, donc `src/*` prendrait `src/main/java/A.java`), ni
    `PurePath.full_match` (3.13+, et ces jobs tournent aussi sous le python3 des runners Windows et
    macOS).
    """
    morceaux = []
    i = 0
    while i < len(motif):
        if motif.startswith("**/", i):
            morceaux.append("(?:.*/)?")
            i += 3
        elif motif.startswith("**", i):
            morceaux.append(".*")
            i += 2
        elif motif[i] == "*":
            morceaux.append("[^/]*")
            i += 1
        elif motif[i] == "?":
            morceaux.append("[^/]")
            i += 1
        else:
            morceaux.append(re.escape(motif[i]))
            i += 1
    return re.fullmatch("".join(morceaux), chemin) is not None


def juger(job: str) -> int:
    """Le verdict pour ce job, et ce que l etape en dit."""
    titre = f"Portée · {job}"
    surveilles = chemins_surveilles(job)
    if not surveilles:
        # Jamais `non` par defaut : un job inconnu doit faire ROUGIR l etape, pas la taire.
        print(f"❌ `{job}` ne declare aucune portee dans PORTEES.")
        print("   Ajoutez-la, ou inscrivez le job dans INCONDITIONNELS avec sa raison.")
        return 1

    base = base_de_comparaison()
    if not base:
        return sans_base(titre)

    modifies = fichiers_modifies(base)
    touches = [m for m in modifies if any(correspond(m, s) for s in surveilles)]

    if touches:
        lignes = [
            f"Ce diff touche {len(touches)} des {len(surveilles)} chemins surveillés :",
            "",
        ] + [f"- `{t}`" for t in touches]
    else:
        lignes = [
            f"**Sans objet** : aucun des {len(surveilles)} chemins surveillés n'apparaît dans les",
            f"{len(modifies)} fichiers de ce diff. Le job s'exécute quand même, et le dit.",
            "",
            "Un job absent du récapitulatif se lirait comme un job vert, dans un dépôt qui n'a",
            "aucune protection de branche.",
        ]
    resume(titre, lignes)

    if touches:
        for t in touches:
            print(f"  · {t}")
        ajoute("GITHUB_OUTPUT", "concerne=oui")
    else:
        print(
            f"Aucun des {len(surveilles)} chemins surveillés parmi {len(modifies)} fichiers : sans objet."
        )
        ajoute("GITHUB_OUTPUT", "concerne=non")
    return 0


# (chemin, motif, attendu). L appariement decide si un job TOURNE : s il derape, des portees cessent
# de correspondre en silence, et des jobs ecrivent « sans objet » sans avoir juge. C est le faux vert
# que tout ce dispositif existe pour eviter, et il se joue ici.
CAS_D_APPARIEMENT = (
    ("src/main/java/a/B.java", "src/main/**", True),
    # `**` traverse les `/`, mais la racine du motif ancre : `src/main/**` ne prend pas `src/test`.
    ("src/test/java/a/BTest.java", "src/main/**", False),
    ("pom.xml", "pom.xml", True),
    # Un motif sans `/` ne prend QUE la racine : sinon toute demande touchant un pom de sous-projet
    # rallumerait les jobs Maven.
    ("jpackage/pom.xml", "pom.xml", False),
    # `*` ne traverse PAS les `/` : c est la difference avec `fnmatch`, dont le `*` les traverse.
    ("src/a/B.java", "src/*", False),
    ("src/B.java", "src/*", True),
    (".github/release/x/y.js", ".github/release/**", True),
    ("openspec/config.yaml", "openspec/**", True),
    ("openspec", "openspec/**", False),
)


def _auto_test() -> int:
    echecs = 0
    for chemin, motif, attendu in CAS_D_APPARIEMENT:
        obtenu = correspond(chemin, motif)
        if obtenu == attendu:
            print(f"  ✔ {chemin!r} ~ {motif!r} → {obtenu}")
        else:
            print(f"  ✘ {chemin!r} ~ {motif!r} : attendu {attendu}, obtenu {obtenu}")
            echecs += 1

    # Sous mutation, une fonction qui rend `[]` doit faire RATER ce cas, pas planter le garde : un
    # rouge pour la mauvaise raison ne prouve rien (ADR 4918).
    for cle in PORTEES:
        if chemins_surveilles(cle):
            print(f"  ✔ la portée `{cle}` déclare des chemins")
        else:
            print(f"  ✘ la portée `{cle}` ne déclare aucun chemin")
            echecs += 1

    # Jamais « non » par defaut : un job inconnu ROUGIT. C est le bord ou une faute de frappe dans le
    # YAML aurait autrement neutralise un job en silence.
    if juger("job-qui-n-existe-pas") == 0:
        print("  ✘ un job inconnu de PORTEES ne fait pas rougir")
        echecs += 1
    else:
        print("  ✔ un job inconnu de PORTEES fait rougir")

    total = len(CAS_D_APPARIEMENT) + len(PORTEES) + 1
    print(f"\n{total} cas d'appariement et de bord.")
    return 1 if echecs else 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    if len(sys.argv) != 2:
        print("usage : python3 .github/scripts/porte_du_job.py <cle-du-job>")
        sys.exit(2)
    sys.exit(juger(sys.argv[1]))
