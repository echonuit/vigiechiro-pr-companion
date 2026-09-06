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

## Ce qu elle ne fait pas

Elle ne remplace pas la CI. `AGENTS.md` le pose : la mesure fait foi en CI, pas sur le poste. Elle
est le PREMIER lecteur, celui qui evite l aller-retour, pas l autorite.
"""

from __future__ import annotations

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


def fichiers_du_diff(contre: str = "origin/main", racine: pathlib.Path | None = None) -> list[str]:
    """Ce que ce diff touche, suivi ET non suivi.

    Les fichiers NEUFS comptent : un garde qu on vient d ecrire n est pas encore suivi, et l oublier
    ferait taire exactement la porte qui devait le juger.
    """
    modifies = _git("diff", "--name-only", contre, racine=racine).splitlines()
    neufs = _git("ls-files", "--others", "--exclude-standard", racine=racine).splitlines()
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
    for dossier in ("adr", "methode"):
        for f in sorted((base / "scripts" / dossier).glob("*.py")):
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
                trouves.append((f"scripts/{dossier}/{f.name}", declares))
    return trouves


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


def rendre(
    contre: str = "origin/main", lance: bool = False, racine: pathlib.Path | None = None
) -> int:
    diff = fichiers_du_diff(contre, racine)
    if not diff:
        print(f"Aucun fichier ne differe de `{contre}` : rien a lancer.")
        return 0

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
    for g in engages:
        print(f"    python3 {g}")
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

    print()
    # ⟨on va AU BOUT, et on rend tous les rouges⟩ La premiere ecriture s arretait au premier refus,
    # et c etait un mauvais choix : elle butait au vingtieme garde sur soixante-trois, sur un refus
    # d ENVIRONNEMENT - `4617` exige `target/pmd.xml`. Elle imposait donc autant de passages qu il y
    # a de rouges, ce qui est exactement le va-et-vient qu elle existe pour supprimer.
    import json
    import time

    joues, rouges, mesures = 0, [], durees_connues()
    for g in engages:
        depart = time.time()
        sortie = subprocess.run(
            ["python3", g], cwd=str(racine or RACINE), capture_output=True, text=True, check=False
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
        if verdict not in SANS_REFUS:
            rouges.append((g, ligne))
            print(f"  ✘ {g}", flush=True)
        else:
            # ⟨flush⟩ Sans lui, la sortie est tamponnee et une execution interrompue n affiche RIEN.
            # Mesure du 2026-09-06 : tuee a quinze minutes, cette porte a laisse un journal VIDE, donc
            # personne n a su ou elle en etait ni ce qu elle avait deja juge. Un outil long qui ne
            # montre rien avant sa fin ne se distingue pas d un outil bloque.
            print(f"  ✔ {g}", flush=True)

    try:
        DUREES.parent.mkdir(parents=True, exist_ok=True)
        DUREES.write_text(json.dumps(mesures, indent=1, sort_keys=True), encoding="utf-8")
    except OSError:
        pass  # Le relevé est un confort : ne pas pouvoir l ecrire ne doit pas faire echouer la porte.

    print(f"\n  {joues} garde(s) joue(s), {len(rouges)} refus.")
    if not rouges:
        return 0
    print()
    for g, ligne in rouges:
        print(f"  ✘ {g}")
        print(f"      {ligne[:160]}")
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
    premiere = lignes[0] if lignes else "(sans sortie)"
    if nom not in EXIGENT_DES_ARGUMENTS:
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


def _auto_test() -> int:
    """Les deux moities, et le bord ou la porte se tairait.

    Une porte qui lance TOUT passerait le premier cas sans rien trier : c est pourquoi le second cas
    exige qu un garde declarant soit ECARTE.
    """
    import tempfile
    import textwrap

    echecs = 0
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

    # ⟨les trois issues d un lancement⟩ La boucle qui les distingue n avait AUCUN cas : elle vivait
    # dans `rendre`, derriere un `subprocess`. Les deux premiers cas rougissent sur le code d avant.
    for libelle, nom, code, sortie, erreur, attendu in (
        (
            "un refus écrit sur `stderr` est cité",
            "4617.py",
            1,
            "",
            "pmd.xml est absent\nLancez : ./mvnw",
            ("rouge", "pmd.xml est absent"),
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
        # ⟨le faux vert de #5398⟩ « Usage abusif de » est une tournure que la prose de ce depot
        # emploie. Un garde NON DECLARE qui refuse ainsi etait compte « arguments », donc retire du
        # compte des refus, et la porte finissait verte.
        (
            "un refus qui commence par « Usage » reste un refus",
            "un-garde-qui-juge.py",
            1,
            "",
            "Usage abusif du selecteur : trois appels non gardes.\nCe garde REFUSE.",
            ("rouge", "Usage abusif du selecteur : trois appels non gardes."),
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

    print("\n15 cas : porte, bord, exemption confrontée et aiguillage.")
    return 1 if echecs else 0


if __name__ == "__main__":
    from _commun import sort_si_contrat_demande

    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    contre = sys.argv[sys.argv.index("--contre") + 1] if "--contre" in sys.argv else "origin/main"
    sys.exit(rendre(contre, "--lance" in sys.argv))
