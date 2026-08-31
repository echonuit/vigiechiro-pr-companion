#!/usr/bin/env python3
"""Loupe : la densite de commentaire, par classe et par methode.

**Ce n est pas un cliquet, et ce ne peut pas en etre un.** Le denominateur est le code : un port
d une seule methode, une interface de contrat, un enum de quatre constantes plafonnent haut quoi
qu on y fasse, et un cliquet la-dessus pousserait a couper du contrat la ou il compte le plus. La
decision de l article A30 l ecarte explicitement comme mesure opposable.

**Ce qu elle fait, en revanche, mieux que le cliquet** : dire par ou commencer. Le cliquet de
longueur rend 994 lignes reparties sur des centaines de fichiers, sans hierarchie. La densite, elle,
designe les endroits ou le commentaire a **mange** le code - une classe de vingt lignes qui en porte
cent, une methode de trois lignes surmontee de quinze.

Deux lectures, parce qu elles ne montrent pas la meme chose :

- **par classe** : le fichier entier, javadoc de type comprise. Une densite haute peut etre saine -
  un port, un record de donnees - et c est la taille du code qui tranche ;
- **par methode** : la javadoc d une methode plus les `//` de son corps, sur les lignes de ce corps.
  Une densite haute y est plus suspecte : le contrat d une methode tient rarement en trois fois son
  implementation.

Usage :

    loupe-densite-de-commentaire.py             # les 20 plus denses de chaque lecture
    loupe-densite-de-commentaire.py --classes N # les N classes les plus denses
    loupe-densite-de-commentaire.py --methodes N
    loupe-densite-de-commentaire.py --auto-test
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import RACINES_ANCREES, RACINE_DEPOT, loupe  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero.
ADR = "4472"

RACINES = RACINES_ANCREES

# Deux planchers, parce que les deux lectures n ont pas la meme echelle.
#
# **Par classe : 25 lignes de code.** En dessous, la densite ne mesure plus la prose mais la nature
# du type : un port d une methode, une interface de contrat, un enum de quatre constantes plafonnent
# a 300-400 % quoi qu on y fasse, et ils occupaient toute la tete du classement. Ce sont eux que
# l article A30 nomme en ecartant cette mesure comme cliquet - la garder ici les ferait passer pour
# des priorites, ce qu ils ne sont pas.
#
# **Par methode : 5 lignes.** Un corps de cinq lignes est deja un corps, et une javadoc trois fois
# plus longue que lui est deja une question.
PLANCHER_CLASSE = 25
PLANCHER_METHODE = 5

DECLARATION = re.compile(r"\w\s*\([^;]*$|\w\s*\(.*\)\s*(\{|throws\b)")
TYPE = re.compile(r"\b(class|interface|enum|record)\s+(\w+)")
ANNOTATION = re.compile(r"^@\w")


def _commentaire(nu: str) -> bool:
    return nu.startswith("//")


def _code(nu: str) -> bool:
    """Une ligne qui porte du code : ni vide, ni commentaire, ni une accolade seule."""
    return bool(nu) and not _commentaire(nu) and nu not in {"{", "}", "});", ");"}


def par_classe(fichier: pathlib.Path) -> tuple[str, int, int] | None:
    """(nom du type, lignes de commentaire, lignes de code) pour le fichier entier."""
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    commentaire = sum(
        1 for l in lignes if _commentaire(l.strip()) and l.strip().lstrip("/").strip()
    )
    code = sum(1 for l in lignes if _code(l.strip()))
    if code < PLANCHER_CLASSE:
        return None
    return (fichier.stem, commentaire, code)


def par_methode(fichier: pathlib.Path) -> list[tuple[str, int, int, int]]:
    """(nom, ligne, lignes de commentaire, lignes de code) pour chaque methode du fichier.

    Le commentaire d une methode est sa javadoc **plus** les `//` de son corps : les deux parlent de
    la meme implementation, et les separer ferait passer pour maigre une methode dont tout le
    commentaire est descendu dans le corps.
    """
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    trouves, i, profondeur = [], 0, 0
    while i < len(lignes):
        nu = lignes[i].strip()
        if not _commentaire(nu) and profondeur == 1 and DECLARATION.search(nu):
            # La javadoc qui precede, annotations sautees.
            j, entete = i - 1, 0
            while j >= 0:
                avant = lignes[j].strip()
                if ANNOTATION.match(avant) or not avant:
                    j -= 1
                    continue
                if avant.startswith("//"):
                    if avant.lstrip("/").strip():
                        entete += 1
                    j -= 1
                    continue
                break
            # Le corps, jusqu a l accolade qui le referme.
            depart, corps, dedans, ouvert = i + 1, 0, 0, profondeur
            k = i
            while k < len(lignes):
                ligne = lignes[k].strip()
                if _commentaire(ligne):
                    if k > i and ligne.lstrip("/").strip():
                        dedans += 1
                else:
                    ouvert += ligne.count("{") - ligne.count("}")
                    if k > i and _code(ligne):
                        corps += 1
                    if ouvert <= profondeur and k > i:
                        break
                k += 1
            # Une declaration qui s ouvre sur `(` - un appel enchaine, un lambda - n a pas de nom
            # avant la parenthese : la ligne entiere fait alors office d etiquette.
            avant_paren = nu.split("(")[0].split()
            nom = avant_paren[-1] if avant_paren else nu[:40]
            if corps >= PLANCHER_METHODE:
                trouves.append((nom, depart, entete + dedans, corps))
            i = k + 1
            profondeur = ouvert
            continue
        if not _commentaire(nu):
            profondeur += nu.count("{") - nu.count("}")
        i += 1
    return trouves


def _ou(f: pathlib.Path) -> str:
    """Le chemin relatif a la racine du depot, ou le seul nom pour un arbre jetable de temoin."""
    return str(f.relative_to(RACINE_DEPOT)) if f.is_relative_to(RACINE_DEPOT) else f.name


def classes(racines=None) -> list[tuple[float, str, int, int]]:
    racines = racines or RACINES
    out = []
    for racine in racines:
        for f in sorted(racine.rglob("*.java")):
            mesure = par_classe(f)
            if mesure:
                _, com, code = mesure
                out.append((com / code, _ou(f), com, code))
    out.sort(reverse=True)
    return out


def methodes(racines=None) -> list[tuple[float, str, int, int]]:
    racines = racines or RACINES
    out = []
    for racine in racines:
        for f in sorted(racine.rglob("*.java")):
            for nom, ligne, com, code in par_methode(f):
                if com:
                    out.append((com / code, f"{_ou(f)}:{ligne}  {nom}()", com, code))
    out.sort(reverse=True)
    return out


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        def pose(contenu: str) -> None:
            (r / "A.java").write_text(contenu, encoding="utf-8")

        # Vingt-six lignes de code - la declaration du type EN EST une - pour trente de commentaire.
        pose(
            "\n".join(f"/// Ligne {i}." for i in range(30))
            + "\nclass A {\n"
            + "\n".join(f"    int c{i} = {i};" for i in range(25))
            + "\n}\n"
        )
        c = classes([r])
        cas.append(
            ("la densite d une classe se mesure", len(c) == 1 and c[0][2] == 30 and c[0][3] == 26)
        )

        # LE PLANCHER, et c est ce qui rend la loupe lisible : un port d une methode plafonne a
        # 300 % quoi qu on y fasse, et sans lui il trone en tete a la place des vrais cas.
        pose("/// Un contrat.\n/// Sur deux lignes.\ninterface B {\n    void f();\n}\n")
        cas.append(("un type sous le plancher est ignore", classes([r]) == []))

        # Une methode : sa javadoc PLUS les `//` de son corps, sur les lignes de son corps.
        pose(
            "class C {\n"
            "    /// Une.\n    /// Deux.\n"
            "    void f() {\n"
            "        // Trois.\n" + "\n".join(f"        int v{i} = {i};" for i in range(5)) + "\n"
            "    }\n}\n"
        )
        m = methodes([r])
        cas.append(("la javadoc et le corps comptent ensemble", len(m) == 1 and m[0][2] == 3))
        cas.append(("le denominateur est le corps seul", m[0][3] == 5))
        cas.append(("la methode est nommee", "f()" in m[0][1]))

        # LA borne qui distingue les deux lectures : un CHAMP surmonte de javadoc ne doit pas
        # passer pour une methode. Sans elle, chaque champ documente serait un faux positif.
        pose(
            "class D {\n    /// Un champ.\n    private int x = 1;\n"
            + "\n".join(f"    private int y{i} = {i};" for i in range(6))
            + "\n}\n"
        )
        cas.append(("un champ n est pas une methode", methodes([r]) == []))

        # Une methode sans commentaire n a pas de densite a montrer.
        pose(
            "class E {\n    void g() {\n"
            + "\n".join(f"        int v{i} = {i};" for i in range(6))
            + "\n    }\n}\n"
        )
        cas.append(("une methode sans commentaire ne sort pas", methodes([r]) == []))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(
            f"\n{len(rates)} cas en échec : la loupe ne mesure pas ce qu'elle annonce.",
            file=sys.stderr,
        )
        return 1
    print(f"\n{len(cas)} cas : la loupe mesure les deux densités et ignore ce qui n'en a pas.")
    return 0


def _rend(titre: str, mesures: list[tuple[float, str, int, int]], combien: int) -> list[str]:
    return [
        f"{densite * 100:5.0f} %  {com:4} com. / {code:4} code  {ou}"
        for densite, ou, com, code in mesures[:combien]
    ]


def main() -> int:
    combien = 20
    for drapeau in ("--classes", "--methodes"):
        if drapeau in sys.argv:
            i = sys.argv.index(drapeau)
            n = (
                int(sys.argv[i + 1])
                if i + 1 < len(sys.argv) and sys.argv[i + 1].isdigit()
                else combien
            )
            mesures = classes() if drapeau == "--classes" else methodes()
            return loupe(ADR, f"densité de commentaire, {drapeau[2:]}", _rend("", mesures, n))

    lignes = ["« par classe »"] + _rend("", classes(), combien)
    lignes += ["", "« par méthode »"] + _rend("", methodes(), combien)
    return loupe(ADR, "densité de commentaire, par classe et par méthode", lignes)


if __name__ == "__main__":
    sys.exit(_auto_test() if "--auto-test" in sys.argv else main())
