#!/usr/bin/env python3
"""Les inventaires que la CI tient sur elle-meme (#3794, porte du bash en #5221).

## Ce qu elle empeche

Ce depot exige de son **produit** que ses inventaires soient prouves : commandes CLI contre
`dev-docs/cli.md` (`DocumentationAJourTest`, dans les deux sens), surface CLI contre le compteur
verrouille de `cli-surface.bats`, vues contre captures contre doc, ADR contre index contre nav.

De sa **propre description**, il n exigeait rien. Trois inventaires etaient tenus a la main, et les
trois avaient derive - mesure le 2026-08-15 a la cloture du lot 3 (#3561) : deux workflows absents du
tableau, six gardes absentes du tableau des gardes (#3771), et la correspondance entre chemins
surveilles et classes jouees jamais confrontee.

Les deux workflows absents etaient **ceux de securite**. Ce n est pas un hasard : un inventaire non
garde perd d abord ce qu on regarde le moins.

Et le defaut se reproduit **pendant qu on le decrit** : #3771 annoncait cinq gardes manquantes, il y
en avait six. Les cinq avaient ete listees a l oeil ; la sixieme est sortie d un comptage. C est la
raison d etre de ce script - obtenir la liste plutot que la refaire.

## Ce qu elle ne verifie PAS, et pourquoi

**Le contenu des colonnes.** La colonne « ou elle tourne » porte des nuances vraies : un garde vit
dans un workflow manuel, un autre avertit sans bloquer. Exiger un libelle pousserait a completer le
tableau **en l aplatissant** - complet et trompeur, ce qui est pire que lacunaire. Le garde verifie
la **presence**, pas la description.

**Qu une classe surveillee merite de l etre.** Que `GestesFichiers` doive figurer parmi les chemins
surveilles du contrat de fichiers est une **decision**, pas une deduction : aucun script ne peut la
tirer du code. Elle reste a la charge d un humain, et c est ecrit ici pour qu on ne croie pas le
contraire.

## Ce qu il compte, maintenant qu il est lui-meme en Python

Sa population est faite de six motifs - `.github/scripts/`, `.github/assets/` et `scripts/**`, en
`.sh` comme en `.py` - et il retient de chaque fichier ceux qui **dispatchent** `--auto-test`, non
ceux qui en parlent.

Il entre donc dans sa propre population, ce qui n a rien de neuf : la version bash y entrait deja,
son bloc de jugement etant un tas de lignes non commentees ou `--auto-test` figurait, et son
dispatch le confirmant. Ce qui change est **par quelle branche** il s y compte. Le `.sh` passait par
la recherche textuelle ; ce fichier-ci passe par l arbre, et s y trouve par les constantes que
`porte_l_option` compare - `"--auto-test"` y est du code, pas un commentaire - puis par le
`if "--auto-test" in sys.argv` du bas de page. Un garde qui se compte par sa propre regle est le seul
cas ou son verdict le juge lui aussi, et son nom doit donc figurer au tableau des gardes comme les
autres.

**Ce qu il ne compte pas** reste ce qu il ne comptait pas : le fichier dont la seule mention de
l option vit dans un commentaire ou une docstring. `scripts/adr/_commun.py` en a fait les frais
avant #5032 - documenter le champ `temoin` du contrat suffisait a l exiger au tableau, alors qu il n
a aucun point d entree.

Usage : python3 .github/scripts/verifie_inventaires_ci.py [--auto-test]
"""

from __future__ import annotations

import ast
import glob
import os
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
RACINE_INJECTEE = "INVENTAIRES_RACINE"

SECTION_GARDES = "## Toute garde de CI porte sa propre preuve"

CITATION = re.compile(r"blob/main/\.github/workflows/([A-Za-z0-9_.-]+\.yml)")
LIGNE_DU_TABLEAU = re.compile(r"^\| `([^`]+)`", re.M)
FICHIER_SURVEILLE = re.compile(r"^(src/test/java/\S+\.java)$", re.M)
CLASSES_JOUEES = re.compile(r"-Dtest='([^']+)'")

# Le perimetre inclut `scripts/**`, et pas seulement `.github/`. Il s est arrete a `.github/` pendant
# tout le temps ou les gardes y vivaient - puis un banc de soixante-cinq cas est arrive sous
# `scripts/doc-video/`, il n etait lance par aucun workflow, et ce garde ne pouvait pas le dire : il
# ne regardait pas la. Un inventaire aveugle a un dossier annonce la completude qu il n a pas (#4013).
#
# Et pas seulement le shell. Le tableau porte `scripts/adr/verifie_scripts.py` de longue date, mais
# ces motifs s arretaient a `*.sh` : les deux gardes du graphe (#4231) y sont entrees parce que leur
# auteur savait qu il fallait le faire, pas parce que le garde l a exige. Aveugle a un dossier hier
# (#4013), aveugle a une EXTENSION aujourd hui - le meme defaut, et toujours sous la forme d un vert
# (#4255).
MOTIFS = (
    (".github", "scripts", "*.sh"),
    (".github", "scripts", "*.py"),
    (".github", "assets", "*.sh"),
    (".github", "assets", "*.py"),
    ("scripts", "**", "*.sh"),
    ("scripts", "**", "*.py"),
)


def racine_de(racine: pathlib.Path | None = None) -> pathlib.Path:
    """La racine jugee : l argument, sinon la variable injectee, sinon celle du depot."""
    return racine or pathlib.Path(os.environ.get(RACINE_INJECTEE, RACINE))


def bloc_de(texte: str, titre: str) -> str | None:
    """La section qui commence par `titre`, jusqu au prochain titre de meme niveau."""
    debut = texte.find(titre)
    if debut < 0:
        return None
    suite = texte.find("\n## ", debut + len(titre))
    return texte[debut : suite if suite > 0 else len(texte)]


def porte_l_option(chemin: str, texte: str) -> bool:
    """Un fichier PORTE l option quand il la DISPATCHE, non quand il en parle.

    La recherche brute sur le fichier entier concluait qu il y repond des qu il la mentionne, et
    `scripts/adr/_commun.py` en a fait les frais : documenter le champ `temoin` du contrat suffisait
    a l exiger au tableau, alors qu il n a aucun point d entree (#5032).

    C est la mise en garde que le fonds porte lui-meme pour les gardes de code, jamais appliquee a
    ce garde-ci : « un script qui compte un motif present dans un COMMENTAIRE est faux par
    construction ; le commentaire cite la chose, il ne la fait pas ».
    """
    if not chemin.endswith(".py"):
        # En shell, retirer les lignes de commentaire suffit.
        nu = "\n".join(l for l in texte.split("\n") if not l.lstrip().startswith("#"))
        return "--auto-test" in nu
    try:
        arbre = ast.parse(texte)
    except SyntaxError:
        # On ne conclut pas sur ce qu on ne sait pas lire, et on penche du cote BRUYANT : compter a
        # tort se voit et se corrige, ne pas compter est le silence que cet inventaire combat.
        return "--auto-test" in texte
    # Les commentaires n entrent pas dans l arbre : `ast` les ecarte seul. Restent les docstrings,
    # seules constantes qui ne sont pas du code, et qu il faut donc reconnaitre pour les exclure.
    docstrings = set()
    for noeud in ast.walk(arbre):
        corps = getattr(noeud, "body", None)
        porteur = (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)
        if isinstance(noeud, porteur) and corps and isinstance(corps[0], ast.Expr):
            tete = corps[0].value
            if isinstance(tete, ast.Constant) and isinstance(tete.value, str):
                docstrings.add(id(tete))
    return any(
        isinstance(n, ast.Constant)
        and isinstance(n.value, str)
        and "--auto-test" in n.value
        and id(n) not in docstrings
        for n in ast.walk(arbre)
    )


def autotestes(racine: pathlib.Path) -> set[str]:
    """Les noms de fichier des gardes qui repondent a `--auto-test`."""
    trouves: set[str] = set()
    for motif in MOTIFS:
        for chemin in glob.glob(os.path.join(str(racine), *motif), recursive=True):
            texte = pathlib.Path(chemin).read_text(encoding="utf-8", errors="ignore")
            if "--auto-test" in texte and porte_l_option(chemin, texte):
                trouves.add(os.path.basename(chemin))
    return trouves


def juger(racine: pathlib.Path | None = None) -> int:
    """Les trois inventaires, et le code de sortie qui va avec."""
    base = racine_de(racine)
    doc = os.path.join(str(base), "dev-docs", "ci-cd-release.md")
    workflows = os.path.join(str(base), ".github", "workflows")

    if not os.path.isfile(doc):
        print(f"❌ Page introuvable : {doc}")
        print(
            "   C'est la GARDE qui est en cause, pas les inventaires : le document a-t-il été renommé ?"
        )
        return 1

    texte = pathlib.Path(doc).read_text(encoding="utf-8")
    ecarts: list[str] = []

    # ─── 1. Les workflows ────────────────────────────────────────────────────────────────────────
    # On compare des FICHIERS CITÉS, jamais un nombre de lignes : `maven.yml` occupe cinq lignes du
    # tableau, une par job, et un garde qui compterait rougirait sur un tableau juste.
    cites = set(CITATION.findall(texte))
    reels = {os.path.basename(p) for p in glob.glob(os.path.join(workflows, "*.yml"))}

    if not reels:
        print(f"❌ Aucun workflow trouvé sous {workflows}")
        print("   La garde ne peut rien confronter : chemin déplacé ?")
        return 1

    for absent in sorted(reels - cites):
        ecarts.append(f"workflow `{absent}` existe mais n'est cité nulle part dans le tableau")
    for fantome in sorted(cites - reels):
        ecarts.append(f"le tableau cite `{fantome}`, qui n'existe plus")

    # ─── 2. Les gardes qui portent leur preuve ───────────────────────────────────────────────────
    bloc = bloc_de(texte, SECTION_GARDES)
    if bloc is None:
        print(f"❌ Section « {SECTION_GARDES} » introuvable dans {doc}")
        print("   C'est la GARDE qui est en cause : la section a-t-elle été renommée ?")
        return 1

    portees = autotestes(base)
    if not portees:
        print("❌ Aucun script portant « --auto-test » trouvé.")
        print("   La garde ne peut rien confronter : le motif ou le chemin a-t-il changé ?")
        return 1

    # On ne retient que le nom de fichier : le tableau écrit tantôt `verifie-jeton.sh`, tantôt
    # `scripts/adr/verifie_scripts.py`, et le chemin n'est pas ce qui est vérifié ici.
    tableau = {c.split("/")[-1] for c in LIGNE_DU_TABLEAU.findall(bloc)}
    for absent in sorted(portees - tableau):
        ecarts.append(f"garde `{absent}` répond à --auto-test mais n'est pas au tableau des gardes")

    # ─── 3. Le contrat de système de fichiers ────────────────────────────────────────────────────
    filtre = os.path.join(str(base), ".github", "scripts", "porte_sur_le_contrat_de_fichiers.py")
    maven = os.path.join(str(base), ".github", "workflows", "maven.yml")
    if os.path.isfile(filtre) and os.path.isfile(maven):
        surveilles = pathlib.Path(filtre).read_text(encoding="utf-8")
        tests_surveilles = {os.path.basename(m)[:-5] for m in FICHIER_SURVEILLE.findall(surveilles)}
        corps = pathlib.Path(maven).read_text(encoding="utf-8")
        joue = CLASSES_JOUEES.search(corps)
        if joue is None:
            ecarts.append(
                "le job `contrat-fichiers` ne porte plus de `-Dtest='…'` : la garde ne sait plus quoi lire"
            )
        else:
            classes = {c.strip() for c in joue.group(1).split(",") if c.strip()}
            for absent in sorted(tests_surveilles - classes):
                ecarts.append(
                    f"`{absent}` est surveillé par le filtre mais n'est pas joué par la matrice"
                )
            for orphelin in sorted(classes - tests_surveilles):
                ecarts.append(
                    f"`{orphelin}` est joué par la matrice mais son fichier n'est pas surveillé"
                )
    else:
        ecarts.append(
            "filtre du contrat de fichiers ou `maven.yml` introuvable : la garde est en cause"
        )

    # ─── Verdict ─────────────────────────────────────────────────────────────────────────────────
    if ecarts:
        print(f"❌ {len(ecarts)} écart(s) entre ce que la CI fait et ce qu'elle dit d'elle-même :")
        for e in ecarts:
            print(f"   · {e}")
        print()
        print(
            "   Un inventaire qui se dit exhaustif et ne l'est pas rend l'inverse du service qu'il"
        )
        print(
            "   annonce : on le lit pour savoir ce qui tourne, et il omet ce qu'on regarde le moins."
        )
        return 1

    print(
        f"✔ {len(reels)} workflow(s) et {len(portees)} garde(s) autotestée(s) : les inventaires concordent."
    )
    return 0


CONTRAT_DE_FICHIERS = """SURVEILLES=$(cat <<'FIN'
src/test/java/fr/univ_amu/iut/UnTest.java
FIN
)
"""
PAGE = """| [a.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/a.yml) | x | y | z |
| [b.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/b.yml) | x | y | z |
| [maven.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml) · job `un` | x | y | z |
| [maven.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml) · job `deux` | x | y | z |

## Toute garde de CI porte sa propre preuve

| Garde | Ce qu'elle vérifie | Où elle tourne |
|---|---|---|
| `verifie-truc.sh` | un truc | `lint.yml` |

## Suite
"""
GARDE_EN_PYTHON = "if '--auto-test' in argv:\n    pass\n"


def _monter(bac: pathlib.Path) -> pathlib.Path:
    """Un depot jouet complet et coherent, que chaque cas degradera d une seule facon."""
    import shutil

    depot = bac / "depot"
    shutil.rmtree(depot, ignore_errors=True)
    for dossier in (".github/workflows", ".github/scripts", ".github/assets", "dev-docs"):
        (depot / dossier).mkdir(parents=True)
    (depot / ".github/workflows/a.yml").write_text(
        "name: A\n        -Dtest='UnTest'\n", encoding="utf-8"
    )
    (depot / ".github/workflows/b.yml").write_text("name: B\n", encoding="utf-8")
    shutil.copy(depot / ".github/workflows/a.yml", depot / ".github/workflows/maven.yml")
    (depot / ".github/scripts/verifie-truc.sh").write_text("echo --auto-test\n", encoding="utf-8")
    (depot / ".github/scripts/porte_sur_le_contrat_de_fichiers.py").write_text(
        CONTRAT_DE_FICHIERS, encoding="utf-8"
    )
    (depot / "dev-docs/ci-cd-release.md").write_text(PAGE, encoding="utf-8")
    return depot


def _remplace(chemin: pathlib.Path, avant: str, apres: str) -> None:
    chemin.write_text(chemin.read_text(encoding="utf-8").replace(avant, apres), encoding="utf-8")


def _pose_la_garde_python(depot: pathlib.Path) -> None:
    (depot / "scripts/outil").mkdir(parents=True, exist_ok=True)
    (depot / "scripts/outil/garde_oubliee.py").write_text(GARDE_EN_PYTHON, encoding="utf-8")


def _workflow_non_cite(d: pathlib.Path) -> None:
    (d / ".github/workflows/c.yml").write_text("name: C\n", encoding="utf-8")


def _workflow_disparu(d: pathlib.Path) -> None:
    _remplace(
        d / "dev-docs/ci-cd-release.md",
        "blob/main/.github/workflows/b.yml",
        "blob/main/.github/workflows/disparu.yml",
    )


def _garde_shell_oubliee(d: pathlib.Path) -> None:
    (d / ".github/scripts/verifie-oublie.sh").write_text("echo --auto-test\n", encoding="utf-8")


def _garde_d_assets_oubliee(d: pathlib.Path) -> None:
    (d / ".github/assets/check-oublie.sh").write_text("echo --auto-test\n", encoding="utf-8")


def _garde_python_inscrite(d: pathlib.Path) -> None:
    _pose_la_garde_python(d)
    _remplace(
        d / "dev-docs/ci-cd-release.md",
        "\n## Suite",
        "\n| `scripts/outil/garde_oubliee.py` | un autre truc | `lint.yml` |\n\n## Suite",
    )


def _classe_jouee_non_surveillee(d: pathlib.Path) -> None:
    _remplace(d / ".github/workflows/maven.yml", "-Dtest='UnTest'", "-Dtest='AutreTest'")


def _fichier_surveille_non_joue(d: pathlib.Path) -> None:
    _remplace(
        d / ".github/scripts/porte_sur_le_contrat_de_fichiers.py",
        "src/test/java/fr/univ_amu/iut/UnTest.java",
        "src/test/java/fr/univ_amu/iut/UnTest.java\nsrc/test/java/fr/univ_amu/iut/PasJoueTest.java",
    )


def _section_renommee(d: pathlib.Path) -> None:
    _remplace(d / "dev-docs/ci-cd-release.md", SECTION_GARDES, "## Autre titre")


def _page_absente(d: pathlib.Path) -> None:
    (d / "dev-docs/ci-cd-release.md").unlink()


# (attendu, libelle, degradation appliquee au depot coherent, motif exige dans la sortie)
CAS = (
    # Le controle NÉGATIF d abord, et il est le plus important : une regle qui refuse tout est aussi
    # inutile qu une regle qui accepte tout. Un depot coherent - `maven.yml` cite DEUX fois, une
    # ligne par job - doit rester vert.
    (0, "un dépôt cohérent reste vert, maven.yml cité deux fois compris", None, ""),
    (1, "un workflow non cité est vu", _workflow_non_cite, "c.yml"),
    (1, "un workflow cité qui n'existe plus est vu", _workflow_disparu, "disparu.yml"),
    (
        1,
        "une garde autotestée absente du tableau est vue",
        _garde_shell_oubliee,
        "verifie-oublie.sh",
    ),
    # Le meme cas depuis `.github/assets/` : les deux dossiers portent des gardes, et n en balayer
    # qu un laissait `check-captures.sh` et ses voisins hors de portee.
    (
        1,
        "une garde autotestée de assets/ est vue aussi",
        _garde_d_assets_oubliee,
        "check-oublie.sh",
    ),
    # Le meme cas en Python. Le tableau porte `scripts/adr/verifie_scripts.py` depuis longtemps,
    # mais les motifs ne balayaient que le shell : les deux gardes du graphe (#4231) y sont entrees
    # parce que leur auteur savait qu il fallait le faire, pas parce que le garde l a exige. Une
    # exigence tenue de memoire finit par tomber - c est la raison d etre de ce script.
    (
        1,
        "une garde autotestée en Python est vue aussi",
        _pose_la_garde_python,
        "garde_oubliee.py",
    ),
    # Et son pendant : inscrite au tableau, elle ne doit plus rien reprocher. Une regle qui refuse
    # tout est aussi inutile qu une regle qui accepte tout.
    (0, "la même garde Python, inscrite au tableau, repasse au vert", _garde_python_inscrite, ""),
    (
        1,
        "une classe jouée dont le fichier n'est pas surveillé est vue",
        _classe_jouee_non_surveillee,
        "AutreTest",
    ),
    (
        1,
        "un test surveillé que la matrice ne joue pas est vu",
        _fichier_surveille_non_joue,
        "PasJoueTest",
    ),
    # Les refus qui accusent la GARDE, et non ce qu elle surveille. Sans eux, un renommage de
    # section rendrait un vert rassurant la ou plus rien n est confronte.
    (
        1,
        "une section renommée accuse la garde",
        _section_renommee,
        "C'est la GARDE qui est en cause",
    ),
    (1, "la page absente accuse la garde", _page_absente, "C'est la GARDE qui est en cause"),
)


def _auto_test() -> int:
    """Les onze cas de la version bash, et chacun exige le MESSAGE autant que le code."""
    import contextlib
    import io
    import tempfile

    echecs = 0
    cas = rouges = 0
    with tempfile.TemporaryDirectory(prefix="vc-inv-") as tmp:
        bac = pathlib.Path(tmp)
        for attendu, libelle, degrade, motif in CAS:
            cas += 1
            if attendu != 0:
                rouges += 1
            depot = _monter(bac)
            if degrade is not None:
                degrade(depot)
            tampon = io.StringIO()
            with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
                code = juger(depot)
            sortie = tampon.getvalue()
            if code != attendu:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {code}")
                for ligne in sortie.splitlines():
                    print(f"       {ligne}")
                echecs = 1
            elif motif and motif not in sortie:
                print(
                    f"  ✘ {libelle} : code {code} attendu, mais le motif « {motif} » manque au verdict"
                )
                echecs = 1
            else:
                print(f"  ✔ {libelle}")

    print()
    verbe = "DOIT" if rouges == 1 else "DOIVENT"
    print(f"{cas} cas, dont {rouges} qui {verbe} rougir.")
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(juger())
