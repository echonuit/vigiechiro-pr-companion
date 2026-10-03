#!/usr/bin/env python3
"""Dit ce que la passe sur la plateforme de test a SAUTE, et pourquoi (#5748, chantier #5643).

    python3 scripts/plateforme-de-test/releve_des_sautes.py [--depuis EPOCH] [DOSSIER]
    python3 scripts/plateforme-de-test/releve_des_sautes.py --auto-test

Un saut par `assumeTrue` sort VERT. Une classe qui saute des son `@BeforeAll` sort meme en « Tests
run: 0 » sur la console. Rien, dans le resume d un job, ne distingue alors une sonde qui a joue d une
sonde qui ne s est pas lancee. Mesure sur #5760 : la cible declaree ignoree, les 29 sondes de
`ContratApiVigieChiroLiveTest` sautaient, et le job restait vert.

Ce releve lit les rapports surefire du run COURANT (`target/surefire-reports/TEST-*.xml`). Leur XML,
lui, compte chaque test d une classe interrompue comme saute, motif compris : le motif est le TEXTE
de l element `<skipped>`, apres « Assumption failed: », et non son attribut `message`, vide.

## Ce qu il refuse

TOUT saut, quel qu en soit le motif (cloture de #5643). Depuis #5772, l etat de depart porte tout
ce que les sondes supposent : sur la plateforme de test, rien ne doit sauter, et une sonde neuve qui
suppose une donnee absente rougit, ce qui force a la declarer. Le verdict ne depend donc d aucun
texte. Les deux diagnostics ci-dessous disent seulement POURQUOI, quand ils le savent :

- un saut dont le motif cite le jeton ou un verrou d ecriture (`vigiechiro.token`, `.write`,
  `.message`, `.participationRebut`, `.participationEssai`) : sur la plateforme de test, ces verrous
  sont ouverts par `CibleLive`, donc un tel saut veut dire que la cible n a pas ete cablee ;
- un rapport dont TOUS les tests sont sautes, quel qu en soit le motif : c est la signature d une
  classe interrompue dans son `@BeforeAll`, et jamais celle d un manque de donnees, qui reste partiel.
  Le motif reconnait un TEXTE ; ce refus-ci reconnait l EFFET, et attrape un verrou dont le message ne
  nommerait pas `vigiechiro.*` (cloture de #5643, signale par une session pair) ;
- aucun rapport lu : un releve qui ne lit rien ne peut pas conclure qu il n y a rien ;
- un rapport anterieur a `--depuis` : `target/` garde les rapports d un run precedent si rien ne les
  efface, et un poste qui le rejoue a la main lirait alors un autre run que le sien. Dans le job,
  `target/` est neuf ; l option sert au poste.

Un saut faute de donnees refuse lui aussi : la donnee se declare dans `etat-de-depart.json` (#5747).

Distinct de `scripts/methode/releve-les-bancs-instables.py`, qui lit les journaux de jobs archives
sur la forge pour classer les rouges : celui-ci lit les rapports XML du run, dans le job.
"""

from __future__ import annotations

import argparse
import collections
import pathlib
import re
import sys
import tempfile
import xml.etree.ElementTree as ET

DOSSIER = pathlib.Path("target/surefire-reports")
PREFIXE = "Assumption failed: "
VERROU = re.compile(r"vigiechiro\.(token|write|message|participationRebut|participationEssai)\b")


def motif(saute: ET.Element) -> str:
    """La premiere ligne du texte de `<skipped>`, sans le nom de l exception ni « Assumption failed »."""
    ligne = (saute.text or saute.get("message") or "").strip().split("\n")[0]
    if PREFIXE in ligne:
        ligne = ligne.split(PREFIXE, 1)[1]
    return ligne.strip() or "(sans motif)"


def releve(dossier: pathlib.Path, depuis: float | None = None) -> tuple[int, list[str]]:
    """Ecrit le releve, et rend (code, lignes). Code 0 : rien a refuser ; 1 : refus."""
    rapports = sorted(dossier.glob("TEST-*.xml"))
    lignes: list[str] = []
    if not rapports:
        return 1, [f"REFUS : aucun rapport `TEST-*.xml` sous {dossier}. Rien lu, rien a conclure."]
    perimes = [r.name for r in rapports if depuis is not None and r.stat().st_mtime < depuis]
    if perimes:
        return 1, [
            f"REFUS : {len(perimes)} rapport(s) anterieur(s) a la passe, donc d un autre run :"
        ] + [f"  {nom}" for nom in perimes]

    tests = 0
    entierement_sautes: list[str] = []
    par_motif: dict[str, list[str]] = collections.defaultdict(list)
    for rapport in rapports:
        cas_du_rapport = list(ET.parse(rapport).getroot().iter("testcase"))
        if cas_du_rapport and all(c.find("skipped") is not None for c in cas_du_rapport):
            entierement_sautes.append(rapport.name)
        for cas in cas_du_rapport:
            tests += 1
            saute = cas.find("skipped")
            if saute is not None:
                classe = cas.get("classname", "?").rsplit(".", 1)[-1]
                par_motif[motif(saute)].append(f"{classe}#{cas.get('name', '?')}")

    sautes = sum(len(c) for c in par_motif.values())
    lignes.append(
        f"Releve des sautes : {len(rapports)} rapport(s), {tests} test(s), {sautes} saute(s)."
    )
    fautifs = 0
    for texte, cas in sorted(par_motif.items(), key=lambda e: (-len(e[1]), e[0])):
        verrou = VERROU.search(texte) is not None
        fautifs += len(cas) if verrou else 0
        lignes.append(f"  {len(cas):>3}  {'[JETON OU VERROU] ' if verrou else ''}{texte}")
        lignes.extend(f"         {c}" for c in cas[:5])
        if len(cas) > 5:
            lignes.append(f"         ... et {len(cas) - 5} autre(s)")
    refus = []
    if sautes:
        refus.append(
            f"REFUS : {sautes} saut(s). Sur la plateforme de test, rien ne doit sauter : declarer la "
            "donnee manquante dans etat-de-depart.json plutot que de la sauter (#5747)."
        )
    if fautifs:
        refus.append(
            f"REFUS : {fautifs} saut(s) dus au jeton ou a un verrou d ecriture. Sur la plateforme de test, "
            "`CibleLive` les ouvre : la cible n a pas ete cablee (#5746)."
        )
    if entierement_sautes:
        refus.append(
            f"REFUS : {len(entierement_sautes)} classe(s) dont TOUS les tests sont sautes, la signature "
            "d une classe interrompue avant ses tests : " + ", ".join(entierement_sautes)
        )
    return (1 if refus else 0), lignes + refus


def auto_test() -> int:
    """Sur des rapports FABRIQUES, dans un dossier temporaire. Sans surefire ni JVM."""
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()

    def rapport(dossier: pathlib.Path, nom: str, *cas: tuple[str, str | None]) -> pathlib.Path:
        corps = "".join(
            f'<testcase name="{n}" classname="fr.x.{nom}">'
            + (
                ""
                if m is None
                else f'<skipped type="org.opentest4j.TestAbortedException"><![CDATA['
                f"org.opentest4j.TestAbortedException: Assumption failed: {m}\n\tat x]]></skipped>"
            )
            + "</testcase>"
            for n, m in cas
        )
        chemin = dossier / f"TEST-fr.x.{nom}.xml"
        chemin.write_text(f'<?xml version="1.0"?><testsuite name="fr.x.{nom}">{corps}</testsuite>')
        return chemin

    with tempfile.TemporaryDirectory() as racine:
        d = pathlib.Path(racine)
        vide = d / "vide"
        vide.mkdir()
        verifie("aucun rapport : refus, et non un vert vide", lambda: releve(vide)[0], 1)

        donnees = d / "donnees"
        donnees.mkdir()
        rapport(
            donnees,
            "Contrat",
            ("a", None),
            ("b", "Aucun site geolocalise sur ce compte"),
            ("c", None),
        )
        code, lignes = releve(donnees)
        verifie("un saut faute de donnees refuse aussi : rien ne doit sauter", lambda: code, 1)
        verifie(
            "le compte dit les tests et les sautes",
            lambda: lignes[0],
            "Releve des sautes : 1 rapport(s), 3 test(s), 1 saute(s).",
        )
        verifie(
            "le motif est le texte, sans l exception ni « Assumption failed »",
            lambda: lignes[1].strip(),
            "1  Aucun site geolocalise sur ce compte",
        )

        jeton = d / "jeton"
        jeton.mkdir()
        rapport(
            jeton, "Contrat", ("a", "Suite ignoree : fournir -Dvigiechiro.token=..."), ("b", None)
        )
        code, lignes = releve(jeton)
        verifie("un saut du au jeton refuse", lambda: code, 1)
        verifie(
            "le refus nomme la cible non cablee",
            lambda: "cible n a pas ete cablee" in lignes[-1],
            True,
        )

        verrou = d / "verrou"
        verrou.mkdir()
        rapport(verrou, "AllerRetour", ("a", "opt-in -Dvigiechiro.write=true"))
        verifie("un saut du a un verrou d ecriture refuse", lambda: releve(verrou)[0], 1)

        voisin = d / "voisin"
        voisin.mkdir()
        rapport(voisin, "Contrat", ("a", "Il faut vigiechiro.writer, pas un verrou"), ("b", None))
        verifie(
            "un mot voisin d un verrou n est pas pris pour un verrou",
            lambda: any("JETON OU VERROU" in ligne for ligne in releve(voisin)[1]),
            False,
        )

        joue = d / "joue"
        joue.mkdir()
        rapport(joue, "Contrat", ("a", None), ("b", None))
        code, lignes = releve(joue)
        verifie("aucun saut : vert", lambda: code, 0)
        verifie(
            "le compte dit les tests joues",
            lambda: lignes,
            ["Releve des sautes : 1 rapport(s), 2 test(s), 0 saute(s)."],
        )

        entiere = d / "entiere"
        entiere.mkdir()
        rapport(
            entiere, "Contrat", ("a", "Aucun site sur ce compte"), ("b", "Aucun site sur ce compte")
        )
        code, lignes = releve(entiere)
        verifie(
            "une classe dont tous les tests sautent refuse, meme pour un motif de donnees",
            lambda: code,
            1,
        )
        verifie("le refus nomme le rapport", lambda: "TEST-fr.x.Contrat.xml" in lignes[-1], True)

        verifie(
            "un rapport anterieur a la passe refuse",
            lambda: releve(
                donnees, depuis=(donnees / "TEST-fr.x.Contrat.xml").stat().st_mtime + 60
            )[0],
            1,
        )
    return echecs()


def main(argv: list[str]) -> int:
    if "--auto-test" in argv[1:]:
        return auto_test()
    parseur = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parseur.add_argument("dossier", nargs="?", type=pathlib.Path, default=DOSSIER)
    parseur.add_argument("--depuis", type=float, help="epoch du lancement de la passe")
    arguments = parseur.parse_args(argv[1:])
    code, lignes = releve(arguments.dossier, arguments.depuis)
    print("\n".join(lignes))
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv))
