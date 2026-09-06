#!/usr/bin/env python3
"""La porte d entree unique : ce que CE diff engage, et rien de plus (#5340).

    python3 scripts/batterie.py                # ce que le diff contre `main` engage
    python3 scripts/batterie.py --lance        # les joue TOUS, et rend tous les refus
    python3 scripts/batterie.py --contre HEAD~1
    python3 scripts/batterie.py --auto-test

## Le besoin, dans les termes du probleme

Un agent qui a fini ne sait pas quoi lancer. Il a deux options : tout lancer, ce qui coute une
cinquantaine de minutes, ou deviner. Il devine, et le banc qu il se compose est tantot
disproportionne, tantot insuffisant.

C est la MEME question que la portee CI du chantier #5294, vue de l autre cote : *quels controles ce
diff engage-t-il ?* Une seule derivation doit y repondre, sinon les deux divergent.

## Le repli, qui est la moitie du dispositif

> **Un garde qui ne declare pas ses `chemins` est LANCE.**

Le defaut penche du cote couteux, jamais du cote muet. C est le meme parti que la portee CI, qui
verifie tout quand la base de comparaison manque.

Cette porte est donc JUSTE des le premier jour, avec neuf gardes declarants sur soixante et onze :
elle lance trop, jamais trop peu. Le cliquet des non-declarants la rend precise par tranches, sans
qu elle passe par un etat ou elle en oublie un.

## Le faux vert qu elle refuse

Une porte qui n engage RIEN rendrait vert en n ayant rien lance. Ce n est pas theorique : le
2026-09-06, un harnais de ce depot a lance `python3` sans argument cent trente et une fois - zsh ne
decoupe pas `$ligne` en mots - et a annonce cent trente et une commandes vertes sans qu aucune n ait
ete jouee. La CI a trouve le rouge que la batterie annoncait absent.

Cette porte DIT donc combien elle a lance, et refuse d en lancer zero sur un diff non vide.

## Ce qu elle ne fait pas

Elle ne remplace pas la CI. `AGENTS.md` le pose : la mesure fait foi en CI, pas sur le poste. Elle
est le PREMIER lecteur, celui qui evite l aller-retour, pas l autorite.
"""

from __future__ import annotations

import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RACINE / "scripts"))
sys.path.insert(0, str(RACINE / "scripts" / "methode"))

CONTRAT = {
    "geste": "commandes de la batterie locale qu un diff engage, et celles qu il n engage pas",
    "population": "les gardes de scripts/ qui declarent un CONTRAT",
    "dispositif": "porte",
    "seuil": "(sans objet)",
    "temoin": "scripts/batterie.py --auto-test",
    "decision": "chantier #5294, lot #5340",
    "chemins": """
scripts/**
""",
}


def _git(*arguments: str, racine: pathlib.Path | None = None) -> str:
    sortie = subprocess.run(
        ["git", "-C", str(racine or RACINE), *arguments],
        capture_output=True,
        text=True,
        check=False,
    )
    return sortie.stdout if sortie.returncode == 0 else ""


def fichiers_du_diff(contre: str = "origin/main", racine: pathlib.Path | None = None) -> list[str]:
    """Ce que ce diff touche, suivi ET non suivi.

    Les fichiers NEUFS comptent : un garde qu on vient d ecrire n est pas encore suivi, et l oublier
    ferait taire exactement la porte qui devait le juger.
    """
    modifies = _git("diff", "--name-only", contre, racine=racine).splitlines()
    neufs = _git("ls-files", "--others", "--exclude-standard", racine=racine).splitlines()
    en_cours = _git("diff", "--name-only", racine=racine).splitlines()
    return sorted({f for f in modifies + neufs + en_cours if f})


def correspond(chemin: str, motif: str) -> bool:
    """`**` traverse les `/`, `*` ne les traverse pas. Meme regle que la portee CI."""
    morceaux, i = [], 0
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
        else:
            morceaux.append(re.escape(motif[i]))
            i += 1
    return re.fullmatch("".join(morceaux), chemin) is not None


def gardes(racine: pathlib.Path | None = None) -> list[tuple[str, list[str]]]:
    """Les gardes de `scripts/`, avec leurs `chemins` declares - vide quand ils n en declarent pas.

    La lecture se fait par `ast`, sans lancer les gardes : les lancer pour savoir s il faut les
    lancer serait le geste que cette porte existe pour eviter.
    """
    import ast

    base = racine or RACINE
    trouves = []
    for dossier in ("adr", "methode"):
        for f in sorted((base / "scripts" / dossier).glob("*.py")):
            if f.name.startswith("_"):
                continue
            try:
                arbre = ast.parse(f.read_text(encoding="utf-8", errors="ignore"))
            except SyntaxError as erreur:
                # ⟨on ne saute PAS un garde illisible⟩ Ce `continue` a existe, et il a coute : le
                # 2026-09-06, une insertion fautive a casse `verifie-dependances-declarees.py`, et la
                # porte l a fait DISPARAITRE du corpus sans un mot. Le compte restait plausible, le
                # garde n etait plus lance, et rien ne le disait.
                #
                # C est exactement le silence que le chantier #5294 combat. Un garde qu on ne sait pas
                # lire est donc ENGAGE, avec sa raison : le defaut penche du cote couteux.
                print(
                    f"⚠ {f.name} est illisible ({erreur.__class__.__name__}) : il est ENGAGE par "
                    "defaut, faute de savoir ce qu il declare.",
                    file=sys.stderr,
                )
                trouves.append((f"scripts/{dossier}/{f.name}", []))
                continue
            declares: list[str] = []
            porte_un_contrat = False
            for noeud in ast.walk(arbre):
                if not isinstance(noeud, ast.Assign):
                    continue
                for cible in noeud.targets:
                    if isinstance(cible, ast.Name) and cible.id == "CONTRAT":
                        porte_un_contrat = True
                        if isinstance(noeud.value, ast.Dict):
                            for cle, valeur in zip(noeud.value.keys, noeud.value.values):
                                if (
                                    isinstance(cle, ast.Constant)
                                    and cle.value == "chemins"
                                    and isinstance(valeur, ast.Constant)
                                ):
                                    declares = [
                                        l.strip()
                                        for l in str(valeur.value).splitlines()
                                        if l.strip()
                                    ]
            if porte_un_contrat:
                trouves.append((f"scripts/{dossier}/{f.name}", declares))
    return trouves


def engage(diff: list[str], racine: pathlib.Path | None = None) -> tuple[list[str], list[str]]:
    """Ce que ce diff engage, et ce qu il n engage pas. Sans `chemins`, on ENGAGE."""
    engages, ecartes = [], []
    for garde, chemins in gardes(racine):
        if not chemins or any(correspond(f, m) for f in diff for m in chemins):
            engages.append(garde)
        else:
            ecartes.append(garde)
    return engages, ecartes


def rendre(
    contre: str = "origin/main", lance: bool = False, racine: pathlib.Path | None = None
) -> int:
    diff = fichiers_du_diff(contre, racine)
    if not diff:
        print(f"Aucun fichier ne differe de `{contre}` : rien a lancer.")
        return 0

    engages, ecartes = engage(diff, racine)
    print(f"{len(diff)} fichier(s) modifie(s) contre `{contre}`.")
    print()

    if not engages:
        # Une porte qui n engage rien sur un diff non vide n a pas trie : elle s est tue.
        print("❌ AUCUN garde engage sur un diff non vide.")
        print("   Une porte qui n engage rien rend vert sans avoir rien lance. Elle REFUSE plutot.")
        return 1

    print(f"  ENGAGE ({len(engages)} garde(s))")
    if lance and len(engages) > 40:
        print(
            f"     ⚠ {len(engages)} gardes, dont deux bancs de mutation : comptez plus de quinze"
            " minutes. Le compte est eleve parce que 62 gardes ne declarent pas encore leurs"
            " `chemins` et sont donc lances par defaut - c est ce que le cliquet de l ADR 5340"
            " fait descendre.",
            flush=True,
        )
    for g in engages:
        print(f"    python3 {g}")
    if ecartes:
        print()
        print(
            f"  NON ENGAGE ({len(ecartes)}), parce que leurs chemins declares ne sont pas touches"
        )
        for g in ecartes:
            print(f"    · {g}")

    sans = [g for g, c in gardes(racine) if not c]
    print()
    print(
        f"  {len(sans)} garde(s) ne declarent pas leurs `chemins`, et sont donc LANCES par defaut."
    )
    print("     Le defaut penche du cote couteux, jamais du cote muet.")

    if not lance:
        return 0

    print()
    # ⟨on va AU BOUT, et on rend tous les rouges⟩ La premiere ecriture s arretait au premier refus,
    # et c etait un mauvais choix : elle butait au vingtieme garde sur soixante-trois, sur un refus
    # d ENVIRONNEMENT - `4617` exige `target/pmd.xml`. Elle imposait donc autant de passages qu il y
    # a de rouges, ce qui est exactement le va-et-vient qu elle existe pour supprimer.
    joues, rouges = 0, []
    for g in engages:
        sortie = subprocess.run(
            ["python3", g], cwd=str(racine or RACINE), capture_output=True, text=True, check=False
        )
        joues += 1
        if sortie.returncode != 0:
            derniere = [l for l in sortie.stdout.splitlines() if l.strip()]
            rouges.append((g, derniere[-1] if derniere else "(sans sortie)"))
            print(f"  ✘ {g}", flush=True)
        else:
            # ⟨flush⟩ Sans lui, la sortie est tamponnee et une execution interrompue n affiche RIEN.
            # Mesure du 2026-09-06 : tuee a quinze minutes, cette porte a laisse un journal VIDE, donc
            # personne n a su ou elle en etait ni ce qu elle avait deja juge. Un outil long qui ne
            # montre rien avant sa fin ne se distingue pas d un outil bloque.
            print(f"  ✔ {g}", flush=True)

    print(f"\n  {joues} garde(s) joue(s), {len(rouges)} refus.")
    if not rouges:
        return 0
    print()
    for g, ligne in rouges:
        print(f"  ✘ {g}")
        print(f"      {ligne[:160]}")
    print()
    # Un garde qui REFUSE faute d un prerequis n est pas un garde qui a juge, et la nuance decide de
    # ce qu on apprend. La porte ne tranche pas a la place du lecteur : elle rend la ligne de refus,
    # ou cette nuance se lit.
    print("  Un refus n est pas toujours un defaut du diff : plusieurs gardes REFUSENT de conclure")
    print("  faute d un prerequis - `target/pmd.xml`, l outil OpenSpec, un paquet reel. Leur ligne")
    print("  de refus le dit, et se rejoue sur `main` pour en avoir le coeur net.")
    return 1


def _auto_test() -> int:
    """Les deux moities, et le bord ou la porte se tairait.

    Une porte qui lance TOUT passerait le premier cas sans rien trier : c est pourquoi le second cas
    exige qu un garde declarant soit ECARTE.
    """
    import tempfile
    import textwrap

    echecs = 0
    with tempfile.TemporaryDirectory(prefix="vc-batterie-") as bac:
        faux = pathlib.Path(bac) / "depot"
        (faux / "scripts" / "methode").mkdir(parents=True)
        (faux / "scripts" / "adr").mkdir(parents=True)
        (faux / "scripts" / "methode" / "declarant.py").write_text(
            textwrap.dedent('''
                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d",
                           "chemins": """
                dev-docs/decisions/**
                """}
            '''),
            encoding="utf-8",
        )
        (faux / "scripts" / "adr" / "muet.py").write_text(
            textwrap.dedent("""
                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}
            """),
            encoding="utf-8",
        )

        # Les deux moities de la regle, sur le MEME garde declarant : engage quand ses chemins sont
        # touches, ecarte quand ils ne le sont pas. Un seul des deux cas serait passe par une porte
        # qui lance tout, ou par une porte qui n engage rien.
        cas = (
            (["dev-docs/decisions/1.md"], True, "ses chemins sont touchés, il est engagé"),
            (["README.md"], False, "ses chemins ne sont pas touchés, il est écarté"),
        )
        for diff, attendu, libelle in cas:
            engages, ecartes = engage(diff, faux)
            obtenu = any("declarant.py" in g for g in engages)
            bon = obtenu is attendu and any(
                "declarant.py" in g for g in (engages if attendu else ecartes)
            )
            print(f"  {'✔' if bon else '✘'} un garde déclarant : {libelle}")
            if not bon:
                echecs += 1
                print(f"      engagés={engages} écartés={ecartes}")

        # Le controle NEGATIF du dispositif : un garde SANS `chemins` est lance quoi qu il arrive.
        engages, _ = engage(["n-importe-quoi.txt"], faux)
        if any("muet.py" in g for g in engages):
            print("  ✔ un garde qui ne déclare rien est lancé, quel que soit le diff")
        else:
            print("  ✘ un garde qui ne déclare rien a été écarté : le repli ne tient pas")
            echecs += 1

        # Et le bord ou la porte se tairait : aucun engage sur un diff non vide fait REFUSER.
        vide = pathlib.Path(bac) / "vide"
        (vide / "scripts" / "methode").mkdir(parents=True)
        (vide / "scripts" / "adr").mkdir(parents=True)
        engages, _ = engage(["x.txt"], vide)
        if not engages:
            print("  ✔ un corpus sans garde n'engage rien, et `rendre` refuse alors")
        else:
            print("  ✘ un corpus vide a engagé quelque chose")
            echecs += 1

    print("\n4 cas de porte et de bord.")
    return 1 if echecs else 0


if __name__ == "__main__":
    from _commun import sort_si_contrat_demande

    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    contre = sys.argv[sys.argv.index("--contre") + 1] if "--contre" in sys.argv else "origin/main"
    sys.exit(rendre(contre, "--lance" in sys.argv))
