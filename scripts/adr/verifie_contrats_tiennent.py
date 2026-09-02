#!/usr/bin/env python3
"""Un contrat declare ne contredit pas ce que le garde fait (ADR 4636, issue #5108).

`imprime_contrat` refuse un contrat INCOMPLET, c est-a-dire a qui il manque un champ. Rien ne
comparait ce qu un contrat DECLARE a ce que le garde FAIT. Un contrat que personne ne confronte est
un commentaire, et il derive : la docstring de `2843-tiret-cadratin.py` annoncait deux arbres pour
un corpus qui n en portait qu un, pendant des mois (#5048).

## La regle n est PAS l egalite

Mesure du 2026-09-02 sur les trois porteurs, dont aucune ligne n est un defaut :

    0008                 decision   declare « ADR 0008 »          infere « 0008 »
    0008                 population declare « PRODUCTION + TESTS » infere « RACINES »
    matrice-constitution population declare « DECISIONS + ... »    infere « (non declaree) »

**Le vocabulaire differe** : `RACINES = (PRODUCTION, TESTS)`, les deux formes disent le meme corpus.
**Et le contrat sait PLUS que l inference**, dont le corpus ne connait que les arbres Java.

La regle est donc « les deux ne se CONTREDISENT pas ». Un silence de l inference n est pas une
contradiction : c est le cas normal, et c est meme la raison d etre du contrat.

**Encore faut-il RECONNAITRE le silence, et le champ `seuil` ne le reconnaissait pas** (#5119).
L inference dit son ignorance en toutes lettres, entre parentheses - `(2 ADR : 4395, 4587)`,
`(sans seuil)` - la ou `population` dit « (non declaree) ». Mais un aveu ecrit en francais peut
porter un chiffre, et `CHIFFRE.search` lisait le « 2 » de « 2 ADR » comme un seuil. Un garde a deux
ADR ne pouvait donc pas declarer le sien sans etre accuse. `seuil_resolu` nomme desormais les deux
formes CONCLUANTES au lieu d ecarter les autres.

## Comment les porteurs se trouvent

Par un grep, puis par un LANCEMENT. Le grep ne fait que reduire les candidats ; c est la reponse a
`--contrat` qui fait foi. Les trois porteurs emploient trois idiomes de dispatch - `sys.argv`,
`argparse`, et le shell - et un motif qui chercherait l un d eux en manquerait deux. J ai commis
cette erreur en mesurant ce chantier, et c est la meme que #5032 et #5103.
"""

from __future__ import annotations

import ast
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINE_DEPOT, cliquet, imprime_contrat, rapporte

ADR = "4636"

# Les alias du corpus, resolus vers les deux arbres elementaires. Sans cela, « RACINES » et
# « PRODUCTION + TESTS » se liraient comme un desaccord alors qu ils nomment le meme corpus.
ALIAS = {
    "RACINES": ("PRODUCTION", "TESTS"),
    "RACINES_ANCREES": ("PRODUCTION", "TESTS"),
    "PRODUCTION_ANCREE": ("PRODUCTION",),
    "TESTS_ANCRES": ("TESTS",),
    "PRODUCTION": ("PRODUCTION",),
    "TESTS": ("TESTS",),
}

# Ce que ce garde ne confronte PAS, et pourquoi. La cecite se declare, sinon un lecteur croit que
# les six champs sont tenus.
HORS_CONFRONTATION = {
    "geste": "une phrase libre, qu aucun motif ne derive du code",
    "dispositif": "cliquet, plancher, loupe ou invariant : l inference le devine du nom de l aide "
    "appelee, ce qui rendrait un desaccord la ou il n y a qu une convention de nommage",
}

CHIFFRE = re.compile(r"(\d+)")

# Les deux formes par lesquelles `seuil()` CONCLUT. Tout le reste est un aveu d ignorance, et un
# aveu ne contredit rien : c est deja la regle du champ `population`, que `corpus_resolu` applique.
SEUIL_CONCLUANT = re.compile(r"^(?:cliquet|plancher) (\d+)$")


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les points d entree qui MENTIONNENT un contrat, candidats a en porter un.

    Le grep ne conclut pas : il reduit. C est la reponse a `--contrat` qui fait foi, parce que les
    idiomes de dispatch different. Lancer `--contrat` sur les 117 points d entree couterait des
    minutes, un script sans cette branche ignorant l argument et faisant son travail.
    """
    base = racine or RACINE_DEPOT
    vus = []
    for motif in ("scripts/**/*.py", ".github/scripts/*.sh", ".github/assets/*.sh"):
        for f in sorted(base.glob(motif)):
            if "__pycache__" in f.parts or f.name.startswith("_"):
                continue
            try:
                if "--contrat" in f.read_text(encoding="utf-8", errors="ignore"):
                    vus.append(f)
            except OSError:
                continue
    return vus


def dispatche_en_code(chemin: pathlib.Path, texte: str) -> bool:
    """`--contrat` apparait-il dans le CODE du script, et non dans sa seule prose ?

    **C est une barriere de surete, pas d optimisation.** Lancer un script qui ne porte pas cette
    branche ne coute pas seulement du temps : il IGNORE l argument et fait son travail. Un
    generateur reecrirait des fichiers, et ce garde deviendrait un effet de bord.

    Les trois porteurs emploient trois idiomes - `"--contrat" in sys.argv`, un `add_argument` de
    `argparse`, et un test shell - mais tous les trois ecrivent le litteral dans du CODE. C est le
    controle de #5032 : une MENTION n est pas un DISPATCH.

    **La cecite est assumee et elle penche du bon cote** : un quatrieme idiome que ce controle ne
    verrait pas ne serait pas lance, donc pas confronte. Mieux vaut manquer un contrat que
    d executer un script dont on ignore ce qu il fait.
    """
    if chemin.suffix != ".py":
        return any("--contrat" in l and not l.lstrip().startswith("#") for l in texte.split("\n"))
    try:
        arbre = ast.parse(texte)
    except SyntaxError:
        return False
    docstrings = set()
    for noeud in ast.walk(arbre):
        corps = getattr(noeud, "body", None)
        porteur = (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)
        if isinstance(noeud, porteur) and corps and isinstance(corps[0], ast.Expr):
            tete = corps[0].value
            if isinstance(tete, ast.Constant) and isinstance(tete.value, str):
                docstrings.add(id(tete))
    return any(
        isinstance(n, ast.Constant)
        and isinstance(n.value, str)
        and "--contrat" in n.value
        and id(n) not in docstrings
        for n in ast.walk(arbre)
    )


def contrat_de(chemin: pathlib.Path) -> dict[str, str] | None:
    """Ce que le garde REPOND a `--contrat`, ou rien s il ne repond pas."""
    # Ne jamais se lancer SOI-MEME. La branche `--contrat` ci-dessus suffit en principe, mais un
    # garde qui lance des scripts ne doit pas dependre d une seule barriere : c est le defaut qui a
    # mis la machine a plat le 2026-09-02, en essaimant des sous-processus plus vite que leur
    # plafond ne les tuait.
    if chemin.resolve() == pathlib.Path(__file__).resolve():
        return None
    if not dispatche_en_code(chemin, chemin.read_text(encoding="utf-8", errors="ignore")):
        return None
    lance = ["bash", str(chemin)] if chemin.suffix == ".sh" else [sys.executable, str(chemin)]
    try:
        # `check=False` est VOULU : un garde qui refuse sort 1, et seule sa sortie nous interesse.
        # Lever sur le code rendrait ce releve dependant du verdict des gardes qu il inventorie.
        rendu = subprocess.run(
            [*lance, "--contrat"],
            capture_output=True,
            text=True,
            timeout=20,
            cwd=RACINE_DEPOT,
            check=False,
        ).stdout
    except (subprocess.TimeoutExpired, OSError):
        return None
    if not rendu.startswith("CONTRAT | garde="):
        return None
    champs = {}
    for ligne in rendu.split("\n")[1:]:
        if ": " in ligne:
            cle, _, valeur = ligne.partition(": ")
            champs[cle.strip()] = valeur.strip()
    return champs


def corpus_resolu(expression: str) -> frozenset[str] | None:
    """Les arbres elementaires qu une expression de population designe, ou rien si on ne sait pas."""
    morceaux = [m.strip() for m in expression.split("+")]
    if not morceaux or not all(m in ALIAS for m in morceaux):
        return None
    return frozenset(a for m in morceaux for a in ALIAS[m])


def seuil_resolu(expression: str) -> str | None:
    """Le nombre qu un seuil infere DESIGNE, ou rien quand l inference n a pas conclu.

    Le pendant de `corpus_resolu` pour le champ `seuil`, et il manquait. `seuil()` rend `cliquet N`
    ou `plancher N` quand elle conclut ; toutes ses autres reponses DISENT qu elle ne sait pas, et
    elles sont entre parentheses : `(2 ADR : 4395, 4587)`, `(sans seuil)`, `(pas d ADR declaree)`,
    `(ADR N introuvable)`. Une seule porte un chiffre, ce qui explique que le defaut ait attendu un
    garde a deux ADR pour se voir : `CHIFFRE.search` y lisait le « 2 » de « 2 ADR » et le comparait,
    si bien que `4395-renvois-en-javadoc.py` ne pouvait pas declarer son plancher de 3280 sans etre
    accuse de contredire un nombre qui n est le seuil de rien (issue #5119).

    **La reconnaissance est POSITIVE, et ce n est pas un detail de style.** Ecarter les parentheses
    marcherait aujourd hui et lirait une RESSEMBLANCE ; nommer les deux formes conclusives demande
    ce que l inference REPOND. C est la lecon de #5032, #5103 et #5108, et le prix en est un
    couplage au vocabulaire de `seuil()` que `_auto_test` tient par un temoin sur un garde reel.
    """
    trouve = SEUIL_CONCLUANT.match(expression)
    return trouve.group(1) if trouve else None


def seuil_contredit(declare: str, infere: str) -> bool:
    """Un seuil DECLARE contredit-il ce que l inference a resolu ?

    La DECISION vit ici, et non dans `suspects()`, pour qu un temoin puisse l eprouver. Une aide
    qu on teste pendant que la comparaison reste ailleurs se verifie elle-meme sans rien tenir :
    supprimer l appel laisserait ses temoins verts.
    """
    d = CHIFFRE.search(declare)
    i = seuil_resolu(infere)
    return bool(d and i and d.group(1) != i)


def temoin_existe(temoin: str, base: pathlib.Path) -> bool:
    """Ce que le champ `temoin` NOMME existe-t-il ?

    Deux formes vivent dans le depot : `fichier#fonction`, et une commande dont le premier mot est
    un fichier. Le garde ne LANCE pas le temoin : le prouver est le travail des meta-gardes, pas
    celui-ci, et le lancer couterait des minutes.
    """
    if "#" in temoin:
        fichier, _, fonction = temoin.partition("#")
        cible = base / fichier
        return cible.is_file() and f"def {fonction}" in cible.read_text(encoding="utf-8")
    premier = temoin.split()[0] if temoin.split() else ""
    return bool(premier) and (base / premier).is_file()


def releve_des_contrats(base: pathlib.Path):
    """Le module d inference, charge depuis son fichier au nom non importable.

    Extrait de `suspects()` pour que `_auto_test` puisse interroger la VRAIE inference (issue
    #5119). Un temoin qui recopierait ses reponses en dur ne verrait pas son vocabulaire deriver,
    et c est precisement ce que le couplage de `seuil_resolu` demande de tenir.
    """
    sys.path.insert(0, str(base / "scripts" / "methode"))
    import importlib.util

    spec = importlib.util.spec_from_file_location(
        "releve", base / "scripts" / "methode" / "contrats-des-gardes.py"
    )
    releve = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(releve)
    return releve


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Un suspect par CONTRADICTION entre ce qu un contrat declare et ce que le garde fait."""
    base = racine or RACINE_DEPOT
    releve = releve_des_contrats(base)

    trouves = []
    for chemin in fichiers(racine):
        contrat = contrat_de(chemin)
        if contrat is None:
            continue
        vu = chemin.relative_to(base).as_posix()
        texte = chemin.read_text(encoding="utf-8", errors="ignore")

        if not temoin_existe(contrat.get("temoin", ""), base):
            trouves.append(f"{vu}  temoin declare introuvable : {contrat.get('temoin')!r}")

        if chemin.suffix != ".py":
            continue  # l inference ne lit que le Python ; pour le shell, seul le temoin se confronte

        declare = CHIFFRE.findall(contrat.get("decision", ""))
        infere = releve.numeros_adr(texte)
        if declare and infere and not set(declare) & set(infere):
            trouves.append(f"{vu}  decision {declare} contredit l ADR rendue {infere}")

        d = corpus_resolu(contrat.get("population", ""))
        i = corpus_resolu(releve.population(texte))
        if d is not None and i is not None and d != i:
            trouves.append(f"{vu}  population declaree {sorted(d)} contredit {sorted(i)}")

        seuil_infere = releve.seuil(texte, base / "dev-docs" / "decisions")
        if seuil_contredit(contrat.get("seuil", ""), seuil_infere):
            declare = CHIFFRE.search(contrat.get("seuil", "")).group(1)
            trouves.append(f"{vu}  seuil declare {declare} contredit {seuil_resolu(seuil_infere)}")
    return trouves


def _auto_test() -> int:
    """Les DEUX moities : une contradiction est vue, et un desaccord de VOCABULAIRE ne l est pas."""
    echecs = 0

    def verifie(libelle, obtenu, attendu):
        nonlocal echecs
        if obtenu == attendu:
            print(f"  ✔ {libelle}")
        else:
            print(f"  ✘ {libelle} : attendu {attendu}, obtenu {obtenu}")
            echecs = 1

    verifie(
        "RACINES et PRODUCTION + TESTS disent le meme corpus",
        corpus_resolu("RACINES") == corpus_resolu("PRODUCTION + TESTS"),
        True,
    )
    verifie(
        "TESTS seul n est pas les deux arbres",
        corpus_resolu("TESTS") == corpus_resolu("RACINES"),
        False,
    )
    verifie(
        "une population inconnue ne se resout pas, donc ne contredit rien",
        corpus_resolu("(non declaree)"),
        None,
    )
    # Le champ `seuil` (issue #5119). L inference DIT quand elle ne conclut pas, et son aveu ne
    # contredit rien - la meme regle que `corpus_resolu` tient pour la population.
    verifie(
        "une inference a deux ADR n a resolu aucun seuil",
        seuil_resolu("(2 ADR : 4395, 4587)"),
        None,
    )
    verifie(
        "et le contrat juste qu elle refusait passe desormais",
        seuil_contredit("3280, polarite=monte", "(2 ADR : 4395, 4587)"),
        False,
    )
    verifie("un cliquet conclut, sur son nombre", seuil_resolu("cliquet 43"), "43")
    verifie("un plancher aussi", seuil_resolu("plancher 3280"), "3280")

    # Ce qui empeche le correctif de rendre le garde AVEUGLE : un desaccord reel refuse toujours.
    verifie(
        "un seuil declare qui contredit un cliquet resolu reste vu",
        seuil_contredit("0, polarite=descend", "cliquet 43"),
        True,
    )
    verifie(
        "et le meme seuil ne se contredit pas lui-meme",
        seuil_contredit("43, polarite=descend", "cliquet 43"),
        False,
    )

    # Le COUPLAGE au vocabulaire de `seuil()`, tenu sur un garde REEL. Sans lui, un renommage dans
    # l inference ferait cesser toute comparaison de seuil sans qu aucun temoin ne se plaigne : le
    # garde deviendrait vert en ayant cesse de juger. Les deux bouts se lisent, aucun n est ecrit
    # en dur, si bien que resserrer le cliquet de 4472 laisse ce cas vert.
    _releve = releve_des_contrats(RACINE_DEPOT)
    _garde_a_une_adr = RACINE_DEPOT / "scripts" / "adr" / "4472-commentaire-en-corps.py"
    _rendu = _releve.seuil(
        _garde_a_une_adr.read_text(encoding="utf-8"), RACINE_DEPOT / "dev-docs" / "decisions"
    )
    verifie(
        "le seuil d un garde reel se resout, et sur le cliquet que son ADR declare",
        seuil_resolu(_rendu),
        str(cliquet("4472")),
    )

    verifie(
        "un temoin nomme une fonction qui existe",
        temoin_existe("scripts/adr/verifie_scripts.py#test_0008_echec_silencieux", RACINE_DEPOT),
        True,
    )
    verifie(
        "un temoin qui nomme une fonction absente est vu",
        temoin_existe("scripts/adr/verifie_scripts.py#test_fantome", RACINE_DEPOT),
        False,
    )
    verifie(
        "un temoin qui nomme un fichier absent est vu",
        temoin_existe("scripts/adr/fantome.py --auto-test", RACINE_DEPOT),
        False,
    )

    # La protection contre la RECURSION. Sans elle, ce garde se lancait LUI-MEME : il n avait pas de
    # branche `--contrat`, tombait donc dans son travail, et se rappelait. Les sous-processus
    # essaimaient plus vite que leur plafond ne les tuait, et la machine est tombee (2026-09-02).
    verifie(
        "ce garde ne se lance jamais lui-meme",
        contrat_de(pathlib.Path(__file__).resolve()),
        None,
    )
    # Et la barriere de SURETE : une mention en prose ne suffit pas a faire lancer un script, parce
    # qu un script sans cette branche ignore l argument et FAIT SON TRAVAIL.
    verifie(
        "une mention en commentaire ne vaut pas dispatch",
        dispatche_en_code(pathlib.Path("x.sh"), "# on parle de --contrat ici\necho bonjour\n"),
        False,
    )
    verifie(
        "mais une ligne de code, oui",
        dispatche_en_code(pathlib.Path("x.sh"), 'if [ "$1" = "--contrat" ]; then\n'),
        True,
    )

    print()
    print("Auto-test concluant." if not echecs else "Auto-test EN ÉCHEC.")
    return echecs


# Ce que ce garde DECLARE etre. Il en portait aucun, et c est ce qui l a rendu dangereux : sans
# branche `--contrat`, s appeler lui-meme le faisait tomber dans son travail, donc se rappeler.
CONTRAT = {
    "geste": "contrat declare qui contredit ce que le garde fait",
    "population": "les points d entree qui mentionnent un contrat",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_un_contrat_ne_contredit_pas_le_garde",
    "decision": "ADR 4636",
}


if __name__ == "__main__":
    # AVANT tout le reste : un contrat s imprime sans rien lire et sans rien exiger. Ce garde en
    # avait besoin plus que les autres, puisqu il LANCE ceux qu il inventorie.
    if "--contrat" in sys.argv:
        sys.exit(
            imprime_contrat(
                pathlib.Path(__file__).resolve().relative_to(RACINE_DEPOT).as_posix(), CONTRAT
            )
        )
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    sys.exit(
        rapporte(
            ADR,
            "contrats qui contredisent ce que le garde fait",
            suspects(),
            lus=len(fichiers()),
        )
    )
