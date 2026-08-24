#!/usr/bin/env python3
"""Copie les competences canoniques de .agents/skills/ vers les dossiers propres a chaque agent.

Le fonds vit dans `.agents/skills/`, hors de tout dossier de marque : aucun contributeur n'est
tenu d'utiliser un agent particulier. `.claude/skills/` n'est qu'un ADAPTATEUR engendre.

On copie plutot qu'on ne lie : sous Windows, sans `core.symlinks`, git ecrit un fichier texte
contenant le chemin, et la decouverte casse en silence.

    --verifie   : ne rien ecrire, sortir 1 si un adaptateur est absent ou perime (garde de CI).
    --auto-test : eprouver le garde lui-meme sur une copie jetable, et sortir 1 s il reste vert
                  la ou il devrait rougir. Un garde vert n est pas un garde verifie.
"""
import filecmp, shutil, subprocess, sys, tempfile, pathlib

RACINE = pathlib.Path(__file__).resolve().parents[2]
SOURCE = RACINE / ".agents" / "skills"
CIBLES = [RACINE / ".claude" / "skills"]
VERIFIE = "--verifie" in sys.argv


def auto_test() -> int:
    """Monte trois etats casses sur une copie, et exige que le garde rougisse sur chacun."""
    script = pathlib.Path(__file__).resolve()
    cas = [
        ("adaptateur absent",  lambda r: shutil.rmtree(r / ".claude" / "skills" / next(
            p.parent.name for p in sorted((r / ".agents" / "skills").glob("*/SKILL.md"))))),
        ("adaptateur perime",  lambda r: next(
            (r / ".claude" / "skills").glob("*/SKILL.md")).open("a", encoding="utf-8").write("derive\n")),
        ("source vide",        lambda r: (r / ".agents" / "skills").rename(r / ".agents" / "skills-off")),
    ]
    echecs = []
    for nom, casser in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = pathlib.Path(tmp) / "depot"
            for d in (".agents", ".claude", "scripts"):
                if (RACINE / d).exists():
                    shutil.copytree(RACINE / d, copie / d, symlinks=True)
            subprocess.run([sys.executable, str(copie / script.relative_to(RACINE))],
                           capture_output=True)
            casser(copie)
            code = subprocess.run([sys.executable, str(copie / script.relative_to(RACINE)), "--verifie"],
                                  capture_output=True).returncode
            etat = "rouge" if code == 1 else f"VERT (code {code})"
            print(f"  {nom:20s} -> {etat}")
            if code != 1:
                echecs.append(nom)
    if echecs:
        print("\nLe garde reste vert sur : " + ", ".join(echecs), file=sys.stderr)
        print("Il ne garde donc pas ce qu il pretend garder.", file=sys.stderr)
        return 1
    print("Auto-test concluant : le garde rougit sur les trois etats casses.")
    return 0


if "--auto-test" in sys.argv:
    sys.exit(auto_test())

def competences(d: pathlib.Path):
    if not d.is_dir():
        return {}
    return {p.parent.name: p for p in sorted(d.glob("*/SKILL.md"))}

nos = competences(SOURCE)
if not nos:
    print(f"aucune competence sous {SOURCE.relative_to(RACINE)}", file=sys.stderr)
    sys.exit(1)

ecarts = []
for cible in CIBLES:
    for nom, src in nos.items():
        dst = cible / nom / "SKILL.md"
        if dst.exists() and filecmp.cmp(src, dst, shallow=False):
            continue
        ecarts.append(f"{dst.relative_to(RACINE)} {'absent' if not dst.exists() else 'perime'}")
        if not VERIFIE:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)

if VERIFIE:
    if ecarts:
        print("Adaptateurs desynchronises :", file=sys.stderr)
        for e in ecarts:
            print(f"  {e}", file=sys.stderr)
        print("\nRelancer : python3 scripts/methode/synchronise-adaptateurs.py", file=sys.stderr)
        sys.exit(1)
    print(f"{len(nos)} competence(s), adaptateurs a jour.")
else:
    print(f"{len(nos)} competence(s) synchronisee(s)" + (f", {len(ecarts)} ecart(s) corrige(s)" if ecarts else ", rien a faire"))
