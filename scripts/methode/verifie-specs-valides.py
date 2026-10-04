#!/usr/bin/env python3
"""Garde : les specs principales d OpenSpec valident, et le corpus n est pas vide (#4962).

Trois gardes tenaient OpenSpec avant celui-ci, et **tous portaient sur la coherence de l outillage
avec lui-meme** : les invocations citees existent, la version epinglee concorde, l adaptation
francaise n a pas ete regeneree. Aucun n executait `openspec validate`, mesure de l audit #4920.

Consequence : qu une spec principale reste bien formee reposait sur le fait que la competence
d archivage pense a valider avant de deplacer. C etait ecrit dans la competence, et rien ne le
verifiait.

## Le corpus vide, qui est le vrai piege

L outil sort en **0** sur un corpus vide, en ecrivant « No items found to validate. » Mesure le
2026-08-31 en vidant `openspec/specs/` dans un bac :

    $ openspec validate --specs
    - Validating...
    No items found to validate.
    $ echo $?
    0

Un garde qui appellerait l outil nu deviendrait donc **muet en ayant l air sain** le jour ou la
racine des specs bouge, ou ou la derniere capacite est retiree. C est l article A3 et l [ADR 2748],
« un dispositif qui peut ne rien verifier le dit ». Ce garde REFUSE sur un corpus vide, et le dit
autrement qu un echec de validation, parce que les deux se reparent differemment.

## Ce qu il ne fait pas

Il ne valide **que les specs principales**, par `--specs`. Un changement actif est legitimement
incomplet pendant qu on le redige, et le faire rougir transformerait un garde en gene. Les specs
principales, elles, sont fusionnees donc finies : c est le seul corpus dont on peut exiger qu il
valide toujours.

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
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import (
    cas_d_auto_test,
    lit_le_refus,
    message_de_refus,
    prerequis,
    sort_si_contrat_demande,
)

BINAIRE_EPINGLE = pathlib.Path(".github") / "openspec" / "node_modules" / ".bin" / "openspec"

# Ce que l outil ecrit quand il n a rien trouve. Cherche sur les DEUX flux : il l ecrit sur la
# sortie standard, mais un changement de version pourrait le deplacer sur l erreur.
RIEN_A_VALIDER = re.compile(r"No items found to validate", re.IGNORECASE)

# La ligne de total, dont on tire le nombre reellement valide.
TOTAUX = re.compile(r"Totals:\s*(\d+)\s+passed,\s*(\d+)\s+failed")


def racine() -> pathlib.Path:
    rendu = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=False
    )
    return pathlib.Path(rendu.stdout.strip() or ".")


def valide(base: pathlib.Path, lance=subprocess.run) -> tuple[int, str]:
    """Lance l outil epingle sur `base`, et rend (code, sortie fusionnee).

    `lance` est injecte pour qu un cas puisse constater que l outil n est PAS lance quand le
    prerequis manque : sans cette couture, le « quand » du refus ne s eprouve pas, et un refus qui
    arriverait APRES une execution inutile passerait pour correct (ADR 3624).
    """
    epingle = base / BINAIRE_EPINGLE

    # ⟨le prerequis LE PLUS PROFOND, et pas seulement le binaire⟩ Ce garde ne testait que la presence
    # du binaire. Quand il etait la et que son interprete manquait, il le lancait quand meme : le
    # sous-processus echouait sur « env: 'node' », la sortie ne portait aucune ligne « Totals: », et
    # ce garde refusait en accusant la SORTIE DE L OUTIL - une consequence prise pour la cause. Deux
    # sessions ont traine ce refus sur huit corps de demande (#5774).
    manque = prerequis.manque_pour(epingle, "npm ci --prefix .github/openspec")
    if manque:
        return 2, message_de_refus(*manque)

    rendu = lance(
        [str(epingle), "validate", "--specs"],
        capture_output=True,
        text=True,
        cwd=base,
        check=False,
    )
    return rendu.returncode, rendu.stdout + rendu.stderr


def juge(base: pathlib.Path, lance=subprocess.run) -> tuple[int, str]:
    """Rend (code, message). 0 = valide, 1 = ecart, 2 = refus de conclure."""
    code, sortie = valide(base, lance=lance)
    # ⟨on LIT la forme declaree, on ne devine pas un prefixe⟩ `startswith("REFUS")` etait une
    # inference sur le texte, que l ADR 5398 refuse la ou le depot porte un lecteur. `lit_le_refus`
    # exige les DEUX champs, donc il ne confond pas un refus avec une sortie d outil qui commencerait
    # par ce mot.
    if code == 2 and lit_le_refus(sortie) is not None:
        return 2, sortie

    # ⟨les TROIS autres refus portent la forme declaree, et le rouge ne la porte PAS⟩ Ils ecrivaient
    # « REFUS : » a la main, sans champ `POUR REPARER :`, donc `lit_le_refus` rendait `None` et la
    # porte retombait sur son repli a deux lignes. Dette nommee a la cloture de #5762 et traitee la.
    if RIEN_A_VALIDER.search(sortie):
        return 2, message_de_refus(
            "l outil n a trouve AUCUNE spec principale a valider, et il sort en 0 pour le dire."
            " Un corpus vide n est pas un corpus valide",
            "verifiez que « openspec/specs/ » existe et porte au moins une capacite",
        )

    totaux = TOTAUX.search(sortie)
    if totaux is None:
        return 2, (
            message_de_refus(
                "la sortie de l outil ne porte aucune ligne « Totals: N passed, M failed », et ce"
                " garde ne conclut pas sur une sortie qu il n a pas su lire",
                "lisez la sortie de l outil ci-dessous ; si elle est vide, l outil n a pas demarre",
            )
            + "\n"
            + sortie.strip()
        )

    passes, echoues = int(totaux.group(1)), int(totaux.group(2))
    if passes == 0 and echoues == 0:
        return 2, message_de_refus(
            "l outil rend « 0 passed, 0 failed » : rien n a ete valide, ce qui n est pas un succes",
            "verifiez que « openspec/specs/ » porte au moins une capacite principale",
        )
    if echoues or code != 0:
        # ⟨un ROUGE n est pas un refus, et il cessait de le dire⟩ Cette branche a JUGE : elle sort en
        # 1 et porte le defaut. Elle ecrivait pourtant « REFUS : », donc elle se faisait lire comme
        # un empechement par qui lisait la marque - l inverse exact du defaut des trois branches
        # ci-dessus. Le mot et le code disent desormais la meme chose.
        return 1, f"{echoues} spec(s) principale(s) ne valident pas.\n" + sortie.strip()
    return 0, f"Les {passes} spec(s) principale(s) valident."


def auto_test() -> int:
    echecs = 0
    base = racine()

    def joue(libelle: str, attendu: int, prepare) -> None:
        nonlocal echecs
        with tempfile.TemporaryDirectory() as bac:
            r = pathlib.Path(bac) / "arbre"
            shutil.copytree(
                base,
                r,
                symlinks=True,
                ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
            )
            prepare(r)
            obtenu = juge(r)[0]
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    joue("un arbre sain passe", 0, lambda r: None)

    # Le cas qui compte : sans lui, tous les verts de ce garde seraient creux, parce que l outil
    # rend 0 sur un corpus vide.
    def vider(r: pathlib.Path) -> None:
        shutil.rmtree(r / "openspec" / "specs", ignore_errors=True)
        (r / "openspec" / "specs").mkdir(parents=True, exist_ok=True)

    joue("un corpus VIDE fait REFUSER, pas conclure", 2, vider)

    def casser(r: pathlib.Path) -> None:
        spec = next((r / "openspec" / "specs").rglob("spec.md"))
        spec.write_text("# Cassee\n\n## Requirements\n", encoding="utf-8")

    joue("une spec sans Purpose ni exigence fait rougir", 1, casser)

    def desinstaller(r: pathlib.Path) -> None:
        shutil.rmtree(r / ".github" / "openspec" / "node_modules", ignore_errors=True)

    joue("l outil epingle absent fait REFUSER", 2, desinstaller)

    # ⟨ce que le code interne ne dit pas⟩ Les cas ci-dessus lisent `juge(r)[0]`, qui distinguait deja
    # le refus de l ecart. Le defaut de #5774 etait ailleurs, dans le TEXTE du refus et dans le code
    # rendu au shell. Les cas qui suivent lisent les deux.
    # L aide PARTAGEE et non une locale : elle evalue un appelable et nomme ce qui leve, et un cas
    # differe est ce qui separe un harnais qui peut nommer sa panne d un harnais muet (ADR 5570).
    assertion, forme_a_rougi = cas_d_auto_test()
    cas_de_forme = [0]

    def forme(libelle: str, obtenu: object, attendu: object) -> None:
        cas_de_forme[0] += 1
        assertion(libelle, obtenu, attendu)

    def message_quand(prepare) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as bac:
            r = pathlib.Path(bac) / "arbre"
            shutil.copytree(
                base,
                r,
                symlinks=True,
                ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
            )
            prepare(r)
            return juge(r)

    code_sans_outil, dit_sans_outil = message_quand(desinstaller)
    declare = lit_le_refus(dit_sans_outil)
    forme("outil absent : les DEUX champs sont lus par la porte", lambda: declare is not None, True)
    forme("outil absent : le code interne reste le refus", code_sans_outil, 2)
    forme(
        "outil absent : le geste est la commande d installation",
        lambda: declare is not None and declare[1] == "npm ci --prefix .github/openspec",
        True,
    )

    # Le cas que ce lot existe pour couvrir : l outil POSE et son interprete absent. Il se decrit en
    # rendant le shebang irresolvable plutot qu en touchant au PATH du harnais : un PATH modifie
    # eprouverait l environnement du processus courant, et non ce garde.
    def interprete_introuvable(r: pathlib.Path) -> None:
        cible = (r / BINAIRE_EPINGLE).resolve()
        cible.write_text(
            "#!/usr/bin/env interprete-qui-n-existe-pas\n"
            + cible.read_text(encoding="utf-8").split("\n", 1)[1],
            encoding="utf-8",
        )

    code_sans_interprete, dit_sans_interprete = message_quand(interprete_introuvable)
    declare_i = lit_le_refus(dit_sans_interprete)
    forme("interprete absent : le code interne est le refus", code_sans_interprete, 2)
    forme("interprete absent : les deux champs sont lus", lambda: declare_i is not None, True)
    forme(
        "interprete absent : la cause NOMME l interprete du shebang",
        lambda: declare_i is not None and "interprete-qui-n-existe-pas" in declare_i[0],
        True,
    )
    forme(
        "interprete absent : la cause ne parle PAS de la sortie de l outil",
        lambda: declare_i is not None and "Totals" in declare_i[0],
        False,
    )
    forme(
        "interprete absent : aucun gestionnaire de version n est nomme",
        lambda: any(g in dit_sans_interprete for g in ("nvm", "asdf", "volta", "fnm")),
        False,
    )

    # ⟨le code rendu au SHELL, et pas seulement celui de `juge`⟩ Les cas ci-dessus lisent le code
    # interne, que ce garde distinguait deja. Le defaut etait a la sortie, qui l ecrasait en 1 : sans
    # ce cas, remettre l ecrasement ne ferait rougir personne. Mesure du 2026-10-03.
    def code_au_shell(prepare) -> int:
        with tempfile.TemporaryDirectory() as bac:
            r = pathlib.Path(bac) / "arbre"
            shutil.copytree(
                base,
                r,
                symlinks=True,
                ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
            )
            prepare(r)
            return subprocess.run(
                [sys.executable, str(r / "scripts" / "methode" / pathlib.Path(__file__).name)],
                capture_output=True,
                check=False,
                cwd=r,
            ).returncode

    forme("outil absent : le SHELL recoit 2, pas 1", lambda: code_au_shell(desinstaller), 2)
    forme("arbre sain : le SHELL recoit 0", lambda: code_au_shell(lambda r: None), 0)

    # ⟨LE « QUAND » DU REFUS, dette nommee a la cloture de #5762⟩ Les cas ci-dessus eprouvent ce que
    # le refus DIT. Aucun n eprouvait qu il arrive AVANT d avoir lance l outil, et un refus juste
    # rendu apres une execution inutile aurait passe tous les cas. Une session pair a paye cette
    # forme exacte le meme jour sur son propre instrument : le verdict restait juste, et c etait le
    # gaspillage qui n etait pas couvert.
    lances: list = []

    class RenduFeint:
        returncode = 0
        stdout = "Totals: 1 passed, 0 failed\n"
        stderr = ""

    def espion_de_lancement(commande, **kwargs):
        lances.append(commande)
        return RenduFeint()

    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac) / "arbre"
        shutil.copytree(
            base,
            r,
            symlinks=True,
            ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
        )
        desinstaller(r)
        forme(
            "outil absent : l outil n est PAS lance, le refus arrive AVANT",
            lambda: (valide(r, lance=espion_de_lancement)[0], len(lances)),
            (2, 0),
        )

    # Et le sens inverse, sans quoi le cas precedent passerait sur un garde qui ne lance JAMAIS rien.
    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac) / "arbre"
        shutil.copytree(
            base,
            r,
            symlinks=True,
            ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
        )
        lances.clear()
        forme(
            "prerequis tenu : l outil EST lance, donc le cas precedent discrimine",
            lambda: (valide(r, lance=espion_de_lancement)[0], len(lances)),
            (0, 1),
        )

    # ⟨CE QUI DISCRIMINE le lecteur declare d un prefixe de chaine⟩ Un outil dont la sortie COMMENCE
    # par ce mot n est pas un refus de ce garde. Avec `startswith("REFUS")`, elle etait prise pour
    # tel et remontee telle quelle ; `lit_le_refus` exige les deux champs, donc elle retombe sur la
    # branche « sortie illisible », qui rend un refus DECLARE. Sans ce cas, remettre le prefixe ne
    # ferait rougir personne.
    def outil_qui_dit_refus(commande, **kwargs):
        if commande[1:2] == ["validate"]:
            return type(
                "R", (), {"returncode": 2, "stdout": "REFUS : je suis l outil\n", "stderr": ""}
            )()
        return RenduFeint()

    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac) / "arbre"
        shutil.copytree(
            base,
            r,
            symlinks=True,
            ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
        )
        forme(
            "une sortie d OUTIL qui commence par « REFUS » n est pas prise pour le notre",
            lambda: lit_le_refus(juge(r, lance=outil_qui_dit_refus)[1]) is not None,
            True,
        )
        forme(
            "et elle est bien passee par la branche « sortie illisible »",
            lambda: "n a pas su lire" in juge(r, lance=outil_qui_dit_refus)[1],
            True,
        )

    # ⟨UN ROUGE NE PORTE PAS la marque d un refus⟩ Cette branche a juge et sort en 1. Elle ecrivait
    # « REFUS : », donc la porte la lisait comme un empechement - l inverse du defaut des trois
    # autres branches. Sans ce cas, remettre le mot ne ferait rougir personne.
    def outil_qui_echoue(commande, **kwargs):
        if commande[1:2] == ["validate"]:
            return type(
                "R", (), {"returncode": 1, "stdout": "Totals: 2 passed, 1 failed\n", "stderr": ""}
            )()
        return RenduFeint()

    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac) / "arbre"
        shutil.copytree(
            base,
            r,
            symlinks=True,
            ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
        )
        forme(
            "un ecart REEL sort en 1 et ne porte AUCUNE marque de refus",
            lambda: (
                juge(r, lance=outil_qui_echoue)[0],
                lit_le_refus(juge(r, lance=outil_qui_echoue)[1]) is None,
                "REFUS" in juge(r, lance=outil_qui_echoue)[1],
            ),
            (1, True, False),
        )

    # ⟨LES DEUX AUTRES REFUS, que la matrice a trouves SANS temoin⟩ Deux de mes trois refus etaient
    # eprouves, pas le troisieme : retirer son champ de geste ne faisait rougir personne. Trouve par
    # mutation, et c est le genre de trou qu une relecture ne voit pas - les trois se ressemblent.
    def outil_qui_rend(stdout: str):
        def faux(commande, **kwargs):
            if commande[1:2] == ["validate"]:
                return type("R", (), {"returncode": 0, "stdout": stdout, "stderr": ""})()
            return RenduFeint()

        return faux

    with tempfile.TemporaryDirectory() as bac:
        r = pathlib.Path(bac) / "arbre"
        shutil.copytree(
            base,
            r,
            symlinks=True,
            ignore=shutil.ignore_patterns(".git", "target", "graphify-out"),
        )
        for libelle, sortie_feinte in (
            ("corpus vide", "No items found to validate\n"),
            ("zero valide sur zero", "Totals: 0 passed, 0 failed\n"),
        ):
            lanceur = outil_qui_rend(sortie_feinte)
            forme(
                f"refus « {libelle} » : les DEUX champs sont declares",
                lambda ln=lanceur: (
                    juge(r, lance=ln)[0],
                    lit_le_refus(juge(r, lance=ln)[1]) is not None,
                ),
                (2, True),
            )
            forme(
                f"refus « {libelle} » : son geste n est pas vide",
                lambda ln=lanceur: bool(
                    (lit_le_refus(juge(r, lance=ln)[1]) or ("", ""))[1].strip()
                ),
                True,
            )

    # Le gage du module partage, joue ici ET dans l autre garde du couple (ADR 5483).
    for libelle, tenu in prerequis.verifie_grammaire():
        forme(f"prerequis : {libelle}", tenu, True)

    if forme_a_rougi():
        echecs = 1

    print()
    print(
        f"Auto-test concluant : le garde voit un corpus vide et une spec cassee, et"
        f" {cas_de_forme[0]} cas de forme rendent leur verdict."
        if not echecs
        else "Auto-test EN ÉCHEC."
    )
    return echecs


CONTRAT = {
    "geste": "specification principale d OpenSpec qui ne valide pas",
    "population": "les specs de .github/openspec, par la ligne de commande epinglee",
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/verifie-specs-valides.py --auto-test",
    "decision": "hygiene, sans decision",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(auto_test())
    code, message = juge(racine())
    # ⟨le code de juge, tel quel⟩ Il vaut deja 0, 1 ou 2, et sa docstring le dit depuis toujours :
    # « 0 = valide, 1 = ecart, 2 = refus de conclure ». Cette ligne l ECRASAIT en 1, donc un refus
    # sortait comme un verdict rouge et le lecteur cherchait dans son diff. Le garde distinguait, et
    # il jetait sa distinction sur le pas de la porte (#5774).
    print(message, file=sys.stderr if code else sys.stdout)
    sys.exit(code)
