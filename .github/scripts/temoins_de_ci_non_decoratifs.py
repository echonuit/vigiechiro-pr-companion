#!/usr/bin/env python3
"""Témoin décoratif : l'auto-test d'un garde de CI reste-t-il vert quand le garde cesse de détecter ?

Le pendant, pour `.github/`, de `scripts/adr/verifie_temoins_non_decoratifs.py` et de
`scripts/methode/temoins-de-methode-non-decoratifs.py`. Il existe parce que ces deux-là ne
regardaient PAS cette population, alors que #5215 avait fermé #4865 en affirmant l'inverse :
« converti, un garde entre gratuitement dans un banc qui existe ». Il n'y entrait pas.

## Le verdict a TROIS valeurs, et c'est la mesure qui l'a imposé

La neutralisation remplace chaque fonction du module par un `lambda` rendant `[]`. Sur les gardes de
methode, qui rendent des listes de suspects, cela retire la detection proprement. Sur les gardes de
CI, dont les fonctions rendent des `tuple`, des `int` ou des chemins, cela fait souvent PLANTER
l auto-test avant sa premiere assertion.

Mesure du 2026-09-05 sur les 43 gardes de la population :

| ce qu on observe                | combien | ce que cela prouve                        |
|---------------------------------|--------:|-------------------------------------------|
| rouge, sans trace Python        |      34 | l auto-test a VU la detection disparaitre |
| rouge, AVEC une trace Python    |       9 | rien : le garde est mort avant d assertir |
| vert                            |       0 | l auto-test est decoratif                 |

Compter les neuf plantages comme des reussites annoncerait 43 gardes eprouves quand il y en a 34.
C est le meme defaut que celui de l ADR 4918 : un cas rouge pour la mauvaise raison ne prouve rien.
Et c est pourquoi le compte des NON CONCLUANTS sort separement, plutot que d etre range avec les
verts ou avec les rouges (ADR 2748).

Les neuf se rangent en trois familles, aucune n etant un defaut du garde : un depaquetage
`a, b = f()` sur un `[]`, une `list` employee comme cle de dictionnaire, et une fonction dont le
travail etait de CREER la fixture que l auto-test ouvre ensuite. Aucune valeur de repli ne les couvre
toutes les trois, et en chercher une reviendrait a deviner ce que chaque fonction rend au lieu de le
demander (ADR 5102).

## Ce qui fait rougir ce garde

Un garde dont l auto-test reste VERT sous mutation. Un non concluant ne fait pas rougir par
lui-meme, il se compte et se nomme - sinon ce garde refuserait sur ce qu il n a pas su lire.

Et, depuis #5497, un ECART entre ce qui plante et la table `PLANTENT_SOUS_MUTATION` : un garde qui
plante sans y etre nomme, ou une entree qui ne plante plus. C est un constat sur la table, rendu en
1 sans marque de refus (ADR 5743, ADR 5774).

## La population est DERIVEE, et de TOUS les ateliers

`lint.yml` seul en nomme 40 sur 43 : `check_doc_images`, `check_doc_videos` et
`filtrer_bruit_cartes` sont lances par d autres ateliers. Deriver du seul `lint.yml` aurait recree
l angle mort que ce garde corrige, mesure avant d ecrire cette ligne.

Usage : python3 .github/scripts/temoins_de_ci_non_decoratifs.py [--auto-test] [--contrat]
"""

from __future__ import annotations

import ast
import contextlib
import io
import pathlib
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
# Le fonds de `scripts/` porte l assertion d auto-test partagee (#5460) : ce banc la reprend plutot
# que d en recopier une, comme `verifie_butoirs.py` du meme dossier.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "scripts"))

from _commun import MARQUE_CAUSE, MARQUE_GESTE, cas_d_auto_test
from _commun.mutation import neutralisation
from _forge import dispatche_l_option

RACINE = pathlib.Path(__file__).resolve().parents[2]
ATELIERS = RACINE / ".github" / "workflows"
DOSSIERS = (".github/scripts", ".github/assets")
MOI = pathlib.Path(__file__).name

TRACE = "Traceback (most recent call last)"
# Le code par lequel un script de ce depot dit qu il n a PAS PU juger (ADR 5774). Voir `classe`.
CODE_DE_REFUS = 2

# ⟨les gardes qui PLANTENT sous mutation, nommes un par un (#5497)⟩ Un plantage ne fait pas rougir
# ce banc (ADR 5257), et c est juste. Mais rien ne BORNAIT ces gardes : ils s imprimaient sous
# `verdict=ok`, et un garde neuf pouvait les rejoindre sans qu une seule demande rougisse.
#
# **Une liste NOMMEE et non un cliquet**, comme le banc des ADR depuis #5743 et pour la meme raison :
# ce banc se declare `invariant`, et l ADR 5743 borne le residu d un invariant par une liste verifiee
# DANS LES DEUX SENS. Un compte laisserait echanger un plantage repare contre un plantage neuf.
#
# Les cles sont des noms NUS, la forme que `juge` imprime. La valeur de chaque entree est sa
# classification, lue dans la trace du garde mute le 2026-10-06 : huit recoivent une liste la ou ils
# attendaient un autre TYPE, trois ouvrent une FIXTURE que la fonction neutralisee devait creer.
# Aucun n est repare ici : borner n est pas vider (EPIC #5265).
PLANTENT_SOUS_MUTATION: dict[str, str] = {
    "cas_manquants_du_tournage.py": "type sous neutralisation : un deballage de deux valeurs sur un vide",
    "clips_orphelins.py": "type sous neutralisation : un deballage de deux valeurs sur un vide",
    "mesure_minutes_par_pr.py": "type sous neutralisation : un deballage de deux valeurs sur un vide",
    "veille_contrat_api.py": "type sous neutralisation : une chaine attendue, `+` sur une liste",
    "verifie_cloture_consignee.py": "type sous neutralisation : le deballage de la fabrique de forge, mutee",
    "verifie_jeton_vivant.py": "type sous neutralisation : une liste employee comme cle de dictionnaire",
    "verifie_specification_consignee.py": "type sous neutralisation : le deballage de la fabrique de forge, mutee",
    "check_capture_mains.py": "fixture sous neutralisation : le fichier que la fonction mutee devait ecrire",
    "compare_apercus.py": "fixture sous neutralisation : l index que la fonction mutee devait ecrire ; "
    "sans `convert`, refuse de jouer",
    "compare_tournages.py": "fixture sous neutralisation : les planchers que la fonction mutee devait "
    "ecrire ; sans `ffmpeg` ou `compare`, refuse de jouer",
    "filtrer_bruit_cartes.py": "type sous neutralisation : un deballage de deux valeurs sur un vide",
}

# ⟨deux entrees ont DEUX causes, selon l hote (#6030)⟩ `compare_apercus.py` et
# `compare_tournages.py` ouvrent leur auto-test par un prealable d outil. Sur un poste qui porte
# ImageMagick et ffmpeg, l auto-test joue et plante sur sa fixture ; sur le runner du job `temoins`,
# qui ne les porte pas, il refuse de jouer et sort en 2. Les deux sont « non concluant » : cette
# table vaut donc sur tout hote, 11 partout.
#
# Elle ne valait pas partout avant : un refus de jouer se lisait « tient », le runner rendait 9 la
# ou un poste outille rendait 11, et une seconde table, `NE_JOUENT_QU_AVEC`, declarait les outils
# de ces deux entrees pour qu elles ne soient pas dites perimees la ou ils manquaient (#5497). Elle
# bornait un defaut de classification ; le defaut corrige, elle n avait plus rien a borner.

CONDUITE_SUR_LA_TABLE = (
    "Un garde qui plante : le reparer pour qu il ASSERTE au lieu de planter, ou le nommer dans "
    "PLANTENT_SOUS_MUTATION avec sa raison. Une entree qui ne plante plus : la retirer."
)

# Ce qu on insere pour retirer sa detection a un garde, sans toucher a ce qui le decrit.
#
# La fonction d auto-test est EPARGNEE, et c est ce qui rend la mesure honnete : la neutraliser
# ferait rougir le garde trivialement, non parce qu il a cesse de detecter. L exemption se DERIVE
# plutot que de s enumerer, parce qu une liste vieillit au premier garde neuf. Elle se lisait sur le
# nom - toute fonction portant « auto » et « test » - jusqu a #5524, et ce motif epargnait
# `autotestes`, la detection de `verifie_inventaires_ci.py`. Elle se lit desormais sur le graphe
# d appel, dans `_commun/mutation.py`.
#
# La meme que celle des deux autres bancs, et deliberement : ils importent tous trois ce module,
# parce que deux neutralisations differentes rendraient deux mesures qu on ne pourrait plus comparer.


CONTRAT = {
    "garde": ".github/scripts/temoins_de_ci_non_decoratifs.py",
    "geste": "temoin decoratif : l auto-test d un garde de CI reste vert quand la detection est retiree",
    "population": "les gardes de .github/scripts et .github/assets nommes par un atelier, portant --auto-test et un point d entree",
    # Les trois surfaces dont son verdict depend : les deux dossiers qu il mute, et les ateliers
    # d ou il derive sa population. Mesure du 2026-09-29 : 10,36 s. Sans cette ligne, la porte le
    # lancait a CHAQUE diff, ce qui aurait ajoute dix secondes a un changement de javadoc (#5525).
    "chemins": """
.github/scripts/**
.github/assets/**
.github/workflows/**
""",
    # ⟨`invariant` et non `cliquet` (#5498)⟩ Le critere est ecrit dans `dev-docs/ci-cd-release.md` :
    # « c est un invariant, pas un cliquet : il n y a pas de marge a relever, et l echappatoire est
    # une liste d exceptions NOMMEES ». Zero decoratif n a pas de marge. L echappatoire, elle, est
    # `HORS_PORTEE` chez les deux autres bancs ; CE banc n en porte aucune, son corpus se derive des
    # ateliers et rien n en est ecarte par son nom, hors lui-meme. Ses listes nommees sont celles de
    # ses non concluants. Les trois bancs le declarent pareil ; `verifie_contrat_obligatoire.py`,
    # que la meme page decrit ainsi, declare de meme.
    "dispositif": "invariant",
    "seuil": "(sans objet)",
    "temoin": ".github/scripts/temoins_de_ci_non_decoratifs.py --auto-test",
    "decision": "ADR 4490",
}


def ateliers() -> str:
    """Le texte de TOUS les ateliers, concatene. Pas seulement lint.yml : mesure faite."""
    return "".join(f.read_text(encoding="utf-8") for f in sorted(ATELIERS.glob("*.yml")))


def corpus(texte: str | None = None) -> list[pathlib.Path]:
    """Les gardes de CI eprouvables, DERIVES et non enumeres.

    Trois conditions, et chacune ecarte quelque chose de different : etre nomme par un atelier
    ecarte un fichier mort, porter `--auto-test` ecarte un outil qui n en est pas un
    (`construit_appimage`, `porte_sur_le_contrat_de_fichiers`), et porter un point d entree
    repérable donne l endroit ou inserer la neutralisation.
    """
    texte = ateliers() if texte is None else texte
    trouves = []
    for dossier in DOSSIERS:
        for f in sorted((RACINE / dossier).glob("*.py")):
            # ⟨plus d exclusion par le NOM⟩ Le souligne initial ecartait les modules de mecanisme
            # par leur forme, quand `verifie_inventaires_ci` ne les ecartait pas du tout : deux
            # populations tirees du meme dossier, sur une regle qui n etait ecrite nulle part. Les
            # deux partagent desormais `_forge.dispatche_l_option`, qui derive de ce que le fichier
            # FAIT (#5318). Un module de mecanisme ne dispatche rien, donc il sort tout seul.
            if f.name == MOI:
                continue
            source = f.read_text(encoding="utf-8")
            if (
                f.name in texte
                and dispatche_l_option(str(f), source)
                and ligne_du_point_d_entree(source) is not None
            ):
                trouves.append(f)
    return trouves


def ligne_du_point_d_entree(source: str) -> int | None:
    """La ligne du `if __name__ == "__main__":` de MODULE, lue dans l arbre et non par un motif.

    Un motif se trompe des qu un garde porte une CHAINE contenant ce texte, ce qui est le cas de
    tout banc dont l auto-test ecrit de faux gardes. Ce fichier en porte trois : le motif y matchait
    QUATRE fois, `search` prenait la premiere, et la neutralisation s inserait au milieu d un
    litteral - ou elle ne neutralisait rien. Constate sur ce banc meme, qui survivait a sa propre
    mutation en restant vert.

    L arbre ne peut pas se tromper : il ne voit que les `if` de niveau module.
    """
    for noeud in ast.parse(source).body:
        if not isinstance(noeud, ast.If):
            continue
        cible = noeud.test
        if (
            isinstance(cible, ast.Compare)
            and isinstance(cible.left, ast.Name)
            and cible.left.id == "__name__"
        ):
            return noeud.lineno
    return None


def classe(rendu) -> tuple[str, str]:
    """Le verdict d un auto-test MUTE, lu sur son code de sortie et sur ce qu il a dit.

    | ce qu on observe | verdict | ce que cela prouve |
    |---|---|---|
    | code 0 | decoratif | l auto-test ne lit pas sa detection |
    | une trace Python | non concluant | rien : il est mort avant d assertir (ADR 5257) |
    | code 2, sans trace | non concluant | rien : il a REFUSE DE JOUER |
    | tout autre code, sans trace | tient | il a vu sa detection disparaitre |

    ## Le refus de jouer (#6030)

    Un auto-test qui s ouvre par un prealable d outil sort en 2 sans trace quand l outil manque. Ce
    banc lisait « rouge sans trace », donc « tient », pour un garde dont AUCUN cas n avait joue.
    Mesure du 2026-10-06 sur le runner du job `temoins`, qui n a ni ImageMagick ni ffmpeg : 2 des
    39 gardes annonces eprouves, `compare_apercus.py` et `compare_tournages.py`.

    Le signe est le CODE, et c est le canal de l ADR 5774 : ce qui empeche de juger sort en `2`, ce
    qui a ete juge sort en `1`. Mesure du meme jour, en relevant le code de chaque auto-test mute :
    les 50 gardes du corpus sortent en 1 sur un poste outille, et sans outils ces deux-la sortent
    en 2, eux seuls. Aucun garde du corpus ne rougit donc en 2 pour dire qu il a detecte.

    Rendu par UNE fonction pour les deux chemins, `eprouve` et `eprouve_fichier` : l auto-test ne
    joue que le second, et une classification recopiee aurait laisse ses cas juger une jumelle de
    ce que le banc applique au corpus.
    """
    if rendu.returncode == 0:
        return "decoratif", "reste vert sans sa detection"
    if TRACE in rendu.stderr:
        derniere = [l for l in rendu.stderr.strip().splitlines() if l and not l.startswith(" ")]
        return "non concluant", (derniere[-1] if derniere else "trace illisible")[:110]
    if rendu.returncode == CODE_DE_REFUS:
        dit = [l for l in (rendu.stderr or rendu.stdout).strip().splitlines() if l.strip()]
        return "non concluant", ("a refuse de jouer : " + (dit[-1] if dit else "sans un mot"))[:110]
    return "tient", ""


def mute(source: str) -> str:
    """La source, neutralisation INSEREE avant le point d entree de module."""
    ligne = ligne_du_point_d_entree(source)
    lignes = source.splitlines(keepends=True)
    return "".join(lignes[: ligne - 1]) + neutralisation(source) + "".join(lignes[ligne - 1 :])


def eprouve(garde: pathlib.Path) -> tuple[str, str]:
    """Lance l auto-test du garde MUTE, et rend (verdict, cause).

    Verdicts : « tient » (rouge sans trace), « non concluant » (rouge avec trace), « decoratif »
    (vert). La cause n est renseignee que pour les deux derniers, et elle est ce qu on lit.

    L arbre est jetable : le dossier de tete du garde est COPIE pour qu on puisse y ecrire le mute,
    tout le reste est LIE. Copier `.github` coute quelques centaines de kilo-octets ; le lier ferait
    resoudre le chemin vers ce depot-ci, et le garde mute s y ecrirait. Constate en construisant ce
    banc, avant que la premiere mesure ne parte.
    """
    with tempfile.TemporaryDirectory(prefix="vc-temoins-ci-") as tmp:
        faux = pathlib.Path(tmp) / "depot"
        faux.mkdir()
        sommet = garde.relative_to(RACINE).parts[0]
        for entree in RACINE.iterdir():
            if entree.name in (".git", "target", "node_modules"):
                continue
            if entree.name == sommet:
                shutil.copytree(entree, faux / entree.name, symlinks=True)
            else:
                (faux / entree.name).symlink_to(entree)
        relatif = garde.relative_to(RACINE)
        (faux / relatif).write_text(mute(garde.read_text(encoding="utf-8")), encoding="utf-8")
        rendu = subprocess.run(
            [sys.executable, str(faux / relatif), "--auto-test"],
            capture_output=True,
            text=True,
            cwd=faux,
            check=False,
            timeout=300,
        )
    return classe(rendu)


def ecarts_de_la_table(plantent: list[str], entier: bool = True) -> tuple[list[str], list[str]]:
    """Les deux ecarts entre ce qui ne conclut pas et ce que la table nomme : (inattendus, perimes).

    Le premier sens vaut toujours. Le second ne se juge que sur le corpus ENTIER : sur une part du
    corpus, les entrees non visitees paraitraient reparees (ADR 5743).

    Il ne depend plus de l hote (#6030). Tant qu un refus de jouer se lisait « tient », deux entrees
    n etaient visitees que la ou leurs outils existaient, et cette fonction recevait de quoi sonder
    le poste. Un garde qui refuse de jouer est desormais visite et non concluant partout.
    """
    inattendus = sorted(set(plantent) - set(PLANTENT_SOUS_MUTATION))
    if not entier:
        return inattendus, []
    return inattendus, sorted(set(PLANTENT_SOUS_MUTATION) - set(plantent))


def conclut_sur_la_table(plantent: list[str], entier: bool = True) -> int:
    """Rend 1 avec un CONSTAT si la table ne decrit plus ce qui plante, et 0 sinon.

    Un constat et non un refus : le banc a mute son corpus et compare, donc il a JUGE (ADR 5774). Il
    rend `1` SANS les marques de refus, que la porte lirait « ce garde n a pas pu juger ».
    """
    inattendus, perimes = ecarts_de_la_table(plantent, entier)
    if not (inattendus or perimes):
        return 0
    dits = []
    if inattendus:
        dits.append(
            f"{len(inattendus)} garde(s) plantent sous mutation sans etre nommes : "
            + ", ".join(inattendus)
        )
    if perimes:
        dits.append(
            f"{len(perimes)} entree(s) de PLANTENT_SOUS_MUTATION ne plantent plus : "
            + ", ".join(perimes)
        )
    print("\n" + " ; ".join(dits), file=sys.stderr)
    print(CONDUITE_SUR_LA_TABLE, file=sys.stderr)
    return 1


def juge(gardes: list[pathlib.Path] | None = None) -> int:
    entier = gardes is None
    gardes = corpus() if gardes is None else gardes
    tient, non_concluants, decoratifs = 0, [], []
    for g in gardes:
        verdict, cause = eprouve(g)
        if verdict == "tient":
            tient += 1
        elif verdict == "non concluant":
            non_concluants.append((g.name, cause))
        else:
            decoratifs.append((g.name, cause))

    print("ADR 4490 - temoin decoratif : l auto-test de CI reste vert sans sa detection")
    print()
    print(f"  eprouves et TENANT           : {tient}")
    print(f"  NON CONCLUANTS               : {len(non_concluants)}")
    for nom, cause in non_concluants:
        print(f"      {nom} : {cause}")
    print(f"  DECORATIFS                   : {len(decoratifs)}")
    for nom, cause in decoratifs:
        print(f"      {nom} : {cause}")
    print()
    somme = tient + len(non_concluants) + len(decoratifs)
    print(f"  les trois comptes font {somme}, et la population en vaut {len(gardes)}.")
    print()
    verdict = "ok" if not decoratifs else "regression"
    print(
        f"ADR 4490 | lus={len(gardes)} | suspects={len(decoratifs)} | cliquet=0 | verdict={verdict}"
    )
    # La ligne de verdict sort AVANT la confrontation : la porte la lit, et un constat qui la
    # precederait la ferait disparaitre de la sortie.
    constat = conclut_sur_la_table([nom for nom, _ in non_concluants], entier)
    return 1 if (decoratifs or constat) else 0


GARDE_QUI_TIENT = '''#!/usr/bin/env python3
"""Faux garde : son auto-test lit ce que sa detection rend."""
import sys


def suspects():
    return ["un defaut"]


def _auto_test():
    if suspects() != ["un defaut"]:
        print("ECHEC")
        return 1
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(_auto_test())
'''

GARDE_DECORATIF = '''#!/usr/bin/env python3
"""Faux garde : son auto-test ne regarde jamais sa detection."""
import sys


def suspects():
    return ["un defaut"]


def _auto_test():
    print("ok, sans avoir rien lu")
    return 0


if __name__ == "__main__":
    sys.exit(_auto_test())
'''

GARDE_QUI_PLANTE = '''#!/usr/bin/env python3
"""Faux garde : son auto-test depaquette ce que la detection rend."""
import sys


def suspects():
    return ["un defaut"], 1


def _auto_test():
    liste, compte = suspects()
    print("ok" if compte == 1 else "ECHEC")
    return 0 if compte == 1 else 1


if __name__ == "__main__":
    sys.exit(_auto_test())
'''

# ⟨#6030⟩ Le garde qui REFUSE DE JOUER : son auto-test s ouvre par un prealable et sort en 2 sans
# trace, avant son premier cas. C est la forme de `compare_apercus.py` sans ImageMagick. Sa
# detection est lue plus bas, donc avec son outil il tiendrait : ce qu on eprouve est le refus.
GARDE_QUI_REFUSE_DE_JOUER = '''#!/usr/bin/env python3
"""Faux garde : son auto-test exige un outil que cet hote ne porte pas."""
import shutil
import sys


def suspects():
    return ["un defaut"]


def _auto_test():
    if shutil.which("outil-que-personne-n-a-installe") is None:
        print("outil requis pour l auto-test.", file=sys.stderr)
        return 2
    return 0 if suspects() == ["un defaut"] else 1


if __name__ == "__main__":
    sys.exit(_auto_test())
'''


def _auto_test() -> int:
    """Les trois verdicts, chacun sur un faux garde ecrit pour lui.

    **L ordre des cas n est pas indifferent, et la raison est ce banc lui-meme.** Sous sa propre
    mutation, `eprouve_fichier` rend `[]` et le depaquetage `obtenu, cause = ...` PLANTE : ce banc
    tomberait alors dans sa propre categorie « non concluant », qui ne prouve rien. Les deux cas de
    POPULATION passent donc en premier : `corpus` rend une liste, `[]` en est une, et le cas echoue
    proprement au lieu de mourir. Ce banc est ainsi vu ROUGE sur sa propre mutation, comme l article
    A2 l exige, et non seulement mort.

    C est aussi l aveu que la limite mesuree sur les neuf autres vaut pour lui : une neutralisation
    qui rend `[]` ne sait pas se faire passer pour un tuple.

    Ce que ces cas n eprouvent PAS : le corpus reel. Ils prouvent que le banc SAIT distinguer les
    trois etats, pas qu il a raison sur les 43. C est le lancement sans argument qui le dit, et il
    est en CI.
    """
    # `echecs` ne compte plus que les deux cas de POPULATION, ecrits a la main plus bas : l arret
    # anticipe porte sur eux. Les cas de VERDICT delèguent au fonds (#5460), et leur depaquetage
    # passe en APPELABLE : sous la mutation de ce banc, `eprouve_fichier` rend `[]` et
    # `obtenu, cause = ...` leve, ce que la fabrique nomme au lieu de laisser le banc mourir.
    asserte, verdicts = cas_d_auto_test()
    echecs = cas = rouges = 0

    def verifie(attendu: str, source: str, libelle: str) -> None:
        nonlocal cas, rouges
        cas += 1
        if attendu != "tient":
            rouges += 1
        vu: dict[str, str] = {}

        def juger() -> str:
            with tempfile.TemporaryDirectory(prefix="vc-temoins-ci-test-") as tmp:
                faux = pathlib.Path(tmp) / "faux_garde.py"
                faux.write_text(source, encoding="utf-8")
                vu["obtenu"], vu["cause"] = eprouve_fichier(faux)
            return vu["obtenu"]

        asserte(libelle, juger, attendu)
        # La CAUSE n est connue que si le jugement a abouti ; la fabrique ne la porte pas, et sans
        # elle un echec dirait QUE le verdict est faux sans dire POURQUOI.
        if vu.get("obtenu", attendu) != attendu:
            print(f"          cause « {vu['cause']} »")

    print("AUTO-TEST")
    # La population se DERIVE : un garde absent des ateliers n y entre pas.
    cas += 1
    trouve = [g.name for g in corpus(texte="verifie_titre_pr.py et rien d autre")]
    if trouve != ["verifie_titre_pr.py"]:
        echecs += 1
        print(f"  [ÉCHEC] la population se derive du texte des ateliers      -> {trouve}")
    else:
        print("  [OK   ] la population se derive du texte des ateliers        -> 1 garde")

    cas += 1
    if MOI in [g.name for g in corpus()]:
        echecs += 1
        print("  [ÉCHEC] ce garde ne s eprouve pas lui-meme                   -> il s y trouve")
    else:
        print("  [OK   ] ce garde ne s eprouve pas lui-meme                   -> absent")

    # On S ARRETE ici si la population est fausse : les trois cas suivants eprouvent le VERDICT, et
    # un verdict rendu sur une population qu on ne sait plus deriver ne veut rien dire. C est aussi
    # ce qui fait rougir ce banc PROPREMENT sous sa propre mutation, la ou continuer le ferait
    # planter au premier depaquetage - et un plantage ne prouve rien (ADR 4918).
    if echecs:
        print()
        print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
        print(f"AUTO-TEST EN ÉCHEC ({echecs}) : la population ne se derive plus, le reste est tu.")
        return 1

    verifie("tient", GARDE_QUI_TIENT, "un auto-test qui lit sa detection rougit")
    verifie("decoratif", GARDE_DECORATIF, "un auto-test qui ne la lit pas reste vert")
    verifie("non concluant", GARDE_QUI_PLANTE, "un depaquetage sur [] ne conclut pas")
    verifie(
        "non concluant",
        GARDE_QUI_REFUSE_DE_JOUER,
        "un auto-test qui refuse de jouer ne conclut pas",
    )

    # ⟨#5497⟩ La table des non concluants, confrontee dans les deux sens. Les cas eprouvent la
    # fonction et le geste sur des noms FABRIQUES : muter quarante-huit gardes pour savoir ce que
    # rend une difference d ensembles ne prouverait rien de plus.
    connus = list(PLANTENT_SOUS_MUTATION)
    cas += 7
    asserte(
        "les nommes qui ne concluent pas ne font aucun ecart",
        lambda: ecarts_de_la_table(connus),
        ([], []),
    )
    asserte(
        "un garde de PLUS qui ne conclut pas est un inattendu, et il se nomme",
        lambda: ecarts_de_la_table([*connus, "neuf.py"]),
        (["neuf.py"], []),
    )
    # ⟨le sens que n aurait pas un cliquet⟩ Un compte reste VERT quand un plantage est repare.
    asserte(
        "une entree qui conclut desormais est perimee, et elle se nomme aussi",
        lambda: ecarts_de_la_table(connus[1:]),
        ([], ["cas_manquants_du_tournage.py"]),
    )
    asserte(
        "sur un corpus PARTIEL, aucune entree n est dite perimee",
        lambda: ecarts_de_la_table([], entier=False),
        ([], []),
    )
    # Une cle qui ne serait pas un garde du corpus ne rencontrerait jamais ce que `juge` rend.
    asserte(
        "chaque entree de la table est un garde du corpus",
        lambda: sorted(set(PLANTENT_SOUS_MUTATION) - {g.name for g in corpus()}),
        [],
    )

    # ⟨le GESTE, et son CANAL⟩ Les cas ci-dessus eprouvent le calcul ; ceux-ci, qu on AGIT dessus.
    def sortie_de(noms: list[str]) -> tuple[int, str]:
        tampon = io.StringIO()
        with contextlib.redirect_stderr(tampon):
            return conclut_sur_la_table(noms), tampon.getvalue()

    asserte(
        "un ecart rend un constat ROUGE, en 1, et les nommes n en rendent aucun",
        lambda: (
            sortie_de([*connus, "neuf.py"])[0],
            sortie_de(connus[1:])[0],
            sortie_de(connus)[0],
        ),
        (1, 1, 0),
    )
    # La porte classe par les MARQUES : un constat qui les porterait serait lu « muet » (ADR 5774).
    asserte(
        "et le constat ne porte AUCUNE marque de refus",
        lambda: [
            m for m in (MARQUE_CAUSE, MARQUE_GESTE) if m in sortie_de([*connus, "neuf.py"])[1]
        ],
        [],
    )

    print()
    print(f"{cas} cas, dont {rouges} qui DOIVENT rougir.")
    if echecs == 0 and verdicts() == 0:
        print("Auto-test concluant.")
        return 0
    print(f"AUTO-TEST EN ÉCHEC ({echecs + verdicts()}) : ne pas se fier au verdict de ce banc.")
    return 1


def eprouve_fichier(chemin: pathlib.Path) -> tuple[str, str]:
    """`eprouve` pour un fichier isole, sans arbre de depot : ce dont l auto-test a besoin."""
    source = chemin.read_text(encoding="utf-8")
    mute_chemin = chemin.with_name("mute_" + chemin.name)
    mute_chemin.write_text(mute(source), encoding="utf-8")
    rendu = subprocess.run(
        [sys.executable, str(mute_chemin)],
        capture_output=True,
        text=True,
        check=False,
        timeout=120,
    )
    return classe(rendu)


if __name__ == "__main__":
    if "--contrat" in sys.argv:
        print("CONTRAT | garde=" + CONTRAT["garde"])
        for clef in ("geste", "population", "dispositif", "seuil", "temoin", "decision"):
            print(f"{clef}: {CONTRAT[clef]}")
        sys.exit(0)
    sys.exit(_auto_test() if "--auto-test" in sys.argv else juge())
