#!/usr/bin/env python3
"""Quels harnais d auto-test ne peuvent pas NOMMER le cas qui leve (#5530).

L ADR 4918 refuse qu un cas rouge le soit pour la mauvaise raison : un auto-test qui s arrete sur
une trace de pile sort non nul, donc la CI l attrape, mais il ne dit pas lequel de ses controles a
rougi. #5444, #5460 et #5461 ont converti vingt-trois harnais pour cela.

## Pourquoi ce releve existe

**Leurs deux populations etaient definies par un IDIOME**, la forme de l ecriture :

- #5460 : les gardes portant une definition LOCALE de `verifie` ;
- #5461 : ceux construisant leurs cas en `cas.append((libelle, expression))`.

`scripts/batterie.py` ne remplit ni l un ni l autre - aucune definition locale, aucun `cas.append` -
et portait pourtant le defaut, sur seize sites. **Une troisieme forme d ecriture echappait a deux
populations definies par les deux premieres.** C est le motif que ce depot appelle « un idiome n est
pas une capacite » : la question n est pas « ce garde ecrit-il `cas.append` » mais « son harnais
nomme-t-il le cas qui leve ».

## Ce que ce releve derive, et comment

Il ne connait aucun idiome. Il lit ce que le harnais FAIT de son echec, et distingue deux formes :

- **B** : le harnais marque l echec LUI-MEME, en ligne - `echecs += 1`, `marque[0] = 1` - sans
  qu aucune aide ne detienne le libelle. Aucun de ses cas ne peut se nommer, par construction ;
- **A** : une aide detient le libelle, mais des cas lui sont passes EN VALEUR. `cas_d_auto_test`
  n attrape que si l expression lui est DIFFEREE - un appelable - car une valeur est evaluee chez
  l appelant, avant que l aide prenne la main.

Un harnais dont tous les cas sont differes n y figure pas : c est le temoin negatif.

Usage : releve-les-harnais-muets.py [--auto-test]
"""

from __future__ import annotations

import ast
import pathlib
import sys

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test, rapporte, sort_si_contrat_demande

# Les dossiers ou vivent les gardes du depot. Une LISTE, et elle est declaree : la deriver des
# ateliers donnerait la population d UN flux, quand ce releve veut celle du depot (article A3).
#
# Trois entrees et non cinq depuis #5570. Le balayage etant devenu recursif, `scripts/adr` et
# `scripts/methode` etaient devenus redondants avec `scripts`, et cette redondance n etait pas
# inoffensive : elle a NEUTRALISE une mutation. Ecarter les fichiers dont le parent est la base ne
# retirait presque rien, puisque `scripts/adr/x.py` reste atteint par le rglob parti de `scripts`.
# Un temoin a donc paru tenir sans discriminer. Mesure : les deux declarations rendent les memes
# 161 fichiers, ecart nul.
DOSSIERS = ("scripts", ".github/scripts", ".github/assets")

# Les noms sous lesquels un harnais marque son echec lui-meme.
MARQUES = ("echecs", "echec", "rouges")

# Une expression evaluee chez l appelant ne peut lever que si elle APPELLE ou INDEXE. Un litteral,
# un nom nu ou une comparaison de constantes ne levent pas, et les compter gonflerait le releve.
PEUT_LEVER = (ast.Call, ast.Subscript)

# Une expression DIFFEREE : le harnais l evalue lui-meme, donc il peut la nommer.
DIFFEREE = (ast.Lambda, ast.Name, ast.Attribute)


def fichiers() -> list[pathlib.Path]:
    """Les fichiers Python des dossiers de gardes et de LEURS sous-dossiers, sans doublon.

    Le balayage est RECURSIF depuis #5570, et il ne l etait pas. Un `glob("*.py")` ne voit que le
    premier niveau : `scripts` y figurait, donc ses fichiers de tete etaient lus, mais
    `scripts/graphify`, `scripts/mkdocs`, `scripts/plateforme-de-test` et `scripts/qualite` ne
    l etaient pas. Mesure du 2026-10-01 : HUIT gardes dont la porte joue l auto-test etaient hors
    population, et SIX d entre eux muets. Le compte passait de `muets=75` a `muets=81`.

    Un cliquet pose sur cette population-la aurait verrouille le mauvais chiffre, et le jour ou
    quelqu un aurait nomme les quatre dossiers il aurait rougi pour une raison qui n est pas la
    sienne : non qu un harnais muet ait ete ajoute, mais que la mesure ait commence a dire vrai.

    Le recursif se derive, une liste de sous-dossiers s enumere et vieillit. C est le meme choix que
    le compte de reference pris sur l en-tete d un tableau plutot qu inscrit dans un garde (#5700).

    La regle des noms a tiret bas ne bouge pas : ce sont des aides, pas des gardes. Elle ne couvre
    pas les DOSSIERS prives, et c est sans effet aujourd hui, mesure : les deux harnais de
    `scripts/_commun` rendent des comptes nuls, donc le releve les ecarte de toute facon. Y ajouter
    une regle sur les dossiers serait une branche que rien ne peut faire rougir.
    """
    vus: dict[pathlib.Path, None] = {}
    for dossier in DOSSIERS:
        base = RACINE / dossier
        if not base.is_dir():
            continue
        for f in sorted(base.rglob("*.py")):
            if not f.name.startswith("_"):
                vus[f.resolve()] = None
    return list(vus)


def harnais(arbre: ast.Module) -> list[ast.FunctionDef]:
    """Les fonctions d auto-test du fichier, reconnues a ce qu elles SONT, non a leur place."""
    return [
        n
        for n in ast.walk(arbre)
        if isinstance(n, ast.FunctionDef) and "auto" in n.name.lower() and "test" in n.name.lower()
    ]


def marque_en_ligne(n: ast.AST) -> bool:
    """Ce noeud marque-t-il l echec directement, sans passer par une aide ?"""
    if isinstance(n, ast.AugAssign) and isinstance(n.target, ast.Name):
        return n.target.id in MARQUES
    if isinstance(n, ast.Assign):
        return any(
            isinstance(c, ast.Subscript)
            and isinstance(c.value, ast.Name)
            and c.value.id == "marque"
            for c in n.targets
        )
    return False


def comptes(source: str) -> tuple[int, int, int]:
    """(sites en ligne, cas passes en VALEUR qui peuvent lever, cas DIFFERES) de ce fichier."""
    try:
        arbre = ast.parse(source)
    except SyntaxError:
        return (0, 0, 0)
    en_ligne = valeur = differe = 0
    for t in harnais(arbre):
        for n in ast.walk(t):
            if marque_en_ligne(n):
                en_ligne += 1
                continue
            if not (
                isinstance(n, ast.Call)
                and isinstance(n.func, ast.Name)
                and len(n.args) >= 2
                and isinstance(n.args[0], ast.Constant)
                and isinstance(n.args[0].value, str)
            ):
                continue
            expression = n.args[1]
            if isinstance(expression, DIFFEREE):
                differe += 1
            elif any(isinstance(x, PEUT_LEVER) for x in ast.walk(expression)):
                valeur += 1
    return (en_ligne, valeur, differe)


def releve() -> tuple[list[tuple[int, str]], list[tuple[int, str]], int]:
    """Les deux formes muettes, et le compte des harnais entierement differes."""
    forme_b: list[tuple[int, str]] = []
    forme_a: list[tuple[int, str]] = []
    sains = 0
    for f in fichiers():
        source = f.read_text(encoding="utf-8", errors="ignore")
        if "--auto-test" not in source:
            continue
        en_ligne, valeur, differe = comptes(source)
        if not (en_ligne or valeur or differe):
            continue
        nom = str(f.relative_to(RACINE))
        if en_ligne:
            forme_b.append((en_ligne, nom))
        elif valeur:
            forme_a.append((valeur, nom))
        else:
            sains += 1
    forme_b.sort(key=lambda c: (-c[0], c[1]))
    forme_a.sort(key=lambda c: (-c[0], c[1]))
    return (forme_b, forme_a, sains)


def suspects() -> tuple[list[str], int, int, int]:
    """Un suspect par harnais muet, et les trois comptes qui disent de quoi la population est faite.

    Un suspect PAR HARNAIS, et non par site : la dette se resorbe harnais par harnais, et compter les
    sites ferait descendre le cliquet quand un gros harnais maigrit sans cesser d etre muet.
    """
    forme_b, forme_a, sains = releve()
    lignes = [f"{nom} : {n} site(s), marque posee EN LIGNE (famille B)" for n, nom in forme_b]
    lignes += [f"{nom} : {n} cas passe(s) EN VALEUR a son aide (famille A)" for n, nom in forme_a]
    return lignes, len(forme_b), len(forme_a), sains


def main() -> int:
    muets, b, a, sains = suspects()
    print(
        f"  B. la marque d echec est posee EN LIGNE, aucune aide ne tient le libelle : {b}\n"
        f"  A. une aide tient le libelle, mais des cas lui sont passes EN VALEUR : {a}\n"
        f"  harnais dont TOUS les cas sont differes : {sains}\n"
    )
    code = rapporte(
        "5570",
        "harnais qui ne peuvent pas nommer le cas qui leve",
        muets,
        apercu=12,
        lus=b + a + sains,
    )
    print(
        "\nUn cas qui leve dans l un d eux arrete son temoin sans dire lequel a rougi (ADR 4918).\n"
        "La capacite de s y conformer existe depuis #5444 : `cas_d_auto_test` accepte un appelable."
    )
    return code


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()
    print("Auto-test du releve des harnais muets (#5530) :")

    en_ligne = (
        "def _auto_test():\n"
        "    echecs = 0\n"
        "    if calcule():\n"
        "        echecs += 1\n"
        "    return echecs\n"
    )
    verifie("la marque posee en ligne est vue", lambda: comptes(en_ligne)[0], 1)

    valeur = (
        "def _auto_test():\n"
        "    verifie, echecs = cas_d_auto_test()\n"
        '    verifie("un cas", calcule(), True)\n'
        "    return echecs()\n"
    )
    verifie("un cas passe en VALEUR est compte muet", lambda: comptes(valeur)[1], 1)

    # LE temoin negatif que l issue #5530 demande, et il porte tout : un harnais entierement
    # differe ne doit figurer dans AUCUNE des deux formes. Sans ce cas, un releve qui compterait
    # tout le monde passerait les deux precedents.
    differe = (
        "def _auto_test():\n"
        "    verifie, echecs = cas_d_auto_test()\n"
        '    verifie("un cas", lambda: calcule(), True)\n'
        "    return echecs()\n"
    )
    verifie("un cas DIFFERE n est pas compte muet", lambda: comptes(differe)[:2], (0, 0))
    verifie("et il est compte comme differe", lambda: comptes(differe)[2], 1)

    # Une expression qui ne peut pas lever ne se compte pas : sinon le releve gonflerait de cas
    # inoffensifs, et son nombre cesserait de designer quelque chose.
    inoffensif = (
        "def _auto_test():\n"
        "    verifie, echecs = cas_d_auto_test()\n"
        '    verifie("un cas", 1, 1)\n'
        "    return echecs()\n"
    )
    verifie("un litteral ne peut pas lever, donc ne compte pas", lambda: comptes(inoffensif)[1], 0)

    # La population reelle DISCRIMINE : elle n est ni vide ni totale. Sans ce cas, un releve qui
    # rendrait toujours zero ou toujours tout passerait tous les precedents.
    forme_b, forme_a, sains = releve()
    verifie(
        "sur le depot, il trouve des harnais muets", lambda: len(forme_b) + len(forme_a) > 0, True
    )
    verifie("et il en epargne d autres", lambda: sains > 0, True)
    verifie("la porte du depot y figure", lambda: any("batterie.py" in n for _, n in forme_b), True)

    # Un fichier qui ne porte pas d auto-test n entre pas dans la population.
    verifie("un fichier sans harnais rend des comptes nuls", lambda: comptes("x = 1\n"), (0, 0, 0))

    # #5570 : la population DESCEND sous les dossiers declares. Le balayage ne le faisait pas, et
    # huit gardes dont la porte joue l auto-test en etaient absents, six muets. Le cas se derive des
    # DOSSIERS plutot que de nommer un fichier : un fichier renomme le rendrait vert a tort.
    declares = {RACINE / d for d in DOSSIERS}
    dessous = [p for p in fichiers() if p.parent not in declares]
    dessus = [p for p in fichiers() if p.parent in declares]
    print(f"     population : {len(dessus)} au premier niveau, {len(dessous)} dans un sous-dossier")
    verifie("la population descend sous les dossiers declares", lambda: len(dessous) > 0, True)
    # ET LE CONTRASTE, sans quoi un balayage qui ne rendrait QUE les sous-dossiers passerait le cas
    # precedent : le premier niveau est toujours lu.
    verifie("et elle lit toujours le premier niveau", lambda: len(dessus) > 0, True)

    # Un suspect PAR HARNAIS, jamais par site : compter les sites ferait descendre le cliquet quand
    # un gros harnais maigrit sans cesser d etre muet, donc le resserrerait sur une fausse bonne
    # nouvelle. Le cas confronte la longueur de la liste aux deux comptes de familles.
    muets, b, a, _ = suspects()
    verifie("un suspect par harnais, pas par site", lambda: len(muets) == b + a, True)
    verifie("et chaque suspect nomme sa famille", lambda: all("famille" in s for s in muets), True)
    return echecs()


# Pourquoi `cliquet` et plus `rapport` (#5570). La phrase qui vivait ici disait : « il COMPTE, il ne
# juge pas, et refuser dessus reviendrait a bloquer le depot sur une dette que ce releve sert a rendre
# visible. » C est l argument d un BUTOIR, et il est faux d un cliquet : pose a la valeur mesuree, il
# n exige rien des 81 harnais muets et exige tout des suivants. Elle est remplacee et non laissee a
# cote, parce que deux arguments opposes dans un meme depot font appliquer celui qu on trouve d abord.
#
# Ce qui a rendu la question urgente : le compte etait SUBI. `lus=89 muets=74` en septembre,
# `lus=93 muets=75` trois semaines plus tard, sans que personne l ait decide.
CONTRAT = {
    "geste": "harnais d auto-test qui ne peuvent pas nommer le cas qui leve",
    "population": "les fichiers Python portant `--auto-test` dans les dossiers de gardes du depot. "
    "Il lit ce que le harnais FAIT de son echec, jamais la forme de son ecriture : les populations "
    "de #5460 et #5461 etaient definies par un idiome, et une troisieme forme y echappait "
    "(`scripts/batterie.py`, seize sites). Il ne voit pas un harnais dont l aide vit dans un autre "
    "module et porte un autre nom que `verifie` : ses comptes sont des MINORANTS",
    "dispositif": "cliquet",
    "seuil": "81, polarite=descend",
    "temoin": "scripts/methode/releve-les-harnais-muets.py --auto-test",
    "decision": "ADR 5570",
    # Devenu obligatoire en devenant cliquet : l ADR 5340 compte les gardes qui portent un CONTRAT
    # sans dire ce qu ils lisent, et le repli LANCE ceux qui se taisent. Mesure : passer de `rapport`
    # a `cliquet` a fait entrer ce fichier dans sa population, suspects=43 contre un cliquet de 42.
    # Les trois entrees sont exactement DOSSIERS, et le `**` dit que le balayage est recursif.
    "chemins": """
scripts/**
.github/scripts/**
.github/assets/**
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(_auto_test() if "--auto-test" in sys.argv else main())
