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
    return any("4617" in g for g in engage(diff)[0])


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
        if lance(candidat, modules):
            return candidat, {}
    return None, modules


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

    # ⟨la porte POSE ce qui est cher et conditionnel⟩ Le crochet `post-checkout` pose ce qui est bon
    # marche - six secondes - a la creation d un worktree (#5406). PMD, lui, depend de ce que le diff
    # touche, et la porte est le seul endroit qui le sache.
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
            print(f"    ECHEC apres {mis:.0f} s : `./mvnw -o test-compile pmd:pmd`.")
            print("    `4617` refusera donc, et son refus ne dira RIEN de ce diff.")

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

    joues, rouges, mesures = 0, [], durees_connues()
    for g in engages:
        depart = time.time()
        sortie = subprocess.run(
            [python, g], cwd=str(racine or RACINE), capture_output=True, text=True, check=False
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
    premiere = "\n".join(lignes[:2]) if lignes else "(sans sortie)"
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
    import contextlib as _ctx
    import io as _io

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

    print("\n28 cas : porte, bord, exemption confrontée, aiguillage, interprète et refus.")
    return 1 if echecs else 0


if __name__ == "__main__":
    from _commun import sort_si_contrat_demande

    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    contre = sys.argv[sys.argv.index("--contre") + 1] if "--contre" in sys.argv else "origin/main"
    sys.exit(rendre(contre, "--lance" in sys.argv))
