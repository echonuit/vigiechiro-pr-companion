#!/usr/bin/env python3
"""Refus de l annotation et de la marque qui font taire le portail qualite (ADR 6022, article A10).

**Ce qui a ouvert ce garde.** Deux methodes mortes ont vecu sous `@SuppressWarnings("unused")` dans
les scenarios d emport, du 2026-08-30 au 2026-10-06. Le cliquet de l ADR 4617 est reste a 40 apres
leur retrait (#6028) : PMD ne les comptait pas. L annotation cachait le code mort au portail, pas
seulement au lecteur. Quatre surfaces ecrivaient pourtant la regle, et la matrice de la Constitution
rangeait l article A10 parmi ceux que seule la relecture tient.

**Une liste FERMEE de valeurs admises, pas une liste de refus.** `unchecked` et `rawtypes`
s adressent a javac, sur un transtypage generique qu il ne peut pas prouver : PMD ne les lit pas, et
les 55 annotations du depot sont de celles-la. Toute autre valeur est refusee. Une liste de refus
(`unused`, `PMD.*`) aurait laisse passer `all`, que PMD honore aussi, et la prochaine valeur qu il
apprendra a honorer.

**Ce que PMD honore, MESURE et non lu dans sa documentation.** Le 2026-10-07 sur `80ae6d71db`, PMD
7.17.0, une classe jetable de dix-huit methodes privees mortes jouee contre `UnusedPrivateMethod` :

- il se tait sur `"unused"`, `"all"`, `"PMD"`, `"PMD.UnusedPrivateMethod"`, et sur un tableau qui
  contient l une de ces valeurs ;
- il ne se tait PAS sur `"unchecked"`, `"rawtypes"`, `"fallthrough"`, ni sur le nom d une AUTRE
  regle, ni sur `"unused, unchecked"` ecrit en une seule chaine ;
- une marque `NOPMD` agit dans un commentaire de LIGNE pose sur la ligne meme de la violation :
  `// NOPMD`, colle ou non, seul ou au milieu d une phrase, et aussi un `///` de fin de ligne ;
- elle n agit PAS dans un bloc `/* NOPMD */`, ni en minuscules, ni depuis un `///` pose sur la
  ligne d au-dessus.

**La marque se refuse donc la ou PMD la lit, et pas ailleurs.** Un commentaire de ligne qui porte
`NOPMD` en majuscules n est suspect que s il SUIT du code sur sa ligne. Deux fichiers citent le mot
en prose, `HorairesDistants.java` et `CourbesActivite.java`, sur des lignes qui ne portent que du
commentaire : aucune violation ne peut y commencer, et les refuser ferait rougir ce garde sur la
phrase qui enonce sa propre regle. C est aussi pourquoi la lecture passe par l arbre syntaxique : un
motif textuel prend ces deux mentions pour des occurrences, soit deux sur deux.

**Ce qu il ne lit pas, et il le dit.**

- Une valeur qui n est pas un litteral, constante ou concatenation, est REFUSEE sans etre resolue :
  ce garde ne suit pas une constante jusqu a sa valeur, et ce qu il ne sait pas lire n est pas admis.
- Une zone que la grammaire n a pas su lire est fouillee comme du texte : `@SuppressWarnings` ou
  `NOPMD` y est refuse, puisque la structure qui les innocenterait manque.
- Il ne lit que le Java de `src/main/java` et `src/test/java`. Les exclusions ecrites dans
  `pmd-ruleset.xml` (`violationSuppressXPath`, `violationSuppressRegex`) se voient au diff du jeu de
  regles et ne sont pas de son ressort.
- Une marque `NOPMD` posee sur une ligne de commentaire seule est toleree parce qu elle ne fait rien
  taire. Si une regle de PMD venait a signaler des commentaires, cette tolerance serait a revoir.
"""

from __future__ import annotations

import contextlib
import io
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from _commun import RACINE_DEPOT, RACINES, cas_d_auto_test, rapporte, sort_si_contrat_demande
from _commun.arbre import arbre, fichiers_java, noeuds_de_type, zones_illisibles

ADR = "6022"

# Les SEULES valeurs admises. En ajouter une est une decision : elle se prend dans l ADR, pas ici.
ADMISES = frozenset({"unchecked", "rawtypes"})

ANNOTATION = "SuppressWarnings"
MARQUE = "NOPMD"

# Ce que l argument d une annotation peut envelopper avant d arriver a ses valeurs.
_ENVELOPPES = frozenset({"element_value_array_initializer", "annotation_argument_list"})


def _nom(annotation) -> str:
    """Le nom simple d une annotation : `java.lang.SuppressWarnings` rend `SuppressWarnings`."""
    nom = annotation.child_by_field_name("name")
    return "" if nom is None else nom.text.decode("utf-8", "replace").rsplit(".", 1)[-1]


def _valeurs(noeud) -> list:
    """Les noeuds de VALEUR d un argument d annotation, a plat, par la structure.

    `("a")`, `({"a", "b"})` et `(value = {"a"})` rendent tous leurs litteraux. Une valeur qui n est
    pas un litteral - `X.Y`, `"un" + "used"` - est rendue telle quelle, pour etre refusee.
    """
    if noeud.type in _ENVELOPPES:
        return [v for enfant in noeud.named_children for v in _valeurs(enfant)]
    if noeud.type == "element_value_pair":
        valeur = noeud.child_by_field_name("value")
        return [] if valeur is None else _valeurs(valeur)
    return [noeud]


def _refusee(valeur) -> str | None:
    """Le texte d une valeur que ce garde REFUSE, ou None si elle est admise."""
    texte = valeur.text.decode("utf-8", "replace")
    if valeur.type == "string_literal" and texte.strip('"') in ADMISES:
        return None
    return texte


def _annotations_refusees(racine_ast) -> list[tuple[int, str]]:
    """Chaque `@SuppressWarnings` dont une valeur au moins sort de la liste fermee."""
    trouvees = []
    for annotation in noeuds_de_type(racine_ast, ["annotation"]):
        if _nom(annotation) != ANNOTATION:
            continue
        arguments = annotation.child_by_field_name("arguments")
        valeurs = [] if arguments is None else _valeurs(arguments)
        refusees = [r for r in map(_refusee, valeurs) if r is not None]
        if refusees:
            ligne = annotation.start_point[0] + 1
            trouvees.append((ligne, f"@{ANNOTATION} porte {', '.join(refusees)}"))
    return trouvees


def _marques_refusees(racine_ast, lignes: list[bytes]) -> list[tuple[int, str]]:
    """Chaque commentaire de ligne portant la marque APRES du code, la ou PMD la lit."""
    trouvees = []
    for commentaire in noeuds_de_type(racine_ast, ["line_comment"]):
        if MARQUE not in commentaire.text.decode("utf-8", "replace"):
            continue
        rang, colonne = commentaire.start_point
        if lignes[rang][:colonne].strip():
            trouvees.append((rang + 1, f"marque {MARQUE} en fin de ligne de code"))
    return trouvees


def _zones_refusees(racine_ast, lignes: list[bytes]) -> list[tuple[int, str]]:
    """Dans une zone que la grammaire n a pas lue, le TEXTE fait foi, et il refuse."""
    trouvees = []
    for debut, fin in zones_illisibles(racine_ast):
        for rang in range(debut - 1, min(fin, len(lignes))):
            texte = lignes[rang].decode("utf-8", "replace")
            if "@" + ANNOTATION in texte or MARQUE in texte:
                trouvees.append((rang + 1, "zone illisible qui cite l annotation ou la marque"))
    return trouvees


def sites(source: str) -> list[str]:
    """Les refus d une SOURCE, `ligne  motif`, un seul par ligne. C est la porte de l auto-test."""
    octets = source.encode("utf-8")
    racine_ast = arbre(octets).root_node
    lignes = octets.split(b"\n")
    par_ligne: dict[int, str] = {}
    for ligne, motif in (
        _annotations_refusees(racine_ast)
        + _marques_refusees(racine_ast, lignes)
        + _zones_refusees(racine_ast, lignes)
    ):
        par_ligne.setdefault(ligne, motif)
    return [f"{ligne}  {motif}" for ligne, motif in sorted(par_ligne.items())]


def fichiers(racine: pathlib.Path | None = None) -> list[pathlib.Path]:
    """Les sources que ce garde LIT, extraites pour que `lus` les compte (ADR 5007)."""
    base = racine or RACINE_DEPOT
    return fichiers_java([base / zone for zone in RACINES])


def suspects(racine: pathlib.Path | None = None) -> list[str]:
    """Les refus sous `racine`, nommes par leur chemin, leur ligne et leur motif.

    La racine s INJECTE parce que `verifie_scripts.py` exerce le garde sur un arbre temporaire.
    """
    base = racine or RACINE_DEPOT
    retenus = []
    for fichier in fichiers(racine):
        ou = fichier.relative_to(base).as_posix()
        for site in sites(fichier.read_text(encoding="utf-8", errors="replace")):
            ligne, motif = site.split("  ", 1)
            retenus.append(f"{ou}:{ligne}  {motif}")
    return retenus


def lus(racine: pathlib.Path | None = None) -> int:
    """Le nombre de sources lues : ce que le garde a REGARDE."""
    return len(fichiers(racine))


def _lignes(source: str) -> list[int]:
    """Les seules lignes refusees d une source : ce que la plupart des cas confrontent."""
    return [int(site.split("  ", 1)[0]) for site in sites(source)]


def _code_de_main(faux_suspects: list[str], faux_lus: int) -> int:
    """Le code que `main` rend pour un corpus donne, sa sortie retenue.

    Le chemin de REFUS se joue ici : tous les autres cas eprouvent le calcul, et un `main` qui
    calculerait juste puis sortirait en 0 les passerait tous.
    """
    sauve = (globals()["suspects"], globals()["lus"], sys.argv)
    globals()["suspects"], globals()["lus"] = (lambda: faux_suspects), (lambda: faux_lus)
    sys.argv = [__file__]
    try:
        tampon = io.StringIO()
        with contextlib.redirect_stdout(tampon), contextlib.redirect_stderr(tampon):
            return main()
    finally:
        globals()["suspects"], globals()["lus"], sys.argv = sauve


def _auto_test() -> int:
    verifie, echecs = cas_d_auto_test()
    print("Auto-test de l annotation qui fait taire le portail (#6022) :")

    def methode(annotation: str, suite: str = "") -> str:
        return f"class T {{\n    {annotation}\n    private void morte() {{}}{suite}\n}}\n"

    # LES QUATRE FORMES QUE PMD HONORE, mesurees le 2026-10-07 : une par cas, chacune vue rouge.
    for valeur in ('"unused"', '"all"', '"PMD"', '"PMD.UnusedPrivateMethod"'):
        verifie(
            f"@SuppressWarnings({valeur}) est refusee",
            lambda v=valeur: _lignes(methode(f"@SuppressWarnings({v})")),
            [2],
        )
    verifie(
        "le motif nomme la valeur refusee",
        lambda: sites(methode('@SuppressWarnings("unused")')),
        ['2  @SuppressWarnings porte "unused"'],
    )

    # LES DEUX VALEURS ADMISES, seules puis ensemble : sans ces cas, un garde qui refuserait toute
    # annotation passerait ceux d au-dessus, et rougirait sur les 55 du depot.
    for valeur in ('"unchecked"', '"rawtypes"', '{"unchecked", "rawtypes"}', 'value = "unchecked"'):
        verifie(
            f"@SuppressWarnings({valeur}) est admise",
            lambda v=valeur: _lignes(methode(f"@SuppressWarnings({v})")),
            [],
        )

    # UNE SEULE VALEUR REFUSEE SUFFIT, dans un tableau comme sous `value =`. PMD s y tait.
    verifie(
        "une valeur refusee dans un tableau d admises est vue",
        lambda: sites(methode('@SuppressWarnings({"unchecked", "unused"})')),
        ['2  @SuppressWarnings porte "unused"'],
    )
    verifie(
        "une valeur refusee sous `value =` est vue",
        lambda: _lignes(methode('@SuppressWarnings(value = {"all"})')),
        [2],
    )

    # LA LISTE EST FERMEE : une valeur que PMD n honore PAS aujourd hui est refusee quand meme.
    verifie(
        "une valeur hors liste est refusee, meme inoffensive pour PMD",
        lambda: _lignes(methode('@SuppressWarnings("fallthrough")')),
        [2],
    )
    verifie(
        "deux valeurs dans une seule chaine ne sont pas deux valeurs admises",
        lambda: _lignes(methode('@SuppressWarnings("unchecked, rawtypes")')),
        [2],
    )

    # CE QUE LE GARDE NE SAIT PAS LIRE N EST PAS ADMIS.
    verifie(
        "une constante est refusee sans etre resolue",
        lambda: sites(methode("@SuppressWarnings(Motifs.INUTILISE)")),
        ["2  @SuppressWarnings porte Motifs.INUTILISE"],
    )
    verifie(
        "une concatenation est refusee",
        lambda: _lignes(methode('@SuppressWarnings("un" + "checked")')),
        [2],
    )
    verifie(
        "le nom qualifie de l annotation est reconnu",
        lambda: _lignes(methode('@java.lang.SuppressWarnings("unused")')),
        [2],
    )
    verifie(
        "une autre annotation n est pas lue",
        lambda: _lignes(methode('@DisplayName("unused")')),
        [],
    )

    # LA MARQUE, LA OU PMD LA LIT : un commentaire de ligne apres du code, quelle que soit sa forme.
    for forme in (
        "// NOPMD",
        "//NOPMD collee",
        "// le reflexe NOPMD, au milieu",
        "/// javadoc de fin de ligne NOPMD",
    ):
        verifie(
            f"« {forme} » en fin de ligne de code est refusee",
            lambda f=forme: sites(methode("", " " + f)),
            ["3  marque NOPMD en fin de ligne de code"],
        )

    # ET PAS AILLEURS. Les trois formes que PMD ignore, plus les deux mentions REELLES du depot,
    # recopiees telles quelles : les refuser ferait rougir le garde sur l enonce de sa propre regle.
    verifie(
        "un bloc /* NOPMD */ ne fait rien taire",
        lambda: _lignes(methode("", " /* NOPMD */")),
        [],
    )
    verifie("la marque en minuscules non plus", lambda: _lignes(methode("", " // nopmd")), [])
    verifie(
        "un commentaire seul sur sa ligne non plus",
        lambda: _lignes(methode("// NOPMD sur la ligne d au-dessus")),
        [],
    )
    horaires = (
        "/// qualité. Le dépôt refuse `@SuppressWarnings` ; on extrait.\n"
        "///\n"
        "final class HorairesDistants {}\n"
    )
    verifie("la mention en prose de HorairesDistants est verte", lambda: sites(horaires), [])
    courbes = (
        "/// portait `ActiviteController` à WMC=49, au-dessus du plafond God-class. Le réflexe "
        "`//NOPMD` est\n/// exclu.\nfinal class CourbesActivite {}\n"
    )
    verifie("la mention en prose de CourbesActivite est verte", lambda: sites(courbes), [])
    verifie(
        "une chaine qui cite l annotation n est pas une annotation",
        lambda: sites('class T { String s = "@SuppressWarnings(\\"unused\\") // NOPMD"; }\n'),
        [],
    )

    # PLUSIEURS SITES, aux bonnes lignes : un detecteur qui rendrait toujours [2] passerait le reste.
    plusieurs = (
        "class T {\n"
        '    @SuppressWarnings("unchecked")\n'
        "    void a() {}\n"
        '    @SuppressWarnings("unused")\n'
        "    void b() {}\n"
        "    void c() {} // NOPMD\n"
        "}\n"
    )
    verifie("plusieurs sites sont vus aux bonnes lignes", lambda: _lignes(plusieurs), [4, 6])

    # UNE ZONE ILLISIBLE NE BLANCHIT RIEN : le texte y fait foi. La source est choisie pour que
    # l arbre n y rende AUCUN noeud d annotation, ce que le cas d au-dessous atteste : une premiere
    # redaction de ce cas passait par le noeud, et survivait a l extinction de cette branche.
    casse = 'class T {\n    int x = 1 @SuppressWarnings("unused") ;\n}\n'
    verifie(
        "la source cassee ne rend aucun noeud d annotation",
        lambda: _annotations_refusees(arbre(casse.encode("utf-8")).root_node),
        [],
    )
    verifie(
        "et l annotation y est refusee quand meme, par le texte",
        lambda: sites(casse),
        ["2  zone illisible qui cite l annotation ou la marque"],
    )

    # LE REFUS LUI-MEME, et non plus le calcul : un seul suspect fait sortir en 1, sans marge.
    verifie(
        "un seul suspect fait REFUSER le garde",
        lambda: _code_de_main(['src/main/java/A.java:2  @SuppressWarnings porte "unused"'], 3),
        1,
    )
    verifie("aucun suspect le laisse passer", lambda: _code_de_main([], 3), 0)
    verifie("un corpus vide REFUSE au lieu de rendre zero", lambda: _code_de_main([], 0), 1)

    if echecs():
        print("auto-test : AU MOINS UN CAS A ROUGI")
        return 1
    print("auto-test : tous les cas sont verts")
    return 0


CONTRAT = {
    "geste": "annotation ou marque qui fait taire le portail qualite",
    "population": "PRODUCTION + TESTS : les `@SuppressWarnings` et les commentaires de ligne des "
    "sources Java, lus par l arbre syntaxique. Une annotation est suspecte des qu une de ses "
    "valeurs sort de la liste FERMEE `unchecked`, `rawtypes`, ou n est pas un litteral. Une marque "
    "`NOPMD` est suspecte quand son commentaire de ligne SUIT du code, seul endroit ou PMD la "
    "lit ; une mention sur une ligne de commentaire seule ne l est pas. LIMITES DECLAREES : les "
    "exclusions ecrites dans `pmd-ruleset.xml` ne sont pas lues, et une constante n est pas "
    "resolue, elle est refusee",
    "dispositif": "cliquet",
    "seuil": "0, polarite=descend",
    "temoin": "scripts/adr/6022-annotation-qui-fait-taire.py --auto-test",
    "decision": "ADR 6022",
    # Lire par l arbre coute. Declarer les chemins rend le garde indolore sur toute demande qui ne
    # touche pas de Java (ADR 5340). Un `chemins` INCOMPLET tait le garde en silence.
    "chemins": """
src/main/java/**
src/test/java/**
scripts/_commun/**
dev-docs/decisions/6022-*.md
""",
}


def main() -> int:
    sort_si_contrat_demande(__file__, CONTRAT)
    if "--auto-test" in sys.argv:
        return _auto_test()
    return rapporte(
        ADR,
        "annotation ou marque qui fait taire le portail qualite",
        suspects(),
        apercu=12,
        lus=lus(),
    )


if __name__ == "__main__":
    raise SystemExit(main())
