#!/usr/bin/env python3
"""Quatre concordances relient commandes, tableau des passes et competences (#4918).

Aucune n etait tenue, et l une d elles s est rompue : le 2026-08-30, `ecrire-une-adr` annoncait
« pass 10 » quand le tableau lui attribue les passes 0 et 11. Sa description etait perimee depuis
#4518, qui a fait de l archivage la passe 10.

## Les quatre, et ce qu elles promettent

| # | La concordance | Ce qui arrive quand elle se rompt |
|---|---|---|
| 1 | une COMMANDE ouvre une competence qui existe | l agent est envoye nulle part |
| 2 | le TABLEAU nomme une competence qui existe | la passe s execute sans description |
| 3 | la DESCRIPTION annonce la passe du tableau | la competence s ouvre au mauvais moment |
| 4 | aucune competence n est ORPHELINE | elle existe et rien ne l appelle |

## Pourquoi `passes-citees-existent.py` ne voyait pas la troisieme

Son motif reconnait « pass 10 », et la passe 10 EXISTE. Il verifie qu une passe citee existe, ce qu
il annonce et fait correctement. Ce garde-ci verifie autre chose : une CONCORDANCE entre deux
inventaires, non l existence d un numero.

## Deux cas se traitent, et ne s ecartent pas

Une competence peut servir DEUX passes quand elles sont les deux bouts d un meme geste, ce que l ADR
4902 autorise : `ecrire-une-adr` porte la 0 et la 11. Sa description doit alors annoncer l une des
deux, pas une troisieme.

Une competence d APPUI est nommee au tableau sans porter la passe : `humaniser`, `tdd`, `mutation`.
Le tableau les marque « en appui », et elles n annoncent aucune passe. Une description muette n est
donc pas une faute.

## Ce qu il ne verifie PAS, et c est ecrit plutot que suppose

Qu une competence DISE VRAI. Il lit des renvois et des declarations, jamais leur contenu. Une
competence qui annonce la bonne passe et decrit la mauvaise procedure lui echappe entierement.

Usage :
    python3 scripts/methode/concordances-du-cycle.py
    python3 scripts/methode/concordances-du-cycle.py --auto-test
"""

import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
FONDS = RACINE / ".agents" / "skills"
COMMANDES = RACINE / ".claude" / "commands"
ORCHESTRATEUR = FONDS / "clore-un-chantier" / "SKILL.md"

# Une ligne de passe : « | 6b | Tests | ... | `couvrir-les-usages-livres`, `tdd` en appui | ».
LIGNE_PASSE = re.compile(r"^\| (\d+b?) \|.*\|([^|]*)\|\s*$", re.M)
# Un nom de competence, entre chevrons inverses.
NOM = re.compile(r"`([a-z][a-z0-9-]+)`")
# Ce qu une description annonce : « Use at closure pass 6, ... ».
ANNONCE = re.compile(r"^description:.*?\bpass (\d+b?)\b", re.M | re.S)
# La marque d une competence d appui dans la cellule du tableau.
APPUI = re.compile(r"`([a-z][a-z0-9-]+)`(?=[^`]*\ben appui\b)")


def competences(fonds: pathlib.Path) -> set[str]:
    """Les competences du fonds, par leur nom de dossier."""
    return {p.name for p in fonds.iterdir() if p.is_dir() and (p / "SKILL.md").is_file()}


def attributions(orchestrateur: pathlib.Path) -> dict[str, list[str]]:
    """Ce que le tableau attribue : competence -> passes, hors compétences d appui."""
    texte = orchestrateur.read_text(encoding="utf-8")
    par_competence: dict[str, list[str]] = {}
    for numero, cellule in LIGNE_PASSE.findall(texte):
        appuis = set(APPUI.findall(cellule))
        for nom in NOM.findall(cellule):
            if nom not in appuis:
                par_competence.setdefault(nom, []).append(numero)
    return par_competence


def ouvertures(commandes: pathlib.Path) -> dict[str, set[str]]:
    """Ce que chaque commande nomme comme competence."""
    par_commande: dict[str, set[str]] = {}
    if not commandes.is_dir():
        return par_commande
    for fichier in sorted(commandes.glob("*.md")):
        par_commande[fichier.stem] = set(NOM.findall(fichier.read_text(encoding="utf-8")))
    return par_commande


def suspects(
    fonds: pathlib.Path = FONDS,
    orchestrateur: pathlib.Path = ORCHESTRATEUR,
    commandes: pathlib.Path = COMMANDES,
) -> list[str]:
    """Les quatre concordances, dans l ordre du tableau de l en-tete."""
    fautes: list[str] = []
    presentes = competences(fonds)
    attrib = attributions(orchestrateur)
    ouvre = ouvertures(commandes)

    # 1. Une commande ouvre une competence qui existe.
    for commande, nommees in sorted(ouvre.items()):
        for nom in sorted(nommees - presentes):
            fautes.append(f"la commande /{commande} ouvre `{nom}`, qui n existe pas")

    # 2. Le tableau nomme une competence qui existe.
    for nom in sorted(set(attrib) - presentes):
        fautes.append(f"le tableau des passes nomme `{nom}`, qui n existe pas")

    # 3. La description annonce la passe que le tableau attribue.
    for nom in sorted(presentes):
        texte = (fonds / nom / "SKILL.md").read_text(encoding="utf-8")
        annoncee = ANNONCE.search(texte)
        if not annoncee:
            continue
        dites = attrib.get(nom, [])
        if not dites:
            fautes.append(
                f"`{nom}` annonce la passe {annoncee.group(1)}, que le tableau ne lui attribue pas"
            )
        elif annoncee.group(1) not in dites:
            fautes.append(
                f"`{nom}` annonce la passe {annoncee.group(1)}, le tableau lui attribue {' et '.join(dites)}"
            )

    # 4. Aucune competence orpheline. Le corpus qui la nomme est plus large que le tableau : une
    # competence d appoint comme `deboguer` vit dans les pages de methode et non dans le cycle des
    # passes. Les y ignorer produisait trois faux positifs sur l arbre reel.
    citees = set(attrib) | {n for noms in ouvre.values() for n in noms}
    pages = [
        fonds.parent.parent / f
        for f in ("AGENTS.md", "CONTRIBUTING.md", "CLAUDE.md", "dev-docs/cycle-de-chantier.md")
    ]
    corpus = "".join(p.read_text(encoding="utf-8") for p in pages if p.is_file())
    corpus += "".join(
        (fonds / autre / "SKILL.md").read_text(encoding="utf-8") for autre in presentes
    )
    for nom in sorted(presentes - citees):
        propre = (fonds / nom / "SKILL.md").read_text(encoding="utf-8").count(nom)
        if corpus.count(nom) <= propre:
            fautes.append(
                f"`{nom}` n est nommee par rien : ni commande, ni tableau, ni page, ni autre competence"
            )

    return fautes


def _bac(
    tmp: pathlib.Path, table: str, descriptions: dict[str, str], commandes: dict[str, str]
) -> tuple[pathlib.Path, pathlib.Path, pathlib.Path]:
    """Un arbre minimal : le fonds, l orchestrateur, les commandes."""
    fonds = tmp / "skills"
    for nom, description in descriptions.items():
        (fonds / nom).mkdir(parents=True, exist_ok=True)
        (fonds / nom / "SKILL.md").write_text(
            f"---\nname: {nom}\ndescription: {description}\n---\n\n# {nom}\n", encoding="utf-8"
        )
    orch = fonds / "clore-un-chantier" / "SKILL.md"
    orch.parent.mkdir(parents=True, exist_ok=True)
    orch.write_text(
        "---\nname: clore-un-chantier\ndescription: rien\n---\n\n" + table, encoding="utf-8"
    )
    cmds = tmp / "commands"
    cmds.mkdir(parents=True, exist_ok=True)
    for nom, corps in commandes.items():
        (cmds / f"{nom}.md").write_text(corps, encoding="utf-8")
    return fonds, orch, cmds


def _auto_test() -> int:
    """Vert sur un arbre sain, ROUGE sur chacune des quatre concordances rompues.

    Un garde vu rouge sur un seul de ses controles ne prouve rien des trois autres, d ou un cas
    casse par concordance et non un cas casse global.
    """
    import tempfile

    SAINE = "| 1 | Audit | ce qu il rend | `auditer` |\n| 6 | Tests | ce qu il rend | `couvrir`, `appui` en appui |\n"
    DESC = {
        "auditer": "Use at closure pass 1, pour auditer.",
        "couvrir": "Use at closure pass 6, pour couvrir.",
        "appui": "Une competence d appui, qui n annonce aucune passe.",
        "clore-un-chantier": "rien",
    }
    CMD = {"clore": "Ouvrir la competence `clore-un-chantier`."}

    # Le cas 1 detourne la commande SANS orpheliner `clore-un-chantier` : le tableau doit donc la
    # nommer, sans quoi le cas rougit par la concordance 4 et ne prouve rien de la 1.
    AVEC_ORCH = SAINE + "| 12 | Bilan | ce qu il rend | `clore-un-chantier` |\n"

    cas = [
        ("temoin, arbre sain", SAINE, DESC, CMD, None),
        (
            "1. une commande ouvre une competence absente",
            AVEC_ORCH,
            DESC,
            {"clore": "Ouvrir la competence `jamais-ecrite`."},
            "la commande /clore ouvre `jamais-ecrite`",
        ),
        (
            "2. le tableau nomme une competence absente",
            SAINE + "| 7 | Harmo | ce qu il rend | `jamais-ecrite` |\n",
            DESC,
            CMD,
            "le tableau des passes nomme `jamais-ecrite`",
        ),
        (
            "3. la description annonce une autre passe que le tableau",
            SAINE,
            {**DESC, "auditer": "Use at closure pass 9, pour auditer."},
            CMD,
            "`auditer` annonce la passe 9, le tableau lui attribue 1",
        ),
        (
            "4. une competence n est nommee par rien",
            SAINE,
            {
                **DESC,
                "orpheline": "Une competence que rien n appelle, et qui n annonce aucune passe.",
            },
            CMD,
            "`orpheline` n est nommee par rien",
        ),
        ("une competence d appui muette ne fait pas rougir", SAINE, DESC, CMD, None),
        (
            "une competence servant DEUX passes, annoncant la SECONDE",
            SAINE + "| 11 | ADR | ce qu il rend | `auditer` |\n",
            {**DESC, "auditer": "Use at closure pass 11, pour ecrire."},
            CMD,
            None,
        ),
        # Une competence d appui NE DOIT PAS annoncer de passe : le tableau la marque « en appui »
        # precisement parce qu elle ne la porte pas. Sans l exemption, elle serait attribuee et ce
        # cas passerait au vert.
        (
            "une competence d appui qui annonce une passe est refusee",
            SAINE,
            {**DESC, "appui": "Use at closure pass 6, en appui."},
            CMD,
            "`appui` annonce la passe 6, que le tableau ne lui attribue pas",
        ),
    ]

    echecs = 0
    rouges = sum(1 for *_, attendu in cas if attendu is not None)
    with tempfile.TemporaryDirectory() as brut:
        for libelle, table, desc, cmd, attendu in cas:
            tmp = pathlib.Path(brut) / re.sub(r"\W+", "-", libelle)
            fonds, orch, cmds = _bac(tmp, table, desc, cmd)
            rendues = suspects(fonds, orch, cmds)
            # Un cas qui rougit pour la MAUVAISE raison ne prouve rien : la premiere version de cet
            # auto-test ne lisait que « rouge ou non », et quatre mutations sur cinq y survivaient.
            if attendu is None:
                juste = not rendues
            else:
                juste = len(rendues) == 1 and attendu in rendues[0]
            if juste:
                print(f"  ✔ {libelle}")
            else:
                print(
                    f"  ✘ {libelle} : attendu {attendu or 'aucune faute'}, obtenu {rendues or 'aucune'}"
                )
                echecs = 1
    print()
    print(f"{len(cas)} cas, dont {rouges} qui DOIVENT rougir, un par concordance.")
    print(
        "Auto-test concluant : le garde voit les quatre." if not echecs else "Auto-test EN ECHEC."
    )
    return echecs


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(_auto_test())
    fautes = suspects()
    if fautes:
        print("Concordances rompues :")
        for faute in fautes:
            print(f"  {faute}")
        raise SystemExit(1)
    print(f"Les quatre concordances du cycle tiennent, sur {len(competences(FONDS))} competences.")
