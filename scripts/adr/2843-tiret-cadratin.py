#!/usr/bin/env python3
"""ADR 2843 - Le tiret cadratin ne se corrige pas d'un coup, il se cliquette.

La convention est écrite deux fois (`CONTRIBUTING.md`, `dev-docs/ajouter-une-fonctionnalite.md`) et
n'était appliquée par rien. Elle a été enfreinte pendant la clôture du chantier #2348, dans un message
de commit et un corps de PR, sans qu'aucun contrôle ne bronche : l'écart n'a été vu que parce que
quelqu'un a relu.

Pourquoi « probable » et non « certaine » : un tiret cadratin peut être **cité** légitimement, par
exemple dans un commentaire qui explique justement la règle, ou dans une chaîne reproduisant un texte
externe. Aucun motif ne sait faire cette différence.

Deux régimes, et c'est le point de conception de ce script.

**Le cliquet** ne porte plus que sur `src/test/java`, seule zone encore loin du plancher. Un cliquet
unique qui aurait aussi compté la documentation pourrait **masquer** une régression dans l'une par un
nettoyage dans l'autre : le total resterait stable et le verdict vert.

**La tolérance zéro** porte sur les zones **déjà nettoyées** ([#ZONES_NETTOYEES]) : la documentation,
et depuis #2365 `src/main/java`. Une zone au plancher n'a pas besoin d'une marge, elle a besoin d'un
refus, et un refus ne se masque pas. Une zone passe de l'un à l'autre le jour où elle touche zéro,
dans la tranche même qui l'y amène : sinon l'arbre est propre et rien ne le garde.
"""

import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import TESTS, rapporte, sort_si_contrat_demande

SOURCES = [TESTS]

CADRATIN = "—"

# Fin de ligne conservée pour situer le suspect : c'est un humain qui tranche, extrait en main.
EXTRAIT = 90

# Ce qui est **cité** ou **affiché** n'est pas notre prose. Cinq formes, une seule règle :
#
# - le glyphe de valeur absente entre chevrons de code ;
# - un libellé de l'application entre guillemets français, qu'une fiche d'écran reproduit fidèlement ;
# - un cadratin **seul dans une cellule de tableau**, qui est le même glyphe posé dans du Markdown ;
# - un littéral Java réduit au glyphe (`"—"`), qui **est** le glyphe : sa définition dans `Formats`,
#   ses concaténations, et les tests qui l'affirment comme valeur attendue ;
# - la classe de caractères `[-—]` **littérale**, par laquelle les trois analyseurs d'en-têtes d'ADR
#   (`_commun.py`, `resserre_cliquets.py`, `DocumentationAJourTest`) acceptent encore l'ancienne forme.
#
# Cette dernière est volontairement **littérale** et non « une classe contenant un cadratin » : un tel
# motif avalerait les libellés de liens Markdown, où un tiret posé entre crochets est de la prose
# ordinaire. Chaque élargissement de ce motif est un risque de déflation silencieuse du compteur, donc
# chacun se taille au plus juste. Cette phrase-ci en a fait la démonstration : elle donnait d'abord
# l'exemple du lien en clair, et la tolérance zéro l'a refusée.
#
# Les cinq se reconnaissent à leur encadrement. Cinq listes d'exceptions auraient dérivé séparément.
CITE = re.compile(
    "\\[-"
    + CADRATIN
    + '\\]|"'
    + CADRATIN
    + '"|`'
    + CADRATIN
    + "`|«[^»\n]*»|\\|\\s*"
    + CADRATIN
    + "\\s*(?=\\|)"
)


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les unités que ce garde LIT, extraites pour que `lus` les compte (issue #5007).

    Le parcours vivait dans `suspects()`, qui ne rendait que ce qu'il RETENAIT. Un ciblage manqué
    donnait donc zéro suspect sur zéro fichier, et ce zéro passait pour un succès.

    Le tri est fait ARBRE PAR ARBRE, comme dans la boucle d'origine. Trier globalement sur les deux
    arbres les entrelacerait : le compte ne bougerait pas, mais l'ordre de l'aperçu si.
    """
    arbres = [racine] if racine is not None else SOURCES
    return [f for a in arbres if a.exists() for f in sorted(a.rglob("*.java"))]


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les lignes Java portant un tiret cadratin **de prose**, citations exclues.

    Citations exclues via [#CITE], comme dans les zones Markdown, et pour la même raison : la mesure
    doit vouloir dire « notre prose ». Comptée brute, elle butait sur un plancher de 50 citations
    légitimes du glyphe, plancher que personne n'aurait su distinguer d'un reste de travail. Un
    cliquet dont on ignore le plancher est un cliquet sur lequel on ne peut pas clore le chantier.

    `racine` sert au garde-fou `verifie_scripts.py`, qui pointe le script vers une fixture jetable.
    Sans elle, on balaie `SOURCES`, c'est-à-dire l'arbre de TEST seul. La production en est sortie
    quand elle a atteint zéro : le cliquet ne garde plus que la zone qui reste loin du plancher, ce
    que l'en-tête du module dit depuis. Cette phrase annonçait « les deux arbres », et le fichier se
    contredisait alors trois fois contre une. Le champ `lus` de la ligne de verdict porte le compte
    du jour ; il n'est pas recopié ici, un chiffre en prose vieillissant sans que rien ne le dise.
    """
    trouves = []
    for source in fichiers(racine):
        contenu = source.read_text(encoding="utf-8")
        for numero, ligne in enumerate(contenu.splitlines(), 1):
            if CADRATIN in CITE.sub("", ligne):
                trouves.append(f"{source}:{numero}  {ligne.strip()[:EXTRAIT]}")
    return trouves


# Les zones **nettoyées** par #2365, en tolérance zéro. Ajouter une tranche revient à ajouter **une
# ligne** ici : c'est délibéré, chaque tranche touchant ce même script, et une insertion d'une ligne
# se résout sans réfléchir là où un bloc de code aurait sérialisé les tranches.
#
# Tolérance zéro et non cliquet : une zone au plancher n'a pas besoin d'une marge, et une marge
# partagée avec une zone encore loin du plancher resterait masquable.
ZONES_NETTOYEES = (
    ("documentation utilisateur", pathlib.Path("docs"), (), "*.md"),
    ("brief projet", pathlib.Path("brief"), (), "*.md"),
    # `decisions` incluse depuis la migration de format : les séparateurs des en-têtes d'ADR sont
    # passés au tiret simple, et les trois analyseurs qui les lisent acceptent les deux formes.
    ("documentation développeur", pathlib.Path("dev-docs"), (), "*.md"),
    # `src/main/java` est au plancher depuis #2365 : la promotion du cliquet à la tolérance zéro est
    # la règle de cette ADR, et elle ferme la fenêtre où l'arbre serait propre mais non gardé. Le
    # cliquet ne porte donc plus que sur `src/test/java` ([#SOURCES]).
    ("sources principales", pathlib.Path("src/main/java"), (), "*.java"),
    # Les familles **hors Java et hors Markdown**, nettoyées elles aussi. Chaque zone est ancrée sur
    # son répertoire réel plutôt que sur la racine du dépôt : un balayage depuis `.` verrait aussi les
    # fichiers **non suivis**, et un artefact local ferait alors rougir le garde chez le développeur
    # sans rien signaler en CI. Un garde qui ment selon la machine ne vaut rien.
    ("feuilles de style", pathlib.Path("src/main/java"), (), "*.css"),
    ("migrations de schéma", pathlib.Path("src/main/resources/db/migration"), (), "*.sql"),
    ("scripts", pathlib.Path("scripts"), (), "*.py"),
    # Les competences d agent (#4337). Elles arrivent propres, et c est justement pourquoi la
    # zone se pose maintenant : le regime de couverture ne signale que les fichiers qui PORTENT
    # deja un cadratin, donc un corpus neuf et propre lui est invisible. Seule la SOURCE est
    # gardee : `.claude/skills` en est une copie, tenue identique par le garde des adaptateurs.
    #
    # Les six competences OpenSpec ont ete ADOPTEES (#4515) : reecrites en francais et tissees au
    # cycle de ce depot, elles ne sont plus du texte amont et n ont plus a etre exemptees. Leurs
    # dix-sept cadratins sont tombes a zero avec la reecriture, ce qui se mesure avant et apres.
    # L exclusion qui les nommait une a une a donc disparu, et la zone les couvre comme le reste.
    #
    # Les six COMMANDES `.claude/commands/opsx/` ont disparu (#4516) : six relais d une ligne
    # les remplacent, nommes par le geste, ecrits ici et non repris. Leurs trois exemptions de
    # [#HORS_COUVERTURE] sont tombees avec elles, et plus aucun texte OpenSpec n est amont.
    ("competences d agent", pathlib.Path(".agents/skills"), (), "*.md"),
    # La spécification vivante (#4513). Elle se déclare **avant** de porter quoi que ce soit, et
    # c'est tout l'objet de l'entrée : le régime de couverture voit un fichier qui PORTE déjà un
    # cadratin, mais son refus propose deux issues, « ajoutez la zone » ou « inscrivez-les dans
    # HORS_COUVERTURE ». Le premier artefact écrit ici trouverait donc la porte de l'exemption
    # ouverte devant lui. La zone posée d'avance n'en laisse qu'une : réécrire le texte.
    #
    # Glob total, pour la raison des tests de paquet plus bas, et pour une seconde qui se mesure :
    # `openspec/` ne contient aujourd'hui que `config.yaml`, aucun `.md`. Une entrée en `*.md`
    # ferait donc LEVER `prose()` au lieu de garder le dossier. Le glob couvre la configuration
    # maintenant et les artefacts quand ils arriveront, sans seconde entrée à tenir.
    ("spécification vivante", pathlib.Path("openspec"), (), "*"),
    ("gardes de capture", pathlib.Path(".github/assets"), (), "*.sh"),
    # Les scripts d'atelier, dont le garde de titre de PR. Cette zone manquait, et le régime de
    # couverture l'a signalée dès que ce garde a porté sa première ligne de prose (#2947).
    ("scripts d'atelier", pathlib.Path(".github/scripts"), (), "*.sh"),
    ("ateliers d'intégration", pathlib.Path(".github/workflows"), (), "*.yml"),
    # Glob total : ce dossier ne contient que des `.bats` et le `.bash` qu'ils chargent, et deux
    # entrées pour deux extensions du même petit dossier se seraient désynchronisées.
    ("tests de paquet", pathlib.Path("src/test/bats"), (), "*"),
    ("schémas de contrat", pathlib.Path("src/test/resources/vigiechiro"), (), "*.json"),
    ("collection d'exploration", pathlib.Path("dev-docs/api"), (), "*.json"),
    # Deux fichiers seuls à la racine. `target` est exclu : Maven peut y déposer des copies, et le
    # garde n'a rien à dire sur un produit de build.
    ("configuration Maven", pathlib.Path("."), ("target",), "pom.xml"),
    ("configuration du site", pathlib.Path("."), ("target",), "mkdocs*.yml"),
    # La documentation **à la racine** (`README`, `CONTRIBUTING`, `AGENTS`…), en balayage NON
    # RÉCURSIF : les sous-arbres ont déjà leurs zones, et descendre depuis `.` ramasserait les fichiers
    # non suivis. Cette zone manquait à la clôture de #2365, et `CONTRIBUTING.md`, le fichier qui
    # **énonce** la convention, portait encore deux cadratins de prose. Une règle sans garde n'est pas
    # appliquée, pas même par qui l'écrit.
    #
    # `CHANGELOG.md` en est exclu : il est **produit** par semantic-release depuis les sujets de
    # commits déjà fusionnés. Le corriger falsifierait le compte rendu de ce qui a réellement été
    # livré, et la ligne réécrite reviendrait à la génération suivante. Ce qu'il faut garder, ce sont
    # les **titres de PR** à la source, pas leur report.
    ("documentation racine", pathlib.Path("."), ("CHANGELOG.md",), "*.md", False),
)


def prose(
    racine: pathlib.Path,
    exclus: tuple[str, ...] = (),
    motif: str = "*.md",
    recursif: bool = True,
) -> list[str]:
    """Les cadratins de **prose** d'une zone nettoyée, citations exclues.

    `racine` est explicite : le garde-fou `verifie_scripts.py` y pointe une fixture jetable, et
    [#ZONES_NETTOYEES] la fournit pour chaque zone du dépôt. `exclus` nomme les sous-dossiers encore
    en chantier, pour qu'une zone puisse se garder **par morceaux** au lieu d'attendre d'être entière.
    `motif` porte l'extension : une zone de documentation et une zone de sources se gardent pareil.

    `recursif=False` limite le balayage au **niveau de `racine`**, sans descendre. C'est ce qui rend
    gardable la documentation de la racine du dépôt : les sous-arbres ont déjà leurs zones, et une
    descente depuis `.` ramasserait les fichiers **non suivis**, faisant rougir le garde chez le
    développeur sans rien signaler en CI.

    Lève si la zone ne voit **aucun fichier**. Un motif mal apparié à son arbre (`*.md` sur un arbre
    Java) rapporterait « 0 cadratin de prose » à jamais, et ce vert-là serait indétectable : il a la
    forme exacte du succès. Une zone déclarée qui ne balaie rien est une erreur de configuration.
    """
    trouves = []
    if not racine.exists():
        return trouves
    balayage = racine.rglob(motif) if recursif else racine.glob(motif)
    # `is_file()` n'est pas une precaution de style : la zone « tests de paquet » balaie `*`, donc un
    # sous-dossier y entre dans la liste et `read_text` leve `IsADirectoryError`. Le garde plantait
    # alors au lieu de rendre un verdict - et il plantait CHEZ LE DEVELOPPEUR seulement, puisqu'un
    # arbre fraichement clone n'en porte pas. C'est exactement ce que le commentaire de
    # [#ZONES_NETTOYEES] promet d'eviter : « un garde qui ment selon la machine ne vaut rien ».
    pages = [
        p for p in sorted(balayage) if p.is_file() and not any(part in exclus for part in p.parts)
    ]
    if not pages:
        raise AssertionError(
            f"zone « {racine} » : aucun fichier « {motif} », le garde ne balaie rien"
        )
    for page in pages:
        for numero, ligne in enumerate(page.read_text(encoding="utf-8").splitlines(), 1):
            if CADRATIN in CITE.sub("", ligne):
                trouves.append(f"{page}:{numero}  {ligne.strip()[:EXTRAIT]}")
    return trouves


# Les fichiers **délibérément** hors couverture, avec leur motif. Un fichier généré depuis des sujets de
# commits déjà fusionnés ne se corrige pas : la ligne réécrite falsifierait le compte rendu de ce qui a
# été livré, et reviendrait à la génération suivante.
# Les trois COMMANDES OpenSpec qui portaient un cadratin sont sorties de cette table avec le
# dossier qui les portait (#4516). Le motif `AMONT` disparaît avec elles : plus aucun texte
# OpenSpec n'est repris verbatim de l'outil, tout est écrit ici.
HORS_COUVERTURE = {
    "CHANGELOG.md": "généré par semantic-release depuis les sujets de commits fusionnés",
}


def couvert(chemin: pathlib.Path) -> bool:
    """Ce fichier tombe-t-il dans une zone en tolérance zéro, ou sous le cliquet ?"""
    for zone in ZONES_NETTOYEES:
        _, racine, exclus, motif = zone[:4]
        recursif = zone[4] if len(zone) > 4 else True
        if not racine.exists():
            continue
        vus = racine.rglob(motif) if recursif else racine.glob(motif)
        if chemin in set(vus) and not any(part in exclus for part in chemin.parts):
            return True
    return any(arbre in chemin.parents for arbre in SOURCES)


def sans_garde() -> list[str]:
    """Les fichiers **suivis** portant un cadratin que rien ne garde.

    Le troisième régime, et celui qui manquait. Les deux autres répondent « combien reste-t-il ? » ;
    celui-ci répond « **qu'est-ce que personne ne regarde ?** ». La différence n'est pas théorique : à
    la première clôture de #2365, les deux premiers régimes étaient au vert alors que `CONTRIBUTING.md`,
    le fichier qui **énonce** la convention, portait encore deux cadratins de prose. Aucune zone ne
    couvrait le niveau racine, et compter ce qui reste ne dit rien de ce qui est gardé.

    La liste part de `git ls-files` : les fichiers **suivis**, donc ni les artefacts de build ni le
    brouillon local de qui lance le script.
    """
    suivis = subprocess.run(
        ["git", "ls-files"], capture_output=True, text=True, check=True
    ).stdout.splitlines()
    orphelins = []
    for nom in suivis:
        chemin = pathlib.Path(nom)
        if nom in HORS_COUVERTURE or not chemin.is_file():
            continue
        try:
            contenu = chemin.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue  # binaire ou illisible : pas de prose à garder
        if CADRATIN in CITE.sub("", contenu) and not couvert(chemin):
            orphelins.append(nom)
    return orphelins


CONTRAT = {
    "geste": "tiret cadratin dans une source Java",
    "population": "TESTS",
    "dispositif": "cliquet",
    "seuil": "1, polarite=descend",
    "temoin": "scripts/adr/verifie_scripts.py#test_2843_tiret_cadratin",
    "decision": "ADR 2843",
}


if __name__ == "__main__":
    sort_si_contrat_demande(__file__, CONTRAT)
    code = rapporte("2843", "tiret cadratin dans une source Java", suspects(), lus=len(fichiers()))

    # Une zone déclare quatre champs, et un cinquième **optionnel** : le balayage non récursif, qui ne
    # sert qu'à la racine du dépôt. Le défaut porté ici plutôt qu'un `True` répété sur chaque ligne
    # garde la table lisible et fait ressortir le seul cas particulier.
    for zone in ZONES_NETTOYEES:
        libelle, racine, exclus, motif = zone[:4]
        rechutes = prose(racine, exclus, motif, zone[4] if len(zone) > 4 else True)
        print(f"\n{libelle} : {len(rechutes)} cadratin(s) de prose (tolérance zéro)")
        for suspect in rechutes:
            print(f"  {suspect}")
        if rechutes:
            print(
                f"\nLa zone « {libelle} » est nettoyée : un cadratin de prose y est une rechute.\n"
                "Écrivez un deux-points ou une virgule. Si vous citez le glyphe de valeur absente ou\n"
                "un libellé de l'application, encadrez-le de guillemets français ou de chevrons de code."
            )
            code = 1

    orphelins = sans_garde()
    print(f"\ncouverture : {len(orphelins)} fichier(s) porteur(s) que rien ne garde")
    for nom in orphelins:
        print(f"  {nom}")
    if orphelins:
        print(
            "\nCes fichiers portent un cadratin de prose et ne tombent dans aucune zone ni sous le\n"
            "cliquet. Ajoutez la zone qui les couvre à ZONES_NETTOYEES, ou inscrivez-les dans\n"
            "HORS_COUVERTURE avec le motif de leur exemption. Un fichier propre mais non gardé\n"
            "rechute sans bruit : c'est ainsi que CONTRIBUTING.md, qui énonce la convention, l'a\n"
            "enfreinte pendant tout le chantier qui l'appliquait."
        )
        code = 1

    sys.exit(code)
