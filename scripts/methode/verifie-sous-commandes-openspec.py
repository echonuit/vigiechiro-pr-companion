#!/usr/bin/env python3
"""Garde sur les invocations d OpenSpec citees dans nos fichiers : elles doivent exister.

Les dix-huit fichiers d OpenSpec du depot decrivent le contrat de la ligne de commande, et une
invocation approximative n y casse rien. Elle attend dans le texte, et echoue le jour ou un agent
suit l instruction qui la cite, loin de sa cause. Le cas s est produit en instruisant #4511 :
`openspec change remove` a ete ecrit dans un plan, et cette sous-commande n existe pas.

**Pourquoi ce garde existe maintenant.** Le lot qui adopte les douze fichiers reecrit environ 130 Ko
de prose dont une part decrit ce contrat. C est le moment ou une invocation approximative entre dans
le texte. Ecrit apres, ce garde constaterait le defaut ; ecrit avant, il tient la reecriture pendant
qu elle se fait. C est le prix assume de la decision d adopter : le depot accepte que ses fichiers
puissent decrire un contrat perime, et ceci rend la derive visible.

**Il est VERT sur l arbre d aujourd hui**, ou les neuf invocations citees existent toutes. Un garde
vert sur un corpus propre ne prouve rien de lui-meme : tout le poids de la demonstration porte sur
son auto-test, et c est pourquoi celui-ci monte quatre etats et exige un temoin vert avant ses
rouges.

**Deux niveaux, pas un.** Trois des invocations citees sont imbriquees : `openspec new change`,
`openspec store list`, `openspec change show`. Un garde qui ne lirait que le premier mot declarerait
`openspec new` valide et laisserait passer `openspec new frobnicate`.

**Le faux positif que ce garde a failli embarquer.** Un premier releve, ecrit avec `\\s+` entre le
nom de l outil et sa sous-commande, annoncait `openspec version` INCONNUE dans les six competences.
La cause n etait pas dans les fichiers : `\\s` franchit la fin de ligne, et l en-tete YAML porte
`author: openspec` suivi de `version: "1.0"` a la ligne d apres. D ou les deux precautions du
releve, chacune avec son cas d auto-test : on lit LIGNE PAR LIGNE, et l en-tete est ecarte.

**Ce qu il ne peut pas lire, il le refuse.** Code **2**, et non 1 : « je n ai pas pu juger » et « j ai
juge et c est rouge » sont deux verdicts, que le depot separe (#5485). Un garde qui passerait au vert
faute d avoir pu comparer annoncerait une verification qu il n a pas faite (article A3) ; un garde qui
sortirait en 1 ferait chercher le defaut dans le diff, ce qui a coute huit corps de demande (#5774).

**Et il nomme le prerequis le plus PROFOND.** Le binaire pose ne suffit pas : c est un script, son
shebang reclame un interprete, et cet interprete doit etre sur le PATH quand ce garde tourne. Sans
lui, ce garde rendait « Invocations d OpenSpec qui n existent pas », donc accusait le diff de ce qui
tenait au poste. La sonde vit dans `_commun/prerequis.py`, partagee avec `verifie-specs-valides.py`.

**Sur l ADR 3645, qui veut qu un detecteur textuel s exclue de son corpus.** La question se pose,
puisque ce fichier nomme des sous-commandes dans sa prose et en fabrique de fausses dans son
auto-test. Elle se resout sans exemption : le corpus n est pas un balayage de l arbre mais trois
motifs fixes sous `.agents/skills`, `.claude/skills` et `.claude/commands`, qui ne peuvent pas
atteindre `scripts/methode/`. La raison est ecrite ici pour qu elle ne se repose pas.

    --verifie   : ne rien ecrire, sortir 1 sur une invocation inconnue. C est aussi le defaut.
    --auto-test : eprouver le garde sur une copie jetable, et sortir 1 s il reste vert la ou il
                  devrait rougir, ou s il rougit sur un arbre sain.
"""

import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import (
    cas_d_auto_test,
    lit_le_refus,
    prerequis,
    refuse,
    sort_si_contrat_demande,
)

BINAIRE_EPINGLE = pathlib.Path(".github") / "openspec" / "node_modules" / ".bin" / "openspec"

# Les deux arbres qui decrivent le contrat. Les adaptateurs de `.claude/skills` sont des copies,
# et les inclure ne coute rien : si l une d elles derivait, ce garde la verrait aussi, la ou
# `synchronise-adaptateurs.py` la voit deja.
#
# Une troisieme entree, `.claude/commands/opsx`, a ete RETIREE par #4516 avec le dossier qu elle
# nommait. Le retrait est ecrit plutot que laisse a la disparition du dossier : `fichiers()` saute
# une entree absente sans rien dire, et le refus « corpus vide » plus bas ne mord que sur un total
# nul. Un corpus reduit d un tiers serait donc reste vert. C est le defaut que #4566 traite ici.
MOTIFS = (
    (pathlib.Path(".agents") / "skills", "openspec-*/SKILL.md"),
    (pathlib.Path(".claude") / "skills", "openspec-*/SKILL.md"),
)

# `[ \t]` et NON `\s` : voir le faux positif documente en tete. Le second mot est optionnel, et il
# n est retenu que si le premier porte reellement des sous-commandes.
INVOCATION = re.compile(r"\bopenspec[ \t]+([a-z][a-z0-9-]*)(?:[ \t]+([a-z][a-z0-9-]*))?")
LIGNE_COMMANDE = re.compile(r"^\s{2}([a-z][a-z0-9-]*)")


def binaire(racine: pathlib.Path) -> str:
    """Le chemin de l outil EPINGLE, sans jamais se rabattre sur le PATH.

    Aucun repli sur le PATH, et ce n est pas une precaution de style. Un poste peut porter une
    installation globale d une AUTRE version, dont l arbre de commandes differe : comparer nos
    citations a cet arbre-la rendrait un verdict sur un outil que le depot n utilise pas. C est la
    regle que #4512 a posee, « la version du depot fait foi, pas celle du poste ».

    L auto-test l a trouve plutot que la relecture : son cas « binaire absent » ressortait VERT,
    parce que `shutil.which` trouvait l installation globale de la machine qui le lancait.

    Il ne dit plus s il est la : c est `refus_de_conclure` qui le demande, et pour une raison de
    CANAL plutot que de gout. Cette fonction rendait sa cause d echec comme un second element de
    couple, qui remontait dans la liste d `ecarts` et heritait de son en-tete - « Invocations
    d OpenSpec qui n existent pas ». Un refus portait donc le titre des constats, et accusait le diff
    de ce qui tenait au poste (#5774).
    """
    return str(racine / BINAIRE_EPINGLE)


def refus_de_conclure(racine: pathlib.Path) -> tuple[str, str] | None:
    """Ce qui empeche de juger, en (cause, geste), ou `None` si l on peut juger.

    SEPARE des ecarts, et c est tout le point. Un garde qui ne peut pas conclure et un garde qui a
    trouve des invocations fausses disent deux choses de nature differente ; les faire voyager dans
    la meme liste donne au premier le titre du second. Huit corps de demande sur deux sessions ont
    classe ce refus « environnemental, etranger a ce diff » apres avoir lu ce titre (#5774).

    L ordre des deux questions est celui du prerequis le plus profond : ce que le poste n a pas
    d abord, ce que l outil ne rend pas ensuite. Inverse, il refuserait sur une aide vide en taisant
    que l interprete manque, ce qui est exactement le message qu une session a traine.
    """
    manque = prerequis.manque_pour(racine / BINAIRE_EPINGLE, "npm ci --prefix .github/openspec")
    if manque:
        return manque

    if not arbre_des_commandes(binaire(racine)):
        return (
            (
                "l aide de l outil epingle ne rend aucune commande, et ce garde compare les"
                " citations a cette aide : il n y a rien a comparer"
            ),
            (
                "verifiez que « openspec --help » rend un bloc « Commands: » ; si l outil a change"
                " de forme d aide, c est la version epinglee qu il faut reprendre"
            ),
        )
    return None


def sous_commandes(outil: str, chemin: list[str]) -> set[str]:
    """Ce que l aide de l outil expose a ce niveau. Lu de l outil, jamais recopie ici."""
    rendu = subprocess.run(
        [outil] + chemin + ["--help"], capture_output=True, text=True, check=False
    ).stdout
    bloc = rendu.split("Commands:", 1)
    if len(bloc) < 2:
        return set()
    trouves = set()
    for ligne in bloc[1].splitlines():
        vu = LIGNE_COMMANDE.match(ligne)
        if vu and vu.group(1) != "help":
            trouves.add(vu.group(1))
    return trouves


def arbre_des_commandes(outil: str) -> dict[str, set[str]]:
    """La racine et un niveau de sous-commandes, tel que l outil le declare."""
    racine = sous_commandes(outil, [])
    return {commande: sous_commandes(outil, [commande]) for commande in sorted(racine)}


def corps(texte: str) -> str:
    """Le texte sans son en-tete YAML : des metadonnees, pas de la prose d instruction."""
    if texte.startswith("---\n"):
        fin = texte.find("\n---\n", 3)
        if fin != -1:
            return texte[fin + 5 :]
    return texte


def entrees(racine: pathlib.Path) -> list[tuple[str, list[pathlib.Path]]]:
    """Pour chaque entree du corpus, son libelle et les fichiers qu elle rend.

    Une entree absente rend une liste VIDE plutot que d etre sautee : c est ce qui permet a
    `corpus_incomplet` de refuser PAR ENTREE au lieu de refuser sur le total. Un corpus dont un
    chemin a disparu rend encore des fichiers, et un refus sur le total resterait vert en n ayant
    lu qu une partie de ce que le garde annonce. Article A3, ADR 2748 (#4566).
    """
    rendu = []
    for base, motif in MOTIFS:
        dossier = racine / base
        rendu.append((str(base), sorted(dossier.glob(motif)) if dossier.is_dir() else []))
    return rendu


def fichiers(racine: pathlib.Path) -> list[pathlib.Path]:
    """Tous les fichiers du corpus, a plat."""
    return [f for _, trouves in entrees(racine) for f in trouves]


def corpus_incomplet(racine: pathlib.Path) -> list[str]:
    """Les refus qui portent sur le CORPUS lui-meme, avant tout verdict sur son contenu."""
    manques = []
    comptes = []
    for libelle, trouves in entrees(racine):
        if not trouves:
            manques.append(
                f"{libelle} ne rend aucun fichier : cette entree du corpus est vide, donc le "
                "garde ne verifie rien de ce cote"
            )
        else:
            comptes.append((libelle, len(trouves)))
    if len({n for _, n in comptes}) > 1:
        detail = ", ".join(f"{libelle} en rend {n}" for libelle, n in comptes)
        manques.append(
            f"les entrees du corpus ne rendent pas le meme nombre de fichiers : {detail}. "
            "La copie a derive du fonds, ou l une des deux a ete reecrite"
        )
    return manques


def ecarts(racine: pathlib.Path) -> list[str]:
    """Les invocations citees qui n existent pas. Liste vide = le garde est au vert.

    Ne porte QUE des constats. Ce qui empeche de juger passe par `refus_de_conclure`, et l appelant
    le demande avant : une liste vide veut donc dire « j ai juge et c est vert », jamais « je n ai
    pas pu juger ».
    """
    arbre = arbre_des_commandes(binaire(racine))

    incomplet = corpus_incomplet(racine)
    if incomplet:
        return incomplet

    vues = fichiers(racine)

    trouves = []
    for fichier in vues:
        court = fichier.relative_to(racine)
        for numero, ligne in enumerate(corps(fichier.read_text(encoding="utf-8")).splitlines(), 1):
            for vu in INVOCATION.finditer(ligne):
                premier, second = vu.group(1), vu.group(2)
                if premier not in arbre:
                    trouves.append(f"{court}:{numero}  « openspec {premier} » n existe pas")
                elif second and arbre[premier] and second not in arbre[premier]:
                    trouves.append(
                        f"{court}:{numero}  « openspec {premier} {second} » n existe pas ; "
                        f"{premier} expose " + ", ".join(sorted(arbre[premier]))
                    )
    return trouves


def auto_test() -> int:
    """Un arbre sain doit etre VERT, et chaque etat casse doit etre ROUGE.

    Le temoin vert passe en premier et compte autant que les rouges : ce garde est vert sur le
    corpus reel, donc un garde qui rougirait sur TOUT rougirait aussi sur les etats casses, et ses
    rouges ne diraient rien.

    Le quatrieme cas est le plus important : il rejoue le faux positif que ce garde a failli
    embarquer, `author: openspec` suivi de `version:` a la ligne d apres. Il doit rester VERT.

    **Et il REFUSE plutot que d echouer quand le prerequis manque**, ce qui est le sujet de ce garde
    applique a son propre harnais. Mesure du 2026-10-03 sur ce poste, `node` hors du PATH : l
    auto-test rendait « Le garde ne tient pas : le temoin rougit », donc il accusait le garde de ce
    qui tenait au poste - exactement le faux signal que #5774 corrige ailleurs. Il le produisait chez
    lui (#5774).
    """
    empeche = prerequis.manque_pour(RACINE / BINAIRE_EPINGLE, "npm ci --prefix .github/openspec")
    if empeche:
        refuse(*empeche)

    script = pathlib.Path(__file__).resolve()

    def premier_fichier(r: pathlib.Path) -> pathlib.Path:
        return fichiers(r)[0]

    def commande_inventee(r: pathlib.Path) -> None:
        f = premier_fichier(r)
        f.write_text(
            f.read_text(encoding="utf-8") + "\nLancez `openspec frobnicate`.\n", encoding="utf-8"
        )

    def sous_commande_inventee(r: pathlib.Path) -> None:
        f = premier_fichier(r)
        f.write_text(
            f.read_text(encoding="utf-8") + "\nLancez `openspec new frobnicate`.\n",
            encoding="utf-8",
        )

    def entete_yaml(r: pathlib.Path) -> None:
        """Le faux positif : dans l en-tete, et a cheval sur deux lignes. Doit rester VERT."""
        f = premier_fichier(r)
        texte = f.read_text(encoding="utf-8")
        f.write_text(
            '---\nmetadata:\n  author: openspec\n  version: "1.0"\n---\n' + corps(texte),
            encoding="utf-8",
        )

    def sans_binaire(r: pathlib.Path) -> None:
        shutil.rmtree(r / ".github" / "openspec" / "node_modules", ignore_errors=True)

    def entree_absente(r: pathlib.Path) -> None:
        """UNE entree du corpus disparait. Les autres rendent encore des fichiers."""
        shutil.rmtree(r / MOTIFS[1][0])

    def arbre_ampute(r: pathlib.Path) -> None:
        """Un arbre perd UNE competence : les entrees cessent de rendre le meme compte."""
        shutil.rmtree(min((r / MOTIFS[1][0]).glob(MOTIFS[1][1])).parent)

    cas = [
        ("commande inventee", commande_inventee, 1),
        ("sous-commande inventee", sous_commande_inventee, 1),
        # 2 et non 1 depuis #5774 : un outil absent est « je n ai pas pu juger », pas « j ai juge et
        # c est rouge ». Cette attente est le controle de la version d avant : elle rougit sur le
        # garde tel qu il etait, qui ecrasait le 2 en 1.
        ("binaire absent", sans_binaire, 2),
        ("une entree du corpus absente", entree_absente, 1),
        ("un arbre ampute d une competence", arbre_ampute, 1),
        ("en-tete YAML a cheval", entete_yaml, 0),
    ]

    def copie_jetable(tmp: str) -> pathlib.Path:
        copie = pathlib.Path(tmp) / "depot"
        for dossier in (".agents", ".claude", "scripts", ".github/openspec"):
            source = RACINE / dossier
            if source.exists():
                (copie / dossier).parent.mkdir(parents=True, exist_ok=True)
                shutil.copytree(source, copie / dossier, symlinks=True)
        return copie

    def path_sans(outil: str) -> str:
        """Le PATH courant prive de tout repertoire qui porte `outil`.

        Calcule plutot qu ecrit en dur. Un cas qui poserait « PATH=/usr/bin:/bin » decrirait CE
        poste, ou `node` vit sous un gestionnaire de version ; ailleurs il laisserait passer un
        `node` de /usr/bin et le cas deviendrait vert sans rien prouver.
        """
        garde = [
            d
            for d in os.environ.get("PATH", "").split(os.pathsep)
            if d and not (pathlib.Path(d) / outil).exists()
        ]
        return os.pathsep.join(garde)

    def lance(copie: pathlib.Path, sans: str | None = None) -> tuple[int, str]:
        env = dict(os.environ)
        if sans:
            env["PATH"] = path_sans(sans)
        rendu = subprocess.run(
            [sys.executable, str(copie / script.relative_to(RACINE)), "--verifie"],
            capture_output=True,
            text=True,
            check=False,
            env=env,
        )
        return rendu.returncode, rendu.stdout + rendu.stderr

    echecs = []

    with tempfile.TemporaryDirectory() as tmp:
        temoin, _ = lance(copie_jetable(tmp))
        print(f"  {'temoin, arbre sain':30s} -> {'vert' if temoin == 0 else f'ROUGE ({temoin})'}")
        if temoin != 0:
            echecs.append("le temoin rougit, donc les rouges qui suivent ne prouvent rien")

    for nom, monter, attendu in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = copie_jetable(tmp)
            monter(copie)
            code, _ = lance(copie)
            veut = "rouge" if attendu else "vert"
            obtenu = "rouge" if code == 1 else ("vert" if code == 0 else f"code {code}")
            print(f"  {nom:30s} -> {obtenu} (attendu {veut})")
            if code != attendu:
                echecs.append(nom)

    # ⟨ce que le code de sortie ne dit pas⟩ Les cas ci-dessus n assertent qu un code, et c est
    # precisement ce qui a laisse passer le defaut de #5774 : le garde sortait bien non nul, et son
    # TEXTE accusait le diff. Les cas qui suivent lisent la sortie.
    # `forme_a_rougi` et non « marque » : le second rendu de la fabrique est un LECTEUR qu on
    # APPELLE, pas la marque elle-meme. Nomme comme une marque, il finit lu comme une marque, et
    # `if marque:` est alors toujours vrai - le defaut que la fabrique documente deja pour la
    # liste nue. Un nom qui mente ne rougit nulle part.
    assertion, forme_a_rougi = cas_d_auto_test()
    comptes = [0]

    def verifie(libelle: str, obtenu: object, attendu: object) -> None:
        comptes[0] += 1
        assertion(libelle, obtenu, attendu)

    with tempfile.TemporaryDirectory() as tmp:
        copie = copie_jetable(tmp)
        sans_binaire(copie)
        code, sortie = lance(copie)
        declare = lit_le_refus(sortie)
        verifie("outil absent : le refus sort en 2, « je n ai pas pu juger »", code, 2)
        verifie(
            "outil absent : la forme DECLAREE est lue par la porte",
            lambda: declare is not None,
            True,
        )
        verifie(
            "outil absent : le titre des constats n apparait PAS",
            lambda: "Invocations d OpenSpec qui n existent pas" in sortie,
            False,
        )

    with tempfile.TemporaryDirectory() as tmp:
        copie = copie_jetable(tmp)
        code, sortie = lance(copie, sans="node")
        declare = lit_le_refus(sortie)
        verifie("interprete absent : le refus sort en 2", code, 2)
        verifie("interprete absent : la forme declaree est lue", lambda: declare is not None, True)
        verifie(
            "interprete absent : la cause NOMME l interprete",
            lambda: declare is not None and "node" in declare[0],
            True,
        )
        verifie(
            "interprete absent : la cause ne renvoie PAS a « npm ci »",
            lambda: declare is not None and "npm ci" in declare[0],
            False,
        )
        verifie(
            "interprete absent : aucun gestionnaire de version n est nomme",
            lambda: any(g in sortie for g in ("nvm", "asdf", "volta", "fnm")),
            False,
        )
        verifie(
            "interprete absent : le titre des constats n apparait PAS",
            lambda: "Invocations d OpenSpec qui n existent pas" in sortie,
            False,
        )
        # ⟨LE « QUAND », observable sans couture⟩ Dette nommee a la cloture de #5762. Si le refus
        # arrivait APRES avoir lance l outil, la sortie porterait l erreur du lanceur - « env: node
        # introuvable ». Son absence prouve que rien n a ete lance, et aucun cas ne l eprouvait.
        verifie(
            "interprete absent : l outil n est PAS lance, le refus arrive AVANT",
            lambda: "env:" in sortie,
            False,
        )

    # Le gage du module partage, joue ici ET dans l autre garde qui l appelle (ADR 5483).
    for libelle, tenu in prerequis.verifie_grammaire():
        verifie(f"prerequis : {libelle}", tenu, True)

    if forme_a_rougi():
        echecs.append("des cas de forme rougissent, lire les lignes marquees ci-dessus")

    if echecs:
        print("\nLe garde ne tient pas : " + ", ".join(echecs), file=sys.stderr)
        return 1
    print(
        f"\nAuto-test concluant : vert sur l arbre sain, {len(cas)} cas de code de sortie et"
        f" {comptes[0]} cas de forme rendent leur verdict."
    )
    return 0


CONTRAT = {
    "geste": "invocation OpenSpec citee et qui n existe pas",
    "population": "les competences openspec-* de .agents et .claude",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-sous-commandes-openspec.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())

    # Le refus AVANT les constats, et son canal est le sien. `refuse` sort en 2 : « je n ai pas pu
    # juger », que le depot distingue de « j ai juge et c est rouge » (#5485, 28 sites sur 50).
    empeche = refus_de_conclure(RACINE)
    if empeche:
        refuse(*empeche)

    trouves = ecarts(RACINE)
    if trouves:
        print("Invocations d OpenSpec qui n existent pas :", file=sys.stderr)
        for e in trouves:
            print(f"  {e}", file=sys.stderr)
        print(
            "\nLa liste du valide est lue de l aide de la ligne de commande epinglee, jamais recopiee "
            "ici : une liste recopiee serait un second inventaire a tenir. Si l invocation est bonne "
            "et l outil a change, c est la version epinglee qu il faut reprendre.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(
        f"{len(fichiers(RACINE))} fichier(s) d OpenSpec relus : toutes les invocations citees existent."
    )
