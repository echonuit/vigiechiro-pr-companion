#!/usr/bin/env python3
"""Garde contre la regeneration accidentelle des competences OpenSpec adoptees.

Les six competences `openspec-*` ont ete ADOPTEES par #4515 : reecrites en francais et tissees au
cycle de ce depot. Elles ne sont plus du texte amont, et le depot en est desormais l auteur.

**Ce que la commande amont ferait, et que rien ne disait.** `openspec update --force` rend les six
fichiers a l anglais amont. Ce n est pas un defaut de l outil : la commande fait exactement ce
qu elle promet, et c est nous qui avons change de camp. Mesure du chantier #4511 : apres un
`--force`, le marqueur `langue: fr` retombe a ZERO sur les six fichiers reecrits. Sans `--force`, la
commande ne reecrit rien et repond « All tools up to date ».

Ce garde exige donc dans chaque competence deux marqueurs propres au depot, que l amont ne peut pas
produire :

    langue: fr    le choix de langue, declare dans l en-tete
    origine:      la phrase qui dit d ou vient le fichier et quelle ADR l a adopte

Un fichier regenere les perd tous les deux. Le garde nomme alors le fichier ET la commande qui l a
probablement ecrase, parce que « il manque un marqueur » sans cette phrase enverrait chercher une
faute de frappe.

**Un refus PAR ENTREE de corpus, et non sur le total.** Une liste de chemins dont un membre a
disparu rend encore des fichiers : un garde qui ne refuserait que sur un total nul resterait vert en
ayant verifie la moitie de ce qu il annonce. C est l article A3, et l [ADR 2748] « un dispositif qui
peut ne rien verifier le dit ». Les deux gardes OpenSpec anterieurs portent ce defaut, et #4566 le
traite chez eux ; celui-ci ne le porte pas.

**Sur l ADR 3645, qui veut qu un detecteur textuel s exclue de son corpus.** La question se pose
puisque ce fichier NOMME les deux marqueurs qu il cherche. Elle se resout sans exemption, comme pour
`verifie-version-openspec.py` : le corpus n est pas un balayage de l arbre mais deux motifs fixes,
`.agents/skills/openspec-*/SKILL.md` et `.claude/skills/openspec-*/SKILL.md`, qui ne peuvent pas
atteindre `scripts/methode/`. La raison est ecrite ici pour que la question ne se repose pas.

    --verifie   : ne rien ecrire, sortir 1 sur un ecart (garde de CI). C est aussi le defaut.
    --auto-test : eprouver le garde sur une copie jetable, et sortir 1 s il reste vert la ou il
                  devrait rougir, ou s il rougit sur un arbre sain.
"""

import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts" / "adr"))
from _commun import sort_si_contrat_demande

# Les deux arbres. `.agents/skills` est le fonds, `.claude/skills` sa copie tenue par
# `synchronise-adaptateurs.py`. Les deux doivent porter les marqueurs : la copie est ce que Claude
# Code lit, et c est elle que `openspec update --force` ecrase en premier.
CORPUS = (
    (pathlib.Path(".agents") / "skills", "openspec-*/SKILL.md"),
    (pathlib.Path(".claude") / "skills", "openspec-*/SKILL.md"),
)

MARQUEURS = (
    ("langue: fr", re.compile(r"^\s*langue:\s*fr\s*$", re.M)),
    ("origine:", re.compile(r"^\s*origine:\s*\S.*$", re.M)),
)

RAPPEL = (
    "Un fichier qui a perdu ses marqueurs a probablement ete reecrit par « openspec update "
    "--force », qui rend les competences a l anglais amont. Le remede est de restaurer le fichier "
    "francais (git checkout), pas de rajouter le marqueur en tete d un texte anglais."
)


def entrees(racine: pathlib.Path) -> list[tuple[str, list[pathlib.Path]]]:
    """Pour chaque entree du corpus, son libelle et les fichiers qu elle rend.

    Une entree absente rend une liste VIDE plutot que d etre sautee : c est ce qui permet a
    `ecarts` de refuser par entree au lieu de refuser sur le total.
    """
    rendu = []
    for base, motif in CORPUS:
        dossier = racine / base
        trouves = sorted(dossier.glob(motif)) if dossier.is_dir() else []
        rendu.append((str(base), trouves))
    return rendu


def ecarts(racine: pathlib.Path) -> list[str]:
    """Tout ce qui rompt l adoption. Liste vide = le garde est au vert."""
    trouves = []
    comptes = []

    for libelle, fichiers in entrees(racine):
        if not fichiers:
            trouves.append(
                f"{libelle} ne rend aucune competence openspec-* : cette entree du corpus est "
                "vide, donc le garde ne verifie rien de ce cote"
            )
            continue
        comptes.append((libelle, len(fichiers)))
        for fichier in fichiers:
            texte = fichier.read_text(encoding="utf-8")
            court = fichier.relative_to(racine)
            for nom, motif in MARQUEURS:
                if not motif.search(texte):
                    trouves.append(f"{court} ne porte plus « {nom} »")

    if len({n for _, n in comptes}) > 1:
        detail = ", ".join(f"{libelle} en rend {n}" for libelle, n in comptes)
        trouves.append(
            f"les arbres ne portent pas le meme nombre de competences : {detail}. La copie a "
            "derive du fonds, ou l un des deux a ete reecrit"
        )

    return trouves


def auto_test() -> int:
    """Un arbre sain doit etre VERT, et chaque etat casse doit etre ROUGE.

    Le vert du temoin compte autant que les rouges : un garde qui rougit sur tout rougirait aussi
    sur les etats casses, et ses rouges ne diraient rien.
    """
    script = pathlib.Path(__file__).resolve()

    def retire_langue(r: pathlib.Path) -> None:
        fichier = entrees(r)[0][1][0]
        texte = fichier.read_text(encoding="utf-8")
        fichier.write_text(
            re.sub(r"^\s*langue:\s*fr\s*$\n", "", texte, count=1, flags=re.M), encoding="utf-8"
        )

    def retire_origine(r: pathlib.Path) -> None:
        fichier = entrees(r)[1][1][0]
        texte = fichier.read_text(encoding="utf-8")
        fichier.write_text(
            re.sub(r"^\s*origine:\s*\S.*$\n", "", texte, count=1, flags=re.M), encoding="utf-8"
        )

    def retire_un_arbre(r: pathlib.Path) -> None:
        shutil.rmtree(r / CORPUS[1][0])

    def ampute_un_arbre(r: pathlib.Path) -> None:
        shutil.rmtree(entrees(r)[1][1][0].parent)

    def anglicise_tout(r: pathlib.Path) -> None:
        """Ce que `openspec update --force` produit : les marqueurs tombent partout a la fois."""
        for _, fichiers in entrees(r):
            for fichier in fichiers:
                texte = fichier.read_text(encoding="utf-8")
                for _, motif in MARQUEURS:
                    texte = motif.sub("", texte)
                fichier.write_text(texte, encoding="utf-8")

    cas = [
        ("langue: fr retire d un seul", retire_langue),
        ("origine: retire d un seul", retire_origine),
        ("un arbre entier absent", retire_un_arbre),
        ("un arbre ampute d une competence", ampute_un_arbre),
        ("les douze anglicises (--force)", anglicise_tout),
    ]

    def copie_jetable(tmp: str) -> pathlib.Path:
        copie = pathlib.Path(tmp) / "depot"
        for d in (".agents", ".claude", "scripts"):
            source = RACINE / d
            if source.exists():
                shutil.copytree(source, copie / d, symlinks=True)
        return copie

    def code_sur(copie: pathlib.Path) -> int:
        return subprocess.run(
            [sys.executable, str(copie / script.relative_to(RACINE)), "--verifie"],
            capture_output=True,
            check=False,
        ).returncode

    echecs = []

    with tempfile.TemporaryDirectory() as tmp:
        temoin = code_sur(copie_jetable(tmp))
        etat = "vert" if temoin == 0 else f"ROUGE (code {temoin})"
        print(f"  {'temoin, arbre sain':34s} -> {etat}")
        if temoin != 0:
            echecs.append("le temoin rougit, donc les rouges qui suivent ne prouvent rien")

    for nom, casser in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = copie_jetable(tmp)
            casser(copie)
            code = code_sur(copie)
            etat = "rouge" if code == 1 else f"VERT (code {code})"
            print(f"  {nom:34s} -> {etat}")
            if code != 1:
                echecs.append(nom)

    if echecs:
        print("\nLe garde ne tient pas : " + ", ".join(echecs), file=sys.stderr)
        return 1
    print(f"\nAuto-test concluant : vert sur l arbre sain, rouge sur les {len(cas)} etats casses.")
    return 0


CONTRAT = {
    "geste": "competence OpenSpec adoptee que la regeneration ecraserait",
    "population": "les competences openspec-* de .agents et .claude",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-adoption-openspec.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())

    trouves = ecarts(RACINE)
    if trouves:
        print("Adoption des competences OpenSpec rompue :", file=sys.stderr)
        for e in trouves:
            print(f"  {e}", file=sys.stderr)
        print("\n" + RAPPEL, file=sys.stderr)
        sys.exit(1)

    total = sum(len(f) for _, f in entrees(RACINE))
    print(f"{total} competence(s) OpenSpec adoptee(s), marqueurs intacts.")
