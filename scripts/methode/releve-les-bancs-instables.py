#!/usr/bin/env python3
"""Combien de fois chaque banc a-t-il rougi, sur combien de tirages.

Six bancs du depot rougissent par intermittence (#4804), et aucun n'avait de taux : chaque issue
consignait une ou deux observations en disant elle-meme que ce n'etait pas une frequence.

Rejouer la suite N fois ne convient pas : elle prend 16 minutes, donc trente tirages font huit
heures. Et rejouer une classe seule ne reproduit rien, le rouge de #4694 n'apparaissant QUE dans la
suite complete, ou le Stage de TestFX est partage entre les classes d'un meme fork.

Les runs passes SONT les tirages. Ce releve les lit.

Usage : releve-les-bancs-instables.py [--jours N] [--classe] [--auto-test]
"""

from __future__ import annotations

import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

# Surefire nomme un test echoue sous deux formes, et il faut les deux : la premiere seule rate les
# erreurs, la seconde seule rate les echecs d'assertion quand la classe entiere tombe.
#
#   [ERROR]   AppTest.le_stage_partage_reste_ajustable:145 [une scene...]
#   [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest.ouvrir_tout(FxRobot) -- ... <<< ERROR!
#
# Le paquet est facultatif et se jette : deux journaux du meme test ne s'agregeraient pas si l'un le
# portait et l'autre non.
CLASSE = r"(?:[a-z][A-Za-z0-9_]*\.)*([A-Z][A-Za-z0-9_]*Test)"
RESUME = re.compile(rf"\[ERROR\]\s+{CLASSE}\.([a-z][A-Za-z0-9_]*):\d+")
DETAIL = re.compile(rf"\[ERROR\]\s+{CLASSE}\.([a-z][A-Za-z0-9_]*)\([^)]*\)\s+--\s+Time elapsed")


def testsEchouesOrdonnes(journal: str) -> list[str]:
    """Les `Classe.methode` en echec, **dans l'ordre du journal**, sans doublon.

    L'ordre est l'information : une cascade emporte une classe entiere quelques secondes apres le
    test qui l'a declenchee, et un ensemble la perd par construction. C'est ce qui faisait figurer
    107 des 141 classes JavaFX au releve, victimes comprises.
    """
    vus, ordre = set(), []
    for ligne in journal.splitlines():
        for motif in (RESUME, DETAIL):
            for classe, methode in motif.findall(ligne):
                nom = f"{classe}.{methode}"
                if nom not in vus:
                    vus.add(nom)
                    ordre.append(nom)
    return ordre


def tete(ordonnes: list[str]) -> str | None:
    """Le premier test tombe : le suspect. Les forks etant entrelaces, ce n'est pas une certitude."""
    return ordonnes[0] if ordonnes else None


def suite(ordonnes: list[str]) -> list[str]:
    """Ce que la tete a emporte, ou ce qui est tombe pour son compte dans un autre fork."""
    return ordonnes[1:]


def comptesParRang(parTentative: list[list[str]]) -> tuple[dict[str, int], dict[str, int]]:
    """Combien de fois chaque test est EN TETE, et combien de fois DANS LA SUITE.

    Un test peut etre les deux : la separation observe un tirage, elle ne classe pas un test une
    fois pour toutes.
    """
    tetes: dict[str, int] = {}
    suites: dict[str, int] = {}
    for ordonnes in parTentative:
        premier = tete(ordonnes)
        if premier:
            tetes[premier] = tetes.get(premier, 0) + 1
        for autre in suite(ordonnes):
            suites[autre] = suites.get(autre, 0) + 1
    return tetes, suites


def testsEchoues(journal: str) -> set[str]:
    """Les `Classe.methode` que ce journal declare en echec."""
    vus = set()
    for motif in (RESUME, DETAIL):
        for classe, methode in motif.findall(journal):
            vus.add(f"{classe}.{methode}")
    return vus


def rapport(parRun: dict[str, set[str]], tirages: int) -> list[tuple[str, int, int]]:
    """`(test, occurrences, tirages)`, du plus frequent au moins frequent."""
    comptes: dict[str, int] = {}
    for echoues in parRun.values():
        for test in echoues:
            comptes[test] = comptes.get(test, 0) + 1
    return [(test, n, tirages) for test, n in sorted(comptes.items(), key=lambda c: (-c[1], c[0]))]


# **Un verdict courant ment.** `gh run list` rend l'etat COURANT d'un run, et une relance ECRASE
# l'echec precedent : sur 21 jours, aucun commit n'apparaissait vu vert ET rouge, alors que 52 runs
# avaient ete relances. La signature d'une instabilite est donc invisible dans cette source.
#
# Les TENTATIVES la disent. Un run dont `run_attempt` vaut 2 et qui finit vert a echoue puis reussi
# sur le meme commit : c'est exactement ce que les six issues du chantier #4804 decrivent.
#
# Le groupe de concurrence tue par ailleurs les runs quand une poussee arrive. Un run annule n'est pas
# un tirage, et le compter au denominateur ferait baisser tous les taux sans qu'aucun chiffre ne
# paraisse faux.
CONCLUS = ("success", "failure")
DEPOT = "echonuit/vigiechiro-pr-companion"
FLUX = 286171791  # « Java CI with Maven »


def _forge(*args: str) -> str:
    """Sortie de `gh`, ou une chaine vide s'il n'est pas installe."""
    if shutil.which("gh") is None:
        return ""
    fait = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
    return fait.stdout


def _borne(jours: int) -> str:
    return subprocess.run(
        ["date", "-u", "-d", f"-{jours} days", "+%Y-%m-%dT%H:%M:%SZ"],
        capture_output=True,
        text=True,
        check=False,
    ).stdout.strip()


def relances(jours: int) -> tuple[list[dict], int]:
    """Les runs RELANCES de la fenetre, et le nombre de tirages (conclus, annules exclus).

    `gh run list` ne suffit pas : il plafonne, et surtout il ne porte pas `run_attempt`.
    """
    brut = _forge(
        "api",
        "--paginate",
        f"repos/{DEPOT}/actions/workflows/{FLUX}/runs?per_page=100",
        "-q",
        ".workflow_runs[] | [.id, .run_attempt, .conclusion, .created_at, .head_sha] | @tsv",
    )
    borne = _borne(jours)
    dans = []
    for ligne in brut.splitlines():
        champs = ligne.split("\t")
        if len(champs) < 5 or champs[3] < borne or champs[2] not in CONCLUS:
            continue
        dans.append(
            {
                "id": int(champs[0]),
                "tentatives": int(champs[1]),
                "verdict": champs[2],
                "sha": champs[4],
            }
        )
    return [r for r in dans if r["tentatives"] > 1], len(dans)


def journalDeTentative(idRun: int, tentative: int, atelier: str = "build") -> str:
    """Le journal d'UNE tentative, decompresse. `--log-failed` ne rend que la DERNIERE."""
    if shutil.which("gh") is None or shutil.which("unzip") is None:
        return ""
    with tempfile.TemporaryDirectory() as dossier:
        zipDeRun = pathlib.Path(dossier) / "l.zip"
        fait = subprocess.run(
            ["gh", "api", f"repos/{DEPOT}/actions/runs/{idRun}/attempts/{tentative}/logs"],
            capture_output=True,
            check=False,
        )
        if not fait.stdout:
            return ""
        zipDeRun.write_bytes(fait.stdout)
        subprocess.run(
            ["unzip", "-qq", "-o", str(zipDeRun), "-d", dossier], capture_output=True, check=False
        )
        morceaux = []
        for fichier in pathlib.Path(dossier).glob("*.txt"):
            morceaux.append(fichier.read_text(encoding="utf-8", errors="replace"))
        return "\n".join(morceaux)


# ---- Classer une tentative rouge (#4187) ----
#
# Le releve savait dire « echouee pour autre chose » ; il ne disait pas QUOI. Or sur 57 tentatives
# rouges de 21 jours, ce seau valait 20, et il portait QUATRE causes dont une seule appelle un rejeu.
# Classer, c'est ce qui remplace le rejeu a l'aveugle par une conduite.

UN_BANC = "un ou deux bancs qui vacillent"
EFFONDREMENT = "effondrement massif de la JVM"
NATIF = "couche graphique native absente"
APPROVISIONNEMENT = "artefact ou action indisponible"
ANNULATION = "annule parce qu'une autre etape avait deja rouge"
INCONNU = "aucune cause reconnue dans le journal"

# Au-dela, ce n'est plus un banc qui tombe : c'est la JVM qui emporte tout ce qui restait a jouer.
# Le plus gros rouge NORMAL du depot en 21 jours en a fait tomber 2 ; l'effondrement, plus de 1 300.
SEUIL_EFFONDREMENT = 50

_NATIF = re.compile(r"OSPango|UnsatisfiedLinkError|no javafx_font", re.I)
_APPRO = re.compile(
    r"could not be resolved|Could not transfer artifact|Non-resolvable"
    r"|could not be found at the URI|Failed to download archive",
    re.I,
)
_ANNULE = "The operation was canceled"

# La FIN du journal, pas le journal : « REFUSE » et les exceptions attendues y trainent partout. Un
# premier dessin lisait le journal entier et rangeait 20 tentatives sur 20 sous « garde de methode »,
# parce qu'un garde VERT imprime aussi son refus.
_AVANT_L_ERREUR = 12


def _finDErreur(journal: str) -> str | None:
    """Les lignes autour de la DERNIERE erreur, ou None si le journal n'en porte aucune."""
    lignes = journal.splitlines()
    marques = [i for i, ligne in enumerate(lignes) if "##[error]" in ligne]
    if not marques:
        return None
    return "\n".join(lignes[max(0, marques[-1] - _AVANT_L_ERREUR) : marques[-1] + 1])


def classe(journal: str, ordonnes: list[str]) -> tuple[str, str]:
    """A qui ce rouge appartient, et pourquoi. Rend `INDETERMINE` plutot que d'inventer une cause."""
    if _NATIF.search(journal):
        return ("RUNNER", NATIF)
    if len(ordonnes) >= SEUIL_EFFONDREMENT:
        return ("RUNNER", EFFONDREMENT)
    if ordonnes:
        return ("DEPOT", UN_BANC)
    fin = _finDErreur(journal)
    if fin is None:
        return ("INDETERMINE", INCONNU)
    if _APPRO.search(fin):
        return ("FORGE", APPROVISIONNEMENT)
    if _ANNULE in fin:
        return ("CASCADE", ANNULATION)
    return ("INDETERMINE", INCONNU)


def _autoTest() -> int:
    """Les temoins, sur des extraits de journaux REELS de la forge."""
    # Forme « resume » : une ligne par test, a la fin du rapport surefire.
    resume = (
        "build\tBuild + tests\t2026-08-29T14:31:22Z [ERROR] Tests run: 5306, Failures: 1\n"
        "build\tBuild + tests\t2026-08-29T14:31:22Z [ERROR]   AppTest.le_stage_partage_reste_ajustable:145 [une scene]\n"
    )
    assert testsEchoues(resume) == {"AppTest.le_stage_partage_reste_ajustable"}, testsEchoues(
        resume
    )

    # Forme « detail » : le paquet est present, et les arguments du cas aussi.
    detail = (
        "build\tBuild\t2026-08-29T14:31:27Z [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest"
        ".ouvrir_tout_charge_les_passages(FxRobot) -- Time elapsed: 0.002 s <<< ERROR!\n"
    )
    assert testsEchoues(detail) == {"ActiviteViewTest.ouvrir_tout_charge_les_passages"}, (
        testsEchoues(detail)
    )

    # Le sens NEGATIF : la ligne de COMPTE ne nomme aucun test, et ne doit rien produire.
    compte = (
        "build\tB\t2026-08-29T14:31:27Z [ERROR] Tests run: 40, Failures: 1, Errors: 0, Skipped: 0\n"
    )
    assert testsEchoues(compte) == set(), testsEchoues(compte)

    # Un meme test vu sous les DEUX formes dans le meme journal ne compte qu'une fois.
    assert len(testsEchoues(resume + detail + resume)) == 2

    # Le rapport agrege par test, et porte le denominateur.
    #
    # Le test le PLUS frequent porte ici un nom alphabetiquement PLUS GRAND que le rare : sans cela,
    # le tri par frequence et le tri alphabetique rendraient le meme ordre, et le temoin ne dirait
    # rien de celui qu'il pretend tenir. Mesure : la mutation qui retire `-c[1]` y a d'abord survecu.
    parRun = {
        "r1": {"ZStageTest.le_stage_partage_reste_ajustable"},
        "r2": {"ZStageTest.le_stage_partage_reste_ajustable", "AbandonTest.bandeau_suit"},
        "r3": set(),
    }
    lignes = rapport(parRun, tirages=200)
    assert lignes[0] == ("ZStageTest.le_stage_partage_reste_ajustable", 2, 200), lignes
    assert lignes[1] == ("AbandonTest.bandeau_suit", 1, 200), lignes
    # Le sens NEGATIF : un rapport qui rendrait toujours vide passerait tout le reste.
    assert lignes, "trois runs dont deux rouges doivent produire des lignes"

    # L'ORDRE decide. Un extrait reel : un test tombe, puis une classe entiere cinq secondes plus
    # tard. Le premier est le suspect, les vingt et un suivants sont ce qu'il a emporte.
    cascade = (
        "b\tB\t2026-08-29T14:31:22Z [ERROR] fr.univ_amu.iut.qualification.view.ScenarioSelectionEcouteTest"
        ".personnaliser_la_selection(FxRobot) -- Time elapsed: 4.159 s <<< ERROR!\n"
        "b\tB\t2026-08-29T14:31:27Z [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest"
        ".ouvrir_tout_charge_les_passages(FxRobot) -- Time elapsed: 0.002 s <<< ERROR!\n"
        "b\tB\t2026-08-29T14:31:27Z [ERROR] fr.univ_amu.iut.analyse.view.ActiviteViewTest"
        ".sans_courbe_tracee_l_export_est_grise(FxRobot) -- Time elapsed: 0.002 s <<< ERROR!\n"
    )
    ordonnes = testsEchouesOrdonnes(cascade)
    assert ordonnes[0] == "ScenarioSelectionEcouteTest.personnaliser_la_selection", ordonnes
    assert len(ordonnes) == 3, ordonnes

    # Le sens NEGATIF : melanger l'ordre doit CHANGER la tete. Sans cela, une implementation qui
    # trierait par nom passerait le temoin ci-dessus, `ActiviteViewTest` venant avant `Scenario`.
    lignes = cascade.strip().split("\n")
    inverse = "\n".join(reversed(lignes)) + "\n"
    assert testsEchouesOrdonnes(inverse)[0] != ordonnes[0], "la tete doit suivre l'ordre du journal"

    # Surefire nomme le MEME test deux fois : en detail pendant la course, puis dans le resume final.
    # Sans dedoublonnage, une cascade de vingt et un tests en compterait quarante-deux, et un test vu
    # dans les deux formes passerait pour tombe deux fois. Mesure : la mutation qui retire le
    # dedoublonnage y a d'abord survecu, mon extrait n'ayant aucun test repete.
    deuxFois = cascade + (
        "b\tB\t2026-08-29T14:32:00Z [ERROR]   ScenarioSelectionEcouteTest.personnaliser_la_selection:88\n"
    )
    assert len(testsEchouesOrdonnes(deuxFois)) == 3, testsEchouesOrdonnes(deuxFois)
    assert (
        tete(testsEchouesOrdonnes(deuxFois))
        == "ScenarioSelectionEcouteTest.personnaliser_la_selection"
    )

    # La tete d'une tentative, et sa suite.
    assert tete(ordonnes) == "ScenarioSelectionEcouteTest.personnaliser_la_selection"
    assert len(suite(ordonnes)) == 2, suite(ordonnes)

    # Un test peut etre en tete ICI et dans la suite LA : la separation est une observation par
    # tirage, pas un classement definitif.
    parTentative = [ordonnes, list(reversed(ordonnes))]
    tetes, suites = comptesParRang(parTentative)
    assert tetes["ScenarioSelectionEcouteTest.personnaliser_la_selection"] == 1, tetes
    assert suites["ScenarioSelectionEcouteTest.personnaliser_la_selection"] == 1, suites

    # ---- Le CLASSEMENT d'une tentative rouge (#4187) ----
    #
    # Les extraits viennent de journaux REELS de la forge, cites au plus court. Le releve savait deja
    # dire « echouee pour autre chose » ; il ne disait pas QUOI, et les quatre causes n'appellent pas
    # la meme conduite.

    # Un banc qui vacille : c'est NOUS, et un rejeu ne repare rien.
    assert classe("", ["ScenarioAccueilTest.chaque_carte"]) == ("DEPOT", UN_BANC), classe(
        "", ["ScenarioAccueilTest.chaque_carte"]
    )

    # La couche graphique native manque : le runner, et le rejeu est la bonne conduite.
    natif = (
        "Could not initialize class com.sun.javafx.font.freetype.OSPango\n"
        "ExceptionInInitializerError: java.lang.UnsatisfiedLinkError: no javafx_font_pango"
    )
    assert classe(natif, [])[0] == "RUNNER", classe(natif, [])

    # Une JVM entiere qui tombe : le runner aussi, meme si des tests sont nommes.
    assert classe("", [f"T{i}.cas" for i in range(60)])[0] == "RUNNER", "60 tests tombes"

    # L'approvisionnement : ni nous ni le runner, et le rejeu est la bonne conduite.
    appro = (
        "[ERROR] Plugin org.apache.maven.plugins:maven-surefire-plugin:3.5.6 or one of its\n"
        "dependencies could not be resolved:\n##[error]Process completed with exit code 1."
    )
    assert classe(appro, []) == ("FORGE", APPROVISIONNEMENT), classe(appro, [])
    action = (
        "##[error]An action could not be found at the URI 'https://codeload.github.com/...'\n"
        "##[error]Failed to download archive 'https://codeload.github.com/...' after 1 attempts."
    )
    assert classe(action, [])[0] == "FORGE", classe(action, [])

    # Une annulation : une CONSEQUENCE, pas une cause. Le rejeu ne dit rien tant que la vraie
    # cause n'est pas lue ailleurs.
    assert classe("##[error]The operation was canceled.", [])[0] == "CASCADE"

    # Le sens NEGATIF, celui qui empeche le classement de tout absorber : un journal qui ne porte
    # AUCUNE erreur ne se range pas. Trois tentatives reelles sont dans ce cas, et les ranger de
    # force aurait invente une cause (ADR 2213).
    assert classe("Cleaning up orphan processes\n", [])[0] == "INDETERMINE"

    # Et l'inverse : un journal ou « REFUSE » traine parce qu'un garde VERT explique son refus ne
    # doit pas passer pour un echec de garde. Ce faux positif a range 20 tentatives sur 20 lors du
    # premier dessin, et c'est ce qui a fait lire la ligne d'erreur FINALE plutot que le journal.
    vert = "Ce garde REFUSE plutot que de conclure sur ce qu il n a pas lu.\nverdict=ok\n"
    assert classe(vert, [])[0] == "INDETERMINE", classe(vert, [])

    # LE temoin qui tient la fenetre. Un journal ou le motif d'approvisionnement traine LOIN au
    # dessus, alors que l'erreur finale est une annulation. Lire le journal entier rend « FORGE » et
    # se trompe de conduite : on rejouerait, alors que la vraie cause est ailleurs, dans l'etape qui
    # a rouge la premiere. Sans ce temoin, remplacer la fenetre par le journal entier survivait.
    loin = (
        "[INFO] telechargement: cette dependance could not be resolved au premier essai, reprise\n"
        + "[INFO] compilation\n" * 40
        + "##[error]The operation was canceled.\n"
    )
    assert classe(loin, [])[0] == "CASCADE", classe(loin, [])

    print("auto-test : 24 temoins verts")
    return 0


def _jours() -> int:
    if "--jours" in sys.argv:
        return int(sys.argv[sys.argv.index("--jours") + 1])
    return 21


def _classement(jours: int) -> int:
    """A qui appartiennent les rouges rejoues, et donc lesquels valent un rejeu."""
    rejoues, tirages = relances(jours)
    if not tirages:
        print("Aucun tirage lu : `gh` est-il installe et authentifie ?")
        return 1
    parts: dict[tuple[str, str], int] = {}
    lues = 0
    for r in rejoues:
        for tentative in range(1, r["tentatives"]):
            journal = journalDeTentative(r["id"], tentative)
            if not journal:
                continue
            lues += 1
            cle = classe(journal, testsEchouesOrdonnes(journal))
            parts[cle] = parts.get(cle, 0) + 1
    print(
        f"CLASSEMENT | fenetre={jours}j | tirages={tirages} | relances={len(rejoues)}"
        f" | tentatives rouges lues={lues}"
    )
    if not lues:
        print("\nAucune tentative lue : rien a classer.")
        return 0
    print("\n  A QUI CE ROUGE APPARTIENT       et ce que la conduite en fait")
    for (qui, pourquoi), n in sorted(parts.items(), key=lambda c: (-c[1], c[0])):
        print(f"  {n:3d}  {100 * n / lues:5.1f} %  {qui:12s} {pourquoi}")
    rejouables = sum(n for (qui, _), n in parts.items() if qui in ("RUNNER", "FORGE"))
    print(
        f"\n  {rejouables}/{lues} valent un rejeu ({100 * rejouables / lues:.0f} %)."
        f" Les autres le rendent inutile : la cause revient au tirage suivant."
    )
    return 0


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    jours = _jours()
    if "--classe" in sys.argv:
        return _classement(jours)
    rejoues, tirages = relances(jours)
    if not tirages:
        print("Aucun tirage lu : `gh` est-il installe et authentifie ?")
        return 1
    parTentative, muets = [], []
    for r in rejoues:
        for tentative in range(1, r["tentatives"]):
            ordonnes = testsEchouesOrdonnes(journalDeTentative(r["id"], tentative))
            if ordonnes:
                parTentative.append(ordonnes)
            else:
                muets.append(r["id"])
    tetes, suites = comptesParRang(parTentative)
    print(
        f"RELEVE bancs | fenetre={jours}j | tirages={tirages} | relances={len(rejoues)}"
        f" | en tete={len(tetes)} | dans la suite={len(suites)}"
    )
    if not tetes:
        print("\nAucun test nomme dans les tentatives echouees.")
    # En tete d'abord : c'est la population des SUSPECTS, et elle est la seule a designer quelque
    # chose. « Dans la suite » compte ce qu'une cascade a emporte, et un test peut etre les deux.
    print("\n  EN TETE (suspects)          tentatives ou il tombe le PREMIER")
    for test, n in sorted(tetes.items(), key=lambda c: (-c[1], c[0])):
        aussi = suites.get(test, 0)
        reste = f", et {aussi} fois dans la suite" if aussi else ""
        print(f"  {n:3d}/{tirages}  {100 * n / tirages:6.3f} %  {test}{reste}")
    emportes = {t: n for t, n in suites.items() if t not in tetes}
    if emportes:
        print(f"\n  {len(emportes)} test(s) JAMAIS en tete : victimes seules, rien ne les accuse.")
    if muets:
        print(
            f"\n{len(muets)} tentative(s) echouee(s) sans aucun test nomme, donc "
            f"echouees pour autre chose : {', '.join(str(i) for i in sorted(set(muets)))}."
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
