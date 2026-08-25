#!/usr/bin/env python3
"""Cliquet sur les blocs de javadoc qui racontent au lieu de contracter.

Le code de production porte des milliers de lignes de PROSE en javadoc. Les etiquettes de contrat
- `@param`, `@return`, `@throws` - n en font qu une faible part : le reste est du recit.

Beaucoup de ce recit est l histoire d un depot qui n existe plus. Un bloc de 89 lignes racontait
l implementation qu il avait remplacee ; un autre, 49 lignes, recopiait mot pour mot une ADR du
corpus. Le lecteur qui cherche ce que fait une classe traverse d abord ce qu elle a ete.

**Le seuil depend de ce que le bloc surmonte**, et le compte est en LIGNES au-dela. Un seuil unique
mesurait mal : c est dans la javadoc de CLASSE que le pourquoi a sa place, et un seuil taille pour
une methode l y comptait comme de la dette. Les distributions de CE depot, production seule :

| nature | blocs | mediane | 9e decile | seuil |
|---|---:|---:|---:|---:|
| type (classe, record, enum, interface) | 1 411 | 7 | 14 | **15** |
| methode | 4 809 | 2 | 5 | **8** |
| champ | 836 | 2 | 3 | **8** |
| constante d enum | 127 | 1 | 3 | **8** |
| autre | 38 | 2 | 4 | **8** |

Le seuil de chaque nature est pose **au-dessus de son 9e decile** : il laisse passer le regime
normal du depot et ne signale que ce qui en sort. Un seuil de 8 sur les types signalait 495 blocs
ici, contre 104 au seuil de 15 - quatre blocs sur cinq relevaient donc du regime normal d une
classe. Ces seuils viennent de la ligne d origine ; ils sont REPRIS parce que la distribution
mesuree ici les confirme, et non parce qu ils y etaient.

Le grain de la ligne a ete choisi apres coup. Au grain du BLOC, reecrire un bloc de 50 lignes en 22
ne bougeait pas le compte d un cran : le travail ne se voyait pas, et le cliquet poussait a couper
du contrat pour passer sous le seuil plutot qu a retirer du recit.

**Ce que ce cliquet ne compte pas.** Les blocs de `//` dans un corps de methode, qui sont une autre
population. La ligne d origine leur donne un garde dedie ; ce depot ne l a pas encore (#4462), et
c est une CECITE, pas une exemption : rien ici ne remarque un recit qui descend de la javadoc vers
le corps de la methode.

**Ce qu il ne compte pas non plus, et c est une decision.** La javadoc des TESTS. La ligne d origine
l a fait entrer dans la meme population ; ici le corpus reste la production, parce qu un garde DOIT
declarer ce qu il verifie (article A2) et que sa propre javadoc est cette declaration. Etendre le
compte aux tests est une decision qui n a pas ete prise.

**Pourquoi « probable » et non « certaine ».** La longueur se compte, ce qu il faut couper ne se
decide pas mecaniquement. Trois natures se melangent dans un bloc trop long, et seule la lecture les
separe : ce qui est caduc s enleve, ce qui est une decision se CITE au lieu d etre recopie, ce que
le code dit deja disparait. Le script rend des SUSPECTS.
"""

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from _commun import rapporte  # noqa: E402

# Le numero, et non le slug : ici l identite d une ADR est son numero, et `_commun.py` la
# retrouve par `dev-docs/decisions/{numero}-*.md`.
ADR = "4359"

RACINE = pathlib.Path(__file__).resolve().parents[2]
PRODUCTION = RACINE / "src" / "main" / "java"

# Au-dela, chaque ligne de prose compte une. Chaque seuil est pose au-dessus du 9e decile de sa
# nature, mesure sur le depot : il laisse passer le regime normal et signale ce qui en sort.
SEUILS = {"type": 15, "methode": 8, "champ": 8, "constante": 8, "autre": 8}

# Conserve pour les appelants qui mesurent une longueur sans connaitre la nature du bloc.
SEUIL = 8

# Ce qu un bloc surmonte, lu sur la premiere ligne de code qui le suit, annotations sautees.
TYPE = re.compile(r"\b(class|interface|enum|record)\s+\w")
# Une signature : un identifiant colle a une parenthese, et une fin de declaration - `{`, `;`,
# `throws`, ou une liste de parametres qui deborde sur la ligne suivante.
SIGNATURE = re.compile(r"\w\s*\([^;]*$|\w\s*\(.*\)\s*(\{|;|throws\b)")
# Une constante d enum : un identifiant majuscule, ses arguments eventuels, une virgule ou un
# point-virgule. Elle se teste AVANT la signature, dont la forme a arguments l imiterait.
CONSTANTE = re.compile(r"^[A-Z][A-Z0-9_]*\s*(\(.*\))?\s*[,;]$")
ANNOTATION = re.compile(r"^@\w")

# Une etiquette de contrat : elle documente une entree ou une sortie, elle ne raconte rien.
ETIQUETTE = re.compile(r"@\w")


def prose(lignes: list[str]) -> int:
    """Les lignes d un bloc qui ne sont ni vides, ni du contrat, ni de la structure.

    Une etiquette `@param` tient souvent sur plusieurs lignes. Les SUITES appartiennent a
    l etiquette : les compter comme de la prose penaliserait un record bien documente, ce que cette
    decision refuse explicitement. Le banc ne le voyait pas - ses trente `@param` tenaient chacun
    sur une ligne.

    **Un tableau et un bloc de code ne racontent pas davantage.** Le meme argument qui exclut
    `@param` les exclut : une ligne de tableau specifie - « ce cas, ce verdict » - et un exemple
    montre un usage. Ni l une ni l autre n est le recit qu on cherche a borner. Les compter faisait
    payer une matrice de decision comme un paragraphe d histoire, soit 86 lignes sur 823.

    **Ce que cette exclusion ouvre, et qui n est pas garde.** Un bloc de code jamais referme
    blanchirait tout ce qui le suit. Le depot n en porte aucun aujourd hui - mesure faite - et un
    tel bloc se verrait a la lecture, mais rien ne l empeche.
    """
    compte = 0
    dans_etiquette = False
    dans_code = False
    for l in lignes:
        corps = l.strip()[3:].strip()
        if corps.startswith("```"):
            dans_code = not dans_code
            continue
        if dans_code:
            continue
        if not corps:
            dans_etiquette = False
            continue
        if ETIQUETTE.match(corps):
            dans_etiquette = True
            continue
        if dans_etiquette or corps.startswith("|"):
            continue
        compte += 1
    return compte


def nature(declaration: str) -> str:
    """Ce qu un bloc surmonte : `type`, `methode`, `champ`, `constante`, ou `autre`.

    Le seuil en depend, parce que la longueur n a pas le meme sens selon la place. Un paragraphe de
    pourquoi au-dessus d une CLASSE est a sa place ; le meme au-dessus d un accesseur ne l est pas.
    """
    if not declaration:
        return "autre"
    if TYPE.search(declaration):
        return "type"
    if CONSTANTE.match(declaration):
        return "constante"
    if SIGNATURE.search(declaration):
        return "methode"
    if declaration.endswith(";") or "=" in declaration:
        return "champ"
    return "autre"


def _declaration(lignes: list[str], i: int) -> str:
    """La premiere ligne de code apres le bloc, annotations sautees.

    Une annotation peut s etaler sur plusieurs lignes - `@Command(` d une commande CLI en prend une
    quinzaine. Sauter la seule ligne qui porte l arobase laissait alors lire `name = "..."` comme la
    declaration, et cinquante-huit javadoc de CLASSE etaient mesurees au seuil d un CHAMP. Les
    parentheses sont donc suivies jusqu a leur fermeture.
    """
    ouverts = 0
    while i < len(lignes):
        nu = lignes[i].strip()
        if not nu or nu.startswith("///"):
            i += 1
            continue
        if ouverts > 0 or ANNOTATION.match(nu):
            ouverts += nu.count("(") - nu.count(")")
            i += 1
            continue
        return nu
    return ""


def blocs(fichier: pathlib.Path) -> list[tuple[int, int]]:
    """Les blocs `///` du fichier : (ligne de depart, lignes de prose)."""
    return [(depart, n) for depart, n, _ in blocs_par_nature(fichier)]


def blocs_par_nature(fichier: pathlib.Path) -> list[tuple[int, int, str]]:
    """Les blocs `///` du fichier : (ligne de depart, lignes de prose, nature de ce qu il surmonte)."""
    lignes = fichier.read_text(encoding="utf-8").split("\n")
    trouves, i = [], 0
    while i < len(lignes):
        if lignes[i].strip().startswith("///"):
            j = i
            while j < len(lignes) and lignes[j].strip().startswith("///"):
                j += 1
            trouves.append((i + 1, prose(lignes[i:j]), nature(_declaration(lignes, j))))
            i = j
        else:
            i += 1
    return trouves


def suspects(racine: pathlib.Path = None) -> list[str]:
    """Un suspect par LIGNE de prose au-dela du seuil, et non par bloc.

    Le grain a ete choisi apres coup, et la raison merite d etre ecrite. Compter les BLOCS trop
    longs ne bougeait pas d un cran quand on reecrivait un bloc de 50 lignes en 22 : le travail ne
    se voyait pas, et le cliquet poussait a couper du CONTRAT pour passer sous le seuil plutot qu a
    retirer du recit. Au grain de la ligne, chaque ligne retiree compte, et un bloc qui garde
    vingt lignes de contrat coute simplement douze - ce qu il vaut.

    Un bloc sous le seuil de sa nature ne coute rien : une classe difficile merite un paragraphe,
    un accesseur non.
    """
    racine = racine or PRODUCTION
    trouves = []
    for f in sorted(racine.rglob("*.java")):
        # Le chemin est relatif a la RACINE du depot, et non a l arbre : sans quoi un suspect ne
        # dirait pas de quel arbre il vient, et deux homonymes se confondraient. Les temoins, eux,
        # montent un arbre jetable et n ont que leur nom de fichier.
        nom = f.relative_to(RACINE) if f.is_relative_to(RACINE) else f.name
        for depart, n, quoi in blocs_par_nature(f):
            seuil = SEUILS[quoi]
            for i in range(n - seuil):
                trouves.append(f"{nom}:{depart}  ligne {seuil + i + 1} d un bloc de {n} ({quoi})")
    return trouves


def _auto_test() -> int:
    import tempfile

    cas = []
    with tempfile.TemporaryDirectory() as d:
        r = pathlib.Path(d)

        def pose(nom: str, contenu: str) -> None:
            (r / nom).write_text(contenu, encoding="utf-8")

        def bloc(n: int, declaration: str) -> str:
            return "\n".join(f"/// Ligne {i}." for i in range(n)) + "\n" + declaration + "\n"

        ST, SM = SEUILS["type"], SEUILS["methode"]

        pose("A.java", bloc(ST, "class A {}"))
        cas.append(("un bloc de type au seuil passe", suspects(r) == []))

        pose("A.java", bloc(ST + 3, "class B {}"))
        f = suspects(r)
        # Trois lignes au-dela du seuil : trois suspects. C est ce grain qui fait qu une reecriture
        # partielle se voit, au lieu d attendre que le bloc passe sous le seuil pour compter.
        cas.append((f"un bloc de type de {ST + 3} lignes coute trois", len(f) == 3))
        cas.append(("le suspect dit la taille du bloc", any(f"bloc de {ST + 3}" in x for x in f)))
        cas.append(("et il dit la nature", any("(type)" in x for x in f)))

        # LE cas qui justifie les seuils par nature : la MEME longueur, au-dessus d une methode,
        # coute. Sans cette distinction, le cliquet comptait pareil un paragraphe de pourquoi sur
        # une classe - ou il est a sa place - et le meme sur un accesseur.
        pose("A.java", "class C {\n" + bloc(SM + 2, "    void f() {}") + "}")
        f = suspects(r)
        cas.append((f"la meme longueur sur une methode coute", len(f) == 2))
        cas.append(("la nature methode est reconnue", any("(methode)" in x for x in f)))

        # Et la borne : ce meme bloc, au-dessus d une CLASSE, ne coute rien.
        pose("A.java", bloc(SM + 2, "class C {}"))
        cas.append(("le meme bloc sur une classe ne coute rien", suspects(r) == []))

        # Un champ suit le regime de la methode, pas celui du type.
        pose("A.java", "class D {\n" + bloc(SM + 1, "    private int x = 1;") + "}")
        f = suspects(r)
        cas.append(("un champ suit le seuil court", len(f) == 1 and "(champ)" in f[0]))

        # Une constante d enum aussi - et sa forme a arguments ne doit pas passer pour une methode.
        pose("A.java", "enum E {\n" + bloc(SM + 1, '    ROUGE("r"),') + "}")
        f = suspects(r)
        cas.append(("une constante d enum n est pas une methode", len(f) == 1 and "(constante)" in f[0]))

        # Une annotation entre le bloc et la declaration ne doit pas brouiller la lecture.
        pose("A.java", "class F {\n" + bloc(SM + 2, "    @Override\n    void g() {}") + "}")
        cas.append(("une annotation ne cache pas la declaration",
                    len(suspects(r)) == 2 and "(methode)" in suspects(r)[0]))

        # LE cas qui a fausse cinquante-huit mesures : une annotation MULTILIGNE. Sauter sa seule
        # premiere ligne fait lire `name = "..."` comme la declaration, et une javadoc de CLASSE se
        # retrouve mesuree au seuil d un CHAMP - deux fois plus severe.
        commande = ('@Command(\n        name = "faire",\n        description = "fait")\n'
                    "public class G {}")
        pose("A.java", bloc(SM + 2, commande))
        cas.append(("une annotation multiligne ne fait pas passer une classe pour un champ",
                    suspects(r) == []))

        # Et la borne : au-dela du seuil du TYPE, la meme classe annotee coute bien.
        pose("A.java", bloc(ST + 2, commande))
        f = suspects(r)
        cas.append(("cette classe annotee coute au seuil du type",
                    len(f) == 2 and "(type)" in f[0]))

        # LE cas qui rend le seuil juste : les etiquettes de contrat ne racontent rien. Sans cette
        # exception, un record de trente champs serait le pire suspect du depot alors qu il est
        # exemplaire.
        tags = "/// Resume.\n" + "\n".join(f"/// @param p{i} un champ" for i in range(30)) + "\nclass C {}\n"
        pose("A.java", tags)
        cas.append(("trente @param ne racontent rien", suspects(r) == []))

        # Une etiquette tient souvent sur plusieurs lignes. Sans ce cas, les SUITES comptaient
        # comme de la prose et un record bien documente devenait le pire suspect du depot - ce que
        # le cas precedent ne voyait pas, ses trente etiquettes tenant chacune sur une ligne.
        longs = "/// Resume.\n" + "\n".join(
            f"/// @param p{i} un champ\n///     dont l explication continue\n///     sur trois lignes"
            for i in range(30)) + "\nclass C {}\n"
        pose("A.java", longs)
        cas.append(("les suites d une etiquette non plus", suspects(r) == []))

        # Un TABLEAU specifie, il ne raconte pas : ses lignes ne comptent pas, meme argument que
        # pour les etiquettes de contrat. Sans ce cas, une matrice de decision se paierait comme
        # un paragraphe d histoire.
        table = ("/// Resume.\n"
                 + "\n".join(f"/// | cas {i} | verdict {i} |" for i in range(ST + 5))
                 + "\nclass T {}\n")
        pose("A.java", table)
        cas.append(("un tableau ne compte pas comme de la prose", suspects(r) == []))

        # Un bloc de CODE montre un usage : il ne compte pas davantage, cloture comprise.
        exemple = ("/// Resume.\n/// ```java\n"
                   + "\n".join(f"/// ligne{i}();" for i in range(ST + 5))
                   + "\n/// ```\nclass U {}\n")
        pose("A.java", exemple)
        cas.append(("un bloc de code non plus", suspects(r) == []))

        # LA borne : ce qui SUIT un bloc de code referme compte de nouveau. Sans elle, une fence
        # ouverte en tete blanchirait tout le bloc.
        apres_code = ("/// ```java\n/// exemple();\n/// ```\n"
                      + "\n".join(f"/// Ligne {i}." for i in range(ST + 2))
                      + "\nclass V {}\n")
        pose("A.java", apres_code)
        cas.append(("ce qui suit un bloc de code referme compte", len(suspects(r)) == 2))

        # Une ligne `///` vide ne compte pas non plus : elle aere, elle ne dit rien.
        aere = "\n".join(f"/// Ligne {i}.\n///" for i in range(SEUILS["type"])) + "\nclass D {}\n"
        pose("A.java", aere)
        cas.append(("les lignes vides n allongent pas le bloc", suspects(r) == []))

        pose("A.java", "class E {}\n")
        cas.append(("un fichier sans javadoc ne rend rien", suspects(r) == []))

        # Deux blocs dans un meme fichier cumulent leur dette : le grain est la ligne.
        pose("A.java", bloc(ST + 3, "class G {}") + "\n" + bloc(ST + 3, "class H {}"))
        cas.append(("deux blocs longs cumulent leur dette", len(suspects(r)) == 6))

    for nom, ok in cas:
        print(f"  {'✔' if ok else '✘'} {nom}")
    rates = [n for n, ok in cas if not ok]
    if rates:
        print(f"\n{len(rates)} cas en échec : le cliquet ne compte pas ce qu'il annonce.", file=sys.stderr)
        return 1
    print(f"\n{len(cas)} cas : le cliquet voit un bloc narratif et laisse passer le contrat.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        sys.exit(_auto_test())
    listes = suspects()
    if "--releve" in sys.argv:
        for s in listes[:30]:
            print(f"  {s}")
        print(f"\n{len(listes)} lignes de prose au-delà de {SEUIL} par bloc")
        sys.exit(0)
    sys.exit(rapporte(ADR, "javadoc qui raconte au lieu de contracter", listes, apercu=20))
