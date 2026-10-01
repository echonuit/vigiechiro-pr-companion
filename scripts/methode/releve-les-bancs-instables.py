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

import ast
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import sort_si_contrat_demande
from _commun.forge import interroge

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


def entraineurs(parTentative: list[list[str]]) -> dict[str, dict[str, int]]:
    """Pour chaque victime, DERRIERE QUI elle tombe et combien de fois.

    `comptesParRang` dit combien de fois un banc est tombe dans la suite. Il ne dit pas derriere qui,
    et les deux formes que ce nombre recouvre demandent des conduites opposees :

    - **un couplage** : `SonsValidationViewTest` est tombe 47 fois, TOUJOURS derriere
      `ScenarioSelectionEcouteTest`. Ce n est pas son instabilite, c est une dependance entre classes ;
    - **une dispersion** : `ImportationClicImporterTest` est tombe 4 fois derriere QUATRE bancs
      differents. C est ce que fait tout banc situe en aval d une suite deja cassee, et cela ne dit
      rien de lui.

    Le nombre seul confond les deux, et il a servi a conclure de travers : le corps de #5275 lisait
    « plus souvent victime qu accuse, donc fragile a l etat laisse par d autres », alors que ses
    quatre entraineurs etaient quatre bancs distincts (#5312).
    """
    parVictime: dict[str, dict[str, int]] = {}
    for ordonnes in parTentative:
        premier = tete(ordonnes)
        if not premier:
            continue
        meneur = _classeDe(premier)
        # UNE TENTATIVE EST UNE VOIX. Compter les methodes tombees donnerait le poids d une classe
        # a son nombre de cas, et surtout laisserait un EFFONDREMENT fabriquer des couplages : une
        # seule tentative de la fenetre du 2026-09-06 a perdu 550 tests sur 88 classes, et comptee
        # en methodes elle suffisait a couronner son meneur sur quatorze victimes « a 100 % ». En
        # tentatives, plus aucune victime n a de dominant (#5312).
        victimes = {_classeDe(a) for a in suite(ordonnes)} - {meneur}
        for victime in victimes:
            compte = parVictime.setdefault(victime, {})
            compte[meneur] = compte.get(meneur, 0) + 1
    return parVictime


def _classeDe(test: str) -> str:
    """La CLASSE d un `Classe.methode`.

    L agregation se fait a la classe et non a la methode, parce que la cascade est un phenomene de
    classe : le `Stage` de TestFX est partage dans un meme fork, et c est la classe qui laisse
    derriere elle l etat que la suivante ne supporte pas. Mesure a la methode, la meme cascade se
    disperse sur dix lignes et le couplage devient invisible : c est ce que le premier jet de ce
    rapport a montre."""
    return test.split(".")[0]


def dominant(comptes: dict[str, int]) -> tuple[str, int, bool] | None:
    """L entraineur le plus frequent, son compte, et s il DOMINE.

    Domine veut dire : PLUS de la moitie des chutes, et au moins deux fois.

    La majorite est STRICTE, et ce n est pas un detail : `MainViewTest` tombe 34 fois derriere DEUX
    bancs, 17 chacun. Une regle a « au moins la moitie » en couronnait un, et le rapport aurait
    accuse l un des deux au hasard de l ordre alphabetique. Une egalite n accuse personne.

    Le second seuil ecarte le banc tombe une seule fois, ou le premier venu ferait toujours 100 %.
    """
    if not comptes:
        return None
    qui, n = max(comptes.items(), key=lambda c: (c[1], c[0]))
    total = sum(comptes.values())
    return qui, n, n >= 2 and n * 2 > total


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


def _borne(jours: int) -> str:
    return subprocess.run(
        ["date", "-u", "-d", f"-{jours} days", "+%Y-%m-%dT%H:%M:%SZ"],
        capture_output=True,
        text=True,
        check=False,
    ).stdout.strip()


def relances(jours: int) -> tuple[list[dict], int, int]:
    """Les runs RELANCES, le nombre de tirages, et le nombre de ROUGES QUE CE FILTRE ECARTE.

    `gh run list` ne suffit pas : il plafonne, et surtout il ne porte pas `run_attempt`.

    ⟨le troisieme nombre est la correction de #5738⟩ Ne garder que `tentatives > 1` n est pas une
    faute : **relancer est le geste par lequel quelqu un juge qu un rouge est une bascule plutot
    qu une regression**, et sans ce filtre le releve melangerait un defaut pousse puis corrige au
    commit suivant avec un banc qui vacille. Le filtre reste donc.

    Ce qui etait fautif, c est qu il ne se DISAIT pas. Mesure du 2026-10-01 sur trente jours :
    **38** tirages ont rougi sans etre rejoues, contre **21** rejoues. Les tirages porteurs d un
    rouge sont donc 59, et le releve en lit 21. Qui lisait « 1/564 » escomptait un minorant sur la
    fenetre, sans savoir qu il portait sur une sous-population dont il ignorait l existence.

    Ce nombre remonte pour que `limiteDeLecture` le dise, parce qu une limite qui vit dans une
    docstring n atteint pas le lecteur du rapport. C est l article A3, et c est le defaut que #5617
    a corrige sur l AUTRE limite de cette meme fonction - la, une limite declaree etait fausse par
    exces de modestie ; ici, une limite reelle n etait pas declaree du tout.
    """
    # ⟨ce releve REFUSE desormais, la ou il rendait du vide⟩ Son ancien `_forge` rendait `""` quand
    # `gh` manquait ET avalait le code de retour, si bien qu une forge qui repond par une erreur
    # devenait « aucun banc instable » : `"".splitlines()` rend `[]`, la boucle ne tourne pas, et le
    # releve conclut. Un releve ne bloque pas, c est son regime - mais « je n ai pas pu lire » n est
    # pas « rien a signaler », et c est la seule distinction que son silence effacait (#5544).
    brut = interroge(
        [
            "api",
            "--paginate",
            f"repos/{DEPOT}/actions/workflows/{FLUX}/runs?per_page=100",
            "-q",
            ".workflow_runs[] | [.id, .run_attempt, .conclusion, .created_at, .head_sha] | @tsv",
        ],
        quoi=f"les tirages de « Java CI with Maven » (flux {FLUX})",
    )
    return comptesDesTirages(brut, _borne(jours))


def comptesDesTirages(brut: str, borne: str) -> tuple[list[dict], int, int]:
    """Le tri des tirages, extrait pour etre eprouvable HORS LIGNE.

    Il vivait dans le corps de `relances`, donc derriere un appel reseau, donc hors d atteinte de
    tout cas. Rien ne reliait le troisieme nombre - les rouges ecartes - a ce que les lignes disent :
    une mutation qui l aurait fixe a zero aurait laisse l auto-test vert, et la phrase de limite
    aurait annonce « 0 tirage qui a rougi sans etre rejoue » sur une fenetre qui en porte 38.

    C est l extraction que #5617 a faite de `joindreLesJournaux`, et pour la meme raison.
    """
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
    rougesEcartes = sum(1 for r in dans if r["verdict"] == "failure" and r["tentatives"] == 1)
    return [r for r in dans if r["tentatives"] > 1], len(dans), rougesEcartes


# Article A3, [ADR 3627] : une mesure rend ce qu elle a lu ET ce qu elle n a pas pu ouvrir. Le releve
# n ouvre qu UN flux (`FLUX`) et, dedans, qu UN atelier. Tous les autres ateliers qui lancent la
# suite lui sont invisibles, et ce n est pas theorique : le flake de #4616 n a ete vu que sous
# `fuseau-alternatif`, celui de #5073 que sous un fuseau lui aussi. Ses taux sont donc des MINORANTS.
#
# La liste des invisibles se DERIVE du workflow, elle ne se recopie pas : recopiee, elle aurait
# vieilli au premier atelier ajoute, et une limite perimee est pire qu une limite absente. C est
# l article A5, [ADR 2385] - le point de comparaison n est jamais une liste tenue a la main, c est
# elle qui derive - applique ici a une sortie machine plutot qu a de la prose.
#
# [ADR 3627]: ../../dev-docs/decisions/3627-une-mesure-dit-ce-qu-elle-n-a-pas-pu-lire.md
# [ADR 2385]: ../../dev-docs/decisions/2385-la-doc-chiffree-est-adossee-au-code.md
# ⟨il n y a PAS d atelier lu, et c est la correction de #5617⟩ Une constante `ATELIER_LU = "build"`
# vivait ici, et un parametre `atelier` dans `journalDeTentative`. NI L UN NI L AUTRE N ETAIT
# EMPLOYE : l appel telecharge l archive du RUN ENTIER et joint tous ses fichiers, soit un par
# atelier. Le releve lisait donc les treize ateliers de `maven.yml` tout en DECLARANT n en lire qu un.
#
# Une limitation declaree a tort est pire qu une limitation reelle : qui lit « je ne vois qu un
# atelier sur huit » escompte un taux qui n a pas besoin de l etre. Prouve sur l execution
# 36747543152, dont `fuseau-alternatif` avait rouge : son journal est dans l archive, et les bancs qui
# n y tombent que la figurent bien dans la sortie.
FLUX_LU = pathlib.Path(__file__).resolve().parents[2] / ".github/workflows/maven.yml"

_ATELIER = re.compile(r"\n  ([a-z][a-z0-9_-]*):")


def ateliersQuiLancentLaSuite(yml: str) -> list[str]:
    """Les ateliers d un workflow qui lancent la suite Maven, dans l ordre du fichier."""
    if "jobs:" not in yml:
        return []
    corps = "\n" + yml.split("\njobs:", 1)[1]
    bornes = [(m.start(), m.group(1)) for m in _ATELIER.finditer(corps)]
    trouves = []
    for i, (debut, nom) in enumerate(bornes):
        fin = bornes[i + 1][0] if i + 1 < len(bornes) else len(corps)
        bloc = corps[debut:fin]
        if "mvnw" in bloc and re.search(r"\b(test|verify)\b", bloc):
            trouves.append(nom)
    return trouves


def limiteDeLecture(
    flux: pathlib.Path | None = None,
    rejoues: int | None = None,
    rougesEcartes: int | None = None,
) -> str:
    """Ce que le releve n a PAS pu lire. Il y en a DEUX, et la seconde a longtemps manque.

    Cette phrase a menti jusqu a #5617. Elle annoncait « atelier `build` seul, 7 invisibles », alors
    que `joindreLesJournaux` joint UN fichier par atelier de l archive - les treize de `maven.yml`.
    Cette limite-la est reparee : les autres FLUX restent dehors, et c est dit.

    ⟨la seconde limite, #5738⟩ Le releve ne lit que les tirages **rejoues**. Un rouge resolu par une
    poussee - un rebase, un commit de correction - garde `run_attempt=1` et son journal n est jamais
    ouvert. Mesure du 2026-10-01 : 38 tirages dans ce cas contre 21 rejoues, soit 21 lus sur les 59
    qui portent un rouge. Elle vivait dans la docstring de `relances`, c est-a-dire nulle part pour
    qui lit le rapport.

    Trouvee par le rouge d une session pair, qui avait rebase au lieu de relancer : son banc
    n apparaissait pas au releve, et l instrument rendait une sortie parfaitement plausible -
    quatorze tests en tete, des taux a trois decimales, un denominateur juste.

    Les deux comptes s INJECTENT plutot que d etre recalcules : les recalculer ici demanderait un
    second appel reseau pour dire ce que l appelant vient d apprendre. Absents, la phrase dit qu ils
    sont NON DENOMBRES, parce qu un appelant qui ne les fournit pas ne doit pas obtenir une limite
    qui a l air chiffree.
    """

    def combien(n: int | None, quoi: str) -> str:
        return f"{n} {quoi}" if n is not None else f"un nombre NON DENOMBRE de {quoi}"

    rejoint = (
        f" Lus parmi les tirages : les {combien(rejoues, 'tirages REJOUES')} seulement."
        f" Jamais ouverts : les {combien(rougesEcartes, 'tirages qui ont ROUGI SANS etre rejoues')}."
    )
    flux = FLUX_LU if flux is None else flux
    if not flux.exists():
        # A3 jusqu au bout : ne pas pouvoir denombrer ce qu on lit est encore quelque chose qu on n a
        # pas pu lire, et le taire rendrait la sortie plus rassurante qu elle ne doit l etre.
        return (
            f"  Lu : tous les ateliers de chaque archive, mais leur nombre est NON DENOMBRE,"
            f" `{flux.name}` etant introuvable."
            + rejoint
            + " Invisibles : tous les autres flux qui"
            " lancent la suite. Les taux ci-dessous sont des MINORANTS (article A3)."
        )
    lus = ateliersQuiLancentLaSuite(flux.read_text(encoding="utf-8"))
    return (
        f"  Lu : `{flux.name}`, ses {len(lus)} atelier(s) qui lancent la suite"
        f" ({', '.join(lus)}) - l archive d une tentative en porte un journal par atelier."
        + rejoint
        + " Invisibles : tous les autres flux qui lancent la suite."
        " Les taux ci-dessous sont des MINORANTS (article A3)."
    )


def journalDeTentative(idRun: int, tentative: int) -> str:
    """Le journal d'UNE tentative, decompresse. `--log-failed` ne rend que la DERNIERE.

    **Cet appel NE passe PAS par `_commun.forge.interroge`, et c est nomme plutot que tu** (#5544).
    Trois raisons, chacune suffisante :

    - il rend des **octets**, pas du texte : c est une archive zip, et `interroge` rend `stdout`
      decode ;
    - il exige un **second outil**, `unzip`, dont l absence est aussi une raison de renoncer ;
    - un journal **vide est legitime** ici : les journaux de tentative expirent, donc `""` est une
      reponse et non un silence. C est exactement l inverse du cas que #5544 corrige ailleurs dans ce
      fichier, ou le vide effacait une erreur.

    L y forcer demanderait une variante binaire de `interroge` pour un seul appelant, et ferait
    disparaitre la troisieme distinction. Elle reste donc ici, sciemment.
    """
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
        return joindreLesJournaux(pathlib.Path(dossier))


def joindreLesJournaux(dossier: pathlib.Path) -> str:
    """Tous les journaux d atelier de l archive, joints. UN fichier par atelier, a la racine.

    Extrait pour etre eprouvable : c est CETTE fonction qui decide ce que le releve lit, et la
    declaration de `limiteDeLecture` doit lui correspondre. Tant qu elle vivait dans le corps d un
    appel reseau, aucun cas ne pouvait confronter les deux, et la declaration a menti pendant des
    semaines (#5617).
    """
    morceaux = [
        fichier.read_text(encoding="utf-8", errors="replace")
        for fichier in sorted(dossier.glob("*.txt"))
    ]
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
COURSE_CONNUE = "course JavaFX du depot (#4823) : le rejeu passe, la cause revient"

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

# La course de #4823 : `PathUtils.configShape` itere les elements d un `Path` pendant qu un autre
# fil les modifie. Elle n a AUCUNE ligne du depot dans sa pile, et pourtant elle nous appartient :
# c est notre usage du graphe qui la declenche, et son remede a existe avant d etre reverte
# (#5114). Mesure du 2026-09-06 : sur sept effondrements de vingt et un jours, TROIS sont elle.
_COURSE = re.compile(r"PathUtils\.configShape")

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


COUCHE_GRAPHIQUE = "la cause profonde traverse la couche graphique"

# Une ligne de pile, et la marque de ce qui est A NOUS. Le classement lit la PILE et non le seul
# VOLUME : un defaut de runner qui n emporte qu un test lui echappait, et il concluait « c est nous »
# sur une pile ou nous n apparaissons pas (#5036).
# Le motif n est PAS ancre en debut de ligne : la forge prefixe chaque ligne de son journal par
# « atelier<TAB>etape<TAB>horodatage », si bien qu un `^\s*at` ne colle jamais. Le premier jet l a
# fait, et son temoin l a attrape.
_CADRE = re.compile(r"\bat [\w.$/]+\([^)]*\)")
_NOTRE = re.compile(r"\bfr\.univ_amu\.")

# La ligne qui OUVRE une pile : un nom d exception qualifie, hors ligne de cadre.
_EXCEPTION = re.compile(r"\b(?:[\w.]+\.)?\w*(?:Exception|Error)\b")


# L echec surefire d un cas, et la CAUSE PROFONDE qu il enveloppe.
_ECHEC_SUREFIRE = re.compile(r"<<< (?:ERROR|FAILURE)!")
_CAUSE = re.compile(r"\bCaused by: ")

# La couche graphique de JavaFX : le verre, le rendu, et le pont vers la boite a outils.
# `com.sun.javafx.text` est le MOTEUR DE TEXTE de Prism, frere de `com.sun.prism` : `PrismTextLayout`
# y calcule la mise en page. Il manquait, et un `NullPointerException` leve dans `TextRun.getAscent`
# etait donc impute au DEPOT (#4823, mesure sur un journal reel du 2026-09-02). C est la meme couche.
_GRAPHIQUE = re.compile(
    r"com\.sun\.glass\.|com\.sun\.prism\.|com\.sun\.javafx\.tk\.|com\.sun\.javafx\.text\."
)


def causeProfonde(journal: str) -> list[str]:
    """Les cadres de la DERNIERE `Caused by:` du premier echec surefire rencontre.

    Quatre portees plus larges ont ete essayees et refutees (#5036) :

    - le journal entier : il porte forcement nos cadres, ailleurs, donc jamais etranger ;
    - une pile quelconque : 61 % des piles d une suite VERTE sont etrangeres, donc toujours vrai ;
    - la pile du cas tombe : elle contient le cas, donc NOS lignes, par construction ;
    - un motif de cadre ancre en debut de ligne : la forge prefixe chaque ligne de son journal.

    Ce qui reste est la cause que TestFX enveloppe : elle n est appelee par aucun de nos cadres, et
    elle est unique par echec.
    """
    lignes = journal.splitlines()
    debut = next((i for i, l in enumerate(lignes) if _ECHEC_SUREFIRE.search(l)), None)
    if debut is None:
        return []
    cadres, dansLaCause = [], False
    for ligne in lignes[debut + 1 :]:
        if _CAUSE.search(ligne):
            cadres, dansLaCause = [], True
            continue
        if _CADRE.search(ligne):
            if dansLaCause:
                cadres.append(ligne)
            continue
        if dansLaCause and cadres:
            break  # la cause s arrete a la premiere ligne qui n est pas un cadre
    return cadres


def coucheGraphique(journal: str) -> bool:
    """La cause profonde de l echec traverse-t-elle la couche graphique de JavaFX ?

    C est la COUCHE qui distingue, pas le proprietaire des cadres. Cinq formulations plus seduisantes
    ont ete refutees sur des journaux reels (#5036) :

    1. aucune ligne du depot dans le JOURNAL : il en porte forcement, ailleurs ;
    2. une pile quelconque entierement etrangere : 61 % des piles d une suite VERTE le sont ;
    3. la pile du cas tombe : elle contient le cas, donc nos lignes, par construction ;
    4. un motif de cadre ancre en debut de ligne : la forge prefixe ses lignes de journal ;
    5. le PREMIER cadre de la cause profonde : une expiration de `WaitForAsyncUtils` ou une fermeture
       de socket appartiennent a autrui tout en etant NOTRE geste.

    La sixieme classe correctement les huit journaux reels dont on dispose. Elle generalise ce que le
    marqueur `OSPango` faisait deja pour un seul symptome : nommer la couche.
    """
    return any(_GRAPHIQUE.search(cadre) for cadre in causeProfonde(journal))


def classe(journal: str, ordonnes: list[str]) -> tuple[str, str]:
    """A qui ce rouge appartient, et pourquoi. Rend `INDETERMINE` plutot que d'inventer une cause."""
    if _NATIF.search(journal):
        return ("RUNNER", NATIF)
    # AVANT la taille, et c est tout le correctif. Un effondrement etait range sous RUNNER parce qu il
    # etait GROS, sans qu on regarde sa cause : trois des sept de la fenetre etaient la course du
    # depot, et le relevé conseillait de relancer une faute qui est la notre (#5333).
    if _COURSE.search(journal):
        return ("DEPOT", COURSE_CONNUE)
    if len(ordonnes) >= SEUIL_EFFONDREMENT:
        return ("RUNNER", EFFONDREMENT)
    if coucheGraphique(journal):
        return ("RUNNER", COUCHE_GRAPHIQUE)
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


def _assertions() -> list[int]:
    """Les lignes des `assert` de [#_autoTest], comptees dans la source plutot qu a la main.

    Un compte ecrit en dur derive des le cas suivant, et il derive **vers le bas** : on ajoute des
    cas plus souvent qu on en retire. Le derive ne peut pas mentir, et il refuse si la fonction
    disparait - ce qui est la seule facon pour ce compte de devenir faux.
    """
    source = pathlib.Path(__file__).read_text(encoding="utf-8")
    for noeud in ast.walk(ast.parse(source)):
        if isinstance(noeud, ast.FunctionDef) and noeud.name == "_autoTest":
            return [n.lineno for n in ast.walk(noeud) if isinstance(n, ast.Assert)]
    raise SystemExit("REFUS : `_autoTest` est introuvable dans ma propre source.")


def _autoTest() -> int:
    """Les temoins, sur des extraits de journaux REELS de la forge."""
    # LES TROIS FORMES D EFFONDREMENT (#5333). La taille seule rangeait les trois sous RUNNER, et le
    # relevé conseillait donc de relancer une faute qui est la notre.
    gros = [f"C{i}Test.m" for i in range(60)]
    natif = "java.lang.UnsatisfiedLinkError: no javafx_font_pango in java.library.path"
    assert classe(natif, gros) == ("RUNNER", NATIF), classe(natif, gros)
    course = "Caused by: java.util.ConcurrentModificationException\n  at com.sun.javafx.scene.shape.PathUtils.configShape"
    assert classe(course, gros) == ("DEPOT", COURSE_CONNUE), classe(course, gros)
    # Et un effondrement dont le journal ne dit rien reste au runner : sans ce cas, le correctif
    # aurait pu attribuer TOUT effondrement au depot.
    assert classe("rien de reconnaissable", gros) == ("RUNNER", EFFONDREMENT)
    # La course sur un rouge ORDINAIRE, pas un effondrement : elle nous appartient aussi.
    assert classe(course, ["UnTest.m"]) == ("DEPOT", COURSE_CONNUE)
    # LES DEUX FORMES QUE LE NOMBRE DE VICTIMES CONFOND (#5312). Sans le second cas, un rapport qui
    # nommerait toujours le premier entraineur venu passerait le premier et mentirait sur le second.
    couplage = [["MeneurTest.a", "SuiveurTest.b"]] * 5
    assert entraineurs(couplage) == {"SuiveurTest": {"MeneurTest": 5}}, entraineurs(couplage)
    assert dominant(entraineurs(couplage)["SuiveurTest"]) == ("MeneurTest", 5, True)
    # DEUX methodes d une meme classe ne se comptent pas : c est la mecanique du fork.
    assert entraineurs([["MemeTest.a", "MemeTest.b"]]) == {}
    # UNE TENTATIVE EST UNE VOIX : deux methodes d une meme classe victime ne valent pas deux.
    # Sans ce cas, un effondrement de 550 tests fabrique des couplages qui n existent pas.
    deuxMethodes = [["MeneurTest.a", "SuiveurTest.b", "SuiveurTest.c"]]
    assert entraineurs(deuxMethodes) == {"SuiveurTest": {"MeneurTest": 1}}

    disperse = [[f"Meneur{i}Test.a", "SuiveurTest.b"] for i in range(4)]
    qui, n, domine = dominant(entraineurs(disperse)["SuiveurTest"])
    assert not domine, (qui, n)
    assert len(entraineurs(disperse)["SuiveurTest"]) == 4

    # UNE EGALITE N ACCUSE PERSONNE : deux entraineurs a 17, le cas reel de MainViewTest.
    assert dominant({"A": 17, "B": 17}) == ("B", 17, False)
    # Mais une majorite stricte, oui.
    assert dominant({"A": 18, "B": 17}) == ("A", 18, True)
    # Une seule chute ne DOMINE pas : sinon le premier venu ferait toujours 100 %.
    assert dominant({"Seul.a": 1}) == ("Seul.a", 1, False)
    # Une tete sans suite n entraine personne.
    assert entraineurs([["SeulTest.a"]]) == {}
    assert dominant({}) is None
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

    # ---- La COUCHE decide, pas le volume (#5036) ----
    #
    # Extraits REELS du 1er septembre 2026, avec le prefixe de journal de la forge et la forme d un
    # bloc surefire : la ligne d echec, l exception enveloppante, puis `Caused by:` et sa pile. Un
    # premier jet a ecrit ces temoins sur un extrait SYNTHETIQUE sans cette forme, et ils passaient
    # alors que la regle ne voyait rien du journal reel.
    prefixe = "build\tB\t2026-09-01T06:45:30Z "
    echec = (
        prefixe
        + "[ERROR] fr.univ_amu.iut.AppTest.un_cas(FxRobot) -- Time elapsed: 2.2 s <<< ERROR!\n"
    )

    # La cause traverse la couche graphique : le runner, meme si NOTRE cadre ferme la pile.
    graphique = echec + "".join(
        prefixe + l + "\n"
        for l in (
            "java.lang.RuntimeException: java.lang.IndexOutOfBoundsException",
            "\tat org.testfx.util.WaitForAsyncUtils.waitFor(WaitForAsyncUtils.java:276)",
            "\tat fr.univ_amu.iut.AppTest.un_cas(AppTest.java:177)",
            "Caused by: java.lang.IndexOutOfBoundsException",
            "\tat java.base/java.nio.ByteBufferAsIntBufferB.put(ByteBufferAsIntBufferB.java:180)",
            "\tat com.sun.glass.ui.headless.HeadlessWindow.clearRect(HeadlessWindow.java:343)",
            "\tat javafx.stage.Stage.setScene(Stage.java:291)",
            "\tat fr.univ_amu.iut.AppTest.lambda$un_cas$2(AppTest.java:177)",
        )
    )
    assert classe(graphique, ["AppTest.un_cas"])[0] == "RUNNER", classe(
        graphique, ["AppTest.un_cas"]
    )

    # LE temoin qui a refute la cinquieme formulation : le premier cadre appartient a autrui, et
    # pourtant l attente qui expire est NOTRE geste. Sans lui, une expiration devenait « runner ».
    attente = echec + "".join(
        prefixe + l + "\n"
        for l in (
            "java.lang.RuntimeException: java.util.concurrent.TimeoutException",
            "\tat fr.univ_amu.iut.AppTest.un_cas(AppTest.java:140)",
            "Caused by: java.util.concurrent.TimeoutException",
            "\tat org.testfx.util.WaitForAsyncUtils.waitFor(WaitForAsyncUtils.java:276)",
            "\tat fr.univ_amu.iut.recette.Attente.que(Attente.java:118)",
        )
    )
    assert classe(attente, ["AppTest.un_cas"]) == ("DEPOT", UN_BANC), classe(
        attente, ["AppTest.un_cas"]
    )

    # Le MOTEUR DE TEXTE est la meme couche que le reste du rendu, et il manquait. Cadres pris dans
    # la section SUREFIRE d un journal reel du 2026-09-02, et non dans le dump de `SansExceptionAvalee`
    # qui parait plus haut : celui-la rapporte les memes cadres SANS le prefixe « at », que `_CADRE`
    # exige. Recopier la mauvaise des deux formes rendait ce temoin rouge alors que la regle est juste.
    texte = echec + "".join(
        prefixe + l + "\n"
        for l in (
            "java.lang.RuntimeException: java.lang.NullPointerException",
            "\tat fr.univ_amu.iut.AppTest.un_cas(AppTest.java:177)",
            (
                "Caused by: java.lang.NullPointerException: Cannot invoke"
                ' "com.sun.javafx.text.TextRun.getAscent()" because "<parameter1>" is null'
            ),
            "\tat com.sun.javafx.text.PrismTextLayout.shape(PrismTextLayout.java:849)",
            "\tat com.sun.javafx.text.PrismTextLayout.layout(PrismTextLayout.java:1239)",
            "\tat javafx.scene.text.Text.getLogicalBounds(Text.java:453)",
            "\tat javafx.scene.control.skin.TextFieldSkin.lambda$new$4(TextFieldSkin.java:249)",
        )
    )
    assert classe(texte, ["AppTest.un_cas"])[0] == "RUNNER", classe(texte, ["AppTest.un_cas"])

    # Et un echec SANS cause enveloppee reste ce qu il etait : un banc qui vacille.
    assert classe(echec, ["AppTest.un_cas"]) == ("DEPOT", UN_BANC)

    # Article A3 : la liste des ateliers invisibles se DERIVE du workflow. Le temoin la joue sur un
    # workflow factice, donc sans toucher le disque, et il rougit des qu un atelier qui lance la
    # suite cesse d etre reconnu - c est exactement la peremption qu une liste recopiee subirait.
    fauxFlux = """name: CI
jobs:
  build:
    steps:
      - run: ./mvnw -B verify
  fuseau-alternatif:
    steps:
      - run: ./mvnw -B test -Duser.timezone=Pacific/Kiritimati
  analyser:
    steps:
      - run: ./mvnw -B compile
"""
    assert ateliersQuiLancentLaSuite(fauxFlux) == ["build", "fuseau-alternatif"], (
        ateliersQuiLancentLaSuite(fauxFlux)
    )
    # `analyser` compile sans tester : il n est pas un atelier manquant, et l y compter gonflerait
    # la limite d un atelier qui n aurait de toute facon jamais rougi sur un banc.
    assert "analyser" not in ateliersQuiLancentLaSuite(fauxFlux)
    # Et un workflow sans aucun atelier rend une liste vide plutot que de lever : le releve doit
    # pouvoir dire « je n ai pas pu denombrer », pas mourir. C est le meme article A3.
    assert ateliersQuiLancentLaSuite("name: rien\n") == []
    # Et le flux introuvable ne se tait pas non plus : la sortie doit dire qu elle n a pas pu
    # denombrer, sans quoi un releve lance hors du depot afficherait des taux sans leur limite.
    absent = limiteDeLecture(pathlib.Path("/n-existe-pas/maven.yml"))
    assert "NON DENOMBRE" in absent and "MINORANTS" in absent, absent

    # ⟨LA JONCTION LIT TOUS LES ATELIERS, et c est elle qui decide⟩ L archive d une tentative porte
    # UN fichier par atelier a sa racine. Extraite pour etre eprouvable hors ligne : tant qu elle
    # vivait dans le corps d un appel reseau, aucun cas ne pouvait confronter ce qu elle lit a ce que
    # `limiteDeLecture` declare - et la declaration a menti pendant des semaines (#5617).
    with tempfile.TemporaryDirectory(prefix="vc-5617-") as bac:
        archive = pathlib.Path(bac)
        for nom, corps in (
            ("0_bats.txt", "rouge de bats"),
            ("2_fuseau-alternatif.txt", "rouge de fuseau"),
            ("3_build.txt", "rouge de build"),
            ("notes.md", "ceci n est pas un journal"),
        ):
            (archive / nom).write_text(corps, encoding="utf-8")
        joint = joindreLesJournaux(archive)
        for attendu in ("rouge de bats", "rouge de fuseau", "rouge de build"):
            assert attendu in joint, f"{attendu} manque du joint : {joint!r}"
        # Le negatif : seuls les `.txt` de la RACINE entrent, pas n importe quel fichier.
        assert "ceci n est pas un journal" not in joint, joint

    # ⟨LA DECLARATION SE CONFRONTE A LA JONCTION⟩ C est le cas qui aurait attrape le mensonge. Il ne
    # relit pas la phrase, il verifie qu elle ne pretend pas lire UN atelier quand la jonction les
    # lit TOUS, et qu elle nomme ceux qu elle lit.
    dite = limiteDeLecture()
    assert " seul." not in dite, f"la declaration pretend encore lire un atelier seul : {dite}"
    attendus = ateliersQuiLancentLaSuite(FLUX_LU.read_text(encoding="utf-8"))
    # ⟨UNE ANCRE INDEPENDANTE, et c est ce qui manquait⟩ Deriver l attente de la fonction qu on
    # controle rend le cas TAUTOLOGIQUE : si `ateliersQuiLancentLaSuite` rendait `[]`, la boucle
    # ci-dessous tournerait zero fois et la declaration dirait « ses 0 atelier(s) », donc le cas
    # passerait. Deux jobs de `maven.yml` sont connus pour lancer la suite depuis des mois : les
    # exiger NOMMEMENT ancre le cas sur autre chose que lui-meme.
    #
    # Trouve en appliquant une lecon d une session pair : quand on change le dessin d un controle, la
    # matrice de contraste se rejoue en ENTIER, car un temoin peut cesser de discriminer sans cesser
    # de passer - et une relecture ne le voit pas.
    for connu in ("build", "fuseau-alternatif"):
        assert connu in attendus, f"« {connu} » lance la suite et n est plus derive : {attendus}"
        assert connu in dite, f"« {connu} » est lu et n est pas nomme : {dite}"
    assert len(attendus) >= 2, attendus
    assert f"{len(attendus)} atelier" in dite, dite
    for atelier in attendus:
        assert atelier in dite, f"« {atelier} » est lu et n est pas nomme : {dite}"
    assert "MINORANTS" in dite, dite

    # ⟨LA SECONDE LIMITE SE DIT, ET AVEC SON CHIFFRE (#5738)⟩ Le releve ne lit que les tirages
    # rejoues. Cette limite vivait dans la docstring de `relances`, donc nulle part pour qui lit le
    # rapport. Les deux cas ci-dessous tiennent les deux etats de la phrase : chiffree quand
    # l appelant fournit les comptes, et explicitement NON DENOMBREE quand il ne les fournit pas.
    #
    # Le second n est pas decoratif : sans lui, une phrase qui tairait la limite en l absence de
    # comptes passerait le premier cas, et c est exactement la forme du defaut que ce lot corrige -
    # une limite qui n apparait que dans les conditions ou on pense a la chercher.
    chiffree = limiteDeLecture(rejoues=21, rougesEcartes=38)
    assert "21 tirages REJOUES" in chiffree, f"le compte des rejoues doit paraitre : {chiffree}"
    assert "38 tirages qui ont ROUGI SANS etre rejoues" in chiffree, (
        f"le compte des rouges ecartes doit paraitre : {chiffree}"
    )
    assert "NON DENOMBRE" not in chiffree, (
        f"chiffree, la phrase ne doit plus dire NON DENOMBRE : {chiffree}"
    )

    assert "NON DENOMBRE" in dite, f"sans comptes, la phrase doit le DIRE : {dite}"
    assert "REJOUES" in dite, f"la limite des rejoues doit etre dite meme sans chiffre : {dite}"
    assert "ROUGI SANS etre rejoues" in dite, (
        f"la limite des rouges non rejoues doit etre dite : {dite}"
    )

    # ⟨ET LA PHRASE PEUT ETRE CHIFFREE⟩ Un appelant qui ne pourrait pas lui passer les comptes
    # rendrait les deux cas ci-dessus vrais et sans effet sur le rapport.
    #
    # Le premier jet de ce cas cherchait le mot « rougesEcartes » dans la SOURCE de `relances`. Il a
    # rougi des que `relances` a delegue son tri a `comptesDesTirages` : la ressemblance avait change,
    # le comportement non. Un cas qui lit du texte de code mesure la forme, pas ce que la chose rend.
    import inspect as _inspect

    assert _inspect.signature(limiteDeLecture).parameters.keys() >= {
        "rejoues",
        "rougesEcartes",
    }, "la phrase doit pouvoir etre chiffree"

    # ⟨LE COMPTE DE LA LIGNE DE VERDICT EST DERIVE⟩ Il valait « 33 » en dur pour 66 assertions. Un
    # chiffre invente dans une ligne de verdict est ce que ce lot reproche a l instrument, et il
    # n aurait pas ete coherent de le laisser ici. L ancre est independante du compte : des LIGNES
    # reelles, en nombre superieur a un plancher que ce fichier depasse largement depuis #5617.
    lignesDesCas = _assertions()
    assert len(lignesDesCas) > 40, f"le compte derive ne trouve que {len(lignesDesCas)} cas"
    assert all(ligne > 0 for ligne in lignesDesCas), lignesDesCas

    # ⟨LE TRI DES TIRAGES, EPROUVE HORS LIGNE⟩ Les quatre formes que la fenetre rencontre, dans un
    # corpus ou chacune est presente une fois de plus que la precedente - sans quoi un detecteur qui
    # confondrait deux colonnes rendrait les bons totaux.
    lignesDuCorpus = (
        "1\t2\tsuccess\t2026-09-20T00:00:00Z\taaa",  # rejoue, vert  -> LU
        "2\t2\tfailure\t2026-09-21T00:00:00Z\tbbb",  # rejoue, rouge -> LU
        "3\t2\tfailure\t2026-09-22T00:00:00Z\tccc",
        "4\t1\tfailure\t2026-09-23T00:00:00Z\tddd",  # rouge NON rejoue -> ECARTE
        "5\t1\tfailure\t2026-09-24T00:00:00Z\teee",
        "6\t1\tfailure\t2026-09-25T00:00:00Z\tfff",
        "7\t1\tsuccess\t2026-09-26T00:00:00Z\tggg",  # vert simple -> ni lu ni ecarte
        "8\t1\tcancelled\t2026-09-27T00:00:00Z\thhh",  # hors CONCLUS
        "9\t1\tfailure\t2026-01-01T00:00:00Z\tiii",  # hors fenetre
    )
    tsv = "\n".join(lignesDuCorpus)
    borne = "2026-09-01T00:00:00Z"
    rejouesVus, tiragesVus, ecartesVus = comptesDesTirages(tsv, borne)
    assert [r["id"] for r in rejouesVus] == [1, 2, 3], rejouesVus
    assert tiragesVus == 7, f"les conclus de la fenetre sont 7, pas {tiragesVus}"
    assert ecartesVus == 3, f"les rouges non rejoues sont 3, pas {ecartesVus}"

    # LE SENS NEGATIF, et c est lui qui tient le troisieme nombre : un rouge REJOUE ne doit pas
    # compter comme ecarte, et un VERT non rejoue non plus. Sans ces deux cas, compter tous les
    # rouges, ou tous les non-rejoues, passerait le cas ci-dessus sur un corpus moins varie.
    _, _, sansRougeEcarte = comptesDesTirages(
        "1\t2\tfailure\t2026-09-21T00:00:00Z\tbbb\n7\t1\tsuccess\t2026-09-26T00:00:00Z\tggg",
        borne,
    )
    assert sansRougeEcarte == 0, (
        f"ni un rouge rejoue ni un vert simple n est ecarte : {sansRougeEcarte}"
    )

    # Et la fenetre mord : une borne posterieure a tout vide les trois.
    assert comptesDesTirages(tsv, "2027-01-01T00:00:00Z") == ([], 0, 0)

    # ⟨l APPEL, et non le verdict (ADR 4331)⟩ Aucun cas de cet auto-test n exercait le chemin de la
    # forge avant #5544 : `relances` n y est jamais appelee. Une mutation le montrait - retirer le
    # refus laissait ces 32 temoins verts - et c est le defaut que `loupe-4992` avait corrige chez
    # lui pour la meme raison. On lance donc le vrai chemin avec un PATH ou `gh` n existe pas, sans
    # reseau et en une milliseconde.
    chemin = os.environ.get("PATH", "")
    os.environ["PATH"] = str(pathlib.Path(__file__).parent)
    try:
        relances(1)
    except SystemExit as sortie:
        assert sortie.code == 2, f"le refus doit sortir en 2, pas en {sortie.code}"
    else:
        raise AssertionError("sans « gh », ce releve doit REFUSER au lieu de conclure a zero banc")
    finally:
        os.environ["PATH"] = chemin

    # ⟨le compte se DERIVE, il ne s ecrit plus a la main (#5738)⟩ Cette ligne annoncait « 33 temoins »
    # en dur, et le fichier en portait **66**. Le nombre etait donc faux avant ce lot, et il l est
    # devenu davantage en ajoutant des cas : une ligne de verdict qui porte un chiffre invente est
    # exactement ce que ce lot reproche au reste de l instrument. L unite change en meme temps, et
    # elle le dit : on compte des ASSERTIONS, pas des « temoins » dont personne ne savait plus la
    # definition.
    print(f"auto-test : {len(_assertions())} assertion(s) verte(s)")
    return 0


def _jours() -> int:
    if "--jours" in sys.argv:
        return int(sys.argv[sys.argv.index("--jours") + 1])
    return 21


def _classement(jours: int) -> int:
    """A qui appartiennent les rouges rejoues, et donc lesquels valent un rejeu."""
    rejoues, tirages, rougesEcartes = relances(jours)
    if not tirages:
        print("Aucun tirage lu : `gh` est-il installe et authentifie ?")
        return 1
    parts: dict[tuple[str, str], int] = {}
    parTentative: list[list[str]] = []
    lues = 0
    for r in rejoues:
        for tentative in range(1, r["tentatives"]):
            journal = journalDeTentative(r["id"], tentative)
            if not journal:
                continue
            lues += 1
            ordonnes = testsEchouesOrdonnes(journal)
            parTentative.append(ordonnes)
            cle = classe(journal, ordonnes)
            parts[cle] = parts.get(cle, 0) + 1
    print(
        f"CLASSEMENT | fenetre={jours}j | tirages={tirages} | relances={len(rejoues)}"
        f" | tentatives rouges lues={lues}"
    )
    # ⟨cette sous-commande ne disait AUCUNE limite (#5738)⟩ Elle rend des pourcentages - « 43 % valent
    # un rejeu » - sur une population dont elle taisait la composition. Et c est la surface la plus
    # exposee au defaut : qui lit un classement cherche a decider d une conduite, pas a estimer un
    # taux. La meme phrase que le relevé, parce qu une seconde formulation divergerait.
    print(limiteDeLecture(rejoues=len(rejoues), rougesEcartes=rougesEcartes))
    if not lues:
        print("\nAucune tentative lue : rien a classer.")
        return 0
    print("\n  A QUI CE ROUGE APPARTIENT       et ce que la conduite en fait")
    for (qui, pourquoi), n in sorted(parts.items(), key=lambda c: (-c[1], c[0])):
        print(f"  {n:3d}  {100 * n / lues:5.1f} %  {qui:12s} {pourquoi}")
    rejouables = sum(n for (qui, _), n in parts.items() if qui in ("RUNNER", "FORGE"))
    course = sum(n for (_, pourquoi), n in parts.items() if pourquoi == COURSE_CONNUE)
    print(
        f"\n  {rejouables}/{lues} valent un rejeu ({100 * rejouables / lues:.0f} %)."
        f" Les autres le rendent inutile : la cause revient au tirage suivant."
    )
    if course:
        # La course de #4823 tient les deux bouts, et une ligne qui n en dirait qu un mentirait :
        # relancer PASSE, parce qu elle est intermittente, et ne repare rien.
        print(
            f"  Dont {course} pour la course de #4823 : le rejeu passe, et c est ce qui la rend"
            f" invisible. Elle n est pas comptee au-dessus, parce que relancer ne la repare pas."
        )
    _derriereQui(parTentative)
    return 0


def _derriereQui(parTentative: list[list[str]]) -> None:
    """Les victimes, et derriere QUI elles tombent : un couplage ne se conduit pas comme un bruit."""
    parVictime = entraineurs(parTentative)
    if not parVictime:
        return
    lignes = []
    for victime, comptes in parVictime.items():
        tete_, n, domine = dominant(comptes)
        lignes.append((sum(comptes.values()), victime, tete_, n, domine, len(comptes)))
    # Les COUPLAGES d abord : un total eleve mais disperse ne dit rien, alors qu un couplage nomme
    # une classe a regarder. Trier par total seul enterrait les 47 chutes de SonsValidationViewTest
    # sous des victimes a 105 qui n accusent personne.
    lignes.sort(key=lambda l: (not l[4], -l[0], l[1]))

    larges = sorted(
        (
            (len(o), len({_classeDe(x) for x in o}), _classeDe(tete(o)))
            for o in parTentative
            if tete(o)
        ),
        reverse=True,
    )[:3]
    if larges and larges[0][0] > 100:
        print("\n  LES EFFONDREMENTS, qui ne sont pas des cascades a attribuer")
        for n, nc, meneur in larges:
            print(f"  {n:4d} tests sur {nc:3d} classes, tentative menee par {meneur}")

    print("\n  DERRIERE QUI LES VICTIMES TOMBENT")
    print(
        "  un entraineur DOMINANT est un couplage entre classes, une dispersion n accuse personne"
    )
    for total, victime, tete_, n, domine, distincts in lignes[:12]:
        verdict = (
            f"couple a {tete_} ({n}/{total})" if domine else f"disperse, {distincts} entraineurs"
        )
        print(f"  {total:3d}  {victime:44s} {verdict}")
    if len(lignes) > 12:
        print(f"  … et {len(lignes) - 12} autres victimes, non montrees")


def main() -> int:
    if "--auto-test" in sys.argv:
        return _autoTest()
    jours = _jours()
    if "--classe" in sys.argv:
        return _classement(jours)
    rejoues, tirages, rougesEcartes = relances(jours)
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
    print(limiteDeLecture(rejoues=len(rejoues), rougesEcartes=rougesEcartes))
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


# Pourquoi `rapport` : il COMPTE, il ne juge pas. Combien de fois chaque banc a rougi sur combien de
# tirages est une mesure, et l ADR 4187 en fait le prealable au classement d un rouge, non le
# classement lui-meme. Il sort en 0 meme quand les taux sont mauvais.
CONTRAT = {
    "geste": "combien de fois chaque banc a rougi, sur combien de tirages",
    "population": "les runs d UN flux (`maven.yml`) sur une fenetre de jours, dont il ne lit "
    "qu UN atelier (`build`). Les six autres ateliers du meme flux et tous les autres flux qui "
    "lancent la suite lui sont INVISIBLES : ses taux sont des MINORANTS, la sortie le dit, et la "
    "liste se derive du workflow. Article A3, ADR 3627 ; limite trouvee a la passe 7 de la cloture "
    "de #5273, ou il etait le seul des trois instruments a n en declarer aucune",
    "dispositif": "rapport",
    "seuil": "(sans objet)",
    "temoin": "scripts/methode/releve-les-bancs-instables.py --auto-test",
    "decision": "ADR 4187",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(main())
