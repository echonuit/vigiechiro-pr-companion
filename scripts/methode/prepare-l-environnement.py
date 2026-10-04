#!/usr/bin/env python3
"""Pose ce qu un worktree neuf n a pas, pour qu un verdict local porte sur le CODE (issue #5406).

Un worktree neuf n a ni `.github/openspec/node_modules` ni `ruff`. Les gardes qui en dependent
refusent alors de conclure, et ce refus **ressemble a un defaut du changement en cours**. Le depot a
deja constate ce mecanisme pour les MODULES Python, en tete de `pyproject.toml` : « en local, six des
neuf plantaient nu sur `ModuleNotFoundError`, erreur qui ressemble a un defaut du changement en
cours ». Celui-ci le corrige pour les OUTILS.

**Ce qu il pose, et ce qu il ne pose pas.** Uniquement ce qui est bon marche - deux secondes pour
l un, quatre pour l autre, mesure le 2026-09-06. `target/pmd.xml` demande une a deux minutes et
depend de ce que le diff touche : il appartient a la porte, qui sait le derive (#5405).

**Il ne bloque JAMAIS, et ce n est pas son code de sortie qui le garantit.** Une preparation qui
echoue le dit en une ligne et rend la main. Le crochet qui l appelle a deja ce dessin pour son
`clean`, et sa raison vaut ici : personne ne regarde un crochet, et une commande qui empeche de creer
un worktree coute plus qu elle ne rend.

La non-blocance est la propriete de l APPELANT : le crochet se protege par son `|| true` suivi d un
`exit 0`. Ce script rendait pourtant toujours `0`, donc son compte d echecs n etait lisible par
personne. Il rend desormais **2** quand il n a pas pu poser - « je n ai pas pu juger », que le depot
separe d un `1` - sans que cela bloque quoi que ce soit (ADR 5775).

**Il ne ment jamais non plus.** Une preparation muette qui echoue rendrait la porte MOINS sure
qu avant : le lecteur croirait l environnement complet. Chaque echec nomme la commande.

**Il n importe que la STDLIB, et c est necessaire.** `post-commit` et `post-merge` choisissent leur
interpreteur - « uv tool, pipx ou systeme » - parce qu ils lancent un script qui importe `graphify`.
Celui-ci n a pas ce besoin et ne doit pas l avoir : il POSE ce dont les autres dependent, donc il ne
peut dependre de rien. Un lecteur qui l alignerait sur ses voisins par souci de coherence le rendrait
incapable de tourner sur le poste ou il sert le plus - celui ou rien n est encore installe.

**Pourquoi en Python et non dans le crochet.** Ce dispositif n a **aucun gardien en CI** - le runner
installe tout lui-meme et ne joue jamais ce chemin. Son auto-test porte donc seul, et trente lignes
de bash se relisent la ou un script Python s eprouve.

Usage :
    python3 scripts/methode/prepare-l-environnement.py
    python3 scripts/methode/prepare-l-environnement.py --auto-test
"""

import os
import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test, sort_si_contrat_demande


# `.venv` DANS le worktree, et non un venv partage hors du depot.
#
# Deux prescriptions concurrentes existaient, et ce lot tranche entre elles (#5426) :
# `CONTRIBUTING.md` decrit `python3 -m venv .venv` puis `pip install --group gardes` - le groupe
# ENTIER - tandis que `ouvrir-une-pr` decrivait `~/.venv-outils` avec `ruff` SEUL. La premiere gagne,
# pour trois raisons mesurees.
#
# **Elle porte ce qu un garde importe**, pas seulement des executables. C est la difference qui rendait
# le trou invisible : `ruff` s appelle par son chemin, une grammaire s importe.
#
# **Un venv PAR worktree** est la meme decision que pour `node_modules` : une branche qui change une
# version epinglee serait eprouvee contre celle d une autre. #4849 avait ecarte le partage pour cette
# raison exacte, et l appliquer aux outils Python et pas aux modules aurait ete incoherent.
#
# **Rien a exclure** : depuis Python 3.11, `venv` ecrit lui-meme son `.venv/.gitignore`, ce que
# `CONTRIBUTING.md` dit deja.
def venv_de(racine: pathlib.Path) -> pathlib.Path:
    """Le venv de CE worktree."""
    return racine / ".venv"


CONTRAT = {
    "geste": "prerequis absent d un worktree neuf",
    "population": "les outils declares que le depot peut poser",
    # `generateur`, et non un mot neuf : le vocabulaire des dispositifs est ferme et declare dans
    # `verifie_contrats_tiennent.py` « et nulle part ailleurs » (ADR 5125). Des sept, c est celui qui
    # dit « il ne juge pas, il ECRIT » - et poser un `node_modules` ou un venv, c est ecrire.
    # L elargir pour un seul cas serait une decision, pas une commodite.
    "dispositif": "generateur",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/prepare-l-environnement.py --auto-test",
    "decision": "hygiene, sans decision",
    "chemins": """
.githooks/**
scripts/methode/prepare-l-environnement.py
""",
}


def openspec_pose(racine: pathlib.Path) -> bool:
    """L outil OpenSpec est-il installe dans CE worktree ?"""
    return (racine / ".github" / "openspec" / "node_modules").is_dir()


def ruff_pose(racine: pathlib.Path) -> bool:
    """Le venv de ce worktree porte-t-il `ruff` ?"""
    return (venv_de(racine) / "bin" / "ruff").exists()


def venv_porte_le_groupe(racine: pathlib.Path, lance=subprocess.run) -> bool:
    """Le venv porte-t-il ce qu un garde IMPORTE, et pas seulement ses executables ?

    `ruff` present ne prouve rien : c est un executable, et le venv peut le porter sans porter un
    seul module. Ce critere-la etait trop grossier - il rendait « rien a faire » sur un venv qui ne
    pouvait lancer aucun garde, defaut mesure le 2026-09-07 (#5426).
    """
    modules = list(modules_declares(racine))
    if not modules:
        return True
    python = venv_de(racine) / "bin" / "python"
    if not python.exists():
        return False
    try:
        rendu = lance(
            [str(python), "-c", "import " + ", ".join(modules)],
            capture_output=True,
            text=True,
            check=False,
        )
    except FileNotFoundError:
        return False
    return rendu.returncode == 0


def modules_declares(racine: pathlib.Path) -> dict[str, str]:
    """Ce qu un garde IMPORTE, et la distribution qui le fournit.

    La table `[tool.vigiechiro.modules]` existe deja et dit exactement cela : « le nom de la
    DISTRIBUTION n est pas celui du MODULE ». On la lit plutot que de deviner - deviner exigerait
    d interroger ce qui est installe, donc de rendre un verdict qui depend de la machine.
    """
    fichier = racine / "pyproject.toml"
    if not fichier.exists():
        return {}
    import tomllib

    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return {}
    table = ((donnees.get("tool") or {}).get("vigiechiro") or {}).get("modules") or {}

    # Le groupe `gardes` SEUL. `mkdocs-material` est declare aussi, et ses modules manqueraient a un
    # poste qui ne construit pas la documentation - les nommer ici ferait crier le dispositif sur du
    # bon travail, ce que l ADR 4002 refuse. Ce controle ne parle que de ce qu un GARDE importe.
    du_groupe = {
        re.split(r"[=<>!~\[;]", e)[0].strip().lower().replace("_", "-")
        for e in (donnees.get("dependency-groups") or {}).get("gardes") or []
        if isinstance(e, str)
    }

    rendu = {}
    for distribution, modules in table.items():
        if distribution.lower().replace("_", "-") not in du_groupe:
            continue
        for module in modules if isinstance(modules, list) else []:
            if isinstance(module, str) and module.strip():
                rendu[module.strip()] = distribution
    return rendu


def modules_manquants(racine: pathlib.Path) -> list[tuple[str, str]]:
    """Les modules DECLARES que l interpreteur courant ne porte pas.

    **Ce que ce controle repare.** `ruff` est un EXECUTABLE : le poser dans un venv suffit, on l
    appelle par son chemin. Une grammaire est un MODULE : la poser dans un venv ne sert a rien tant
    que le python qui LANCE le garde ne la porte pas. Le depot avait une convention pour les premiers
    et aucune pour les seconds, et `PyYAML` masquait le trou en etant porte par chance - par le python
    systeme comme par l image de CI (#5426).

    Il ne POSE rien : `pip` n est pas garanti sur un poste, `uv` n est pas un prerequis - `pyproject`
    l ecrit - et choisir a la place de l utilisateur ou vit son interpreteur serait decider pour lui.
    Il DIT, ce qui suffit a distinguer « le code est fautif » de « ton interpreteur ne voit pas ce que
    le depot declare ».
    """
    import importlib.util

    manquants = []
    for module, distribution in sorted(modules_declares(racine).items()):
        if importlib.util.find_spec(module) is None:
            manquants.append((module, distribution))
    return manquants


def a_faire(base: pathlib.Path) -> list[tuple[str, list[str]]]:
    """Ce qui MANQUE, nomme, et la commande qui le pose.

    Rendu comme une liste plutot qu execute ici : c est ce qui rend la decision lisible et le cas
    « rien a faire » verifiable sans lancer quoi que ce soit.
    """
    manques = []
    if not openspec_pose(base):
        manques.append(
            ("l outil OpenSpec", ["npm", "ci", "--prefix", str(base / ".github" / "openspec")])
        )
    if not ruff_pose(base):
        manques.append(("le venv du worktree", [sys.executable, "-m", "venv", str(venv_de(base))]))
    elif not venv_porte_le_groupe(base):
        # Le venv existe et porte `ruff`, mais pas les modules : le groupe a bouge depuis sa creation.
        manques.append(
            (
                "le groupe `gardes` du venv",
                [
                    str(venv_de(base) / "bin" / "pip"),
                    "install",
                    "-q",
                    "--group",
                    str(base / "pyproject.toml") + ":gardes",
                ],
            )
        )
    return manques


# Combien de temps on laisse au profil du poste. Un shell INTERACTIF lit le profil entier, et un
# profil peut etre lent, bavard, ou attendre un terminal : un crochet qui pend est pire qu un crochet
# qui echoue, parce qu il n a pas de symptome.
DELAI_DU_POSTE_S = 10.0


def resolu_par_le_poste(
    outil: str, shell: str | None = None, delai: float = DELAI_DU_POSTE_S, lance=subprocess.run
) -> tuple[str | None, str | None]:
    """Le chemin absolu de `outil` tel que le POSTE le declare. Rend (chemin, cause d echec).

    ## Le depot ne CHERCHE pas l outil, il DEMANDE au poste

    Mesure du 2026-10-03 sur le poste de developpement, ou `node` vit sous un gestionnaire de version :

        /bin/sh -c    'command -v npm'  -> INTROUVABLE   (ce que le crochet voit)
        bash -lc      'command -v npm'  -> INTROUVABLE   (nvm vit dans .zshrc, pas dans .profile)
        bash -ic      'command -v npm'  -> INTROUVABLE
        "$SHELL" -ic  'command -v npm'  -> .../nvm/versions/node/v24.21.0/bin/npm

    Le poste **declare deja** ou vit son outil, dans le profil de son propre shell, et cette
    declaration est tenue par son proprietaire. Trois autres formes ont ete pesees et ecartees : une
    table de dispositions connues - nvm, asdf, volta, fnm - que le depot devrait maintenir et qui
    vieillit ; un fichier non versionne a faire ecrire, qui duplique une declaration existante et que
    personne ne pense a ecrire ; et ne rien chercher, qui etait le perimetre plus petit refuse par le
    porteur.

    Aucun gestionnaire de version n est donc nomme ici, et il n y a pas de liste a tenir. C est ce qui
    retire a ce lot la decision de portabilite qu il croyait devoir prendre.

    ## Pourquoi `stdout` SEUL, et le dernier chemin EXISTANT

    Le profil de ce poste ecrit une ligne parasite sur `stderr` - un `source ~/.cargo/env` casse,
    present dans `.profile` comme dans `.zshrc`. Melanger les deux flux ferait lire cette ligne comme
    un chemin. Et un profil peut aussi ecrire sur `stdout` : on retient donc la derniere ligne qui
    EST un chemin absolu existant, ce qui se verifie au lieu de se supposer.
    """
    shell = shell or os.environ.get("SHELL") or "/bin/sh"
    try:
        rendu = lance(
            [shell, "-ic", f"command -v {outil}"],
            capture_output=True,
            text=True,
            check=False,
            timeout=delai,
        )
    except FileNotFoundError:
        return None, f"le shell du poste « {shell} » est introuvable"
    except subprocess.TimeoutExpired:
        return None, (
            f"le profil interactif de « {shell} » n a pas rendu la main en {delai:.0f} s :"
            " la cause est le PROFIL du poste, pas l outil"
        )

    for ligne in reversed(rendu.stdout.splitlines()):
        chemin = pathlib.Path(ligne.strip())
        if chemin.is_absolute() and chemin.exists():
            return str(chemin), None
    return None, (
        f"le poste ne declare pas « {outil} » dans le profil de son shell « {shell} » :"
        " ce refus ne parle pas de votre diff"
    )


def avec_le_voisinage(chemin: str) -> dict[str, str]:
    """L environnement courant, plus le repertoire de `chemin` en TETE du PATH.

    ## Pourquoi resoudre le chemin ne suffit pas

    Trouve en eprouvant ce lot sur le poste reel, et c est le maillon 3 de #5774 qui mordait mon
    propre correctif. Le poste declare bien ou vit `npm`, on le lance par son chemin absolu, et il
    echoue : `npm` est un script dont le shebang est `#!/usr/bin/env node`, donc il reclame son
    INTERPRETE sur le PATH de l enfant. Resoudre l outil sans resoudre son interprete reproduit
    exactement le defaut que #5774 a corrige cote message.

    ## La premisse, mesuree avant d en dependre

    L outil et son interprete sont **voisins**, installes dans le meme `bin/` :

        .../nvm/versions/node/v24.21.0/bin/  ->  corepack  node  npm  npx

    Mettre ce repertoire en tete du PATH suffit donc, et se verifie : le meme `npm` lance avec son
    voisinage rend sa version au lieu d echouer. C est vrai des gestionnaires qui installent une
    distribution complete par version - nvm, asdf, volta, fnm, un paquet systeme.

    **La limite se declare** : un poste qui rangerait l outil loin de son interprete ne serait pas
    couvert. Il obtiendrait l echec nomme du lanceur plutot qu un silence, ce qui est le comportement
    voulu, et non une pose.
    """
    dossier = str(pathlib.Path(chemin).parent)
    chemins = [dossier, *os.environ.get("PATH", "").split(os.pathsep)]
    return {**os.environ, "PATH": os.pathsep.join(c for c in chemins if c)}


def pose(racine: pathlib.Path | None = None, lance=subprocess.run) -> int:
    """Pose ce qui manque. Rend le nombre d echecs, et n en fait jamais un motif de blocage."""
    base = racine or RACINE
    manques = a_faire(base)
    if not manques:
        return 0

    echecs = 0
    for quoi, commande in manques:
        # `FileNotFoundError` quand l OUTIL lui-meme manque - `npm` absent du PATH. Sans ce filet, la
        # trace Python entiere sort dans un crochet que personne ne regarde, la ou le crochet voisin
        # prend soin qu « une seule ligne d avertissement remplace le mur d erreurs ». Trouve en
        # mesurant, pas en relisant.
        try:
            rendu = lance(commande, capture_output=True, text=True, check=False)
        except FileNotFoundError:
            # ⟨on ne demande au poste QU ICI, et le « quand » a son cas⟩ Le PATH ordinaire vient
            # d echouer : c est le seul moment ou interroger le profil du poste se justifie. Le faire
            # d avance couterait 0,6 s a chaque arbre pour une question deja resolue, et un cas
            # d auto-test rougit si cette interrogation devient inconditionnelle.
            chemin, cause = resolu_par_le_poste(commande[0], lance=lance)
            if chemin is None:
                echecs += 1
                print(f"preparation : {quoi} n a pas pu etre pose, {cause}")
                continue
            print(f"preparation : « {commande[0]} » hors du PATH ; le poste le declare en {chemin}")
            try:
                rendu = lance(
                    [chemin, *commande[1:]],
                    capture_output=True,
                    text=True,
                    check=False,
                    env=avec_le_voisinage(chemin),
                )
            except OSError as leve:
                echecs += 1
                print(
                    f"preparation : {quoi} n a pas pu etre pose, {chemin} a leve"
                    f" {type(leve).__name__}"
                )
                continue
        if rendu.returncode != 0:
            echecs += 1
            print(f"preparation : {quoi} n a pas pu etre pose ({' '.join(commande[:3])}...)")
            continue
        # `ruff` demande un second geste : le venv, puis le paquet epingle par `pyproject.toml`.
        #
        # Et le GROUPE ENTIER avec lui, pas seulement `ruff` : le venv doit porter ce qu un garde
        # IMPORTE, sans quoi il ne sert qu aux executables. C est ce qui rend le venv utilisable comme
        # interpreteur, et donc #5434 possible - la porte pourra le lancer au lieu de `python3`.
        if quoi == "le venv du worktree":
            suite = lance(
                [
                    str(venv_de(base) / "bin" / "pip"),
                    "install",
                    "-q",
                    "--group",
                    str(base / "pyproject.toml") + ":gardes",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            if suite.returncode != 0:
                # Repli : le groupe demande un `pip` recent. A defaut, `ruff` seul, qui suffit aux
                # compétences qui l appellent par son chemin.
                suite = lance(
                    [str(venv_de(base) / "bin" / "pip"), "install", "-q", version_de_ruff(base)],
                    capture_output=True,
                    text=True,
                    check=False,
                )
            if suite.returncode != 0:
                echecs += 1
                print("preparation : le groupe `gardes` n a pas pu etre installe dans `.venv`")
                continue
        print(f"preparation : {quoi} pose")
    return echecs


def dit_les_modules_manquants(racine: pathlib.Path | None = None) -> int:
    """Nomme ce que l interpreteur courant ne voit pas, et rend le compte. Ne pose rien."""
    base = racine or RACINE
    manquants = modules_manquants(base)
    if not manquants:
        return 0
    import sys as _sys

    print(
        f"preparation : {len(manquants)} module(s) declare(s) invisible(s) depuis {_sys.executable} :"
    )
    for module, distribution in manquants:
        print(f"    `import {module}` -> absent ; il vient de `{distribution}`")
    print(
        "    Le depot les declare au groupe `gardes` de `pyproject.toml`. Trois facons de les avoir,"
    )
    print(
        "    et le depot n en impose aucune : `pip install --group gardes`, `uv run --group gardes`,"
    )
    print(
        "    ou un venv dont on lance le python. Un garde qui refuse ici ne dit RIEN de votre diff."
    )
    return len(manquants)


def version_de_ruff(racine: pathlib.Path) -> str:
    """La version EPINGLEE, lue dans `pyproject.toml` plutot que recopiee ici.

    Une version ecrite a deux endroits diverge, et c est le garde d epinglage qui le paierait. A
    defaut de la lire, on installe `ruff` nu : mieux vaut un outil present qu un refus de poser.
    """
    fichier = racine / "pyproject.toml"
    if not fichier.exists():
        return "ruff"
    import tomllib

    try:
        donnees = tomllib.loads(fichier.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError:
        return "ruff"
    for exigence in (donnees.get("dependency-groups") or {}).get("gardes") or []:
        if isinstance(exigence, str) and exigence.lower().startswith("ruff"):
            return exigence
    return "ruff"


def _auto_test() -> int:
    import shutil
    import tempfile

    verifie, echecs = cas_d_auto_test()

    bac = pathlib.Path(tempfile.mkdtemp())
    (bac / "pyproject.toml").write_text(
        '[dependency-groups]\ngardes = ["PyYAML==6.0.2", "ruff==0.16.5"]\n', encoding="utf-8"
    )

    # La VERSION vient du fichier, jamais d une constante : deux endroits divergent.
    verifie("la version de ruff se lit dans pyproject.toml", version_de_ruff(bac), "ruff==0.16.5")
    verifie(
        "sans fichier, ruff nu plutot qu un refus de poser", version_de_ruff(bac / "absent"), "ruff"
    )

    # Ce qui MANQUE est nomme. `node_modules` absent de ce bac : OpenSpec doit y figurer.
    quoi = [q for q, _ in a_faire(bac)]
    verifie("un OpenSpec absent est a poser", "l outil OpenSpec" in quoi, True)

    # Et ce qui est LA n est pas repose : c est ce qui rend la seconde creation gratuite.
    (bac / ".github" / "openspec" / "node_modules").mkdir(parents=True)
    quoi = [q for q, _ in a_faire(bac)]
    verifie("un OpenSpec deja pose n est PAS repose", "l outil OpenSpec" in quoi, False)

    # L ECHEC ne bloque pas, et il se compte. Sans ce cas, une preparation muette passerait pour
    # complete, et la porte serait moins sure qu avant.
    vide = pathlib.Path(tempfile.mkdtemp())

    class Rate:
        returncode = 1
        stdout = ""
        stderr = "pas de reseau"

    verifie("un echec est compte, pas leve", pose(vide, lance=lambda *a, **k: Rate()) > 0, True)

    # L OUTIL ABSENT du PATH : `npm` introuvable leve `FileNotFoundError`, et une trace Python
    # entiere dans un crochet que personne ne regarde vaut moins qu une ligne. Trouve en mesurant.
    def introuvable(*a, **k):
        raise FileNotFoundError(2, "No such file or directory", "npm")

    verifie(
        "un outil introuvable est dit en UNE ligne, pas en trace",
        pose(vide, lance=introuvable) > 0,
        True,
    )

    # ⟨LES MODULES DECLARES⟩ Un module absent de l interpreteur courant se DIT, il ne se pose pas.
    decl = pathlib.Path(tempfile.mkdtemp())
    (decl / "pyproject.toml").write_text(
        "[dependency-groups]\n"
        'gardes = ["PyYAML==6.0.2", "tree-sitter-language-pack==1.16.1"]\n'
        'doc = ["mkdocs-material==9.7.7"]\n'
        "[tool.vigiechiro.modules]\n"
        'PyYAML = ["yaml"]\n'
        'tree-sitter-language-pack = ["tree_sitter_language_pack"]\n'
        'mkdocs-material = ["mkdocs", "material"]\n',
        encoding="utf-8",
    )
    vus = modules_declares(decl)
    verifie(
        "les modules du groupe gardes sont lus", sorted(vus), ["tree_sitter_language_pack", "yaml"]
    )
    verifie("ceux du groupe DOC sont ecartes", "material" in vus, False)
    verifie("la distribution est rendue avec le module", vus.get("yaml"), "PyYAML")

    # Le sens NEGATIF de ce controle : un module PRESENT ne se signale pas. `yaml` l est ici.
    manquants = dict(modules_manquants(decl))
    verifie("un module present n est pas signale", "yaml" in manquants, False)

    # Sans declaration, rien a dire - un dispositif qui crierait sur un depot vide serait faux.
    verifie(
        "sans table, aucun module manquant", modules_manquants(pathlib.Path(tempfile.mkdtemp())), []
    )

    # Le sens NEGATIF : rien a poser rend zero sans rien lancer.
    lances = []

    def espion(*a, **k):
        lances.append(a)
        return Rate()

    complet = pathlib.Path(tempfile.mkdtemp())
    (complet / ".github" / "openspec" / "node_modules").mkdir(parents=True)
    if ruff_pose(complet):
        verifie("rien a poser ne lance rien", (pose(complet, lance=espion), len(lances)), (0, 0))

    # ⟨LE DEPOT DEMANDE AU POSTE⟩ Cas de #5775. Tous DIFFERES : un cas passe en valeur fait de son
    # harnais un harnais muet, qui ne peut pas nommer l expression qui leve (cliquet 5570).
    class Rendu:
        def __init__(self, code=0, sortie="", erreur=""):
            self.returncode, self.stdout, self.stderr = code, sortie, erreur

    def poste(declare, leve=None, journal=None, kwargs_vus=None, sur_erreur=None):
        """Un `lance` ou le PATH ordinaire n a pas `npm`, et ou le poste rend `declare` sur stdout.

        `declare` est une LISTE de lignes et non une chaine : un profil peut ecrire plusieurs lignes
        sur `stdout`, et c est exactement ce que les cas du `reversed` et du chemin relatif doivent
        pouvoir decrire. Un bouchon a une seule ligne rendait ces deux cas decoratifs.
        """

        def faux(commande, **kwargs):
            if journal is not None:
                journal.append(commande)
            if "-ic" in commande:
                if kwargs_vus is not None:
                    kwargs_vus.append(kwargs)
                if leve is not None:
                    raise leve
                # Le profil ecrit sur les DEUX flux : `stderr` porte une ligne parasite, comme sur
                # le poste reel, et elle ne doit jamais etre lue comme un chemin.
                return Rendu(
                    0,
                    "".join(l + "\n" for l in (declare or [])),
                    "".join(l + "\n" for l in (sur_erreur or [])),
                )
            if commande[0] == "npm":
                raise FileNotFoundError(2, "No such file or directory", "npm")
            return Rendu(0)

        return faux

    nu = pathlib.Path(tempfile.mkdtemp())

    # Le cas que ce lot existe pour couvrir : l arbre naissait vide, il naît equipe.
    verifie(
        "un outil hors du PATH mais DECLARE par le poste est pose",
        lambda: pose(nu, lance=poste([sys.executable])),
        0,
    )

    # ⟨LE « QUAND », et non le « quoi »⟩ Sans ce cas, demander au poste a chaque fois passerait :
    # le resultat serait juste et couterait 0,6 s par arbre. C est la forme de defaut qu une session
    # pair a payee sur son propre instrument le meme jour - un verdict juste, un gaspillage invisible.
    journal: list = []

    def path_suffit(ou):
        """Un `lance` ou TOUT reussit du premier coup : le PATH ordinaire porte les outils."""

        def faux(commande, **kwargs):
            ou.append(commande)
            return Rendu(0)

        return faux

    verifie(
        "le poste N EST PAS interroge quand le PATH suffit",
        lambda: (
            pose(nu, lance=path_suffit(journal)),
            any("-ic" in c for c in journal),
            len(journal) > 0,
        ),
        (0, False, True),
    )

    # Et le sens inverse, qui prouve que le cas precedent discrimine.
    journal_bis: list = []
    verifie(
        "il EST interroge quand le PATH a echoue",
        lambda: (
            pose(nu, lance=poste([sys.executable], journal=journal_bis)),
            any("-ic" in c for c in journal_bis),
        ),
        (0, True),
    )

    verifie(
        "un poste qui ne declare rien fait ECHOUER",
        lambda: pose(nu, lance=poste(None)) > 0,
        True,
    )

    # ⟨LE « QUAND » DE L ECHEC⟩ Dette nommee a la cloture de #5762. Quand le poste ne declare rien,
    # il ne reste RIEN a lancer : la seule commande passee au lanceur est la question posee au poste,
    # et aucune tentative d execution ne la suit. Sans ce cas, un echec rendu APRES une execution
    # vouee a echouer aurait passe tous les autres.
    # On compte les TENTATIVES de poser cet outil-la, et non les commandes du journal : l arbre a
    # aussi un venv a poser, dont les commandes sont lancees a juste titre. Le cas d abord ecrit
    # comptait tout, donc il rougissait sur du bon travail.
    def tentatives(journal) -> int:
        return len([c for c in journal if len(c) > 1 and c[1] == "ci"])

    journal_muet: list = []
    verifie(
        "poste muet : UNE seule tentative, aucune apres l echec de la question",
        lambda: (pose(nu, lance=poste(None, journal=journal_muet)) > 0, tentatives(journal_muet)),
        (True, 1),
    )
    # Le contraste, sans quoi le cas ci-dessus passerait sur un code qui ne retenterait JAMAIS.
    journal_declare: list = []
    verifie(
        "poste qui declare : DEUX tentatives, la seconde par le chemin resolu",
        lambda: (
            pose(nu, lance=poste([sys.executable], journal=journal_declare)),
            tentatives(journal_declare),
        ),
        (0, 2),
    )

    # Les causes se lisent, et chacune nomme ce qui manque plutot que ce qui en resulte.
    verifie(
        "un poste muet rend None, et non une ligne de son profil",
        lambda: resolu_par_le_poste(
            "npm", shell="/bin/sh", lance=poste(None, sur_erreur=["/home/x/.zshrc: pas de fichier"])
        )[0],
        None,
    )
    # Le cas qui DISCRIMINE : un chemin EXISTANT sur `stderr`, et un autre sur `stdout`. Avec une
    # ligne parasite qui n est pas un chemin, melanger les flux ne changeait rien et ce cas ne
    # prouvait rien - trouve par mutation, pas par relecture.
    verifie(
        "un chemin existant ecrit sur STDERR est ignore, celui de STDOUT gagne",
        lambda: resolu_par_le_poste(
            "npm", shell="/bin/sh", lance=poste(["/bin/sh"], sur_erreur=[sys.executable])
        )[0],
        "/bin/sh",
    )
    verifie(
        "un chemin declare mais INEXISTANT est refuse",
        lambda: resolu_par_le_poste("npm", shell="/bin/sh", lance=poste(["/n-existe-pas/npm"]))[0],
        None,
    )
    # DEUX chemins existants, et le bon est le DERNIER : `command -v` s execute apres le profil, donc
    # sa ligne est la derniere. Sans ce cas a deux lignes, le `reversed` n etait exerce par rien.
    verifie(
        "le DERNIER chemin existant est retenu, pas le premier",
        lambda: resolu_par_le_poste(
            "npm", shell="/bin/sh", lance=poste(["/bin/sh", sys.executable])
        )[0],
        sys.executable,
    )

    # ⟨une ligne relative QUI EXISTE⟩ « npm » ne designe aucun fichier, donc `exists()` le rejetait
    # deja et le cas ne prouvait rien de `is_absolute`. Le risque reel est un profil qui ecrit un mot
    # NU qui se trouve designer un fichier du repertoire courant - `docs`, `scripts`, `pom.xml` sont
    # tous a la racine d un worktree, qui est le repertoire ou le crochet tourne.
    def relatif_existant() -> str | None:
        bac = pathlib.Path(tempfile.mkdtemp())
        (bac / "docs").mkdir()
        avant_cwd = pathlib.Path.cwd()
        try:
            os.chdir(bac)
            return resolu_par_le_poste("npm", shell="/bin/sh", lance=poste(["docs"]))[0]
        finally:
            os.chdir(avant_cwd)

    verifie("un mot NU qui designe un fichier du cwd est refuse", relatif_existant, None)
    # Le DELAI est-il reellement passe au lanceur ? Le cas du depassement eprouve le traitement, pas
    # la transmission : sans celui-ci, retirer `timeout=` ne ferait rougir personne.
    kwargs_vus: list = []
    verifie(
        "le delai de garde est TRANSMIS au lanceur",
        lambda: (
            resolu_par_le_poste(
                "npm", shell="/bin/sh", lance=poste([sys.executable], kwargs_vus=kwargs_vus)
            )[0],
            [k.get("timeout") for k in kwargs_vus],
        ),
        (sys.executable, [DELAI_DU_POSTE_S]),
    )
    verifie(
        "un DELAI depasse nomme le PROFIL et non l outil",
        lambda: (
            "PROFIL"
            in (
                resolu_par_le_poste(
                    "npm",
                    shell="/bin/sh",
                    lance=poste(None, leve=subprocess.TimeoutExpired("sh", 10)),
                )[1]
                or ""
            )
        ),
        True,
    )
    verifie(
        "un shell du poste ABSENT se nomme",
        lambda: (
            "shell du poste"
            in (
                resolu_par_le_poste(
                    "npm", shell="/pas-de-shell", lance=poste(None, leve=FileNotFoundError())
                )[1]
                or ""
            )
        ),
        True,
    )
    verifie(
        "une cause qui tient au POSTE dit qu elle ne parle pas du diff",
        lambda: (
            "votre diff"
            in (resolu_par_le_poste("npm", shell="/bin/sh", lance=poste(None))[1] or "")
        ),
        True,
    )

    # ⟨LE CODE RENDU AU SHELL, et pas seulement le compte interne⟩ `pose` comptait juste et `__main__`
    # jetait le compte. Sans ce cas, remettre le `SystemExit(0)` ne ferait rougir personne : c est
    # exactement le trou que #5774 a trouve dans son propre harnais, et je le comble ici d avance.
    def code_au_shell(avec_outil: bool) -> int:
        arbre = pathlib.Path(tempfile.mkdtemp()) / "depot"
        (arbre / "scripts").mkdir(parents=True)
        for quoi in ("methode", "_commun"):
            shutil.copytree(RACINE / "scripts" / quoi, arbre / "scripts" / quoi)
        # `ruff` pose et aucun `pyproject.toml` : `a_faire` ne rend alors QUE l outil OpenSpec, ce qui
        # evite de creer un venv reel et son groupe - vingt secondes pour une question deja tranchee.
        (arbre / ".venv" / "bin").mkdir(parents=True)
        (arbre / ".venv" / "bin" / "ruff").touch()
        if avec_outil:
            (arbre / ".github" / "openspec" / "node_modules").mkdir(parents=True)
        # `SHELL=/bin/sh` : un poste qui ne declare rien, donc l echec sans lancer de vrai `npm`.
        return subprocess.run(
            [sys.executable, str(arbre / "scripts" / "methode" / pathlib.Path(__file__).name)],
            capture_output=True,
            check=False,
            env={**os.environ, "SHELL": "/bin/sh", "PATH": "/usr/bin:/bin"},
        ).returncode

    verifie("rien a poser : le SHELL recoit 0", lambda: code_au_shell(True), 0)
    verifie("un outil introuvable : le SHELL recoit 2, pas 0", lambda: code_au_shell(False), 2)

    # ⟨LE VOISINAGE⟩ Resoudre l outil ne suffit pas : son shebang reclame son interprete. Ces cas
    # sont nes de l echec du lot sur le poste reel, pas d une relecture.
    verifie(
        "le repertoire de l outil resolu est en TETE du PATH de l enfant",
        lambda: avec_le_voisinage("/opt/truc/bin/npm")["PATH"].split(os.pathsep)[0],
        "/opt/truc/bin",
    )
    verifie(
        "et le PATH existant est conserve DERRIERE, pas remplace",
        lambda: avec_le_voisinage("/opt/truc/bin/npm")["PATH"].split(os.pathsep)[1:],
        [c for c in os.environ.get("PATH", "").split(os.pathsep) if c],
    )
    verifie(
        "le reste de l environnement est conserve",
        lambda: avec_le_voisinage("/opt/truc/bin/npm").get("HOME"),
        os.environ.get("HOME"),
    )
    # Et le cas d INTEGRATION : l environnement est reellement transmis au lanceur de l outil resolu.
    envs_vus: list = []

    def poste_qui_note_l_env(declare):
        def faux(commande, **kwargs):
            if "-ic" in commande:
                return Rendu(0, declare + "\n")
            if commande[0] == "npm":
                raise FileNotFoundError(2, "No such file or directory", "npm")
            envs_vus.append(kwargs.get("env"))
            return Rendu(0)

        return faux

    verifie(
        "l outil resolu est lance AVEC son voisinage",
        lambda: (
            pose(nu, lance=poste_qui_note_l_env(sys.executable)),
            [(e or {}).get("PATH", "").split(os.pathsep)[0] for e in envs_vus if e is not None],
        ),
        (0, [str(pathlib.Path(sys.executable).parent)]),
    )

    verifie(
        "AUCUN gestionnaire de version n est nomme dans les causes",
        lambda: any(
            g in (resolu_par_le_poste("npm", shell="/bin/sh", lance=poste(None))[1] or "")
            for g in ("nvm", "asdf", "volta", "fnm", "nodenv")
        ),
        False,
    )

    print()
    return echecs()


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    # ⟨le compte, et non un zero de politesse⟩ Ce script rendait TOUJOURS 0, en se justifiant par
    # « jamais un motif de blocage : le crochet doit rendre la main quoi qu il arrive ». La raison est
    # bonne et ne couvre pas ce qu elle justifiait : le crochet se protege DEJA lui-meme, par
    # `|| true` suivi d un `exit 0`. La non-blocance est la propriete de l APPELANT, et elle est tenue.
    #
    # Le script n avait donc pas besoin de mentir sur son code, et ce mensonge coutait : `pose`
    # comptait ses echecs, et personne ne pouvait les lire. Meme forme que celle que #5774 vient de
    # corriger dans `verifie-specs-valides.py`, troisieme dispositif a jeter un compte juste sur le
    # pas de sa porte. Un `2` dit « je n ai pas pu », ce que le depot separe d un `1` (#5485).
    echecs = pose()
    # ⟨les modules N ENTRENT PAS dans ce code, et c est une decision⟩ Un module absent de
    # l interpreteur courant n est pas un echec du depot a poser : `modules_manquants` le DIT sans
    # rien poser, parce que choisir ou vit l interpreteur appartient a qui travaille. Mesure sur un
    # arbre neuf de ce poste : `/usr/bin/python3` en signale un, ce qui est l etat NORMAL puisque les
    # gardes se lancent par le venv. Les compter ferait sortir 2 a chaque creation d arbre, et un
    # refus qui crie sur du bon travail est un refus qu on apprend a ignorer (ADR 4002).
    dit_les_modules_manquants()
    raise SystemExit(2 if echecs else 0)
