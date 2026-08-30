#!/usr/bin/env python3
"""Garde d egalite entre la ligne de commande OpenSpec epinglee et ce que les competences declarent.

Les douze fichiers d OpenSpec presents ici portent `generatedBy: "<version>"` dans leur en-tete.
Ils DECRIVENT le contrat de la ligne de commande de cette version : ses sous-commandes, les champs
de son JSON, les etats qu elle rend. Une version installee qui ne serait pas celle-la ferait decrire
un contrat perime par des fichiers qui se lisent comme vrais, et le defaut ne se verrait qu au
moment ou un agent suit une instruction qui n a plus de sens.

**Pourquoi lire le lockfile plutot que lancer `openspec --version`.** C est la version DU DEPOT qui
fait foi, pas celle du poste. Un poste peut porter une installation globale divergente, et c est
precisement le cas qui a ouvert le chantier #4511 : l outil ne vivait qu a un endroit, hors du
depot. Lire le lockfile rend en outre le garde utilisable en integration continue sans y installer
Node ni l outil.

**Deux egalites, pas une.** La version resolue dans le lockfile doit valoir le `generatedBy` des
competences, ET le manifeste doit epingler cette version EXACTEMENT. Sans la seconde, un intervalle
(`^1.10.0`) laisserait le prochain `npm install` deplacer le lockfile sans qu aucun diff de
manifeste ne le montre, et le garde ne rougirait qu apres coup.

**Ce qu il ne peut pas lire, il le refuse.** Lockfile absent, illisible, ou paquet introuvable :
code 1 avec la cause. Un garde qui passe au vert quand sa source manque annonce une egalite qu il n
a pas verifiee (article A3).

**Sur l ADR 3645, qui veut qu un detecteur textuel s exclue de son corpus.** La question se pose
puisque ce fichier NOMME `generatedBy` et porte un numero de version dans sa prose. Elle se resout
sans exemption : le corpus n est pas un balayage de l arbre mais deux motifs fixes,
`.agents/skills/openspec-*/SKILL.md` et `.claude/skills/openspec-*/SKILL.md`, qui ne peuvent pas
atteindre `scripts/methode/`. Aucune exemption a declarer, donc, et la raison est ecrite ici pour
que la question ne se repose pas.

    --verifie   : ne rien ecrire, sortir 1 sur un ecart (garde de CI). C est aussi le defaut.
    --auto-test : eprouver le garde sur une copie jetable, et sortir 1 s il reste vert la ou il
                  devrait rougir, ou s il rougit sur un arbre sain.
"""

import json
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
PAQUET = "@fission-ai/openspec"
DOSSIER_OUTIL = pathlib.Path(".github") / "openspec"

# Les deux arbres de competences. Les six commandes de `.claude/commands/opsx/` ont disparu avec
# #4516, remplacees par six relais d une ligne qui deleguent a ces memes competences. Un relais ne
# decrit aucun contrat de ligne de commande, donc il n a pas de `generatedBy` a tenir : ce corpus
# reste celui des competences, et le lot qui devait l elargir a ete annule (ADR 4516).
MOTIFS = (
    pathlib.Path(".agents") / "skills",
    pathlib.Path(".claude") / "skills",
)

ENTETE_VERSION = re.compile(r"^\s*generatedBy:\s*[\"']?([0-9][0-9A-Za-z.\-+]*)[\"']?\s*$", re.M)


def version_resolue(racine: pathlib.Path) -> tuple[str | None, str | None]:
    """La version que `npm ci` installera, lue dans le lockfile. Rend (version, cause d echec)."""
    lock = racine / DOSSIER_OUTIL / "package-lock.json"
    if not lock.exists():
        return None, f"{lock.relative_to(racine)} est absent"
    try:
        arbre = json.loads(lock.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as panne:
        return None, f"{lock.relative_to(racine)} est illisible : {panne}"
    entree = arbre.get("packages", {}).get(f"node_modules/{PAQUET}")
    if not entree or "version" not in entree:
        return None, f"{lock.relative_to(racine)} ne resout pas {PAQUET}"
    return entree["version"], None


def version_epinglee(racine: pathlib.Path) -> tuple[str | None, str | None]:
    """Ce que le manifeste demande. Un intervalle est un echec, pas une valeur."""
    manifeste = racine / DOSSIER_OUTIL / "package.json"
    if not manifeste.exists():
        return None, f"{manifeste.relative_to(racine)} est absent"
    try:
        d = json.loads(manifeste.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as panne:
        return None, f"{manifeste.relative_to(racine)} est illisible : {panne}"
    demande = d.get("devDependencies", {}).get(PAQUET) or d.get("dependencies", {}).get(PAQUET)
    if demande is None:
        return None, f"{manifeste.relative_to(racine)} ne declare pas {PAQUET}"
    if not re.fullmatch(r"[0-9][0-9A-Za-z.\-+]*", demande):
        return None, (
            f"{manifeste.relative_to(racine)} epingle {PAQUET} par un intervalle, « {demande} » : "
            "le prochain « npm install » deplacerait le lockfile sans qu aucun diff du manifeste "
            "ne le montre"
        )
    return demande, None


def entrees(racine: pathlib.Path) -> list[tuple[str, list[pathlib.Path]]]:
    """Pour chaque entree du corpus, son libelle et les SKILL.md qu elle rend.

    Une entree absente rend une liste VIDE plutot que d etre sautee : c est ce qui permet a
    `corpus_incomplet` de refuser PAR ENTREE au lieu de refuser sur le total. Un corpus dont un
    chemin a disparu rend encore des fichiers, et un refus sur le total resterait vert en n ayant
    lu qu une partie de ce que le garde annonce. Article A3, ADR 2748 (#4566).
    """
    rendu = []
    for motif in MOTIFS:
        base = racine / motif
        rendu.append((str(motif), sorted(base.glob("openspec-*/SKILL.md")) if base.is_dir() else []))
    return rendu


def competences(racine: pathlib.Path) -> list[pathlib.Path]:
    """Les SKILL.md des competences OpenSpec, dans les deux arbres, tries."""
    return [f for _, trouves in entrees(racine) for f in trouves]


def corpus_incomplet(racine: pathlib.Path) -> list[str]:
    """Les refus qui portent sur le CORPUS lui-meme, avant tout verdict sur son contenu."""
    manques = []
    comptes = []
    for libelle, trouves in entrees(racine):
        if not trouves:
            manques.append(
                f"{libelle} ne rend aucune competence openspec-* : cette entree du corpus est "
                "vide, donc le garde ne verifie rien de ce cote"
            )
        else:
            comptes.append((libelle, len(trouves)))
    if len({n for _, n in comptes}) > 1:
        detail = ", ".join(f"{libelle} en rend {n}" for libelle, n in comptes)
        manques.append(
            f"les entrees du corpus ne rendent pas le meme nombre de competences : {detail}. "
            "La copie a derive du fonds, ou l une des deux a ete reecrite"
        )
    return manques


def ecarts(racine: pathlib.Path) -> list[str]:
    """Tout ce qui rompt les deux egalites. Liste vide = le garde est au vert."""
    trouves = []

    resolue, panne = version_resolue(racine)
    if panne:
        return [panne]
    demandee, panne = version_epinglee(racine)
    if panne:
        return [panne]

    if demandee != resolue:
        trouves.append(
            f"le manifeste epingle {demandee}, le lockfile resout {resolue}"
        )

    incomplet = corpus_incomplet(racine)
    if incomplet:
        return trouves + incomplet

    fichiers = competences(racine)

    for fichier in fichiers:
        trouve = ENTETE_VERSION.search(fichier.read_text(encoding="utf-8"))
        court = fichier.relative_to(racine)
        if not trouve:
            trouves.append(f"{court} ne declare aucun generatedBy")
        elif trouve.group(1) != resolue:
            trouves.append(
                f"{court} declare generatedBy {trouve.group(1)}, le lockfile resout {resolue}"
            )
    return trouves


def auto_test() -> int:
    """Un arbre sain doit etre VERT, et chaque etat casse doit etre ROUGE.

    Le vert du temoin compte autant que les rouges : un garde qui rougit sur tout rougirait aussi
    sur les quatre etats casses, et ses quatre rouges ne diraient rien.
    """
    script = pathlib.Path(__file__).resolve()

    def casse_lockfile(r: pathlib.Path) -> None:
        lock = r / DOSSIER_OUTIL / "package-lock.json"
        arbre = json.loads(lock.read_text(encoding="utf-8"))
        arbre["packages"][f"node_modules/{PAQUET}"]["version"] = "9.9.9"
        lock.write_text(json.dumps(arbre), encoding="utf-8")

    def casse_competence(r: pathlib.Path) -> None:
        fichier = competences(r)[0]
        texte = fichier.read_text(encoding="utf-8")
        fichier.write_text(ENTETE_VERSION.sub('  generatedBy: "0.0.1"', texte, count=1),
                           encoding="utf-8")

    def relache_epinglage(r: pathlib.Path) -> None:
        manifeste = r / DOSSIER_OUTIL / "package.json"
        d = json.loads(manifeste.read_text(encoding="utf-8"))
        d["devDependencies"][PAQUET] = "^" + d["devDependencies"][PAQUET]
        manifeste.write_text(json.dumps(d, indent=2), encoding="utf-8")

    def retire_lockfile(r: pathlib.Path) -> None:
        (r / DOSSIER_OUTIL / "package-lock.json").unlink()

    def entree_absente(r: pathlib.Path) -> None:
        """UNE entree du corpus disparait. L autre rend encore des fichiers."""
        shutil.rmtree(r / MOTIFS[1])

    def arbre_ampute(r: pathlib.Path) -> None:
        """Un arbre perd UNE competence : les entrees cessent de rendre le meme compte."""
        shutil.rmtree(sorted((r / MOTIFS[1]).glob("openspec-*/SKILL.md"))[0].parent)

    cas = [
        ("version du lockfile deplacee", casse_lockfile),
        ("generatedBy d une competence", casse_competence),
        ("epinglage relache en intervalle", relache_epinglage),
        ("lockfile absent", retire_lockfile),
        ("une entree du corpus absente", entree_absente),
        ("un arbre ampute d une competence", arbre_ampute),
    ]

    def copie_jetable(tmp: str) -> pathlib.Path:
        copie = pathlib.Path(tmp) / "depot"
        for d in (".agents", ".claude", "scripts", ".github/openspec"):
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


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())

    trouves = ecarts(RACINE)
    if trouves:
        print("Version d OpenSpec desynchronisee :", file=sys.stderr)
        for e in trouves:
            print(f"  {e}", file=sys.stderr)
        print(
            "\nLe lockfile fait foi. Pour monter de version, il ne suffit pas de le deplacer : les "
            "douze fichiers d OpenSpec decrivent le contrat de la version qu ils declarent, et se "
            "regenerent ou se relisent a la main.",
            file=sys.stderr,
        )
        sys.exit(1)

    resolue, _ = version_resolue(RACINE)
    print(f"{PAQUET} {resolue}, {len(competences(RACINE))} competence(s) alignee(s).")
