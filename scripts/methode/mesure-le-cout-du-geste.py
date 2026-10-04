#!/usr/bin/env python3
"""Mesure la duree de la suite pour plusieurs variantes d un geste, avec sa dispersion (#5732).

La question de #5732 est « le geste du pointeur coute-t-il vraiment 10 % de temps de suite ». Une
paire de passes y repondait +10,1 %, sans dispersion, et l ADR 5628 refuse de conclure un ecart de
medianes sans elle.

## Ce que cet instrument refuse de faire

**Il ne compare pas deux arbres.** La paire d origine mesurait un vieux `main` contre un neuf, et six
lots avaient touche des fichiers de test entre-temps : l ecart portait ces six lots. Ici un SEUL
arbre, et les variantes ne different que par le fichier que l on permute.

**Il ne conclut pas sous charge.** Une JVM de travail etrangere ne fait pas rougir une mesure de
duree, elle la rend fausse sans le dire - une premiere paire de #5732 a ete perdue ainsi. Ce refus a
DEUX causes qu il ne faut pas confondre, et elles ne portent pas le meme verdict :

- une JVM de travail etrangere a tourne pendant une passe -> REFUS de mesure, la serie se rejoue ;
- je n ai pas pu savoir s il y en avait une -> code 2, le verdict porte sur le POSTE et non sur la
  mesure (ADR 5407).

Les fondre ferait dire « charge subie » a un poste ou `/proc` n a pas repondu.

## Ce qu il ne reecrit pas

`mediane` et `dispersion_robuste` viennent de `.github/scripts/mesure_duree_portail.py`, avec son
`ECART_MINIMAL_EN_MAD`. Reecrire la machinerie de dispersion du depot serait mesurer avec son propre
motif plutot qu avec celui du dispositif.

Usage : mesure-le-cout-du-geste.py --variantes nom=chemin[,nom=chemin...] [--passes 3] [--cible X]
        mesure-le-cout-du-geste.py --auto-test
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import pathlib
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

RACINE = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import TESTS, cas_d_auto_test, sort_si_contrat_demande

# ⟨la dispersion se lit chez le dispositif qui la porte deja⟩
_CHEMIN_PORTAIL = RACINE / ".github" / "scripts" / "mesure_duree_portail.py"
_spec = importlib.util.spec_from_file_location("_portail_5732", _CHEMIN_PORTAIL)
_portail = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_portail)
mediane = _portail.mediane
dispersion_robuste = _portail.dispersion_robuste
ECART_MINIMAL_EN_MAD = _portail.ECART_MINIMAL_EN_MAD

# Trois, parce qu une mediane de deux valeurs n a pas de dispersion (ADR 5628).
PASSES_MINIMALES = 3

# ⟨deux cadences, et les confondre fait mesurer l instrument⟩ On interroge la VIE du processus
# finement, et `/proc` rarement : lire `/proc` toutes les quarts de seconde couterait plus que la
# mesure, et dormir dix secondes entre deux lectures de l horloge quantifie la duree a dix secondes.
PAS_DE_SONDAGE_S = 0.25
INTERVALLE_DE_SURVEILLANCE_S = 10.0

# ⟨une passe polluee se rejoue, elle n abat pas la serie⟩ Trois, comme le `insiste()` de
# `mesure_duree_portail.py` contre les hoquets de la forge (ADR 2748). Le cout d une JVM de pair
# tombe de cinquante minutes a six, sans qu aucune passe polluee entre dans le resultat.
TENTATIVES_PAR_PASSE = 3

# Un arbre qui n existe pas : les portes d auto-test ne touchent aucun disque.
NULLE_PART = pathlib.Path("/nulle/part")

# Les deux moments ou une charge peut etre vue, et le « quand » les distingue.
AVANT_LE_DEPART = "avant le depart"
PENDANT_LA_PASSE = "pendant la passe"

# Le fichier que les variantes permutent. Une seule aide porte le geste du pointeur depuis #5731.
# ⟨le corps du corpus s IMPORTE, il ne se recopie pas⟩ `TESTS` vient de `_commun`, comme l ADR 4586
# l exige : un garde qui reecrit le chemin d un arbre peut cesser d en lire un sans que rien ne s en
# plaigne. C est la regle « mesurer avec le motif du dispositif » rendue executable, et la porte m a
# pris en defaut dessus.
GESTE = TESTS / "fr/univ_amu/iut/recette/GesteVisible.java"

# ⟨une JVM de TRAVAIL, pas une JVM⟩ Exiger l interprete NE SUFFIT PAS : les serveurs de langage de
# l editeur tournent en permanence et ne sont la charge de personne. Ces marqueurs disent qu une JVM
# BATIT, et le repertoire de travail dit ensuite a quel arbre elle appartient.
MARQUEURS_DE_BATI = ("surefire", "org.apache.maven", "plexus-classworlds", "maven-wrapper")


def juge_une_jvm(
    pid: str, executable: str | None, ligne: str, repertoire: str | None, mon_arbre: pathlib.Path
) -> str | None:
    """LA decision, en UN seul endroit : ce processus est-il une JVM de bati etrangere ?

    Rend le libelle du suspect, ou `None`. Elle est ecrite ici et nulle part ailleurs - ni le
    detecteur ni sa porte d auto-test ne la recopient. Une decision ecrite a DEUX endroits se mute
    sur l un et survit sur l autre, ce que le lot #5757 a paye.

    **« Est-ce une JVM » se lit a l EXECUTABLE, pas au texte de la ligne de commande.** La premiere
    version cherchait la chaine `/bin/java` dans `cmdline` : un script dont la ligne de commande
    PARLE de java s y reconnaissait lui-meme. Une session pair l a vu en jouant mon propre controle,
    qui s est trouve dans sa propre sortie. C est le defaut que le lot #5773 venait de corriger
    ailleurs - lire un NOM au lieu d une PROPRIETE - et je l avais reproduit ici le meme jour.
    """
    if executable is None or pathlib.Path(executable).name != "java":
        return None
    if not any(marqueur in ligne for marqueur in MARQUEURS_DE_BATI):
        return None
    if repertoire is None:
        return f"pid {pid} : JVM de bati, repertoire illisible"
    ou = pathlib.Path(repertoire)
    if ou == mon_arbre or mon_arbre in ou.parents:
        return None
    return f"pid {pid} : JVM de bati dans {repertoire}"


def jvm_etrangeres(mon_arbre: pathlib.Path) -> tuple[list[str], str | None]:
    """Les JVM de travail qui ne sont pas a moi, et la raison de ne pas avoir su.

    Rend `(liste, None)` quand la lecture a abouti - liste vide signifiant « le poste est seul » -
    et `([], raison)` quand elle n a pas abouti. Les deux ne se confondent pas : la seconde ne dit
    rien de la charge, elle dit que je n ai pas regarde.

    On lit `/proc` et jamais `pgrep -f` : le motif cherche apparait dans la propre ligne de commande
    du chercheur, qui se compte alors lui-meme et refuse toujours.
    """
    proc = pathlib.Path("/proc")
    if not proc.is_dir():
        return [], "`/proc` est absent, je ne peux pas savoir si une JVM etrangere tourne"
    moi = os.getpid()
    lignes: dict[str, str] = {}
    repertoires: dict[str, str] = {}
    executables: dict[str, str] = {}
    for entree in proc.iterdir():
        if not entree.name.isdigit() or int(entree.name) == moi:
            continue
        try:
            lignes[entree.name] = (
                (entree / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
            )
        except OSError:
            continue
        for champ, ou in (("exe", executables), ("cwd", repertoires)):
            try:
                ou[entree.name] = str(pathlib.Path(os.readlink(entree / champ)).resolve())
            except OSError:
                pass
    if not lignes:
        return [], "aucune ligne de commande lisible dans `/proc`, je n ai rien pu examiner"
    return jvm_etrangeres_sur(lignes, executables, repertoires, mon_arbre)


def jvm_etrangeres_sur(
    lignes: dict[str, str],
    executables: dict[str, str],
    repertoires: dict[str, str],
    mon_arbre: pathlib.Path,
) -> tuple[list[str], str | None]:
    """La meme decision, sur un `/proc` fourni : c est la PORTE de l auto-test.

    Elle n ajoute aucune regle, elle ne fait que boucler : tout le jugement vit dans
    `juge_une_jvm`, que le detecteur reel appelle par ce meme chemin.
    """
    if not lignes:
        return [], "aucune ligne de commande lisible dans `/proc`, je n ai rien pu examiner"
    suspects = [
        libelle
        for pid, ligne in sorted(lignes.items())
        if (
            libelle := juge_une_jvm(
                pid, executables.get(pid), ligne, repertoires.get(pid), mon_arbre
            )
        )
        is not None
    ]
    return suspects, None


def comptes_surefire(dossier: pathlib.Path) -> dict[str, int | float]:
    """Les comptes de la suite, lus par un PARSEUR et jamais par un motif.

    L ordre des attributs de `<testsuite>` est `version / name / time / tests / errors / skipped /
    failures / flakes`, et un motif litteral qui en suppose un autre rend vide A COUP SUR - un vide
    qui se lit « aucun echec ». `flakes` compte les cas ayant rougi puis passe au rejeu : un
    `failures=0 flakes=2` est vert et dit quand meme que deux cas sont instables.
    """
    total = {"tests": 0, "errors": 0, "skipped": 0, "failures": 0, "flakes": 0, "temps": 0.0}
    for rapport in sorted(dossier.glob("TEST-*.xml")):
        try:
            suite = ET.parse(rapport).getroot()
        except ET.ParseError:
            total["errors"] += 1
            continue
        for cle, valeur in comptes_d_une_suite(suite).items():
            total[cle] += valeur
    return total


def comptes_d_une_suite(suite: ET.Element) -> dict[str, int | float]:
    """Les comptes d UN element `<testsuite>`, par ses attributs nommes et jamais par leur ordre.

    Ecrite ici et nulle part ailleurs : l auto-test l atteint par `comptes_surefire_sur`, qui ne fait
    que lui donner un rapport construit a la main.
    """
    total: dict[str, int | float] = {
        cle: int(suite.get(cle, 0) or 0)
        for cle in ("tests", "errors", "skipped", "failures", "flakes")
    }
    total["temps"] = float(suite.get("time", 0) or 0)
    return total


def une_passe(
    arbre: pathlib.Path,
    cible: str | None = None,
    lanceur=subprocess.Popen,
    dors=time.sleep,
    sonde=jvm_etrangeres,
    montre=time.monotonic,
    intervalle: float = INTERVALLE_DE_SURVEILLANCE_S,
    comptes_de=comptes_surefire,
) -> dict:
    """Une passe de suite, chronometree a l horloge murale, sous surveillance de charge.

    Les rapports d avant sont effaces : un `clean` enchaine ou un rapport rance font lire « 0 test
    execute » comme un resultat.

    **Le pas de sondage et la cadence de surveillance sont DEUX choses**, et les confondre fait
    mesurer l instrument au lieu de la suite. La premiere version dormait dix secondes a chaque tour
    de boucle : les neuf durees de l essai sont sorties a 30,1 / 10,0 / 10,0 / 30,0 / 10,0 / 10,0 /
    30,1 / 10,0 / 20,0 s, tous multiples de la cadence. Le chiffre etait plausible et ne mesurait
    que le nombre de sommeils. On interroge donc la vie du processus toutes les `PAS_DE_SONDAGE_S`,
    et `/proc` seulement toutes les `intervalle` secondes.
    """
    rapports = arbre / "target" / "surefire-reports"
    if rapports.is_dir():
        shutil.rmtree(rapports)
    commande = ["./mvnw", "-B", "-o", "test", "-Dglass.platform=Headless"]
    if cible:
        commande.append(f"-Dtest={cible}")

    intruses, empeche = sonde(arbre)
    if empeche:
        return {"verdict": "poste-illisible", "raison": empeche}
    if intruses:
        return {"verdict": "charge-subie", "intruses": intruses, "quand": AVANT_LE_DEPART}

    depart = montre()
    processus = lanceur(commande, cwd=arbre, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    vus: list[str] = []
    prochaine = depart + intervalle
    while processus.poll() is None:
        dors(PAS_DE_SONDAGE_S)
        if montre() < prochaine:
            continue
        prochaine = montre() + intervalle
        pendant, souci = sonde(arbre)
        if souci:
            return {"verdict": "poste-illisible", "raison": souci}
        vus.extend(p for p in pendant if p not in vus)
    horloge = montre() - depart

    if vus:
        return {"verdict": "charge-subie", "intruses": vus, "quand": PENDANT_LA_PASSE}
    comptes = comptes_de(rapports)
    rendu = {
        "verdict": "mesuree",
        "horloge": horloge,
        "code": processus.returncode,
        **comptes,
    }
    # ⟨une passe ROUGE n a pas fait le MEME TRAVAIL⟩ Troisieme defaut de mesure de cet instrument,
    # et le plus couteux : il comptait la duree d une passe dont la suite avait echoue. Un cas qui
    # leve abrege son chemin, un qui expire l allonge - donc deux passes aux comptes differents ne
    # mesurent pas la meme chose, et leur ecart ne veut rien dire. La serie du 2026-10-03 a rendu
    # neuf passes portant de 0 a 5 erreurs, y compris sur la variante de reference, et un verdict
    # « l ecart tient » calcule sur elles.
    if comptes["failures"] or comptes["errors"]:
        return {**rendu, "verdict": "suite-rouge"}
    return rendu


def assez_de_passes(passes: int) -> bool:
    """Trois passes au moins, sans quoi la dispersion n existe pas (ADR 5628).

    Deux passes donnent un ecart et AUCUNE dispersion : c est exactement l etat de la paire d origine
    de #5732, dont le `+10,1 %` ne peut etre ni cru ni refute. Le plancher est donc un refus et non
    un avertissement.
    """
    return passes >= PASSES_MINIMALES


def conclut(reference: list[float], autre: list[float]) -> tuple[str, float, float]:
    """L ecart des medianes tient-il hors de la dispersion ?

    Rend `(verdict, ecart_en_pourcent, ecart_en_mad)`. Le verdict est « tient » quand l ecart depasse
    `ECART_MINIMAL_EN_MAD` fois la dispersion, « indiscernable » sinon - et c est un RESULTAT, pas un
    echec de la mesure.
    """
    a, b = mediane(reference), mediane(autre)
    pire = max(dispersion_robuste(reference), dispersion_robuste(autre))
    pourcent = (b - a) / a * 100 if a else 0.0
    if pire == 0:
        return ("tient" if b != a else "identique"), pourcent, float("inf") if b != a else 0.0
    en_mad = abs(b - a) / pire
    return ("tient" if en_mad >= ECART_MINIMAL_EN_MAD else "indiscernable"), pourcent, en_mad


def paie_la_compilation(arbre: pathlib.Path) -> float:
    """Compile la variante AVANT la premiere passe comptee, et rend ce que ca a coute.

    Mesure de l essai court du 2026-10-03 : la premiere passe suivant une permutation coutait 20 a
    25 s la ou les suivantes en coutaient 8. L ecart est la **recompilation** du fichier permute, et
    il est systematique - une aberrante par variante, toujours la premiere.

    Trois passes dont une aberrante systematique donnent un ecart absolu median **minuscule**, parce
    qu il se calcule sur les deux passes voisines : le dispositif annonce alors « l ecart tient »
    avec une dispersion qu il a sous-estimee. Payer la compilation separement coute **vingt secondes
    par variante** la ou une passe de chauffe en couterait six minutes.
    """
    depart = time.monotonic()
    subprocess.run(
        ["./mvnw", "-B", "-o", "-q", "test-compile"],
        cwd=arbre,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return time.monotonic() - depart


def mesure_une_variante(
    arbre: pathlib.Path,
    passes: int,
    cible: str | None = None,
    compile_d_abord=paie_la_compilation,
    passe=une_passe,
    tentatives: int = TENTATIVES_PAR_PASSE,
) -> tuple[list[float], dict | None]:
    """La compilation D ABORD, puis les passes comptees. Rend `(durees, refus_eventuel)`.

    L ordre est la propriete : une passe comptee qui paierait la compilation serait une aberrante
    systematique, et c est ce qui a sous-estime la dispersion a l essai du 2026-10-03.

    **Une passe polluee se REJOUE, elle n abat pas la serie.** Deux tentatives de #5732 ont ete
    perdues en une heure parce qu une JVM de pair est apparue, et sur un poste que quatre sessions
    partagent une fenetre exclusive de cinquante minutes n est pas tenable. Faire dependre une heure
    de machine d une promesse humaine est fragile par construction. On rejoue donc, comme
    `mesure_duree_portail.py:insiste()` le fait contre les hoquets de la forge en citant l ADR 2748,
    et on n abandonne qu apres `tentatives` refus de suite.

    **L invariant preserve est le seul qui compte** : aucune passe polluee n entre dans le resultat.
    Rejouer ne tolere pas la charge, cela cesse seulement de la payer au prix de la serie entiere.
    """
    mis = compile_d_abord(arbre)
    print(f"    compilation payee hors mesure : {mis:.1f} s")
    durees: list[float] = []
    for tour in range(1, passes + 1):
        for essai in range(1, tentatives + 1):
            rendu = passe(arbre, cible)
            if rendu["verdict"] == "mesuree":
                break
            if rendu["verdict"] == "poste-illisible":
                return durees, rendu
            if rendu["verdict"] == "suite-rouge":
                print(
                    f"    passe {tour}, tentative {essai}/{tentatives} REJETEE :"
                    f" la suite a ROUGI (failures={rendu['failures']} errors={rendu['errors']}),"
                    " donc cette passe n a pas fait le meme travail"
                )
                continue
            print(
                f"    passe {tour}, tentative {essai}/{tentatives} REJETEE :"
                f" charge subie {rendu['quand']}"
            )
            for intrus in rendu["intruses"]:
                print(f"        {intrus}")
        if rendu["verdict"] != "mesuree":
            return durees, rendu
        durees.append(rendu["horloge"])
        print(
            f"    passe {tour} : {rendu['horloge']:.1f} s  "
            f"(tests={rendu['tests']} failures={rendu['failures']} "
            f"errors={rendu['errors']} flakes={rendu['flakes']} code={rendu['code']})"
        )
    return durees, None


def mesurer(variantes: dict[str, pathlib.Path], passes: int, cible: str | None) -> int:
    """Permute chaque variante sur le MEME arbre, joue `passes` passes, et rend le bilan."""
    arbre = RACINE
    sain = (arbre / GESTE).read_text(encoding="utf-8")
    releve: dict[str, list[float]] = {}
    try:
        for nom, source in variantes.items():
            (arbre / GESTE).write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
            print(f"\n  variante « {nom} » ({source.name})")
            durees, refus = mesure_une_variante(arbre, passes, cible)
            if refus is not None:
                if refus["verdict"] == "poste-illisible":
                    print(f"    JE N AI PAS CONCLU : {refus['raison']}")
                    return 2
                if refus["verdict"] == "suite-rouge":
                    print(
                        "    REFUS : la suite a rougi a chaque tentative"
                        f" (failures={refus['failures']} errors={refus['errors']})."
                    )
                    print(
                        "    Une duree ne se compare pas entre des passes qui n ont pas fait le"
                        " meme travail. Il faut une suite verte avant de mesurer."
                    )
                    return 1
                print(f"    REFUS : charge subie {refus['quand']}")
                for intrus in refus["intruses"]:
                    print(f"      {intrus}")
                return 1
            releve[nom] = durees
    finally:
        (arbre / GESTE).write_text(sain, encoding="utf-8")

    noms = list(releve)
    reference = noms[0]
    print(f"\n  BILAN, {passes} passe(s) par variante, reference « {reference} »")
    for nom in noms:
        d = releve[nom]
        print(
            f"    {nom:12} mediane={mediane(d):7.1f} s   ecart absolu median={dispersion_robuste(d):5.1f} s"
        )
    for nom in noms[1:]:
        verdict, pourcent, en_mad = conclut(releve[reference], releve[nom])
        print(f"    {reference} -> {nom:12} {pourcent:+6.1f} %   {en_mad:7.3f} MAD   {verdict}")
    return 0


def _auto_test() -> int:
    """Les cas sont DIFFERES : une expression passee en valeur est evaluee chez l appelant, donc son
    echec sort en trace de pile et le libelle ne sort pas (cliquet des harnais muets, ADR 5570)."""
    verifie, echecs = cas_d_auto_test()

    verifie("la mediane vient du depot", lambda: mediane([1.0, 3.0, 2.0]), 2.0)

    # ⟨le plancher de passes est un REFUS, pas un avertissement⟩ Deux passes donnent un ecart et
    # AUCUNE dispersion : c est l etat de la paire d origine de #5732.
    verifie("deux passes ne suffisent pas", lambda: assez_de_passes(2), False)
    verifie("trois passes suffisent", lambda: assez_de_passes(3), True)
    # ⟨une donnee ASYMETRIQUE, sinon le cas ne discrimine pas⟩ Sur `[1, 1, 1]` l ecart absolu median
    # et l ecart absolu MOYEN valent tous deux zero, donc le cas passerait sur une reecriture
    # fautive. Ici l ecart absolu median vaut 1 - median(1, 0, 8) - la ou la moyenne vaudrait 3.
    verifie(
        "l ecart ABSOLU MEDIAN, et non l ecart moyen",
        lambda: dispersion_robuste([1.0, 2.0, 10.0]),
        1.0,
    )

    verifie(
        "un ecart sous la dispersion est INDISCERNABLE, et c est un resultat",
        lambda: conclut([100.0, 110.0, 90.0], [101.0, 111.0, 91.0])[0],
        "indiscernable",
    )
    verifie(
        "un ecart au-dela de la dispersion TIENT",
        lambda: conclut([100.0, 101.0, 99.0], [200.0, 201.0, 199.0])[0],
        "tient",
    )

    # ⟨les DEUX causes de refus ne se confondent pas⟩
    def muette(_arbre):
        return [], None

    def chargee(_arbre):
        return ["pid 1 : JVM de bati dans /ailleurs"], None

    def illisible(_arbre):
        return [], "`/proc` est absent"

    class FauxProcessus:
        """Vit `tours_vivants` tours de boucle, puis rend la main."""

        returncode = 0
        tours_vivants = 1

        def __init__(self, *_a, **_k):
            self.tours = 0

        def poll(self):
            self.tours += 1
            return None if self.tours <= self.tours_vivants else 0

    class Horloge:
        """Une montre que les SOMMEILS font avancer, et rien d autre.

        C est ce qui permet d eprouver que la duree rendue suit le pas de sondage et non la cadence
        de surveillance - la confusion qui a fait sortir neuf durees multiples de dix secondes.
        """

        def __init__(self):
            self.t = 1000.0

        def montre(self):
            return self.t

        def dors(self, secondes):
            self.t += secondes

    def passe_avec(sonde, tours=1, intervalle=INTERVALLE_DE_SURVEILLANCE_S):
        class Processus(FauxProcessus):
            tours_vivants = tours

        pendule = Horloge()
        return une_passe(
            NULLE_PART,
            lanceur=Processus,
            dors=pendule.dors,
            sonde=sonde,
            montre=pendule.montre,
            intervalle=intervalle,
        )

    verifie(
        "une JVM etrangere AVANT le depart refuse la mesure",
        lambda: passe_avec(chargee)["verdict"],
        "charge-subie",
    )
    # ⟨refuser TROP TARD refuse quand meme⟩ Sans ce cas, retirer le controle d avant-depart SURVIT :
    # la boucle de surveillance rattrape l intruse et le verdict reste « charge-subie ». Mais la
    # passe a demarre, donc six minutes de machine sont parties. Le « quand » est ce qui distingue
    # un refus d un gaspillage.
    verifie(
        "et elle refuse AVANT d avoir lance quoi que ce soit",
        lambda: passe_avec(chargee)["quand"],
        AVANT_LE_DEPART,
    )
    verifie(
        "un poste illisible rend « poste-illisible », et NON « charge-subie »",
        lambda: passe_avec(illisible)["verdict"],
        "poste-illisible",
    )
    verifie(
        "le poste seul laisse la passe se mesurer",
        lambda: passe_avec(muette)["verdict"],
        "mesuree",
    )

    # ⟨la duree suit le PAS DE SONDAGE, pas la cadence de surveillance⟩ Le cas qui manquait quand
    # l essai a rendu neuf durees multiples de dix secondes. Quatre tours de boucle a 0,25 s font
    # une seconde : si la duree valait la cadence, elle vaudrait dix.
    verifie(
        "quatre tours de sondage font une seconde, et non une cadence de surveillance",
        lambda: round(passe_avec(muette, tours=4)["horloge"], 3),
        round(4 * PAS_DE_SONDAGE_S, 3),
    )

    # ⟨la detection PENDANT la passe n avait aucun cas⟩ Une intruse qui apparait apres le depart doit
    # etre vue, et le « quand » doit la distinguer de celle vue avant.
    def propre_puis_chargee():
        tours = {"n": 0}

        def sonde(_arbre):
            tours["n"] += 1
            if tours["n"] == 1:
                return [], None
            return ["pid 7 : JVM de bati dans /ailleurs"], None

        return sonde

    verifie(
        "une JVM qui apparait PENDANT la passe est vue",
        lambda: passe_avec(propre_puis_chargee(), tours=3, intervalle=0)["verdict"],
        "charge-subie",
    )
    verifie(
        "et le « quand » la distingue de celle vue avant le depart",
        lambda: passe_avec(propre_puis_chargee(), tours=3, intervalle=0)["quand"],
        PENDANT_LA_PASSE,
    )

    # ⟨la compilation se paie UNE fois, AVANT les passes comptees⟩ A l essai du 2026-10-03 la
    # premiere passe de chaque variante coutait 20 a 25 s contre 8 : la recompilation du fichier
    # permute. Une aberrante systematique par variante, qui sous-estime l ecart absolu median
    # puisqu il se calcule alors sur les deux passes voisines.
    def variante_avec_journal(passes):
        ordre = []

        def faux_compile(_arbre):
            ordre.append("compile")
            return 1.0

        def fausse_passe(_arbre, _cible=None):
            ordre.append("passe")
            return {
                "verdict": "mesuree",
                "horloge": 5.0,
                "code": 0,
                "tests": 1,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "flakes": 0,
                "temps": 1.0,
            }

        durees, refus = mesure_une_variante(NULLE_PART, passes, None, faux_compile, fausse_passe)
        return ordre, durees, refus

    verifie(
        "la compilation est payee UNE fois, et AVANT toute passe comptee",
        lambda: variante_avec_journal(3)[0],
        ["compile", "passe", "passe", "passe"],
    )
    verifie(
        "les trois passes comptees sont rendues",
        lambda: variante_avec_journal(3)[1],
        [5.0, 5.0, 5.0],
    )

    # Un refus pendant la serie arrete la variante et REMONTE, il ne se tait pas.
    def variante_qui_refuse():
        def refuse(_arbre, _cible=None):
            return {"verdict": "charge-subie", "intruses": ["pid 7"], "quand": PENDANT_LA_PASSE}

        return mesure_une_variante(NULLE_PART, 3, None, lambda _a: 0.0, refuse)

    verifie(
        "un refus pendant la serie REMONTE au lieu d etre tu",
        lambda: variante_qui_refuse()[1]["verdict"],
        "charge-subie",
    )

    # ⟨une passe polluee se REJOUE⟩ Deux tentatives de #5732 perdues en une heure parce qu une JVM de
    # pair est apparue. Le cas discriminant n est pas « il refuse » mais « il rejoue PUIS compte » :
    # un dispositif qui abandonne et un dispositif qui rejoue refusent tous deux sous charge pure.
    def variante_polluee_puis_propre(refus_de_suite):
        etat = {"n": 0}

        def passe(_arbre, _cible=None):
            etat["n"] += 1
            if etat["n"] <= refus_de_suite:
                return {
                    "verdict": "charge-subie",
                    "intruses": ["pid 7 : JVM de bati dans /ailleurs"],
                    "quand": PENDANT_LA_PASSE,
                }
            return {
                "verdict": "mesuree",
                "horloge": 42.0,
                "code": 0,
                "tests": 1,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "flakes": 0,
                "temps": 1.0,
            }

        return mesure_une_variante(NULLE_PART, 1, None, lambda _a: 0.0, passe, tentatives=3)

    verifie(
        "une passe polluee UNE fois est rejouee, puis COMPTEE",
        lambda: variante_polluee_puis_propre(1),
        ([42.0], None),
    )
    verifie(
        "deux refus de suite n empechent pas la troisieme tentative",
        lambda: variante_polluee_puis_propre(2),
        ([42.0], None),
    )
    verifie(
        "mais TROIS refus de suite abandonnent, sans rien compter",
        lambda: variante_polluee_puis_propre(3)[1]["verdict"],
        "charge-subie",
    )
    verifie(
        "et l abandon ne compte AUCUNE duree",
        lambda: variante_polluee_puis_propre(3)[0],
        [],
    )

    # ⟨une passe ROUGE n a pas fait le meme travail⟩ Le troisieme defaut de mesure, trouve sur la
    # serie reelle : neuf passes portant de 0 a 5 erreurs, et un verdict calcule sur elles.
    def passe_rouge(failures, errors):
        def passe(_arbre, _cible=None):
            comptes = {
                "horloge": 300.0,
                "code": 1 if (failures or errors) else 0,
                "tests": 5659,
                "failures": failures,
                "errors": errors,
                "skipped": 0,
                "flakes": 0,
                "temps": 290.0,
            }
            if failures or errors:
                return {"verdict": "suite-rouge", **comptes}
            return {"verdict": "mesuree", **comptes}

        return mesure_une_variante(NULLE_PART, 1, None, lambda _a: 0.0, passe, tentatives=2)

    verifie(
        "une passe dont la suite a ROUGI n est pas comptee",
        lambda: passe_rouge(1, 0)[0],
        [],
    )
    verifie(
        "et son refus dit « suite-rouge », non « charge-subie »",
        lambda: passe_rouge(1, 0)[1]["verdict"],
        "suite-rouge",
    )
    verifie(
        "une ERREUR seule, sans echec, rougit aussi",
        lambda: passe_rouge(0, 2)[1]["verdict"],
        "suite-rouge",
    )
    verifie(
        "une suite verte reste comptee",
        lambda: passe_rouge(0, 0)[0],
        [300.0],
    )

    # ⟨la DECISION, et non la reaction du rejeu⟩ Les quatre cas ci-dessus eprouvent ce que
    # `mesure_une_variante` FAIT d un verdict qu on lui tend. Retirer la decision de `une_passe`
    # SURVIVAIT donc : j avais eprouve le consommateur et pas le producteur.
    def passe_dont_la_suite_rend(failures, errors):
        return une_passe(
            NULLE_PART,
            lanceur=FauxProcessus,
            dors=lambda _s: None,
            sonde=muette,
            comptes_de=lambda _d: {
                "tests": 5659,
                "failures": failures,
                "errors": errors,
                "skipped": 0,
                "flakes": 0,
                "temps": 1.0,
            },
        )

    verifie(
        "`une_passe` CLASSE une suite a echecs en « suite-rouge »",
        lambda: passe_dont_la_suite_rend(1, 0)["verdict"],
        "suite-rouge",
    )
    verifie(
        "et une suite a ERREURS seules aussi - pas seulement les echecs",
        lambda: passe_dont_la_suite_rend(0, 3)["verdict"],
        "suite-rouge",
    )
    verifie(
        "une suite verte est classee « mesuree »",
        lambda: passe_dont_la_suite_rend(0, 0)["verdict"],
        "mesuree",
    )

    # ⟨le cas qui DISCRIMINE : un refus trop large passe pour de la prudence⟩ « il refuse quand une
    # JVM tourne » est facile a faire passer, y compris par un detecteur qui refuse toujours. Ce cas
    # est l autre moitie, et sans lui le dispositif ne prouve pas qu il distingue.
    mien = pathlib.Path("/home/moi/mon-arbre")
    JAVA = "/usr/lib/jvm/jdk/bin/java"
    bati = "java -classpath surefire/booter.jar Forked"

    verifie(
        "une JVM SANS marqueur de bati - un serveur de langage - ne refuse PAS",
        lambda: jvm_etrangeres_sur(
            {"41": "java -jar lsp-server.jar", "42": "bash"},
            {"41": JAVA, "42": "/bin/bash"},
            {"41": "/home/ailleurs"},
            mien,
        ),
        ([], None),
    )
    verifie(
        "une JVM de bati AILLEURS est vue",
        lambda: jvm_etrangeres_sur(
            {"41": bati}, {"41": JAVA}, {"41": "/home/un/autre/arbre"}, mien
        )[0],
        ["pid 41 : JVM de bati dans /home/un/autre/arbre"],
    )
    verifie(
        "une JVM de bati DANS MON arbre n est pas une etrangere",
        lambda: jvm_etrangeres_sur({"41": bati}, {"41": JAVA}, {"41": str(mien / "target")}, mien),
        ([], None),
    )
    verifie(
        "une JVM de bati dont le repertoire est ILLISIBLE est signalee, pas tue",
        lambda: jvm_etrangeres_sur({"41": bati}, {"41": JAVA}, {}, mien)[0],
        ["pid 41 : JVM de bati, repertoire illisible"],
    )
    verifie(
        "un `/proc` vide rend « je n ai pas pu savoir », et NON « le poste est seul »",
        lambda: jvm_etrangeres_sur({}, {}, {}, mien)[1] is not None,
        True,
    )

    # ⟨« est-ce une JVM » se lit a l EXECUTABLE, pas au texte⟩ Le cas qui discrimine, trouve en vrai
    # par une session pair : mon propre controle, lance depuis un shell, portait « /bin/java » et
    # « surefire » dans sa PROPRE ligne de commande et s est compte lui-meme. L executable, lui, est
    # un shell. Sans ce cas, lire le nom au lieu de la propriete SURVIT.
    verifie(
        "un script qui PARLE de java et de surefire n est pas une JVM",
        lambda: jvm_etrangeres_sur(
            {"41": "/bin/sh -c 'pgrep -f /bin/java | grep surefire'"},
            {"41": "/bin/sh"},
            {"41": "/home/un/autre/arbre"},
            mien,
        ),
        ([], None),
    )
    verifie(
        "une JVM dont l EXECUTABLE est illisible n est pas comptee",
        lambda: jvm_etrangeres_sur({"41": bati}, {}, {"41": "/home/ailleurs"}, mien),
        ([], None),
    )

    verifie(
        "les comptes surefire se lisent quel que soit l ORDRE des attributs",
        lambda: comptes_surefire_sur(
            '<testsuite version="3.0" name="X" time="1.5" tests="9" errors="0"'
            ' skipped="1" failures="2" flakes="3"/>'
        ),
        {"tests": 9, "errors": 0, "skipped": 1, "failures": 2, "flakes": 3, "temps": 1.5},
    )

    return 1 if echecs() else 0


def comptes_surefire_sur(xml: str) -> dict[str, int | float]:
    """Les memes comptes, sur un rapport fourni : la porte d auto-test de `comptes_d_une_suite`."""
    return comptes_d_une_suite(ET.fromstring(xml))


def main() -> int:
    analyseur = argparse.ArgumentParser(add_help=True)
    analyseur.add_argument("--variantes", required=True, help="nom=chemin,nom=chemin...")
    analyseur.add_argument("--passes", type=int, default=3)
    analyseur.add_argument("--cible", default=None, help="restreindre a une classe, pour un essai")
    arguments = analyseur.parse_args()
    variantes = {}
    for morceau in arguments.variantes.split(","):
        nom, _, chemin = morceau.partition("=")
        source = pathlib.Path(chemin)
        if not source.is_file():
            print(f"REFUS : la variante « {nom} » designe {chemin}, qui n existe pas")
            return 2
        variantes[nom] = source
    if not assez_de_passes(arguments.passes):
        print(f"REFUS : {arguments.passes} passe(s) ne donnent pas de dispersion (ADR 5628).")
        return 2
    return mesurer(variantes, arguments.passes, arguments.cible)


CONTRAT = {
    "geste": "la duree de la suite pour plusieurs variantes d un geste, avec sa dispersion",
    "population": "les variantes qu on lui donne, permutees sur UN seul arbre : il ne compare jamais "
    "deux arbres, parce que la paire d origine de #5732 opposait un vieux `main` a un neuf et que "
    "six lots avaient touche des fichiers de test entre-temps. Il ne mesure que ce qu il a joue "
    "lui-meme, et refuse de conclure sous une JVM de bati etrangere",
    "dispositif": "rapport",
    "seuil": "aucun : il rend des medianes et leur dispersion, et dit si l ecart tient",
    "temoin": "scripts/methode/mesure-le-cout-du-geste.py --auto-test",
    "decision": "ADR 5628",
    # Il ne LIT aucun fichier du depot pour juger : sa matiere est la duree de ses propres passes.
    # Ses chemins sont donc ceux du fichier qu il permute et de la machinerie qu il importe.
    "chemins": """
src/test/java/fr/univ_amu/iut/recette/GesteVisible.java
.github/scripts/mesure_duree_portail.py
""",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    sys.exit(_auto_test() if "--auto-test" in sys.argv else main())
