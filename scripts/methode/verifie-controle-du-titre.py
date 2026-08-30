#!/usr/bin/env python3
"""Garde du controle local du titre, avant que la pull request ne soit ouverte.

Le depot pratique DEUX conventions de titre a un caractere d ecart. Mesure du 2026-08-26, sur les
100 derniers titres de chaque famille : 62 titres d issue portent l espace avant les deux-points et
15 ne le portent pas ; les 100 dernieres pull requests fusionnees sont a 0 contre 100. La seconde
colonne est propre parce que `titre-pr.yml` refuse, pas parce que la main ecrit juste.

**Ou nait le defaut, mesure et non suppose.** Les quatre pull requests rouges du 2026-08-26,
#4570, #4588, #4589 et #4591, ont toutes ete ouvertes depuis une branche dont les sujets de commit
etaient CONFORMES. Le titre n a donc pas ete rempli par `gh pr create --fill` : il a ete retape.
Trois des quatre sont le sujet du commit reecrit avec ses accents, le quatrieme est le titre de
l issue #4574 recopie tel quel, espace compris. Sur 297 sujets de commit de branche hors `main`,
286 sont conformes et aucun n est hors Conventional Commits. Le defaut n entre pas au commit, il
entre a la frappe du titre, quand la main ecrit du francais correct et lui applique la typographie
francaise que la syntaxe interdit. Ce qui decide de la frappe est le nombre de commits :
`gh pr create --fill` reprend le sujet quand la branche n en porte qu un, et titre la pull request
avec le NOM DE BRANCHE au-dela, forme que `titre-pr.yml` refuse. Mesure du 2026-08-26 sur les 30
dernieres pull requests fusionnees : 20 tenaient en un commit, 10 en plusieurs, et les quatre rouges
etaient toutes dans la seconde famille.

Le remede ne cree aucune regle : il lance le garde qui existe deja, `verifie-titre-pr.sh`, au
moment ou le titre s ecrit. Ce garde-ci tient ce remede en place, et il ne se contente pas de
compter une citation : il RELANCE le script cite sur deux titres connus, et refuse si celui-ci a
cesse de refuser l espace. Une methode qui nomme une commande devenue permissive vaut moins que
rien, car elle rend le vert rassurant.

**Trois entrees de corpus, et un refus PAR ENTREE** (article A3, ADR 2748). Une entree dont le
fichier a disparu ne se saute pas : le garde refuse au lieu de conclure sur ce qui reste, sans quoi
il resterait vert en ayant verifie deux documents sur trois.

**Sur l ADR 3645, qui veut qu un detecteur textuel s exclue de son corpus.** La question se pose
puisque ce fichier NOMME le script qu il cherche. Elle se resout sans exemption : le corpus n est
pas un balayage de l arbre mais trois chemins fixes, dont aucun n atteint `scripts/methode/`.

    --verifie   : ne rien ecrire, sortir 1 sur un ecart (garde de CI). C est aussi le defaut.
    --auto-test : eprouver le garde sur une copie jetable, et sortir 1 s il reste vert la ou il
                  devrait rougir, ou s il rougit sur un arbre sain.
"""

import pathlib
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]

# Le script que la methode doit nommer. Il est lance par `bash` et non execute directement : les
# workflows lui posent le bit d execution avant chaque appel, et un checkout qui ne le porte pas
# rendrait ce garde rouge pour une raison qui n est pas la sienne.
GARDE = pathlib.Path(".github") / "scripts" / "verifie-titre-pr.sh"

# Les trois documents qui decrivent le moment ou le titre s ecrit. `.agents/skills` est le fonds,
# `.claude/skills` sa copie tenue par `synchronise-adaptateurs.py` : les deux doivent nommer la
# commande, la copie etant ce que Claude Code lit.
#
# La competence a change : le titre s ecrit en OUVRANT la pull request, et `clore-une-issue` le
# portait par accident d histoire. Elle vient desormais APRES la fusion, ou il est trop tard pour
# eprouver un titre. Le corpus suit le moment, pas le nom.
CORPUS = (
    pathlib.Path("CONTRIBUTING.md"),
    pathlib.Path(".agents") / "skills" / "ouvrir-une-pr" / "SKILL.md",
    pathlib.Path(".claude") / "skills" / "ouvrir-une-pr" / "SKILL.md",
)

# Les deux titres temoins. Le second compte autant que le premier : un script qui refuserait tout
# refuserait aussi l espace, et son rouge ne dirait rien.
REFUSE = "feat(garde) : un espace avant les deux-points"
ACCEPTE = "feat(garde): un espace avant les deux-points"

RAPPEL = (
    "La methode doit nommer la commande, pas seulement la regle. `CONTRIBUTING.md` porte deja la "
    "regle dans un bloc [!IMPORTANT], et quatre pull requests ont rougi le meme jour : ecrire la "
    "regle une fois de plus ne change rien, lancer le garde avant d ouvrir la pull request si."
)


def verdicts(racine: pathlib.Path) -> tuple[int, int]:
    """Les codes rendus par le script cite sur les deux titres temoins."""
    def code(titre: str) -> int:
        return subprocess.run(
            ["bash", str(racine / GARDE), titre], capture_output=True
        ).returncode

    return code(REFUSE), code(ACCEPTE)


def ecarts(racine: pathlib.Path) -> list[str]:
    """Tout ce qui rompt le controle local. Liste vide = le garde est au vert."""
    trouves = []

    for chemin in CORPUS:
        fichier = racine / chemin
        if not fichier.is_file():
            trouves.append(f"{chemin} est absent : cette entree du corpus ne verifie rien")
            continue
        if GARDE.name not in fichier.read_text(encoding="utf-8"):
            trouves.append(f"{chemin} ne nomme plus « {GARDE.name} »")

    if not (racine / GARDE).is_file():
        trouves.append(f"{GARDE} est absent : la methode nomme une commande qui n existe pas")
        return trouves

    refuse, accepte = verdicts(racine)
    if refuse == 0:
        trouves.append(
            f"{GARDE} ACCEPTE « {REFUSE} » : la commande que la methode fait lancer a cesse de "
            "refuser l espace avant les deux-points"
        )
    if accepte != 0:
        trouves.append(
            f"{GARDE} refuse « {ACCEPTE} » (code {accepte}) : il refuse un titre conforme, donc "
            "son rouge sur un titre fautif ne prouve rien"
        )

    return trouves


def auto_test() -> int:
    """Un arbre sain doit etre VERT, et chaque etat casse doit etre ROUGE."""
    script = pathlib.Path(__file__).resolve()

    def oublie(chemin: pathlib.Path):
        def casser(r: pathlib.Path) -> None:
            fichier = r / chemin
            fichier.write_text(
                fichier.read_text(encoding="utf-8").replace(GARDE.name, "un-autre-script.sh"),
                encoding="utf-8",
            )

        return casser

    def supprime_un_document(r: pathlib.Path) -> None:
        (r / CORPUS[1]).unlink()

    def supprime_le_garde(r: pathlib.Path) -> None:
        (r / GARDE).unlink()

    def desserre_le_garde(r: pathlib.Path) -> None:
        """Ce que ferait un garde affaibli : tout passe, y compris l espace."""
        (r / GARDE).write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")

    def durcit_le_garde(r: pathlib.Path) -> None:
        """Le controle negatif : un garde qui refuse tout ne prouve rien."""
        (r / GARDE).write_text("#!/usr/bin/env bash\nexit 1\n", encoding="utf-8")

    cas = [
        ("CONTRIBUTING ne le nomme plus", oublie(CORPUS[0])),
        ("le fonds ne le nomme plus", oublie(CORPUS[1])),
        ("la copie ne le nomme plus", oublie(CORPUS[2])),
        ("un document du corpus absent", supprime_un_document),
        ("le script cite absent", supprime_le_garde),
        ("le script cite desserre", desserre_le_garde),
        ("le script cite refuse tout", durcit_le_garde),
    ]

    def copie_jetable(tmp: str) -> pathlib.Path:
        copie = pathlib.Path(tmp) / "depot"
        for dossier in (".agents", ".claude", ".github", "scripts"):
            source = RACINE / dossier
            if source.exists():
                shutil.copytree(source, copie / dossier, symlinks=True)
        shutil.copy2(RACINE / CORPUS[0], copie / CORPUS[0])
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
        print(f"  {'temoin, arbre sain':32s} -> {etat}")
        if temoin != 0:
            echecs.append("le temoin rougit, donc les rouges qui suivent ne prouvent rien")

    for nom, casser in cas:
        with tempfile.TemporaryDirectory() as tmp:
            copie = copie_jetable(tmp)
            casser(copie)
            code = code_sur(copie)
            etat = "rouge" if code == 1 else f"VERT (code {code})"
            print(f"  {nom:32s} -> {etat}")
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
        print("Le controle local du titre ne tient plus :", file=sys.stderr)
        for e in trouves:
            print(f"  {e}", file=sys.stderr)
        print("\n" + RAPPEL, file=sys.stderr)
        sys.exit(1)

    print(f"{len(CORPUS)} document(s) nomment « {GARDE.name} », qui refuse encore l espace.")
