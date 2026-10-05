#!/usr/bin/env python3
"""La porte d entree unique : ce que CE diff engage, et rien de plus (#5340).

    python3 scripts/batterie.py                # ce que le diff contre `main` engage
    python3 scripts/batterie.py --lance        # les joue TOUS, et rend tous les refus
    python3 scripts/batterie.py --contre HEAD~1
    python3 scripts/batterie.py --auto-test

## Le besoin, dans les termes du probleme

Un agent qui a fini ne sait pas quoi lancer. Il a deux options : tout lancer, ce qui coute une
cinquantaine de minutes, ou deviner. Il devine, et le banc qu il se compose est tantot
disproportionne, tantot insuffisant.

C est la MEME question que la portee CI du chantier #5294, vue de l autre cote : *quels controles ce
diff engage-t-il ?* Une seule derivation doit y repondre, sinon les deux divergent.

## Le repli, qui est la moitie du dispositif

> **Un garde qui ne declare pas ses `chemins` est LANCE.**

Le defaut penche du cote couteux, jamais du cote muet. C est le meme parti que la portee CI, qui
verifie tout quand la base de comparaison manque.

Cette porte est donc JUSTE des le premier jour, avec neuf gardes declarants sur soixante et onze :
elle lance trop, jamais trop peu. Le cliquet des non-declarants la rend precise par tranches, sans
qu elle passe par un etat ou elle en oublie un.

## Le faux vert qu elle refuse

Une porte qui n engage RIEN rendrait vert en n ayant rien lance. Ce n est pas theorique : le
2026-09-06, un harnais de ce depot a lance `python3` sans argument cent trente et une fois - zsh ne
decoupe pas `$ligne` en mots - et a annonce cent trente et une commandes vertes sans qu aucune n ait
ete jouee. La CI a trouve le rouge que la batterie annoncait absent.

Cette porte DIT donc combien elle a lance, et refuse d en lancer zero sur un diff non vide.

Un fichier neuf hors de l index ouvre le meme silence : la porte voit son chemin, mais les gardes
qui construisent leur population avec `git ls-files` ne le voient pas encore. Elle le nomme et
refuse donc de conclure jusqu a son indexation.

## Ce qu elle ne fait pas

Elle ne remplace pas la CI. `AGENTS.md` le pose : la mesure fait foi en CI, pas sur le poste. Elle
est le PREMIER lecteur, celui qui evite l aller-retour, pas l autorite.
"""

from __future__ import annotations

import collections
import pathlib
import re
import subprocess
import sys

RACINE = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RACINE / "scripts"))
sys.path.insert(0, str(RACINE / "scripts" / "methode"))

CONTRAT = {
    "geste": "commandes de la batterie locale qu un diff engage, et celles qu il n engage pas",
    "population": "les gardes de scripts/ qui declarent un CONTRAT",
    # `rapport` et non un dispositif invente : le vocabulaire est ferme, declare une seule fois dans
    # `verifie_contrats_tiennent.DISPOSITIFS` (ADR 5125). Cette porte RELEVE ce qu un diff engage et
    # delegue le jugement aux gardes ; elle ne juge rien elle-meme.
    "dispositif": "rapport",
    "seuil": "(sans objet)",
    "temoin": "scripts/batterie.py --auto-test",
    "decision": "chantier #5294, lot #5340",
    "chemins": """
scripts/**
""",
}


# ⟨les gardes qui ne sont pas en Python⟩ Ce depot teste sa documentation comme du code : des classes
# Java lisent des `.md` et refusent quand ils derivent. La porte les ignorait, et c est ce qui a coute
# un aller-retour en #5356 - un `enforced_by` mal forme, vu par `build` et par rien d autre.
#
# ⟨pourquoi une liste, alors que l ADR 3450 les refuse⟩ Parce que `gardes-java-declares.py` la TIENT :
# la population se derive de l arbre - toute classe de test qui CONSTRUIT un chemin vers de la prose -
# et cette declaration lui est confrontee. Une sixieme classe qui lirait un `.md` sans etre ici fait
# rougir. Une liste qu un garde confronte n est plus une liste, c est un inventaire.
#
# Le cout est ce qui rend l omission chere : `DocumentationAJourTest` met 2,4 s pour vingt et un
# invariants, contre huit minutes de `build` entier.
GARDES_JAVA: dict[str, str] = {
    "DocumentationAJourTest": """
dev-docs/**
docs/**
brief/**
mkdocs.yml
mkdocs-dev.yml
mkdocs-brief.yml
README.md
CONTRIBUTING.md
TESTING.md
REMERCIEMENTS.md
.github/workflows/**
src/test/bats/**
src/main/resources/db/migration/**
""",
    "NomDeLApplicationTest": """
docs/**
mkdocs.yml
""",
    "CorrespondanceRecetteTest": """
dev-docs/recette/**
""",
    "PageDesClipsTest": """
dev-docs/recette/**
""",
    "InventaireDesSessionsTest": """
dev-docs/recette/**
""",
}


def _git(*arguments: str, racine: pathlib.Path | None = None) -> str:
    sortie = subprocess.run(
        ["git", "-C", str(racine or RACINE), *arguments],
        capture_output=True,
        text=True,
        check=False,
    )
    return sortie.stdout if sortie.returncode == 0 else ""


def fichiers_non_suivis(racine: pathlib.Path | None = None) -> list[str]:
    """Les fichiers non ignores que les populations fondees sur `git ls-files` ne voient pas."""
    return sorted(_git("ls-files", "--others", "--exclude-standard", racine=racine).splitlines())


def fichiers_du_diff(contre: str = "origin/main", racine: pathlib.Path | None = None) -> list[str]:
    """Ce que ce diff touche, suivi ET non suivi.

    Les fichiers NEUFS comptent : un garde qu on vient d ecrire n est pas encore suivi, et l oublier
    ferait taire exactement la porte qui devait le juger.
    """
    modifies = _git("diff", "--name-only", contre, racine=racine).splitlines()
    neufs = fichiers_non_suivis(racine)
    en_cours = _git("diff", "--name-only", racine=racine).splitlines()
    return sorted({f for f in modifies + neufs + en_cours if f})


def correspond(chemin: str, motif: str) -> bool:
    """`**` traverse les `/`, `*` ne les traverse pas. Meme regle que la portee CI."""
    morceaux, i = [], 0
    while i < len(motif):
        if motif.startswith("**/", i):
            morceaux.append("(?:.*/)?")
            i += 3
        elif motif.startswith("**", i):
            morceaux.append(".*")
            i += 2
        elif motif[i] == "*":
            morceaux.append("[^/]*")
            i += 1
        else:
            morceaux.append(re.escape(motif[i]))
            i += 1
    return re.fullmatch("".join(morceaux), chemin) is not None


# Les dispositifs qui JUGENT, et peuvent donc faire rougir la CI. Le vocabulaire est ferme et
# declare une seule fois dans `verifie_contrats_tiennent.DISPOSITIFS` (ADR 5125) ; on ne retient ici
# que la moitie qui refuse.
#
# ⟨pourquoi ce critere, et pas la duree⟩ Une loupe rend `0` par construction, un rapport releve, un
# generateur produit : aucun ne peut causer l aller-retour de CI que cette porte existe pour eviter.
# Les lancer avant de pousser est du temps paye deux fois, pour rien.
#
# Mesure du 2026-09-06, les 72 gardes lances un par un : ceux qui JUGENT coutent 810 s, les autres
# 1123 s. Ecarter ce qui ne juge pas retire donc 58 % du cout SANS PERDRE UN SEUL ROUGE. Le critere
# n est pas choisi, il est deja declare par chaque garde.
JUGENT = frozenset({"cliquet", "plancher", "invariant", "harnais"})


def gardes(racine: pathlib.Path | None = None) -> list[tuple[str, list[str]]]:
    """Les gardes de `scripts/`, avec leurs `chemins` declares - vide quand ils n en declarent pas.

    La lecture se fait par `ast`, sans lancer les gardes : les lancer pour savoir s il faut les
    lancer serait le geste que cette porte existe pour eviter.
    """
    import ast

    base = racine or RACINE
    trouves = []
    # ⟨`.github/scripts` entre par la MEME regle⟩ La porte ne lisait que `scripts/`, donc les gardes
    # de CI n existaient pas pour elle : elle ne pouvait ni les engager ni dire qu elle les ecartait.
    # Le critere ne change pas d un dossier a l autre - c est le `CONTRAT` declare, jamais le chemin
    # du fichier. La CI lance aussi `revoque_jeton.py` et `installer_paquets.py` dans ce dossier : une
    # regle fondee sur le dossier ferait revoquer un jeton depuis un poste (#5525).
    for dossier in ("scripts/adr", "scripts/methode", ".github/scripts"):
        for f in sorted((base / dossier).glob("*.py")):
            if f.name.startswith("_"):
                continue
            try:
                arbre = ast.parse(f.read_text(encoding="utf-8", errors="ignore"))
            except SyntaxError as erreur:
                # ⟨on ne saute PAS un garde illisible⟩ Ce `continue` a existe, et il a coute : le
                # 2026-09-06, une insertion fautive a casse `verifie-dependances-declarees.py`, et la
                # porte l a fait DISPARAITRE du corpus sans un mot. Le compte restait plausible, le
                # garde n etait plus lance, et rien ne le disait.
                #
                # C est exactement le silence que le chantier #5294 combat. Un garde qu on ne sait pas
                # lire est donc ENGAGE, avec sa raison : le defaut penche du cote couteux.
                print(
                    f"⚠ {f.name} est illisible ({erreur.__class__.__name__}) : il est ENGAGE par "
                    "defaut, faute de savoir ce qu il declare.",
                    file=sys.stderr,
                )
                trouves.append((f"scripts/{dossier}/{f.name}", []))
                continue
            declares: list[str] = []
            dispositif = None
            porte_un_contrat = False
            for noeud in ast.walk(arbre):
                if not isinstance(noeud, ast.Assign):
                    continue
                for cible in noeud.targets:
                    if isinstance(cible, ast.Name) and cible.id == "CONTRAT":
                        porte_un_contrat = True
                        if isinstance(noeud.value, ast.Dict):
                            for cle, valeur in zip(noeud.value.keys, noeud.value.values):
                                if (
                                    isinstance(cle, ast.Constant)
                                    and cle.value == "chemins"
                                    and isinstance(valeur, ast.Constant)
                                ):
                                    declares = [
                                        l.strip()
                                        for l in str(valeur.value).splitlines()
                                        if l.strip()
                                    ]
                                if (
                                    isinstance(cle, ast.Constant)
                                    and cle.value == "dispositif"
                                    and isinstance(valeur, ast.Constant)
                                ):
                                    dispositif = valeur.value
            if porte_un_contrat and dispositif in JUGENT:
                trouves.append((f"{dossier}/{f.name}", declares))
    return trouves


ATELIERS = ".github/workflows"

# Une invocation d atelier : `python3 <script>.py <arguments>`, ou `ruff <arguments>`. Les deux
# formes se lisent ligne a ligne plutot qu en analysant le YAML : un `run:` est un bloc de shell,
# et l analyser vraiment demanderait un shell.
# Le nom de ce fichier. Les trois bancs de mutation portent chacun le sien, et pour la meme raison :
# un dispositif qui derive sa population des ateliers s y trouve lui-meme, et se relance a l interieur
# de sa propre execution. Mesure du 2026-09-29 : 6,32 s, et un rouge qui n est pas celui du diff.
MOI = pathlib.Path(__file__).name

INVOCATION_PY = re.compile(r"python3?\s+((?:scripts|\.github/scripts)/[\w./-]+\.py)([^\n|&;]*)")
INVOCATION_RUFF = re.compile(r"(?:^|\s)(ruff\s+[\w-]+(?:\s+--check)?)\s+([\w./ -]+)$")

# Une forme utilisable est faite de DRAPEAUX, et de rien d autre. Les ateliers passent aussi des
# chemins, des variables et des redirections - `"${CORPS}"`, `>> "$GITHUB_STEP_SUMMARY"` - qui ne
# veulent rien dire hors de la CI. La porte ne les rejoue pas : elle lance nu, comme avant.
DRAPEAUX_SEULS = re.compile(r"^--?[\w-]+(?: --?[\w-]+)*$")


def _lignes_des_ateliers(racine: pathlib.Path | None = None) -> list[str]:
    base = (racine or RACINE) / ATELIERS
    lignes: list[str] = []
    for atelier in sorted(base.glob("*.yml")) if base.is_dir() else []:
        lignes += atelier.read_text(encoding="utf-8").splitlines()
    return lignes


def arguments_des_ateliers(racine: pathlib.Path | None = None) -> dict[str, list[str]]:
    """Les arguments avec lesquels les ATELIERS lancent chaque garde.

    **Pourquoi derive, et non ecrit ici.** La porte lancait chaque garde NU, et la CI en lance dix
    avec `--verifie`. Pour plusieurs d entre eux, le mode nu ECRIT au lieu de juger : sur une ADR
    modifiee, `matrice-constitution.py --verifie` rend 1 quand le meme garde nu rend 0 et reecrit
    `CONSTITUTION.md`. La porte rendait donc vert en rendant le depot conforme, au lieu de constater
    qu il l etait (#5481).

    Une liste ecrite ici se perimerait au premier garde qui gagne un mode. L atelier, lui, est la
    reference : c est lui qui decide du rouge que la porte existe pour anticiper.

    **Cette derivation est INDEXEE SUR LA CI, donc muette la ou la CI est muette.** L ADR 5157 a
    rejete une regle indexee sur la CI pour choisir le `dispositif` d un garde, et sa raison vaut
    ici : un outil qu aucun atelier ne nomme garde un comportement par defaut que rien ne declare.
    Mesure du 2026-09-25 : vingt-quatre gardes du corpus ne sont dans aucun atelier, et AUCUN d eux
    ne porte de mode `--verifie` ou `--ecrire`. Le trou est donc vide aujourd hui, et rien ne le
    maintient vide : le premier garde hors atelier qui gagnera un mode sera lance dans le mauvais.

    **Une forme, et une seule.** Un garde que les ateliers lancent tantot nu, tantot avec des
    arguments, reste lance nu : la porte ne choisit pas a la place de l atelier. `compte-les-reliquats.py`
    est dans ce cas, et `EXIGENT_DES_ARGUMENTS` dit deja qu il ne juge rien lance nu.
    """
    formes: dict[str, set[str]] = collections.defaultdict(set)
    for ligne in _lignes_des_ateliers(racine):
        for trouve in INVOCATION_PY.finditer(ligne):
            arguments = trouve.group(2).split("#")[0].strip()
            if "--auto-test" in arguments:
                continue  # L auto-test du garde n est pas son emploi.
            formes[trouve.group(1)].add(arguments)
    return {
        garde: next(iter(f)).split()
        for garde, f in formes.items()
        if len(f) == 1 and DRAPEAUX_SEULS.match(next(iter(f)))
    }


def auto_tests_des_ateliers(racine: pathlib.Path | None = None) -> list[str]:
    """Les scripts qu un atelier lance en `--auto-test` et que la population de la porte ignore.

    ## Le trou, et pourquoi aucune des cinq exclusions n etait fautive

    `gardes()` ne balaie que `scripts/adr` et `scripts/methode`, et c est sa regle ecrite. Les trois
    bancs de mutation declarent la leur. Un script sans `CONTRAT` sort du banc de methode par le
    filtre qui le dit. **Aucune de ces regles n est fautive prise isolement** : c est leur conjonction
    qui laissait quatre auto-tests sans autre lecteur que la CI, et aucune des cinq n avait de raison
    de le voir (#5555).

    ## Le critere est le MODE, ni la liste ni le contrat

    Mesure du 2026-09-29 sur les vingt ateliers : **53** invocations `python3 scripts/...` distinctes,
    dont **48** declarent un `CONTRAT` et sont deja jouees par la porte. Des cinq restantes, quatre
    sont lancees en `--auto-test` et une en `--markdown`.

    Les quatre tournent en local, vertes, en **0,05 s chacune**. La cinquieme,
    `qualite/rapport_mutation.py --markdown`, sort en **1** en reclamant un passage de PIT : elle n a
    aucun sens hors de la CI, et elle s exclut **d elle-meme** par son mode.

    Un auto-test est autonome par construction - c est ce que ce depot exige de lui - donc le jouer
    localement est sur. Le risque que l issue nommait, « rejouer en local ce qui n a de sens qu en
    CI », est donc **retire** par le critere plutot qu assume : c est la difference entre deriver de
    ce que la chose FAIT et declarer une liste (ADR 5452).
    """
    deja = {nom for nom, _ in gardes(racine)}
    vus: dict[str, None] = {}
    for ligne in _lignes_des_ateliers(racine):
        for trouve in INVOCATION_PY.finditer(ligne):
            chemin, arguments = trouve.group(1), trouve.group(2).split("#")[0].strip()
            # `.github/scripts` est le terrain de #5525, et il a ses propres raisons d etre hors de
            # cette porte : on ne l elargit pas ici sans l avoir mesure la-bas.
            if not chemin.startswith("scripts/"):
                continue
            # ⟨la porte ne se joue pas ELLE-MEME⟩ Sans cette ligne, son auto-test se relance a
            # l interieur de sa propre execution : 6,32 s, et un rouge qui n est pas celui du diff.
            # C est la bombe a fork que les trois bancs evitent chacun avec sa constante `MOI`.
            if chemin.endswith(MOI):
                continue
            if "--auto-test" not in arguments or chemin in deja:
                continue
            vus[chemin] = None
    return sorted(vus)


def outils_des_ateliers(racine: pathlib.Path | None = None) -> list[tuple[str, list[str]]]:
    """Les outils NON PYTHON que les ateliers lancent, avec LEURS dossiers.

    `ruff` seul ici, et derive pour la meme raison que les arguments : recopier ses quatre dossiers
    les ferait diverger de ceux de `lint.yml` sans que rien ne le dise. Mesure du 2026-09-22 :
    0,02 s a froid, sans cache, contre plusieurs dizaines de secondes pour la porte entiere. Le
    conditionner couterait plus cher que de le lancer.

    **Son nom promettait plus que son corps** jusqu a #5555 : « les outils que les ateliers lancent »
    couvrait aussi les scripts Python, dont quatre n avaient pour premier lecteur que la CI. Ceux-la
    sont desormais derives par `auto_tests_des_ateliers`, et ce nom-ci dit ce qu il fait.
    """
    vus: dict[str, list[str]] = {}
    for ligne in _lignes_des_ateliers(racine):
        trouve = INVOCATION_RUFF.search(ligne)
        if trouve:
            vus.setdefault(trouve.group(1), trouve.group(1).split() + trouve.group(2).split())
    return sorted(vus.items())


def engage_java(diff: list[str]) -> list[str]:
    """Les classes Java que ce diff engage, sous la forme que Maven attend.

    Pas de repli « on lance tout » ici, contrairement aux gardes Python : une classe non declaree
    n est pas invisible, elle fait ROUGIR `gardes-java-declares.py`. Le silence est ferme ailleurs.
    """
    engagees = []
    for classe, bloc in GARDES_JAVA.items():
        chemins = [l.strip() for l in bloc.splitlines() if l.strip()]
        if any(correspond(f, m) for f in diff for m in chemins):
            engagees.append(classe)
    return sorted(engagees)


def engage_pmd(diff: list[str]) -> bool:
    """Ce diff demande-t-il un rapport PMD ?

    `4617` REFUSE quand `target/pmd.xml` manque, et ce refus est sa qualite - « ce garde REFUSE
    plutot que de conclure sur ce qu il n a pas lu ». Mais rien ne produisait ce rapport en local :
    il se taisait donc a chaque lot Java, et un refus repete se classe « environnemental ». Il a
    ainsi tu six fois de suite un vrai depassement de seuil le 2026-09-06 (#5405).

    **Seulement si le diff porte du `.java`.** Mesure du 2026-09-07 : 20 s sur un arbre neuf, 9 s a
    chaud. C est peu au regard d une minute de CI, et beaucoup au regard des 6 s que coute le reste
    de la preparation - assez pour qu on ne le paie pas sur un lot de prose.

    **La question posee est « son consommateur est-il engage ? », et non « y a-t-il du Java ? ».** Les
    deux se confondaient tant que `4617` ne declarait aucun `chemins` : il etait alors lance partout,
    donc refuse partout ou le rapport n existait pas (#5465). Une fois ses chemins declares, les deux
    questions divergent sur un cas reel - une demande qui touche le garde LUI-MEME, que la regle de
    #5421 engage quels que soient ses chemins. Deriver la reponse du consommateur ferme ce cas sans
    liste a tenir : le rapport existe exactement quand quelqu un le lit.
    """
    return any(consomme_le_rapport_pmd(g) for g in engage(diff)[0])


def consomme_le_rapport_pmd(garde: str) -> bool:
    """Ce garde LIT-il `target/pmd.xml` ? Fonction PURE, pour le temoin.

    Le consommateur est nomme ICI et nulle part ailleurs. `engage_pmd` decide d en produire le
    rapport, et `renvoi_a_la_preparation` decide d avouer que sa production a echoue : deux
    designations du meme concept divergeraient le jour ou il change de numero (#5850).
    """
    return "4617" in garde


def _cause_possiblement_locale(pmd_a_echoue: bool) -> str:
    """La fin de la phrase qui conclut sur les muets. Fonction PURE, pour le temoin.

    Sans echec de preparation, leur refus est bien etranger au diff : un outil absent, un paquet
    manquant. Avec un echec de preparation, il ne l est PAS, et le dire l etait envoyait chercher du
    cote du poste alors que l arbre ne compilait pas.
    """
    if pmd_a_echoue:
        return " L un d eux attend un rapport que cette porte n a PAS pu produire, et il"
    return " Leur refus ne parle pas de votre diff, et il"


def renvoi_a_la_preparation(garde: str, pmd_a_echoue: bool) -> str:
    """La ligne qui renvoie un garde MUET a l echec de preparation qui l explique, ou `""`.

    ## Le defaut que ceci repare

    Un garde qui refuse faute de `target/pmd.xml` nomme sa cause et prescrit la commande qui produit
    le rapport. Son refus est juste, et il ne peut pas savoir que la porte vient de lancer cette
    commande et qu elle a echoue. Le lecteur, lui, lit le bloc du muet SEUL : il relance donc une
    commande deja tentee, et c est un tour de trop.

    Vecu par une session pair le 2026-10-05, dont l arbre ne compilait pas : une javadoc coupee hors
    de son commentaire par le formateur. Elle a trouve la cause en relancant `./mvnw` sans `-q`, ce
    que le bloc suggerait - donc le chemin fonctionnait, en un tour de trop.

    ## Ce que ce renvoi n est PAS

    La porte DIT deja l echec de sa preparation, a l endroit ou elle le constate. Le pair ne l avait
    pas lu parce qu il filtrait la sortie sur trois motifs dont aucun ne la couvrait : le defaut
    n est donc pas le silence de la porte, mais le fait que le bloc du muet ne renvoie a rien.

    ## Pourquoi la condition porte sur le CONSOMMATEUR

    Un garde muet pour une autre raison - l outil OpenSpec absent, un paquet manquant - n a rien a
    voir avec PMD, et lui coller ce renvoi le rendrait bavard au lieu de juste. C est le second cas
    du critere de fin, et sans lui le remede serait indiscernable d un bavardage.
    """
    if not (pmd_a_echoue and consomme_le_rapport_pmd(garde)):
        return ""
    return (
        "      La PREPARATION de ce rapport a ECHOUE plus haut : la commande ci-dessus a deja ete\n"
        "      tentee par cette porte. Rejouez-la SANS `-q` pour en lire la cause, qui est souvent\n"
        "      que votre arbre ne compile pas."
    )


def engage(diff: list[str], racine: pathlib.Path | None = None) -> tuple[list[str], list[str]]:
    """Ce que ce diff engage, et ce qu il n engage pas. Sans `chemins`, on ENGAGE.

    **Un garde se voit LUI-MEME**, quels que soient ses `chemins`. La demande qui reecrit sa source
    est precisement celle ou l on veut le voir tourner : c est la qu il peut cesser de juger, ou
    juger faux. Ses `chemins` disent ce qu il LIT, pas ce qui le concerne, et neuf gardes du depot
    etaient donc aveugles a leur propre reecriture (#5421).

    **D office, plutot qu une consigne.** « Chaque garde cite sa source dans ses `chemins` » aurait
    tenu sur le papier et cede a l usage. La preuve est une population, pas un cas : au 2026-09-07,
    **neuf** des **treize** gardes qui declarent des `chemins` ne se citaient pas. Le chiffre se
    refait - retenir les gardes dont `chemins` n est pas vide, et voir si l un des motifs couvre le
    fichier par `correspond()`.

    Un garde etroit n a d ailleurs aucune raison legitime de s elargir pour se voir : c est a la porte
    de le savoir, pas a lui de le declarer.

    **Ceci n est pas l inverse de l ADR 5398**, qui refuse qu une EXEMPTION s infere. Cette
    ADR-la ecrit elle-meme pourquoi les deux sens ne se valent pas : une exemption inferee a tort
    produit un faux VERT, invisible et dangereux, tandis qu un engagement de trop produit un faux
    ROUGE, visible et sans danger. Inferer pour ELARGIR le compte va donc dans le sens sur. Le dire
    ici parce qu un lecteur qui trouve « d office » a cote de « ne s infere pas » conclurait
    autrement.
    """
    engages, ecartes = [], []
    for garde, chemins in gardes(racine):
        if not chemins or garde in diff or any(correspond(f, m) for f in diff for m in chemins):
            engages.append(garde)
        else:
            ecartes.append(garde)
    return engages, ecartes


# ⟨la porte se calibre, elle ne se devine pas⟩ Chaque `--lance` releve la duree de ce qu il joue et
# la garde ici. La fois suivante, la porte ANNONCE ce qu elle va couter au lieu de partir pour une
# demi-heure sans le dire - c est ce qu elle a fait deux fois le 2026-09-06, tuee a quinze puis a
# trente minutes.
#
# Un fichier plutot qu une constante, parce qu une constante serait une mesure ecrite une fois puis
# perimee : c est le defaut que l en-tete de `maven.yml` a porte des mois en annoncant 148 s pour un
# harnais qui en mettait 787.
DUREES = RACINE / "target" / "batterie-durees.json"


def durees_connues() -> dict[str, float]:
    import json

    try:
        return json.loads(DUREES.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def annonce_la_duree(engages: list[str]) -> None:
    """Ce que ce passage va couter, d apres ce que le precedent a mesure."""
    connues = durees_connues()
    vues = [connues[g] for g in engages if g in connues]
    if not vues:
        print("     (durée inconnue : ce relevé se construit au premier `--lance`)")
        return
    total = sum(vues) + (len(engages) - len(vues)) * (sum(vues) / len(vues))
    manquants = len(engages) - len(vues)
    apercu = f"     ⏱ environ {total / 60:.0f} min"
    if manquants:
        apercu += f", dont {manquants} garde(s) jamais mesuré(s), estimés à la moyenne"
    print(apercu, flush=True)
    chers = sorted(((connues.get(g, 0), g) for g in engages), reverse=True)[:2]
    for d, g in chers:
        if d > 30:
            print(f"        {d:5.0f} s  {g}", flush=True)


def modules_attendus(racine: pathlib.Path | None = None) -> dict[str, str]:
    """Ce que les gardes IMPORTENT, et la distribution qui le fournit.

    Lu dans `[tool.vigiechiro.modules]` par `prepare-l-environnement.py`, plutot que devine : le nom
    de la distribution n est pas celui du module, et le deviner exigerait d interroger ce qui est
    installe, donc de rendre un verdict qui depend de la machine.
    """
    import importlib.util

    outil = (racine or RACINE) / "scripts" / "methode" / "prepare-l-environnement.py"
    # ⟨une racine qui ne porte pas le declarant ne DECLARE rien⟩ Sans ce retour, un arbre jetable
    # - celui d un cas, celui d un pair - faisait planter la porte avant son premier garde, sur une
    # trace qui ne parle pas de son diff.
    if not outil.exists():
        return {}
    spec = importlib.util.spec_from_file_location("_prep", outil)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.modules_declares(racine or RACINE)


def candidats(racine: pathlib.Path | None = None) -> list[str]:
    """Les interpretes a essayer, DANS L ORDRE, et l ordre porte une decision.

    **Le `.venv` du worktree passe en premier, et jamais un venv partage.** C est la convention posee
    par #5426, dont la raison n est pas le confort : une branche qui change une version epinglee doit
    etre eprouvee contre LA SIENNE, sinon elle l est contre celle d une autre branche. Preferer un
    venv commun parce qu il est complet reintroduirait exactement ce que la convention ecarte.

    Ensuite l interprete qui lance cette porte, puis celui du PATH. Aucun chemin de poste n est ecrit
    ici : trois conventions ont circule en deux jours dans ce depot, et coder l une d elles serait la
    figer au moment ou elle bouge.
    """
    base = racine or RACINE
    return [str(base / ".venv" / "bin" / "python"), sys.executable, "python3"]


def porte_les_modules(interprete: str, modules) -> bool:
    """Cet interprete importe-t-il tout ce que les gardes declarent ?"""
    if not modules:
        return True
    essai = subprocess.run(
        [interprete, "-c", "import " + ", ".join(sorted(modules))],
        capture_output=True,
        text=True,
        check=False,
    )
    return essai.returncode == 0


def interprete(racine=None, essais=None, sonde=None) -> tuple[str | None, dict[str, str]]:
    """Le premier interprete qui porte les modules declares, et ce qui manque quand aucun ne les porte.

    ## Pourquoi la porte ne peut pas se contenter de `python3`

    Les gardes declarent leurs dependances, et l interprete du systeme ne les porte pas toutes.
    Mesure du 2026-09-07 : `python3` porte `yaml` et pas `tree_sitter_language_pack`, que
    `4472-commentaire-en-corps.py` importe depuis #5420. La batterie rougissait donc sur un garde
    sain, avec un `ModuleNotFoundError` tombant au vingtieme lancement.

    ## Pourquoi elle SONDE au lieu de deriver un chemin

    Le paysage a change deux fois en deux jours : `~/.venv-outils` portait `ruff` seul, puis plus
    rien ; `.venv` n existait pas, puis a porte le groupe entier. Un chemin ecrit en dur aurait ete
    faux dans les deux sens. On essaie donc, dans l ordre, et le premier qui repond gagne.

    `essais` et `sonde` sont injectables, sans quoi aucun cas ne pourrait fabriquer un poste ou
    l interprete manque (ADR 3624).
    """
    modules = modules_attendus(racine)
    lance = sonde or porte_les_modules
    for candidat in essais if essais is not None else candidats(racine):
        # ⟨un chemin qui n existe pas n est pas un candidat⟩ Quand RIEN n est declare, la sonde rend
        # vrai sans rien lancer, et le premier candidat est un `.venv` qui peut ne pas exister : la
        # porte partait alors avec un interprete introuvable, et chaque garde echouait sur un
        # `FileNotFoundError` qui ne parle ni du diff ni du poste.
        if sonde is None and "/" in candidat and not pathlib.Path(candidat).exists():
            continue
        if lance(candidat, modules):
            return candidat, {}
    return None, modules


def _suffixe(arguments: list[str]) -> str:
    """Les arguments affiches a cote du garde. Sans eux, deux lancements differents se ressemblent."""
    return (" " + " ".join(arguments)) if arguments else ""


def hors_de_la_porte(racine: pathlib.Path | None = None) -> list[str]:
    """Les scripts de `.github/scripts` que les ateliers lancent, et que la porte n engage pas.

    Ils ne declarent pas de `CONTRAT`, donc ils n existent pas pour elle (#5525). Les compter EST le
    service : la porte ne peut pas les jouer, et un lecteur qui ne sait pas qu ils existent croit
    son vert complet.
    """
    corpus = {g for g, _ in gardes(racine)}
    vus = set()
    for ligne in _lignes_des_ateliers(racine):
        for trouve in INVOCATION_PY.finditer(ligne):
            nom = trouve.group(1)
            if nom.startswith(".github/scripts/") and "--auto-test" not in trouve.group(2):
                vus.add(nom)
    return sorted(vus - corpus)


def reste_a_lancer(
    diff: list[str], absents: list[tuple[str, str]], racine: pathlib.Path | None = None
) -> list[str]:
    """Ce que la porte n a PAS joue, dit SOUS sa ligne de verdict.

    **La position est le defaut qu on ferme.** Ce que la porte ne couvre pas etait annonce plus
    haut, dans une section qui nomme des commandes sans les lancer, et sa ligne de verdict avait la
    forme d une conclusion : un compte, des refus, une phrase. Un lecteur qui descend jusqu au
    resume, ce que sa position invite a faire, croyait avoir tout vu. Cinq fois en vingt-quatre
    heures, une porte verte a precede une CI rouge (#5481).

    C est l article A3 applique a la porte : un dispositif dit ce qu il couvre, ET ce qu il n a pas
    pu lire.
    """
    lignes = ["", "  RESTE A LANCER, que cette porte ne joue pas :"]
    java = engage_java(diff)
    if java:
        lignes.append(
            f"    ./mvnw -B test -Dglass.platform=Headless -Dtest={','.join(java)}"
            f"   ({len(java)} classe(s) Java qui jugent la prose)"
        )
    for libelle, distribution in absents:
        lignes.append(f"    {libelle}   absent de l interprete, fourni par `{distribution}`")
    lignes.append(
        '    python3 .github/scripts/verifie_titre_pr.py "<titre>"  puis  verifie_corps_pr.py'
        ' "<corps>"'
    )
    dehors = hors_de_la_porte(racine)
    if dehors:
        lignes.append(
            f"    {len(dehors)} script(s) de `.github/scripts` que les ateliers lancent : hors de"
        )
        lignes.append("      cette porte, faute de `CONTRAT` declare (#5525)")
    return lignes


def rendre(
    contre: str = "origin/main", lance: bool = False, racine: pathlib.Path | None = None
) -> int:
    diff = fichiers_du_diff(contre, racine)
    if not diff:
        print(f"Aucun fichier ne differe de `{contre}` : rien a lancer.")
        return 0

    non_suivis = fichiers_non_suivis(racine)
    if non_suivis:
        print("REFUS : des fichiers neufs restent hors de l index Git.")
        for chemin in non_suivis:
            print(f"  {chemin}")
        print()
        print("Des gardes construisent leur population avec `git ls-files` et ne les voient pas.")
        print("Indexez-les avec `git add`, puis relancez la batterie.")
        return 1

    engages, ecartes = engage(diff, racine)
    print(f"{len(diff)} fichier(s) modifie(s) contre `{contre}`.")
    print()

    if not engages:
        # Une porte qui n engage rien sur un diff non vide n a pas trie : elle s est tue.
        print("❌ AUCUN garde engage sur un diff non vide.")
        print("   Une porte qui n engage rien rend vert sans avoir rien lance. Elle REFUSE plutot.")
        return 1

    print(f"  ENGAGE ({len(engages)} garde(s))")
    if lance:
        annonce_la_duree(engages)
    # ⟨la liste montre ce qui sera LANCE⟩ Sans les arguments, elle invite a copier une commande que
    # la porte ne joue pas - et pour dix gardes, cette commande ECRIT au lieu de juger (#5481).
    des_ateliers = arguments_des_ateliers(racine)
    for g in engages:
        print(f"    python3 {g}{_suffixe(des_ateliers.get(g, []))}")
    if ecartes:
        print()
        print(
            f"  NON ENGAGE ({len(ecartes)}), parce que leurs chemins declares ne sont pas touches"
        )
        for g in ecartes:
            print(f"    · {g}")

    java = engage_java(diff)
    if java:
        print()
        print(f"  ENGAGE ({len(java)} classe(s) Java qui jugent la prose)")
        print(f"    ./mvnw -B test -Dglass.platform=Headless -Dtest={','.join(java)}")
        print("       Ce depot teste sa documentation comme du code : ces classes lisent des `.md`")
        print("       et refusent quand ils derivent. `DocumentationAJourTest` met 2,4 s.")

    sans = [g for g, c in gardes(racine) if not c]
    print()
    print(
        f"  {len(sans)} garde(s) ne declarent pas leurs `chemins`, et sont donc LANCES par defaut."
    )
    print("     Le defaut penche du cote couteux, jamais du cote muet.")

    if not lance:
        return 0

    # ⟨la porte POSE ce qui est cher et conditionnel⟩ Le crochet `post-checkout` pose ce qui est bon
    # marche - six secondes - a la creation d un worktree (#5406). PMD, lui, depend de ce que le diff
    # touche, et la porte est le seul endroit qui le sache.
    pmd_a_echoue = False
    if engage_pmd(diff):
        print()
        print("  Rapport PMD : `4617` REFUSE sans lui, et se classerait « environnemental ».")
        depart_pmd = __import__("time").time()
        rendu = subprocess.run(
            ["./mvnw", "-B", "-o", "-q", "test-compile", "pmd:pmd"],
            cwd=racine or RACINE,
            capture_output=True,
            text=True,
            check=False,
        )
        mis = __import__("time").time() - depart_pmd
        if rendu.returncode == 0:
            print(f"    produit en {mis:.0f} s")
        else:
            # Dire, et poursuivre. Une preparation muette qui echoue rendrait la porte MOINS sure
            # qu avant : le lecteur croirait l environnement complet, et `4617` refuserait sans qu on
            # sache si c est le rapport ou le code.
            pmd_a_echoue = True
            print(f"    ECHEC apres {mis:.0f} s : `./mvnw -o test-compile pmd:pmd`.")
            # ⟨l ORDRE de ces deux lignes est le remede, et il vient d un pair⟩ L ecriture d avant
            # disait « `4617` refusera donc, et son refus ne dira RIEN de ce diff ». Elle repondait a
            # « que vaut le refus de 4617 » alors que le lecteur, a cet instant, se demande « qu est-ce
            # qui a casse ». Et sa reponse ecartait precisement la bonne : dans le cas vecu, Spotless
            # avait coupe une ligne de javadoc hors de son commentaire, donc la cause ETAIT le diff.
            #
            # On dit donc la cause d abord, puis seulement ce que le verdict de `4617` vaut. La
            # formulation est celle du pair qui a vecu le cas, et il la donne pour ce qu elle est : un
            # jugement a la relecture, sur un seul cas, et non une mesure (#5850).
            print("    La compilation a echoue : la cause la plus probable est VOTRE diff.")
            print("    Le verdict de `4617` n est pas a lire tant qu elle n est pas retablie.")

    print()
    # ⟨on va AU BOUT, et on rend tous les rouges⟩ La premiere ecriture s arretait au premier refus,
    # et c etait un mauvais choix : elle butait au vingtieme garde sur soixante-trois, sur un refus
    # d ENVIRONNEMENT - `4617` exige `target/pmd.xml`. Elle imposait donc autant de passages qu il y
    # a de rouges, ce qui est exactement le va-et-vient qu elle existe pour supprimer.
    import json
    import time

    # ⟨le choix se fait UNE fois, et AVANT le premier garde⟩ Laisser tomber un `ModuleNotFoundError`
    # au vingtieme lancement fait passer un defaut d environnement pour un defaut du diff. Le refus
    # arrive donc en tete, et il nomme la distribution qui manque.
    python, manquants = interprete(racine)
    if python is None:
        print(
            "\nREFUS : aucun interprete ne porte les modules que les gardes declarent.", flush=True
        )
        for module, distribution in sorted(manquants.items()):
            print(f"  {module}  fourni par  {distribution}", flush=True)
        print(
            "\nPosez le `.venv` de ce worktree, que `CONTRIBUTING.md` decrit :\n"
            "  python3 -m venv .venv && .venv/bin/python -m pip install --group gardes\n"
            "Un venv PAR worktree, jamais partage : une branche qui change une version epinglee\n"
            "doit etre eprouvee contre la sienne.",
            flush=True,
        )
        return 1
    if python != "python3":
        print(f"  interprete : {python}", flush=True)

    joues, rouges, muets, mesures = 0, [], [], durees_connues()
    for g in engages:
        depart = time.time()
        arguments = des_ateliers.get(g, [])
        sortie = subprocess.run(
            [python, g, *arguments],
            cwd=str(racine or RACINE),
            capture_output=True,
            text=True,
            check=False,
        )
        mesures[g] = round(time.time() - depart, 1)
        joues += 1
        # Les trois issues d un lancement, et pourquoi elles sont trois : voir
        # `verdict_du_lancement`, qui les tient et que l auto-test eprouve.
        verdict, ligne = verdict_du_lancement(
            pathlib.Path(g).name, sortie.returncode, sortie.stdout, sortie.stderr
        )
        if verdict == "arguments":
            print(f"  · {g}  (s attend des arguments, non jugé ici)", flush=True)
            continue
        if verdict == "muet":
            # `?` et non `✘` : l ADR 2748 pose cette distinction entre `0` et `?`, et l ADR 5407
            # ecrit qu un garde qui refuse faute de donnees « n est pas vert, il est MUET ».
            muets.append((g, ligne))
            print(f"  ? {g}{_suffixe(arguments)}", flush=True)
        elif verdict not in SANS_REFUS:
            rouges.append((g, ligne))
            print(f"  ✘ {g}{_suffixe(arguments)}", flush=True)
        else:
            # ⟨flush⟩ Sans lui, la sortie est tamponnee et une execution interrompue n affiche RIEN.
            # Mesure du 2026-09-06 : tuee a quinze minutes, cette porte a laisse un journal VIDE, donc
            # personne n a su ou elle en etait ni ce qu elle avait deja juge. Un outil long qui ne
            # montre rien avant sa fin ne se distingue pas d un outil bloque.
            print(f"  ✔ {g}{_suffixe(arguments)}", flush=True)

    try:
        DUREES.parent.mkdir(parents=True, exist_ok=True)
        DUREES.write_text(json.dumps(mesures, indent=1, sort_keys=True), encoding="utf-8")
    except OSError:
        pass  # Le relevé est un confort : ne pas pouvoir l ecrire ne doit pas faire echouer la porte.

    # ⟨les auto-tests que les ateliers lancent et que la population de la porte ignore⟩ Quatre
    # d entre eux n avaient pour premier lecteur que la CI ; neuf autres sont des loupes et des
    # releves, hors de la population de gardes parce qu ils ne JUGENT pas, donc hors de la porte
    # aussi. Mesure du 2026-09-29 : treize scripts, 1,64 s au total, la porte exclue d elle-meme.
    autotests = 0
    for chemin in auto_tests_des_ateliers(racine):
        rendu = subprocess.run(
            [python, chemin, "--auto-test"],
            cwd=str(racine or RACINE),
            capture_output=True,
            text=True,
            check=False,
        )
        autotests += 1
        if rendu.returncode == 0:
            print(f"  ✔ {chemin} --auto-test", flush=True)
        else:
            # ⟨un COUPLE, comme les deux autres familles⟩ Cette ligne ajoutait une CHAINE la ou
            # les gardes et les outils ajoutent `(nom, ligne)`, et le recapitulatif depaquette en
            # deux : un auto-test d atelier qui echouait faisait LEVER la porte sur un `ValueError`
            # au lieu de rendre son verdict. Jamais vu, parce que ces dix-sept passent - et c est
            # exactement le chemin ou l on a le plus besoin qu elle parle. Trouve en lisant le
            # fichier pour #5780.
            queue = (rendu.stdout + rendu.stderr).strip().splitlines()[-3:]
            rouges.append((f"{chemin} --auto-test", queue[0] if queue else "sans ligne"))
            print(f"  ✘ {chemin} --auto-test", flush=True)
            for ligne in queue:
                print(f"      {ligne}", flush=True)

    outils, absents = 0, []
    for libelle, commande in outils_des_ateliers(racine):
        # ⟨un outil absent se DIT, il ne se compte pas refus⟩ Un refus ferait croire a un defaut du
        # diff ; l absence parle du poste. C est la distinction de l ADR 5407, appliquee aux outils.
        if not porte_les_modules(python, {commande[0]: commande[0]}):
            absents.append((libelle, commande[0]))
            continue
        rendu = subprocess.run(
            [python, "-m", *commande],
            cwd=str(racine or RACINE),
            capture_output=True,
            text=True,
            check=False,
        )
        outils += 1
        if rendu.returncode == 0:
            print(f"  ✔ {libelle}", flush=True)
        else:
            premiere = next(
                (l for l in (rendu.stdout + rendu.stderr).splitlines() if l.strip()), "sans ligne"
            )
            rouges.append((libelle, premiere))
            print(f"  ✘ {libelle}", flush=True)

    # ⟨trois comptes, pas un⟩ Un auto-test derive d un atelier n est pas un garde de la population :
    # les confondre ferait croire que la porte couvre treize gardes de plus, quand elle joue treize
    # auto-tests de scripts qu elle n engage pas autrement (#5555).
    # ⟨TROIS comptes dans la ligne de verdict, pas deux⟩ Deux sessions ont fait la meme objection :
    # si un garde muet cesse d etre compte refus, une porte avec deux muets dirait « 0 refus » et
    # sortirait 0, donc le garde cesserait de mentir et la porte commencerait. Un faux vert est la
    # direction dangereuse, et c est celle que ce compte ferme.
    print(
        f"\n  {joues} garde(s), {autotests} auto-test(s) d atelier et {outils} outil(s) joue(s),"
        f" {len(rouges)} refus, {len(muets)} muet(s)."
    )
    # ⟨les muets se NOMMENT, et dans la QUEUE⟩ Une session pair a lu « 0 refus » dans un code 1 en
    # filtrant la sortie sur `✘` : un verdict qui ne se voit que dans le corps du texte se perd.
    for g, ligne in muets:
        print(f"  ? {g} n a PAS pu juger")
        for affichee in refus_affiche(ligne):
            print(affichee)
        renvoi = renvoi_a_la_preparation(g, pmd_a_echoue)
        if renvoi:
            print(renvoi)
    for ligne in reste_a_lancer(diff, absents, racine):
        print(ligne)
    if not rouges:
        if muets:
            print()
            print("  Rien n est rouge, et tout n a pas ete juge : les gardes ci-dessus ont REFUSE")
            # ⟨« ne parle pas de votre diff » n est pas toujours vrai⟩ Pour un muet faute de `node`
            # ou d un paquet, l affirmation est juste. Quand la PREPARATION du rapport PMD a echoue,
            # elle est fausse : la cause la plus probable d un `test-compile` qui echoue est le diff
            # lui-meme, et la promettre etrangere au diff envoie chercher du cote du poste (#5850).
            print(f"  de conclure faute d un prerequis.{_cause_possiblement_locale(pmd_a_echoue)}")
            print("  ne vaut pas un vert. Code 2 : « je n ai pas pu tout juger ».")
            return 2
        return 0
    print()
    for g, ligne in rouges:
        print(f"  ✘ {g}")
        for affichee in refus_affiche(ligne):
            print(affichee)
    print()
    # Un garde qui REFUSE faute d un prerequis n est pas un garde qui a juge, et la nuance decide de
    # ce qu on apprend. La porte ne tranche pas a la place du lecteur : elle rend la ligne de refus,
    # ou cette nuance se lit.
    print("  Un refus n est pas toujours un defaut du diff : plusieurs gardes REFUSENT de conclure")
    print("  faute d un prerequis - `target/pmd.xml`, l outil OpenSpec, un paquet reel. Leur ligne")
    print("  de refus le dit, et se rejoue sur `main` pour en avoir le coeur net.")
    return 1


# ⟨une liste, et pourquoi elle n en est pas une⟩ Un garde qui EXIGE des arguments n a pas juge
# lance nu, et le compter refus ferait croire a un defaut du diff. Deviner cela de la forme de sa
# sortie ne marche pas : les deux gardes du depot qui exigent des arguments n ont PAS la meme forme.
# `compte-les-reliquats.py` sort par `raise SystemExit(...)`, donc en 1 avec UNE ligne ;
# `convertit-adr-okf.py` passe par `argparse`, donc en 2 avec DEUX lignes. Toute condition sur le
# code ou sur le nombre de lignes est calibree sur l un contre l autre.
#
# On declare donc, et `verdict_du_lancement` CONFRONTE la declaration a ce que le garde fait
# vraiment, dans les deux sens. C est le parti que `GARDES_JAVA` prend vingt lignes plus haut :
# une liste qu un garde confronte n est plus une liste, c est un inventaire (ADR 3450).
# ⟨l aiguillage est TOTAL, et par defaut il refuse⟩ Ecrit `verdict == "rouge"`, il laissait
# `declaration-perimee` tomber dans le `else` et s afficher VERT - le faux vert meme qu on ferme.
# Un verdict neuf compte donc comme un refus tant que personne ne l a range ici.
SANS_REFUS = ("vert", "arguments")

EXIGENT_DES_ARGUMENTS = {
    "compte-les-reliquats.py": "cliquet DIFFERENTIEL : il compare un avant a un apres, donc "
    "`--avant` ou `--apres` lui est necessaire et il ne juge rien lance nu",
}


def refus_affiche(ligne: str) -> list[str]:
    """Les lignes du recapitulatif pour un refus, indentees et bornees CHACUNE.

    `verdict_du_lancement` rend jusqu a DEUX lignes depuis #5395. Les imprimer telles quelles
    laissait la seconde sans indentation, et la troncature a 160 s appliquait a la chaine JOINTE :
    un refus dont la premiere ligne est longue perdait la seconde, c est-a-dire exactement le geste
    qu on venait de rendre visible.
    """
    return [f"      {l[:160]}" for l in ligne.splitlines() if l.strip()]


def verdict_du_lancement(nom: str, code: int, stdout: str, stderr: str) -> tuple[str, str]:
    """Ce qu un lancement de garde veut dire, et la ligne qui l explique.

    **Trois issues, pas deux.** Un garde qui EXIGE DES ARGUMENTS n a pas juge : le compter rouge
    ferait croire a un defaut du diff, qui est exactement le faux signal que cette porte existe pour
    supprimer.

    **Les deux flux se lisent.** Un garde qui refuse ecrit sur `stderr`, comme la maison le veut.
    Cette fonction lisait `stdout` seul, si bien que `4617-code-mort-et-zone-de-test.py` sortait
    « (sans sortie) » alors qu il disait quoi lancer. Un rouge illisible renvoie l agent au banc
    improvise que cette porte remplace (#5383).

    **La PREMIERE ligne, jamais la derniere.** Un garde bien ecrit explique comment se corriger
    APRES avoir refuse : sa queue de sortie ressemble donc a de la prose calme, et c est la premiere
    ligne qui nomme la cause.
    """
    lignes = [l.strip() for l in (stdout + "\n" + stderr).splitlines() if l.strip()]
    if code == 0:
        return "vert", ""
    # ⟨on lit le COMPORTEMENT, jamais une liste d exemptions⟩ Le code de sortie ne suffit pas :
    # `compte-les-reliquats.py` est un cliquet differentiel qui sort en **1**, pas en 2 comme le
    # commentaire d origine l affirmait. Le test `returncode == 2` ne pouvait donc jamais etre vrai,
    # et ce garde etait compte rouge a chaque lancement.
    # ⟨DEUX lignes, et pourquoi pas une regle de position⟩ Mesure de #5395 sur les trois refus reels
    # d une batterie : `4617` et `verifie-specs-valides` commencent par la cause,
    # `verifie-sous-commandes-openspec` par un en-tete suivi de deux points. Un sur trois, et aucune
    # position ne dit systematiquement quoi faire.
    #
    # Prendre « la deuxieme ligne quand la premiere finit par deux points » serait une INFERENCE sur
    # la forme du texte. Ce depot a tranche deux fois contre l inference cette nuit, dont l ADR 5398
    # sur cette porte meme. On MONTRE PLUS a la place : les deux premieres lignes non vides couvrent
    # les trois formes sans rien deviner, et coutent une ligne de plus au recapitulatif.
    # ⟨la forme DECLAREE d abord, le repli ensuite⟩ Un garde qui emploie `_commun.refuse` nomme sa
    # cause et son geste, et la porte les lit OU QU ILS SOIENT. Le repli a deux lignes reste pour les
    # gardes non convertis : rendre `None` plutot que de deviner est ce qui permet de les convertir
    # un a un sans fausser la porte entre-temps (#5485).
    from _commun import lignes_du_refus, lit_le_refus

    declare = lit_le_refus(stdout + "\n" + stderr)
    # ⟨le refus, ses ADR et ses suspects, LUS dans `_commun` et non devines ici⟩ Un garde qui juge
    # plusieurs ADR rend une ligne de verdict par ADR, et le refus n appartient qu a l une d elles.
    # Les deux premieres lignes non vides tombaient sur le TITRE de la premiere ADR et son verdict
    # `ok` : un refus du plancher 4587 s affichait sous l ADR 4395 (#5817).
    #
    # Deux defauts de cette premiere correction sont reparees ici, et tous deux mesures (#5834) :
    #
    #   - sur un garde qui ne juge qu UNE ADR, montrer la ligne de verdict RETIRAIT le nom du
    #     fichier, que les deux premieres lignes donnaient. 8 gardes gagnaient l attribution, 34
    #     perdaient le suspect ;
    #   - `a-resserrer` n est pas `ok` et ne refuse pas non plus : il sort en 0, et c est une bonne
    #     nouvelle. Le filtre « pas ok » l accusait, donc la porte nommait deux ADR quand une seule
    #     refusait.
    #
    # Les deux savoirs qui manquaient, l ensemble des verdicts qui refusent et le rattachement d un
    # suspect a son ADR, sont **decides** par `_commun` : ils vivent donc la-bas. Recopies ici, ils
    # se perimeraient en silence le jour ou un verdict s ajoute ou ou un emetteur imprime une ligne
    # de plus, et c est precisement la panne que ce lot repare.
    #
    # Le repli a deux lignes reste pour tout ce qui ne rend aucun verdict qui refuse : un garde non
    # converti, une trace d exception, un `Usage abusif de`.
    refus = lignes_du_refus(stdout + "\n" + stderr)
    premiere = (
        f"{declare[0]}\n{declare[1]}"
        if declare
        else (refus if refus else ("\n".join(lignes[:2]) if lignes else "(sans sortie)"))
    )
    if nom not in EXIGENT_DES_ARGUMENTS:
        # ⟨LE TROISIEME VERDICT, decide sur ce que cette fonction LIT DEJA⟩ Un garde qui emploie la
        # forme declaree dit « je n ai pas pu juger », et c est une autre chose que « j ai juge et
        # c est rouge ». Cette fonction lisait les deux champs pour mieux les AFFICHER, puis jetait
        # ce que leur presence signifie : huit corps de demande sur deux sessions ont cherche dans
        # leur diff un defaut qui tenait au poste (#5780).
        #
        # Aucune inference : on ne devine pas, on lit une forme que le garde DECLARE. Et la lecture
        # des CODES de sortie n est pas rouverte - la porte l a quittee parce qu un test
        # `returncode == 2` ne pouvait jamais etre vrai sur `compte-les-reliquats`.
        #
        # Un garde NON CONVERTI, qui sort non nul sans la forme, reste compte comme avant : c est le
        # repli de #5485, qui permet de convertir un a un sans fausser la porte entre-temps.
        if declare:
            return "muet", premiere
        # ⟨aucune devinette ici, et c est le point⟩ Un garde NEUF qui exigerait des arguments sans
        # etre declare est compte refus, et sa ligne d usage s affiche : le lecteur voit tout de
        # suite ce qui se passe. C est un faux ROUGE, visible et sans danger. Deviner a sa place
        # rouvrirait le faux VERT que cette fonction vient de fermer, puisque « Usage abusif de »
        # ouvre aussi de vrais refus.
        return "rouge", premiere
    # Le DESACCORD qui compte, et le seul : la declaration a vieilli. Elle exempterait alors un vrai
    # refus, ce qui est un faux vert - la direction dangereuse. On refuse plutot que d exempter.
    if not lignes or not lignes[0].lower().startswith("usage"):
        return "declaration-perimee", premiere
    return "arguments", premiere


# ⟨le dernier cas JOUE, retenu hors de l auto-test⟩ Ce harnais est procedural : ses cas evaluent
# leur expression dans un `if`, donc une expression qui leve arrete le temoin AVANT que le libelle
# n ait ete imprime. Le harnais sortait alors sur une trace de pile, non nulle - donc la CI
# l attrapait - sans dire lequel de ses quarante-sept controles avait rougi, ce que l ADR 4918
# refuse (#5530).
#
# Cette liste vit au niveau module pour survivre a la remontee de l exception. Elle retient les
# lignes de cas, et le rattrapage nomme la DERNIERE : le leve s est donc produit entre elle et la
# suivante, ce qui localise la panne a un cas pres sans rien deferer.
_CAS_JOUES: list[str] = []


def _auto_test_nomme() -> int:
    """Joue l auto-test, et NOMME le dernier cas joue si une expression leve.

    Le rattrapage est LARGE, et c est le but : une expression de cas peut lever n importe quoi, et
    le propos est qu elle se situe au lieu d arreter le temoin sans un mot. Restreindre ici rendrait
    muettes exactement les pannes que ce lot existe pour nommer.
    """
    _CAS_JOUES.clear()
    try:
        return _auto_test()
    except Exception as leve:  # noqa: BLE001
        print(situe_le_leve(leve, _CAS_JOUES))
        return 1


def situe_le_leve(leve: BaseException, joues: list[str]) -> str:
    """Le message qui SITUE un levé, extrait pour que l auto-test puisse l eprouver lui-meme.

    Batir ce message dans le `except` le rendait intestable : aucun cas ne pouvait le lire sans
    faire lever le harnais entier, donc sans se detruire. C est la lecon de #5530 appliquee a son
    propre remede - une capacite qu on ne peut pas interroger n est pas une capacite.
    """
    dernier = joues[-1].strip() if joues else "(aucun cas joué avant le levé)"
    return (
        f"\n  ✘ l auto-test a levé {type(leve).__name__} : {leve}"
        f"\n      dernier cas joué : {dernier}"
        f"\n      le levé est donc survenu entre ce cas et le suivant."
    )


def _auto_test() -> int:
    """Les deux moities, et le bord ou la porte se tairait.

    Une porte qui lance TOUT passerait le premier cas sans rien trier : c est pourquoi le second cas
    exige qu un garde declarant soit ECARTE.
    """
    import builtins
    import contextlib as _ctx
    import io as _io
    import tempfile
    import textwrap

    echecs = 0
    # ⟨le compte se DERIVE des cas joues⟩ Il etait ecrit en dur - « 30 cas » pour quarante-sept
    # reellement imprimes - et personne ne l avait vu : c est l inventaire qui se dit exhaustif sans
    # l etre, dans le fichier meme que ce lot corrige pour cela. Ce `print` local ne reecrit pas les
    # quarante-sept sites : il les compte au passage.
    dits: list[str] = []

    def print(*morceaux, **nommes):
        ligne = " ".join(str(m) for m in morceaux)
        dits.append(ligne)
        # Les lignes de CAS seulement : le rattrapage nomme un cas, pas un titre de section.
        if ligne.startswith(("  ✔", "  ✘")):
            _CAS_JOUES.append(ligne)
        builtins.print(*morceaux, **nommes)

    with tempfile.TemporaryDirectory(prefix="vc-batterie-") as bac:
        faux = pathlib.Path(bac) / "depot"
        (faux / "scripts" / "methode").mkdir(parents=True)
        (faux / "scripts" / "adr").mkdir(parents=True)
        (faux / "scripts" / "methode" / "declarant.py").write_text(
            textwrap.dedent('''
                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d",
                           "chemins": """
                dev-docs/decisions/**
                """}
            '''),
            encoding="utf-8",
        )
        # ⟨ce qui ne juge pas est ECARTE⟩ Une loupe rend `0` par construction : elle ne peut pas
        # causer l aller-retour de CI que cette porte existe pour eviter. C est le controle qui
        # distingue le critere du DISPOSITIF d un simple seuil de duree.
        (faux / "scripts" / "methode" / "une_loupe.py").write_text(
            textwrap.dedent("""
                CONTRAT = {"geste": "x", "population": "y", "dispositif": "loupe",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}
            """),
            encoding="utf-8",
        )
        (faux / "scripts" / "adr" / "muet.py").write_text(
            textwrap.dedent("""
                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}
            """),
            encoding="utf-8",
        )

        # ⟨PMD se paie sur le Java, et seulement la⟩ Les deux sens, car un dispositif qui rendrait
        # toujours vrai passerait le premier cas sans rien trier (#5405).
        for diff, attendu, libelle in (
            (["src/main/java/X.java"], True, "un diff Java demande le rapport PMD"),
            (["src/test/java/XTest.java"], True, "un diff de test Java aussi"),
            (["dev-docs/x.md"], False, "un diff de prose ne le paie PAS"),
            ([], False, "un diff vide non plus"),
        ):
            if engage_pmd(diff) != attendu:
                print(f"  ✘ {libelle}")
                echecs = 1
            else:
                print(f"  ✔ {libelle}")

        # Les deux moities de la regle, sur le MEME garde declarant : engage quand ses chemins sont
        # touches, ecarte quand ils ne le sont pas. Un seul des deux cas serait passe par une porte
        # qui lance tout, ou par une porte qui n engage rien.
        cas = (
            (["dev-docs/decisions/1.md"], True, "ses chemins sont touchés, il est engagé"),
            (["README.md"], False, "ses chemins ne sont pas touchés, il est écarté"),
            # ⟨un garde se voit LUI-MEME⟩ La demande qui reecrit sa source est precisement celle ou
            # l on veut le voir tourner, et ses `chemins` etroits l en ecartaient. Neuf gardes du
            # depot etaient dans ce cas, dont un ecrit la veille par qui venait de lire le defaut
            # (#5421). D ou l ajout d office : une consigne « chaque garde se cite » s oublie.
            (
                ["scripts/methode/declarant.py"],
                True,
                "la demande réécrit sa SOURCE, il est engagé même si ses chemins sont étroits",
            ),
        )
        for diff, attendu, libelle in cas:
            engages, ecartes = engage(diff, faux)
            obtenu = any("declarant.py" in g for g in engages)
            bon = obtenu is attendu and any(
                "declarant.py" in g for g in (engages if attendu else ecartes)
            )
            print(f"  {'✔' if bon else '✘'} un garde déclarant : {libelle}")
            if not bon:
                echecs += 1
                print(f"      engagés={engages} écartés={ecartes}")

        # Le controle NEGATIF du dispositif : un garde SANS `chemins` est lance quoi qu il arrive.
        engages, _ = engage(["n-importe-quoi.txt"], faux)
        if any("muet.py" in g for g in engages):
            print("  ✔ un garde qui ne déclare rien est lancé, quel que soit le diff")
        else:
            print("  ✘ un garde qui ne déclare rien a été écarté : le repli ne tient pas")
            echecs += 1

        # Le critere du dispositif, dans les DEUX sens : une loupe est absente du corpus, un
        # invariant y est. Un seul des deux cas passerait par un filtre qui garderait tout.
        noms = [g for g, _ in gardes(faux)]
        if not any("une_loupe.py" in g for g in noms):
            print("  ✔ une loupe est écartée : elle ne peut pas faire rougir la CI")
        else:
            print("  ✘ une loupe est restée dans le corpus")
            echecs += 1
        if any("muet.py" in g for g in noms):
            print("  ✔ un invariant reste : il juge, donc il peut rougir")
        else:
            print("  ✘ un invariant a été écarté")
            echecs += 1

        # ⟨un fichier NEUF ne peut pas rester hors de la population⟩ La porte voyait son chemin et
        # engageait les gardes, mais ceux qui construisent leur corpus avec `git ls-files` jugeaient
        # encore l ancien arbre. #5514 l a mesure : la batterie annonçait 56 gardes sans refus avant
        # le premier commit, puis deux planchers ont refusé les mêmes fichiers une fois suivis.
        subprocess.run(["git", "-C", str(faux), "init", "-q"], check=True)
        subprocess.run(["git", "-C", str(faux), "add", "."], check=True)
        subprocess.run(
            [
                "git",
                "-C",
                str(faux),
                "-c",
                "user.name=Test",
                "-c",
                "user.email=test@example.invalid",
                "commit",
                "-qm",
                "socle",
            ],
            check=True,
        )
        nouveau = faux / "src" / "main" / "java" / "Nouveau.java"
        nouveau.parent.mkdir(parents=True)
        nouveau.write_text("/// Voir #5515.\nclass Nouveau {}\n", encoding="utf-8")

        tampon = _io.StringIO()
        with _ctx.redirect_stdout(tampon):
            code_avant = rendre(contre="HEAD", racine=faux)
        sortie_avant = tampon.getvalue()
        bon = (
            code_avant == 1
            and "src/main/java/Nouveau.java" in sortie_avant
            and "git add" in sortie_avant
        )
        print(f"  {'✔' if bon else '✘'} un fichier neuf non indexé empêche la batterie de conclure")
        if not bon:
            echecs += 1
            print(f"      code={code_avant} sortie={sortie_avant[:200]!r}")

        subprocess.run(["git", "-C", str(faux), "add", "src/main/java/Nouveau.java"], check=True)
        tampon = _io.StringIO()
        with _ctx.redirect_stdout(tampon):
            code_apres = rendre(contre="HEAD", racine=faux)
        bon = code_apres == 0
        print(f"  {'✔' if bon else '✘'} le même fichier indexé rejoint la population du diff")
        if not bon:
            echecs += 1
            print(f"      code={code_apres} sortie={tampon.getvalue()[:200]!r}")

        # Et le bord ou la porte se tairait : aucun engage sur un diff non vide fait REFUSER.
        vide = pathlib.Path(bac) / "vide"
        (vide / "scripts" / "methode").mkdir(parents=True)
        (vide / "scripts" / "adr").mkdir(parents=True)
        engages, _ = engage(["x.txt"], vide)
        if not engages:
            print("  ✔ un corpus sans garde n'engage rien, et `rendre` refuse alors")
        else:
            print("  ✘ un corpus vide a engagé quelque chose")
            echecs += 1

        # ⟨les arguments viennent des ATELIERS⟩ La porte lancait chaque garde NU, et dix gardes que
        # la CI lance avec `--verifie` ECRIVENT dans ce mode au lieu de juger (#5481). Les cinq cas
        # tiennent la regle ET ses bords : une seule forme se rejoue, tout le reste se lance nu.
        ateliers = pathlib.Path(bac) / "ateliers"
        (ateliers / ".github" / "workflows").mkdir(parents=True)
        (ateliers / ".github" / "workflows" / "x.yml").write_text(
            textwrap.dedent("""
                jobs:
                  a:
                    steps:
                      - run: python3 scripts/methode/declarant.py --verifie
                      - run: python3 scripts/methode/nu.py
                      - run: python3 scripts/methode/deux.py --verifie
                      - run: python3 scripts/methode/deux.py
                      - run: python3 scripts/methode/auto.py --auto-test
                      - run: python3 scripts/methode/shell.py "${CORPS}"
                      - run: ruff check scripts icone
            """),
            encoding="utf-8",
        )
        derives = arguments_des_ateliers(ateliers)
        for libelle, garde, attendu in (
            ("une forme unique se rejoue", "declarant.py", ["--verifie"]),
            ("un garde que la CI lance nu le reste", "nu.py", None),
            ("deux formes : la porte ne choisit pas", "deux.py", None),
            ("un auto-test n est pas un emploi", "auto.py", None),
            ("une variable de shell ne se rejoue pas", "shell.py", None),
        ):
            obtenu = derives.get(f"scripts/methode/{garde}")
            bon = obtenu == attendu
            print(f"  {'✔' if bon else '✘'} arguments des ateliers : {libelle}")
            if not bon:
                echecs += 1
                print(f"      {garde} : attendu {attendu}, obtenu {obtenu}")

        # ⟨LES cas de #5555⟩ Le critere est le MODE, ni le dossier ni le contrat. L atelier ci-dessus
        # lance `auto.py --auto-test`, qui n a pas de `CONTRAT` et ne vit ni dans `adr` ni dans
        # `methode/` au sens de `gardes()` : il doit donc etre joue. Les trois autres portent le
        # CONTRASTE, sans lequel un critere fonde sur le dossier passerait le premier.
        derives_auto = auto_tests_des_ateliers(ateliers)
        for libelle, chemin, attendu in (
            ("un auto-test sans contrat est JOUE", "scripts/methode/auto.py", True),
            (
                "un script lance dans un AUTRE mode ne l est pas",
                "scripts/methode/declarant.py",
                False,
            ),
            ("ni un script lance nu", "scripts/methode/nu.py", False),
            (
                "et `.github/scripts` reste hors de cette porte (#5525)",
                ".github/scripts/x.py",
                False,
            ),
        ):
            bon = (chemin in derives_auto) == attendu
            print(f"  {'✔' if bon else '✘'} auto-tests des ateliers : {libelle}")
            if not bon:
                echecs += 1
                print(f"      {chemin} : attendu {attendu}, derives={derives_auto}")
        # La porte ne se joue jamais elle-meme : sans cette ligne, son auto-test se relance a
        # l interieur de sa propre execution. Mesure du 2026-09-29 : 6,32 s, et un rouge etranger.
        bon = not any(MOI in c for c in auto_tests_des_ateliers())
        print(f"  {'✔' if bon else '✘'} auto-tests des ateliers : la porte s exclut d elle-meme")
        if not bon:
            echecs += 1

        attendu_outils = [("ruff check", ["ruff", "check", "scripts", "icone"])]
        obtenu_outils = outils_des_ateliers(ateliers)
        bon = obtenu_outils == attendu_outils
        print(f"  {'✔' if bon else '✘'} un outil et SES dossiers se derivent de l atelier")
        if not bon:
            echecs += 1
            print(f"      attendu {attendu_outils}, obtenu {obtenu_outils}")

        # ⟨de bout en bout, et dans les deux sens⟩ Le garde ci-dessous REFUSE avec `--verifie` et se
        # tait sans lui : c est le comportement exact des dix gardes ecrivains. Un seul des deux cas
        # passerait par une porte qui lancerait toujours nu, ou toujours avec les arguments.
        bout = pathlib.Path(bac) / "bout"
        (bout / "scripts" / "methode").mkdir(parents=True)
        (bout / "scripts" / "adr").mkdir(parents=True)
        (bout / ".github" / "workflows").mkdir(parents=True)
        (bout / "scripts" / "methode" / "sensible.py").write_text(
            textwrap.dedent("""
                import sys

                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}

                if "--verifie" in sys.argv:
                    print("REFUS : la matrice est perimee")
                    raise SystemExit(1)
                print("regeneree")
            """),
            encoding="utf-8",
        )
        (bout / "lu.md").write_text("socle\n", encoding="utf-8")
        # ⟨l outil se JOUE, il ne se derive pas seulement⟩ Sans ce fichier mal formate, retirer la
        # boucle des outils ne tuerait aucun cas : elle serait decorative.
        (bout / "scripts" / "mal_formate.py").write_text("x = [1,2,\n  3]\n", encoding="utf-8")
        atelier = bout / ".github" / "workflows" / "x.yml"
        atelier.write_text(
            "jobs:\n  a:\n    steps:\n      - run: python3 scripts/methode/sensible.py --verifie\n",
            encoding="utf-8",
        )
        for commande in (
            ["init", "-q"],
            ["add", "."],
            ["-c", "user.name=T", "-c", "user.email=t@example.invalid", "commit", "-qm", "socle"],
        ):
            subprocess.run(["git", "-C", str(bout), *commande], check=True)
        (bout / "lu.md").write_text("socle\nune ligne de plus\n", encoding="utf-8")

        # ⟨deux citations⟩ La liste ENGAGE et la ligne du lancement doivent TOUTES DEUX porter les
        # arguments : sans cela, la liste invite a copier une commande que la porte ne joue pas.
        for libelle, avec_atelier, code_attendu in (
            (
                "l atelier le lance avec `--verifie` : la porte REFUSE, et le dit aux deux endroits",
                True,
                1,
            ),
            ("sans atelier, le meme garde est lance nu et se tait", False, 0),
        ):
            if avec_atelier:
                atelier.write_text(
                    "jobs:\n  a:\n    steps:\n      - run: python3 scripts/methode/sensible.py"
                    " --verifie\n      - run: ruff format --check scripts\n",
                    encoding="utf-8",
                )
            else:
                atelier.write_text("jobs:\n  a:\n    steps: []\n", encoding="utf-8")
            tampon = _io.StringIO()
            with _ctx.redirect_stdout(tampon):
                code = rendre(contre="HEAD", lance=True, racine=bout)
            rendu = tampon.getvalue()
            # ⟨deux assertions, pas un compte⟩ Un compte se satisfait de n importe quelle
            # occurrence : la premiere ecriture comptait `sensible.py --verifie` deux fois et
            # survivait a la mutation qui cesse de PASSER les arguments, parce qu un refus de
            # `ruff` dans la meme fixture rendait deja le code attendu. On nomme donc les deux
            # endroits qui doivent le dire.
            liste = f"    python3 scripts/methode/sensible.py{' --verifie' if avec_atelier else ''}"
            lance_ainsi = f"  {'✘' if avec_atelier else '✔'} scripts/methode/sensible.py"
            lance_ainsi += " --verifie" if avec_atelier else ""
            bon = code == code_attendu and liste in rendu and lance_ainsi in rendu
            print(f"  {'✔' if bon else '✘'} {libelle}")
            if not bon:
                echecs += 1
                print(f"      code={code} attendu={code_attendu} sortie={rendu[-300:]!r}")
            if avec_atelier:
                # ⟨la POSITION est le defaut qu on ferme⟩ Ce qui reste a lancer etait annonce
                # AU-DESSUS de la ligne de verdict, et un lecteur qui descend jusqu au resume
                # croyait avoir tout vu.
                place = rendu.find("RESTE A LANCER") > rendu.find(" joue(s), ") > -1
                print(f"  {'✔' if place else '✘'} ce qui reste est dit SOUS la ligne de verdict")
                if not place:
                    echecs += 1

            if avec_atelier:
                joue = "✘ ruff format --check" in rendu
                print(f"  {'✔' if joue else '✘'} l outil derive est JOUE, et son refus compte")
                if not joue:
                    echecs += 1
                    print(
                        "      ruff absent de l interprete : posez le `.venv` du worktree"
                        if "absent de l interprete" in rendu
                        else f"      sortie={rendu[-300:]!r}"
                    )

        # ⟨LE TROISIEME VERDICT, DE BOUT EN BOUT (#5780)⟩ Les cas de `verdict_du_lancement` tiennent
        # la fonction ; ceux-ci tiennent ce que la PORTE en fait - sa ligne de verdict, le nom du
        # muet dans la QUEUE, et son code de sortie. Deux sessions pairs ont fait la meme objection :
        # un muet qui cesse d etre compte refus, sans etre montre ailleurs, ferait dire « 0 refus »
        # et sortir 0. Le garde cesserait de mentir, la porte commencerait.
        for libelle, forme_declaree, code_attendu, muets_attendus in (
            ("un garde MUET fait sortir la porte en 2, et se nomme", True, 2, 1),
            ("le MEME garde sans la forme declaree reste rouge, code 1", False, 1, 0),
        ):
            # ⟨DEUX sources entieres, et non un fragment interpole⟩ La premiere ecriture glissait
            # un fragment multiligne dans une position indentee, et produisait un fichier que Python
            # ne lisait pas. La porte l a DIT - « sensible.py est illisible (IndentationError) » -
            # et c est elle qui a diagnostique ma fixture, pas moi.
            declare = textwrap.dedent("""
                import sys

                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}

                print("REFUS : un prerequis manque", file=sys.stderr)
                print("POUR REPARER : posez-le", file=sys.stderr)
                raise SystemExit(2)
            """)
            nu = textwrap.dedent("""
                import sys

                CONTRAT = {"geste": "x", "population": "y", "dispositif": "invariant",
                           "seuil": "(sans objet)", "temoin": "t", "decision": "d"}

                print("quelque chose a casse", file=sys.stderr)
                raise SystemExit(2)
            """)
            (bout / "scripts" / "methode" / "sensible.py").write_text(
                declare if forme_declaree else nu, encoding="utf-8"
            )
            atelier.write_text("jobs:\n  a:\n    steps: []\n", encoding="utf-8")
            # ⟨on INDEXE avant de lancer, et ce n est pas une commodite⟩ La premiere ecriture de ce
            # cas rendait code=1 sans aucun muet, et j ai cru mon code faux. Le bac portait un
            # fichier NON INDEXE laisse par les cas voisins, donc la porte refusait AVANT le premier
            # garde - le piege exact qu une session pair avait rencontre le matin meme, et dont elle
            # m avait dit de me defier. Un cas qui ne maitrise pas l etat de son bac n eprouve pas
            # ce qu il croit.
            subprocess.run(["git", "-C", str(bout), "add", "."], check=True)
            tampon = _io.StringIO()
            with _ctx.redirect_stdout(tampon):
                code = rendre(contre="HEAD", lance=True, racine=bout)
            rendu = tampon.getvalue()
            # QUATRE choses, et chacune ferme une porte de sortie : le code, le compte de la ligne
            # de verdict, le nom dans la queue, et - pour le muet - la phrase qui dit pourquoi ce
            # n est pas un vert. Un seul de ces controles laisserait passer le faux vert.
            compte = f"{muets_attendus} muet(s)." in rendu
            nomme = ("n a PAS pu juger" in rendu) == bool(muets_attendus)
            dit_le_code = ("Code 2" in rendu) == bool(muets_attendus)
            bon = code == code_attendu and compte and nomme and dit_le_code
            print(f"  {'✔' if bon else '✘'} {libelle}")
            if not bon:
                echecs += 1
                print(
                    f"      code={code} attendu={code_attendu} compte={compte}"
                    f" nomme={nomme} dit_le_code={dit_le_code}"
                )

        # ⟨UN AUTO-TEST D ATELIER QUI ECHOUE ne doit pas faire LEVER la porte⟩ Ces auto-tests
        # ajoutaient une CHAINE la ou les gardes ajoutent un couple, et le recapitulatif depaquette
        # en deux : la porte levait un `ValueError` au lieu de rendre son verdict. Aucun cas ne
        # jouait ce chemin, parce que les dix-sept auto-tests reels passent - donc le defaut vivait
        # exactement la ou l on a le plus besoin d un verdict. Trouve en lisant le fichier (#5780).
        #
        # Un auto-test SANS `CONTRAT` sort de la population des gardes et n est lu que par
        # `auto_tests_des_ateliers`, d ou le fichier distinct.
        (bout / "scripts" / "methode" / "muet_d_atelier.py").write_text(
            textwrap.dedent("""
                import sys

                print("  ✘ un cas du harnais a rougi", file=sys.stderr)
                raise SystemExit(1)
            """),
            encoding="utf-8",
        )
        atelier.write_text(
            "jobs:\n  a:\n    steps:\n      - run: python3"
            " scripts/methode/muet_d_atelier.py --auto-test\n",
            encoding="utf-8",
        )
        subprocess.run(["git", "-C", str(bout), "add", "."], check=True)
        tampon = _io.StringIO()
        # ⟨ce cas RATTRAPE, et c est le point⟩ Le defaut qu il garde est un LEVE, pas un faux
        # verdict. Laisse nu, il faisait remonter le `ValueError` jusqu au rattrapage du harnais : la
        # mutation etait detectee, mais en « non concluant » plutot qu en cas rouge nomme. Un cas qui
        # plante vaut moins qu un cas qui rougit en se nommant - la lecon de #5530, appliquee ici.
        leve = None
        code = None
        try:
            with _ctx.redirect_stdout(tampon):
                code = rendre(contre="HEAD", lance=True, racine=bout)
        except Exception as attrape:  # noqa: BLE001
            leve = attrape
        rendu = tampon.getvalue()
        # La porte doit CONCLURE : ne pas lever, un code non nul, et sa ligne de verdict presente.
        conclut = leve is None and code != 0 and " joue(s), " in rendu
        print(
            f"  {'✔' if conclut else '✘'} un auto-test d atelier qui échoue ne fait pas LEVER la porte"
        )
        if not conclut:
            echecs += 1
            print(
                f"      levé {type(leve).__name__} : {leve}"
                if leve
                else f"      code={code} sortie={rendu[-300:]!r}"
            )

        # Et ce que la queue nomme, dans les deux sens : une ADR engage une classe Java, un texte non.
        for diff, doit, libelle in (
            (
                ["dev-docs/decisions/9999-x.md"],
                True,
                "une ADR fait nommer la classe Java non jouee",
            ),
            (["notes.txt"], False, "un diff sans prose jugee ne nomme aucune classe"),
        ):
            lignes = "\n".join(reste_a_lancer(diff, [], None))
            bon = ("./mvnw" in lignes) is doit and "verifie_titre_pr.py" in lignes
            print(f"  {'✔' if bon else '✘'} {libelle}")
            if not bon:
                echecs += 1
                print(f"      {lignes!r}")

    # ⟨les trois issues d un lancement⟩ La boucle qui les distingue n avait AUCUN cas : elle vivait
    # dans `rendre`, derriere un `subprocess`. Les deux premiers cas rougissent sur le code d avant.
    for libelle, nom, code, sortie, erreur, attendu in (
        (
            "un refus écrit sur `stderr` est cité",
            "4617.py",
            1,
            "",
            "pmd.xml est absent\nLancez : ./mvnw",
            # La seconde ligne est le GESTE, et c est elle que la regle d une seule ligne perdait.
            ("rouge", "pmd.xml est absent\nLancez : ./mvnw"),
        ),
        (
            "un usage sorti en 1 n est pas un rouge",
            "compte-les-reliquats.py",
            1,
            "",
            "Usage : x.py --avant | --apres",
            ("arguments", "Usage : x.py --avant | --apres"),
        ),
        (
            "un refus sur `stdout` est cité aussi",
            "un-garde.py",
            1,
            "REFUS : outil absent",
            "",
            ("rouge", "REFUS : outil absent"),
        ),
        ("un garde muet reste lisible", "muet.py", 1, "", "", ("rouge", "(sans sortie)")),
        ("un garde vert ne dit rien", "vert.py", 0, "tout va bien", "", ("vert", "")),
        # ⟨le refus dont la PREMIERE ligne est un en-tete⟩ Mesure de #5395 sur les trois refus reels
        # d une batterie : deux commencent par la cause, le troisieme par un titre suivi de deux
        # points. Aucune POSITION n est donc fiable, et deviner d apres le deux-points serait une
        # inference sur la forme du texte - ce que l ADR 5398 vient de refuser sur cette porte meme.
        # ⟨la borne haute, et elle est annoncee⟩ « les DEUX premieres lignes » : sans ce cas, en
        # montrer trois ne ferait rougir personne. `4617` refuse en trois lignes, dont la derniere
        # est la commande - on la perd deliberement, et le cout est ecrit dans #5395.
        (
            "un refus de trois lignes n en montre que deux",
            "4617-code-mort-et-zone-de-test.py",
            1,
            "",
            (
                "target/pmd.xml est absent : PMD n a pas tourne.\n"
                "Ce garde REFUSE plutot que de conclure sur ce qu il n a pas lu.\n"
                "Lancez d abord : ./mvnw -B -o test-compile pmd:pmd"
            ),
            (
                "rouge",
                (
                    "target/pmd.xml est absent : PMD n a pas tourne.\n"
                    "Ce garde REFUSE plutot que de conclure sur ce qu il n a pas lu."
                ),
            ),
        ),
        (
            "un refus dont la première ligne est un en-tête montre quand même la cause",
            "verifie-sous-commandes-openspec.py",
            1,
            "",
            "Invocations d OpenSpec qui n existent pas :\n  openspec est absent. Lancez « npm ci »",
            (
                "rouge",
                "Invocations d OpenSpec qui n existent pas :\nopenspec est absent. Lancez « npm ci »",
            ),
        ),
        # ⟨le faux vert de #5398⟩ « Usage abusif de » est une tournure que la prose de ce depot
        # emploie. Un garde NON DECLARE qui refuse ainsi etait compte « arguments », donc retire du
        # compte des refus, et la porte finissait verte.
        (
            "un refus qui commence par « Usage » reste un refus",
            "un-garde-qui-juge.py",
            1,
            "",
            "Usage abusif du selecteur : trois appels non gardes.\nCe garde REFUSE.",
            ("rouge", "Usage abusif du selecteur : trois appels non gardes.\nCe garde REFUSE."),
        ),
        # ⟨la confrontation, dans le sens dangereux⟩ Le jour ou `compte-les-reliquats.py` cessera
        # d exiger ses arguments, son exemption se mettrait a masquer un VRAI refus. C est ce qui
        # rend cette declaration un inventaire plutot qu une liste : elle est confrontee a chaque
        # lancement, et son desaccord refuse au lieu de se taire.
        (
            "une déclaration périmée refuse au lieu d exempter",
            "compte-les-reliquats.py",
            1,
            "",
            "RELIQUATS | lus=0 | verdict=refus",
            ("declaration-perimee", "RELIQUATS | lus=0 | verdict=refus"),
        ),
        # ⟨LE TROISIEME VERDICT, #5780⟩ Aucun cas de cette table n assertait le VERDICT d un refus
        # DECLARE : les trois cas voisins ne lisent que sa LIGNE. C est pour cela que le defaut a
        # vecu - la fonction lisait les deux champs pour mieux les afficher, et rendait « rouge ».
        (
            "un refus DECLARE est muet, pas rouge",
            "x.py",
            2,
            "",
            "REFUS : node est absent du PATH\nPOUR REPARER : mettez node sur le PATH",
            ("muet", "node est absent du PATH\nmettez node sur le PATH"),
        ),
        # Le CONTRASTE qui compte le plus : un garde qui a JUGE et qui est rouge reste rouge. Sans
        # lui, le cas ci-dessus passerait sur une porte qui appellerait TOUT muet.
        (
            "un ecart JUGE reste rouge, et ne devient pas muet",
            "x.py",
            1,
            "",
            "3 spec(s) principale(s) ne valident pas",
            ("rouge", "3 spec(s) principale(s) ne valident pas"),
        ),
        # Le second contraste : un garde NON CONVERTI, non nul et sans la forme, est compte comme
        # avant. C est le repli de #5485, et le retirer ferait basculer d un coup des gardes dont
        # personne n a relu le refus.
        (
            "un garde NON CONVERTI reste rouge, forme absente",
            "x.py",
            2,
            "",
            "quelque chose a casse\net une seconde ligne",
            ("rouge", "quelque chose a casse\net une seconde ligne"),
        ),
        # ⟨le defaut de #5817⟩ Un garde qui juge DEUX ADR rend deux lignes de verdict, et le refus
        # n appartient qu a l une. Les deux premieres lignes non vides tombaient sur le TITRE de la
        # premiere et son verdict `ok` : « 4587 » n apparaissait nulle part.
        (
            "un refus de la SECONDE ADR nomme la seconde, pas la premiere",
            "scripts/adr/4395-renvois-en-javadoc.py",
            1,
            (
                "ADR 4395 - production\n"
                "\nPLANCHER 4395 | lus=1254 | mesure=3393 | plancher=3393 | verdict=ok\n"
                "ADR 4587 - test\n"
                "\nPLANCHER 4587 | lus=937 | mesure=1305 | plancher=1304 | verdict=a-relever\n"
            ),
            "",
            ("rouge", "PLANCHER 4587 | lus=937 | mesure=1305 | plancher=1304 | verdict=a-relever"),
        ),
        # ⟨le cas que #5834 a retourne⟩ Il attendait d abord « son compte, PAS son premier
        # suspect », et cette attente etait le defaut : sur un garde a une seule ADR, les deux
        # premieres lignes nommaient le FICHIER, et le remplacer par un compte retirait l information
        # que le lecteur cherche. Il attend donc maintenant les DEUX, le compte et le fichier.
        (
            "un cliquet depasse montre son compte ET ses suspects",
            "scripts/adr/4359-javadoc-narratif.py",
            1,
            (
                "ADR 4359 - javadoc narrative\n"
                "  src/main/java/fr/univ_amu/iut/Launcher.java\n"
                "  src/main/java/fr/univ_amu/iut/A.java\n"
                "\nADR 4359 | lus=1254 | suspects=742 | cliquet=740 | verdict=regression\n"
            ),
            "",
            (
                "rouge",
                (
                    "ADR 4359 | lus=1254 | suspects=742 | cliquet=740 | verdict=regression\n"
                    "src/main/java/fr/univ_amu/iut/Launcher.java\n"
                    "src/main/java/fr/univ_amu/iut/A.java"
                ),
            ),
        ),
        # ⟨`a-resserrer` n est pas `ok` et ne refuse pas⟩ Il sort en 0 : le garde est passe SOUS sa
        # marge, et c est une bonne nouvelle. Le filtre « pas ok » de #5828 l accusait, si bien que
        # la porte nommait deux ADR quand une seule refusait. Sans ce cas, le filtre peut y revenir.
        (
            "un verdict `a-resserrer` n est pas montre comme une cause de refus",
            "x.py",
            1,
            (
                "ADR 5068 - clic sur reference tenue\n"
                "\nADR 5068 | lus=200 | suspects=33 | cliquet=37 | verdict=a-resserrer\n"
                "ADR 5707 - geste du pointeur hors du fil\n"
                "  ClicTest.java:42\n"
                "\nADR 5707 | lus=166 | suspects=170 | cliquet=162 | verdict=regression\n"
            ),
            "",
            (
                "rouge",
                (
                    "ADR 5707 | lus=166 | suspects=170 | cliquet=162 | verdict=regression\n"
                    "ClicTest.java:42"
                ),
            ),
        ),
        # ⟨les suspects d une ADR ne debordent pas sur la suivante⟩ `lignes_du_refus` vide son
        # accumulateur sur CHAQUE ligne de verdict, y compris un `ok`. Sans cela, les suspects d une
        # ADR verte seraient montres sous l ADR qui refuse apres elle, ce qui est le defaut de #5817
        # sous une autre forme : nommer les lignes de quelqu un d autre.
        (
            "les suspects d une ADR verte ne passent pas a l ADR qui refuse",
            "x.py",
            1,
            (
                "ADR 1111 - la verte\n"
                "  AppartientALaVerte.java\n"
                "\nADR 1111 | lus=10 | suspects=1 | cliquet=1 | verdict=ok\n"
                "ADR 2222 - celle qui refuse\n"
                "\nADR 2222 | lus=10 | suspects=5 | cliquet=0 | verdict=regression\n"
            ),
            "",
            ("rouge", "ADR 2222 | lus=10 | suspects=5 | cliquet=0 | verdict=regression"),
        ),
        # Le CONTRASTE NEGATIF : tous les verdicts `ok` et un code non nul. Montrer une ligne `ok`
        # dirait au lecteur que tout va bien sur un garde qui refuse ; on retombe donc sur le repli,
        # qui ne pretend rien.
        (
            "des verdicts tous ok avec un code non nul retombent sur le repli",
            "x.py",
            1,
            "ADR 9999 - x\n\nADR 9999 | lus=10 | suspects=0 | cliquet=0 | verdict=ok\n",
            "",
            ("rouge", "ADR 9999 - x\nADR 9999 | lus=10 | suspects=0 | cliquet=0 | verdict=ok"),
        ),
        # ⟨pourquoi la ligne est ANCREE, et non cherchee n importe ou dedans⟩ Un garde peut CITER
        # une ligne de verdict dans sa prose de refus, pour renvoyer a un rapport. Chercher le motif
        # sans l ancrer ferait alors nommer une ADR que ce garde ne juge meme pas, ce qui est pire
        # que le defaut de #5817 : celui-la montrait la mauvaise ADR du BON garde, celui-ci
        # montrerait l ADR d un AUTRE.
        #
        # ⟨ce cas ne meurt d aucune mutation simple, et c est mesure⟩ Le comportement est tenu TROIS
        # fois : `match` part de la position 0, le motif porte `^`, et il porte `$`. Mesure du
        # 2026-10-04 sur la ligne « ECHEC : comparez avec ADR 4587 | verdict=perte du rapport » :
        # `search` seul ne la prend pas, `search` sans `^` ne la prend pas non plus - le `$` refuse,
        # « perte » etant suivi de prose. Seule la reecriture LACHE, `search` sans `^` ni `$`, prend
        # « 4587 » et fait rougir ce cas. Il ne garde donc aucun de ces trois choix isolement ; il
        # garde l INTENTION d un motif ancre, et c est contre un motif reecrit a la legere qu il
        # tire. Ne pas le lire comme vacant parce qu une mutation d un caractere le laisse vert.
        (
            "une ligne de verdict CITEE dans la prose n est pas prise pour un verdict",
            "x.py",
            1,
            (
                "ADR 1234 - x\n"
                "\nADR 1234 | lus=5 | suspects=0 | cliquet=0 | verdict=ok\n"
                "ECHEC : comparez avec ADR 4587 | verdict=perte du rapport hebdomadaire\n"
            ),
            "",
            ("rouge", "ADR 1234 - x\nADR 1234 | lus=5 | suspects=0 | cliquet=0 | verdict=ok"),
        ),
        # La PRECEDENCE, et c est elle qui decide de l ordre du code : un garde qui porte la forme
        # declaree ET une ligne de verdict non `ok` rend sa cause et son geste. La forme declaree
        # dit « je n ai pas pu juger » ; la ligne de verdict dirait « j ai juge et c est rouge ».
        # Les inverser rouvrirait le faux rouge que #5780 a ferme.
        (
            "la forme declaree l emporte sur une ligne de verdict non ok",
            "x.py",
            2,
            "ADR 7777 | lus=5 | suspects=9 | cliquet=1 | verdict=regression\n",
            "REFUS : le lecteur est absent\nPOUR REPARER : posez-le",
            ("muet", "le lecteur est absent\nposez-le"),
        ),
    ):
        obtenu = verdict_du_lancement(nom, code, sortie, erreur)
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs += 1

    # ⟨l aiguillage de `rendre`, et non la fonction⟩ Un verdict juste que l appelant range du cote
    # vert ne vaut rien. Ce cas tient la table sur laquelle `rendre` decide, seule chose que
    # l auto-test puisse atteindre sans lancer de sous-processus.
    for verdict in ("rouge", "declaration-perimee"):
        if verdict not in SANS_REFUS:
            print(f"  ✔ un verdict « {verdict} » est compté refus par `rendre`")
        else:
            print(f"  ✘ « {verdict} » est rangé du côté vert : il ne serait jamais montré")
            echecs += 1

    # ⟨« muet » n est NI vert NI refus, et les deux erreurs sont dangereuses⟩ Rangé du côté vert, il
    # disparaitrait et la porte dirait « tout est juge ». Rangé avec les refus, il ferait chercher
    # dans le diff. C est la troisieme place que ce lot ouvre, et `rendre` la tient par son `if`
    # dedie plutot que par cette table.
    muet_hors_du_vert = "muet" not in SANS_REFUS
    print(f"  {'✔' if muet_hors_du_vert else '✘'} « muet » n est pas rangé du côté vert")
    if not muet_hors_du_vert:
        echecs += 1

    # ⟨le choix de l interprete, dans les DEUX sens⟩ Sans le second cas, un sondage qui rendrait
    # toujours le premier candidat passerait le premier et la porte refuserait de tourner partout.
    for libelle, essais, repond, attendu in (
        (
            "le `.venv` du worktree est essaye EN PREMIER",
            ["/a/.venv/bin/python", "python3"],
            lambda c, m: True,
            "/a/.venv/bin/python",
        ),
        (
            "on passe au suivant quand le premier ne porte rien",
            ["/a/.venv/bin/python", "python3"],
            lambda c, m: c == "python3",
            "python3",
        ),
        (
            "aucun interprete valide rend None, et ce qui manque",
            ["/a", "/b"],
            lambda c, m: False,
            None,
        ),
    ):
        choisi, manquants = interprete(essais=essais, sonde=repond)
        bon = choisi == attendu and (attendu is not None or bool(manquants))
        print(f"  {'✔' if bon else '✘'} {libelle}")
        if not bon:
            echecs = 1
            print(f"      choisi={choisi!r} manquants={manquants!r}")

    # ⟨le CHEMIN DE REFUS, et pas seulement le calcul⟩ Les trois cas ci-dessus eprouvent le choix ;
    # celui-ci eprouve ce que la porte FAIT quand il n y a rien a choisir. Un refus qu aucun cas ne
    # traverse est le premier a se casser en silence.
    # ⟨le diff est INJECTE, sinon le cas depend du disque⟩ En CI le worktree est sur la reference de
    # fusion, donc `git diff origin/main` est vide et `rendre` sort avant d atteindre le controle : le
    # cas passait en local et rougissait en CI. Un cas dont le verdict depend de l etat du disque
    # n eprouve pas ce qu il annonce.
    _vrai = globals()["interprete"]
    _vrai_diff = globals()["fichiers_du_diff"]
    globals()["interprete"] = lambda racine=None: (None, {"yaml": "PyYAML"})
    globals()["fichiers_du_diff"] = lambda contre="origin/main", racine=None: ["scripts/adr/x.py"]
    try:
        tampon = _io.StringIO()
        with _ctx.redirect_stdout(tampon):
            code = rendre(lance=True)
        sortie = tampon.getvalue()
    finally:
        globals()["interprete"] = _vrai
        globals()["fichiers_du_diff"] = _vrai_diff

    bon = code == 1 and "REFUS" in sortie and "PyYAML" in sortie
    print(f"  {'✔' if bon else '✘'} sans interprete valide, la porte REFUSE avant le premier garde")
    if not bon:
        echecs = 1
        print(f"      code={code} sortie={sortie[:200]!r}")

    # ⟨le rapport existe exactement quand quelqu un le lit⟩ La coherence se verifie sur les DEUX
    # sens : un diff qui n engage pas `4617` ne doit pas payer PMD, et un diff qui l engage doit le
    # payer - y compris celui qui touche le garde lui-meme, que la regle de #5421 engage sans que ses
    # `chemins` le disent (#5465).
    for libelle, diff in (
        ("un diff de prose ne paie pas PMD", ["dev-docs/une-page.md"]),
        ("un diff Python non plus", ["scripts/x.py"]),
        ("un diff Java le paie", ["src/main/java/X.java"]),
        (
            "le garde lui-meme le paie, par la regle du garde qui se voit",
            ["scripts/adr/4617-code-mort-et-zone-de-test.py"],
        ),
        (
            "son ADR aussi, parce qu un cliquet qui bouge doit se confronter",
            ["dev-docs/decisions/4682-le-portail-compte-chaque-zone-a-part.md"],
        ),
    ):
        joue = any("4617" in g for g in engage(diff)[0])
        if joue == engage_pmd(diff):
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : 4617 engage={joue}, PMD produit={engage_pmd(diff)}")
            echecs = 1

    # ⟨l affichage, et non la fonction⟩ Deux lignes justes que `rendre` imprimerait mal ne valent
    # rien. Ces cas tiennent l INDENTATION et la troncature PAR LIGNE : bornee sur la chaine jointe,
    # elle mangeait la seconde quand la premiere etait longue - le geste qu on venait de rendre
    # visible (#5395).
    for libelle, entree, attendu in (
        (
            "les deux lignes d un refus sont indentées",
            "Invocations qui n existent pas :\nopenspec est absent. Lancez « npm ci »",
            [
                "      Invocations qui n existent pas :",
                "      openspec est absent. Lancez « npm ci »",
            ],
        ),
        (
            "un refus d une seule ligne n en gagne pas une vide",
            "pmd.xml est absent",
            ["      pmd.xml est absent"],
        ),
        (
            "chaque ligne est bornée séparément, pas la chaîne jointe",
            "x" * 200 + "\nLancez : ./mvnw",
            ["      " + "x" * 160, "      Lancez : ./mvnw"],
        ),
    ):
        obtenu = refus_affiche(entree)
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs += 1

    # ⟨le harnais nomme le cas qui leve, et ces cas-ci le prouvent⟩ Avant #5530, une expression qui
    # levait arretait ce temoin sur une trace de pile : non nulle, donc attrapee par la CI, mais
    # muette sur lequel des quarante-sept controles avait rougi. Ces trois cas lisent le message que
    # le rattrapage construit, sans avoir a faire lever le harnais entier.
    # ⟨ces cas-ci passent leur expression DIFFEREE⟩ Les ecrire en ligne aurait ajoute trois sites a
    # la dette que ce meme lot mesure, dans le fichier qui en porte deja le plus. L aide est locale
    # et non la fabrique partagee : l importer ici change ce que `interprete` voit, et fait rougir
    # le cas qui eprouve un interprete demuni. Mesure faite, puis defaite.
    def juge(libelle: str, calcul) -> int:
        try:
            bon = bool(calcul())
        except Exception as leve:  # noqa: BLE001
            print(f"  ✘ {libelle} : l expression a levé {type(leve).__name__} : {leve}")
            return 1
        print(f"  {'✔' if bon else '✘'} {libelle}")
        return 0 if bon else 1

    situe = situe_le_leve(ValueError("un cas qui leve"), ["  ✔ le cas d avant", "  ✔ le dernier"])
    echecs += juge(
        "le levé nomme son type et son message",
        lambda: "ValueError : un cas qui leve" in situe,
    )
    echecs += juge(
        "il nomme le DERNIER cas joué", lambda: "dernier cas joué : ✔ le dernier" in situe
    )
    # Le CONTRASTE : sans lui, un message qui nommerait toujours le premier cas passerait le
    # precedent, et un harnais qui leve avant tout cas rendrait une ligne vide.
    echecs += juge(
        "et sans aucun cas joué, il le DIT",
        lambda: "aucun cas joué" in situe_le_leve(ValueError("x"), []),
    )

    # ⟨LES cas de #5525⟩ Les gardes de `.github/scripts` entrent par leur `CONTRAT`, jamais par leur
    # dossier : la CI lance aussi `revoque_jeton.py` et `installer_paquets.py` dans ce meme dossier,
    # et une regle fondee sur le chemin ferait revoquer un jeton depuis un poste. Ces cas portent le
    # positif ET les deux contrastes, sans quoi une porte qui engagerait TOUT le dossier passerait
    # le premier.
    for libelle, diff, attendu in (
        (
            "un garde de CI est engage par ses chemins",
            [".github/scripts/neuf.py"],
            "verifie_inventaires_ci.py",
        ),
        (
            "la page des gardes l engage aussi",
            ["dev-docs/ci-cd-release.md"],
            "verifie_inventaires_ci.py",
        ),
    ):
        echecs += juge(libelle, lambda d=diff, a=attendu: any(a in g for g in engage(d)[0]))
    # Le CONTRASTE du cout : le banc de mutation de CI met 10,36 s, et un diff qui ne touche pas
    # `.github/` ne doit pas le payer. Sans ce cas, des `chemins` oublies passeraient inapercus.
    echecs += juge(
        "un diff sans `.github/` n engage aucun garde de CI",
        lambda: not any(g.startswith(".github") for g in engage(["src/main/java/fr/A.java"])[0]),
    )
    # Le CONTRASTE du critere : un script du meme dossier SANS contrat n est jamais engage, quel
    # que soit le diff. C est ce qui separe « la porte lit un dossier » de « la porte lit un contrat ».
    echecs += juge(
        "un script sans contrat du meme dossier n est JAMAIS engage",
        lambda: (
            not any(
                "revoque_jeton" in g
                for d in ([".github/scripts/neuf.py"], [".github/workflows/lint.yml"], ["x.txt"])
                for g in engage(d)[0]
            )
        ),
    )

    # ⟨LES cas de #5485⟩ La porte montrait les DEUX PREMIERES lignes d un refus, et c etait le
    # meilleur choix sans idiome : aucune position ne nomme systematiquement le geste. Elle ratait
    # donc celui de `4617`, en troisieme ligne. Ces cas tiennent les deux chemins - la forme
    # declaree, et le repli pour les gardes non convertis - et le CONTRASTE qui les separe.
    declare_loin = (
        "un en-tete\n"
        "REFUS : le lecteur est absent\n"
        "de la prose\n"
        "encore de la prose\n"
        "POUR REPARER : pip install --group gardes\n"
    )
    echecs += juge(
        "un geste declare se lit, meme en cinquieme ligne",
        lambda: (
            verdict_du_lancement("x.py", 2, declare_loin, "")[1]
            == "le lecteur est absent\npip install --group gardes"
        ),
    )
    # Le CONTRASTE du repli : un garde NON converti garde le comportement d avant, mot pour mot.
    # Sans ce cas, une lecture qui devinerait la position passerait le precedent.
    echecs += juge(
        "un refus non declare garde le repli a deux lignes",
        lambda: (
            verdict_du_lancement("x.py", 2, "la cause\nde la prose\nle geste", "")[1]
            == "la cause\nde la prose"
        ),
    )
    # Le CONTRASTE de la forme : la cause SEULE ne suffit pas. Rendre le geste vide serait pire que
    # le repli, puisque le lecteur croirait avoir tout vu.
    # ⟨le renvoi a la preparation, et ses DEUX contrastes⟩ Un garde muet qui consomme le rapport PMD
    # doit apprendre que sa production a echoue ; tout autre muet ne doit RIEN apprendre de plus. Sans
    # le second cas, le remede rendrait le bloc bavard sur tous les muets au lieu de le rendre juste,
    # et les deux etats seraient indiscernables (#5850).
    consommateur = "scripts/adr/4617-code-mort-et-zone-de-test.py"
    autre = "scripts/methode/verifie-specs-valides.py"
    echecs += juge(
        "un muet qui consomme le rapport PMD est renvoye a la preparation",
        lambda: "ECHOUE plus haut" in renvoi_a_la_preparation(consommateur, True),
    )
    echecs += juge(
        "et il lui dit de rejouer SANS `-q`, ce qui donne la cause",
        lambda: "-q" in renvoi_a_la_preparation(consommateur, True),
    )
    # Le PREMIER contraste : preparation reussie, donc rien a avouer.
    echecs += juge(
        "le meme garde ne recoit rien quand la preparation a reussi",
        lambda: renvoi_a_la_preparation(consommateur, False) == "",
    )
    # Le SECOND : un muet qui ne consomme pas ce rapport n a rien a voir avec PMD.
    echecs += juge(
        "un muet qui ne consomme pas le rapport ne recoit rien",
        lambda: renvoi_a_la_preparation(autre, True) == "",
    )
    # Et le consommateur est nomme UNE fois : si les deux designations divergent, la porte produirait
    # le rapport pour un garde et avouerait l echec pour un autre.
    # ⟨la conclusion des muets ne promet pas l innocence du diff quand elle ne la connait pas⟩
    echecs += juge(
        "sans echec de preparation, la conclusion dit le refus etranger au diff",
        lambda: "ne parle pas de votre diff" in _cause_possiblement_locale(False),
    )
    echecs += juge(
        "avec un echec, elle ne le promet plus et nomme le rapport manquant",
        lambda: (
            "ne parle pas de votre diff" not in _cause_possiblement_locale(True)
            and "PAS pu produire" in _cause_possiblement_locale(True)
        ),
    )
    echecs += juge(
        "le consommateur du rapport est nomme une seule fois",
        lambda: consomme_le_rapport_pmd(consommateur) and not consomme_le_rapport_pmd(autre),
    )
    echecs += juge(
        "une cause sans geste retombe sur le repli",
        lambda: (
            verdict_du_lancement("x.py", 2, "REFUS : x\nautre chose", "")[1]
            == "REFUS : x\nautre chose"
        ),
    )

    joues = sum(1 for ligne in dits if ligne.startswith(("  ✔", "  ✘")))
    print(
        f"\n{joues} cas : porte, bord, fichiers neufs, aiguillage, interprète et refus, dont l'attribution d'un refus a son ADR."
    )
    return 1 if echecs else 0


if __name__ == "__main__":
    from _commun import sort_si_contrat_demande

    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test_nomme())
    contre = sys.argv[sys.argv.index("--contre") + 1] if "--contre" in sys.argv else "origin/main"
    sys.exit(rendre(contre, "--lance" in sys.argv))
