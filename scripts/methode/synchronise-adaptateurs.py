#!/usr/bin/env python3
"""Copie les competences canoniques de .agents/skills/ vers les dossiers propres a chaque agent.

Le fonds vit dans `.agents/skills/`, hors de tout dossier de marque : aucun contributeur n'est
tenu d'utiliser un agent particulier. `.claude/skills/` n'est qu'un ADAPTATEUR engendre.

On copie plutot qu'on ne lie : sous Windows, sans `core.symlinks`, git ecrit un fichier texte
contenant le chemin, et la decouverte casse en silence.

Le generateur regarde dans les DEUX sens : ce qui manque a la copie, et ce qu elle porte en trop.
Un dossier de `.claude/skills/` qui n a plus de source est un ORPHELIN, que tout renommage de
competence laisse derriere lui. En mode ecriture il est supprime et NOMME ; en `--verifie` il fait
rougir. Une copie n a pas de valeur propre, et `git` garde ce qui est efface.

    --verifie   : ne rien ecrire, sortir 1 si un adaptateur est absent, perime ou orphelin.
    --auto-test : eprouver le garde lui-meme sur une copie jetable, et sortir 1 s il reste vert
                  la ou il devrait rougir. Un garde vert n est pas un garde verifie.
"""

import filecmp
import pathlib
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande

SOURCE = RACINE / ".agents" / "skills"
CIBLES = [RACINE / ".claude" / "skills"]
VERIFIE = "--verifie" in sys.argv


def auto_test() -> int:
    """Monte un TEMOIN vert et des etats casses, et exige le bon verdict sur chacun.

    Le temoin passe en premier et compte autant que les rouges : un garde qui rougirait sur TOUT
    rougirait aussi sur les etats casses, et ses rouges ne diraient rien. Il manquait ici, et son
    absence s est vue en ajoutant le cas de la copie orpheline (#4593).
    """
    script = pathlib.Path(__file__).resolve()

    def copie_orpheline(r: pathlib.Path) -> None:
        """Une copie qui n a plus de source : ce que laisse tout renommage de competence."""
        orphelin = r / ".claude" / "skills" / "competence-disparue"
        orphelin.mkdir(parents=True)
        (orphelin / "SKILL.md").write_text(
            "---\nname: competence-disparue\n---\n", encoding="utf-8"
        )

    cas = [
        (
            "adaptateur absent",
            lambda r: shutil.rmtree(
                r
                / ".claude"
                / "skills"
                / next(p.parent.name for p in sorted((r / ".agents" / "skills").glob("*/SKILL.md")))
            ),
        ),
        (
            "adaptateur perime",
            lambda r: (
                next((r / ".claude" / "skills").glob("*/SKILL.md"))
                .open("a", encoding="utf-8")
                .write("derive\n")
            ),
        ),
        ("source vide", lambda r: (r / ".agents" / "skills").rename(r / ".agents" / "skills-off")),
        ("copie orpheline", copie_orpheline),
    ]
    echecs = []

    def monte(tmp: str) -> pathlib.Path:
        copie = pathlib.Path(tmp) / "depot"
        for d in (".agents", ".claude", "scripts"):
            if (RACINE / d).exists():
                shutil.copytree(RACINE / d, copie / d, symlinks=True)
        subprocess.run(
            [sys.executable, str(copie / script.relative_to(RACINE))],
            capture_output=True,
            check=False,
        )
        return copie

    with tempfile.TemporaryDirectory() as tmp:
        temoin = subprocess.run(
            [sys.executable, str(monte(tmp) / script.relative_to(RACINE)), "--verifie"],
            capture_output=True,
            check=False,
        ).returncode
        print(
            f"  {'temoin, arbre sain':20s} -> {'vert' if temoin == 0 else f'ROUGE (code {temoin})'}"
        )
        if temoin != 0:
            echecs.append("le temoin rougit, donc les rouges qui suivent ne prouvent rien")
    for nom, casser in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = pathlib.Path(tmp) / "depot"
            for d in (".agents", ".claude", "scripts"):
                if (RACINE / d).exists():
                    shutil.copytree(RACINE / d, copie / d, symlinks=True)
            subprocess.run(
                [sys.executable, str(copie / script.relative_to(RACINE))],
                capture_output=True,
                check=False,
            )
            casser(copie)
            code = subprocess.run(
                [sys.executable, str(copie / script.relative_to(RACINE)), "--verifie"],
                capture_output=True,
                check=False,
            ).returncode
            etat = "rouge" if code == 1 else f"VERT (code {code})"
            print(f"  {nom:20s} -> {etat}")
            if code != 1:
                echecs.append(nom)
    if echecs:
        print("\nLe garde reste vert sur : " + ", ".join(echecs), file=sys.stderr)
        print("Il ne garde donc pas ce qu il pretend garder.", file=sys.stderr)
        return 1
    print(f"Auto-test concluant : vert sur l arbre sain, rouge sur les {len(cas)} etats casses.")
    return 0


# Remontee AVANT le point d entree (issue #4788) : la neutralisation du harnais de mutation
# s insere juste avant `if __name__`, et une fonction definie apres lui echapperait.
def competences(d: pathlib.Path):
    if not d.is_dir():
        return {}
    return {p.parent.name: p for p in sorted(d.glob("*/SKILL.md"))}


CONTRAT = {
    "geste": "copie de competence perimee ou orpheline",
    "population": "les competences de .agents/skills, et leur copie de .claude/skills",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/synchronise-adaptateurs.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())

    nos = competences(SOURCE)
    if not nos:
        print(f"aucune competence sous {SOURCE.relative_to(RACINE)}", file=sys.stderr)
        sys.exit(1)

    ecarts = []
    retires = []
    for cible in CIBLES:
        for nom, src in nos.items():
            dst = cible / nom / "SKILL.md"
            if dst.exists() and filecmp.cmp(src, dst, shallow=False):
                continue
            ecarts.append(f"{dst.relative_to(RACINE)} {'absent' if not dst.exists() else 'perime'}")
            if not VERIFIE:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)

        # L AUTRE SENS. Boucler sur la seule source repond a « tout ce qui doit etre copie l est-il ? »
        # et jamais a « ce qui est la doit-il y etre ? ». Un renommage de competence laissait donc sa
        # copie derriere lui, et ce garde annoncait « adaptateurs a jour » avec un dossier de plus.
        # Mesure de #4565 : cinq renommages, cinq orphelins survivants, aucun mot. Article A3, ADR 2748.
        if cible.is_dir():
            for mort in sorted(d for d in cible.iterdir() if d.is_dir() and d.name not in nos):
                ecarts.append(
                    f"{mort.relative_to(RACINE)} orphelin : aucune source sous "
                    f"{SOURCE.relative_to(RACINE)}"
                )
                if not VERIFIE:
                    shutil.rmtree(mort)
                    retires.append(str(mort.relative_to(RACINE)))

    if VERIFIE:
        if ecarts:
            print("Adaptateurs desynchronises :", file=sys.stderr)
            for e in ecarts:
                print(f"  {e}", file=sys.stderr)
            print(
                "\nRelancer : python3 scripts/methode/synchronise-adaptateurs.py", file=sys.stderr
            )
            sys.exit(1)
        print(f"{len(nos)} competence(s), adaptateurs a jour.")
    else:
        for mort in retires:
            print(f"  retire : {mort} (orphelin, plus aucune source)")
        print(
            f"{len(nos)} competence(s) synchronisee(s)"
            + (f", {len(ecarts)} ecart(s) corrige(s)" if ecarts else ", rien a faire")
        )
