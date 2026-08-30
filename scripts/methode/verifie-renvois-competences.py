#!/usr/bin/env python3
"""Garde d integrite des renvois barre-oblique cites dans les competences.

Une competence qui renvoie l agent vers `/realiser` ou `/archiver` promet qu une commande de ce nom
existe. Si elle n existe pas, l agent est envoye nulle part, et rien ne le dit : le defaut attend
dans le texte et se manifeste le jour ou quelqu un suit l instruction, loin de sa cause.

**Le defaut qui a ouvert ce garde.** `openspec-apply-change` envoyait l etat `blocked` vers
`/opsx:continue`, une commande que ce depot n a jamais eue, sans repli. Le renvoi tombait au moment
precis ou l agent est bloque. Le texte amont portait le repli DANS la meme phrase ; la reecriture
francaise de #4515 a garde le renvoi et laisse tomber la parenthese.

**Deux familles, une seule regle.** Le garde de #4514 verifie les invocations de la LIGNE DE
COMMANDE, `openspec <sous-commande>`. Celui-ci verifie les commandes barre-oblique du CLIENT, qui
sont une autre famille : rien ne reliait un `/nom` cite a l existence du fichier correspondant.

Un renvoi est accepte dans deux cas, et deux seulement :

    1. `.claude/commands/<nom>.md` existe ;
    2. la competence DECLARE le flux optionnel, par la phrase « `/<nom>` est un flux optionnel »,
       et donne donc le repli a suivre quand il manque.

Le second cas n est pas une echappatoire : il porte le seul usage legitime, un flux amont que ce
depot n installe pas. `openspec-update-change` le fait pour ses six renvois vers `/opsx:continue`,
et c est pourquoi ils sont corrects la ou celui d `openspec-apply-change` ne l etait pas. Un
comptage aurait rendu sept defauts ; l ouverture des occurrences en rend un.

**Sur le motif, eprouve avant d etre cru.** Deux lectures fausses ont ete corrigees en le mesurant.
Exiger l apostrophe fermante juste apres le nom manquait `` `/realiser <autre>` ``, quatre
occurrences. Et `/tmp`, chemin de systeme de fichiers cite par la competence `worktree`, se
presentait comme une commande absente : il est exempte NOMINATIVEMENT, pour que l exemption ne
puisse pas s elargir en silence.

**Sur l ADR 3645.** Ce fichier nomme les renvois qu il cherche et porte la phrase de declaration
dans sa prose. Aucune exemption a declarer : le corpus n est pas un balayage de l arbre mais deux
motifs fixes sous `.agents/skills` et `.claude/skills`, qui ne peuvent pas atteindre
`scripts/methode/`.

**Refus PAR ENTREE de corpus** (#4566) : une entree dont le chemin a disparu rend une liste vide, et
le garde refuse au lieu de conclure sur ce qui reste.

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
COMMANDES = pathlib.Path(".claude") / "commands"

CORPUS = (
    (pathlib.Path(".agents") / "skills", "*/SKILL.md"),
    (pathlib.Path(".claude") / "skills", "*/SKILL.md"),
)

# Le nom peut porter un argument DANS les apostrophes : `/realiser <autre>`. Exiger l apostrophe
# fermante juste apres le nom manquait quatre occurrences, mesure.
RENVOI = re.compile(r"`(/[a-z][a-z0-9:_-]*)[^`]*`")

# Ce qui ressemble a un renvoi sans en etre un. Exemption NOMINATIVE : elle tient a un jeton, pour
# qu elle ne puisse pas s elargir en silence. `/tmp` est cite par la competence `worktree`, qui
# interdit d y placer un arbre de travail.
NON_COMMANDES = ("/tmp",)


def declaration_optionnelle(renvoi: str) -> re.Pattern:
    """La phrase par laquelle une competence assume un flux qu elle sait absent."""
    return re.compile(r"`" + re.escape(renvoi) + r"`\s*est un flux optionnel")


def entrees(racine: pathlib.Path) -> list[tuple[str, list[pathlib.Path]]]:
    """Pour chaque entree du corpus, son libelle et les SKILL.md qu elle rend.

    Une entree absente rend une liste VIDE plutot que d etre sautee, pour que `ecarts` refuse par
    entree au lieu de conclure sur ce qui reste (article A3, ADR 2748, #4566).
    """
    rendu = []
    for base, motif in CORPUS:
        dossier = racine / base
        rendu.append((str(base), sorted(dossier.glob(motif)) if dossier.is_dir() else []))
    return rendu


def commandes(racine: pathlib.Path) -> set[str]:
    """Les noms de commande que le depot expose, sans leur extension."""
    dossier = racine / COMMANDES
    return {p.stem for p in dossier.glob("*.md")} if dossier.is_dir() else set()


def ecarts(racine: pathlib.Path) -> list[str]:
    """Les renvois qui n ont ni cible ni repli. Liste vide = le garde est au vert."""
    trouves = []
    comptes = []

    for libelle, fichiers in entrees(racine):
        if not fichiers:
            trouves.append(
                f"{libelle} ne rend aucune competence : cette entree du corpus est vide, donc le "
                "garde ne verifie rien de ce cote"
            )
            continue
        comptes.append((libelle, len(fichiers)))

    if len({n for _, n in comptes}) > 1:
        detail = ", ".join(f"{libelle} en rend {n}" for libelle, n in comptes)
        trouves.append(f"les entrees du corpus ne rendent pas le meme nombre de competences : {detail}")

    if trouves:
        return trouves

    connues = commandes(racine)
    if not connues:
        return [f"{COMMANDES} n expose aucune commande : le garde n a rien a quoi comparer"]

    for _, fichiers in entrees(racine):
        for fichier in fichiers:
            texte = fichier.read_text(encoding="utf-8")
            court = fichier.relative_to(racine)
            for renvoi in sorted({m.group(1) for m in RENVOI.finditer(texte)}):
                if renvoi in NON_COMMANDES or renvoi.lstrip("/") in connues:
                    continue
                if declaration_optionnelle(renvoi).search(texte):
                    continue
                trouves.append(
                    f"{court} renvoie vers {renvoi}, qui n existe pas sous {COMMANDES}, et la "
                    f"competence ne declare pas « `{renvoi}` est un flux optionnel » ni le repli "
                    "a suivre"
                )
    return trouves


def auto_test() -> int:
    """Un arbre sain doit etre VERT, et chaque etat casse doit etre ROUGE."""
    script = pathlib.Path(__file__).resolve()

    def renvoi_invente(r: pathlib.Path) -> None:
        f = sorted((r / CORPUS[0][0]).glob(CORPUS[0][1]))[0]
        f.write_text(f.read_text(encoding="utf-8") + "\nPuis lancez `/frobnicate`.\n",
                     encoding="utf-8")

    def commande_supprimee(r: pathlib.Path) -> None:
        (r / COMMANDES / "realiser.md").unlink()

    def repli_retire(r: pathlib.Path) -> None:
        """La declaration qui couvre les six renvois de `openspec-update-change` disparait."""
        for base, motif in CORPUS:
            f = r / base / "openspec-update-change" / "SKILL.md"
            if f.exists():
                texte = f.read_text(encoding="utf-8")
                f.write_text(texte.replace("est un flux optionnel", "est commode"), encoding="utf-8")

    def entree_absente(r: pathlib.Path) -> None:
        shutil.rmtree(r / CORPUS[1][0])

    def arbre_ampute(r: pathlib.Path) -> None:
        shutil.rmtree(sorted((r / CORPUS[1][0]).glob(CORPUS[1][1]))[0].parent)

    cas = [
        ("renvoi invente vers /frobnicate", renvoi_invente),
        ("commande citee puis supprimee", commande_supprimee),
        ("repli retire d une competence", repli_retire),
        ("une entree du corpus absente", entree_absente),
        ("un arbre ampute d une competence", arbre_ampute),
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
        ).returncode

    echecs = []
    with tempfile.TemporaryDirectory() as tmp:
        temoin = code_sur(copie_jetable(tmp))
        print(f"  {'temoin, arbre sain':34s} -> {'vert' if temoin == 0 else f'ROUGE (code {temoin})'}")
        if temoin != 0:
            echecs.append("le temoin rougit, donc les rouges qui suivent ne prouvent rien")

    for nom, casser in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = copie_jetable(tmp)
            casser(copie)
            code = code_sur(copie)
            print(f"  {nom:34s} -> {'rouge' if code == 1 else f'VERT (code {code})'}")
            if code != 1:
                echecs.append(nom)

    if echecs:
        print("\nLe garde ne tient pas : " + ", ".join(echecs), file=sys.stderr)
        return 1
    print(f"\nAuto-test concluant : vert sur l arbre sain, rouge sur les {len(cas)} etats casses.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())

    trouves = ecarts(RACINE)
    if trouves:
        print("Renvoi sans cible ni repli :", file=sys.stderr)
        for e in trouves:
            print(f"  {e}", file=sys.stderr)
        print(
            "\nUn renvoi se corrige en pointant une commande qui existe, ou en declarant le flux "
            "optionnel et le repli dans la meme competence, comme le fait openspec-update-change.",
            file=sys.stderr,
        )
        sys.exit(1)

    total = sum(len(f) for _, f in entrees(RACINE))
    print(f"{total} competence(s) relues : tous les renvois ont une cible ou un repli.")
