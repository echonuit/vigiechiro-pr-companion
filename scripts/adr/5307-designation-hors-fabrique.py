#!/usr/bin/env python3
"""Cliquet a zero sur les constructions du selecteur natif hors de la fabrique (ADR 5307).

Le choix du dispositif de designation d un fichier etait ecrit DOUZE fois en dur, dans douze
controleurs et actions de six paquets. Il n existait donc aucun endroit ou le changer, et le
changement `selecteur-de-repli` en aurait ecrit douze de plus.

`SelecteurSelonLaPreference` est desormais le seul endroit ou ce choix se construit. Ce garde tient
cette unicite.

Le lieu a DEMENAGE une fois, en #5310, et le garde l a suivi SANS s elargir : `Selecteurs` resolvait
le dispositif dans `pour()`, donc a la construction de l ecran, ce qui figeait le reglage jusqu au
redemarrage. La resolution est passee au moment de designer, et l endroit permis avec elle. Il en
reste UN, ce que l ADR demande : autoriser les deux aurait transforme le garde en liste, et une liste
s allonge.

## Pourquoi un cliquet plutot que la discipline

Manquer un endroit NE CASSE RIEN. L ecran oublie ouvre le dialogue du systeme, qui est le
comportement par defaut : il a l air juste. Le defaut ne se verrait qu en filmant ce parcours-la, ou
en le recevant du terrain - trop tard, et loin de sa cause.

C est la forme de dette qui ne se voit pas en relecture, et que ce depot tient par un compte.

## Le cliquet est a ZERO, et le restera

La plupart des cliquets de ce depot bornent une dette qu on tolere en attendant de la resorber.
Celui-ci compte une population qui doit rester VIDE : il n y a pas de raison legitime de construire
le selecteur natif ailleurs. Une construction de plus est une regression, jamais une tolerance.

## La population, et sa limite declaree

`src/main/java`, commentaires retires, la fabrique exclue. Une construction obtenue par REFLEXION
echapperait au motif ; il n y en a aucune dans ce depot. Une fabrique tierce qui recopierait la ligne
sous un autre nom y echapperait aussi, mais elle se verrait en relecture : elle demanderait d ecrire
a nouveau ce que `SelecteurSelonLaPreference` ecrit deja.

Le garde lit la PRODUCTION seule. Un test qui construit le selecteur natif le fait pour eprouver le
dispositif lui-meme, ce qui est son role.

Usage :
    python3 scripts/adr/5307-designation-hors-fabrique.py
    python3 scripts/adr/5307-designation-hors-fabrique.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import (
    PRODUCTION,
    PRODUCTION_ANCREE,
    cas_d_auto_test,
    rapporte,
    sans_commentaires_java,
    sort_si_contrat_demande,
)

CONSTRUCTION = re.compile(r"new\s+SelecteurFichierJavaFx\s*\(")

# Le lieu du choix, seul autorise a construire. Nomme par son chemin et non par son nom de classe :
# un fichier homonyme ailleurs ne doit pas heriter de la permission.
FABRIQUE = PRODUCTION / "fr/univ_amu/iut/commun/view/SelecteurSelonLaPreference.java"


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les sources de production, la fabrique exclue."""
    base = racine if racine is not None else PRODUCTION_ANCREE
    permise = (
        (RACINE / FABRIQUE).resolve() if racine is None else (racine / FABRIQUE.name).resolve()
    )
    return sorted(p for p in base.rglob("*.java") if p.resolve() != permise)


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les sites qui construisent le selecteur natif hors de la fabrique."""
    trouves = []
    for fichier in fichiers(racine):
        source = sans_commentaires_java(fichier.read_text(encoding="utf-8", errors="ignore"))
        for numero, ligne in enumerate(source.splitlines(), start=1):
            if CONSTRUCTION.search(ligne):
                relatif = fichier.relative_to(racine if racine is not None else RACINE)
                trouves.append(f"{relatif}:{numero}")
    return trouves


def lus(racine: pathlib.Path | None = None) -> int:
    """Ce que le garde a BALAYE, que `suspects` ne dit pas quand il ne retient rien."""
    return len(fichiers(racine))


def _auto_test() -> int:
    """Le temoin, planté puis retiré, et le meme en commentaire pour qu il ne compte pas."""
    import tempfile

    verifie, echecs = cas_d_auto_test()

    with tempfile.TemporaryDirectory() as bac:
        base = pathlib.Path(bac)

        # 1. Une population VIDE refuse, et c est ce que `rapporte` garantit deja : on verifie ici que
        #    le garde balaie quelque chose, sans quoi son vert ne vaudrait rien.
        (base / "Innocent.java").write_text(
            "class Innocent { void f() { Selecteurs.pour(() -> null); } }\n", encoding="utf-8"
        )
        verifie("un fichier qui passe par la fabrique n est pas suspect", suspects(base), [])
        verifie("et il est bien LU", lus(base), 1)

        # 2. Le temoin plante : une treizieme construction, ailleurs.
        (base / "Coupable.java").write_text(
            "class Coupable {\n"
            "    private final X s = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(f));\n"
            "}\n",
            encoding="utf-8",
        )
        verifie("une construction hors fabrique est vue", suspects(base), ["Coupable.java:2"])

        # 3. La MEME en commentaire ne compte pas. Sans ce cas, un garde qui compterait la prose
        #    rougirait sur la javadoc de la fabrique elle-meme, qui cite la ligne qu elle remplace.
        (base / "Coupable.java").write_text(
            "class Coupable {\n"
            "    // new SelecteurFichierJavaFx(f) : ce que la fabrique fait desormais\n"
            "    private final X s = Selecteurs.pour(f);\n"
            "}\n",
            encoding="utf-8",
        )
        verifie("la meme ligne EN COMMENTAIRE ne compte pas", suspects(base), [])

        # 4. La fabrique elle-meme est exclue, alors qu elle porte la construction.
        (base / FABRIQUE.name).write_text(
            "class Selecteurs { static X pour(Y f) { return new SelecteurFichierJavaFx(f); } }\n",
            encoding="utf-8",
        )
        verifie("la fabrique a le droit de construire", suspects(base), [])

    return echecs()


CONTRAT = {
    "geste": "construction du selecteur de fichiers natif hors de la fabrique",
    "population": "les fichiers .java de src/main/java, commentaires retires, "
    "`commun/view/Selecteurs.java` exclu. Une construction par reflexion echapperait au motif ; il "
    "n y en a aucune dans ce depot",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/5307-designation-hors-fabrique.py --auto-test",
    "decision": "ADR 5307",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    raise SystemExit(
        rapporte(
            "5307",
            "constructions du selecteur natif hors de la fabrique",
            suspects(),
            apercu=12,
            lus=lus(),
        )
    )
