#!/usr/bin/env python3
"""Decoupe, audite et fusionne les lots de la couche semantique du graphe du depot.

La couche de structure du graphe se tient a jour sans LLM, par `rebuild.py`. La couche semantique,
elle, sort d agents qui lisent la prose : chacun rend un lot, et les lots se fusionnent. Trois
defauts de cette fusion ne font rougir aucun dispositif, et l ADR 5790 les nomme. Cet outil porte
leurs parades, et un quatrieme controle que la mise a jour du 4 octobre 2026 a rendu necessaire.

  1. un lot ne REEMET JAMAIS un identifiant de structure : il s y ancre par une arete ;
  2. un noeud semantique HOMONYME d un titre se REPLIE sur ce titre, sans quoi le dedoublonnage
     fusionne les deux sous l identifiant semantique et celui du titre disparait ;
  3. le champ des MEMBRES d une hyperarete se normalise : les agents l ecrivent de quatre facons,
     le moteur n en lit qu une, et les autres hyperaretes tombent sans message ;
  4. un lot de MISE A JOUR remplace toute la couche de sa page : un identifiant semantique qu il
     ne reemet pas est efface, et les aretes venues d autres pages se rompent. Il le DECLARE ;
  5. une hyperarete SANS IDENTIFIANT tombe a la fusion suivante, meme a vide. Elle en recoit un ;
  6. une HYPERARETE de la page se remplace avec le reste de sa couche : celle que le lot ne
     reemet pas est effacee. La fiche la nomme, et le lot la DECLARE comme un identifiant ;
  7. une ARETE d avant peut aboutir sur une page HORS de la passe : la fiche range cette
     extremite sous `ailleurs`, et l audit l admet, comme le membre d une hyperarete ;
  8. un LIBELLE ne se partage pas : le moteur fond deux enonces de meme libelle, et l identifiant
     de l un disparait. L audit le refuse entre deux lots, et la fusion NOMME ce qu elle a fondu.

La cinquieme a ete trouvee en rejouant cet outil sur des lots reels, le 4 octobre 2026 : 25 des 71
hyperaretes de la premiere extraction n avaient pas d `id`, et la mise a jour du meme jour les a
toutes effacees, dont 22 sur des pages qu elle ne touchait pas. Aucun compte de noeuds ne le montre.

La sixieme a ete trouvee le 5 octobre 2026 par une session neuve, qui refaisait la couche d une
page avec la seule documentation du depot (#5904). La fiche ne nommait pas l hyperarete de la
page, l audit n en regardait aucune, et la fusion la retirait sous un verdict « 0 perdu » : 63
pages sur 589 en portaient une. La fusion retire aussi, en le DISANT, le membre d une hyperarete
qui ne designe plus aucun noeud : celle d une autre page en gardait un quand le lot le lachait.

Les deux dernieres ont ete trouvees le 5 octobre 2026 par la premiere reextraction a plusieurs
lecteurs depuis que cet outil est au depot : 37 pages, 4 lots (#5936). Les quatre rendus passaient
l audit et la fusion rendait « perdus du fait des lots=0 ». Treize aretes d avant sur 1 023
n avaient pas pu etre reemises, et un enonce d avant sur 918 avait ete fondu dans un autre : le
verdict ne comptait que la structure, et l audit jugeait chaque lot seul.

Mesure du 4 octobre 2026, sur la premiere fusion de 32 lots : 62 identifiants de structure perdus
sans la parade 2, dont 10 par ressemblance approchee ; 101 replis et aucune perte avec elle.

La perte se juge contre une FUSION A VIDE, qui fait deja tomber environ 550 identifiants, presque
tous des titres repetes du journal des versions. Le premier garde en comptait 612 et refusait a
tort : seul l ecart est imputable au lot.

Usage :
    couche_semantique.py cherche  [--graphe G] [--nombre N] "QUESTION"
    couche_semantique.py a-reextraire [--graphe G]
    couche_semantique.py decoupe  --dossier DIR [--graphe G] (--a-reextraire | PAGE [PAGE ...])
    couche_semantique.py audite   --dossier DIR
    couche_semantique.py fusionne --dossier DIR
    couche_semantique.py note     [--graphe G] [--commit C] (--perimetre | PAGE [PAGE ...])
    couche_semantique.py oublie   [--graphe G] PAGE [PAGE ...]
    couche_semantique.py --auto-test

`cherche` rend les enonces de la couche qui repondent a une question posee en langage courant,
chacun avec sa justification et sa page. Il lit le graphe sans importer le moteur. `graphify
query` reste l outil pour aller d un noeud a ses voisins.

L ORDRE, pour qui refait la couche d une ou de plusieurs pages : `a-reextraire` dit lesquelles,
`decoupe` prepare DIR, chaque lot est lu et rendu, `audite` juge les rendus, `fusionne` les verse
au graphe. `G` est le chemin d un fichier `graph.json` ; sans lui c est `graphify-out/graph.json`
de l arbre du script.

`decoupe` ecrit dans DIR un `lot_NN.json` par lot, plus les index du code et des pages. La fiche
d une page porte sa structure, sa couche semantique d avant avec ses aretes et ses hyperaretes, et deux
empreintes : celle que la fusion avait notee et celle d aujourd hui, pour que `git diff` de l une
a l autre montre ce qui a change. Chaque agent lit `consigne-des-agents.md` et rend
`rendu_NN.json` dans le meme dossier.

`fusionne` refuse si l audit refuse, et abandonne sans toucher au graphe si un identifiant de
structure manque. Il se lance DEPUIS L ARBRE QUI PORTE LE GRAPHE, et avec l interprete de
graphify, que nomme `graphify-out/.graphify_python`. Il ecrit le graphe de son arbre : un graphe
designe ailleurs est refuse, car la fusion le lirait et noterait son registre pendant que la
reconstruction ecrirait celui de l arbre. Son verdict dit ce qu une fusion A VIDE perd, a cote
de ce que les lots font perdre.

LIRE SA SORTIE. Trois comptes de noeuds s y suivent, et un seul est celui du graphe ecrit :
`FUSION | noeuds=` compte la fusion des lots, les lignes `resultat :` sont celles des ponts, et
la derniere ligne `[graphify] N noeuds` est le graphe ecrit. `ENONCES | attendus=` compte les
enonces qui devaient s y trouver, et chaque ligne `fondu :` nomme celui que le moteur a fondu dans
un autre de meme libelle, avec celui qui reste : la fusion le DIT et sort en 0, c est a l audit
que le lecteur pouvait encore renommer. `HYPERARETES | dans le graphe=`
donne leur nombre apres fusion, et `amputees` nomme celles dont un membre ne designait plus
rien. Les ponts comptent ce qu ils AJOUTENT : un zero y dit que tout etait deja relie.

POUR CONSTATER qu une fusion a pris, rejouer `decoupe` sur la page dans un autre dossier : la
fiche neuve porte les noeuds, les aretes et les hyperaretes que le graphe a maintenant.

## Quelles pages reextraire

Une page modifiee apres son extraction garde ses noeuds semantiques d avant, et rien ne le signale
a qui interroge le graphe. `a-reextraire` rend ces pages. Il compare l EMPREINTE git de chaque page
du perimetre a celle que la fusion a notee dans `couche-semantique.json`, a cote du graphe.

Une empreinte PAR PAGE et non un commit pour toute la couche : le 4 octobre 2026 elle a ete faite
en deux passes, et six pages ont ete ecartees de la seconde expres. Un seul commit ne decrit pas
cela. `decoupe` releve l empreinte au moment ou les agents commencent a lire, et `fusionne` la
note : une page modifiee PENDANT l extraction ressort donc comme modifiee, au lieu d etre crue lue.

`note` sert a amorcer le registre, ou a attester qu une page a ete relue et que son changement ne
touche pas sa couche, un chiffre regenere par exemple. Avec `--commit`, il note l etat de ce commit.

`oublie` retire du graphe la couche semantique d une page qui a quitte le perimetre ou le depot,
et son empreinte du registre. Sans lui la page sortirait « disparue » a chaque passe, et ses noeuds
resteraient a repondre pour une page que plus personne ne relit. Il refuse une page encore dans le
perimetre : celle-la se reextrait, elle ne s oublie pas.

Le graphe et son registre sont ignores par git et ne vivent que dans la copie principale. Depuis
un worktree, `--graphe` les designe pour LIRE, par `a-reextraire` et `decoupe`, et la liste porte
l arbre qu elle a compare. Sans graphe ou
sans registre l outil REFUSE : une liste vide y serait le resultat le plus facile a obtenir, et
celui qui ne prouve rien.

Cet outil ne juge aucune demande et ne declare pas de CONTRAT : il repond a qui refait la couche.
Seul `fusionne` a besoin de graphify, et il sort en 2 quand il manque.
"""

from __future__ import annotations

import contextlib
import difflib
import hashlib
import io
import json
import math
import re
import subprocess
import sys
import tempfile
import types
import unicodedata
from collections import Counter
from pathlib import Path, PurePosixPath

RACINE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RACINE / "scripts"))
from _commun import cas_d_auto_test

IDENTIFIANT = re.compile(r"^[a-z0-9_]+$")
# Les quatre noms que les agents ont donnes au champ des membres, le 4 octobre 2026. Le moteur ne
# lit que le premier et normalise le deuxieme ; les deux autres faisaient tomber l hyperarete.
CHAMPS_DES_MEMBRES = ("nodes", "members", "member_ids", "node_ids")
# Le seuil du dedoublonnage approche de graphify : en dessous, il ne fusionne pas, donc rien a parer.
SEUIL_DE_RESSEMBLANCE = 0.85
PLAFOND_DE_MOTS = 22_000
PLAFOND_DE_PAGES = 40
# Le perimetre de l ADR 5790, complete par l ADR 5857. Rejoue sur le commit de la premiere
# extraction, cette regle rend 576 de ses 577 pages : toutes sauf le registre que l ADR 5857 en
# sort. C est ce compte connu d avance qui la tient.
RACINES_DE_PROSE = ("brief", "dev-docs", "docs")
# Deux pages nommees, parce qu aucune regle de forme ne les distingue de leurs voisines. Le journal
# des versions est engendre. Le registre des javadocs relues est reecrit a chaque relecture : 15 des
# 33 fusions qui ont suivi la premiere extraction le touchaient, pour une couche d un seul noeud.
PAGES_EXCLUES = ("CHANGELOG.md", "scripts/methode/relus.txt")
RACINES_DE_CODE = ("scripts", "src")
EXTENSIONS_DE_PAGE = (".md", ".txt")
REGISTRE = "couche-semantique.json"
CONFIANCE = ("confidence", "confidence_score")


class Refus(Exception):
    """Ce que l outil ne peut pas faire, dit a qui l appelle au lieu d une liste vide."""


def du_perimetre(chemin: str) -> bool:
    """Cette page entre-t-elle dans la couche semantique, d apres l ADR 5790 ?"""
    page = PurePosixPath(chemin)
    if page.suffix not in EXTENSIONS_DE_PAGE or chemin in PAGES_EXCLUES:
        return False
    if len(page.parts) == 1:
        return page.suffix == ".md"
    if page.parts[0] in RACINES_DE_PROSE:
        return True
    return page.parts[0] in RACINES_DE_CODE and not chemin.endswith(".approved.txt")


def a_reextraire(
    courantes: dict[str, str], notees: dict[str, str]
) -> tuple[list[str], list[str], list[str]]:
    """Les pages modifiees, neuves et disparues, de l etat courant contre l etat note.

    Une page NEUVE n a jamais ete extraite ; une page DISPARUE laisse des noeuds que rien ne
    viendra remplacer. Les trois listes sont separees parce qu elles n appellent pas le meme geste.
    """
    modifiees = sorted(p for p in courantes if p in notees and courantes[p] != notees[p])
    neuves = sorted(p for p in courantes if p not in notees)
    disparues = sorted(p for p in notees if p not in courantes)
    return modifiees, neuves, disparues


def cle_de_libelle(libelle: object) -> str:
    """Le libelle reduit a ses lettres et chiffres, sans accent ni casse.

    C est ce que le dedoublonnage compare : « `WINGET_TOKEN` : huit jours » et « WINGET_TOKEN : huit
    jours » y sont le meme noeud, et l un efface l autre.
    """
    plat = unicodedata.normalize("NFKD", str(libelle)).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", plat.casefold())


# Les mots qui ne disent rien d une question. Ecrits sans accent : `mots_de` les compare apres
# avoir aplati le texte.
MOTS_VIDES = frozenset(
    [
        "a",
        "au",
        "aux",
        "avec",
        "ce",
        "ces",
        "cet",
        "cette",
        "comment",
        "d",
        "dans",
        "de",
        "des",
        "du",
        "elle",
        "elles",
        "en",
        "est",
        "et",
        "etre",
        "fait",
        "faire",
        "il",
        "ils",
        "je",
        "l",
        "la",
        "le",
        "les",
        "leur",
        "leurs",
        "n",
        "ne",
        "nous",
        "on",
        "ont",
        "ou",
        "par",
        "pas",
        "plus",
        "plutot",
        "pour",
        "pourquoi",
        "qu",
        "que",
        "quel",
        "quelle",
        "quelles",
        "quels",
        "qui",
        "quoi",
        "s",
        "sa",
        "sans",
        "se",
        "ses",
        "son",
        "sont",
        "sur",
        "tu",
        "un",
        "une",
        "vous",
        "y",
    ]
)


def mots_de(texte: object) -> list[str]:
    """Les mots d un texte, sans accent ni casse, sans mots vides, ramenes a une racine courte.

    La racine est grossiere, et c est voulu : un `s` ou un `x` final tombe, puis le mot se coupe a
    sept lettres. « depots » et « depot », « relancable » et « relancables » se rejoignent sans
    qu aucun dictionnaire n entre au depot.
    """
    plat = unicodedata.normalize("NFKD", str(texte or "")).encode("ascii", "ignore").decode()
    mots = [m for m in re.findall(r"[a-z0-9]+", plat.casefold()) if m not in MOTS_VIDES]
    return [(m[:-1] if len(m) > 3 and m[-1] in "sx" else m)[:7] for m in mots if len(m) > 1]


def enonces_qui_repondent(
    question: str, enonces: list[dict], nombre: int = 5
) -> tuple[list[str], list[dict]]:
    """Les mots retenus de la question, et les enonces qui les portent, du mieux au moins bien note.

    Le moteur choisit ses points de depart parmi les libelles qui ressemblent a la question : un
    symbole de code au libelle exact, `WAV`, capte le depart, et la justification d un enonce, qui
    porte le pourquoi, n est jamais lue (#5939). Ici seuls les ENONCES sont candidats, et leur
    justification compte.

    Un mot compte une fois, deux s il est dans le libelle : c est la PRESENCE qui note, pas la
    repetition, sans quoi une longue justification qui redit « depot » passe devant l enonce dont
    le libelle porte tous les mots de la question. Un mot rare pese plus qu un mot que beaucoup
    d enonces portent. A note egale, le plus court passe devant.
    """
    demandes = sorted(set(mots_de(question)))
    lus = [(e, set(mots_de(e.get("label"))), set(mots_de(e.get("rationale")))) for e in enonces]
    porteurs = Counter(mot for _, du_libelle, du_reste in lus for mot in du_libelle | du_reste)
    notes = []
    for enonce, du_libelle, du_reste in lus:
        note = sum(
            (2 if mot in du_libelle else 1) * math.log(1 + len(lus) / porteurs[mot])
            for mot in demandes
            if mot in du_libelle | du_reste
        )
        if note:
            notes.append((-note, len(du_libelle | du_reste), enonce["id"], enonce))
    return demandes, [n[3] for n in sorted(notes, key=lambda n: n[:3])[:nombre]]


def identifiant_d_hyperarete(hyperarete: dict, membres: list[str]) -> str:
    """Un identifiant STABLE pour une hyperarete qui n en porte pas.

    Derive de ce qui la definit, sa page, son libelle et ses membres, pour que deux fusions du
    meme lot lui donnent le meme : un compteur la ferait doubler a chaque rejeu.
    """
    graine = "|".join(
        [str(hyperarete.get("source_file")), str(hyperarete.get("label")), *sorted(membres)]
    )
    return "hyper_" + hashlib.sha1(graine.encode("utf-8")).hexdigest()[:12]


def normalise_hyperaretes(hyperaretes: list[dict]) -> list[dict]:
    """Range les membres sous `nodes`, et donne un identifiant a l hyperarete qui n en a pas."""
    rendues = []
    for hyperarete in hyperaretes:
        propre = {k: v for k, v in hyperarete.items() if k not in CHAMPS_DES_MEMBRES}
        membres = next((hyperarete[c] for c in CHAMPS_DES_MEMBRES if c in hyperarete), [])
        propre["nodes"] = list(membres)
        if not propre.get("id"):
            propre["id"] = identifiant_d_hyperarete(hyperarete, propre["nodes"])
        rendues.append(propre)
    return rendues


def jumeau_de_structure(noeud: dict, structure: list[dict]) -> str | None:
    """L identifiant du noeud de structure du MEME fichier dont le libelle vaut celui-ci, ou None.

    L egalite d abord, la ressemblance ensuite, au seuil ou le dedoublonnage fusionnerait.
    """
    cle = cle_de_libelle(noeud["label"])
    candidats = [s for s in structure if s.get("source_file") == noeud.get("source_file")]
    for candidat in candidats:
        if cle_de_libelle(candidat["label"]) == cle:
            return candidat["id"]
    for candidat in candidats:
        autre = cle_de_libelle(candidat["label"])
        if difflib.SequenceMatcher(None, cle, autre).ratio() >= SEUIL_DE_RESSEMBLANCE:
            return candidat["id"]
    return None


def replie_les_homonymes(
    noeuds: list[dict], aretes: list[dict], hyperaretes: list[dict], structure: list[dict]
) -> tuple[list[dict], list[dict], list[dict], dict[str, str], dict[str, str]]:
    """Replie chaque noeud semantique homonyme d un noeud de structure sur ce dernier.

    Rend les noeuds restants, les aretes et hyperaretes recablees, la table des replis, et les
    justifications a reporter sur les noeuds de structure : l information du noeud replie ne se
    perd pas, elle change de porteur.
    """
    replis: dict[str, str] = {}
    justifications: dict[str, str] = {}
    gardes = []
    for noeud in noeuds:
        cible = jumeau_de_structure(noeud, structure)
        if cible is None or cible == noeud["id"]:
            gardes.append(noeud)
            continue
        replis[noeud["id"]] = cible
        if noeud.get("rationale"):
            justifications[cible] = noeud["rationale"]
    recablees = []
    for arete in aretes:
        neuve = dict(arete)
        neuve["source"] = replis.get(arete["source"], arete["source"])
        neuve["target"] = replis.get(arete["target"], arete["target"])
        if neuve["source"] != neuve["target"]:
            recablees.append(neuve)
    hyper = []
    for hyperarete in normalise_hyperaretes(hyperaretes):
        hyperarete["nodes"] = [replis.get(m, m) for m in hyperarete["nodes"]]
        hyper.append(hyperarete)
    return gardes, recablees, hyper, replis, justifications


def audite(
    rendu: dict,
    fiche: dict,
    autres_connus: set[str],
    emis_ailleurs: frozenset[str] = frozenset(),
    libelles_d_ailleurs: dict[str, str] | None = None,
) -> dict[str, list[str]]:
    """Les defauts d un lot rendu, confronte a la fiche que `decoupe` lui avait donnee.

    Rend un dictionnaire vide quand le lot est sain. `autres_connus` porte les identifiants des
    index du code et des pages : un lot peut les viser par une arete, jamais les reemettre.

    `emis_ailleurs` porte ce que les AUTRES lots de la meme passe emettent. Une arete peut y
    aboutir : deux ADR qui s amendent tombent dans deux lots des que le decoupage les separe, et
    le premier rejeu sur des lots reels refusait a tort cinq aretes de cette forme.

    `libelles_d_ailleurs` porte les libelles que ces autres lots donnent, chacun avec l enonce qui
    le porte. Le moteur dedoublonne par libelle : deux enonces de meme libelle n en font qu un, et
    l identifiant de l un disparait sans qu aucun `laches` le nomme (#5936).
    """
    structure = {n["id"] for page in fiche.values() for n in page["structure"]}
    semantiques = {n["id"] for page in fiche.values() for n in page.get("semantique", [])}
    emis = [n["id"] for n in rendu.get("nodes", [])]
    ensemble = set(emis)
    items = rendu.get("nodes", []) + rendu.get("edges", [])
    declares = set(rendu.get("laches", []))
    # Les hyperaretes se lisent comme la fusion les lira : membres sous `nodes`, et un identifiant
    # derive pour celle qui n en porte pas, qui ne peut donc pas tenir lieu de celle de la page.
    hyperaretes = normalise_hyperaretes(rendu.get("hyperedges", []))
    tenues = {h["id"] for page in fiche.values() for h in page.get("hyperaretes", [])}
    # Un membre peut vivre sur une AUTRE page : 34 hyperaretes sur 71 le 5 octobre 2026. L audit
    # ne voit pas le graphe, c est la fiche qui les nomme.
    ailleurs = {
        m
        for page in fiche.values()
        for h in page.get("hyperaretes", [])
        for m in h.get("ailleurs", [])
    }
    membres_admis = ensemble | structure | autres_connus | emis_ailleurs | ailleurs
    # Une arete d avant peut aboutir sur une page HORS de la passe : la fiche la porte pour que le
    # lecteur garde celles que la page porte toujours, et nomme cette extremite (#5936).
    bouts_d_ailleurs = {
        bout
        for page in fiche.values()
        for arete in page.get("aretes", [])
        for bout in arete.get("ailleurs", [])
    }
    bouts_admis = ensemble | structure | autres_connus | emis_ailleurs | bouts_d_ailleurs
    pris = dict(libelles_d_ailleurs or {})
    en_double: list[str] = []
    for noeud in rendu.get("nodes", []):
        cle = cle_de_libelle(noeud.get("label"))
        if cle in pris:
            en_double.append(f"{noeud['id']} = {pris[cle]}")
        pris.setdefault(cle, f"lot courant : {noeud['id']}")
    defauts = {
        "identifiant de structure reemis": sorted(ensemble & (structure | autres_connus)),
        "identifiant mal forme": sorted(
            i for i in ensemble - semantiques if not IDENTIFIANT.match(i)
        ),
        "identifiant en double": sorted({i for i in emis if emis.count(i) > 1}),
        "extremite inconnue": sorted(
            {
                arete[bout]
                for arete in rendu.get("edges", [])
                for bout in ("source", "target")
                if arete[bout] not in bouts_admis
            }
        ),
        "libelle deja porte par un autre enonce": sorted(en_double),
        "source_location non nul": sorted(
            {str(x.get("id", x.get("source"))) for x in items if x.get("source_location")}
        ),
        "origine autre que semantic": sorted(
            {str(x.get("id", x.get("source"))) for x in items if x.get("_origin") != "semantic"}
        ),
        "source_file hors du lot": sorted(
            {
                str(x.get("source_file"))
                for x in items + hyperaretes
                if x.get("source_file") not in fiche
            }
        ),
        "page sans noeud": sorted(
            page
            for page in fiche
            if not any(n.get("source_file") == page for n in rendu.get("nodes", []))
        ),
        "identifiant semantique lache sans le declarer": sorted(semantiques - ensemble - declares),
        "hyperarete lachee sans le declarer": sorted(
            tenues - {h["id"] for h in hyperaretes} - declares
        ),
        "membre d hyperarete inconnu": sorted(
            {m for h in hyperaretes for m in h["nodes"] if m not in membres_admis}
        ),
    }
    return {nom: liste for nom, liste in defauts.items() if liste}


def sans_membres_pendants(
    hyperaretes: list[dict], identifiants: set[str]
) -> tuple[list[dict], dict[str, list[str]]]:
    """Retire de chaque hyperarete les membres qui ne designent plus aucun noeud, et dit lesquels.

    Le moteur le fait pour l hyperarete du lot, sans le dire. Il ne le fait pas pour celle d une
    AUTRE page dont un membre vivait sur la page reextraite : elle gardait un membre pendant.
    """
    tenues: list[dict] = []
    retires: dict[str, list[str]] = {}
    for hyperarete in hyperaretes:
        membres = list(hyperarete.get("nodes", []))
        partis = [m for m in membres if m not in identifiants]
        if partis:
            retires[str(hyperarete.get("id"))] = partis
            hyperarete = {**hyperarete, "nodes": [m for m in membres if m in identifiants]}
        tenues.append(hyperarete)
    return tenues, retires


def perdus_imputables(avant: set[str], apres: set[str], a_vide: set[str]) -> set[str]:
    """Les identifiants de structure que le LOT a fait tomber, hors de ce qu une fusion a vide perd."""
    return (avant - apres) - a_vide


def decoupe(
    mots: dict[str, int], plafond_de_mots: int = PLAFOND_DE_MOTS, plafond: int = PLAFOND_DE_PAGES
) -> list[list[str]]:
    """Range les pages en lots, dans l ordre des chemins pour garder les voisines ensemble.

    Une page plus lourde que le plafond fait un lot a elle seule : la couper ferait lire a deux
    agents deux moities d un meme raisonnement.
    """
    lots: list[list[str]] = []
    courant: list[str] = []
    poids = 0
    for page in sorted(mots):
        if courant and (poids + mots[page] > plafond_de_mots or len(courant) >= plafond):
            lots.append(courant)
            courant, poids = [], 0
        courant.append(page)
        poids += mots[page]
    if courant:
        lots.append(courant)
    return lots


def _lis(chemin: Path) -> dict:
    return json.loads(chemin.read_text(encoding="utf-8"))


def _ecris(chemin: Path, contenu: object) -> None:
    chemin.write_text(json.dumps(contenu, ensure_ascii=False, indent=1), encoding="utf-8")


def _fiche_vide() -> dict[str, list[dict]]:
    return {"structure": [], "semantique": [], "hyperaretes": [], "aretes": []}


def _noeuds_du_graphe(graphe: Path) -> list[dict]:
    return _lis(graphe)["nodes"]


def sans_la_couche_de(graphe: dict, pages: set[str]) -> tuple[dict, dict[str, int]]:
    """Le graphe sans la couche SEMANTIQUE de ces pages, et le compte de ce qui en sort.

    Sortent : les noeuds semantiques de ces pages, toute arete qui touche l un d eux quelle que soit
    son origine, puisqu elle n aurait plus d extremite, les aretes semantiques que ces pages
    portaient, et leurs hyperaretes. Une hyperarete d une AUTRE page perd ses membres retires, et
    reste. La couche de structure n est pas touchee : c est la mise a jour de la structure qui la
    tient, et elle sait deja retirer une page disparue.
    """
    partis = {
        n["id"]
        for n in graphe["nodes"]
        if n.get("_origin") == "semantic" and n.get("source_file") in pages
    }
    noeuds = [n for n in graphe["nodes"] if n["id"] not in partis]
    aretes = [
        a
        for a in graphe["links"]
        if a["source"] not in partis
        and a["target"] not in partis
        and not (a.get("_origin") == "semantic" and a.get("source_file") in pages)
    ]
    hyperaretes = []
    for hyperarete in graphe.get("hyperedges", []):
        if hyperarete.get("source_file") in pages:
            continue
        hyperaretes.append(
            {**hyperarete, "nodes": [m for m in hyperarete.get("nodes", []) if m not in partis]}
        )
    compte = {
        "noeuds": len(graphe["nodes"]) - len(noeuds),
        "aretes": len(graphe["links"]) - len(aretes),
        "hyperaretes": len(graphe.get("hyperedges", [])) - len(hyperaretes),
    }
    return {**graphe, "nodes": noeuds, "links": aretes, "hyperedges": hyperaretes}, compte


def _git(racine: Path, *arguments: str, entree: str | None = None) -> str:
    """La sortie d une commande git jouee dans `racine`, ou un Refus qui nomme la commande."""
    commande = ["git", "-C", str(racine), "-c", "core.quotePath=false", *arguments]
    try:
        rendu = subprocess.run(commande, input=entree, capture_output=True, text=True, check=False)
    except FileNotFoundError as absent:
        raise Refus("git est introuvable sur ce poste") from absent
    if rendu.returncode:
        raise Refus(f"`git {' '.join(arguments[:2])}` a echoue : {rendu.stderr.strip()[:200]}")
    return rendu.stdout


def pages_du_perimetre(racine: Path) -> list[str]:
    """Les pages SUIVIES du perimetre.

    Par `git ls-files` et non par un glob : un glob compte `node_modules`, que le crochet pose par
    worktree, et rend 94 pages sous `.github/` la ou le depot en suit 5. Et sans motif de chemin :
    le `**` d un pathspec git ne couvre pas le niveau zero, et rend 3 de ces 5 pages.
    """
    suivies = _git(racine, "ls-files", "-z").split("\0")
    return sorted(page for page in suivies if page and du_perimetre(page))


def empreintes(racine: Path, pages: list[str], commit: str | None = None) -> dict[str, str]:
    """L empreinte git de chaque page, dans l arbre de travail ou a un commit donne."""
    if commit is None:
        sorties = _git(racine, "hash-object", "--stdin-paths", entree="\n".join(pages) + "\n")
        return dict(zip(pages, sorties.split(), strict=True))
    rendues = {}
    for ligne in _git(racine, "ls-tree", "-r", "-z", commit).split("\0"):
        if ligne:
            meta, page = ligne.split("\t", 1)
            rendues[page] = meta.split()[2]
    absentes = [page for page in pages if page not in rendues]
    if absentes:
        raise Refus(f"{len(absentes)} page(s) absente(s) du commit {commit} : {absentes[:3]}")
    return {page: rendues[page] for page in pages}


def arbre_compare(racine: Path) -> str:
    """Le commit de l arbre lu, marque quand des pages suivies y sont modifiees sans etre commises."""
    court = _git(racine, "rev-parse", "--short", "HEAD").strip()
    return court + ("+modifs" if _git(racine, "status", "--porcelain", "-uno").strip() else "")


def registre_de(graphe: Path) -> Path:
    return graphe.parent / REGISTRE


def notees_de(graphe: Path) -> dict[str, str]:
    """Les empreintes notees, ou un Refus : sans elles aucune comparaison n a de sens."""
    if not graphe.is_file():
        raise Refus(
            f"aucun graphe a {graphe}. Il est ignore par git et ne vit que dans la copie"
            " principale : depuis un worktree, le designer par --graphe."
        )
    registre = registre_de(graphe)
    if not registre.is_file():
        raise Refus(
            f"aucune empreinte notee a {registre}. Une liste vide ne dirait pas « rien n a"
            " change » mais « rien n a ete compare » : amorcer par `note`."
        )
    return _lis(registre)["pages"]


def note(graphe: Path, neuves: dict[str, str]) -> int:
    """Ajoute ces empreintes au registre, sans toucher celles des autres pages."""
    registre = registre_de(graphe)
    pages = _lis(registre)["pages"] if registre.is_file() else {}
    pages.update(neuves)
    _ecris(registre, {"pages": dict(sorted(pages.items()))})
    return len(pages)


def commande_a_reextraire(graphe: Path, racine: Path) -> int:
    """Imprime les pages a reextraire, une par ligne avec sa raison, puis la ligne de compte."""
    notees = notees_de(graphe)
    courantes = empreintes(racine, pages_du_perimetre(racine))
    modifiees, neuves, disparues = a_reextraire(courantes, notees)
    for raison, pages in (("modifiee", modifiees), ("neuve", neuves), ("disparue", disparues)):
        for page in pages:
            print(f"{raison}\t{page}")
    print(
        f"A REEXTRAIRE | perimetre={len(courantes)} | notees={len(notees)}"
        f" | modifiees={len(modifiees)} | neuves={len(neuves)} | disparues={len(disparues)}"
        f" | arbre={arbre_compare(racine)}"
    )
    return 0


def commande_note(graphe: Path, racine: Path, pages: list[str], commit: str | None) -> int:
    """Note l empreinte de ces pages : leur couche reflete leur contenu a cet instant."""
    if not graphe.is_file():
        raise Refus(f"aucun graphe a {graphe} : le registre se pose a cote de lui.")
    hors = [page for page in pages if not du_perimetre(page)]
    if hors:
        raise Refus(f"{len(hors)} page(s) hors du perimetre de l ADR 5790 : {hors[:3]}")
    total = note(graphe, empreintes(racine, pages, commit))
    print(f"NOTE | pages={len(pages)} | registre={total} | etat={commit or arbre_compare(racine)}")
    return 0


def commande_oublie(graphe: Path, racine: Path, pages: list[str]) -> int:
    """Retire du graphe la couche semantique de ces pages, et leur empreinte du registre."""
    if not graphe.is_file():
        raise Refus(f"aucun graphe a {graphe}.")
    encore_la = sorted(set(pages) & set(pages_du_perimetre(racine)))
    if encore_la:
        raise Refus(
            f"{len(encore_la)} page(s) encore dans le perimetre : {encore_la[:3]}. Une page du"
            " perimetre se reextrait, elle ne s oublie pas."
        )
    allege, compte = sans_la_couche_de(_lis(graphe), set(pages))
    # Le graphe pese plusieurs dizaines de Mo : il s ecrit d un bloc, sans indentation, comme
    # `graphify` l ecrit lui-meme.
    graphe.write_text(json.dumps(allege, ensure_ascii=False), encoding="utf-8")
    registre = registre_de(graphe)
    oubliees = 0
    if registre.is_file():
        notees = _lis(registre)["pages"]
        oubliees = sum(1 for page in pages if notees.pop(page, None) is not None)
        _ecris(registre, {"pages": notees})
    print(
        f"OUBLIE | pages={len(pages)} | noeuds={compte['noeuds']} | aretes={compte['aretes']}"
        f" | hyperaretes={compte['hyperaretes']} | empreintes={oubliees}"
    )
    return 0


def commande_cherche(graphe: Path, question: str, nombre: int) -> int:
    """Rend les enonces de la couche qui repondent a une question, avec la page de chacun."""
    enonces = [n for n in _noeuds_du_graphe(graphe) if n.get("_origin") == "semantic"]
    if not enonces:
        # Une liste vide se lirait « rien dans la prose » alors qu aucune prose n a ete lue.
        raise Refus(f"{graphe} ne porte aucune couche semantique : la recherche n y lirait rien.")
    demandes, rendus = enonces_qui_repondent(question, enonces, nombre)
    print(f"CHERCHE | enonces={len(enonces)} | mots retenus={demandes} | rendus={len(rendus)}")
    if not demandes:
        print("Aucun mot de la question n est retenu : elle ne porte que des mots vides.")
    elif not rendus:
        print(
            "Aucun enonce de la couche ne porte ces mots. La prose n en dit rien, ou le dit autrement."
        )
    for rang, enonce in enumerate(rendus, 1):
        print(f"{rang}. {enonce['label']}")
        if enonce.get("rationale"):
            print(f"   parce que : {enonce['rationale']}")
        print(f"   page : {enonce.get('source_file')}")
        print(f"   id : {enonce['id']}")
    return 0


def commande_decoupe(dossier: Path, graphe: Path, pages: list[str], racine: Path = RACINE) -> int:
    """Ecrit les fiches de lot et les deux index dont les agents ont besoin."""
    absentes = [p for p in pages if not (racine / p).is_file()]
    if absentes:
        print(f"REFUS : {len(absentes)} page(s) introuvable(s) : {absentes[:5]}", file=sys.stderr)
        return 2
    if not pages:
        print("DECOUPE | pages=0 | lots=0 : rien a reextraire, aucun dossier ecrit.")
        return 0
    brut = _lis(graphe)
    noeuds = brut["nodes"]
    par_page: dict[str, dict[str, list[dict]]] = {}
    for noeud in noeuds:
        origine = noeud.get("_origin")
        if origine not in ("ast", "semantic"):
            continue
        fiche = par_page.setdefault(noeud.get("source_file"), _fiche_vide())
        court = {"id": noeud["id"], "label": noeud["label"]}
        if origine == "ast":
            fiche["structure"].append({**court, "node_kind": noeud.get("node_kind")})
        else:
            # La justification d avant, pour que l agent la COMPARE a la page au lieu de la
            # reecrire a l aveugle : cinq agents sur cinq l ont reecrite sans l avoir, le 5 octobre.
            # Et son type, que deux sessions neuves ont du redeviner (#5904).
            fiche["semantique"].append(
                {**court, **{c: noeud[c] for c in ("file_type", "rationale") if noeud.get(c)}}
            )
    # Les hyperaretes de la page, que le rendu remplace avec le reste de sa couche : sans elles
    # dans la fiche, l agent ne sait pas qu elles existent, et la fusion les retire (#5904).
    page_de = {n["id"]: n.get("source_file") for n in noeuds}
    for hyperarete in normalise_hyperaretes(brut.get("hyperedges", [])):
        sienne = hyperarete.get("source_file")
        par_page.setdefault(sienne, _fiche_vide())["hyperaretes"].append(
            {
                "id": hyperarete["id"],
                "label": hyperarete.get("label"),
                "nodes": hyperarete["nodes"],
                "ailleurs": [m for m in hyperarete["nodes"] if page_de.get(m) != sienne],
                **{c: hyperarete[c] for c in CONFIANCE if hyperarete.get(c) is not None},
            }
        )
    # Les aretes d avant, pour COMPARER : le rendu les remplace toutes, et une arete vers une autre
    # page ou vers le code que le lecteur ne reemet pas part sans que rien le dise. Deux sessions
    # neuves sur deux l ont releve. Elles informent, l audit ne les exige pas : une arete n a pas
    # d identifiant, et c est la page qui dit si elle tient encore.
    # Une extremite qui vit sur une page HORS de la passe n est ni dans les lots ni dans les index :
    # l audit la refusait, et le lecteur ne pouvait pas garder l arete (#5936). La fiche la range
    # sous `ailleurs`, comme le membre d une hyperarete. Sur une page de la passe, rien a nommer :
    # son lecteur reemettra l enonce ou le declarera.
    de_la_passe = set(pages)
    aux_index = {
        n["id"] for n in noeuds if n.get("_callable_class") or n.get("node_kind") == "page"
    }
    for arete in brut.get("links", brut.get("edges", [])):
        if arete.get("_origin") == "semantic":
            hors_de_la_passe = [
                arete[bout]
                for bout in ("source", "target")
                if page_de[arete[bout]] not in de_la_passe and arete[bout] not in aux_index
            ]
            par_page.setdefault(arete.get("source_file"), _fiche_vide())["aretes"].append(
                {
                    **{
                        c: arete[c]
                        for c in ("source", "target", "relation", *CONFIANCE)
                        if arete.get(c) is not None
                    },
                    **({"ailleurs": hors_de_la_passe} if hors_de_la_passe else {}),
                }
            )
    textes = {p: (racine / p).read_text(encoding="utf-8", errors="ignore") for p in pages}
    courantes = empreintes(racine, sorted(textes))
    # L empreinte que la fusion avait notee, quand il y en a une : `git diff` de celle-la a celle
    # d aujourd hui montre ce qui a change dans la page, que l agent devinait jusqu ici (#5904).
    notees = _lis(registre_de(graphe)).get("pages", {}) if registre_de(graphe).is_file() else {}
    lots = decoupe({p: len(t.split()) for p, t in textes.items()})
    dossier.mkdir(parents=True, exist_ok=True)
    for numero, lot in enumerate(lots, 1):
        fiche = {
            page: {
                "lignes": textes[page].count("\n") + 1,
                "empreintes": {"notee": notees.get(page), "courante": courantes.get(page)},
                **par_page.get(page, _fiche_vide()),
            }
            for page in lot
        }
        _ecris(dossier / f"lot_{numero:02d}.json", fiche)
    classes = [
        {"id": n["id"], "label": n["label"], "source_file": n["source_file"]}
        for n in noeuds
        if n.get("_callable_class")
    ]
    feuilles = [
        {"id": n["id"], "source_file": n["source_file"]}
        for n in noeuds
        if n.get("node_kind") == "page"
    ]
    _ecris(dossier / "index-du-code.json", classes)
    _ecris(dossier / "index-des-pages.json", feuilles)
    # L empreinte se releve ICI, quand les agents commencent a lire, et non a la fusion : une page
    # modifiee entre les deux doit ressortir comme modifiee.
    _ecris(dossier / "plan.json", {"lots": lots, "empreintes": courantes})
    print(f"DECOUPE | pages={len(pages)} | lots={len(lots)} | dossier={dossier}")
    return 0


def _connus(dossier: Path) -> set[str]:
    return {x["id"] for x in _lis(dossier / "index-du-code.json")} | {
        x["id"] for x in _lis(dossier / "index-des-pages.json")
    }


def commande_audite(dossier: Path) -> int:
    """Audite chaque lot attendu. Un lot ABSENT est un defaut, jamais un lot de moins."""
    plan = _lis(dossier / "plan.json")["lots"]
    connus = _connus(dossier)
    rendus = {
        numero: _lis(dossier / f"rendu_{numero:02d}.json")
        for numero in range(1, len(plan) + 1)
        if (dossier / f"rendu_{numero:02d}.json").is_file()
    }
    refus = 0
    for numero in range(1, len(plan) + 1):
        if numero not in rendus:
            print(f"lot {numero:02d} : ABSENT")
            refus += 1
            continue
        ailleurs = frozenset(
            n["id"] for autre, rendu in rendus.items() if autre != numero for n in rendu["nodes"]
        )
        libelles = {
            cle_de_libelle(n.get("label")): f"lot {autre:02d} : {n['id']}"
            for autre, rendu in rendus.items()
            if autre != numero
            for n in rendu["nodes"]
        }
        fiche = _lis(dossier / f"lot_{numero:02d}.json")
        defauts = audite(rendus[numero], fiche, connus, ailleurs, libelles)
        for nom, liste in defauts.items():
            print(f"lot {numero:02d} : {nom} ({len(liste)}) : {liste[:5]}")
        if not defauts:
            # Un lot sain etait MUET : tant que d autres lots manquaient, son lecteur ne distinguait
            # pas « mon lot passe » de « mon lot n a pas ete lu ». Quatre sur quatre, le 5 octobre.
            rendu = rendus[numero]
            print(
                f"lot {numero:02d} : sain ({len(rendu.get('nodes', []))} noeuds,"
                f" {len(rendu.get('edges', []))} aretes,"
                f" {len(rendu.get('hyperedges', []))} hyperarete)"
            )
        refus += bool(defauts)
    print(f"AUDIT | lots={len(plan)} | refuses={refus}")
    return 1 if refus else 0


def commande_fusionne(dossier: Path, graphe: Path) -> int:
    """Fusionne les lots audites dans le graphe, puis rejoue les ponts et la reconstruction."""
    try:
        from graphify.build import build_merge
    except ModuleNotFoundError:
        print(
            "REFUS : graphify n est pas importable par cet interprete. Ce refus parle du poste,"
            " pas des lots : lancer avec l interprete de graphify-out/.graphify_python.",
            file=sys.stderr,
        )
        return 2
    import rebuild

    if graphe.resolve() != rebuild.GRAPHE.resolve():
        # La fusion lirait le graphe designe et noterait son registre, mais la reconstruction
        # ecrit celui de l arbre du script : joue le 5 octobre 2026, le graphe designe n avait pas
        # bouge et son registre disait la page extraite.
        raise Refus(
            f"`fusionne` ecrit le graphe de l arbre d ou il est lance, {rebuild.GRAPHE}, et"
            f" {graphe} n en est pas. Lancer la commande depuis l arbre qui porte ce graphe."
        )
    if commande_audite(dossier):
        print("REFUS : l audit refuse, la fusion n est pas tentee.", file=sys.stderr)
        return 1
    plan = _lis(dossier / "plan.json")["lots"]
    noeuds: dict[str, dict] = {}
    aretes: list[dict] = []
    hyperaretes: list[dict] = []
    for numero in range(1, len(plan) + 1):
        rendu = _lis(dossier / f"rendu_{numero:02d}.json")
        for noeud in rendu["nodes"]:
            noeuds.setdefault(noeud["id"], noeud)
        aretes += rendu["edges"]
        hyperaretes += [{**h, "_origin": "semantic"} for h in rendu.get("hyperedges", [])]
    avant = _noeuds_du_graphe(graphe)
    structure = [n for n in avant if n.get("_origin") == "ast"]
    ids_de_structure = {n["id"] for n in structure}
    gardes, recablees, hyper, replis, justifications = replie_les_homonymes(
        list(noeuds.values()), aretes, hyperaretes, structure
    )
    print(f"replis sur un noeud de structure : {len(replis)}")

    vide = {"nodes": [], "edges": [], "hyperedges": []}
    restent_a_vide = set(
        build_merge([vide], graph_path=str(graphe), root=str(RACINE), directed=False).nodes
    )
    a_vide = ids_de_structure - restent_a_vide
    lot = {"nodes": gardes, "edges": recablees, "hyperedges": hyper}
    fusion = build_merge([lot], graph_path=str(graphe), root=str(RACINE), directed=False)
    perdus = perdus_imputables(ids_de_structure, set(fusion.nodes), a_vide)
    # La perte a vide se DIT : sans elle, des identifiants de structure disparaissent sous un
    # verdict « 0 perdu », et rien ne permet de savoir qu ils ne doivent rien au lot.
    print(
        f"FUSION | noeuds={fusion.number_of_nodes()} | perdus a vide={len(a_vide)}"
        f" | perdus du fait des lots={len(perdus)}"
    )
    if perdus:
        print(f"REFUS : identifiants de structure perdus : {sorted(perdus)[:10]}", file=sys.stderr)
        print("Le graphe n a pas ete touche.", file=sys.stderr)
        return 1
    for cible, texte in justifications.items():
        if cible in fusion.nodes:
            fusion.nodes[cible]["rationale"] = texte
    # Les ENONCES aussi se comptent. Le moteur dedoublonne par libelle : un enonce qui porte celui
    # d un autre disparait, et le verdict ci-dessus, qui ne compte que la structure, rendait
    # « 0 perdu ». Un identifiant sur 918 le 5 octobre 2026. La fusion le NOMME et ne refuse pas :
    # deux pages peuvent dire la meme chose, et c est a l audit que le lecteur peut encore renommer.
    pages_de_la_passe = {page for lot in plan for page in lot}
    d_avant = {n["id"]: n for n in avant if n.get("_origin") == "semantic"}
    hors_de_la_passe = {
        i for i, n in d_avant.items() if n.get("source_file") not in pages_de_la_passe
    }
    attendus = hors_de_la_passe | {n["id"] for n in gardes}
    absents_a_vide = hors_de_la_passe - restent_a_vide
    fondus = sorted(attendus - set(fusion.nodes) - absents_a_vide)
    print(
        f"ENONCES | attendus={len(attendus)} | absents a vide={len(absents_a_vide)}"
        f" | fondus du fait des lots={len(fondus)}"
    )
    libelle_de = {
        **{i: n.get("label") for i, n in d_avant.items()},
        **{n["id"]: n.get("label") for n in gardes},
    }
    restes: dict[str, str] = {}
    for identifiant, donnees in fusion.nodes(data=True):
        restes.setdefault(cle_de_libelle(donnees.get("label")), identifiant)
    for identifiant in fondus:
        reste = restes.get(cle_de_libelle(libelle_de.get(identifiant)))
        print(f"  fondu : {identifiant} -> {reste or 'aucun enonce de meme libelle'}")
    tenues, retires = sans_membres_pendants(
        list(fusion.graph.get("hyperedges", [])), set(fusion.nodes)
    )
    print(
        f"HYPERARETES | dans le graphe={len(tenues)}"
        f" | membres pendants retires={sum(map(len, retires.values()))}"
        f" | amputees={sorted(retires)}"
    )

    rebuild.EXTRAIT.write_text(
        json.dumps(
            {
                "nodes": [{"id": n, **d} for n, d in fusion.nodes(data=True)],
                "edges": [
                    {
                        **{
                            k: v
                            for k, v in d.items()
                            if k not in ("_src", "_tgt", "source", "target")
                        },
                        "source": d.get("_src", u),
                        "target": d.get("_tgt", v),
                    }
                    for u, v, d in fusion.edges(data=True)
                ],
                "hyperedges": tenues,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    try:
        rebuild.jouer_les_passes()
        rebuild.reconstruire()
    finally:
        rebuild.EXTRAIT.unlink(missing_ok=True)
    releve = _lis(dossier / "plan.json").get("empreintes", {})
    print(f"NOTE | pages={len(releve)} | registre={note(graphe, releve)}")
    return 0


def auto_test() -> int:
    """Les cas des quatre parades, sur des lots FABRIQUES : graphify n y est jamais importe.

    Chaque parade a un cas qui rougit quand on la retire ET un cas temoin qui reste vert, sans quoi
    rien ne distinguerait un controle qui detecte d un controle qui refuse toujours.
    """
    verifie, echecs = cas_d_auto_test()

    page = "dev-docs/page.md"
    structure = [
        {"id": "dev_docs_page", "label": "page.md", "source_file": page},
        {
            "id": "dev_docs_page_le_portail",
            "source_file": page,
            "label": "Le portail regarde les deux zones, et le code mort compte",
        },
        {"id": "dev_docs_autre_titre", "label": "Le depot part en WAV", "source_file": "autre.md"},
    ]
    fiche = {
        page: {
            "lignes": 10,
            "structure": [{"id": s["id"], "label": s["label"]} for s in structure[:2]],
            "semantique": [{"id": "dev_docs_page_ancien", "label": "Un concept deja la"}],
        }
    }

    def noeud(identifiant: str, libelle: str, **plus: object) -> dict:
        return {
            "id": identifiant,
            "label": libelle,
            "source_file": page,
            "source_location": None,
            "_origin": "semantic",
            **plus,
        }

    def arete(source: str, cible: str) -> dict:
        return {
            "source": source,
            "target": cible,
            "relation": "references",
            "source_file": page,
            "source_location": None,
            "_origin": "semantic",
        }

    sain = {
        "nodes": [
            noeud("dev_docs_page_ancien", "Un concept deja la"),
            noeud("dev_docs_page_neuf", "Un concept neuf"),
        ],
        "edges": [
            arete("dev_docs_page_le_portail", "dev_docs_page_neuf"),
            arete("dev_docs_page_neuf", "une_classe"),
        ],
    }
    connus = {"une_classe"}

    def avec(**change: object) -> dict:
        return {**sain, **change}

    def noms(rendu: dict) -> list[str]:
        return sorted(audite(rendu, fiche, connus))

    verifie("un lot sain ne porte aucun defaut", lambda: noms(sain), [])

    # ⟨les aretes d avant vers une autre page, #5936⟩ la fiche les porte pour que le lecteur garde
    # celles que la page porte toujours, et l audit refusait leur cible : 13 aretes sur 1 023 le
    # 5 octobre 2026, que quatre lecteurs sur quatre ont du remplacer ou abandonner.
    vers_ailleurs = {
        "source": "dev_docs_page_ancien",
        "target": "enonce_d_une_autre_page",
        "relation": "references",
        "ailleurs": ["enonce_d_une_autre_page"],
    }
    fiche_reliee = {page: {**fiche[page], "aretes": [vers_ailleurs]}}
    reemise_ailleurs = avec(
        edges=[*sain["edges"], arete("dev_docs_page_ancien", "enonce_d_une_autre_page")]
    )
    verifie(
        "une arete d avant vers un enonce d une autre page, que la fiche nomme, est admise",
        lambda: sorted(audite(reemise_ailleurs, fiche_reliee, connus)),
        [],
    )
    verifie(
        "une extremite que la fiche ne nomme pas reste refusee",
        lambda: sorted(audite(reemise_ailleurs, fiche, connus)),
        ["extremite inconnue"],
    )

    # ⟨les hyperaretes de la page⟩ une page reextraite perdait la sienne sans que rien le dise
    # (#5904) : la fiche ne la nommait pas, l audit ne regardait aucune hyperarete, et la fusion la
    # retirait sous un verdict « 0 perdu ». 63 pages sur 589 en portaient une le 5 octobre 2026.
    tenue = {
        "id": "dev_docs_page_hyper_ensemble",
        "label": "Un ensemble",
        "nodes": ["dev_docs_page_ancien", "dev_docs_page_le_portail"],
    }
    fiche_tenue = {page: {**fiche[page], "hyperaretes": [tenue]}}

    def hyper(membres: list[str], **plus: object) -> dict:
        return {
            "id": tenue["id"],
            "label": "Un ensemble",
            "nodes": membres,
            "source_file": page,
            "source_location": None,
            "_origin": "semantic",
            **plus,
        }

    def noms_tenue(rendu: dict) -> list[str]:
        return sorted(audite(rendu, fiche_tenue, connus))

    reemise = hyper(tenue["nodes"])
    verifie(
        "un lot qui reemet l hyperarete de sa page est sain",
        lambda: noms_tenue(avec(hyperedges=[reemise])),
        [],
    )
    verifie(
        "un lot qui OMET l hyperarete de sa page est refuse",
        lambda: noms_tenue(sain),
        ["hyperarete lachee sans le declarer"],
    )
    verifie(
        "un lot qui la lache en le DECLARANT est sain",
        lambda: noms_tenue(avec(laches=[tenue["id"]])),
        [],
    )
    sans_identifiant = {k: v for k, v in reemise.items() if k != "id"}
    verifie(
        "une hyperarete rendue sans identifiant ne tient pas lieu de celle de la page",
        lambda: noms_tenue(avec(hyperedges=[sans_identifiant])),
        ["hyperarete lachee sans le declarer"],
    )
    verifie(
        "une hyperarete dont un membre n existe nulle part est refusee",
        lambda: noms_tenue(avec(hyperedges=[hyper(["dev_docs_page_ancien", "nulle_part"])])),
        ["membre d hyperarete inconnu"],
    )
    partout = ["dev_docs_page_neuf", "dev_docs_page_le_portail", "une_classe"]
    verifie(
        "un membre peut etre un noeud du lot, un titre de la page ou une classe de l index",
        lambda: noms_tenue(avec(hyperedges=[hyper(partout)])),
        [],
    )
    autrement = {**sans_identifiant, "id": tenue["id"], "member_ids": ["nulle_part"]}
    del autrement["nodes"]
    verifie(
        "les membres se lisent sous leurs quatre noms, comme a la fusion",
        lambda: noms_tenue(avec(hyperedges=[autrement])),
        ["membre d hyperarete inconnu"],
    )
    verifie(
        "une hyperarete rangee sous une autre page que celles du lot est refusee",
        lambda: noms_tenue(avec(hyperedges=[hyper(tenue["nodes"], source_file="autre.md")])),
        ["source_file hors du lot"],
    )
    verifie(
        "une fiche sans hyperarete n en exige aucune",
        lambda: noms(avec(hyperedges=[hyper(tenue["nodes"])])),
        [],
    )
    # Un membre qui vit sur une AUTRE page n est ni dans le lot ni dans les index : c est la
    # fiche qui le nomme, sous `ailleurs`. Sans cela, reemettre fidelement l hyperarete serait
    # refuse. Le temoin est le meme membre que la fiche ne nomme pas.
    voisine = {**tenue, "nodes": [*tenue["nodes"], "concept_d_une_autre_page"]}
    fiche_voisine = {
        page: {
            **fiche[page],
            "hyperaretes": [{**voisine, "ailleurs": ["concept_d_une_autre_page"]}],
        }
    }
    verifie(
        "un membre d une autre page est admis quand la fiche le nomme, et refuse sinon",
        lambda: [
            sorted(audite(avec(hyperedges=[hyper(voisine["nodes"])]), fiche_voisine, connus)),
            noms_tenue(avec(hyperedges=[hyper(voisine["nodes"])])),
        ],
        [[], ["membre d hyperarete inconnu"]],
    )
    # A la fusion, un membre qui ne designe plus aucun noeud se retire, et se DIT. Le moteur ne
    # le fait pas pour l hyperarete d une autre page.
    pendante = {"id": "h_voisine", "nodes": ["reste", "parti", "parti_aussi"]}
    entiere = {"id": "h_entiere", "nodes": ["reste"]}
    verifie(
        "un membre pendant se retire de son hyperarete, qui reste, et les autres ne bougent pas",
        lambda: sans_membres_pendants([pendante, entiere], {"reste"}),
        (
            [{"id": "h_voisine", "nodes": ["reste"]}, entiere],
            {"h_voisine": ["parti", "parti_aussi"]},
        ),
    )

    # ⟨parade 1⟩ un identifiant de structure, ou de l index du code, ne se reemet pas.
    verifie(
        "un lot qui reemet un identifiant de structure est refuse",
        lambda: noms(avec(nodes=[*sain["nodes"], noeud("dev_docs_page_le_portail", "Autre")])),
        ["identifiant de structure reemis"],
    )
    verifie(
        "un lot qui reemet un identifiant de l index du code est refuse",
        lambda: noms(avec(nodes=[*sain["nodes"], noeud("une_classe", "Une classe")])),
        ["identifiant de structure reemis"],
    )

    # ⟨parade 2⟩ l homonyme d un titre se replie, a l egalite comme a la ressemblance.
    # Le libelle du titre se lie a un NOM avant l appel : le releve des harnais muets compte toute
    # expression indexee passee en second argument, et cette aide n est pas un cas.
    titre = structure[1]["label"]
    exact = noeud("dev_docs_page_decision", titre, rationale="la raison")
    proche = noeud(
        "dev_docs_page_decision",
        "Le portail regarde les deux zones de code, et le code mort y compte",
    )
    distinct = noeud("dev_docs_page_cliquet", "Le cliquet du portail descend")
    verifie(
        "un homonyme exact d un titre se replie sur ce titre",
        lambda: replie_les_homonymes([exact], [], [], structure)[3],
        {"dev_docs_page_decision": "dev_docs_page_le_portail"},
    )
    verifie(
        "un homonyme APPROCHE se replie aussi, au seuil du dedoublonnage",
        lambda: replie_les_homonymes([proche], [], [], structure)[3],
        {"dev_docs_page_decision": "dev_docs_page_le_portail"},
    )
    # L egalite passe AVANT la ressemblance : sans cet ordre, un titre voisin place plus haut dans
    # la page capterait le noeud a la place du titre qu il nomme exactement.
    voisins = [
        {"id": "p_voisin", "label": "Le cliquet du portail descend vite", "source_file": page},
        {"id": "p_exact", "label": "Le cliquet du portail descend", "source_file": page},
    ]
    verifie(
        "un titre EXACT l emporte sur un titre voisin place avant lui",
        lambda: replie_les_homonymes([distinct], [], [], voisins)[3],
        {"dev_docs_page_cliquet": "p_exact"},
    )
    verifie(
        "un concept distinct du meme fichier n est pas replie",
        lambda: replie_les_homonymes([distinct], [], [], structure)[3],
        {},
    )
    verifie(
        "un homonyme d un titre d un AUTRE fichier n est pas replie",
        lambda: replie_les_homonymes(
            [noeud("dev_docs_page_wav", "Le depot part en WAV")], [], [], structure
        )[3],
        {},
    )
    verifie(
        "les aretes du noeud replie visent desormais le titre",
        lambda: [
            (a["source"], a["target"])
            for a in replie_les_homonymes(
                [exact, distinct],
                [arete("dev_docs_page_decision", "dev_docs_page_cliquet")],
                [],
                structure,
            )[1]
        ],
        [("dev_docs_page_le_portail", "dev_docs_page_cliquet")],
    )
    verifie(
        "la justification du noeud replie passe sur le titre",
        lambda: replie_les_homonymes([exact], [], [], structure)[4],
        {"dev_docs_page_le_portail": "la raison"},
    )

    # ⟨parade 3⟩ les quatre noms du champ des membres rendent la meme hyperarete.
    # Les quatre noms sont ECRITS ici et non lus dans la constante : boucler sur elle ferait
    # retrecir le temoin avec ce qu il eprouve.
    for champ in ("nodes", "members", "member_ids", "node_ids"):
        verifie(
            f"une hyperarete dont les membres sont sous `{champ}` les garde",
            lambda champ=champ: normalise_hyperaretes([{"id": "h", champ: ["a", "b", "c"]}]),
            [{"id": "h", "nodes": ["a", "b", "c"]}],
        )
    verifie(
        "les membres d une hyperarete suivent le repli",
        lambda: replie_les_homonymes(
            [exact], [], [{"id": "h", "member_ids": ["dev_docs_page_decision", "x"]}], structure
        )[2],
        [{"id": "h", "nodes": ["dev_docs_page_le_portail", "x"]}],
    )

    # ⟨parade 5⟩ une hyperarete sans identifiant en recoit un, stable, et celle qui en a le garde.
    orpheline = {"label": "Un flux", "source_file": page, "nodes": ["b", "a", "c"]}
    verifie(
        "une hyperarete sans identifiant en recoit un",
        lambda: [h["id"].startswith("hyper_") for h in normalise_hyperaretes([orpheline])],
        [True],
    )
    verifie(
        "le meme lot rejoue donne le meme identifiant, quel que soit l ordre des membres",
        lambda: (
            normalise_hyperaretes([orpheline])[0]["id"]
            == normalise_hyperaretes([{**orpheline, "nodes": ["c", "a", "b"]}])[0]["id"]
        ),
        True,
    )
    verifie(
        "deux hyperaretes distinctes de la meme page ne recoivent pas le meme identifiant",
        lambda: (
            normalise_hyperaretes([orpheline])[0]["id"]
            != normalise_hyperaretes([{**orpheline, "label": "Un autre flux"}])[0]["id"]
        ),
        True,
    )
    verifie(
        "un identifiant vide vaut une absence",
        lambda: normalise_hyperaretes([{**orpheline, "id": ""}])[0]["id"].startswith("hyper_"),
        True,
    )

    # ⟨parade 4⟩ une mise a jour qui lache un identifiant semantique le declare, ou est refusee.
    lache = avec(nodes=[sain["nodes"][1]])
    verifie(
        "un lot de mise a jour qui lache un identifiant semantique sans le dire est refuse",
        lambda: noms(lache),
        ["identifiant semantique lache sans le declarer"],
    )
    verifie(
        "le meme lot est accepte quand il declare ce qu il lache",
        lambda: noms({**lache, "laches": ["dev_docs_page_ancien"]}),
        [],
    )

    # ⟨le reste de l audit⟩ chaque defaut a son cas, sans quoi son nom ne prouve rien.
    verifie(
        "un `source_location` rempli est refuse : il ferait passer le noeud pour de la structure",
        lambda: noms(
            avec(nodes=[sain["nodes"][0], {**sain["nodes"][1], "source_location": "L42"}])
        ),
        ["source_location non nul"],
    )
    verifie(
        "une arete vers un identifiant que rien ne connait est refusee",
        lambda: noms(avec(edges=[arete("dev_docs_page_neuf", "nulle_part")])),
        ["extremite inconnue"],
    )
    verifie(
        "une arete vers un noeud emis par un AUTRE lot de la passe est acceptee",
        lambda: sorted(
            audite(
                avec(edges=[arete("dev_docs_page_neuf", "noeud_d_un_autre_lot")]),
                fiche,
                connus,
                frozenset({"noeud_d_un_autre_lot"}),
            )
        ),
        [],
    )
    verifie(
        "une page du lot restee sans noeud est refusee",
        lambda: noms({"nodes": [], "edges": [], "laches": ["dev_docs_page_ancien"]}),
        ["page sans noeud"],
    )

    # ⟨le temoin de la fusion a vide⟩ seul l ecart au lot vide est imputable.
    verifie(
        "ce qu une fusion a vide perd deja n est pas impute au lot",
        lambda: perdus_imputables({"a", "b", "c", "d"}, {"a"}, {"b", "c"}),
        {"d"},
    )
    verifie(
        "un lot qui ne perd rien de plus que la fusion a vide est sain",
        lambda: perdus_imputables({"a", "b", "c"}, {"a"}, {"b", "c"}),
        set(),
    )

    # ⟨le decoupage⟩ toutes les pages, dans l ordre, sous les deux plafonds.
    poids = {"b.md": 10, "a.md": 15, "c.md": 30, "d.md": 5}
    verifie(
        "le decoupage garde toutes les pages, dans l ordre des chemins",
        lambda: decoupe(poids, plafond_de_mots=25),
        [["a.md", "b.md"], ["c.md"], ["d.md"]],
    )
    verifie(
        "le plafond de pages coupe un lot de pages legeres",
        lambda: decoupe(poids, plafond_de_mots=1000, plafond=3),
        [["a.md", "b.md", "c.md"], ["d.md"]],
    )

    # ⟨par le point d entree ENTIER⟩ le refus et le cas positif empruntent `main`, pas `audite`.
    def par_main(rendu: dict | None) -> int:
        with tempfile.TemporaryDirectory() as temporaire:
            dossier = Path(temporaire)
            _ecris(dossier / "plan.json", {"lots": [[page]]})
            _ecris(dossier / "lot_01.json", fiche)
            _ecris(dossier / "index-du-code.json", [{"id": "une_classe"}])
            _ecris(dossier / "index-des-pages.json", [])
            if rendu is not None:
                _ecris(dossier / "rendu_01.json", rendu)
            return main(["couche_semantique.py", "audite", "--dossier", str(dossier)])

    def sans_plan() -> int:
        # Le refus se tait ici : sa marque sortirait d un auto-test qui PASSE, et la porte la
        # citerait comme la cause le jour ou il rougirait pour une autre raison (#5890).
        with (
            tempfile.TemporaryDirectory() as temporaire,
            contextlib.redirect_stderr(io.StringIO()),
        ):
            return main(["couche_semantique.py", "audite", "--dossier", temporaire])

    verifie("`audite` refuse en 2 un dossier que `decoupe` n a pas prepare", lambda: sans_plan(), 2)
    verifie("`audite` sort en 0 sur un lot sain", lambda: par_main(sain), 0)
    verifie("`audite` sort en 1 sur un lot en defaut", lambda: par_main(lache), 1)
    verifie("`audite` sort en 1 quand un lot attendu est ABSENT", lambda: par_main(None), 1)

    # ⟨deux lots, #5936⟩ l audit jugeait chaque lot seul. Deux lecteurs ont donne le meme libelle
    # a un enonce sur deux pages, le moteur a fondu les deux noeuds, et un identifiant d avant a
    # disparu sans qu aucun `laches` le nomme.
    seconde = "dev-docs/seconde.md"

    def lot_de_la_seconde(libelle: str) -> dict:
        return {
            "nodes": [{**noeud("dev_docs_seconde_enonce", libelle), "source_file": seconde}],
            "edges": [],
        }

    def par_deux_lots(premier: dict, second: dict) -> tuple[int, list[str]]:
        """Le code de l audit sur deux lots, et ses lignes de lot."""
        fiche_seconde = {seconde: {"lignes": 3, "structure": [], "semantique": []}}
        sortie = io.StringIO()
        with tempfile.TemporaryDirectory() as temporaire:
            dossier = Path(temporaire)
            _ecris(dossier / "plan.json", {"lots": [[page], [seconde]]})
            _ecris(dossier / "lot_01.json", fiche)
            _ecris(dossier / "lot_02.json", fiche_seconde)
            _ecris(dossier / "index-du-code.json", [{"id": "une_classe"}])
            _ecris(dossier / "index-des-pages.json", [])
            _ecris(dossier / "rendu_01.json", premier)
            _ecris(dossier / "rendu_02.json", second)
            with contextlib.redirect_stdout(sortie):
                code = main(["couche_semantique.py", "audite", "--dossier", str(dossier)])
        return code, [ligne for ligne in sortie.getvalue().splitlines() if ligne.startswith("lot ")]

    refus_du_premier = (
        "lot 01 : libelle deja porte par un autre enonce (1) : "
        "['dev_docs_page_neuf = lot 02 : dev_docs_seconde_enonce']"
    )
    refus_du_second = (
        "lot 02 : libelle deja porte par un autre enonce (1) : "
        "['dev_docs_seconde_enonce = lot 01 : dev_docs_page_neuf']"
    )
    verifie(
        "deux lots qui donnent le meme libelle a deux enonces sont refuses tous les deux",
        lambda: par_deux_lots(sain, lot_de_la_seconde("Un concept neuf")),
        (1, [refus_du_premier, refus_du_second]),
    )
    verifie(
        "deux lots aux libelles distincts sont sains, et chacun a sa ligne",
        lambda: par_deux_lots(sain, lot_de_la_seconde("Un tout autre concept")),
        (
            0,
            [
                "lot 01 : sain (2 noeuds, 2 aretes, 0 hyperarete)",
                "lot 02 : sain (1 noeuds, 0 aretes, 0 hyperarete)",
            ],
        ),
    )
    jumeaux = avec(nodes=[*sain["nodes"], noeud("dev_docs_page_bis", "Un concept neuf")])
    verifie(
        "deux enonces d un meme lot au meme libelle sont refuses aussi",
        lambda: noms(jumeaux),
        ["libelle deja porte par un autre enonce"],
    )

    # ⟨la fusion elle-meme⟩ la commande importe le moteur, absent du runner : ses calculs avaient
    # leurs cas, son cablage et son REFUS n en avaient aucun. Un faux moteur les joue. Il rend un
    # graphe qui garde la structure, moins ce qu on lui dit de perdre : a vide, ou du fait du lot.
    class Noeuds(dict):
        def __call__(self, data=False):
            return list(self.items()) if data else list(self)

    class Fusion:
        def __init__(self, identifiants, hyperaretes, donnees=None):
            donnees = donnees or {}
            self.nodes = Noeuds({i: dict(donnees.get(i, {})) for i in identifiants})
            self.graph = {"hyperedges": [dict(h) for h in hyperaretes]}

        def edges(self, data=False):
            return []

        def number_of_nodes(self):
            return len(self.nodes)

    def par_fusionne(
        rendu,
        perdus_a_vide=(),
        perdus_de_plus=(),
        moteur_present=True,
        graphe_de_l_arbre=True,
        hyperaretes=(),
        enonces_d_avant=(),
        fondus=(),
        enonces_perdus_a_vide=(),
    ):
        """Ce qu une fusion jouee a fait : son code, ses etapes, ses verdicts, et ce qui reste."""
        de_structure = {s["id"] for s in structure}
        d_avant_semantiques = {e["id"]: e for e in enonces_d_avant}
        etapes: list = []
        fusions = [0]

        def faux_build_merge(lots, graph_path=None, root=None, directed=False):
            fusions[0] += 1
            restent = (de_structure | set(d_avant_semantiques)) - set(perdus_a_vide)
            restent -= set(enonces_perdus_a_vide)
            donnees = dict(d_avant_semantiques)
            if lots[0]["nodes"]:
                restent -= {i for i, e in d_avant_semantiques.items() if e["source_file"] == page}
                restent = (restent - set(perdus_de_plus)) | {n["id"] for n in lots[0]["nodes"]}
                restent -= set(fondus)
                donnees.update({n["id"]: n for n in lots[0]["nodes"]})
            return Fusion(restent, hyperaretes, donnees)

        noms_des_modules = ("graphify", "graphify.build", "rebuild")
        d_avant = {nom: sys.modules.get(nom) for nom in noms_des_modules}
        with tempfile.TemporaryDirectory() as temporaire:
            dossier = Path(temporaire)
            _ecris(dossier / "plan.json", {"lots": [[page]], "empreintes": {page: "abc123"}})
            _ecris(dossier / "lot_01.json", fiche)
            _ecris(dossier / "index-du-code.json", [{"id": "une_classe"}])
            _ecris(dossier / "index-des-pages.json", [])
            _ecris(dossier / "rendu_01.json", rendu)
            graphe = dossier / "graph.json"
            _ecris(
                graphe,
                {
                    "nodes": [{**s, "_origin": "ast"} for s in structure] + list(enonces_d_avant),
                    "links": [],
                },
            )
            extrait = dossier / "extrait.json"
            faux_rebuild = types.ModuleType("rebuild")
            faux_rebuild.GRAPHE = graphe if graphe_de_l_arbre else dossier / "ailleurs.json"
            faux_rebuild.EXTRAIT = extrait
            faux_rebuild.jouer_les_passes = lambda: etapes.append(
                ("ponts", [h["nodes"] for h in _lis(extrait)["hyperedges"]])
                if extrait.is_file()
                else ("ponts", None)
            )
            faux_rebuild.reconstruire = lambda: etapes.append("reconstruction")
            sortie = io.StringIO()
            try:
                if moteur_present:
                    paquet = types.ModuleType("graphify")
                    paquet.__path__ = []
                    module = types.ModuleType("graphify.build")
                    module.build_merge = faux_build_merge
                    sys.modules["graphify"], sys.modules["graphify.build"] = paquet, module
                else:
                    # `None` dans `sys.modules` fait lever l import : un poste sans le module.
                    sys.modules["graphify"] = None
                    sys.modules.pop("graphify.build", None)
                sys.modules["rebuild"] = faux_rebuild
                with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
                    code = main(
                        [
                            "couche_semantique.py",
                            "fusionne",
                            "--dossier",
                            temporaire,
                            "--graphe",
                            str(graphe),
                        ]
                    )
            finally:
                for nom, module in d_avant.items():
                    if module is None:
                        sys.modules.pop(nom, None)
                    else:
                        sys.modules[nom] = module
            notees = sorted(notees_de(graphe)) if registre_de(graphe).is_file() else []
            # Les verdicts sans le compte de noeuds, qui depend du lot et n est pas le propos.
            verdicts = [
                ligne.split(" | ", 2)[2] if ligne.startswith(("FUSION", "ENONCES")) else ligne
                for ligne in sortie.getvalue().splitlines()
                if ligne.startswith(("FUSION |", "ENONCES |", "  fondu :", "HYPERARETES |"))
            ]
            return code, etapes, fusions[0], extrait.exists(), notees, verdicts

    sans_perte = "perdus a vide=0 | perdus du fait des lots=0"
    aucun_fondu = "absents a vide=0 | fondus du fait des lots=0"
    aucune_hyper = "HYPERARETES | dans le graphe=0 | membres pendants retires=0 | amputees=[]"
    verifie(
        "`fusionne` ecrit l extrait AVANT les ponts, reconstruit, le retire, et note les pages",
        lambda: par_fusionne(sain),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            [sans_perte, aucun_fondu, aucune_hyper],
        ),
    )
    verifie(
        "`fusionne` REFUSE un lot qui fait tomber un identifiant de structure, sans rien ecrire",
        lambda: par_fusionne(sain, perdus_de_plus=["dev_docs_page_le_portail"]),
        (1, [], 2, False, [], ["perdus a vide=0 | perdus du fait des lots=1"]),
    )
    verifie(
        "`fusionne` n impute pas au lot ce que la fusion a vide perd deja, et DIT cette perte",
        lambda: par_fusionne(sain, perdus_a_vide=["dev_docs_autre_titre"]),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            ["perdus a vide=1 | perdus du fait des lots=0", aucun_fondu, aucune_hyper],
        ),
    )
    verifie(
        "`fusionne` ne tente aucune fusion quand l audit refuse",
        lambda: par_fusionne(lache),
        (1, [], 0, False, [], []),
    )
    verifie(
        "`fusionne` refuse en 2, avant tout audit, sur un poste sans le module graphify",
        lambda: par_fusionne(sain, moteur_present=False),
        (2, [], 0, False, [], []),
    )
    # Le graphe designe n est pas celui de l arbre du script : la fusion le lirait et noterait son
    # registre, mais la reconstruction ecrirait ailleurs. Joue pour de bon le 5 octobre 2026.
    verifie(
        "`fusionne` refuse en 2 un graphe qui n est pas celui de son arbre, sans rien noter",
        lambda: par_fusionne(sain, graphe_de_l_arbre=False),
        (2, [], 0, False, [], []),
    )
    # L hyperarete d une AUTRE page garde un membre que le lot vient de retirer : la fusion le
    # retire avant d ecrire l extrait, et le dit.
    d_ailleurs = [{"id": "h_voisine", "nodes": ["dev_docs_page_ancien", "noeud_parti"]}]
    verifie(
        "`fusionne` retire un membre pendant avant d ecrire l extrait, et le declare",
        lambda: par_fusionne(sain, hyperaretes=d_ailleurs),
        (
            0,
            [("ponts", [["dev_docs_page_ancien"]]), "reconstruction"],
            2,
            False,
            [page],
            [
                sans_perte,
                aucun_fondu,
                "HYPERARETES | dans le graphe=1 | membres pendants retires=1 | amputees=['h_voisine']",
            ],
        ),
    )
    # ⟨un enonce fondu dans un autre, #5936⟩ le moteur dedoublonne par libelle : un enonce du lot
    # qui porte le libelle d un enonce d une AUTRE page disparait, sous « perdus du fait des
    # lots=0 ». Le verdict ne comptait que la structure. La fusion le nomme, et ne refuse pas.
    d_une_autre_page = {
        "id": "autre_page_enonce",
        "label": "Un concept neuf",
        "source_file": "autre.md",
        "_origin": "semantic",
    }
    sans_rapport = {**d_une_autre_page, "id": "autre_page_sans_rapport", "label": "Sans rapport"}
    verifie(
        "`fusionne` nomme l enonce fondu dans un autre et celui qui reste, sans refuser",
        lambda: par_fusionne(
            sain, enonces_d_avant=[d_une_autre_page], fondus=["dev_docs_page_neuf"]
        ),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            [
                sans_perte,
                "absents a vide=0 | fondus du fait des lots=1",
                "  fondu : dev_docs_page_neuf -> autre_page_enonce",
                aucune_hyper,
            ],
        ),
    )
    de_la_page = {**d_une_autre_page, "id": "dev_docs_page_ancien", "source_file": page}
    declare = {
        "nodes": [noeud("dev_docs_page_neuf", "Un concept neuf")],
        "edges": sain["edges"],
        "laches": ["dev_docs_page_ancien"],
    }
    verifie(
        "un enonce de la page que le lot lache en le declarant n est pas compte comme fondu",
        lambda: par_fusionne(declare, enonces_d_avant=[de_la_page]),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            [sans_perte, aucun_fondu, aucune_hyper],
        ),
    )
    verifie(
        "`fusionne` n impute pas au lot un enonce d avant que la fusion a vide perd deja",
        lambda: par_fusionne(
            sain,
            enonces_d_avant=[d_une_autre_page, sans_rapport],
            enonces_perdus_a_vide=["autre_page_sans_rapport"],
        ),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            [sans_perte, "absents a vide=1 | fondus du fait des lots=0", aucune_hyper],
        ),
    )
    verifie(
        "un enonce d avant d une autre page qui disparait du fait du lot est nomme lui aussi",
        lambda: par_fusionne(
            sain, enonces_d_avant=[sans_rapport], fondus=["autre_page_sans_rapport"]
        ),
        (
            0,
            [("ponts", []), "reconstruction"],
            2,
            False,
            [page],
            [
                sans_perte,
                "absents a vide=0 | fondus du fait des lots=1",
                "  fondu : autre_page_sans_rapport -> aucun enonce de meme libelle",
                aucune_hyper,
            ],
        ),
    )

    # ⟨le perimetre de l ADR 5790⟩ ecrit ici chemin par chemin, pour que la regle ne se juge pas
    # elle-meme. Les deux pieges d enumeration y sont : la page a la racine d un dossier, que le
    # `**` d un pathspec git rate, et celles de `.github/`, hors couche.
    for chemin, attendu in (
        ("README.md", True),
        ("CHANGELOG.md", False),
        ("pom.xml", False),
        ("dev-docs/a-la-racine.md", True),
        ("dev-docs/sous/profonde.md", True),
        ("brief/docs/Une page avec des espaces.md", True),
        ("docs/ecrans/capture.png", False),
        (".github/copilot-instructions.md", False),
        ("openspec/specs/une-spec.md", False),
        ("scripts/adr/critere-de-fin.motif.md", True),
        ("scripts/methode/relus.txt", False),
        ("scripts/methode/versions-verifiees.txt", True),
        ("scripts/adr/non-declarees.txt", True),
        ("src/main/resources/fonts/LICENCE.txt", True),
        ("src/test/java/Golden.sortie.approved.txt", False),
    ):
        verifie(
            f"perimetre : `{chemin}` {'entre' if attendu else 'reste dehors'}",
            lambda chemin=chemin: du_perimetre(chemin),
            attendu,
        )

    # ⟨la comparaison, sans git⟩ trois listes, parce que trois gestes.
    etat_note = {"a.md": "1", "b.md": "2", "partie.md": "3"}
    etat_courant = {"a.md": "1", "b.md": "9", "neuve.md": "4"}
    verifie(
        "une empreinte qui a change rend la page modifiee, une page inconnue neuve, une absente"
        " disparue",
        lambda: a_reextraire(etat_courant, etat_note),
        (["b.md"], ["neuve.md"], ["partie.md"]),
    )
    verifie(
        "deux etats egaux ne rendent rien",
        lambda: a_reextraire(etat_note, etat_note),
        ([], [], []),
    )

    # ⟨le retrait d une couche, #5857⟩ sur un graphe fabrique : la page P s en va, la page Q reste.
    def du_graphe(identifiant: str, origine: str, fichier: str) -> dict:
        return {"id": identifiant, "label": identifiant, "_origin": origine, "source_file": fichier}

    def lien(source: str, cible: str, origine: str, fichier: str) -> dict:
        return {"source": source, "target": cible, "_origin": origine, "source_file": fichier}

    graphe_temoin = {
        "directed": False,
        "nodes": [
            du_graphe("titre_p", "ast", "P.md"),
            du_graphe("concept_p", "semantic", "P.md"),
            du_graphe("concept_q", "semantic", "Q.md"),
            du_graphe("classe", "ast", "Classe.java"),
        ],
        "links": [
            lien("titre_p", "concept_p", "semantic", "P.md"),
            lien("titre_p", "classe", "semantic", "P.md"),
            lien("concept_q", "concept_p", "semantic", "Q.md"),
            lien("classe", "concept_p", "pont", "Classe.java"),
            lien("concept_q", "classe", "semantic", "Q.md"),
            lien("titre_p", "classe", "ast", "P.md"),
        ],
        "hyperedges": [
            {"id": "flux_p", "source_file": "P.md", "nodes": ["concept_p", "classe", "titre_p"]},
            {"id": "flux_q", "source_file": "Q.md", "nodes": ["concept_q", "concept_p", "classe"]},
        ],
    }
    allege, sortis = sans_la_couche_de(graphe_temoin, {"P.md"})
    verifie(
        "le noeud semantique de la page s en va, son titre de structure et l autre page restent",
        lambda: [n["id"] for n in allege["nodes"]],
        ["titre_p", "concept_q", "classe"],
    )
    verifie(
        "partent les aretes de la page et celles qui touchent son noeud, quelle que soit l origine",
        lambda: [(a["source"], a["target"], a["_origin"]) for a in allege["links"]],
        [("concept_q", "classe", "semantic"), ("titre_p", "classe", "ast")],
    )
    verifie(
        "l hyperarete de la page part, celle d une autre page perd le membre retire et reste",
        lambda: [(h["id"], h["nodes"]) for h in allege["hyperedges"]],
        [("flux_q", ["concept_q", "classe"])],
    )
    verifie(
        "le compte dit ce qui est sorti",
        lambda: sortis,
        {"noeuds": 1, "aretes": 4, "hyperaretes": 1},
    )
    verifie(
        "les autres champs du graphe sont rendus tels quels",
        lambda: allege["directed"],
        False,
    )
    verifie(
        "oublier une page qui n a aucune couche ne retire rien",
        lambda: sans_la_couche_de(graphe_temoin, {"Classe.java"})[1],
        {"noeuds": 0, "aretes": 0, "hyperaretes": 0},
    )

    # ⟨la consigne nomme chaque champ que l audit et la fusion lisent, #5857⟩ Sa premiere lecture
    # reelle a montre le manque : quatre agents sur cinq ont du deviner ces cles dans les cas
    # ci-dessus. La liste est ECRITE ici, et confrontee au texte que les agents recoivent.
    consigne = Path(__file__).with_name("consigne-des-agents.md").read_text(encoding="utf-8")

    def section(titre: str) -> str:
        """Le texte d une section de la consigne, de son titre au titre suivant."""
        apres = consigne.split(f"## {titre}\n", 1)
        return apres[1].split("\n## ", 1)[0] if len(apres) == 2 else ""

    # Chaque champ se cherche sur la LIGNE de l objet qui le porte, et non dans la page entiere ni
    # dans le tableau pris en bloc. Les deux formes larges ont ete essayees : un champ cite ailleurs,
    # puis le meme champ nomme sur la ligne d un autre objet, laissaient le cas vert quand on le
    # retirait de sa ligne.
    champs_par_objet = {
        "Nœud": ("id", "label", "file_type", "source_file", "source_location", "_origin",
                 "rationale"),
        "Arête": ("source", "target", "relation", "confidence", "confidence_score", "source_file",
                  "source_location", "_origin"),
        "Hyperarête": ("id", "label", "nodes", "relation", "confidence", "confidence_score",
                       "source_file", "source_location", "_origin"),
    }  # fmt: skip
    lignes_du_tableau = {
        ligne.split("|")[1].strip(): ligne
        for ligne in section("Les champs, exactement").splitlines()
        if ligne.startswith("| ")
    }
    for objet, attendus in champs_par_objet.items():
        verifie(
            f"la consigne nomme chaque champ d un objet « {objet} » sur sa propre ligne",
            lambda objet=objet, attendus=attendus: [
                champ for champ in attendus if f"`{champ}`" not in lignes_du_tableau.get(objet, "")
            ],
            [],
        )
    rendu_attendu = section("Le rendu")
    verifie(
        "la section du rendu nomme ses quatre listes, dont celle des identifiants laches",
        lambda: [
            cle
            for cle in ("nodes", "edges", "hyperedges", "laches")
            if f'"{cle}"' not in rendu_attendu
        ],
        [],
    )
    verifie(
        "une section absente de la consigne ne passe pas pour une section complete",
        lambda: section("Une section qui n existe pas"),
        "",
    )

    # ⟨le critere de fin de #5814⟩ sur un depot temoin, par le point d entree entier.
    with tempfile.TemporaryDirectory() as temporaire:
        depot = Path(temporaire) / "depot"
        racine_de_dossier = "dev-docs/a-la-racine.md"
        profonde = "dev-docs/sous/profonde.md"
        hors_perimetre = ".github/hors-couche.md"
        suivies = (racine_de_dossier, profonde, hors_perimetre, "README.md", "CHANGELOG.md")

        def ecris_la_page(chemin: str, texte: str) -> None:
            fichier = depot / chemin
            fichier.parent.mkdir(parents=True, exist_ok=True)
            fichier.write_text(texte, encoding="utf-8")

        def commets() -> None:
            _git(depot, "add", "-A")
            identite = ("-c", "user.name=temoin", "-c", "user.email=temoin@exemple.invalid")
            _git(depot, *identite, "-c", "commit.gpgsign=false", "commit", "-q", "-m", "temoin")

        def joue(*arguments: str) -> tuple[int, list[str]]:
            """Le code de sortie, et les pages rendues : une ligne `raison<TAB>chemin` chacune."""
            sortie = io.StringIO()
            with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
                code = main(["couche_semantique.py", *arguments, "--racine", str(depot)])
            return code, [ligne for ligne in sortie.getvalue().splitlines() if "\t" in ligne]

        def dit(*arguments: str) -> str:
            """Ce que l outil ecrit hors de la liste : sa ligne de compte, ou son refus."""
            sortie, erreur = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(erreur):
                main(["couche_semantique.py", *arguments, "--racine", str(depot)])
            lignes = [ligne for ligne in sortie.getvalue().splitlines() if "\t" not in ligne]
            return " ".join(lignes + erreur.getvalue().splitlines())

        for chemin in suivies:
            ecris_la_page(chemin, "avant\n")
        _git(depot, "init", "-q")
        commets()
        # Non suivie, comme le `node_modules` que le crochet pose par worktree : un glob la
        # compterait, et elle sortirait « neuve » a chaque passe.
        ecris_la_page("docs/node_modules/paquet/LISEZMOI.md", "un paquet tiers\n")

        verifie(
            "sans graphe, `a-reextraire` refuse en 2 au lieu de rendre une liste vide",
            lambda: joue("a-reextraire"),
            (2, []),
        )
        verifie(
            "et ce refus nomme le graphe qui manque, pas le registre",
            lambda: "aucun graphe" in dit("a-reextraire"),
            True,
        )
        (depot / "graphify-out").mkdir()
        _ecris(depot / "graphify-out" / "graph.json", {"nodes": []})
        verifie(
            "sans empreinte notee, il refuse en 2 lui aussi",
            lambda: joue("a-reextraire"),
            (2, []),
        )
        verifie(
            "`note` refuse une page hors du perimetre",
            lambda: joue("note", hors_perimetre),
            (2, []),
        )
        verifie(
            "`note --perimetre` amorce le registre", lambda: joue("note", "--perimetre"), (0, [])
        )
        verifie(
            "le registre ne porte que les pages SUIVIES du perimetre",
            lambda: sorted(_lis(depot / "graphify-out" / REGISTRE)["pages"]),
            ["README.md", racine_de_dossier, profonde],
        )
        verifie(
            "rien n a change : la liste est vide, et cette fois elle le prouve",
            lambda: joue("a-reextraire"),
            (0, []),
        )
        verifie(
            "la ligne de compte porte l arbre compare, sans marque quand il est propre",
            lambda: (
                dit("a-reextraire").endswith("arbre=" + arbre_compare(depot))
                and "+modifs" not in dit("a-reextraire")
            ),
            True,
        )
        for chemin in (racine_de_dossier, profonde, hors_perimetre):
            ecris_la_page(chemin, "apres\n")
        verifie(
            "et elle marque l arbre quand des pages suivies y sont modifiees sans etre commises",
            lambda: dit("a-reextraire").endswith("+modifs"),
            True,
        )
        verifie(
            "elle compte ce qu elle rend",
            lambda: (
                "perimetre=3 | notees=3 | modifiees=2 | neuves=0 | disparues=0"
                in dit("a-reextraire")
            ),
            True,
        )
        verifie(
            "les deux pages du perimetre modifiees sont rendues, celle hors perimetre ne l est pas",
            lambda: joue("a-reextraire"),
            (0, [f"modifiee\t{racine_de_dossier}", f"modifiee\t{profonde}"]),
        )
        verifie(
            "`note --commit` note l etat COMMIS : la page modifiee depuis reste a reextraire",
            lambda: (joue("note", "--commit", "HEAD", profonde)[0], joue("a-reextraire")[1]),
            (0, [f"modifiee\t{racine_de_dossier}", f"modifiee\t{profonde}"]),
        )
        verifie(
            "`note` sans commit note l arbre de travail : la page sort de la liste",
            lambda: (joue("note", profonde)[0], joue("a-reextraire")[1]),
            (0, [f"modifiee\t{racine_de_dossier}"]),
        )
        ecris_la_page("docs/neuve.md", "une page neuve\n")
        _git(depot, "add", "docs/neuve.md")
        _git(depot, "rm", "-q", "-f", "README.md")
        verifie(
            "une page neuve du perimetre et une page disparue sont rendues, chacune a son nom",
            lambda: joue("a-reextraire")[1],
            [f"modifiee\t{racine_de_dossier}", "neuve\tdocs/neuve.md", "disparue\tREADME.md"],
        )

        # `decoupe --a-reextraire` prend ces pages sans passer par le shell, qui couperait un
        # chemin a ses espaces. Et il releve l empreinte au moment ou la lecture commence.
        lots = Path(temporaire) / "lots"
        verifie(
            "`decoupe --a-reextraire` decoupe les pages modifiees et neuves, pas les disparues",
            lambda: (
                joue("decoupe", "--a-reextraire", "--dossier", str(lots))[0],
                _lis(lots / "plan.json")["lots"],
            ),
            (0, [[racine_de_dossier, "docs/neuve.md"]]),
        )
        ecris_la_page(racine_de_dossier, "modifiee pendant que les agents lisaient\n")
        verifie(
            "une page modifiee PENDANT l extraction reste a reextraire apres la note de fusion",
            lambda: (
                note(depot / "graphify-out" / "graph.json", _lis(lots / "plan.json")["empreintes"]),
                joue("a-reextraire")[1],
            ),
            (4, [f"modifiee\t{racine_de_dossier}", "disparue\tREADME.md"]),
        )

        # ⟨`oublie`, #5857⟩ la page disparue quitte le graphe et le registre, et sort de la liste.
        graphe_du_depot = depot / "graphify-out" / "graph.json"
        avec_raison = {
            **du_graphe("concept_profond", "semantic", profonde),
            "rationale": "parce que",
        }
        _ecris(
            graphe_du_depot,
            {
                "nodes": [
                    du_graphe("concept_lisezmoi", "semantic", "README.md"),
                    avec_raison,
                    du_graphe("concept_sans_raison", "semantic", profonde),
                ],
                "links": [],
                "hyperedges": [],
            },
        )
        verifie(
            "`oublie` refuse en 2 une page encore dans le perimetre : elle se reextrait",
            lambda: joue("oublie", profonde),
            (2, []),
        )
        verifie(
            "`oublie` dit ce qu il retire : un noeud et une empreinte pour la page disparue",
            lambda: (
                "noeuds=1 | aretes=0 | hyperaretes=0 | empreintes=1" in dit("oublie", "README.md")
            ),
            True,
        )
        verifie(
            "il n a rien retire de la couche des autres pages",
            lambda: [n["id"] for n in _lis(graphe_du_depot)["nodes"]],
            ["concept_profond", "concept_sans_raison"],
        )
        verifie(
            "l empreinte a quitte le registre : la page ne sort plus, ni modifiee ni disparue",
            lambda: joue("a-reextraire")[1],
            [f"modifiee\t{racine_de_dossier}"],
        )
        verifie(
            "rejoue, `oublie` ne retire plus rien et sort en 0",
            lambda: (
                joue("oublie", "README.md")[0],
                "noeuds=0 | aretes=0 | hyperaretes=0 | empreintes=0" in dit("oublie", "README.md"),
            ),
            (0, True),
        )

        # ⟨la fiche d un lot porte la justification d avant, #5857⟩
        fiches = Path(temporaire) / "fiches"
        verifie(
            "la fiche rend la justification d un noeud existant qui en porte une, et pas de cle"
            " vide pour celui qui n en a pas",
            lambda: (
                joue("decoupe", "--dossier", str(fiches), profonde)[0],
                _lis(fiches / "lot_01.json")[profonde]["semantique"],
            ),
            (
                0,
                [
                    {"id": "concept_profond", "label": "concept_profond", "rationale": "parce que"},
                    {"id": "concept_sans_raison", "label": "concept_sans_raison"},
                ],
            ),
        )

        # ⟨la fiche nomme les hyperaretes de la page, #5904⟩ avec leurs membres, et ceux qui
        # vivent sur une autre page. L hyperarete d une autre page n y entre pas.
        _ecris(
            graphe_du_depot,
            {
                "nodes": [
                    {**avec_raison, "file_type": "rationale"},
                    du_graphe("concept_voisin", "semantic", racine_de_dossier),
                    {
                        **du_graphe("une_classe", "ast", "src/UneClasse.java"),
                        "_callable_class": True,
                    },
                    du_graphe("titre", "ast", profonde),
                ],
                "links": [
                    {
                        **lien("concept_profond", "une_classe", "semantic", profonde),
                        "relation": "references",
                        "confidence": "EXTRACTED",
                    },
                    lien("concept_voisin", "concept_profond", "semantic", racine_de_dossier),
                    lien("titre", "concept_profond", "ast", profonde),
                    {
                        **lien("concept_profond", "concept_voisin", "semantic", profonde),
                        "relation": "conceptually_related_to",
                    },
                ],
                "hyperedges": [
                    {
                        "id": "profonde_hyper",
                        "label": "Un ensemble",
                        "members": ["concept_profond", "concept_voisin"],
                        "source_file": profonde,
                        "confidence": "INFERRED",
                        "confidence_score": 0.85,
                    },
                    {"id": "h_d_ailleurs", "nodes": ["concept_voisin"], "source_file": "README.md"},
                ],
            },
        )
        fiches_tenues = Path(temporaire) / "fiches-tenues"
        verifie(
            "la fiche nomme l hyperarete de la page, ses membres, et ceux d une autre page",
            lambda: (
                joue("decoupe", "--dossier", str(fiches_tenues), profonde)[0],
                _lis(fiches_tenues / "lot_01.json")[profonde]["hyperaretes"],
            ),
            (
                0,
                [
                    {
                        "id": "profonde_hyper",
                        "label": "Un ensemble",
                        "nodes": ["concept_profond", "concept_voisin"],
                        "ailleurs": ["concept_voisin"],
                        "confidence": "INFERRED",
                        "confidence_score": 0.85,
                    }
                ],
            ),
        )
        # Le type de l ancien noeud et les aretes d avant de la page, pour comparer : ni l arete
        # d une autre page, ni une arete de structure.
        verifie(
            "la fiche porte le type des anciens noeuds et les aretes semantiques d avant de la page",
            lambda: (
                _lis(fiches_tenues / "lot_01.json")[profonde]["semantique"],
                _lis(fiches_tenues / "lot_01.json")[profonde]["aretes"],
            ),
            (
                [
                    {
                        "id": "concept_profond",
                        "label": "concept_profond",
                        "file_type": "rationale",
                        "rationale": "parce que",
                    }
                ],
                [
                    {
                        "source": "concept_profond",
                        "target": "une_classe",
                        "relation": "references",
                        "confidence": "EXTRACTED",
                    },
                    {
                        "source": "concept_profond",
                        "target": "concept_voisin",
                        "relation": "conceptually_related_to",
                        "ailleurs": ["concept_voisin"],
                    },
                ],
            ),
        )
        # L autre page est dans la passe : son lecteur reemettra l enonce ou le declarera, et
        # c est par ce que les autres lots emettent que l audit admet l arete. Rien a nommer ici.
        deux_pages = Path(temporaire) / "fiches-deux-pages"
        verifie(
            "une extremite sur une page de la MEME passe n est pas rangee sous `ailleurs`",
            lambda: (
                joue("decoupe", "--dossier", str(deux_pages), profonde, racine_de_dossier)[0],
                [
                    arete_lue.get("ailleurs")
                    for numero in (1, 2)
                    if (deux_pages / f"lot_{numero:02d}.json").is_file()
                    for arete_lue in _lis(deux_pages / f"lot_{numero:02d}.json")
                    .get(profonde, {})
                    .get("aretes", [])
                ],
            ),
            (0, [None, None]),
        )

        def empreintes_de_la_fiche() -> tuple[bool, bool, bool]:
            """La courante vaut celle de git, la notee celle du registre, et elles different."""
            fiche_lue = _lis(fiches_tenues / "lot_01.json")[racine_de_dossier]["empreintes"]
            de_git = subprocess.run(
                ["git", "-C", str(depot), "hash-object", racine_de_dossier],
                capture_output=True,
                text=True,
                check=True,
            ).stdout.strip()
            au_registre = _lis(depot / "graphify-out" / REGISTRE)["pages"][racine_de_dossier]
            return (
                fiche_lue["courante"] == de_git,
                fiche_lue["notee"] == au_registre,
                fiche_lue["courante"] != fiche_lue["notee"],
            )

        verifie(
            "la fiche d une page modifiee porte l empreinte notee et celle d aujourd hui",
            lambda: (
                joue("decoupe", "--dossier", str(fiches_tenues), racine_de_dossier)[0],
                empreintes_de_la_fiche(),
            ),
            (0, (True, True, True)),
        )

    # ⟨une question en langage courant, #5939⟩ le moteur part des libelles qui ressemblent aux
    # mots de la question, et un symbole de code homonyme capte le depart : « pourquoi le depot se
    # fait en WAV par defaut plutot qu en ZIP » ne rendait que du code, alors que la couche portait
    # plus de trente enonces sur le sujet. `cherche` lit les ENONCES, libelle et justification.
    def enonce(
        identifiant: str, libelle: str, justification: str = "", fichier: str = "a.md"
    ) -> dict:
        return {
            "id": identifiant,
            "label": libelle,
            "rationale": justification,
            "source_file": fichier,
            "_origin": "semantic",
        }

    constante = {"id": "wav", "label": "WAV", "source_file": "TypeDepot.java", "_origin": "ast"}
    par_defaut = enonce(
        "e_par_defaut",
        "Sans réglage, le dépôt part en séquences WAV",
        "Le retour du porteur : la compression coûte du temps et du disque.",
    )
    forme = enonce(
        "e_forme", "Deux formes au choix", "Le ZIP n'est plus le défaut depuis la décision 5677."
    )
    relancable = enonce(
        "e_relancable", "Une participation relançable", "Chaque son du dépôt reste en ligne."
    )
    bavard = enonce(
        "e_bavard",
        "Un plancher se remesure",
        "Il se remesure après chaque rebase, même quand il ne conflicte pas, parce que deux"
        " branches peuvent poser la même valeur et masquer le conflit qui les oppose.",
    )
    court = enonce("e_court", "Autre règle", "Un plancher protège.")

    def cherche(question: str, noeuds: list[dict], *options: str) -> tuple[int, str, list[str]]:
        """Le code, la ligne de verdict, et les identifiants rendus, dans l ordre."""
        sortie = io.StringIO()
        with tempfile.TemporaryDirectory() as temporaire:
            graphe_cherche = Path(temporaire) / "graph.json"
            _ecris(graphe_cherche, {"nodes": noeuds, "links": []})
            with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
                code = main(
                    ["couche_semantique.py", "cherche", "--graphe", str(graphe_cherche), *options]
                    + [question]
                )
        lignes = sortie.getvalue().splitlines()
        verdict = next((ligne for ligne in lignes if ligne.startswith("CHERCHE |")), "")
        return code, verdict, [ligne[8:] for ligne in lignes if ligne.startswith("   id : ")]

    corpus = [constante, par_defaut, forme, relancable, bavard, court]
    verifie(
        "une question dont un symbole de code porte le mot rend l enonce de prose, pas le symbole",
        lambda: cherche("pourquoi le dépôt part en WAV", corpus),
        (
            0,
            "CHERCHE | enonces=5 | mots retenus=['depot', 'part', 'wav'] | rendus=2",
            ["e_par_defaut", "e_relancable"],
        ),
    )
    verifie(
        "la justification repond quand le libelle ne porte aucun mot de la question",
        lambda: cherche("pourquoi le ZIP n'est plus le défaut", corpus)[2],
        ["e_forme"],
    )
    verifie(
        "un mot du libelle pese plus que le meme mot dans la justification",
        lambda: cherche("plancher", [bavard, court])[2],
        ["e_bavard", "e_court"],
    )
    long_ = enonce(
        "e_a_long",
        "Une règle",
        "La graine du tirage est fixée, pour que deux passes sur le même graphe rendent la même"
        " partition, et que la comparaison de deux états ne mesure pas le hasard du partitionneur.",
    )
    bref = enonce("e_z_bref", "Une autre règle", "La graine est fixée.")
    verifie(
        "a note egale, l enonce court passe devant le long, quel que soit l ordre des identifiants",
        lambda: cherche("graine", [long_, bref])[2],
        ["e_z_bref", "e_a_long"],
    )
    # Les deux libelles portent chacun UN mot de la question : sans la rarete ils sont a egalite,
    # et c est le plus court, celui du mot courant, qui passerait devant.
    commun = enonce("e_commun", "Le dépôt")
    rare = enonce("e_rare", "La graine du tirage")
    verifie(
        "un mot rare pese plus qu un mot que beaucoup d enonces portent",
        lambda: cherche("dépôt graine", [commun, rare, par_defaut, relancable])[2][:2],
        ["e_rare", "e_commun"],
    )
    verifie(
        "les accents, la casse et le pluriel ne comptent pas",
        lambda: cherche("DÉPÔTS", corpus)[2],
        ["e_par_defaut", "e_relancable"],
    )
    verifie(
        "quand rien ne repond, la ligne le dit et la commande sort en 0",
        lambda: cherche("chiroptère", corpus),
        (0, "CHERCHE | enonces=5 | mots retenus=['chiropt'] | rendus=0", []),
    )
    verifie(
        "une question faite de mots vides ne retient aucun mot",
        lambda: cherche("pourquoi est-ce que le la", corpus)[1],
        "CHERCHE | enonces=5 | mots retenus=[] | rendus=0",
    )
    verifie(
        "sur un graphe sans couche, la recherche refuse en 2 au lieu de ne rien rendre",
        lambda: cherche("dépôt", [constante]),
        (2, "", []),
    )

    def dit_la_recherche(*arguments: str, noeuds: list[dict] | None = None) -> tuple[int, str]:
        """Le code, et ce que la commande ecrit apres sa ligne de verdict, ou son refus."""
        sortie, erreur = io.StringIO(), io.StringIO()
        with tempfile.TemporaryDirectory() as temporaire:
            graphe_cherche = Path(temporaire) / "graph.json"
            _ecris(graphe_cherche, {"nodes": noeuds or corpus, "links": []})
            morceaux = [str(graphe_cherche) if a == "LE_GRAPHE" else a for a in arguments]
            with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(erreur):
                code = main(["couche_semantique.py", "cherche", *morceaux])
        suite = [
            ligne for ligne in sortie.getvalue().splitlines() if not ligne.startswith("CHERCHE")
        ]
        return code, " ".join(suite + erreur.getvalue().splitlines())

    verifie(
        "quand rien ne repond, la commande dit que la prose n en dit rien, ou le dit autrement",
        lambda: dit_la_recherche("--graphe", "LE_GRAPHE", "chiroptère"),
        (
            0,
            "Aucun enonce de la couche ne porte ces mots. La prose n en dit rien, ou le dit autrement.",
        ),
    )
    verifie(
        "et quand la question n a que des mots vides, elle le dit aussi",
        lambda: dit_la_recherche("--graphe", "LE_GRAPHE", "pourquoi est-ce que"),
        (0, "Aucun mot de la question n est retenu : elle ne porte que des mots vides."),
    )
    sans_raison = enonce("e_sans_raison", "Graine unique")
    verifie(
        "chaque enonce rendu porte son rang, son libelle, sa justification, sa page et son identifiant",
        lambda: dit_la_recherche("--graphe", "LE_GRAPHE", "participation relançable")[1],
        "1. Une participation relançable    parce que : Chaque son du dépôt reste en ligne."
        "    page : a.md    id : e_relancable",
    )
    verifie(
        "un enonce sans justification n en invente pas",
        lambda: dit_la_recherche("--graphe", "LE_GRAPHE", "unique", noeuds=[sans_raison])[1],
        "1. Graine unique    page : a.md    id : e_sans_raison",
    )
    verifie(
        "sans question, `cherche` refuse en 2",
        lambda: dit_la_recherche("--graphe", "LE_GRAPHE"),
        (2, "REFUS : `cherche` attend une question."),
    )
    verifie(
        "sans graphe a l endroit designe, `cherche` refuse en 2",
        lambda: dit_la_recherche("--graphe", "/nulle/part/graph.json", "dépôt"),
        (2, "REFUS : aucun graphe a /nulle/part/graph.json."),
    )
    verifie(
        "`--nombre` borne ce qui est rendu",
        lambda: cherche("dépôt", corpus, "--nombre", "1")[2],
        ["e_par_defaut"],
    )

    def aide() -> tuple[int, bool, str]:
        sortie, erreur = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(erreur):
            code = main(["couche_semantique.py", "--help"])
        return code, sortie.getvalue().startswith("Usage :"), erreur.getvalue()

    verifie("`--help` imprime l usage et sort en 0", aide, (0, True, ""))

    return echecs()


USAGE = __doc__.split("Usage :")[1].split("\n\n")[0]
COMMANDES = ("a-reextraire", "cherche", "decoupe", "audite", "fusionne", "note", "oublie")


def main(argv: list[str]) -> int:
    arguments = argv[1:]
    if "--auto-test" in arguments:
        return auto_test()
    if arguments and arguments[0] in ("-h", "--help"):
        print("Usage :" + USAGE)
        return 0
    if not arguments or arguments[0] not in COMMANDES:
        print("Usage :" + USAGE, file=sys.stderr)
        return 2
    try:
        return _joue(arguments[0], arguments[1:])
    except Refus as refus:
        print(f"REFUS : {refus}", file=sys.stderr)
        return 2


def _joue(commande: str, reste: list[str]) -> int:
    dossier = graphe = commit = None
    nombre = 5
    racine = RACINE
    pages: list[str] = []
    drapeaux: set[str] = set()
    while reste:
        mot = reste.pop(0)
        if mot == "--dossier" and reste:
            dossier = Path(reste.pop(0))
        elif mot == "--graphe" and reste:
            graphe = Path(reste.pop(0))
        elif mot == "--commit" and reste:
            commit = reste.pop(0)
        elif mot == "--nombre" and reste:
            nombre = int(reste.pop(0))
        elif mot == "--racine" and reste:
            # Pour les cas de l auto-test, qui jouent l outil sur un depot temoin.
            racine = Path(reste.pop(0))
        elif mot in ("--a-reextraire", "--perimetre"):
            drapeaux.add(mot)
        else:
            pages.append(mot)
    graphe = graphe or racine / "graphify-out" / "graph.json"
    if commande == "a-reextraire":
        return commande_a_reextraire(graphe, racine)
    if commande == "cherche":
        if not pages:
            raise Refus("`cherche` attend une question.")
        if not graphe.is_file():
            raise Refus(f"aucun graphe a {graphe}.")
        return commande_cherche(graphe, " ".join(pages), nombre)
    if commande == "note":
        if "--perimetre" in drapeaux:
            pages = pages_du_perimetre(racine)
        if not pages:
            raise Refus("`note` attend des pages, ou --perimetre pour toutes celles du perimetre.")
        return commande_note(graphe, racine, pages, commit)
    if commande == "oublie":
        if not pages:
            raise Refus("`oublie` attend au moins une page.")
        return commande_oublie(graphe, racine, pages)
    if dossier is None:
        print("REFUS : --dossier est obligatoire.\nUsage :" + USAGE, file=sys.stderr)
        return 2
    if commande != "decoupe" and not (dossier / "plan.json").is_file():
        # Un dossier que `decoupe` n a pas prepare n est pas un audit vide : c est une erreur
        # d appel, et elle se dit au lieu de se lire dans une trace de pile.
        print(
            f"REFUS : {dossier} ne porte aucun plan.json, lancer `decoupe` d abord.",
            file=sys.stderr,
        )
        return 2
    if commande == "audite":
        return commande_audite(dossier)
    if not graphe.is_file():
        print(f"REFUS : aucun graphe a {graphe}.", file=sys.stderr)
        return 2
    if commande == "decoupe":
        if "--a-reextraire" in drapeaux:
            modifiees, neuves, _ = a_reextraire(
                empreintes(racine, pages_du_perimetre(racine)), notees_de(graphe)
            )
            pages = modifiees + neuves
        return commande_decoupe(dossier, graphe, pages, racine)
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    return commande_fusionne(dossier, graphe)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
