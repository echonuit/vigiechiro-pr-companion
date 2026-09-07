#!/usr/bin/env python3
"""Aucun temoin de `verifie_scripts.py` n est decoratif (#4490, article A2).

`verifie_scripts.py` clot sur « les N scripts charges DETECTENT leur violation temoin ». La phrase
affirme plus que ce que la suite verifie : un temoin peut exister, s executer, passer, et ne rien
tenir. Celui du cliquet de longueur d ADR n affirmait que `isinstance(suspects(), list)`, si bien
qu un garde ayant cesse de detecter le passait (#4487). C etait le faux vert que le depot refuse
partout ailleurs, installe dans le dispositif meme qui le refuse.

**Le geste.** Pour chaque garde, neutraliser ses fonctions de detection, relancer la suite, et
exiger qu elle rougisse. C est l article A2 rendu mecanique : un garde est vu rouge sur sa propre
mutation, et la mutation se refait apres toute reecriture plutot qu a la prochaine cloture.

**La liste se DERIVE, elle ne s ecrit pas.** Les gardes viennent des appels `_charge("...")` de
`verifie_scripts.py`, et non d un glob. Un glob vieillit, et un garde neuf passerait au travers :
c est exactement le defaut que ce script existe pour attraper, et il serait cocasse de l y poser.

**Le sens de la panne est le bon.** Si la neutralisation cessait de fonctionner, le garde
continuerait de detecter, la suite resterait verte, et ce script crierait « temoin decoratif » a
tort. Un faux positif est bruyant ; c est le silence qu il fallait eviter.

**La cecite declaree.** La mutation ne remplace que les fonctions de MODULE non prefixees. Un temoin
qui n eprouverait qu une constante, une expression reguliere ou une classe survit sans etre
decoratif pour autant : ce script ne prononce donc rien sur ceux-la, et la liste des exemptions le
dit une par une.
"""

import ast
import contextlib
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, rapporte, sort_si_contrat_demande

ADR = "4490"
DOSSIER = pathlib.Path(__file__).resolve().parent
# Ce garde LANCE les scripts qu il eprouve : il ne se prend jamais lui-meme pour cible, sous peine
# de se rappeler sans fin. Meme barriere que `verifie_contrats_tiennent.py` (#5119).
MOI = pathlib.Path(__file__).name
SUITE = DOSSIER / "verifie_scripts.py"

# Ce qu on ajoute a la fin d un garde pour lui retirer sa detection, sans toucher a ce qui le decrit.
# `rapporte` est epargne : il vient de `_commun` et sert a rendre le verdict, il ne detecte rien.
NEUTRALISATION = """

import types as _t_mutation
for _nom_mutation, _val_mutation in list(globals().items()):
    if (isinstance(_val_mutation, _t_mutation.FunctionType)
            and not _nom_mutation.startswith("_")
            and _nom_mutation != "rapporte"):
        globals()[_nom_mutation] = (lambda *a, **k: [])
"""

# Les temoins qui n eprouvent AUCUNE fonction de module, et que la mutation ne peut donc pas tuer.
# Chacun est nomme avec ce qu il eprouve reellement, sinon cette liste deviendrait le tapis sous
# lequel on pousse les temoins faibles.
HORS_PORTEE = {
    "resserre_cliquets.py": "eprouve une expression reguliere et la PRESENCE d une fonction, pas son effet",
}


def charges(source: str) -> list[str]:
    """Les gardes qu une suite charge, litteral OU constante (issue #5134).

    Le motif precedent ne lisait que la forme LITTERALE de `_charge`. Le harnais ecrit aussi
    `_charge(ADR_2843)`, et ce garde ne voyait donc pas `2843-tiret-cadratin.py` : il le laissait
    hors de sa population SANS LE DIRE, et son compte paraissait sain.

    **C est le defaut que ce garde existe pour combattre, dans le garde lui-meme.** Un idiome n est
    pas une capacite, lecon de #5032, #5103, #5108 et #5128. La liaison se resout par l AST.
    """
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        return []
    # Les constantes de MODULE seules : ce qui est indente appartient a un cas, et un cas peut
    # nommer un garde qu il ne charge pas.
    const = {
        cible.id: noeud.value.value
        for noeud in arbre.body
        if isinstance(noeud, ast.Assign)
        and isinstance(noeud.value, ast.Constant)
        and isinstance(noeud.value.value, str)
        for cible in noeud.targets
        if isinstance(cible, ast.Name)
    }
    trouves = set()
    for noeud in ast.walk(arbre):
        if not (isinstance(noeud, ast.Call) and isinstance(noeud.func, ast.Name)):
            continue
        if noeud.func.id != "_charge" or not noeud.args:
            continue
        argument = noeud.args[0]
        if isinstance(argument, ast.Constant) and isinstance(argument.value, str):
            trouves.add(argument.value)
        elif isinstance(argument, ast.Name) and argument.id in const:
            trouves.add(const[argument.id])
    return sorted(trouves)


def gardes() -> list[str]:
    """Les gardes que la suite charge reellement."""
    return charges(SUITE.read_text(encoding="utf-8"))


# Ce qui, touche, fait muter TOUT le corpus : un garde peut cesser de rougir a cause d un module
# qu il importe, sans que son propre fichier ait bouge. La liste est courte et elle est LARGE a
# dessein - se tromper ici coute des minutes, l oublier coute un faux vert.
FONDS_PARTAGE = (
    "scripts/_commun/",
    "scripts/adr/_",
    "scripts/adr/verifie_temoins_non_decoratifs.py",
    "scripts/adr/verifie_scripts.py",
    "pyproject.toml",
)


def _git(*arguments: str) -> str:
    import subprocess

    sortie = subprocess.run(
        ["git", "-C", str(RACINE_DEPOT), *arguments], capture_output=True, text=True, check=False
    )
    return sortie.stdout if sortie.returncode == 0 else ""


def _modifies_hors_ci() -> list[str] | None:
    """Ce que ce diff touche HORS CI, ou `None` si `origin/main` n est pas la.

    `GITHUB_BASE_SHA` n existe que dans un atelier. En local il est absent, donc la portee de
    l ADR 5345 ne s appliquait JAMAIS : le banc mutait tout le corpus a chaque lancement de la
    batterie. Mesure du 2026-09-06 sur un diff d une seule ligne de documentation : 346,6 s sur
    les 514 s de la batterie, soit 67 %, pour un mecanisme cense n avoir rien a muter.

    Le repli reste du cote couteux quand il ne sait pas : sans `origin/main`, on mute tout.

    On compte les fichiers NEUFS et NON INDEXES comme `scripts/batterie.py` le fait : un garde
    qu on vient d ecrire n est pas encore suivi, et l oublier ferait sauter exactement la mutation
    qui devait le juger.
    """
    if _git("rev-parse", "--verify", "origin/main^{commit}").strip() == "":
        print(
            "`origin/main` est introuvable et aucune base n est fournie : le banc mute TOUT.",
            file=sys.stderr,
        )
        return None
    suivis = _git("diff", "--name-only", "origin/main").splitlines()
    neufs = _git("ls-files", "--others", "--exclude-standard").splitlines()
    en_cours = _git("diff", "--name-only").splitlines()
    return sorted({f for f in suivis + neufs + en_cours if f})


def _gardes_touches(modifies: list[str]) -> list[str] | None:
    """La portee que ces fichiers dessinent, ou `None` pour dire « mute tout »."""
    if not modifies:
        return None
    if any(m.startswith(FONDS_PARTAGE) for m in modifies):
        print(
            "Le fonds partage a bouge : le banc mute TOUT le corpus.",
            file=sys.stderr,
        )
        return None
    return sorted({pathlib.Path(m).name for m in modifies if m.startswith("scripts/adr/")})


def portee_du_diff(
    base: str | None = None,
    modifies: list[str] | None = None,
    hors_ci=None,
) -> list[str] | None:
    """Les gardes que CE diff touche, ou `None` pour dire « mute tout ».

    ## Pourquoi le banc peut se restreindre

    Il prouve qu un garde porte un auto-test NON DECORATIF. C est une propriete du **code du garde** :
    quand ce code n a pas change, elle rend le meme verdict que sur la base.

    Ce que le depot accepte donc de ne pas rejouer est etroit et se nomme : *la non-decorativite d un
    garde dont le code est identique a celui que la base a deja juge*.

    ## Pourquoi il se restreint quand meme rarement

    Un garde peut cesser de rougir a cause d un module PARTAGE qu il importe, sans que son fichier ait
    bouge. Toucher `FONDS_PARTAGE` fait donc tout muter.

    Et **sans base, on mute tout** : le defaut penche du cote couteux, jamais du cote muet. C est le
    meme parti que les portees de job du chantier #5294.

    ## Pourquoi ce n est pas un confort

    Le banc neutralise chaque garde puis le relance : son cout croit LINEAIREMENT avec le corpus. Il
    est passe de 4,67 a 6,87 min entre le 5 et le 6 septembre, parce que le chantier #5294 y a ajoute
    quatre gardes. Sans cette portee, `lint` ralentit a chaque garde ecrit - c est-a-dire que mieux
    garder le depot le rendrait indefiniment plus lent.
    """
    # ⟨couture⟩ `modifies` est injectable : sans elle, cette fonction irait chercher ses propres
    # donnees et aucun cas ne pourrait lui en fabriquer. C est le patron d ADR 3624, deja pose par
    # `mesure_duree_portail.py`.
    if modifies is None:
        base = base or os.environ.get("GITHUB_BASE_SHA") or ""
        if not base:
            locaux = (hors_ci or _modifies_hors_ci)()
            return None if locaux is None else _gardes_touches(locaux)

        # ⟨le commit de base n est PAS la⟩ Le checkout est a profondeur 1 : le SHA que la forge
        # fournit designe un commit que ce clone ne porte pas, et `git diff` echoue. Il faut donc le
        # rapatrier d abord, comme `_portee.py` le fait depuis #4440.
        #
        # Sans ce fetch, `git diff` rendait une chaine vide, que le code d en dessous confondait avec
        # « le diff est vide ». Le banc mutait donc TOUT en croyant avoir mesure une portee - et le
        # mecanisme de #5345 est reste inerte deux demandes durant, en vert.
        _git("fetch", "--no-tags", "--depth=1", "origin", base)
        if (
            _git("cat-file", "-e", f"{base}^{{commit}}") == ""
            and _git("rev-parse", "--verify", f"{base}^{{commit}}").strip() == ""
        ):
            print(
                f"Le commit de base {base[:9]} reste introuvable apres fetch : le banc mute TOUT.",
                file=sys.stderr,
            )
            return None

        sortie = _git("diff", "--name-only", base, "HEAD")
        # ⟨git n a pas su repondre, ou le diff est vide : ce n est PAS la meme chose⟩ La premiere est
        # un accident, la seconde un fait. Les confondre est ce qui a rendu ce mecanisme inerte : une
        # chaine vide passait pour « rien n a change », et le repli suivant mutait tout sans le dire.
        if sortie == "":
            if _git("rev-list", "--count", f"{base}..HEAD").strip() in ("", "0"):
                print(
                    "Le diff est vide : le banc mute TOUT, faute de savoir pourquoi.",
                    file=sys.stderr,
                )
            else:
                print("`git diff` n a pas su repondre : le banc mute TOUT.", file=sys.stderr)
            return None
        modifies = sortie.splitlines()
    return _gardes_touches(modifies)


@contextlib.contextmanager
def arbre_jetable():
    """Un depot ou muter sans toucher a celui-ci (#4700, #4686).

    `scripts/` est COPIE, tout le reste est lie. Les gardes lisent `src/` et `dev-docs/` sans
    jamais y ecrire - mesure faite sur les 36 gardes, leurs seules ecritures allant dans un
    `TemporaryDirectory` a eux. Et tous resolvent leurs chemins depuis leur propre emplacement,
    par `parents[1]` ou `parents[2]`, donc l arbre copie leur suffit.

    Le poids decide de la forme : `scripts/` pese 1,1 Mo quand le depot en fait 1,4 Go hors
    `target/` et `.git`. Copier le tout serait impensable ; copier `scripts/` ne coute rien, et se
    fait UNE fois pour toutes les mutations.

    **`.git` n est PAS lie, et la mesure le justifie.** Le harnais eprouve chaque garde sur un
    temoin synthetique dans un `TemporaryDirectory` a lui, jamais sur le corpus versionne : son
    verdict ne depend donc pas de git. Mesure du 2026-08-29, harnais lance dans les deux arbres :

        .git absent :  18,2 s, code 0, 104 lignes
        .git lie    : 229,7 s, code 0, 104 lignes   -- sorties IDENTIQUES

    Douze fois plus vite pour le meme verdict. Ce n est donc pas un garde qui regarde moins ; c est
    un garde qui ne paie plus un acces dont il n a pas l usage.

    **C est le remede qui supprime la classe, pas ses instances.** Muter la source laissait des
    gardes neutralisees des qu un signal coupait le processus : le `finally` couvre l exception,
    pas le `SIGTERM`. Cinq gardes ont ete retrouves ainsi en deux jours, sans que rien ne le dise.
    """
    racine = DOSSIER.parents[1]
    with tempfile.TemporaryDirectory(prefix="vc-temoins-") as tmp:
        faux = pathlib.Path(tmp) / "depot"
        faux.mkdir()
        shutil.copytree(racine / "scripts", faux / "scripts", symlinks=True)
        for entree in racine.iterdir():
            if entree.name not in {"scripts", ".git"}:
                (faux / entree.name).symlink_to(entree)
        yield faux


def suite_rougit(nom: str, faux: pathlib.Path) -> tuple[str, str]:
    """La suite rougit-elle quand ce garde perd sa detection ?

    La mutation porte sur l arbre JETABLE. Interrompre ce script ne peut donc plus laisser un
    garde neutralise dans le depot : il n y a jamais rien eu a restaurer.
    """
    cible = faux / "scripts" / "adr" / nom
    original = cible.read_text(encoding="utf-8")
    try:
        cible.write_text(original + NEUTRALISATION, encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(faux / "scripts" / "adr" / "verifie_scripts.py")],
            capture_output=True,
            cwd=faux,
            check=False,
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    return classe_le_rouge(rendu)


TRACE = "Traceback (most recent call last)"


def classe_le_rouge(rendu) -> tuple[str, str]:
    """Ce que vaut le rouge d une mutation : « tient », « non concluant » ou « decoratif ».

    TROIS valeurs et non deux (ADR 5257). La neutralisation rend `[]` pour toute fonction : un garde
    dont une fonction rend un tuple ou un chemin PLANTE au lieu d assertir, et ce rouge-la ne prouve
    rien (ADR 4918). Le compter comme une reussite reviendrait a annoncer 43 gardes eprouves quand il
    y en a 39.

    Mesure du 2026-09-05 sur les deux chemins de ce banc : 8 tiennent et 2 ne concluent pas sur le
    chemin de l auto-test propre, 31 et 2 sur celui de la suite. C est le banc le plus solide des
    trois, et il annoncait quand meme quatre gardes de plus qu il n en prouvait.
    """
    if rendu.returncode == 0:
        return "decoratif", "reste vert sans sa detection"
    erreur = (
        rendu.stderr.decode("utf-8", "replace")
        if isinstance(rendu.stderr, bytes)
        else (rendu.stderr or "")
    )
    if TRACE in erreur:
        lignes = [l for l in erreur.strip().splitlines() if l and not l.startswith(" ")]
        return "non concluant", (lignes[-1] if lignes else "trace illisible")[:110]
    return "tient", ""


MARQUE_MAIN = 'if __name__ == "__main__":'


def porte_son_auto_test(nom: str) -> bool:
    """Ce garde dispatche-t-il `--auto-test` dans son CODE, et non dans sa seule prose ?

    Barriere de SURETE : lancer un script qui ne porte pas cette branche ne coute pas du temps, il
    IGNORE l argument et FAIT SON TRAVAIL. Un generateur reecrirait des fichiers.
    """
    try:
        arbre = ast.parse((DOSSIER / nom).read_text(encoding="utf-8"))
    except (SyntaxError, OSError):
        return False
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


def neutralise_avant_main(source: str) -> str | None:
    """La neutralisation posee AVANT le bloc `__main__`, ou rien si le fichier n en a pas.

    **C est toute la difference entre les deux moities, et elle a failli rendre huit faux suspects.**
    `suite_rougit` mute un garde que le harnais CHARGE comme module : `__main__` est saute, le code
    ajoute en fin de fichier s execute, les fonctions sont remplacees. Lance en SCRIPT, le meme
    fichier fait l inverse : le bloc `__main__` s execute d abord et `sys.exit` part avant que la fin
    du fichier ne soit atteinte. La neutralisation en queue ne s applique alors JAMAIS, et l auto-test
    passe en voyant les vraies fonctions - ce qui se lit « ce temoin ne prouve rien » alors qu il n a
    rien eu a prouver. Verifie sur un fichier temoin de six lignes avant d etre corrige (#5134).
    """
    ligne = ligne_du_point_d_entree(source)
    if ligne is None:
        return None
    lignes = source.splitlines(keepends=True)
    return "".join(lignes[: ligne - 1]) + NEUTRALISATION + "\n" + "".join(lignes[ligne - 1 :])


def ligne_du_point_d_entree(source: str) -> int | None:
    """La ligne du `if __name__ == "__main__":` de MODULE, lue dans l ARBRE et non cherchee.

    Ce fichier cherchait `MARQUE_MAIN` par `partition`, donc la PREMIERE occurrence. Une chaine
    cherchee a exactement le meme angle mort qu une expression reguliere : ni l une ni l autre ne
    distingue le code d un litteral. Des qu un garde porte `if __name__` dans une chaine - et c est
    le cas de tout banc dont l auto-test ecrit de faux gardes - la neutralisation s inserait DANS le
    litteral, ou elle ne neutralisait rien, et ce banc declarait « decoratif » un garde qui ne
    l etait pas.

    Mesure du 2026-09-05 sur un faux garde de douze lignes : la neutralisation atterrissait ligne 4
    au lieu de la ligne 11. Le cas `un point d entree cache dans une chaine ne trompe pas` de
    l auto-test rejoue le piege, et il est vu ROUGE avant cette correction (#5263).

    L arbre ne voit que les `if` de niveau module. C est la difference entre reconnaitre une forme et
    demander a la chose ([ADR 5102]).
    """
    for noeud in ast.parse(source).body:
        cible = getattr(noeud, "test", None)
        if (
            isinstance(noeud, ast.If)
            and isinstance(cible, ast.Compare)
            and isinstance(cible.left, ast.Name)
            and cible.left.id == "__name__"
        ):
            return noeud.lineno
    return None


def auto_test_rougit(nom: str, faux: pathlib.Path) -> tuple[str, str]:
    """L auto-test PROPRE de ce garde rougit-il quand sa detection est neutralisee ?

    L autre moitie du dispositif (issue #5134). `suite_rougit` eprouve les gardes dont le temoin vit
    dans le harnais ; celui-ci eprouve ceux qui portent leur propre `--auto-test`, que rien ne mutait.
    """
    cible = faux / "scripts" / "adr" / nom
    original = cible.read_text(encoding="utf-8")
    mute = neutralise_avant_main(original)
    if mute is None:
        return "non concluant", "aucun point d entree ou inserer la neutralisation"
    try:
        cible.write_text(mute, encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(cible), "--auto-test"],
            capture_output=True,
            cwd=faux,
            check=False,
            timeout=300,
        )
    except subprocess.TimeoutExpired:
        return "non concluant", "butoir de 300 s atteint"
    finally:
        cible.write_text(original, encoding="utf-8")
    return classe_le_rouge(rendu)


def autonomes(noms: list[str] | None = None) -> list[str]:
    """Les points d entree que le harnais ne charge PAS, et qui portent leur propre `--auto-test`.

    Population DERIVEE et non enumeree : un garde neuf y entre tout seul. C est la lecon que
    `gardes()` avait apprise avant d etre elle-meme prise en defaut par une constante (#5134).
    """
    par_le_harnais = set(gardes())
    if noms is not None:
        # Le meme filtre que la branche sans portee, et pour la meme raison : un garde que la suite
        # charge appartient a l AUTRE moitie. L omettre ici mettait dix-huit gardes dans les deux,
        # et la partition ne tenait que tant que personne ne passait de portee (#5484).
        return [
            n
            for n in noms
            if n not in HORS_PORTEE and n not in par_le_harnais and porte_son_auto_test(n)
        ]
    return sorted(
        f.name
        for f in DOSSIER.glob("*.py")
        if not f.name.startswith("_")
        and f.name not in par_le_harnais
        and f.name not in HORS_PORTEE
        and f.name != MOI
        and porte_son_auto_test(f.name)
    )


def mutes(noms: list[str] | None = None) -> list[str]:
    """Les gardes que ce garde MUTE reellement, extraits pour que `lus` les compte (issue #5007).

    L unite n est ni le fichier ni la ligne : c est le GARDE. `HORS_PORTEE` et les absents ne sont
    jamais neutralises, donc jamais lus. Les compter gonflerait le nombre d une population que ce
    garde n eprouve pas.

    **Et un garde que la SUITE ne charge pas n entre pas ici.** Ce chemin neutralise le garde puis
    joue `verifie_scripts.py` : si la suite ignore le garde, elle reste verte quoi qu il arrive, et
    le verdict « decoratif » ne dit rien de lui. Ces gardes ont leur propre moitie, `autonomes`, qui
    joue leur `--auto-test`. Sans portee la partition tenait, parce que la liste par defaut EST celle
    du harnais ; avec une portee elle etait perdue, et les dix gardes autonomes du depot tombaient
    dans les deux moities. Un diff qui en touchait un recevait donc un refus faux (#5484).
    """
    par_le_harnais = set(gardes())
    return [
        nom
        for nom in (noms if noms is not None else gardes())
        if nom not in HORS_PORTEE and (DOSSIER / nom).is_file() and nom in par_le_harnais
    ]


def suspects(noms: list[str] | None = None) -> tuple[list[str], list[str]]:
    """DEUX listes : les decoratifs, et les NON CONCLUANTS.

    La seconde est ce que l ADR 5257 ajoute. Un garde qui PLANTE sous mutation n a rien prouve, mais
    il n a rien montre de faux non plus : il ne fait donc pas refuser, il se compte et se nomme.
    """
    decoratifs, non_concluants = [], []
    with arbre_jetable() as faux:
        for nom in mutes(noms):
            verdict, cause = suite_rougit(nom, faux)
            if verdict == "decoratif":
                decoratifs.append(f"{nom}  la suite reste verte, sa detection neutralisee")
            elif verdict == "non concluant":
                non_concluants.append(f"{nom}  {cause}")
        # L autre moitie : les gardes que le HARNAIS ne charge pas, dont le temoin est leur propre
        # `--auto-test`. Sans ce passage, ce temoin n etait verifie qu EXISTANT (#5134).
        for nom in autonomes(noms):
            verdict, cause = auto_test_rougit(nom, faux)
            if verdict == "decoratif":
                decoratifs.append(f"{nom}  son --auto-test reste vert, sa detection neutralisee")
            elif verdict == "non concluant":
                non_concluants.append(f"{nom}  {cause}")
    return decoratifs, non_concluants


CAS_DE_PORTEE = (
    # (libelle, fichiers modifies, attendu) - `None` veut dire « mute TOUT ».
    (
        "un garde touche est le seul mute",
        ["scripts/adr/2843-tiret-cadratin.py"],
        ["2843-tiret-cadratin.py"],
    ),
    (
        "deux gardes touches, deux mutes",
        ["scripts/adr/2843-tiret-cadratin.py", "scripts/adr/4477-longueur-des-adr.py"],
        ["2843-tiret-cadratin.py", "4477-longueur-des-adr.py"],
    ),
    # Le CONTROLE NEGATIF du dispositif : le fonds partage fait tout muter. Sans lui, un garde
    # cesserait de rougir a cause d un module qu il importe, sans que son fichier ait bouge.
    ("le fonds partage fait tout muter", ["scripts/_commun/__init__.py"], None),
    ("le banc lui-meme fait tout muter", ["scripts/adr/verifie_temoins_non_decoratifs.py"], None),
    ("un diff sans garde ne mute rien", ["dev-docs/decisions/1.md"], []),
    ("un diff vide fait tout muter, faute de savoir pourquoi il est vide", [], None),
)


def verdict_sans_objet(portee: list[str] | None, corpus: int) -> tuple[int, str] | None:
    """Que faire quand la portee est VIDE : conclure, ou refuser ? Et pourquoi.

    ⟨vide par DECISION, vide par ACCIDENT : la nuance decide de ce qu on apprend⟩

    `rapporte` REFUSE sur `lus=0`, et il a raison : un garde dont la population s est videe en
    silence - un chemin qui a bouge, un motif qui ne s apparie plus - reste vert sans juger.

    Mais une portee vide n est pas cet accident-la. Elle dit « ce diff ne touche aucun garde », ce
    qui est un fait lisible, pas une cecite. Confondre les deux ferait refuser chaque demande
    documentaire, et apprendrait a passer outre - ce qui coute bien plus cher que la minute gagnee.

    Le banc conclut donc, et il ECRIT pourquoi : c est la forme de l ADR 2748, un silence explicite
    et non une absence. L accident, lui, reste refuse : si le CORPUS ENTIER est vide, le harnais ne
    trouve plus rien, et cela n a aucun rapport avec le diff.

    Rend `None` quand il n y a rien de special a faire, c est-a-dire quand le banc doit muter.
    """
    if portee is None or portee:
        return None
    if not corpus:
        return 1, (
            "ÉCHEC : le corpus entier est vide. Ce n est pas la portee du diff qui est en cause,\n"
            "c est le harnais : il ne trouve plus aucun garde a muter."
        )
    return 0, (
        f"Sans objet : ce diff ne touche aucun des {corpus} gardes du banc. Leur code est\n"
        "identique a celui que la base a deja juge, donc leur non-decorativite aussi.\n"
        "Le banc s execute quand meme, et le dit : un silence explicite n est pas une absence."
    )


def _auto_test_de_portee() -> int:
    echecs = 0
    # Le silence explicite, et son bord : vide par DECISION conclut, vide par ACCIDENT refuse.
    for libelle, portee, corpus, attendu in (
        ("une portee vide sur un corpus plein CONCLUT", [], 46, 0),
        ("une portee vide sur un corpus VIDE refuse", [], 0, 1),
        ("une portee pleine ne declenche rien", ["x.py"], 46, None),
        ("muter tout ne declenche rien non plus", None, 46, None),
    ):
        rendu = verdict_sans_objet(portee, corpus)
        obtenu = None if rendu is None else rendu[0]
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs += 1
    for libelle, modifies, attendu in CAS_DE_PORTEE:
        obtenu = portee_du_diff(modifies=modifies)
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs += 1

    # ⟨le repli HORS CI⟩ `GITHUB_BASE_SHA` est POSE dans l atelier `temoins` : sans ce retrait, ces
    # cas emprunteraient la branche de la forge et ne prouveraient rien la ou ils comptent.
    ancien = os.environ.pop("GITHUB_BASE_SHA", None)
    try:
        for libelle, locaux, attendu in (
            (
                "hors CI, le diff local restreint le banc",
                ["scripts/adr/x.py", "docs/a.md"],
                ["x.py"],
            ),
            ("hors CI, un diff sans garde ADR rend une portee VIDE", ["docs/a.md"], []),
            ("hors CI, `origin/main` introuvable fait muter TOUT", None, None),
            (
                "hors CI, toucher le fonds partage fait muter TOUT",
                [FONDS_PARTAGE[0] + "x.py"],
                None,
            ),
        ):
            obtenu = portee_du_diff(hors_ci=lambda a=locaux: a)
            if obtenu == attendu:
                print(f"  ✔ {libelle}")
            else:
                print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
                echecs += 1
    finally:
        if ancien is not None:
            os.environ["GITHUB_BASE_SHA"] = ancien

    # ⟨les deux moities ne se recouvrent jamais⟩ Aucun cas n eprouvait la PARTITION elle-meme : les
    # cas ci-dessus verifient QUELS gardes une portee retient, jamais par quel chemin ils passent.
    # Le cas se derive du DOSSIER, et non de l une des deux fonctions qu il eprouve : le batir
    # depuis `autonomes()` le rendrait incapable de voir cette fonction se tromper (#5491).
    tous = sorted(
        f.name for f in DOSSIER.glob("*.py") if not f.name.startswith("_") and f.name != MOI
    )
    chevauchement = sorted(set(mutes(tous)) & set(autonomes(tous)))
    if chevauchement:
        print(
            f"  ✘ sous portee, {len(chevauchement)} garde(s) dans les DEUX moities : {chevauchement[:3]}"
        )
        echecs += 1
    else:
        print("  ✔ sous portee, les deux moities restent disjointes")
    return echecs


def auto_test() -> int:
    """Le mecanisme se prouve dans les DEUX sens, sinon il ne prouve rien.

    Un script qui ne saurait que dire « tout va bien » passerait le premier sens tout seul.
    """
    echecs = 0

    def verifie(libelle: str, obtenu, attendu) -> None:
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    print("Auto-test du garde des temoins non decoratifs (#4490) :")
    # 1. La liste se derive de la suite, et elle n est pas vide.
    verifie("la liste des gardes vient des appels `_charge` de la suite", len(gardes()) > 15, True)
    verifie("elle ne contient pas la suite elle-meme", SUITE.name in gardes(), False)

    # --- LA SECONDE MOITIE (issue #5134) ---
    #
    # La liaison de `_charge` se resout, litteral ET constante. Sans cela `2843-tiret-cadratin.py`
    # restait hors population sans que rien ne le dise : le garde qui refuse les temoins decoratifs
    # etait lui-meme aveugle, pour la raison qu il existe pour combattre.
    verifie(
        "un _charge par CONSTANTE est vu comme un litteral",
        charges('ADR_X = "g.py"\n_charge(ADR_X)\n'),
        ["g.py"],
    )
    verifie("et le litteral l est toujours", charges('_charge("h.py")\n'), ["h.py"])
    verifie("2843 est desormais dans la population", "2843-tiret-cadratin.py" in gardes(), True)

    # La NEUTRALISATION se pose AVANT le bloc `__main__`, sinon elle ne s applique jamais quand le
    # fichier est lance en SCRIPT : `sys.exit` part avant la fin du fichier. La version qui ajoutait
    # en queue rendait huit faux suspects, et son erreur ressemblait a une trouvaille.
    avec_main = 'def f():\n    return [1]\n\n\nif __name__ == "__main__":\n    print(f())\n'
    mute = neutralise_avant_main(avec_main)
    verifie(
        "la neutralisation precede le bloc __main__",
        mute.index("_t_mutation") < mute.index(MARQUE_MAIN),
        True,
    )

    # #5264. Le verdict a TROIS valeurs : un garde qui plante n est pas eprouve. Sans ces cas, ce
    # banc comptait ces rouges-la parmi les reussites et annoncait 43 gardes tenus quand il y en a 39.
    class _RenduFeint:
        def __init__(self, code, err):
            self.returncode, self.stderr = code, err

    verifie(
        "un rouge SANS trace tient",
        classe_le_rouge(_RenduFeint(1, b"ECHEC : la detection a disparu\n"))[0],
        "tient",
    )
    verifie(
        "un rouge AVEC trace ne conclut pas",
        classe_le_rouge(_RenduFeint(1, b"Traceback (most recent call last)\nValueError: x\n"))[0],
        "non concluant",
    )
    verifie(
        "et sa cause est rendue",
        "ValueError"
        in classe_le_rouge(_RenduFeint(1, b"Traceback (most recent call last)\nValueError: x\n"))[
            1
        ],
        True,
    )
    verifie("un vert est decoratif", classe_le_rouge(_RenduFeint(0, b""))[0], "decoratif")

    # #5263. Le piege : un garde qui porte `if __name__` dans une CHAINE avant de le porter pour de
    # vrai. `partition` prenait la PREMIERE occurrence, la neutralisation atterrissait dans le
    # litteral, et ce banc declarait « decoratif » un garde qui ne l etait pas. Mesure d alors :
    # ligne 4 au lieu de la ligne 11. Ce cas est vu ROUGE sans la lecture par l arbre.
    piege = (
        'MODELE = """\nif __name__ == "__main__":\n    print("un modele")\n"""\n\n\n'
        "def f():\n    return [1]\n\n\n"
        'if __name__ == "__main__":\n    print(f())\n'
    )
    verifie(
        "un point d entree cache dans une chaine ne trompe pas", ligne_du_point_d_entree(piege), 11
    )
    mute_piege = neutralise_avant_main(piege)
    verifie(
        "et la neutralisation se pose APRES le litteral",
        mute_piege.index("_t_mutation") > mute_piege.index('MODELE = """'),
        True,
    )
    verifie(
        "et AVANT le vrai point d entree",
        mute_piege.index("_t_mutation") < mute_piege.rindex(MARQUE_MAIN),
        True,
    )
    verifie(
        "un fichier sans bloc __main__ ne se mute pas",
        neutralise_avant_main("def f():\n    return []\n"),
        None,
    )

    # La population des autonomes se DERIVE, et ne recoupe pas celle du harnais.
    verifie("ce garde ne s eprouve jamais lui-meme", MOI in autonomes(), False)
    verifie("aucun garde n est compte deux fois", set(autonomes()) & set(gardes()), set())
    verifie("une mention en prose ne vaut pas dispatch", porte_son_auto_test("_commun.py"), False)
    with arbre_jetable() as faux:
        # 2. Le sens POSITIF : un garde dont le temoin tient fait bien rougir la suite une fois mute.
        #    `2843-tiret-cadratin.py` sert de reference : son temoin compte des cadratins.
        verifie(
            "un garde au temoin solide fait rougir la suite sous mutation",
            suite_rougit("2843-tiret-cadratin.py", faux)[0],
            "tient",
        )
        # 3. Le sens NEGATIF : sans mutation, la suite est verte. Sans ce cas, un script qui rendrait
        #    TOUJOURS `True` passerait le cas precedent et n aurait rien prouve.
        rendu = subprocess.run(
            [sys.executable, str(faux / "scripts" / "adr" / "verifie_scripts.py")],
            capture_output=True,
            cwd=faux,
            check=False,
        )
        verifie("sans mutation, la suite est verte", rendu.returncode, 0)
        # 4. Ce que ce lot prouve (#4700) : muter n a PAS touche le depot. Sans ce cas, une
        #    reecriture qui reviendrait a muter la source passerait les trois precedents.
        vraie = (DOSSIER / "2843-tiret-cadratin.py").read_text(encoding="utf-8")
        verifie("la mutation n a pas touche le depot", NEUTRALISATION.strip() in vraie, False)
        # 5. Et l arbre jetable est bien un AUTRE arbre, sinon le quatrieme cas ne prouve rien.
        verifie(
            "l arbre mute n est pas le depot", faux.resolve() == DOSSIER.parents[1].resolve(), False
        )
        # 6. Il porte pourtant de quoi juger : `src/` est lie, donc lisible.
        verifie("l arbre jetable voit les sources du depot", (faux / "src").exists(), True)
        # 7. La seconde moitie, dans les DEUX sens elle aussi. `verifie_corpus_declare.py` porte
        #    son propre `--auto-test` et n est pas charge par le harnais : c est un des huit que
        #    rien n eprouvait avant #5134.
        verifie(
            "un auto-test propre qui tient fait rougir sous mutation",
            auto_test_rougit("verifie_corpus_declare.py", faux)[0],
            "tient",
        )
        # 8. Et le sens negatif : sans mutation, ce meme auto-test conclut.
        sain = subprocess.run(
            [
                sys.executable,
                str(faux / "scripts" / "adr" / "verifie_corpus_declare.py"),
                "--auto-test",
            ],
            capture_output=True,
            cwd=faux,
            check=False,
        )
        verifie("sans mutation, son auto-test est vert", sain.returncode, 0)
    # La PORTEE fait partie du mecanisme depuis #5345 : ses cas tournent ici, sinon ils ne
    # tourneraient nulle part et seraient decoratifs par construction.
    echecs += _auto_test_de_portee()
    return echecs


CONTRAT = {
    "geste": "temoin decoratif : rien ne rougit quand la detection est neutralisee",
    # « de scripts/adr » et « du MEME dossier » : precise a la clote de #5218, ou le contrat
    # annoncait une population plus large que ce que `candidats()` globbe, c est-a-dire DOSSIER.
    # Les gardes de .github/scripts portent eux aussi leur propre --auto-test et n entraient pas
    # ici ; ils ont desormais leur banc, `.github/scripts/temoins_de_ci_non_decoratifs.py` (#5254).
    "population": "les gardes de scripts/adr que verifie_scripts.py charge, ET ceux du MEME "
    "dossier qui portent leur propre --auto-test sans etre charges par lui",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_temoins_non_decoratifs.py --auto-test",
    "decision": "ADR 4490",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    # ⟨portee⟩ Le banc ne mute que les gardes que le diff touche, ou TOUT quand il ne sait pas.
    portee = portee_du_diff()
    if portee is not None:
        print(
            f"Portee du diff : {len(portee)} garde(s) touche(s) sur {len(gardes())}. "
            "Les autres portent un code identique a celui que la base a deja juge.",
            file=sys.stderr,
        )
    sans_objet = verdict_sans_objet(portee, len(mutes()) + len(autonomes()))
    if sans_objet is not None:
        code, message = sans_objet
        print(message, file=sys.stderr if code else sys.stdout)
        sys.exit(code)

    decoratifs, non_concluants = suspects(portee)
    # ⟨`lus` compte ce qui a ETE MUTE, jamais le corpus⟩ Cette ligne appelait `mutes()` et
    # `autonomes()` SANS la portee : elle aurait annonce quarante-sept gardes lus quand le banc en
    # avait joue trois. Un `lus` qui depasse ce qu on a lu est le faux vert que l ADR 5007 refuse, et
    # c est exactement le defaut que ce chantier combat ailleurs.
    population = len(mutes(portee)) + len(autonomes(portee))
    # Les NON CONCLUANTS sortent AVANT le verdict, et separement (ADR 5257) : ils ne font pas
    # refuser, parce que refuser dessus reviendrait a refuser sur ce que ce banc n a pas su lire.
    # Les compter parmi les eprouves annoncerait toute la population comme tenue.
    for l in non_concluants:
        print(f"NON CONCLUANT : {l}", file=sys.stderr)
    if non_concluants:
        print(
            f"\n{len(non_concluants)} garde(s) ont PLANTE sous mutation au lieu d assertir : "
            "ils n ont rien prouve, et un rouge pour la mauvaise raison ne prouve rien (ADR 4918). "
            f"{population - len(non_concluants) - len(decoratifs)} tiennent reellement sur "
            f"{population}.",
            file=sys.stderr,
        )
    sys.exit(
        rapporte(
            ADR,
            "temoin decoratif : la suite reste verte sans detection",
            decoratifs,
            # Les DEUX populations : n en compter qu une ferait mentir `lus` de huit unites,
            # ce que l ADR 5007 refuse.
            lus=population,
        )
    )
