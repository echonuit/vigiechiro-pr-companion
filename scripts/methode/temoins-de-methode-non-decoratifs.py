#!/usr/bin/env python3
"""Aucun auto-test des gardes de METHODE n est decoratif (#4760, article A2).

`scripts/adr/verifie_temoins_non_decoratifs.py` rend l article A2 mecanique : il neutralise chaque
detecteur et exige que la suite rougisse. Il ne couvre que `scripts/adr/`. Les gardes de
`scripts/methode/`, dont onze sont bloquants dans `lint.yml`, avaient chacun un `--auto-test` que
RIEN n obligeait a detecter quoi que ce soit : un motif elargi jusqu a tout accepter, un cas retire
« parce qu il genait », et le vert reste vert.

**La transposition n est pas celle qu on croit, et la mesure l a dit.** Les gardes d ADR sont mutes
en AJOUTANT la neutralisation en fin de fichier, parce que le harnais les IMPORTE : tout le fichier
s execute, puis les fonctions sont appelees. Un garde de methode, lui, se lance en `--auto-test` :
son `raise SystemExit` part AVANT d atteindre une neutralisation ajoutee a la fin, qui n agit donc
jamais. Cinq essais ont rendu « decoratif » pour cinq gardes dont deux avaient ete vus rougir sur un
vrai defaut le meme jour. La neutralisation s INSERE donc avant le point d entree.

**Il refuse plutot que de sauter.** Six gardes du corpus n ont aucun `if __name__` : leur corps
s execute au niveau du module, et aucune insertion sure n existe. Les passer en silence rendrait
vert sur une couverture partielle, ce qui est le defaut que ce garde traite. Ils sont donc NOMMES,
et leur sort est une decision a part - voir l issue citee dans le message de refus.

**Le corpus se derive de `lint.yml`**, et non d un glob : ce sont les gardes que la CI lance
vraiment. Un glob vieillit, et un script d appoint pose dans le dossier passerait pour un garde.

Usage :
    python3 scripts/methode/temoins-de-methode-non-decoratifs.py
    python3 scripts/methode/temoins-de-methode-non-decoratifs.py --auto-test
"""

import ast
import contextlib
import io
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import MARQUE_CAUSE, MARQUE_GESTE, cas_d_auto_test, sort_si_contrat_demande
from _commun.mutation import neutralisation

ATELIER = RACINE / ".github" / "workflows" / "lint.yml"
# ⟨l ancre negative, et elle n est pas decorative⟩ Sans elle, le motif attrape la QUEUE des chemins
# `.github/scripts/...` et le corpus passe de 26 a 71. Mesure du 2026-09-07 en ecrivant #5397.
#
# ⟨`scripts/adr` est exclu⟩ Il a son propre banc, `verifie_temoins_non_decoratifs.py`. Deux bancs
# mutant le meme garde compteraient deux fois, et la somme cesserait de valoir la population.
LANCE = re.compile(r"(?<![\w./-])scripts/(?!adr/)([a-z0-9_/-]+\.py)")

# ⟨ce banc est dans le corpus qu il mute, et c est une DECISION⟩ L ADR 4770 l ecrit : « le garde
# est dans son propre corpus, et c est sain ». Il y entre comme tout autre garde, par la derivation :
# `lint.yml` le lance et il declare un `CONTRAT`. Jusqu a #5550 rien dans ce fichier ne le disait, si
# bien qu un atelier modifie l aurait fait sortir ou entrer sans un mot.
#
# ⟨pourquoi il peut s y trouver⟩ Son `--auto-test` ne rappelle pas `suspects()` : il ne mute que de
# faux gardes ecrits dans un bac. Se muter ne le fait donc pas se relancer sur tout le corpus. C est
# SA raison ; le banc de CI s exclut et le banc des ADR ne s inclut que par une moitie, chacun pour
# la sienne.
#
# ⟨la forme est celle du corpus⟩ `corpus()` rend des chemins relatifs a `scripts/`. Le nom nu n y
# est jamais, et le comparer rendrait « absent » pour une raison de forme : les trois bancs stockent
# trois formes (nom nu chez les ADR, chemin relatif ici, chemin absolu en CI). La constante se
# derive donc du chemin du fichier, sous la forme que `corpus()` emploie.
MOI = pathlib.Path(__file__).resolve().relative_to(RACINE / "scripts").as_posix()

# Ce qu on insere pour retirer sa detection a un garde, sans toucher a ce qui le decrit.
#
# **La fonction d auto-test est EPARGNEE, et c est ce qui rend la mesure honnete.** La neutraliser
# ferait rougir le garde trivialement - non parce qu il a cesse de detecter, mais parce que son
# point d entree rend une liste au lieu d un entier. Deux des sept gardes du corpus nomment la leur
# `auto_test`, sans souligne : sans cette exemption, leur verdict ne voulait rien dire.
#
# L exemption se DERIVE du nom, elle ne s enumere pas : toute fonction dont le nom porte « auto »
# et « test ». Une liste aurait vieilli au premier garde neuf, comme trois listes tenues a la main
# l ont fait dans ce depot.


def declare_un_contrat(chemin: pathlib.Path) -> bool:
    """Ce fichier declare-t-il un `CONTRAT` ? Sinon ce n est pas un garde.

    La regle est celle que `verifie-batterie-locale.py` porte deja : « un script qui ne declare
    aucun CONTRAT n est pas un garde ». `scripts/graphify/rebuild.py` reconstruit un graphe et
    `scripts/mkdocs/bandeau_adr.py` engendre un bandeau : ils PRODUISENT, ils ne jugent pas, et
    `lint.yml` les lance pourtant. Sans ce filtre, l elargissement du motif les compterait.
    """
    try:
        arbre = ast.parse(chemin.read_text(encoding="utf-8"))
    except (OSError, SyntaxError):
        return False
    return any(
        isinstance(n, ast.Assign) and any(getattr(c, "id", "") == "CONTRAT" for c in n.targets)
        for n in ast.walk(arbre)
    )


# Les gardes de `scripts/methode` qui declarent un CONTRAT et que ce banc ne mute PAS, avec la
# raison et l ADR qui la decide. C est l idiome de `verifie_temoins_non_decoratifs.HORS_PORTEE`, que
# le banc voisin porte depuis longtemps et qui manquait ici (#5479).
#
# Ces deux-la ne sont dans AUCUN atelier, et c est assume : l ADR 5157 ecrit qu « une regle indexee
# sur la CI est muette la ou la CI est muette », et qu « un outil qu on lance a la main garde un
# dispositif ». Le corpus de ce banc se derivant de `lint.yml`, ils en sortent par construction.
#
# La liste est CONFRONTEE dans les deux sens : une entree qui ne correspond plus a rien fait rougir,
# et un garde ni couvert ni exempte aussi. Sans quoi elle serait le tapis sous lequel on pousse ce
# qu on ne veut pas muter - le mot du banc voisin, et il vaut ici.
HORS_PORTEE: dict[str, str] = {
    "convertit-adr-okf.py": "dans aucun atelier ; `generateur` sans mode, il rend un apercu et ne "
    "juge rien (ADR 5157)",
    "releve-des-secrets.py": "dans aucun atelier ; `rapport` sans mode, il releve et ne juge pas "
    "(ADR 5157)",
}


def exemptions_perimees(
    racine: pathlib.Path | None = None, exemptions: dict[str, str] | None = None
) -> list[str]:
    """Les entrees de `HORS_PORTEE` qui ne correspondent plus a aucun fichier du dossier.

    C est la moitie qui fait d une liste un INVENTAIRE : sans elle, une entree survivrait a ce
    qu elle exempte et couvrirait alors un garde neuf portant le meme nom, en silence. Meme
    exigence que l ADR 5398 pose sur la porte locale.
    """
    racine = racine or RACINE
    exemptions = HORS_PORTEE if exemptions is None else exemptions
    return sorted(n for n in exemptions if not (racine / "scripts" / "methode" / n).exists())


def sans_domicile(
    racine: pathlib.Path | None = None,
    dedans: set[str] | None = None,
    exemptions: dict[str, str] | None = None,
) -> list[str]:
    """Les gardes du dossier qui declarent un CONTRAT, hors du corpus et hors des exemptions.

    Un garde oublie et un garde deliberement hors champ rendaient le meme resultat - rien - et
    c est ce qui a laisse `scripts/batterie.py` invisible jusqu a #5397. Cette confrontation les
    separe : le second est NOMME, le premier fait rougir.
    """
    racine = racine or RACINE
    dedans = set(corpus()) if dedans is None else dedans
    exemptions = HORS_PORTEE if exemptions is None else exemptions
    trouves = []
    for f in sorted((racine / "scripts" / "methode").glob("*.py")):
        if f.name in exemptions or f"methode/{f.name}" in dedans:
            continue
        if declare_un_contrat(f):
            trouves.append(f.name)
    return trouves


# Les trois bancs de mutation du depot, un par famille de gardes. Ils font la MEME chose - neutraliser
# une detection et exiger que le temoin rougisse - et #5498 a mesure qu ils ne le DECLARAIENT pas
# pareil : deux disaient `cliquet`, celui-ci `invariant`, pour un comportement identique.
#
# Le critere qui departage est ecrit dans `dev-docs/ci-cd-release.md` a propos de
# `verifie_contrat_obligatoire.py` : « c est un invariant, pas un cliquet : il n y a pas de marge a
# relever, et l echappatoire est une liste d exceptions NOMMEES ». Les trois remplissent les deux
# conditions - leur nombre est zero decoratif, sans marge, et leur echappatoire est `HORS_PORTEE`.
LES_TROIS_BANCS = (
    "scripts/adr/verifie_temoins_non_decoratifs.py",
    "scripts/methode/temoins-de-methode-non-decoratifs.py",
    ".github/scripts/temoins_de_ci_non_decoratifs.py",
)


def declarations_des_bancs(racine: pathlib.Path | None = None) -> list[tuple[str, str, str]]:
    """Ce que chaque banc declare : son nom, son dispositif, son seuil.

    Derive du fichier plutot que recopie, pour qu une divergence se voie au lieu de se supposer.
    """
    racine = racine or RACINE
    rendu = []
    for chemin in LES_TROIS_BANCS:
        arbre = ast.parse((racine / chemin).read_text(encoding="utf-8"))
        for noeud in ast.walk(arbre):
            if not (isinstance(noeud, ast.Assign) and isinstance(noeud.value, ast.Dict)):
                continue
            if not any(getattr(c, "id", "") == "CONTRAT" for c in noeud.targets):
                continue
            d = {
                k.value: getattr(v, "value", None)
                for k, v in zip(noeud.value.keys, noeud.value.values)
                if hasattr(k, "value")
            }
            rendu.append((chemin.split("/")[-1], str(d.get("dispositif")), str(d.get("seuil"))))
            break
    return rendu


def corpus(texte: str | None = None) -> list[str]:
    """Les gardes que `lint.yml` lance sous `scripts/`, derives et non enumeres.

    Rendus RELATIFS a `scripts/`, donc `methode/x.py` et `batterie.py` : la porte vit a la racine du
    dossier, et le motif d avant la manquait alors que la prose de ce banc annonce « les gardes que
    la CI lance vraiment » (#5397).

    `texte` remplace le contenu de l atelier. Il sert a l auto-test, qui fabrique un atelier lancant
    ce banc et un autre ne le lancant pas : sans lui, l appartenance du banc a son propre corpus ne
    se verrait que d un cote (#5550).
    """
    texte = ATELIER.read_text(encoding="utf-8") if texte is None else texte
    trouves = set(LANCE.findall(texte))
    return sorted(c for c in trouves if declare_un_contrat(RACINE / "scripts" / c))


def porte_un_auto_test(source: str) -> bool:
    return "--auto-test" in source


def ligne_du_point_d_entree(source: str) -> int | None:
    """La ligne du `if __name__ == "__main__":` de MODULE, lue dans l ARBRE et non par un motif.

    Un motif ne distingue pas le code d une chaine. Des qu un garde porte un litteral contenant ce
    texte - et c est le cas de tout banc dont l auto-test ecrit de faux gardes - `search` prend la
    PREMIERE occurrence, la neutralisation s insere au milieu du litteral, et elle n y neutralise
    rien. Le garde tourne alors normalement, son auto-test passe, et ce banc conclut « decoratif »
    sur un garde qui ne l est pas.

    Ce n est pas une hypothese : le banc de `.github/scripts` s est fait prendre sur LUI-MEME en
    #5254, ou le motif trouvait quatre occurrences pour un seul point d entree. Le cas
    `un point d entree cache dans une chaine ne trompe pas` de l auto-test ci-dessous rejoue le
    piege, et il est vu ROUGE avant cette correction (#5263).

    L arbre ne peut pas se tromper : il ne voit que les `if` de niveau module. C est la difference
    entre reconnaitre une forme et demander a la chose ([ADR 5102]).

    ## Pourquoi elle est RECOPIEE et non partagee

    `.github/scripts/temoins_de_ci_non_decoratifs.py` en porte une jumelle. La question du partage
    s est posee, et la mesure la tranche contre : elle fait QUATORZE lignes la-bas et DIX-SEPT ici,
    et les deux bancs ne partagent RIEN d autre - ni fonds, ni import, ni arbre. Les relier
    demanderait un QUATRIEME domicile commun, apres `scripts/_commun/` et
    `.github/scripts/_forge.py`, pour une fonction que chacun peut lire en entier sans quitter son
    fichier.

    C est la mesure de #5216 appliquee a ces deux-la : « le partage reel est plus etroit qu il n y
    parait ». Ce qui protege ici n est pas le partage, c est que CHACUN porte son cas rouge : celui
    de ce banc est plus bas, et celui de l autre vit dans son propre auto-test.
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


def mutable(source: str) -> bool:
    """Un garde n est mutable que si son point d entree est reperable.

    Sans lui, il n existe aucun endroit sur ou inserer la neutralisation : la deviner reviendrait
    a rendre « decoratif » un garde qui ne l est pas, ce que ce garde existe pour eviter.
    """
    try:
        return ligne_du_point_d_entree(source) is not None
    except SyntaxError:
        return False


def mute(source: str) -> str:
    """La source, neutralisation INSEREE avant le point d entree de module."""
    ligne = ligne_du_point_d_entree(source)
    lignes = source.splitlines(keepends=True)
    return "".join(lignes[: ligne - 1]) + neutralisation(source) + "".join(lignes[ligne - 1 :])


@contextlib.contextmanager
def arbre_jetable():
    """Un depot ou muter sans toucher a celui-ci, comme le fait le garde des ADR depuis #4700."""
    with tempfile.TemporaryDirectory(prefix="vc-temoins-methode-") as tmp:
        faux = pathlib.Path(tmp) / "depot"
        faux.mkdir()
        shutil.copytree(RACINE / "scripts", faux / "scripts", symlinks=True)
        for entree in RACINE.iterdir():
            if entree.name not in {"scripts", ".git"}:
                (faux / entree.name).symlink_to(entree)
        yield faux


TRACE = "Traceback (most recent call last)"


def verdict_sous_mutation(nom: str, faux: pathlib.Path) -> tuple[str, str]:
    """Ce que rend l auto-test du garde MUTE : « tient », « non concluant » ou « decoratif ».

    TROIS valeurs et non deux, et c est la mesure qui l a impose (ADR 5257). La neutralisation
    remplace chaque fonction par un `lambda` rendant `[]` : un garde dont une fonction rend un tuple,
    un entier ou un chemin PLANTE au lieu d assertir. Ce rouge-la ne prouve rien (ADR 4918), et le
    compter comme une reussite revient a annoncer 22 gardes eprouves quand il y en a 16.

    Mesure du 2026-09-05 sur les 23 de la population : 16 tiennent, 6 ne concluent pas, 0 decoratifs.

    La cause est rendue avec le verdict : elle est ce qu on lit pour savoir POURQUOI un garde ne
    conclut pas, et c est elle qui distingue « il faudrait le rendre mutable » de « le banc a un
    defaut ».
    """
    cible = faux / "scripts" / nom
    original = cible.read_text(encoding="utf-8")
    try:
        cible.write_text(mute(original), encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(cible), "--auto-test"], capture_output=True, cwd=faux, check=False
        )
    finally:
        cible.write_text(original, encoding="utf-8")
    if rendu.returncode == 0:
        return "decoratif", "reste vert sans sa detection"
    erreur = rendu.stderr.decode("utf-8", "replace")
    if TRACE in erreur:
        lignes = [l for l in erreur.strip().splitlines() if l and not l.startswith(" ")]
        return "non concluant", (lignes[-1] if lignes else "trace illisible")[:110]
    return "tient", ""


def suspects() -> tuple[list[str], list[str], list[str]]:
    """TROIS listes : les decoratifs, les illisibles, et les NON CONCLUANTS.

    La troisieme est ce que l ADR 5257 ajoute. Elle n est pas un refus - un garde qui plante sous
    mutation n a rien prouve, mais il n a rien montre de faux non plus - et elle n est pas un
    silence : chacun est nomme avec sa cause. Le compter parmi les eprouves annoncerait 23 gardes
    tenus quand il y en a 16.
    """
    decoratifs, illisibles, non_concluants = [], [], []
    with arbre_jetable() as faux:
        for nom in corpus():
            f = RACINE / "scripts" / nom
            if not f.is_file():
                illisibles.append(f"{nom} : absent de scripts/methode")
                continue
            source = f.read_text(encoding="utf-8")
            if not porte_un_auto_test(source):
                continue
            if not mutable(source):
                illisibles.append(f"{nom} : aucun `if __name__` ou inserer la neutralisation")
                continue
            verdict, cause = verdict_sous_mutation(nom, faux)
            if verdict == "decoratif":
                decoratifs.append(f"{nom} : son auto-test reste vert, detection neutralisee")
            elif verdict == "non concluant":
                non_concluants.append(f"{nom} : {cause}")
    return decoratifs, illisibles, non_concluants


def code_de_sortie(decoratifs: list[str], illisibles: list[str]) -> int:
    """Le verdict, extrait du point d entree pour qu un temoin puisse l atteindre (issue #4788).

    Les DEUX refusent, et c est la decision de cette issue. Un garde illisible etait signale en
    sortant 0 : six sur quinze etaient dans ce cas, la CI restait verte, et la liste ne se vidait
    pas. Une ligne de journal sous un vert ne se lit pas.
    """
    return 1 if (decoratifs or illisibles) else 0


# ⟨les gardes qui PLANTENT sous mutation, nommes un par un (#5497)⟩ Un plantage ne fait pas refuser
# (ADR 5257), et c est juste. Mais rien ne BORNAIT ces gardes : ils s imprimaient sous un banc vert,
# et leur compte est passe de 6 a 11 en un mois sans qu une seule demande rougisse.
#
# **Une liste NOMMEE et non un cliquet**, comme le banc des ADR depuis #5743 et pour la meme raison :
# ce banc se declare `invariant`, et l ADR 5743 borne le residu d un invariant par une liste verifiee
# DANS LES DEUX SENS. Un compte laisserait echanger un plantage repare contre un plantage neuf.
#
# Les cles ont la FORME du corpus, un chemin relatif a `scripts/`. La valeur de chaque entree est sa
# classification, lue dans la trace du garde mute le 2026-10-06 et non recopiee d un motif : six
# recoivent une liste la ou ils attendaient un autre TYPE, quatre lisent le PREMIER element d une
# liste que la neutralisation a videe. Aucun n est repare ici : borner n est pas vider (EPIC #5265).
PLANTENT_SOUS_MUTATION: dict[str, str] = {
    "methode/couverture-openspec.py": "type sous neutralisation : un chemin attendu, `/` sur une liste",
    "methode/prepare-l-environnement.py": "type sous neutralisation : un entier attendu, `>` sur une liste",
    "methode/releve-les-harnais-muets.py": "type sous neutralisation : un deballage de trois valeurs sur un vide",
    "methode/releve-les-planchers.py": "type sous neutralisation : un dict attendu, une cle lue dans une liste",
    "methode/verifie-adoption-openspec.py": "index sous neutralisation : `entrees(r)[0]` sur une liste videe",
    "methode/verifie-commandes-prescrites.py": "index sous neutralisation : `suspects(muet)[0]` sur une liste videe",
    "methode/verifie-normalisation-git.py": "type sous neutralisation : un deballage de deux valeurs sur un vide",
    "methode/verifie-sous-commandes-openspec.py": "index sous neutralisation : `fichiers(r)[0]` sur une liste videe",
    "methode/verifie-specs-valides.py": "type sous neutralisation : un chemin attendu, une liste passee a `copytree`",
    "methode/verifie-version-openspec.py": "index sous neutralisation : `competences(r)[0]` sur une liste videe",
}

CONDUITE_SUR_LA_TABLE = (
    "Un garde qui plante : le reparer pour qu il ASSERTE au lieu de planter, ou le nommer dans "
    "PLANTENT_SOUS_MUTATION avec sa raison. Une entree qui ne plante plus : la retirer."
)


def ecarts_de_la_table(non_concluants: list[str]) -> tuple[list[str], list[str]]:
    """Les deux ecarts entre ce qui plante et ce que la table nomme : (inattendus, perimes).

    Les deux sens valent toujours ici : ce banc mute son corpus ENTIER a chaque lancement. Il n a
    donc pas la borne de portee que le banc des ADR pose sur le second sens.
    """
    plantent = {ligne.split(" : ", 1)[0] for ligne in non_concluants}
    return (
        sorted(plantent - set(PLANTENT_SOUS_MUTATION)),
        sorted(set(PLANTENT_SOUS_MUTATION) - plantent),
    )


def conclut_sur_la_table(non_concluants: list[str]) -> None:
    """Sort en 1 avec un CONSTAT si la table ne decrit plus ce qui plante, et rien sinon.

    Un constat et non un refus : le banc a mute son corpus et compare, donc il a JUGE (ADR 5774). Il
    sort en `1` SANS les marques de refus, que la porte lirait « ce garde n a pas pu juger ».
    """
    inattendus, perimes = ecarts_de_la_table(non_concluants)
    if not (inattendus or perimes):
        return
    dits = []
    if inattendus:
        dits.append(
            f"{len(inattendus)} garde(s) plantent sous mutation sans etre nommes : "
            + ", ".join(inattendus)
        )
    if perimes:
        dits.append(
            f"{len(perimes)} entree(s) de PLANTENT_SOUS_MUTATION ne plantent plus : "
            + ", ".join(perimes)
        )
    print("\n" + " ; ".join(dits), file=sys.stderr)
    print(CONDUITE_SUR_LA_TABLE, file=sys.stderr)
    raise SystemExit(1)


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()

    verifie("le corpus vient de lint.yml, et n est pas vide", len(corpus()) > 5, True)
    # ⟨#5397⟩ La prose de ce banc dit « les gardes que la CI lance ». Son motif disait
    # `scripts/methode/` seulement, si bien que `scripts/batterie.py` - lance par `lint.yml:600`,
    # portant un CONTRAT et un temoin - n etait mute par AUCUN des trois bancs.
    verifie(
        "le corpus prend la porte, qui vit hors de scripts/methode", "batterie.py" in corpus(), True
    )
    # ⟨#5479⟩ Un garde du dossier qui declare un CONTRAT est soit dans le corpus, soit EXEMPTE
    # avec sa raison. Sans cette confrontation, un garde oublie et un garde deliberement hors champ
    # rendent le meme resultat : rien. C est ce qui est arrive a `scripts/batterie.py` jusqu a #5397.
    verifie("aucun garde du dossier ne sort du compte en silence", sans_domicile(), [])
    verifie("aucune exemption n a survécu à son motif", exemptions_perimees(), [])
    # ⟨#5550⟩ Ce banc fait-il partie du corpus qu il mute ? Oui, et l ADR 4770 l a decide. Les deux
    # premiers cas le disent sur le depot reel, SOUS LA FORME du corpus. Les deux suivants fabriquent
    # les deux cotes, parce que sur le depot reel l appartenance ne peut etre vue que d un seul.
    verifie("ce banc est dans le corpus qu il mute (ADR 4770)", lambda: MOI in corpus(), True)
    verifie(
        "sous la forme du corpus : son nom nu n y est pas",
        lambda: pathlib.Path(MOI).name in corpus(),
        False,
    )
    # La LISTE et non une appartenance : « il n y est pas » serait vrai aussi d un corpus vide, donc
    # d un `corpus` qui ne lirait rien.
    verifie(
        "un atelier qui ne le lance pas rend ce qu il lance, sans ce banc",
        lambda: corpus("python3 scripts/batterie.py --auto-test\n"),
        ["batterie.py"],
    )
    verifie(
        "et un atelier qui le lance l y fait entrer",
        lambda: corpus(f"python3 scripts/{MOI}\n"),
        [MOI],
    )
    # ⟨on S ARRETE si la population est fausse⟩ Les cas suivants eprouvent la mutation et le verdict.
    # Rendus sur un corpus qu on ne sait plus deriver, ils ne veulent rien dire, et c est le geste que
    # le banc de CI porte deja. C est aussi ce qui rend vraie la phrase de l ADR 4770, « mute, son
    # auto-test rougit » : sans cet arret, ce banc sous sa propre mutation PLANTAIT plus bas, sur
    # `mute(src).index(...)`, et son verdict sur lui-meme etait « non concluant » (ADR 4918).
    if echecs():
        return echecs()
    # ⟨#5498⟩ Les trois bancs font la meme chose : ils doivent la declarer pareil, sans quoi aucun
    # dispositif ne les retrouve ensemble.
    declarations = declarations_des_bancs()
    # ⟨le compte AVANT l ensemble⟩ Sans lui, une derivation qui ne lirait qu un seul banc rendrait
    # un ensemble d un element, qui satisfait les deux attentes suivantes sans rien prouver.
    verifie("les trois bancs sont bien lus", len(declarations), 3)
    verifie(
        "les trois bancs déclarent le même dispositif",
        sorted({d for _, d, _ in declarations}),
        ["invariant"],
    )
    verifie(
        "et le même genre de seuil",
        sorted({s for _, _, s in declarations}),
        ["(sans objet)"],
    )

    # ⟨les DEUX confrontations, sur un depot fabrique⟩ Sur le depot reel, les deux cas ci-dessus sont
    # vrais A VIDE : aucun garde ne sort du compte, et aucune exemption n a peri. Un cas qui ne peut
    # pas rougir ne prouve rien - le defaut consigne en #5418 - donc on fabrique les deux situations.
    with tempfile.TemporaryDirectory(prefix="vc-hors-portee-") as bac:
        faux = pathlib.Path(bac)
        (faux / "scripts" / "methode").mkdir(parents=True)
        contrat = '\nCONTRAT = {"geste": "x", "dispositif": "invariant"}\n'
        (faux / "scripts" / "methode" / "oublie.py").write_text(contrat, encoding="utf-8")
        (faux / "scripts" / "methode" / "exempte.py").write_text(contrat, encoding="utf-8")

        verifie(
            "un garde a CONTRAT ni couvert ni exempte est nomme",
            sans_domicile(faux, dedans=set(), exemptions={}),
            ["exempte.py", "oublie.py"],
        )
        verifie(
            "le meme, une fois exempte, ne l est plus",
            sans_domicile(
                faux, dedans=set(), exemptions={"exempte.py": "raison", "oublie.py": "raison"}
            ),
            [],
        )
        verifie(
            "une exemption qui ne correspond a aucun fichier est nommee",
            exemptions_perimees(faux, {"disparu.py": "raison", "exempte.py": "raison"}),
            ["disparu.py"],
        )
    # ⟨le filtre par CONTRAT⟩ `lint.yml` lance aussi `scripts/graphify/rebuild.py` et
    # `scripts/mkdocs/bandeau_adr.py`, qui PRODUISENT sans juger. Sans ce filtre, l elargissement du
    # motif les compterait comme des gardes, et le banc muterait un generateur de graphe.
    verifie(
        "un script lance par la CI mais sans CONTRAT n est pas un garde",
        any("rebuild.py" in c or "bandeau_adr.py" in c for c in corpus()),
        False,
    )
    # ⟨l ancre negative, eprouvee sur le MOTIF⟩ Elle empeche d attraper la queue des chemins
    # `.github/scripts/...`. Son effet est invisible depuis `corpus()`, que le filtre par CONTRAT
    # rattrape ; c est donc le motif qu il faut interroger, sans quoi cette propriete n a aucun
    # temoin - le defaut consigne en #5418.
    verifie(
        "le motif ne prend pas la queue d un chemin de .github/scripts",
        LANCE.findall("python3 .github/scripts/verifie_corps_pr.py\n"),
        [],
    )
    verifie(
        "un garde a point d entree est mutable",
        mutable('def f():\n    pass\n\n\nif __name__ == "__main__":\n    f()\n'),
        True,
    )
    verifie(
        "un garde sans point d entree ne l est pas", mutable("def f():\n    pass\n\n\nf()\n"), False
    )

    src = 'def detecte():\n    return [1]\n\n\nif __name__ == "__main__":\n    detecte()\n'
    # Le point qui a coute cinq mesures fausses : la neutralisation s INSERE, elle ne s ajoute pas.
    verifie(
        "la neutralisation se pose AVANT le point d entree",
        mute(src).index("_t_mutation") < mute(src).index("if __name__"),
        True,
    )
    verifie("elle ne se pose pas apres", mute(src).rstrip().endswith("detecte()"), True)
    # Le sens NEGATIF : sans lui, un `mute` qui rendrait son entree passerait les deux precedents.
    verifie("la source est bien changee", mute(src) != src, True)

    # La fonction d auto-test est epargnee, sinon le garde rougit pour la mauvaise raison (#4760).
    # ⟨eprouve par le COMPORTEMENT, non par le texte⟩ Ce cas lisait une sous-chaine de la constante
    # de neutralisation. Il passait donc sans qu aucune fonction n ait ete epargnee pour de vrai, et
    # il serait mort d une simple reecriture de la condition. Depuis #5524 la regle se derive par
    # fichier : il n y a plus de constante a lire, et c est l occasion de l eprouver pour ce qu elle
    # FAIT.
    fabrique = (
        "def detecte():\n"
        "    return 1\n"
        "\n"
        "\n"
        "def auto_test():\n"
        "    return 0 if detecte() == 1 else 1\n"
        "\n"
        "\n"
        'if __name__ == "__main__":\n'
        "    raise SystemExit(auto_test())\n"
    )
    espace: dict[str, object] = {}
    exec(compile(mute(fabrique), "<mutation>", "exec"), espace)  # noqa: S102
    verifie("apres mutation, `detecte` ne detecte plus", espace["detecte"](), [])
    # Le CONTRASTE, et il porte tout : si `auto_test` etait mutee elle aussi, elle rendrait `[]` au
    # lieu de 1, et l on ne saurait pas distinguer « le temoin a vu la detection tomber » de « le
    # temoin est mort avec elle ».
    verifie("`auto_test` survit et VOIT la detection tombee", espace["auto_test"](), 1)

    # Et l exemption se derive : elle ne nomme aucun GARDE, seulement des fonctions du fichier mute.
    verifie(
        "l exemption ne cite aucun nom de garde",
        any(g.split(".")[0] in mute(fabrique) for g in corpus()),
        False,
    )

    # #5264. Un garde qui PLANTE sous mutation n est pas eprouve. Sans ce cas, le banc comptait ces
    # rouges-la parmi les reussites et annoncait 23 gardes tenus quand il y en a 17.
    with arbre_jetable() as faux_verdict:
        planteur = faux_verdict / "scripts" / "methode" / "faux-planteur.py"
        planteur.write_text(
            "def detecte():\n    return [1], 1\n\n\n"
            "def _auto_test():\n    liste, compte = detecte()\n"
            '    print("ok" if compte == 1 else "ECHEC")\n'
            "    return 0 if compte == 1 else 1\n\n\n"
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verdict, cause = verdict_sous_mutation("methode/faux-planteur.py", faux_verdict)
        verifie("un garde qui plante ne CONCLUT pas", verdict, "non concluant")
        verifie("et sa cause est rendue", "ValueError" in cause or "TypeError" in cause, True)

        tenu = faux_verdict / "scripts" / "methode" / "faux-tenu.py"
        tenu.write_text(
            "def detecte():\n    return [1]\n\n\n"
            "def _auto_test():\n"
            '    print("ok" if detecte() == [1] else "ECHEC")\n'
            "    return 0 if detecte() == [1] else 1\n\n\n"
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verifie(
            "un garde qui lit sa detection TIENT",
            verdict_sous_mutation("methode/faux-tenu.py", faux_verdict)[0],
            "tient",
        )

        muet = faux_verdict / "scripts" / "methode" / "faux-muet.py"
        muet.write_text(
            "def detecte():\n    return [1]\n\n\n"
            'def _auto_test():\n    print("ok, sans rien lire")\n    return 0\n\n\n'
            'if __name__ == "__main__":\n    raise SystemExit(_auto_test())\n',
            encoding="utf-8",
        )
        verifie(
            "un garde qui ne la lit pas est DECORATIF",
            verdict_sous_mutation("methode/faux-muet.py", faux_verdict)[0],
            "decoratif",
        )

    # #5263. Le piege qui a fait tomber le banc de `.github/scripts` sur LUI-MEME : un garde qui
    # porte `if __name__` dans une CHAINE avant de le porter pour de vrai. Un motif prenait la
    # premiere occurrence, la neutralisation atterrissait dans le litteral, le garde tournait
    # normalement, et ce banc le declarait « decoratif ». Vu ROUGE avant la lecture par l arbre.
    piege = (
        'MODELE = """\nif __name__ == "__main__":\n    print("un modele")\n"""\n\n\n'
        "def detecte():\n    return [1]\n\n\n"
        'if __name__ == "__main__":\n    detecte()\n'
    )
    verifie(
        "un point d entree cache dans une chaine ne trompe pas",
        ligne_du_point_d_entree(piege),
        11,
    )
    verifie(
        "et la neutralisation se pose APRES le litteral",
        mute(piege).index("_t_mutation") > mute(piege).index('MODELE = """'),
        True,
    )

    # #4788. Un garde sans point d entree REFUSE, au lieu d etre signale sous un vert.
    verifie("rien a signaler passe", code_de_sortie([], []), 0)
    verifie("un garde decoratif refuse", code_de_sortie(["x"], []), 1)
    verifie("un garde SANS POINT D ENTREE refuse aussi", code_de_sortie([], ["y"]), 1)
    verifie("et les deux ensemble refusent", code_de_sortie(["x"], ["y"]), 1)

    # ⟨#5497⟩ La table des non concluants, confrontee dans les deux sens. Les cas eprouvent la
    # fonction et le geste sur des lignes FABRIQUEES : muter trente gardes pour savoir ce que rend
    # une difference d ensembles ne prouverait rien de plus.
    connus = [f"{nom} : peu importe" for nom in PLANTENT_SOUS_MUTATION]
    verifie(
        "les nommes qui plantent ne font aucun ecart",
        lambda: ecarts_de_la_table(connus),
        ([], []),
    )
    verifie(
        "un garde de PLUS qui plante est un inattendu, et il se nomme",
        lambda: ecarts_de_la_table([*connus, "methode/neuf.py : boum"]),
        (["methode/neuf.py"], []),
    )
    # ⟨le sens que n aurait pas un cliquet⟩ Un compte reste VERT quand un plantage est repare.
    verifie(
        "une entree qui ne plante plus est perimee, et elle se nomme aussi",
        lambda: ecarts_de_la_table(connus[1:]),
        ([], ["methode/couverture-openspec.py"]),
    )
    # ⟨la table n est ni vide ni bavarde⟩ Vidée, chaque plantage serait un inattendu ; et une cle
    # qui n aurait pas la forme du corpus ne rencontrerait jamais ce que `suspects()` rend.
    verifie(
        "chaque entree de la table est un garde du corpus, sous sa forme",
        lambda: sorted(set(PLANTENT_SOUS_MUTATION) - set(corpus())),
        [],
    )

    # ⟨le GESTE, et son CANAL⟩ Les cas ci-dessus eprouvent le calcul ; ceux-ci, qu on AGIT dessus.
    def sortie_de(lignes: list[str]) -> tuple[object, str]:
        tampon = io.StringIO()
        with contextlib.redirect_stderr(tampon):
            try:
                conclut_sur_la_table(lignes)
            except SystemExit as sortie:
                return sortie.code, tampon.getvalue()
        return "aucun constat", tampon.getvalue()

    verifie(
        "un plantage de plus rend un constat ROUGE, en 1 : ce banc a juge",
        lambda: sortie_de([*connus, "methode/neuf.py : boum"])[0],
        1,
    )
    verifie("une entree perimee rend un constat rouge aussi", lambda: sortie_de(connus[1:])[0], 1)
    verifie("et les nommes ne rendent aucun constat", lambda: sortie_de(connus)[0], "aucun constat")
    # La porte classe par les MARQUES : un constat qui les porterait serait lu « muet » (ADR 5774).
    verifie(
        "et le constat ne porte AUCUNE marque de refus",
        lambda: [
            m
            for m in (MARQUE_CAUSE, MARQUE_GESTE)
            if m in sortie_de([*connus, "methode/neuf.py : boum"])[1]
        ],
        [],
    )
    return echecs()


CONTRAT = {
    "geste": "auto-test de garde de methode qui reste vert sans detection",
    "population": "les gardes de scripts/methode que la suite charge",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/temoins-de-methode-non-decoratifs.py --auto-test",
    "decision": "ADR 4490",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    decoratifs, illisibles, non_concluants = suspects()
    for l in illisibles:
        print(f"NON ÉPROUVÉ : {l}", file=sys.stderr)
    for l in decoratifs:
        print(f"ÉCHEC : {l}", file=sys.stderr)
    if decoratifs:
        print(
            "\nUn auto-test qui reste vert alors que le garde ne détecte plus rien ne prouve rien.\n"
            "L'article A2 demande qu'un garde soit vu rouge sur sa propre mutation.",
            file=sys.stderr,
        )
        raise SystemExit(1)
    if illisibles:
        print(
            "\nCes gardes exécutent leur corps au niveau du module : aucun endroit sûr où insérer\n"
            "la neutralisation, donc aucune preuve au titre de l'article A2.\n"
            "\nLe remède tient en une ligne : placez la partie qui S'EXÉCUTE sous\n"
            '`if __name__ == "__main__":`, en laissant AU-DESSUS tout ce qui se définit.\n'
            "La neutralisation s'insère juste avant ce point d'entrée, et une fonction\n"
            "définie après lui y échapperait.\n"
            "\nCe garde REFUSE désormais au lieu de le signaler (issue #4788). Il l'a signalé\n"
            "en sortant 0 tant que six gardes sur quinze étaient dans ce cas : une ligne de\n"
            "journal sous une CI verte ne se lit pas, et la liste ne se vidait pas.",
            file=sys.stderr,
        )
    for l in non_concluants:
        print(f"NON CONCLUANT : {l}", file=sys.stderr)
    if non_concluants:
        print(
            "\nUn garde qui PLANTE sous mutation n'a rien prouvé : il est mort avant sa première\n"
            "assertion, et un rouge pour la mauvaise raison ne prouve rien (ADR 4918). Ces gardes\n"
            "ne font pas refuser, parce que refuser dessus reviendrait à refuser sur ce que ce banc\n"
            "n'a pas su lire. Ils se comptent à part, et les rendre mutables est un travail en soi.",
            file=sys.stderr,
        )
    if not code_de_sortie(decoratifs, illisibles):
        tenus = len(corpus()) - len(non_concluants) - len(illisibles)
        print(
            f"{tenus} garde(s) de méthode rougissent sous mutation, "
            f"{len(non_concluants)} ne concluent pas, {len(illisibles)} sont illisibles : "
            f"soit {tenus + len(non_concluants) + len(illisibles)} sur {len(corpus())}."
        )
    # Le compte sort AVANT la confrontation : un constat qui le precederait le ferait disparaitre.
    conclut_sur_la_table(non_concluants)
    raise SystemExit(code_de_sortie(decoratifs, illisibles))
