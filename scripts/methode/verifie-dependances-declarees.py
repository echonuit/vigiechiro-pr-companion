#!/usr/bin/env python3
"""Un garde declare ce dont il a besoin, comme `pom.xml` le declare pour Java (issue #5008).

Neuf gardes de CI portent `import yaml`, et **rien ne declare PyYAML**. En CI ils ne marchent que
parce que l'image `ubuntu-latest` le fournit : le job `lint` n'installe rien. En local, six des neuf
plantent nu sur `ModuleNotFoundError`, erreur qui ressemble a un defaut du changement en cours.

L'asymetrie n'a jamais ete argumentee : 36 dependances declarees dans `pom.xml` pour tester le Java,
un fichier pour construire la doc, et rien pour les gardes.

**Ce que ce garde refuse.** Un import hors stdlib, hors module local du depot, qui ne figure dans
aucun fichier de dependances.

**Ce qu'il ne refuse PAS, et pourquoi.** Un import ecrit dans un CORPS DE FONCTION. Il est
paresseux par construction : le chemin peut ne jamais s'executer, et `scripts/graphify/rebuild.py`
en vit, dont `--auto-test` tourne en CI sans que graphify soit installe. Exiger sa declaration
ferait installer un outil de poste sur un runner. La distinction reprend l'idiome de l'ADR 4586,
ou la marge separe deja une declaration d'une fixture.

**La stdlib se derive, elle ne s'enumere pas** : `sys.stdlib_module_names` la donne, et une liste
ecrite a la main vieillirait a chaque version de Python.

Usage :
    python3 scripts/methode/verifie-dependances-declarees.py
    python3 scripts/methode/verifie-dependances-declarees.py --auto-test
"""

import ast
import contextlib
import io
import pathlib
import re
import sys
import tempfile
import tomllib

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from _commun import sort_si_contrat_demande

# Les deux arbres de gardes, plus les fichiers de dependances qui peuvent les couvrir.
ARBRES = ("scripts", ".github/scripts")

# Un seul fichier declare les dependances Python du depot, groupees par usage. `tomllib` est dans la
# stdlib depuis 3.11 : le garde qui exige des declarations n'en introduit donc aucune lui-meme.
DECLARATION = "pyproject.toml"

# Le Python enfoui dans un garde shell : `python3 - "$X" <<'PY' ... PY`.
HEREDOC = re.compile(r"<<'(?P<borne>[A-Z]+)'\n(?P<corps>.*?)\n(?P=borne)\n", re.S)

# Un module local du depot s'importe par son nom de fichier, sans paquet. Cette liste se tient a la
# main, et elle grossit quand un garde en charge un autre : `loupe-5175` emprunte `corpus_resolu` au
# cliquet des contrats pour savoir OU CELUI-CI S'ARRETE, sa population etant les abstentions de
# l'autre. Ecrire une seconde resolution aurait fait diverger les deux (issue #5175).
LOCAUX = {"_commun", "rapport", "verifie_okf", "resserre_cliquets", "verifie_contrats_tiennent"}


def declares(racine: pathlib.Path | None = None) -> set[str]:
    """Les modules que le depot declare, lus dans `pyproject.toml`.

    Deux sources dans le meme fichier. Les DISTRIBUTIONS de chaque groupe de dependances, et la
    correspondance distribution -> modules de `[tool.vigiechiro.modules]`, que le nom seul ne donne
    pas : `PyYAML` repond a `import yaml`. Deviner cette correspondance demanderait d'interroger ce
    qui est INSTALLE, donc de rendre un verdict qui depend de la machine.

    Tous les groupes comptent, sans distinction : un import est declare ou il ne l'est pas. Savoir
    QUEL groupe l'installe est la question du workflow, pas celle de ce garde.
    """
    fichier = (racine or RACINE) / DECLARATION
    if not fichier.exists():
        return set()
    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return set()

    noms: set[str] = set()
    for groupe in (donnees.get("dependency-groups") or {}).values():
        for exigence in groupe:
            if not isinstance(exigence, str):
                continue
            paquet = re.split(r"[=<>!~\[;]", exigence)[0].strip()
            noms.add(paquet.lower().replace("-", "_"))
    fournitures = ((donnees.get("tool") or {}).get("vigiechiro") or {}).get("modules") or {}
    for modules in fournitures.values():
        noms.update(m.strip() for m in modules if isinstance(m, str))
    return noms


def imports_durs(source: str) -> list[str]:
    """Les modules importes HORS corps de fonction. Rend [] si la source ne se parse pas.

    La descente s'arrete aux `def` et `async def`, et a eux seuls : un import en corps de CLASSE
    s'execute a l'import du module, donc il compte. Une source illisible rend une liste vide plutot
    que de faire planter le garde sur un heredoc qui n'etait pas du Python.
    """
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        return []
    trouves: list[str] = []

    def visite(noeud) -> None:
        for enfant in ast.iter_child_nodes(noeud):
            if isinstance(enfant, (ast.FunctionDef, ast.AsyncFunctionDef)):
                continue
            if isinstance(enfant, ast.Import):
                trouves.extend(a.name.split(".")[0] for a in enfant.names)
            elif isinstance(enfant, ast.ImportFrom):
                # `level` non nul est un import RELATIF : il designe le depot, jamais une dependance.
                if enfant.level == 0 and enfant.module:
                    trouves.append(enfant.module.split(".")[0])
            else:
                visite(enfant)

    visite(arbre)
    return trouves


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unites que ce garde LIT : les sources des deux arbres de gardes.

    Un `.sh` compte pour une unite, comme un `.py` : c'est un porteur de code, et neuf d'entre eux
    portent le Python qui motive cette issue.
    """
    base = racine or RACINE
    vues: list[pathlib.Path] = []
    for arbre in ARBRES:
        dossier = base / arbre
        if not dossier.is_dir():
            continue
        vues += [f for f in dossier.rglob("*.py") if "__pycache__" not in f.parts]
        vues += list(dossier.rglob("*.sh"))
    return sorted(vues)


def sources_python(fichier: pathlib.Path) -> list[str]:
    """Le Python que ce fichier porte : lui-meme, ou ce que ses heredocs enferment."""
    texte = fichier.read_text(encoding="utf-8", errors="replace")
    if fichier.suffix == ".py":
        return [texte]
    return [m.group("corps") for m in HEREDOC.finditer(texte)]


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les imports durs, hors stdlib et hors module local, que rien ne declare."""
    connus = sys.stdlib_module_names | LOCAUX | declares(racine)
    base = racine or RACINE
    trouves = []
    for fichier in fichiers(racine):
        for source in sources_python(fichier):
            for module in dict.fromkeys(imports_durs(source)):
                if module.lower().replace("-", "_") in connus or module in connus:
                    continue
                trouves.append(f"{fichier.relative_to(base)}  importe « {module} », non declare")
    return trouves


def _auto_test() -> int:
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu!r}, obtenu {obtenu!r}")
            echecs = 1

    verifie("un import de module se voit", imports_durs("import yaml\n"), ["yaml"])
    verifie("une forme `from` aussi", imports_durs("from yaml import safe_load\n"), ["yaml"])
    verifie(
        "un import groupe se decompose",
        imports_durs("import glob, os, sys\n"),
        ["glob", "os", "sys"],
    )
    # Le sens NEGATIF, sans lequel un detecteur qui rendrait TOUT passerait les trois premiers.
    verifie(
        "un import en corps de fonction est ignore",
        imports_durs("def f():\n    import graphify\n"),
        [],
    )
    verifie(
        "un import sous try, au niveau du module, compte",
        imports_durs("try:\n    import yaml\nexcept ImportError:\n    pass\n"),
        ["yaml"],
    )
    verifie("une source illisible ne fait pas planter le garde", imports_durs("def f(:\n"), [])
    verifie("le groupe de la doc est lu comme une declaration", "mkdocs" in declares(), True)

    with tempfile.TemporaryDirectory() as brut:
        bac = pathlib.Path(brut)
        (bac / "scripts").mkdir(parents=True)
        (bac / ".github" / "scripts").mkdir(parents=True)
        (bac / "scripts" / "un-garde.py").write_text("import yaml\n", encoding="utf-8")

        verifie(
            "un import non declare est un suspect",
            [s for s in suspects(bac) if "yaml" in s] != [],
            True,
        )
        verifie("et le garde a bien LU quelque chose", len(fichiers(bac)), 1)

        # Le sens NEGATIF : declarer le paquet doit faire disparaitre le suspect. Sans ce cas, un
        # detecteur qui accuserait TOUT passerait le precedent.
        # Le nom de la distribution NE SUFFIT PAS : `PyYAML` ne s importe pas `pyyaml`.
        (bac / "pyproject.toml").write_text(
            '[dependency-groups]\ngardes = ["PyYAML==6.0.2"]\n', encoding="utf-8"
        )
        verifie(
            "le seul nom de distribution ne suffit pas",
            [s for s in suspects(bac) if "yaml" in s] != [],
            True,
        )

        (bac / "pyproject.toml").write_text(
            '[dependency-groups]\ngardes = ["PyYAML==6.0.2"]\n'
            '[tool.vigiechiro.modules]\nPyYAML = ["yaml"]\n',
            encoding="utf-8",
        )
        verifie(
            "une fois le module declare, il ne l est plus",
            [s for s in suspects(bac) if "yaml" in s],
            [],
        )

        # Le Python enfoui dans un garde shell compte, sans quoi les neuf gardes de CI
        # passeraient au travers : c est la population meme qui motive cette issue.
        (bac / ".github" / "scripts" / "un-garde.sh").write_text(
            "python3 - <<'PY'\nimport requests\nPY\n", encoding="utf-8"
        )
        verifie(
            "le Python enfoui dans un heredoc compte",
            [s for s in suspects(bac) if "requests" in s] != [],
            True,
        )

        # Un import en corps de fonction reste hors champ, meme dans l arbre reel.
        (bac / "scripts" / "paresseux.py").write_text(
            "def f():\n    import graphify\n", encoding="utf-8"
        )
        verifie(
            "un import paresseux n est pas un suspect",
            [s for s in suspects(bac) if "graphify" in s],
            [],
        )

        # Le chemin de REFUS, et non le seul calcul. Sans ces trois cas, desarmer une condition de
        # refus laisserait le garde rendre 0 sur un depot fautif, temoins verts.
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            sur_defaut = rend_verdict(bac)
        verifie("un import non declare fait REFUSER", sur_defaut, 1)

        (bac / ".github" / "scripts" / "un-garde.sh").unlink()
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            sur_propre = rend_verdict(bac)
        verifie("un arbre conforme passe", sur_propre, 0)

    with tempfile.TemporaryDirectory() as vide:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            sur_vide = rend_verdict(pathlib.Path(vide))
        verifie("une population vide fait REFUSER", sur_vide, 1)
    return echecs


def rend_verdict(racine: pathlib.Path | None = None) -> int:
    """Le verdict, extrait du `__main__` pour que le REFUS soit eprouvable (ADR 4770).

    Un garde a deux comportements et l'on n'en teste spontanement qu'un : ce qu'il COMPTE, et ce
    qu'il REFUSE de conclure. Le second est ecrit partout et prouve nulle part ; sur
    `compte-les-reliquats.py`, desarmer la condition du refus a laisse six temoins verts pendant que
    le garde faisait ce que son ADR existe pour empecher.
    """
    lus = len(fichiers(racine))
    trouves = suspects(racine)
    print("Dependances non declarees par les gardes")
    for s in trouves:
        print(f"  {s}")
    print(f"\nDEPENDANCES | lus={lus} | non-declarees={len(trouves)}")
    sys.stdout.flush()

    # `lus=0` refuse, comme les trois fonctions de verdict de `_commun` depuis #5007 : un garde qui
    # ne balaie rien rend zero suspect, et ce zero ressemble a un succes.
    if lus == 0:
        print(
            "\nECHEC : ce garde n'a lu aucune source. Son zero ne prouve rien ; verifiez sa "
            "population.",
            file=sys.stderr,
        )
        return 1
    if trouves:
        print(
            f"\nECHEC : {len(trouves)} import(s) hors stdlib que rien ne declare.\n"
            f"Ajoutez-le a un groupe de `pyproject.toml`, avec sa version EPINGLEE. Si le module ne\n"
            f"porte pas le nom de la distribution, declarez-le dans `[tool.vigiechiro.modules]`.\n"
            f"Un import qui ne peut pas etre declare parce qu'il est facultatif se met en corps de\n"
            f"fonction : le garde ne le compte plus, et le lecteur voit qu'il est paresseux.",
            file=sys.stderr,
        )
        return 1
    print(f"\n{lus} source(s) lue(s) : tout import hors stdlib est declare.")
    return 0


CONTRAT = {
    "geste": "garde qui ne declare pas ce dont il a besoin",
    "population": "les points d entree de scripts/",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-dependances-declarees.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    raise SystemExit(rend_verdict())
